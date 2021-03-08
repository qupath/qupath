/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.process.gui.ml;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.RegionFilter;
import qupath.lib.gui.viewer.RegionFilter.StandardRegionFilters;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.BooleanParameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;

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
		comboRegion.getItems().addAll(StandardRegionFilters.EVERYWHERE, StandardRegionFilters.ANY_OBJECTS, StandardRegionFilters.ANY_ANNOTATIONS);
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
	 * @param classifierName expression that provides the saved name of the classifier. If available, running a command will 
	 * 					     result in it being logged to the history workflow for later scripting.
	 * @return a {@link Pane} that may be added to a scene
	 */
	public static Pane createPixelClassifierButtons(ObjectExpression<ImageData<BufferedImage>> imageData, ObjectExpression<PixelClassifier> classifier, StringExpression classifierName) {
	
		BooleanProperty allowWithoutSaving = new SimpleBooleanProperty(false);
		
		BooleanBinding disableButtons = imageData.isNull()
				.or(classifier.isNull())
				.or(classifierName.isEmpty().and(allowWithoutSaving.not()));
		
		
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
			promptToAddMeasurements(imageData.get(), classifier.get(), classifierName.get());			
		});
		btnCreateObjects.setOnAction(e -> {
			promptToCreateObjects(imageData.get(), classifier.get(), classifierName.get());
		});
		btnClassifyObjects.setOnAction(e -> {
			promptToClassifyDetectionsByCentroid(imageData.get(), classifier.get(), classifierName.get());
		});
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, btnAddMeasurements, btnCreateObjects, btnClassifyObjects);
		
		var paneMain = PaneTools.createColumnGrid(btnAddMeasurements, btnCreateObjects, btnClassifyObjects);
		
		// Add some more options
		var menu = new ContextMenu();
		var miWithoutSaving = new CheckMenuItem("Enable buttons for unsaved classifiers");
		miWithoutSaving.selectedProperty().bindBidirectional(allowWithoutSaving);
		var miSavePrediction = new MenuItem("Save prediction image");
		miSavePrediction.setOnAction(e -> promptToSavePredictionImage(imageData.get(), classifier.get(), classifierName.get()));
		miSavePrediction.disableProperty().bind(disableButtons);
		
		menu.getItems().addAll(
				miSavePrediction,
				miWithoutSaving
				);
		
		var btnAdvanced = GuiTools.createMoreButton(menu, Side.RIGHT);
		
		var pane = new BorderPane(paneMain);
		pane.setRight(btnAdvanced);
		return pane;
	}
	
	/**
	 * Create a pane that contains a text field and save button to allow a pixel classifier to be saved in a project.
	 * @param project the current project, within which the classifier can be saved
	 * @param classifier the classifier to save
	 * @param savedName property to store the classifier name. If the user saves the classifier, this will be set to the saved name.
	 *                  Otherwise, if classifier is changed, this will be set to null. Therefore it provides a way to determine if the  
	 *                  current classifier has been saved and, if so, what is its name.
	 * @return a pane that may be added to a scene 
	 */
	public static Pane createSavePixelClassifierPane(ObjectExpression<Project<BufferedImage>> project, ObjectExpression<PixelClassifier> classifier, StringProperty savedName) {
		
		var label = new Label("Classifier name");
		var defaultName = savedName.get();
		var tfClassifierName = new TextField(defaultName == null ? "" : defaultName);
		tfClassifierName.setPromptText("Enter pixel classifier name");
		
		// Reset the saved name if the classifier changes
		classifier.addListener((v, o, n) -> savedName.set(null));
		
		var btnSave = new Button("Save");
		btnSave.setOnAction(e -> {
			var name = tryToSave(project.get(), classifier.get(), tfClassifierName.getText(), false);
			if (name != null) {
				Dialogs.showInfoNotification("Pixel classifier", "Classifier saved as \"" + name + "\"");
				savedName.set(name);
				tfClassifierName.requestFocus();
				btnSave.requestFocus();
			}
		});
		btnSave.disableProperty().bind(
				classifier.isNull()
					.or(project.isNull())
					.or(tfClassifierName.textProperty().isEmpty()));
		tfClassifierName.disableProperty().bind(project.isNull());
		label.setLabelFor(tfClassifierName);
		
		var tooltip = new Tooltip();
		var tooltipTextYes = "Save classifier in the current project - this is required to use the classifier to use the classifier later (e.g. to create objects, measurements)";
		var tooltipTextNo = "Cannot save a classifier outside a project. Please create a project to save the classifier.";
		tooltip.setShowDelay(Duration.millis(500));
		tooltip.textProperty().bind(Bindings
				.when(project.isNull())
				.then(Bindings.createStringBinding(() -> tooltipTextNo, project))
				.otherwise(Bindings.createStringBinding(() -> tooltipTextYes, project)));
		
		var pane = new GridPane();
		Tooltip.install(pane, tooltip);
		PaneTools.addGridRow(pane, 0, 0, null, label, tfClassifierName, btnSave);
		PaneTools.setToExpandGridPaneWidth(tfClassifierName);
		pane.setHgap(5);
		
		ProjectClassifierBindings.bindPixelClassifierNameInput(tfClassifierName, project);
		
		return pane;
	}
	
	
	
	private static String tryToSave(Project<?> project, PixelClassifier classifier, String name, boolean overwriteQuietly) {
		if (project == null) {
			Dialogs.showWarningNotification("Pixel classifier", "You need a project to be able to save the pixel classifier");
			return null;
		}
		name = GeneralTools.stripInvalidFilenameChars(name);
		if (name.isBlank()) {
			Dialogs.showErrorMessage("Pixel classifier", "Please enter a valid classifier name!");
			return null;
		}
		try {
			var classifiers = project.getPixelClassifiers();
			if (!overwriteQuietly && classifiers.contains(name)) {
				if (!Dialogs.showYesNoDialog("Pixel classifier", "Overwrite existing classifier '" + name + "'?"))
					return null;
			}
			classifiers.put(name, classifier);
			return name;
		} catch (IOException ex) {
			Dialogs.showErrorMessage("Pixel classifier", ex);
			return null;
		}
	}
	
	
	private static boolean promptToSavePredictionImage(ImageData<BufferedImage> imageData, PixelClassifier classifier, String classifierName) {
		Objects.requireNonNull(imageData);
		Objects.requireNonNull(classifier);
		
		var server = PixelClassifierTools.createPixelClassificationServer(imageData, classifier);
		ImageWriter<BufferedImage> writer;
		var allWriters = ImageWriterTools.getCompatibleWriters(server, "ome.tif");
		if (allWriters == null || allWriters.isEmpty()) {
			allWriters = ImageWriterTools.getCompatibleWriters(server, null);
		}
		if (allWriters.isEmpty()) {
			Dialogs.showErrorMessage("Save prediction", "Sorry, I could not find any compatible image writers!");
			return false;
		} else if (allWriters.size() > 1) {
			Map<String, ImageWriter<BufferedImage>> map = new LinkedHashMap<>();
			for (var w : allWriters)
				map.put(w.getName(), w);
			var choice = Dialogs.showChoiceDialog("Save prediction", "Choose image writer", map.keySet(), map.keySet().iterator().next());
			writer = choice == null ? null : map.get(choice);
			if (writer == null)
				return false;
		} else
			writer = allWriters.iterator().next();

		var file = Dialogs.promptToSaveFile("Save prediction", null, classifierName, writer.getName(), writer.getDefaultExtension());
		if (file == null)
			return false;
		try {
			var path = file.getAbsolutePath();
			writer.writeImage(server, path);
			if (classifierName != null && !classifierName.isBlank()) {
				imageData.getHistoryWorkflow().addStep(
						new DefaultScriptableWorkflowStep("Write prediction image",
								String.format("writePredictionImage(\"%s\", \"%s\")", classifierName, path)
								)
						);
			}

		} catch (IOException e) {
			Dialogs.showErrorMessage("Save prediction", e);
		}
		
		return true;
	}
	
	
	private static boolean promptToClassifyDetectionsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier, String classifierName) {
		Objects.requireNonNull(imageData);
		Objects.requireNonNull(classifier);
		
		PixelClassifierTools.classifyDetectionsByCentroid(imageData, classifier);
		if (classifierName != null) {
			imageData.getHistoryWorkflow().addStep(
					new DefaultScriptableWorkflowStep("Classify detections by centroid",
							String.format("classifyDetectionsByCentroid(\"%s\")",
									classifierName)
					)
			);
		}
		return true;
	}

	
	private static ParameterList lastCreateObjectParams;
	
	/**
	 * Prompt the user to create objects directly from the pixels of an {@link ImageServer}.
	 * Often, the {@link ImageServer} has been created by applying a {@link PixelClassifier}.
	 * 
	 * @param imageData the {@link ImageData} to which objects should be added
	 * @param classifier the {@link ImageServer} used to generate objects
	 * @param classifierName the name of the classifier; if not null and the command runs to completion, it will be logged in the history 
	 * 						 workflow of the {@link ImageData} for later scripting.
	 * @return true if changes were made, false otherwise
	 */
	private static boolean promptToCreateObjects(ImageData<BufferedImage> imageData, PixelClassifier classifier, String classifierName) {
		Objects.requireNonNull(imageData);
		Objects.requireNonNull(classifier);


		// Check what is selected
		List<SelectionChoice> choices = buildChoiceList(imageData.getHierarchy(),
				SelectionChoice.FULL_IMAGE, SelectionChoice.CURRENT_SELECTION, SelectionChoice.ANNOTATIONS, SelectionChoice.TMA);
		SelectionChoice defaultChoice;
		if (choices.contains(SelectionChoice.CURRENT_SELECTION))
			defaultChoice = SelectionChoice.CURRENT_SELECTION;
		else if (choices.contains(SelectionChoice.ANNOTATIONS))
			defaultChoice = SelectionChoice.ANNOTATIONS;
		else 
			defaultChoice = choices.get(0);

		var parentChoice = Dialogs.showChoiceDialog("Pixel classifier",
				"Choose parent objects", choices, defaultChoice);
		if (parentChoice == null)
			return false;

		var outputObjectTypes = Arrays.asList(
				"Annotation", "Detection"
				);
		
		// To avoid confusing the user unnecessary, if we *only* have ignored classes then set default for includeIgnored to true
		boolean includeIgnored = false;
		var labels = classifier.getMetadata().getClassificationLabels();
		if (!labels.isEmpty() && labels.values().stream().allMatch(p -> p == null || PathClassTools.isIgnoredClass(p)))
			includeIgnored = true;

		var cal = imageData.getServer().getPixelCalibration();
		var units = cal.unitsMatch2D() ? cal.getPixelWidthUnit()+"^2" : cal.getPixelWidthUnit() + "x" + cal.getPixelHeightUnit();

		ParameterList params;
		if (lastCreateObjectParams != null) {
			params = lastCreateObjectParams.duplicate();
			params.setHiddenParameters(false, params.getKeyValueParameters(true).keySet().toArray(String[]::new));
			((BooleanParameter)params.getParameters().get("includeIgnored")).setValue(includeIgnored);
		} else {
			params = new ParameterList()
					.addChoiceParameter("objectType", "New object type", "Annotation", outputObjectTypes, "Define the type of objects that will be created")
					.addDoubleParameter("minSize", "Minimum object size", 0, units, "Minimum size of a region to keep (smaller regions will be dropped)")
					.addDoubleParameter("minHoleSize", "Minimum hole size", 0, units, "Minimum size of a hole to keep (smaller holes will be filled)")
					.addBooleanParameter("doSplit", "Split objects", false,
							"Split multi-part regions into separate objects")
					.addBooleanParameter("clearExisting", "Delete existing objects", false,
							"Delete any existing objects within the selected object before adding new objects (or entire image if no object is selected)")
					.addBooleanParameter("includeIgnored", "Create objects for ignored classes", includeIgnored,
							"Create objects for classifications that are usually ignored (e.g. \"Ignore*\", \"Region*\")")
					.addBooleanParameter("selectNew", "Set new objects to selected", false,
							"Set the newly-created objects to be selected");
		}
		
		if (!Dialogs.showParameterDialog("Create objects", params))
			return false;

		boolean createDetections = params.getChoiceParameterValue("objectType").equals("Detection");
		boolean doSplit = params.getBooleanParameterValue("doSplit");
		includeIgnored = params.getBooleanParameterValue("includeIgnored");
		double minSize = params.getDoubleParameterValue("minSize");
		double minHoleSize = params.getDoubleParameterValue("minHoleSize");
		boolean clearExisting = params.getBooleanParameterValue("clearExisting");
		boolean selectNew = params.getBooleanParameterValue("selectNew");

		lastCreateObjectParams = params;

		parentChoice.handleSelection(imageData);
		
		List<CreateObjectOptions> options = new ArrayList<>();
		if (doSplit)
			options.add(CreateObjectOptions.SPLIT);
		if (clearExisting)
			options.add(CreateObjectOptions.DELETE_EXISTING);
		if (includeIgnored)
			options.add(CreateObjectOptions.INCLUDE_IGNORED);
		if (selectNew)
			options.add(CreateObjectOptions.SELECT_NEW);
		
		var optionsArray = options.toArray(CreateObjectOptions[]::new);
		String optionsString = "";
		if (!options.isEmpty())
			optionsString = ", " + options.stream().map(o -> "\"" + o.name() + "\"").collect(Collectors.joining(", "));

		if (createDetections) {
			if (PixelClassifierTools.createDetectionsFromPixelClassifier(imageData, classifier, minSize, minHoleSize, optionsArray)) {
				if (classifierName != null) {
					imageData.getHistoryWorkflow().addStep(
							new DefaultScriptableWorkflowStep("Pixel classifier create detections",
									String.format("createDetectionsFromPixelClassifier(\"%s\", %s, %s)",
											classifierName,
											minSize,
											minHoleSize + optionsString)
									)
							);
				}
				return true;
			}
		} else {
			if (PixelClassifierTools.createAnnotationsFromPixelClassifier(imageData, classifier, minSize, minHoleSize, optionsArray)) {
				if (classifierName != null) {
					imageData.getHistoryWorkflow().addStep(
							new DefaultScriptableWorkflowStep("Pixel classifier create annotations",
									String.format("createAnnotationsFromPixelClassifier(\"%s\", %s, %s)",
											classifierName,
											minSize,
											minHoleSize + optionsString)
									)
							);
				}
				return true;
			}
		}
		return false;
	}
	
	
	
	
	
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
				return "All annotations";
			case CELLS:
				return "All cells";
			case CURRENT_SELECTION:
				return "Current selection";
			case DETECTIONS:
				return "All detections";
			case TMA:
				return "TMA cores";
			case FULL_IMAGE:
				return "Full image";
			case TILES:
				return "All tiles";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
	}
	

	
	
	private static List<SelectionChoice> buildChoiceList(PathObjectHierarchy hierarchy, SelectionChoice... validChoices) {
		List<SelectionChoice> choices = new ArrayList<>();
		if (!hierarchy.getSelectionModel().noSelection()) {
			choices.add(SelectionChoice.CURRENT_SELECTION);
		}
		choices.add(SelectionChoice.FULL_IMAGE);
		var classes = hierarchy.getFlattenedObjectList(null).stream().map(p -> p.getClass()).collect(Collectors.toSet());
		for (var choice : validChoices) {
			if (choice.getObjectClass() != null && classes.contains(choice.getObjectClass()))
					choices.add(choice);
		}
		return choices;
	}
	
	
	private static boolean promptToAddMeasurements(ImageData<BufferedImage> imageData, PixelClassifier classifier, String classifierName) {
		
		if (imageData == null) {
			Dialogs.showNoImageError("Pixel classifier");
			return false;
		}
		
		var hierarchy = imageData.getHierarchy();
		
		List<SelectionChoice> choices = buildChoiceList(imageData.getHierarchy(), SelectionChoice.values());
		SelectionChoice defaultChoice;
		if (choices.contains(SelectionChoice.CURRENT_SELECTION))
			defaultChoice = SelectionChoice.CURRENT_SELECTION;
		else if (choices.contains(SelectionChoice.ANNOTATIONS))
			defaultChoice = SelectionChoice.ANNOTATIONS;
		else 
			defaultChoice = choices.get(0);
			
		var params = new ParameterList()
				.addStringParameter("id", "Measurement name", classifierName == null ? "Classifier" : classifierName, "Choose a base name for measurements - this helps distinguish between measurements from different classifiers")
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
		
		if (PixelClassifierTools.addMeasurementsToSelectedObjects(imageData, classifier, measurementID)) {
			if (classifierName != null) {
				String idString = measurementID == null ? "null" : "\"" + measurementID + "\"";
				imageData.getHistoryWorkflow().addStep(
						new DefaultScriptableWorkflowStep("Pixel classifier measurements",
								String.format("addPixelClassifierMeasurements(\"%s\", %s)", classifierName, idString)
								)
						);
			}
			return true;
		}
		return false;
	}
	

}