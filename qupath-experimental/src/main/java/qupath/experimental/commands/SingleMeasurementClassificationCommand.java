package qupath.experimental.commands;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers.ClassifyByMeasurementBuilder;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.plots.HistogramPanelFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Command to (sub)classify objects based on a single measurement.
 * 
 * @author Pete Bankhead
 */
public class SingleMeasurementClassificationCommand implements PathCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(SingleMeasurementClassificationCommand.class);
	
	private static String title = "Create measurement classifier";
	
	private QuPathGUI qupath;
	
	private SingleMeasurementPane pane;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public SingleMeasurementClassificationCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (pane == null)
			pane = new SingleMeasurementPane(qupath);
		pane.show();
	}
	
	
	
	static class SingleMeasurementPane {
		
		private QuPathGUI qupath;
		
		private GridPane pane;
		
		private ComboBox<PathObjectFilter> comboFilter = new ComboBox<>();
		
		/**
		 * Map storing previous threshold values used for specific measurements.
		 */
		private Map<String, Double> previousThresholds = new HashMap<>();
		
		private ComboBox<String> comboMeasurements = new ComboBox<>();
		private Slider sliderThreshold = new Slider();
		private ComboBox<PathClass> comboAbove = new ComboBox<>();
		private ComboBox<PathClass> comboBelow = new ComboBox<>();
		
		private CheckBox cbLivePreview = new CheckBox("Live preview");
		
		private HistogramPanelFX histogramPane = new HistogramPanelFX();
		
		private ClassificationRequest<BufferedImage> nextRequest;
		
		private TextField tfSaveName = new TextField();
		
		private ExecutorService pool;
		
		SingleMeasurementPane(QuPathGUI qupath) {
			this.qupath = qupath;
			
			// Object selection filter
			this.comboFilter.getItems().setAll(
					PathObjectFilter.DETECTIONS_ALL,
					PathObjectFilter.DETECTIONS,
					PathObjectFilter.CELLS,
					PathObjectFilter.TILES
					);
			comboFilter.getSelectionModel().select(PathObjectFilter.DETECTIONS_ALL);
			
			// Set up text fields
			var tf = new TextField();
			tf.setPrefColumnCount(6);
			GuiTools.bindSliderAndTextField(sliderThreshold, tf);
			tfSaveName.setPromptText("Enter name to save classifier if needed");
			
			// Initialize pane
			pane = new GridPane();
			pane.setHgap(5.0);
			pane.setVgap(5.0);
			
			int row = 0;
			var labelFilter = new Label("Objects");
			PaneTools.addGridRow(pane, row++, 0, "Select objects to classify", labelFilter, comboFilter, comboFilter);
			
			var labelMeasurements = new Label("Measurement");
			PaneTools.addGridRow(pane, row++, 0, "Select measurement to threshold", labelMeasurements, comboMeasurements, comboMeasurements);
			
			var labelThreshold = new Label("Threshold");
			PaneTools.addGridRow(pane, row++, 0, "Select threshold value", labelThreshold, sliderThreshold, tf);

			var labelAbove = new Label("Above threshold");
			PaneTools.addGridRow(pane, row++, 0, "Specify the classification for objects above the threshold", labelAbove, comboAbove, comboAbove);

			var labelBelow = new Label("Below threshold");
			PaneTools.addGridRow(pane, row++, 0, "Specify the classification for objects below the threshold", labelBelow, comboBelow, comboBelow);

			PaneTools.addGridRow(pane, row++, 0, "Turn on/off live preview while changing settings", cbLivePreview, cbLivePreview, cbLivePreview);

			var btnSave = new Button("Save");
			btnSave.setOnAction(e -> tryToSave());
			var labelSave = new Label("Classifier name");
			btnSave.disableProperty().bind(comboMeasurements.valueProperty().isNull().or(tfSaveName.textProperty().isEmpty()));
			PaneTools.addGridRow(pane, row++, 0, "Specify classifierName", labelSave, tfSaveName, btnSave);
			
			pane.add(histogramPane.getChart(), pane.getColumnCount(), 0, 1, pane.getRowCount());
			
			// Set sizes
			histogramPane.getChart().setPrefWidth(140);
			histogramPane.getChart().setPrefHeight(80);
			histogramPane.getChart().getYAxis().setTickLabelsVisible(false);
			histogramPane.getChart().setAnimated(false);
			PaneTools.setToExpandGridPaneWidth(comboFilter, comboMeasurements, sliderThreshold, comboAbove, comboBelow, tfSaveName, cbLivePreview);
			PaneTools.setToExpandGridPaneHeight(histogramPane.getChart());
			
			// Add listeners
			comboMeasurements.valueProperty().addListener((v, o, n) -> {
				if (o != null)
					previousThresholds.put(o, getThreshold());
				updateThresholdSlider();
				maybePreview();
			});
			sliderThreshold.valueProperty().addListener((v, o, n) -> maybePreview());
			comboAbove.valueProperty().addListener((v, o, n) -> maybePreview());
			comboBelow.valueProperty().addListener((v, o, n) -> maybePreview());
		}
		
		public void show() {
			var imageData = qupath.getImageData();
			if (imageData == null) {
				Dialogs.showNoImageError(title);
				return;
			}
			
			var pathObjects = imageData.getHierarchy().getFlattenedObjectList(null);
			Map<PathObject, PathClass> mapPrevious = PathClassifierTools.createClassificationMap(pathObjects);

			refreshOptions();
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("single-measurement-classifier", true));
			
			var dialog = new Dialog<ButtonType>();
			dialog.initOwner(qupath.getStage());
			dialog.setTitle(title);
			dialog.getDialogPane().setContent(pane);
			dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
			var response = dialog.showAndWait().orElse(ButtonType.CANCEL);
					
			pool.shutdown();
			try {
				pool.awaitTermination(5000L, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.debug("Exception waiting for classification to complete: " + e.getLocalizedMessage(), e);
			}
			
			// Check if we did anything, if not return
			if (nextRequest == null)
				return;
			
			if (ButtonType.APPLY.equals(response)) {
				// Make sure we ran the last command, then log it in the workflow
				var nextRequest = getUpdatedRequest();
				if (nextRequest != null)
					nextRequest.doClassification();
				// TODO: Log classification in the workflow?
//				imageData.getHistoryWorkflow().addStep(nextRequest.toWorkflowStep());
			} else {
				// Restore classifications if the user cancelled
				resetClassifications(imageData.getHierarchy(), mapPrevious);
			}
		}
		
		
		ImageData<BufferedImage> getImageData() {
			return qupath.getImageData();
		}
		
		PathObjectHierarchy getHierarchy() {
			var imageData = getImageData();
			return imageData == null ? null : imageData.getHierarchy();
		}
		
		/**
		 * Get objects for which the classification should be applied (depending upon image and filter).
		 * @return
		 */
		Collection<? extends PathObject> getCurrentObjects() {
			var hierarchy = getHierarchy();
			if (hierarchy == null)
				return Collections.emptyList();
			var filter = comboFilter.getValue();
			var pathObjects = hierarchy.getFlattenedObjectList(new ArrayList<>());
			if (filter != null)
				pathObjects.removeIf(filter.negate());
			return pathObjects;
		}
		
		String getSelectedMeasurement() {
			return comboMeasurements.getSelectionModel().getSelectedItem();
		}
		
		double getThreshold() {
			return sliderThreshold.getValue();
		}
		
		void refreshOptions() {
			updateAvailableClasses();
			updateAvailableMeasurements();
			updateThresholdSlider();
		}
		
		void updateAvailableClasses() {
			comboAbove.getItems().setAll(qupath.getAvailablePathClasses());
			comboBelow.getItems().setAll(qupath.getAvailablePathClasses());
		}
		
		void resetClassifications(PathObjectHierarchy hierarchy, Map<PathObject, PathClass> mapPrevious) {
			// Restore classifications if the user cancelled
			var changed = PathClassifierTools.restoreClassificationsFromMap(mapPrevious);
			if (hierarchy != null && !changed.isEmpty())
				hierarchy.fireObjectClassificationsChangedEvent(this, changed);
		}
		
		void updateThresholdSlider() {
			var measurement = getSelectedMeasurement();
			var pathObjects = getCurrentObjects();
			if (measurement == null || pathObjects.isEmpty()) {
				sliderThreshold.setMin(0);
				sliderThreshold.setMax(1);
				sliderThreshold.setValue(0);
				return;
			}
			double[] allValues = pathObjects.stream().mapToDouble(p -> p.getMeasurementList().getMeasurementValue(measurement))
					.filter(d -> Double.isFinite(d)).toArray();
			var stats = new DescriptiveStatistics(allValues);
			var histogram = new Histogram(allValues, 100, stats.getMin(), stats.getMax());
			histogramPane.getHistogramData().setAll(HistogramPanelFX.createHistogramData(histogram, false, ColorTools.makeRGBA(200, 20, 20, 100)));
			
			double value = previousThresholds.getOrDefault(measurement, stats.getMean());
			sliderThreshold.setMin(stats.getMin());
			sliderThreshold.setMax(stats.getMax());
			sliderThreshold.setValue(value);
		}
		
		/**
		 * Update measurements according to current image and filter.
		 */
		void updateAvailableMeasurements() {
			var measurements = PathClassifierTools.getAvailableFeatures(getCurrentObjects());
			comboMeasurements.getItems().setAll(measurements);
		}
		
		void tryToSave() {
			var project = qupath.getProject();
			if (project == null) {
				Dialogs.showErrorMessage(title, "You need a project to save this classifier!");
				return;
			}
			var name = GeneralTools.stripInvalidFilenameChars(tfSaveName.getText());
			if (name.isBlank()) {
				Dialogs.showErrorMessage(title, "Please enter a name for the classifier!");
				return;
			}
			var classifier = updateClassifier();
			if (classifier == null) {
				Dialogs.showErrorMessage(title, "Not enough information to create a classifier!");
				return;
			}
			try {
				if (project.getObjectClassifiers().getNames().contains(name)) {
					if (!Dialogs.showConfirmDialog(title, "Do you want to overwrite the existing classifier '" + name + "'?"))
						return;
				}
				project.getObjectClassifiers().put(name, classifier);
				Dialogs.showInfoNotification(title, "Saved classifier as '" + name + "'");
			} catch (Exception e) {
				Dialogs.showErrorNotification(title, e);
			}
		}
		
		/**
		 * Request a preview of the classification update, asynchronously.
		 * This will only have an effect if there is an {@link ExecutorService} ready (and thereby may be suppressed during initialization 
		 * by delaying the creation of the service), and also if 'live preview' is selected.
		 */
		void maybePreview() {
			if (!cbLivePreview.isSelected() || pool == null || pool.isShutdown())
				return;

			nextRequest = getUpdatedRequest();
			pool.execute(() -> processRequest());
		}
				
		ClassificationRequest<BufferedImage> getUpdatedRequest() {
			var imageData = getImageData();
			if (imageData == null) {
				return null;
			}
			var classifier = updateClassifier();
			return new ClassificationRequest<>(imageData, classifier);
		}
		
		ObjectClassifier<BufferedImage> updateClassifier() {
			var filter = comboFilter.getValue();
			var measurement = getSelectedMeasurement();
			var threshold = getThreshold();
			var classAbove = comboAbove.getValue();
			var classBelow = comboBelow.getValue();
			var classEquals = classAbove;
			
			if (measurement == null || Double.isNaN(threshold))
				return null;
			
			return new ClassifyByMeasurementBuilder<BufferedImage>(measurement)
					.threshold(threshold)
					.filter(filter)
					.above(classAbove)
					.equalTo(classEquals)
					.below(classBelow)
					.build();
		}
		
		synchronized void processRequest() {
			if (nextRequest == null || nextRequest.isComplete())
				return;
			nextRequest.doClassification();
		}
		
		
	}
	
	
	/**
	 * Encapsulate the requirements for a intensity classification into a single object.
	 */
	static class ClassificationRequest<T> {
		
		private ImageData<T> imageData;
		private ObjectClassifier<T> classifier;
		
		private boolean isComplete = false;
		
		ClassificationRequest(ImageData<T> imageData, ObjectClassifier<T> classifier) {
			this.imageData = imageData;
			this.classifier = classifier;
		}
		
		public synchronized void doClassification() {
			var pathObjects = classifier.getCompatibleObjects(imageData);
			classifier.classifyObjects(imageData, pathObjects, true);
			imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
			isComplete = true;
		}
		
		public synchronized boolean isComplete() {
			return isComplete;
		}
		
	}
	

}
