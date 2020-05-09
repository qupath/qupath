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
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.measure.PixelClassificationMeasurementManager;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.RegionFilter;
import qupath.lib.gui.viewer.RegionFilter.StandardRegionFilters;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.roi.interfaces.ROI;
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
		comboRegion.getItems().addAll(StandardRegionFilters.values());
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
	//		var availableChannels = new String[] {
	//			server.getOriginalMetadata().getC
	//		};
			var sizeUnits = Arrays.asList(
					"Pixels",
					GeneralTools.micrometerSymbol()
			);
			
			var params = new ParameterList()
					.addChoiceParameter("objectType", "Object type", "Annotation", objectTypes)
					.addDoubleParameter("minSize", "Minimum object size", 0, null, "Minimum size of a region to keep (smaller regions will be dropped)")
					.addDoubleParameter("minHoleSize", "Minimum hole size", 0, null, "Minimum size of a hole to keep (smaller holes will be filled)")
					.addChoiceParameter("sizeUnits", "Minimum object/hole size units", "Pixels", sizeUnits)
					.addBooleanParameter("doSplit", "Split objects", true,
							"Split multi-part regions into separate objects")
					.addBooleanParameter("clearExisting", "Delete existing objects", false,
							"Delete any existing objects within the selected object before adding new objects (or entire image if no object is selected)");
			
			PixelCalibration cal = server.getPixelCalibration();
			params.setHiddenParameters(!cal.hasPixelSizeMicrons(), "sizeUnits");
			
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
			double minSizePixels = params.getDoubleParameterValue("minSize");
			double minHoleSizePixels = params.getDoubleParameterValue("minHoleSize");
			if (cal.hasPixelSizeMicrons() && !params.getChoiceParameterValue("sizeUnits").equals("Pixels")) {
				minSizePixels /= (cal.getPixelWidthMicrons() * cal.getPixelHeightMicrons());
				minHoleSizePixels /= (cal.getPixelWidthMicrons() * cal.getPixelHeightMicrons());
			}
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
			
	//		int nChildObjects = 0;
	//		if (selected == null)
	//			nChildObjects = hierarchy.nObjects();
	//		else
	//			nChildObjects = PathObjectTools.countDescendants(selected);
	//		if (nChildObjects > 0) {
	//			String message = "Existing child object will be deleted - is that ok?";
	//			if (nChildObjects > 1)
	//				message = nChildObjects + " existing descendant object will be deleted - is that ok?";
	//			if (!DisplayHelpers.showConfirmDialog("Create objects", message))
	//				return false;
	//		}
	//		// Need to turn off live prediction so we don't start training on the results...
	//		livePrediction.set(false);
			
			return PixelClassifierTools.createObjectsFromPixelClassifier(
					server, imageData.getHierarchy(), selected, creator,
					minSizePixels, minHoleSizePixels, doSplit, clearExisting);
		}
	
	/**
	 * Prompt to add measurements to objects based upon a classification output.
	 * @param imageData the image containing the objects to which measurements should be added
	 * @param classifier the classifier used to determine the measurements
	 * @return true if measurements were added, false otherwise
	 */
	public static boolean promptToAddMeasurements(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		return promptToAddMeasurements(imageData, new PixelClassificationImageServer(imageData, classifier));
	}

	
	/**
	 * Prompt to add measurements to objects based upon an {@link ImageServer} representing a classification output.
	 * @param imageData the image containing the objects to which measurements should be added
	 * @param classifierServer the {@link ImageServer} that generates corresponding classified pixels
	 * @return true if measurements were added, false otherwise
	 */
	public static boolean promptToAddMeasurements(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> classifierServer) {
		return promptToAddMeasurements(imageData, new PixelClassificationMeasurementManager(classifierServer));
	}

	
	private static boolean promptToAddMeasurements(ImageData<BufferedImage> imageData, PixelClassificationMeasurementManager manager) {
		
		if (imageData == null) {
			Dialogs.showNoImageError("Pixel classifier");
			return false;
		}
		
		var hierarchy = imageData.getHierarchy();
		
		var selected = hierarchy.getSelectionModel().getSelectedObjects();
		var annotations = hierarchy.getAnnotationObjects();
		
		String optionSelected = "Selected objects";
		String optionAnnotations = "Annotations (only)";
		String optionImage = "Full image (only)";
		String optionAnnotationImage = "Annotations + full image";
		List<String> options = new ArrayList<>();
		String defaultOption = optionImage;
		if (!annotations.isEmpty()) {
			options.add(optionAnnotations);
			options.add(optionAnnotationImage);
			defaultOption = optionAnnotations;
		}
		if (!selected.isEmpty()) {
			options.add(0, optionSelected);
			defaultOption = optionSelected;
		}
		options.add(optionImage);
		
		var selectedOption = Dialogs.showChoiceDialog("Pixel classifier", "Choose objects to measure", options, defaultOption);
		if (selectedOption == null)
			return false;
		
		List<PathObject> objectsToMeasure = new ArrayList<>();
		if (optionSelected.equals(selectedOption)) {
			objectsToMeasure.addAll(selected);
		} else if (optionAnnotations.equals(optionAnnotations)) {
			objectsToMeasure.addAll(annotations);			
		} else if (optionAnnotations.equals(optionImage)) {
			objectsToMeasure.addAll(annotations);			
			objectsToMeasure.add(hierarchy.getRootObject());
		} else if (optionAnnotations.equals(optionAnnotationImage)) {
			objectsToMeasure.addAll(annotations);		
			objectsToMeasure.add(hierarchy.getRootObject());
		}
		
		if (objectsToMeasure.isEmpty())
			return false;
		
		int n = objectsToMeasure.size();
		if (optionAnnotations.equals(optionImage))
			logger.info("Requesting measurements for image");
		else if (n == 1)
			logger.info("Requesting measurements for one object");
		else
			logger.info("Requesting measurements for {} objects", n);
		
		int i = 0;
		for (var pathObject : objectsToMeasure) {
			i++;
			if (n < 100 || n % 100 == 0)
				logger.debug("Completed {}/{}", i, n);
			try (var ml = pathObject.getMeasurementList()) {
				for (String name : manager.getMeasurementNames()) {
					Number value = manager.getMeasurementValue(pathObject, name, false);
					double val = value == null ? Double.NaN : value.doubleValue();
					ml.putMeasurement(name, val);
				}
			}
			// We really want to lock objects so we don't end up with wrong measurements
			pathObject.setLocked(true);
		}
		hierarchy.fireObjectMeasurementsChangedEvent(manager, objectsToMeasure);
		return true;
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
