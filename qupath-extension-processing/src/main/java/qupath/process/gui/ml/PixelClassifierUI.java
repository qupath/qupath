package qupath.process.gui.ml;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.RegionFilter;
import qupath.lib.gui.viewer.RegionFilter.StandardRegionFilters;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;
import qupath.opencv.ml.pixel.PixelClassifierTools;

/**
 * Helper class for generating standardized UI components for pixel classification.
 * 
 * @author Pete Bankhead
 */
public class PixelClassifierUI {
	
	private final static Logger logger = LoggerFactory.getLogger(PixelClassifierUI.class);

	/**
	 * Create a {@link ComboBox} that can be used to select the pixel classification region filter.
	 * @param options
	 * @return
	 */
	public static ComboBox<RegionFilter> createRegionFilterCombo(OverlayOptions options) {
		var comboRegion = new ComboBox<RegionFilter>();
//		comboRegion.getItems().addAll(StandardRegionFilters.values());
		comboRegion.getItems().addAll(StandardRegionFilters.EVERYWHERE, StandardRegionFilters.ANY_ANNOTATIONS);
		var selected = options.getPixelClassificationRegionFilter();
		if (!comboRegion.getItems().contains(selected))
			comboRegion.getItems().add(selected);
		comboRegion.getSelectionModel().select(selected);
		comboRegion.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> options.setPixelClassificationRegionFilter(n));
		// We need to be able to update somehow... but don't really want to listen to the OverlayOptions and risk thwarting garbage collection
		comboRegion.focusedProperty().addListener((v, o, n) -> {
			comboRegion.getSelectionModel().select(options.getPixelClassificationRegionFilter());
		});
		comboRegion.setMaxWidth(Double.MAX_VALUE);
		comboRegion.setTooltip(new Tooltip("Control where the pixel classification is applied during preview.\n"
				+ "Warning! Classifying the entire image at high resolution can be very slow and require a lot of memory."));
		return comboRegion;
	}

	/**
	 * Create a standard button pane for pixel classifiers, to create, measure and classify objects.
	 * @param imageData expression that provides the {@link ImageData} to which the operation should be applied
	 * @param classifier expression that provides the {@link PixelClassifier} that will be used
	 * @return a {@link Pane} that may be added to a scene
	 */
	public static Pane createPixelClassifierButtons(ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<PixelClassifier> classifier) {
	
		BooleanBinding disableButtons = imageData.isNull().or(classifier.isNull());
		
		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.disableProperty().bind(disableButtons);
		btnCreateObjects.setTooltip(new Tooltip("Create annotation or detection objects from the classification output"));
		
		var btnAddMeasurements = new Button("Measure");
		btnAddMeasurements.disableProperty().bind(disableButtons);
		btnAddMeasurements.setTooltip(new Tooltip("Add measurements to existing objects based upon the classification output"));
		
		var btnClassifyObjects = new Button("Classify");
		btnClassifyObjects.disableProperty().bind(disableButtons);
		btnClassifyObjects.setTooltip(new Tooltip("Classify detection based upon the prediction at the ROI centroid"));
		
		btnAddMeasurements.setOnAction(e -> {
			promptToAddMeasurements(imageData.get(), classifier.get());			
		});		
		btnCreateObjects.setOnAction(e -> {
			promptToCreateObjects(imageData.get(), classifier.get());
		});
		btnClassifyObjects.setOnAction(e -> {
			PixelClassifierTools.classifyDetectionsByCentroid(imageData.get(), classifier.get());
		});
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, btnAddMeasurements, btnCreateObjects, btnClassifyObjects);
		
		return PaneTools.createColumnGrid(btnAddMeasurements, btnCreateObjects, btnClassifyObjects);
	}
	
	
	

	/**
	 * Prompt the user to create objects from the output of a {@link PixelClassifier}.
	 * 
	 * @param imageData the {@link ImageData} to which objects should be added
	 * @param classifier the {@link PixelClassifier} used to create predictions, which will be used to create objects
	 * @return true if changes were made, false otherwise
	 */
	public static boolean promptToCreateObjects(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		return promptToCreateObjects(imageData, new PixelClassificationImageServer(imageData, classifier));
	}

	/**
	 * Prompt the user to create objects directly from the pixels of an {@link ImageServer}.
	 * Often, the {@link ImageServer} has been created by applying a {@link PixelClassifier}.
	 * 
	 * @param imageData the {@link ImageData} to which objects should be added
	 * @param server the {@link ImageServer} used to generate objects
	 * @return true if changes were made, false otherwise
	 */
	public static boolean promptToCreateObjects(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> server) {
			Objects.requireNonNull(imageData);
			Objects.requireNonNull(server);
			
			var objectTypes = Arrays.asList(
					"Annotation", "Detection"
			);

			var cal = server.getPixelCalibration();
			var units = cal.unitsMatch2D() ? cal.getPixelWidthUnit()+"^2" : cal.getPixelWidthUnit() + "x" + cal.getPixelHeightUnit();
			
			var params = new ParameterList()
					.addChoiceParameter("objectType", "Object type", "Annotation", objectTypes)
					.addDoubleParameter("minSize", "Minimum object size", 0, units, "Minimum size of a region to keep (smaller regions will be dropped)")
					.addDoubleParameter("minHoleSize", "Minimum hole size", 0, units, "Minimum size of a hole to keep (smaller holes will be filled)")
					.addBooleanParameter("doSplit", "Split objects", true,
							"Split multi-part regions into separate objects")
					.addBooleanParameter("clearExisting", "Delete existing objects", false,
							"Delete any existing objects within the selected object before adding new objects (or entire image if no object is selected)");
			
			if (!Dialogs.showParameterDialog("Create objects", params))
				return false;
			
			Function<ROI, PathObject> creator;
			if (params.getChoiceParameterValue("objectType").equals("Detection"))
				creator = r -> PathObjects.createDetectionObject(r);
			else
				creator = r -> {
					var annotation = PathObjects.createAnnotationObject(r);
					((PathAnnotationObject)annotation).setLocked(true);
					return annotation;
				};
			boolean doSplit = params.getBooleanParameterValue("doSplit");
			double minSize = params.getDoubleParameterValue("minSize");
			double minHoleSize = params.getDoubleParameterValue("minHoleSize");
			boolean clearExisting = params.getBooleanParameterValue("clearExisting");
			
			Collection<PathObject> allSelected = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
			List<PathObject> selected = allSelected.stream().filter(p -> p.hasROI() && p.getROI().isArea() && 
					(p.isAnnotation() || p.isTMACore())).collect(Collectors.toList());
			boolean hasSelection = true;
			if (allSelected.isEmpty()) {
				hasSelection = false;
				selected = Collections.singletonList(imageData.getHierarchy().getRootObject());
			} else if (selected.size() != allSelected.size()) {
				Dialogs.showErrorMessage("Create objects", "All selected objects should be annotations with area ROIs or TMA cores!");
				return false;
			}
			if (hasSelection && selected.size() == 1 && selected.get(0).getPathClass() != null && selected.get(0).getPathClass() != PathClassFactory.getPathClass(StandardPathClasses.REGION)) {
				var btn = Dialogs.showYesNoCancelDialog("Create objects", "Create objects for selected annotation(s)?\nChoose 'no' to use the entire image.");
				if (btn == DialogButton.CANCEL)
					return false;
				if (btn == DialogButton.NO)
					selected = Collections.singletonList(imageData.getHierarchy().getRootObject());
			}
			
			return PixelClassifierTools.createObjectsFromPredictions(
					server, imageData.getHierarchy(), selected, creator,
					minSize, minHoleSize, doSplit, clearExisting);
		}
	
