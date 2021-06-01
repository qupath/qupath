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

package qupath.process.gui.commands;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers.ClassifyByMeasurementBuilder;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.HistogramPanelFX;
import qupath.lib.gui.charts.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.process.gui.ml.ProjectClassifierBindings;

/**
 * Command to (sub)classify objects based on a single measurement.
 * 
 * @author Pete Bankhead
 */
public class SingleMeasurementClassificationCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(SingleMeasurementClassificationCommand.class);
	
	private static String title = "Single measurement classifier";
	
	private QuPathGUI qupath;
	
	private Map<QuPathViewer, SingleMeasurementPane> paneMap = new WeakHashMap<>();
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public SingleMeasurementClassificationCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		var pane = paneMap.get(viewer);
		if (pane == null) {
			pane = new SingleMeasurementPane(qupath, viewer);
			paneMap.put(viewer, pane);
		}
		if (pane.dialog != null) {
			pane.dialog.getDialogPane().requestFocus();
		} else {
			pane.show();
		}
	}
	
	
	
	static class SingleMeasurementPane implements ChangeListener<ImageData<BufferedImage>> {
		
		private QuPathGUI qupath;
		private QuPathViewer viewer;
		
		private GridPane pane;

		private Predicate<String> ALWAYS_TRUE = m -> true;
		private String NO_CHANNEL_FILTER = "No filter (allow all channels)";
		private ComboBox<String> comboChannels = new ComboBox<>();

		private ComboBox<PathObjectFilter> comboFilter = new ComboBox<>();
		
		/**
		 * Map storing previous threshold values used for specific measurements.
		 */
		private Map<String, Double> previousThresholds = new HashMap<>();
		
		private ObservableList<String> measurements = FXCollections.observableArrayList();
		private FilteredList<String> measurementsFiltered = measurements.filtered(ALWAYS_TRUE);
		
		private ComboBox<String> comboMeasurements = new ComboBox<>(measurementsFiltered);
		
		private Slider sliderThreshold = new Slider();
		private ComboBox<PathClass> comboAbove = new ComboBox<>();
		private ComboBox<PathClass> comboBelow = new ComboBox<>();
		
		private CheckBox cbLivePreview = new CheckBox("Live preview");
		
		private HistogramPanelFX histogramPane = new HistogramPanelFX();
		
		private ClassificationRequest<BufferedImage> nextRequest;
		
		private TextField tfSaveName = new TextField();
		
		private Dialog<ButtonType> dialog;
		
		private StringProperty titleProperty = new SimpleStringProperty(title);
		
		private ExecutorService pool;
		
		SingleMeasurementPane(QuPathGUI qupath, QuPathViewer viewer) {
			this.qupath = qupath;
			this.viewer = viewer;
			
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
			
			// Initialize pane
			pane = new GridPane();
			pane.setHgap(5.0);
			pane.setVgap(5.0);
			
//			comboMeasurements.getEditor().textProperty().addListener((v, o, n) -> {
//				String text = n == null ? "" : n.toLowerCase().strip();
//				if (n.isEmpty())
//					measurementsFiltered.setPredicate(p -> true);
//				else
//					measurementsFiltered.setPredicate(p -> p.toLowerCase().contains(text));
//			});
			
			int row = 0;
			
			var labelFilter = new Label("Object filter");
			PaneTools.addGridRow(pane, row++, 0, "Select objects to classify", labelFilter, comboFilter, comboFilter);

			var labelChannels = new Label("Channel filter");
			PaneTools.addGridRow(pane, row++, 0, "Optionally filter measurement lists & classifications by channel name", labelChannels, comboChannels, comboChannels);

			var labelMeasurements = new Label("Measurement");
			PaneTools.addGridRow(pane, row++, 0, "Select measurement to threshold", labelMeasurements, comboMeasurements, comboMeasurements);
			
			var labelThreshold = new Label("Threshold");
			PaneTools.addGridRow(pane, row++, 0, "Select threshold value", labelThreshold, sliderThreshold, tf);

			var labelAbove = new Label("Above threshold");
			PaneTools.addGridRow(pane, row++, 0, "Specify the classification for objects above (or equal to) the threshold", labelAbove, comboAbove, comboAbove);

			var labelBelow = new Label("Below threshold");
			PaneTools.addGridRow(pane, row++, 0, "Specify the classification for objects below the threshold", labelBelow, comboBelow, comboBelow);

			PaneTools.addGridRow(pane, row++, 0, "Turn on/off live preview while changing settings", cbLivePreview, cbLivePreview, cbLivePreview);

			var btnSave = new Button("Save");
			btnSave.setOnAction(e -> {
				tryToSave();
				tfSaveName.requestFocus();
				btnSave.requestFocus();
			});
			var labelSave = new Label("Classifier name");
			tfSaveName.setMaxWidth(Double.MAX_VALUE);
			tfSaveName.setPromptText("Enter object classifier name");
			ProjectClassifierBindings.bindObjectClassifierNameInput(tfSaveName, qupath.projectProperty());
			btnSave.setMaxWidth(Double.MAX_VALUE);
			btnSave.disableProperty().bind(comboMeasurements.valueProperty().isNull().or(tfSaveName.textProperty().isEmpty()));
			PaneTools.addGridRow(pane, row++, 0, "Specify name of the classifier - this will be used to save to "
					+ "save the classifier in the current project, so it may be used for scripting later", labelSave, tfSaveName, btnSave);
			
			var chartWrapper = new ThresholdedChartWrapper(histogramPane.getChart());
			chartWrapper.setIsInteractive(true);
			chartWrapper.addThreshold(sliderThreshold.valueProperty(), Color.rgb(0, 0, 0, 0.2));
			
			histogramPane.getChart().getYAxis().setTickLabelsVisible(false);
			histogramPane.getChart().setAnimated(false);
			
			PaneTools.setToExpandGridPaneHeight(chartWrapper.getPane());
			PaneTools.setToExpandGridPaneWidth(chartWrapper.getPane());
			PaneTools.setToExpandGridPaneWidth(comboFilter, comboChannels, comboMeasurements, sliderThreshold, 
					comboAbove, comboBelow, tfSaveName, cbLivePreview);
			
			histogramPane.getChart().getYAxis().setTickLabelsVisible(false);
			histogramPane.getChart().setAnimated(false);
			chartWrapper.getPane().setPrefSize(200, 80);
			pane.add(chartWrapper.getPane(), pane.getColumnCount(), 0, 1, pane.getRowCount());
			
			// Add listeners
			comboChannels.valueProperty().addListener((v, o, n) -> updateChannelFilter());
			comboMeasurements.valueProperty().addListener((v, o, n) -> {
				if (o != null)
					previousThresholds.put(o, getThreshold());
				updateThresholdSlider();
				maybePreview();
			});
			sliderThreshold.valueProperty().addListener((v, o, n) -> maybePreview());
			comboAbove.valueProperty().addListener((v, o, n) -> maybePreview());
			comboBelow.valueProperty().addListener((v, o, n) -> maybePreview());
			cbLivePreview.selectedProperty().addListener((v, o, n) -> maybePreview());
		}
		
		
		void updateChannelFilter() {
			var selected = comboChannels.getSelectionModel().getSelectedItem();
			if (selected == null || selected.isBlank() || NO_CHANNEL_FILTER.equals(selected)) {
				measurementsFiltered.setPredicate(ALWAYS_TRUE);
			} else {
				var lowerSelected = selected.trim().toLowerCase();
				Predicate<String> predicate = m -> m.toLowerCase().contains(lowerSelected);
				if (measurements.stream().anyMatch(predicate))
					measurementsFiltered.setPredicate(predicate);
				else
					measurementsFiltered.setPredicate(ALWAYS_TRUE);
				
				if (comboMeasurements.getSelectionModel().getSelectedItem() == null && !comboMeasurements.getItems().isEmpty())
					comboMeasurements.getSelectionModel().selectFirst();
				
				var imageData = getImageData();
				var pathClass = qupath.getAvailablePathClasses().stream()
						.filter(p -> p.toString().toLowerCase().contains(lowerSelected))
						.findFirst().orElse(null);
				if (imageData != null && pathClass != null) {
//					if (imageData.isBrightfield()) {
					comboAbove.getSelectionModel().select(pathClass);
					comboBelow.getSelectionModel().select(null);
//					}
				}
				tfSaveName.setText(selected.trim());
			}
		}
		
		
		private Map<PathObjectHierarchy, Map<PathObject, PathClass>> mapPrevious = new WeakHashMap<>();
		
		/**
		 * Store the classifications for the current hierarchy, so these may be reset if the user cancels.
		 */
		void storeClassificationMap(PathObjectHierarchy hierarchy) {
			if (hierarchy == null)
				return;
			var pathObjects = hierarchy.getFlattenedObjectList(null);
			mapPrevious.put(
					hierarchy,
					PathClassifierTools.createClassificationMap(pathObjects)
					);
		}
		
		public void show() {
			var imageData = viewer.getImageData();
			if (imageData == null) {
				Dialogs.showNoImageError(title);
				return;
			}
			
			viewer.imageDataProperty().addListener(this);
			
			storeClassificationMap(imageData.getHierarchy());
			refreshOptions();
			
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("single-measurement-classifier", true));
			
			dialog = new Dialog<ButtonType>();
			dialog.initOwner(qupath.getStage());
			dialog.titleProperty().bind(titleProperty);
			dialog.getDialogPane().setContent(pane);
			dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
			dialog.initModality(Modality.NONE);
			
			dialog.getDialogPane().focusedProperty().addListener((v, o, n) -> {
				if (n)
					refreshTitle();
			});
			
			dialog.setOnCloseRequest(e -> {
				var applyClassifier = ButtonType.APPLY.equals(dialog.getResult());
				cleanup(applyClassifier);
			});			
			
			dialog.show();
			maybePreview();
		}
		
		
		/**
		 * Cleanup after the dialog is closed.
		 * @param applyLastClassifier
		 */
		void cleanup(boolean applyLastClassifier) {
			pool.shutdown();
			try {
				pool.awaitTermination(5000L, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.debug("Exception waiting for classification to complete: " + e.getLocalizedMessage(), e);
			}
			if (applyLastClassifier) {
				// Make sure we ran the last command, then log it in the workflow
				var nextRequest = getUpdatedRequest();
				if (nextRequest != null) {
					nextRequest.doClassification();
					String name = null;
					if (tfSaveName.getText() != null && !tfSaveName.getText().isEmpty())
						name = tryToSave();
					if (name == null) {
						Dialogs.showWarningNotification("Object classifier", "Classifier was not saved, so will not appear in the command history");
					} else {
						nextRequest.imageData.getHistoryWorkflow().addStep(ObjectClassifierLoadCommand.createObjectClassifierStep(name));
					}
				}
				// TODO: Log classification in the workflow?
			} else {
				// Restore classifications if the user cancelled
				for (var entry : mapPrevious.entrySet())
					resetClassifications(entry.getKey(), entry.getValue());
			}
			viewer.imageDataProperty().removeListener(this);
			dialog = null;
		}
		
		
		
		ImageData<BufferedImage> getImageData() {
			return viewer.getImageData();
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
		
		void refreshTitle() {
			var imageData = getImageData();
			var project = qupath.getProject();
			if (imageData == null)
				titleProperty.set(title);
			else {
				String imageName = null;
				if (project != null) {
					var entry = project.getEntry(imageData);
					if (entry != null)
						imageName = entry.getImageName();
				}
				if (imageName == null)
					imageName = imageData.getServer().getMetadata().getName();
				titleProperty.set(title + " (" + imageName + ")");
			}
		}
		
		/**
		 * Refresh all displayed options to match the current image
		 */
		void refreshOptions() {
			refreshTitle();
			refreshChannels();
			updateAvailableClasses();
			updateAvailableMeasurements();
			updateThresholdSlider();
		}
		
		void refreshChannels() {
			var list = new ArrayList<String>();
			list.add(NO_CHANNEL_FILTER);
			var imageData = getImageData();
			if (imageData != null) {
				var stains = imageData.getColorDeconvolutionStains();
				if (stains != null) {
					for (int s = 1; s <= 3; s++) {
						var stain = stains.getStain(s);
						if (!stain.isResidual())
							list.add(stain.getName());
					}
				}
				for (var channel : imageData.getServer().getMetadata().getChannels()) {
					list.add(channel.getName());
				}
			}
			comboChannels.getItems().setAll(list);
			if (comboChannels.getSelectionModel().getSelectedItem() == null)
				comboChannels.getSelectionModel().selectFirst();
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
			histogramPane.getHistogramData().setAll(HistogramPanelFX.createHistogramData(histogram, false, ColorTools.packARGB(100, 200, 20, 20)));
			
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
			this.measurements.setAll(measurements);
		}
		
		/**
		 * Try to save the classifier & return the name of the saved classifier if successful
		 * @return
		 */
		String tryToSave() {
			var project = qupath.getProject();
			if (project == null) {
				Dialogs.showErrorMessage(title, "You need a project to save this classifier!");
				return null;
			}
			var name = GeneralTools.stripInvalidFilenameChars(tfSaveName.getText());
			if (name.isBlank()) {
				Dialogs.showErrorMessage(title, "Please enter a name for the classifier!");
				return null;
			}
			var classifier = updateClassifier();
			if (classifier == null) {
				Dialogs.showErrorMessage(title, "Not enough information to create a classifier!");
				return null;
			}
			try {
				if (project.getObjectClassifiers().contains(name)) {
					if (!Dialogs.showConfirmDialog(title, "Do you want to overwrite the existing classifier '" + name + "'?"))
						return null;
				}
				project.getObjectClassifiers().put(name, classifier);
				Dialogs.showInfoNotification(title, "Saved classifier as '" + name + "'");
				return name;
			} catch (Exception e) {
				Dialogs.showErrorNotification(title, e);
				return null;
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
			if (classifier == null)
				return null;
			return new ClassificationRequest<>(imageData, classifier);
		}
		
		ObjectClassifier<BufferedImage> updateClassifier() {
			var filter = comboFilter.getValue();
			var measurement = getSelectedMeasurement();
			var threshold = getThreshold();
			var classAbove = comboAbove.getValue();
			var classBelow = comboBelow.getValue();
			var classEquals = classAbove; // We use >= and if this changes the tooltip must change too!
			
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

		@Override
		public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
				ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
			
			if (newValue != null && ! mapPrevious.containsKey(newValue.getHierarchy()))
				storeClassificationMap(newValue.getHierarchy());
			
			refreshOptions();
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