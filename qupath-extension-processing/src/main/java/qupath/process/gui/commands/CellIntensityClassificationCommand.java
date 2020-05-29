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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.common.ColorTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.HistogramPanelFX;
import qupath.lib.gui.charts.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;

/**
 * Command to (sub)classify cells as Negative/Positive or Negative/1+/2+/3+ based on a single (usually intensity-based) measurement.
 * 
 * @author Pete Bankhead
 */
public class CellIntensityClassificationCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(CellIntensityClassificationCommand.class);
	
	private static String title = "Cell intensity classification";
	
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public CellIntensityClassificationCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
//		Dialogs.showErrorNotification(title, "Not implemented yet!");
		
		var imageData = qupath.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}
		var hierarchy = imageData.getHierarchy();
		
		// Try to operate on cells, but accept operating on all detections if necessary
		var cells = imageData.getHierarchy().getCellObjects();
		boolean allDetections = cells.isEmpty();
		if (allDetections)
			logger.debug("No cells found - will try using all detections");
		var detections = allDetections ? imageData.getHierarchy().getDetectionObjects() : cells;
		if (detections.isEmpty()) {
			Dialogs.showErrorMessage(title, "No cells found in the current image!");
			return;
		}
		var measurements = PathClassifierTools.getAvailableFeatures(detections);
		if (measurements.isEmpty()) {
			Dialogs.showErrorMessage(title, "No cell measurements found in the current image!");
			return;
		}

		var currentClassifications = PathClassifierTools.createClassificationMap(detections);
		
		var comboMeasurements = new ComboBox<String>();
		comboMeasurements.getItems().setAll(measurements);
		PaneTools.setToExpandGridPaneWidth(comboMeasurements);
		var selectedMeasurement = comboMeasurements.getSelectionModel().selectedItemProperty();
		
		var cbSingleThreshold = new CheckBox("Single threshold");
		cbSingleThreshold.setSelected(true);
		var singleThreshold = cbSingleThreshold.selectedProperty();
		
		var sliders = new ArrayList<Slider>();
		var textFields = new ArrayList<TextField>();
		for (int i = 0; i < 3; i++) {
			var slider = new Slider();
			var tf = new TextField();
			tf.setPrefColumnCount(6);
			textFields.add(tf);
			GuiTools.bindSliderAndTextField(slider, tf);
			slider.valueProperty().addListener((v, o, n) -> {
				updateClassifications(hierarchy, allDetections, selectedMeasurement.get(), parseValues(sliders, singleThreshold.get()));
			});
			PaneTools.setToExpandGridPaneWidth(slider);
			sliders.add(slider);
		}
		
		var map = new HashMap<String, double[]>();
		
		var histogramPanel = new HistogramPanelFX();
		var chartWrapper = new ThresholdedChartWrapper(histogramPanel.getChart());
		chartWrapper.setIsInteractive(true);
		
		singleThreshold.addListener((v, o, n) -> {
			chartWrapper.clearThresholds();
			Color color = Color.rgb(0, 0, 0, 0.2);
			if (!n) {
				for (int i = 0; i < sliders.size(); i++) {
					chartWrapper.addThreshold(sliders.get(i).valueProperty(), color);
				}
			} else
				chartWrapper.addThreshold(sliders.get(0).valueProperty(), color);
		});

		selectedMeasurement.addListener((v, o, n) -> {
			if (o != null)
				map.put(o, parseValues(sliders));
			
			double[] measurementValues = detections.stream().mapToDouble(p -> p.getMeasurementList().getMeasurementValue(n))
					.filter(d -> Double.isFinite(d)).toArray();
			var stats = new DescriptiveStatistics(measurementValues);
			var histogram = new Histogram(measurementValues, 100, stats.getMin(), stats.getMax());
			histogramPanel.getHistogramData().setAll(HistogramPanelFX.createHistogramData(histogram, false, ColorTools.makeRGBA(200, 20, 20, 100)));
			
			double[] values = map.get(n);
			for (int i = 0; i < sliders.size(); i++) {
				var slider = sliders.get(i);
				slider.setMin(stats.getMin());
				slider.setMax(stats.getMax());
				double val = values == null ? stats.getMean() + stats.getStandardDeviation() * i : values[i];
				slider.setValue(val);
				
				// Add first threshold to histogram
				if (i == 0) {
					Color color = Color.rgb(0, 0, 0, 0.2);
					chartWrapper.addThreshold(sliders.get(i).valueProperty(), color);
				}
			}
		});
				
		selectedMeasurement.addListener((v, o, n) -> updateClassifications(hierarchy, allDetections, n, parseValues(sliders, singleThreshold.get())));
		singleThreshold.addListener((v, o, n) -> updateClassifications(hierarchy, allDetections, selectedMeasurement.get(), parseValues(sliders, singleThreshold.get())));
		
		var pane = new GridPane();
		int row = 0;
		var labelMeasurements = new Label("Measurement");
		PaneTools.addGridRow(pane, row++, 0, "Select measurement to threshold", labelMeasurements, comboMeasurements, comboMeasurements);
		
		for (int i = 0; i < sliders.size(); i++) {
			var labelThreshold = new Label("Threshold " + (i+1) + "+");
			var slider = sliders.get(i);
			var tf = textFields.get(i);
			if (i > 0) {
				slider.disableProperty().bind(singleThreshold);
				tf.disableProperty().bind(singleThreshold);
			}
			PaneTools.addGridRow(pane, row++, 0, "Select threshold value", labelThreshold, slider, tf);
		}
		PaneTools.addGridRow(pane, row++, 0, "Toggle between using a single threshold (Negative/Positive) or three threshold Negative/1+/2+/3+)", cbSingleThreshold, cbSingleThreshold, cbSingleThreshold);
		pane.setHgap(5.0);
		pane.setVgap(5.0);
				
		PaneTools.setToExpandGridPaneHeight(chartWrapper.getPane());
		PaneTools.setToExpandGridPaneWidth(chartWrapper.getPane());
		histogramPanel.getChart().getYAxis().setTickLabelsVisible(false);
		histogramPanel.getChart().setAnimated(false);
		chartWrapper.getPane().setPrefSize(200, 80);
		pane.add(chartWrapper.getPane(), pane.getColumnCount(), 0, 1, pane.getRowCount());
		
		var dialog = new Dialog<ButtonType>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle(title);
		dialog.getDialogPane().setContent(pane);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
		var response = dialog.showAndWait().orElse(ButtonType.CANCEL);
				
		if (pool != null) {
			pool.shutdown();
			try {
				pool.awaitTermination(5000L, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.debug("Exception waiting for classification to complete: " + e.getLocalizedMessage(), e);
			}
		}
		
		// Check if we did anything, if not return
		if (nextRequest == null)
			return;
		
		if (ButtonType.APPLY.equals(response)) {
			// Make sure we ran the last command, then log it in the workflow
			if (!nextRequest.isComplete())
				nextRequest.doClassification();
			imageData.getHistoryWorkflow().addStep(nextRequest.toWorkflowStep());
		} else {
			// Restore classifications if the user cancelled
			var changed = PathClassifierTools.restoreClassificationsFromMap(currentClassifications);
			if (!changed.isEmpty())
				hierarchy.fireObjectClassificationsChangedEvent(this, changed);
		}
		
	}
	
	
	double[] parseValues(List<Slider> sliders) {
		return sliders.stream().mapToDouble(s -> s.getValue()).toArray();
	}
	
	double[] parseValues(List<Slider> sliders, boolean singleThreshold) {
		if (singleThreshold)
			return new double[] {sliders.get(0).getValue()};
		return sliders.stream().mapToDouble(s -> s.getValue()).toArray();
	}
	
	void setValues(List<Slider> sliders, double[] values) {
		for (int i = 0; i < sliders.size(); i++) {
			sliders.get(i).setValue(values[i]);
		}
	}
	
	
	private IntensityClassificationRequest nextRequest;
	private ExecutorService pool;
	
	/**
	 * Request a classification update, asynchronously.
	 * @param hierarchy
	 * @param measurement
	 * @param thresholds
	 */
	void updateClassifications(PathObjectHierarchy hierarchy, boolean allDetections, String measurement, double... thresholds) {
		var imageData = qupath.getImageData();
		if (thresholds.length == 0 || imageData == null || measurement == null || Arrays.stream(thresholds).anyMatch(d -> Double.isNaN(d)))
			return;
		nextRequest = new IntensityClassificationRequest(hierarchy, allDetections, measurement, thresholds);
		if (pool == null || pool.isShutdown())
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("intensity-classifier", true));
		pool.execute(() -> processRequest());
	}
	
	synchronized void processRequest() {
		if (nextRequest == null || nextRequest.isComplete())
			return;
		nextRequest.doClassification();
	}
	
	
	/**
	 * Encapsulate the requirements for a intensity classification into a single object.
	 */
	static class IntensityClassificationRequest {
		
		private PathObjectHierarchy hierarchy;
		private String measurement;
		private double[] thresholds;
		private boolean allDetections;
		
		private boolean isComplete = false;
		
		IntensityClassificationRequest(PathObjectHierarchy hierarchy, boolean allDetections, String measurement, double[] thresholds) {
			this.hierarchy = hierarchy;
			this.measurement = measurement;
			this.thresholds = thresholds;
			this.allDetections = allDetections;
		}
		
		public void doClassification() {
			var pathObjects = allDetections ? hierarchy.getDetectionObjects() : hierarchy.getCellObjects();
			QP.setIntensityClassifications(pathObjects, measurement, thresholds);
			hierarchy.fireObjectClassificationsChangedEvent(this, pathObjects);
			isComplete = true;
		}
		
		public boolean isComplete() {
			return isComplete;
		}
		
		public WorkflowStep toWorkflowStep() {
			var formatter = new DecimalFormat("#.#####");
			String thresholdString = Arrays.stream(thresholds).mapToObj(d -> formatter.format(d)).collect(Collectors.joining(", "));
			if (allDetections) {
				return new DefaultScriptableWorkflowStep("Set detection intensity classifications",
						String.format("setDetectionIntensityClassifications(\"%s\", %s)", measurement, thresholdString));
			} else {
				return new DefaultScriptableWorkflowStep("Set cell intensity classifications",
					String.format("setCellIntensityClassifications(\"%s\", %s)", measurement, thresholdString));
			}
		}
		
	}
	

}