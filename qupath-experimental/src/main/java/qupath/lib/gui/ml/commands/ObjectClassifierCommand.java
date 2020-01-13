/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.ml.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.ml.ClassificationPieChart;
import qupath.lib.gui.panels.classify.PathClassificationLabellingHelper;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.objects.OpenCVMLClassifier;


/**
 * Command used to create and show a suitable dialog box for interactive display of OpenCV classifiers.
 * <p>
 * This is intended as a replacement for 'Create detection classifier' in QuPath v0.1.2, supporting better 
 * classifier options and serialization.
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectClassifierCommand implements PathCommand {
	
	final private static String name = "Train detection classifier";
	
	private QuPathGUI qupath;
	
	// TODO: Check use of static dialog
	private Stage dialog;
//	private ClassifierBuilderPanel<PathObjectClassifier> panel;

	
	public ObjectClassifierCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		dialog = null;
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
			dialog.setTitle(name);
			
			BorderPane pane = new BorderPane();
			var panel = new ObjectClassifierPane(qupath);
			pane.setCenter(panel.getPane());
//			List<PathObjectClassifier> classifiers = OpenCVMLClassifier.createDefaultClassifiers();
//			panel = new ClassifierBuilderPanel<>(qupath, classifiers, classifiers.get(0));
//			pane.setCenter(panel.getPane());
			
			ScrollPane scrollPane = new ScrollPane(pane);
			scrollPane.setFitToWidth(true);
			scrollPane.setFitToHeight(true);
			dialog.setScene(new Scene(scrollPane));
		}
		
		dialog.setOnCloseRequest(e -> {
//			// If we don't have a classifier yet, just remove completely
//			if (panel.getSelectedFeatures().isEmpty()) {
//				resetPanel();
//				return;
//			}
//			
//			// If we have a classifier, give option to hide
//			DialogButton button = Dialogs.showYesNoCancelDialog("Classifier builder", "Retain classifier for later use?");
//			if (button == DialogButton.CANCEL)
//				e.consume();
//			else if (button == DialogButton.NO) {
//				resetPanel();
//			}
		});
		
		dialog.sizeToScene();
		
		dialog.show();
	}
	
	
//	/**
//	 * Handle cleanup whenever a dialog should be closed (and forgotten)
//	 */
//	private void resetPanel() {
//		if (panel == null)
//			return;
//		qupath.removeImageDataChangeListener(panel);
//		panel.setImageData(qupath.getImageData(), null);
//		if (dialog != null)
//			dialog.setOnCloseRequest(null);
//		dialog = null;
//		panel = null;
//	}
	
	
	
	static class ObjectClassifierPane {
		
		private final static Logger logger = LoggerFactory.getLogger(ObjectClassifierPane.class);
		
		private QuPathGUI qupath;
		
		private GridPane pane;
		
		private ReadOnlyObjectProperty<OpenCVStatModel> selectedClassifier;
		
		private OpenCVMLClassifier classifier;
		private ObservableList<PathClass> selectedClasses;
		
		private StringProperty cursorLocation = new SimpleStringProperty();
		
		private BooleanProperty livePrediction = new SimpleBooleanProperty(false);
		
		private ClassificationPieChart pieChart;
		
		ObjectClassifierPane(QuPathGUI qupath) {
			this.qupath = qupath;
			initialize();
		}
		
		private void updateClassifier() {
			updateClassifier(livePrediction.get());
		}
		
		public Pane getPane() {
			return pane;
		}
		
		private void updateClassifier(boolean doClassification) {
			
			var temp = selectedClassifier == null ? null : selectedClassifier.get();
			if (temp == null)
				classifier = null;
			else
				classifier = OpenCVMLClassifier.create(temp);
			if (classifier == null) {
				pieChart.setData(Collections.emptyMap(), false);
				return;
			}
			
			var imageData = qupath.getImageData();
			if (imageData == null) {
				logger.warn("No image - cannot update classifier");
				pieChart.setData(Collections.emptyMap(), false);
				return;
			}
			var detections = imageData.getHierarchy().getDetectionObjects();
			var measurements = PathClassifierTools.getAvailableFeatures(detections);
			if (measurements.isEmpty()) {
				logger.warn("No measurements - cannot update classifier");
				pieChart.setData(Collections.emptyMap(), false);
				return;
			}
			
			boolean trainFromPoints = false;
			var map = PathClassificationLabellingHelper.getClassificationMap(imageData.getHierarchy(), trainFromPoints);
			
			var iterator = map.entrySet().iterator();
			while (iterator.hasNext()) {
				if (!selectedClasses.contains(iterator.next().getKey()))
					iterator.remove();
			}
			
			classifier.updateClassifier(map, new ArrayList<>(measurements), Normalization.NONE);
			
			var counts = new LinkedHashMap<PathClass, Integer>();
			for (var entry : map.entrySet()) {
				counts.put(entry.getKey(), entry.getValue().size());
			}
			pieChart.setData(counts, true);
			
			if (doClassification) {
				if (classifier.classifyPathObjects(detections) > 0)
					imageData.getHierarchy().fireObjectClassificationsChangedEvent(this, detections);
			}
			
		}
		
		private boolean showAdvancedOptions() {
			Dialogs.showErrorNotification("Advanced options", "Not implemented!");
			return false;
		}
		
		private boolean saveAndApply() {
			Dialogs.showErrorNotification("Advanced options", "Not implemented!");
			return false;
		}
		
		private boolean editClassifierParameters() {
			var model = selectedClassifier.get();
			if (model == null) {
				Dialogs.showErrorMessage("Edit parameters", "No classifier selected!");
				return false;
			}
			Dialogs.showParameterDialog("Edit parameters", model.getParameterList());
			updateClassifier();
			return true;
		}
		
		private void initialize() {
			
			int row = 0;
			
			// Classifier
			pane = new GridPane();

			var labelClassifier = new Label("Classifier");
			var comboClassifier = new ComboBox<OpenCVStatModel>();
			comboClassifier.getItems().addAll(
					OpenCVClassifiers.createStatModel(RTrees.class),
					OpenCVClassifiers.createStatModel(ANN_MLP.class),
					OpenCVClassifiers.createStatModel(KNearest.class)
					);
			labelClassifier.setLabelFor(comboClassifier);
			
			selectedClassifier = comboClassifier.getSelectionModel().selectedItemProperty();
			comboClassifier.getSelectionModel().selectFirst();
			selectedClassifier.addListener((v, o, n) -> updateClassifier());
			var btnEditClassifier = new Button("Edit");
			btnEditClassifier.setOnAction(e -> editClassifierParameters());
			btnEditClassifier.disableProperty().bind(selectedClassifier.isNull());
			
			PaneTools.addGridRow(pane, row++, 0, 
					"Choose classifier type (RTrees or ANN_MLP are generally good choices)",
					labelClassifier, comboClassifier, btnEditClassifier);
			
//			// TODO: Enable feature selection
//			var labelFeatures = new Label("Features");
//			var comboFeatures = new ComboBox<FeatureCalculatorBuilder>();
//			comboFeatures.getItems().add(new FeatureCalculatorBuilder.DefaultFeatureCalculatorBuilder(viewer.getImageData()));
//			comboFeatures.getItems().add(new FeatureCalculatorBuilder.ExtractNeighborsFeatureCalculatorBuilder(viewer.getImageData()));
//			labelFeatures.setLabelFor(comboFeatures);
//			selectedFeatureCalculatorBuilder = comboFeatures.getSelectionModel().selectedItemProperty();
//			
////			var labelFeaturesSummary = new Label("No features selected");
//			var btnShowFeatures = new Button("Show");
//			btnShowFeatures.setOnAction(e -> showFeatures());
//			
//			var btnCustomizeFeatures = new Button("Edit");
//			btnCustomizeFeatures.disableProperty().bind(Bindings.createBooleanBinding(() -> {
//				var calc = selectedFeatureCalculatorBuilder.get();
//				return calc == null || !calc.canCustomize(viewer.getImageData());
//			},
//					selectedFeatureCalculatorBuilder));
//			btnCustomizeFeatures.setOnAction(e -> {
//				if (selectedFeatureCalculatorBuilder.get().doCustomize(viewer.getImageData())) {
//					updateFeatureCalculator();
//				}
//			});
//			comboFeatures.getItems().addAll(defaultFeatureCalculatorBuilders);
//			
//			comboFeatures.getSelectionModel().select(0);
//			comboFeatures.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateFeatureCalculator());
//			
//			PaneTools.addGridRow(pane, row++, 0, 
//					"Select features for the classifier",
//					labelFeatures, comboFeatures, btnCustomizeFeatures, btnShowFeatures);
			
			// Output
			var labelOutput = new Label("Classes");
			var comboOutput = new CheckComboBox<PathClass>(QuPathGUI.getInstance().getAvailablePathClasses());
			comboOutput.getCheckModel().checkAll();
			selectedClasses = comboOutput.getCheckModel().getCheckedItems();
			selectedClasses.addListener((Change<? extends PathClass> c) -> {
				updateClassifier();
			});
			
			PaneTools.addGridRow(pane, row++, 0, 
					"Choose which classes to use when training the classifier - annotations with other classifications will be ignored",
					labelOutput, comboOutput, comboOutput);
			
			// Live predict
			var btnAdvancedOptions = new Button("Advanced options");
			btnAdvancedOptions.setTooltip(new Tooltip("Advanced options to customize preprocessing and classifier behavior"));
			btnAdvancedOptions.setOnAction(e -> {
				if (showAdvancedOptions())
					updateClassifier();
			});
			
			var btnLive = new ToggleButton("Live prediction");
			btnLive.selectedProperty().bindBidirectional(livePrediction);
			btnLive.setTooltip(new Tooltip("Toggle whether to calculate classification 'live' while viewing the image"));
			livePrediction.addListener((v, o, n) -> {
				if (n) {
					updateClassifier(n);				
					return;
				}
			});
			
			var panePredict = PaneTools.createColumnGridControls(btnAdvancedOptions, btnLive);
			pane.add(panePredict, 0, row++, pane.getColumnCount(), 1);
			
//			addGridRow(pane, row++, 0, btnPredict, btnPredict, btnPredict);

			var btnSave = new Button("Save");
			btnSave.setMaxWidth(Double.MAX_VALUE);
			btnSave.setOnAction(e -> saveAndApply());
			pane.add(btnSave, 0, row++, pane.getColumnCount(), 1);
			
			
			pieChart = new ClassificationPieChart();
			
			var chart = pieChart.getChart();
			chart.setLabelsVisible(false);
			chart.setLegendVisible(true);
			chart.setPrefSize(40, 40);
			chart.setMaxSize(100, 100);
			chart.setLegendSide(Side.RIGHT);
			GridPane.setVgrow(chart, Priority.ALWAYS);
			Tooltip.install(chart, new Tooltip("View training classes by proportion"));
			pane.add(chart, 0, row++, pane.getColumnCount(), 1);
			
			// Label showing cursor location
			var labelCursor = new Label();
			labelCursor.textProperty().bindBidirectional(cursorLocation);
			labelCursor.setMaxWidth(Double.MAX_VALUE);
			labelCursor.setAlignment(Pos.CENTER);
			labelCursor.setTooltip(new Tooltip("Prediction for current cursor location"));
			pane.add(labelCursor, 0, row++, pane.getColumnCount(), 1);
						
			PaneTools.setMaxWidth(Double.MAX_VALUE, comboClassifier, comboOutput, panePredict);
			PaneTools.setHGrowPriority(Priority.ALWAYS, comboClassifier, comboOutput, panePredict);
			PaneTools.setFillWidth(Boolean.TRUE, comboClassifier, comboOutput, panePredict);

//			pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			pane.setHgap(5);
			pane.setVgap(6);
			
//			var btnCreateObjects = new Button("Create objects");
//			btnCreateObjects.setTooltip(new Tooltip("Create annotations or detections from pixel classification"));
//			btnCreateObjects.disableProperty().bind(classificationComplete);
//			btnCreateObjects.setOnAction(e -> {
//				var server = getClassificationServerOrShowError();
//				var imageData = viewer.getImageData();
//				if (imageData != null && server != null)
//					promptToCreateObjects(imageData, server);
//			});
//			
//			var btnClassifyObjects = new Button("Classify detections");
//			btnClassifyObjects.setTooltip(new Tooltip("Assign classifications to detection objects based on the corresponding pixel classification"));
//			btnClassifyObjects.disableProperty().bind(classificationComplete);
//			btnClassifyObjects.setOnAction(e -> classifyObjects());
//			
//			var panePostProcess = PaneTools.createColumnGridControls(btnCreateObjects, btnClassifyObjects);
//					
//			pane.add(panePostProcess, 0, row++, pane.getColumnCount(), 1);
//
//			PaneTools.setMaxWidth(Double.MAX_VALUE, pane.getChildren().stream().filter(p -> p instanceof Region).toArray(Region[]::new));
			
			pane.setPadding(new Insets(5));
			
//			stage = new Stage();
//			stage.setScene(new Scene(pane));
//			
//			stage.setMinHeight(400);
//			stage.setMinWidth(500);
//
//			stage.initOwner(QuPathGUI.getInstance().getStage());
//			
//			stage.getScene().getRoot().disableProperty().bind(
//					QuPathGUI.getInstance().viewerProperty().isNotEqualTo(viewer)
//					);
//			
//			updateTitle();
//			
//			updateFeatureCalculator();
//			
////			pane.getChildren().stream().forEach(c -> {
////				if (c instanceof Control)
////					((Control)c).setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
////			});
//			PaneTools.setMinWidth(
//					Region.USE_PREF_SIZE,
//					PaneTools.getContentsOfType(stage.getScene().getRoot(), Region.class, true).toArray(Region[]::new));
//			
//			stage.show();
//			stage.setOnCloseRequest(e -> destroy());
//			
//			viewer.getView().addEventFilter(MouseEvent.MOUSE_MOVED, mouseListener);
//			
//			viewer.getImageDataProperty().addListener(imageDataListener);
//			if (viewer.getImageData() != null)
//				viewer.getImageData().getHierarchy().addPathObjectListener(hierarchyListener);
			
		}
		
	}
	
	
}