//	/**
//	 * Prompt to add measurements to objects based upon a classification output.
//	 * @param imageData the image containing the objects to which measurements should be added
//	 * @param classifier the classifier used to determine the measurements
//	 * @return true if measurements were added, false otherwise
//	 */
//	public static boolean promptToAddMeasurements(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
//		return promptToAddMeasurements(imageData, new PixelClassificationImageServer(imageData, classifier));
//	}

	
	private static enum SelectionChoice {
		CURRENT_SELECTION, ANNOTATIONS, DETECTIONS, CELLS, TILES, TMA, FULL_IMAGE;
		
		private void handleSelection(ImageData<?> imageData) {
			switch (this) {
			case FULL_IMAGE:
				Commands.resetSelection(imageData);
				break;
			case ANNOTATIONS:
			case CELLS:
			case DETECTIONS:
			case TMA:
			case TILES:
				Commands.selectObjectsByClass(imageData, getObjectClass());
				break;
			case CURRENT_SELECTION:
			default:
				break;
			}
		}
		
		private Class<? extends PathObject> getObjectClass() {
			switch (this) {
			case ANNOTATIONS:
				return PathAnnotationObject.class;
			case CELLS:
				return PathCellObject.class;
			case DETECTIONS:
				return PathDetectionObject.class;
			case TMA:
				return TMACoreObject.class;
			case TILES:
				return PathTileObject.class;
			default:
				return null;
			}
		}
		
		@Override
		public String toString() {
			switch (this) {
			case ANNOTATIONS:
				return "Annotations";
			case CELLS:
				return "Detections (cells only)";
			case CURRENT_SELECTION:
				return "Current selection";
			case DETECTIONS:
				return "Detections (all)";
			case TMA:
				return "TMA cores";
			case FULL_IMAGE:
				return "Full image (no selection)";
			case TILES:
				return "Detections (tiles only)";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
	}
	
	
	private static boolean promptToAddMeasurements(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		
		if (imageData == null) {
			Dialogs.showNoImageError("Pixel classifier");
			return false;
		}
		
		var hierarchy = imageData.getHierarchy();
		
		List<SelectionChoice> choices = new ArrayList<>();
		SelectionChoice defaultChoice = null;
		if (!hierarchy.getSelectionModel().noSelection()) {
			choices.add(SelectionChoice.CURRENT_SELECTION);
			defaultChoice = SelectionChoice.CURRENT_SELECTION;
		}
		choices.add(SelectionChoice.FULL_IMAGE);
		var classes = hierarchy.getFlattenedObjectList(null).stream().map(p -> p.getClass()).collect(Collectors.toSet());
		for (var choice : SelectionChoice.values()) {
			if (choice.getObjectClass() != null && classes.contains(choice.getObjectClass()))
					choices.add(choice);
		}
		if (defaultChoice == null) {
			if (choices.contains(SelectionChoice.ANNOTATIONS))
				defaultChoice = SelectionChoice.ANNOTATIONS;
			else
				defaultChoice = choices.get(1);
		}
			
		var params = new ParameterList()
				.addStringParameter("id", "Measurement name", "Classifier", "Choose a base name for measurements - this helps distinguish between measurements from different classifiers")
				.addChoiceParameter("choice", "Select objects", defaultChoice, choices, "Select the objects");
		
		if (!Dialogs.showParameterDialog("Pixel classifier", params))
			return false;
		
		var measurementID = params.getStringParameterValue("id");
		var selectionChoice = (SelectionChoice)params.getChoiceParameterValue("choice");
		
		selectionChoice.handleSelection(imageData);
		
		var objectsToMeasure = hierarchy.getSelectionModel().getSelectedObjects();
		int n = objectsToMeasure.size();
		if (objectsToMeasure.isEmpty()) {
			objectsToMeasure = Collections.singleton(hierarchy.getRootObject());
			logger.info("Requesting measurements for image");
		} else if (n == 1)
			logger.info("Requesting measurements for one object");
		else
			logger.info("Requesting measurements for {} objects", n);
		
		if (PixelClassifierTools.addPixelClassificationMeasurements(imageData, classifier, measurementID)) {
			
			return true;
		}
		return false;
	}
	
	
	
	/**
	 * Prompt the user to save a pixel classifier within a project.
	 * 
	 * @param project the project within which to save the classifier
	 * @param classifier the classifier to save
	 * @return the name of the saved classifier, or null if the operation was stopped
	 * @throws IOException thrown if there was an error while attempting to save the classifier
	 */
	public static String promptToSaveClassifier(Project<BufferedImage> project, PixelClassifier classifier) throws IOException {
		
		String name = getDefaultClassifierName(project, classifier);
		
		String classifierName = GuiTools.promptForFilename("Save model", "Model name", name);
		if (classifierName == null)
			return null;
		
//		var pane = new GridPane();
//		pane.setHgap(5);
//		pane.setVgap(5);
//		pane.setPadding(new Insets(10));
//		pane.setMaxWidth(Double.MAX_VALUE);
//		
//		var labelGeneral = new Label("Click 'Apply' to save the prediction model & predictions in the current project.\n" +
//				"Click 'File' if you want to save either of these elsewhere.");
//		labelGeneral.setContentDisplay(ContentDisplay.CENTER);
//		
//		var label = new Label("Name");
//		var tfName = new TextField(name);
//		label.setLabelFor(tfName);
//		
//		var cbModel = new CheckBox("Save prediction model");
//		var cbImage = new CheckBox("Save prediction image");
//		var btnModel = new Button("File");
//		btnModel.setTooltip(new Tooltip("Save prediction model to a file"));
//		btnModel.setOnAction(e -> {
//			var file = QuPathGUI.getSharedDialogHelper().promptToSaveFile("Save model", null, tfName.getText(), "Prediction model", ".json");
//			if (file != null) {
//				try (var writer = Files.newWriter(file, StandardCharsets.UTF_8)) {
//					GsonTools.getInstance(true).toJson(classifier, writer);
//				} catch (IOException e1) {
//					DisplayHelpers.showErrorMessage("Save model", e1);
//				}
//			}
//		});
//		
//		var btnImage = new Button("File");
//		btnImage.setTooltip(new Tooltip("Save prediction image to a file"));
//		btnImage.setOnAction(e -> {
//			var file = QuPathGUI.getSharedDialogHelper().promptToSaveFile("Save image", null, tfName.getText(), "Prediction image", ".ome.tif");
//			if (file != null) {
//				try {
//					ImageWriterTools.writeImageRegion(new PixelClassificationImageServer(QuPathGUI.getInstance().getImageData(), classifier), null, file.getAbsolutePath());
//				} catch (IOException e1) {
//					DisplayHelpers.showErrorMessage("Save image", e1);
//				}
//			}
//		});
//		
//		int row = 0;
//		int col = 0;
//		GridPaneTools.addGridRow(pane, row++, col, "Input a unique classifier name", label, tfName);
//		GridPaneTools.addGridRow(pane, row++, col, "Save the classification model (can be applied to similar images)", cbModel, cbModel, btnModel);
//		GridPaneTools.addGridRow(pane, row++, col, "Save the prediction image", cbImage, cbImage, btnImage);
//		GridPaneTools.addGridRow(pane, row++, col, labelGeneral.getText(), labelGeneral, labelGeneral);
//		
//		GridPaneTools.setHGrowPriority(Priority.ALWAYS, labelGeneral, cbModel, cbImage, tfName);
//		GridPaneTools.setFillWidth(Boolean.TRUE, labelGeneral, cbModel, cbImage, tfName);
//		GridPaneTools.setMaxWidth(Double.MAX_VALUE, labelGeneral, cbModel, cbImage, tfName);
//		
//		var dialog = new Dialog<ButtonType>();
//		dialog.setTitle("Save");
//		dialog.getDialogPane().setContent(pane);
//		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
//		if (dialog.showAndWait().orElseGet(() -> ButtonType.CANCEL) == ButtonType.CANCEL)
//			return null;
////		if (!DisplayHelpers.showMessageDialog("Save & Apply", pane)) {
////			return null;
////		}
//		String classifierName = tfName.getText();	
//		
////		var classifierName = DisplayHelpers.showInputDialog("Pixel classifier", "Pixel classifier name", name);
//		if (classifierName == null || classifierName.isBlank())
//			return null;
//		classifierName = classifierName.strip();
//		if (classifierName.isBlank() || classifierName.contains("\n")) {
//			DisplayHelpers.showErrorMessage("Pixel classifier", "Classifier name must be unique, non-empty, and not contain invalid characters");
//			return null;
//		}
//		
//		// Save the classifier in the project
//		if (cbModel.isSelected()) {
			try {
				saveClassifier(project, classifier, classifierName);
			} catch (IOException e) {
				Dialogs.showWarningNotification("Pixel classifier", "Unable to write classifier to JSON - classifier can't be reloaded later");
				logger.error("Error saving classifier", e);
				throw e;
			}
//		}
//		// Save the image
//		if (cbImage.isSelected()) {
//			var server = new PixelClassificationImageServer(QuPathGUI.getInstance().getImageData(), classifier);
//			var imageData = QuPathGUI.getInstance().getImageData();
//			var entry = project.getEntry(imageData);
//			var path = entry.getEntryPath();
//			ImageWriterTools.writeImageRegion(new PixelClassificationImageServer(imageData, classifier), null, file.getAbsolutePath());
//			logger.warn("Saving image now yet supported!");
//		}
		
		return classifierName;
	}

	
	private static void saveClassifier(Project<BufferedImage> project, PixelClassifier classifier, String classifierName) throws IOException {
		project.getPixelClassifiers().put(classifierName, classifier);
	}
	
	static boolean saveAndApply(Project<BufferedImage> project, ImageData<BufferedImage> imageData, PixelClassifier classifier) throws IOException {
		String name = promptToSaveClassifier(project, classifier);
		if (name == null)
			return false;
		return true;
//		return PixelClassifierTools.applyClassifier(project, imageData, classifier, name);
	}
	
	
	/**
	 * Get a suitable (unique) name for a pixel classifier.
	 * 
	 * @param project
	 * @param classifier
	 * @return
	 */
	private static String getDefaultClassifierName(Project<BufferedImage> project, PixelClassifier classifier) {
		String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
//		String simpleName = classifier.toString();
		String simpleName = "Pixel Model";
		String name = String.format("%s %s", date, simpleName);
		Collection<String> names = null;
		try {
			names = project.getPixelClassifiers().getNames();
		} catch (Exception e) {}
		if (names == null || names.isEmpty() || !names.contains(name))
			return name;
		int i = 1;
		while (names.contains(name)) {
			name = String.format("%s %s (%d)", date, simpleName, i);
			i++;
		}
		return GeneralTools.stripInvalidFilenameChars(name);
	}
}
