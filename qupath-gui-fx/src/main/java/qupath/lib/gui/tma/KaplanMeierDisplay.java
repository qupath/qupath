/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.tma;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.analysis.stats.survival.KaplanMeierData;
import qupath.lib.analysis.stats.survival.LogRankTest;
import qupath.lib.analysis.stats.survival.LogRankTest.LogRankResult;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.charts.ChartTools;
import qupath.lib.gui.charts.HistogramPanelFX;
import qupath.lib.gui.charts.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.IntParameter;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Create an manage a display component for survival data.
 * 
 * @author Pete Bankhead
 *
 */
class KaplanMeierDisplay implements ParameterChangeListener, PathObjectHierarchyListener {

	final private static Logger logger = LoggerFactory.getLogger(KaplanMeierDisplay.class);

	// Flag to enable/disable calculating all P-values, and allowing threshold to be set to the lowest
	private boolean calculateAllPValues = true;

	private PathObjectHierarchy hierarchy;
	private HistogramPanelFX histogramPanel;
	private ThresholdedChartWrapper histogramWrapper;

	private LineChart<Number, Number> chartPValues;
	private ThresholdedChartWrapper pValuesWrapper;
	private KaplanMeierChartWrapper plotter;

	private ParameterList params;
	private ParameterPanelFX panelParams;

	private BorderPane paneMain = new BorderPane();

	private TableView<Integer> table = new TableView<>();
	private KaplanMeierTableModel tableModel = new KaplanMeierTableModel(table);

	private String scoreColumn;
	private KaplanMeierDisplay.ScoreData scoreData;

	// P-value computations are relatively expensive... so cache the results for possible reuse
	private double lastPValueCensorThreshold = Double.NaN;
	private double[] pValues = null;
	private double[] pValuesSmoothed = null;
	private double[] pValueThresholds = null;
	private boolean[] pValueThresholdsObserved = null;

	// Wrapper class for storing score data - helps to reduce (relatively) expensive p-value computations
	private static class ScoreData {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			//			long temp;
			//			temp = Double.doubleToLongBits(censorTime);
			//			result = prime * result + (int) (temp ^ (temp >>> 32));
			result = prime * result + Arrays.hashCode(censored);
			result = prime * result + Arrays.hashCode(scores);
			result = prime * result + Arrays.hashCode(survival);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			KaplanMeierDisplay.ScoreData other = (KaplanMeierDisplay.ScoreData) obj;
			//			if (Double.doubleToLongBits(censorTime) != Double.doubleToLongBits(other.censorTime))
			//				return false;
			if (!Arrays.equals(censored, other.censored))
				return false;
			if (!Arrays.equals(scores, other.scores))
				return false;
			if (!Arrays.equals(survival, other.survival))
				return false;
			return true;
		}

		double[] scores;
		double[] survival;
		boolean[] censored;
		//		double censorTime;

		ScoreData(final double[] scores, final double[] survival, final boolean[] censored) {
			this.scores = scores;
			this.survival = survival;
			this.censored = censored;
		}


	}

	// Threshold properties, used for displaying on 
	private DoubleProperty[] threshProperties = new DoubleProperty[]{new SimpleDoubleProperty(Double.NaN), new SimpleDoubleProperty(Double.NaN), new SimpleDoubleProperty(Double.NaN)};

	private String survivalColumn;
	private String censoredColumn;

	public KaplanMeierDisplay(final PathObjectHierarchy hierarchy, final String scoreColumn, final String survivalColumn, final String censoredColumn) {
		this.hierarchy = hierarchy;
		if (this.hierarchy != null)
			this.hierarchy.addPathObjectListener(this);
		this.scoreColumn = scoreColumn;
		this.survivalColumn = survivalColumn;
		this.censoredColumn = censoredColumn;
		initialize();
	}

	private void initialize() {
		generatePlot();
	}


	public Parent getView() {
		return paneMain;
	}


	private Stage createStage(final Window parent, final String title) {
		Stage frame = new Stage();
		if (parent != null)
			frame.initOwner(parent);
		frame.setTitle("Kaplan Meier: " + title);

		frame.setOnCloseRequest(e -> {
			if (hierarchy != null)
				hierarchy.removePathObjectListener(KaplanMeierDisplay.this);
			panelParams.removeParameterChangeListener(KaplanMeierDisplay.this);
			frame.hide();
		});

		Scene scene = new Scene(getView(), 600, 400);
		frame.setScene(scene);
		frame.setMinWidth(600);
		frame.setMinHeight(400);
		return frame;
	}

	/**
	 * Show in own window, with optional parent window specified.
	 * 
	 * @param parent
	 * @param title
	 * @return
	 */
	public Stage show(final Window parent, final String title) {
		Stage frame = createStage(parent, title);
		frame.show();
		return frame;
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (!Platform.isFxApplicationThread())
			Platform.runLater(() -> hierarchyChanged(event));
		else
			generatePlot();
	}


	/**
	 * Set a new hierarchy, updating the plot accordingly.
	 * 
	 * @param hierarchy
	 * @param survivalKey 
	 * @param censoredKey 
	 */
	public void setHierarchy(final PathObjectHierarchy hierarchy, final String survivalKey, final String censoredKey) {
		if (this.hierarchy != null)
			this.hierarchy.removePathObjectListener(this);
		this.survivalColumn = survivalKey;
		this.censoredColumn = censoredKey;
		this.hierarchy = hierarchy;
		if (this.hierarchy != null)
			this.hierarchy.addPathObjectListener(this);
		generatePlot();
	}



	public String getScoreColumn() {
		return scoreColumn;
	}

	public void setScoreColumn(final String scoreColumn) {
		this.scoreColumn = scoreColumn;
		refresh();
	}

	public void refresh() {
		generatePlot();
	}


	@SuppressWarnings("unchecked")
	private void generatePlot() {

		KaplanMeierDisplay.ScoreData newScoreData = scoreData;

		// If we have a hierarchy, update the scores with the most recent data
		if (hierarchy != null) {
			List<TMACoreObject> cores = PathObjectTools.getTMACoreObjects(hierarchy, false);
			double[] survival = new double[cores.size()];
			boolean[] censored = new boolean[cores.size()];
			double[] scores = new double[cores.size()];

			//				// Optionally sort by scores... helps a bit when debugging e.g. p-values, Hazard ratios etc.
			//				cores.sort((c1, c2) -> Double.compare(c1.getMeasurementList().getMeasurementValue(scoreColumn), c2.getMeasurementList().getMeasurementValue(scoreColumn)));

			//				scoreColumn = "Positive %";
			//			scoreColumn = "RoughScore";
			for (int i = 0; i < cores.size(); i++) {
				TMACoreObject core = cores.get(i);
				MeasurementList ml = core.getMeasurementList();
				survival[i] = core.getMeasurementList().getMeasurementValue(survivalColumn);
				double censoredValue = core.getMeasurementList().getMeasurementValue(censoredColumn);
				boolean hasCensoredValue = !Double.isNaN(censoredValue) && (censoredValue == 0 || censoredValue == 1);
				censored[i] = censoredValue != 0;
				if (!hasCensoredValue) {
					// If we don't have a censored value, ensure we mask out everything else
					scores[i] = Double.NaN;
					survival[i] = Double.NaN;
				} else if (ml.containsNamedMeasurement(scoreColumn))
					// Get the score if we can
					scores[i] = ml.getMeasurementValue(scoreColumn);
				else {
//					// Try to compute score if we need to
//					Map<String, Number> map = ROIMeaningfulMeasurements.getPathClassSummaryMeasurements(core.getChildObjects(), true);
//					Number value = map.get(scoreColumn);
//					if (value == null)
						scores[i] = Double.NaN;
//					else
//						scores[i] = value.doubleValue();
				}
			}
			// Mask out any scores that don't have associated survival data
			for (int i = 0; i < survival.length; i++) {
				if (Double.isNaN(survival[i]))
					scores[i] = Double.NaN;
			}

			newScoreData = new ScoreData(scores, survival, censored);

		}


		if (newScoreData == null || newScoreData.scores.length == 0)
			return;

		//			KaplanMeier kmHigh = new KaplanMeier("Above threshold");
		//			KaplanMeier kmLow = new KaplanMeier("Below threshold");

		double[] quartiles = StatisticsHelper.getQuartiles(newScoreData.scores);
		double q1 = quartiles[0];
		double median = quartiles[1];
		double q3 = quartiles[2];
		double[] thresholds;
		if (params != null) {
			Object thresholdMethod = params.getChoiceParameterValue("scoreThresholdMethod");
			if (thresholdMethod.equals("Median")) {
				//					panelParams.setNumericParameterValue("scoreThreshold", median);
				//					((DoubleParameter)params.getParameters().get("scoreThreshold")).setValue(median); // TODO: UPDATE DIALOG!
				thresholds = new double[]{median};
			} else if (thresholdMethod.equals("Tertiles")) {
				//						((DoubleParameter)params.getParameters().get("scoreThreshold")).setValue(median); // TODO: UPDATE DIALOG!
				thresholds = StatisticsHelper.getTertiles(newScoreData.scores);
			} else if (thresholdMethod.equals("Quartiles")) {
				//					((DoubleParameter)params.getParameters().get("scoreThreshold")).setValue(median); // TODO: UPDATE DIALOG!
				thresholds = new double[]{q1, median, q3};
			} else if (thresholdMethod.equals("Manual (1)")) {
				thresholds = new double[]{params.getDoubleParameterValue("threshold1")};
			} else if (thresholdMethod.equals("Manual (2)")) {
				thresholds = new double[]{params.getDoubleParameterValue("threshold1"), params.getDoubleParameterValue("threshold2")};
			} else //if (thresholdMethod.equals("Manual (3)")) {
				thresholds = new double[]{params.getDoubleParameterValue("threshold1"), params.getDoubleParameterValue("threshold2"), params.getDoubleParameterValue("threshold3")};
		} else
			thresholds = new double[]{median};			

		double minVal = Double.POSITIVE_INFINITY;
		double maxVal = Double.NEGATIVE_INFINITY;
		int numNonNaN = 0;
		for (double d : newScoreData.scores) {
			if (Double.isNaN(d))
				continue;
			if (d < minVal)
				minVal = d;
			if (d > maxVal)
				maxVal = d;
			numNonNaN++;
		}
		boolean scoresValid = maxVal > minVal; // If not this, we don't have valid scores that we can work with

		double maxTimePoint = 0;
		for (double d : newScoreData.survival) {
			if (Double.isNaN(d))
				continue;
			if (d > maxTimePoint)
				maxTimePoint = d;
		}
		if (panelParams != null && maxTimePoint > ((IntParameter)params.getParameters().get("censorTimePoints")).getUpperBound()) {
			panelParams.setNumericParameterValueRange("censorTimePoints", 0, Math.ceil(maxTimePoint));
		}

		// Optionally censor at specified time
		double censorThreshold = params == null ? maxTimePoint : params.getIntParameterValue("censorTimePoints");

		// Compute log-rank p-values for *all* possible thresholds
		// Simultaneously determine the threshold that yields the lowest p-value, 
		// resolving ties in favour of a more even split between high/low numbers of events
		boolean pValuesChanged = false;
		if (calculateAllPValues) {
			if (!(pValues != null && pValueThresholds != null && newScoreData.equals(scoreData) && censorThreshold == lastPValueCensorThreshold)) {
				Map<Double, Double> mapLogRank = new TreeMap<>();
				Set<Double> setObserved = new HashSet<>();
				for (int i = 0; i < newScoreData.scores.length; i++) {
					Double d = newScoreData.scores[i];
					boolean observed = !newScoreData.censored[i] && newScoreData.survival[i] < censorThreshold;
					if (observed)
						setObserved.add(d);
					if (mapLogRank.containsKey(d))
						continue;
					List<KaplanMeierData> kmsTemp = splitByThresholds(newScoreData, new double[]{d}, censorThreshold, false);
					//					if (kmsTemp.get(1).nObserved() == 0 || kmsTemp.get(1).nObserved() == 0)
					//						continue;
					LogRankResult test = LogRankTest.computeLogRankTest(kmsTemp.get(0), kmsTemp.get(1));
					double pValue = test.getPValue();
					//						double pValue = test.hazardRatio < 1 ? test.hazardRatio : 1.0/test.hazardRatio; // Checking usefulness of Hazard ratios...
					if (!Double.isFinite(pValue))
						continue;
					//					if (!Double.isFinite(test.getHazardRatio())) {
					////						continue;
					//						pValue = Double.NaN;
					//					}
					mapLogRank.put(d, pValue);
				}
				pValueThresholds = new double[mapLogRank.size()];
				pValues = new double[mapLogRank.size()];
				pValueThresholdsObserved = new boolean[mapLogRank.size()];
				int count = 0;
				for (Entry<Double, Double> entry : mapLogRank.entrySet()) {
					pValueThresholds[count] = entry.getKey();
					pValues[count] = entry.getValue();
					if (setObserved.contains(entry.getKey()))
						pValueThresholdsObserved[count] = true;
					count++;
				}



				// Find the longest 'significant' stretch
				int maxSigCount = 0;
				int maxSigInd = -1;
				int sigCurrent = 0;
				int[] sigCount = new int[pValues.length];
				for (int i = 0; i < pValues.length; i++) {
					if (pValues[i] < 0.05) {
						sigCurrent++;
						sigCount[i] = sigCurrent;
						if (sigCurrent > maxSigCount) {
							maxSigCount = sigCurrent;
							maxSigInd = i;
						}
					} else
						sigCurrent = 0;
				}
				if (maxSigCount == 0) {
					logger.info("No p-values < 0.05");
				} else {
					double minThresh = maxSigInd-maxSigCount < 0 ? pValueThresholds[0]-0.0000001 : pValueThresholds[maxSigInd-maxSigCount];
					double maxThresh = pValueThresholds[maxSigInd];
					int nBetween = 0;
					int nBetweenObserved = 0;
					for (int i = 0; i < newScoreData.scores.length; i++) {
						if (newScoreData.scores[i] > minThresh && newScoreData.scores[i] <= maxThresh) {
							nBetween++;
							if (newScoreData.survival[i] < censorThreshold && !newScoreData.censored[i])
								nBetweenObserved++;
						}
					}
					logger.info("Longest stretch of p-values < 0.05: {} - {} ({} entries, {} observed)", minThresh, maxThresh, nBetween, nBetweenObserved);						
				}




				pValuesSmoothed = new double[pValues.length];
				Arrays.fill(pValuesSmoothed, Double.NaN);
				int n = (pValues.length / 20) * 2 + 1;
				logger.info("Smoothing log-rank test p-values by " + n);
				for (int i = n/2; i < pValues.length-n/2; i++) {
					double sum = 0;
					for (int k = i-n/2; k < i-n/2+n; k++) {
						sum += pValues[k];
					}
					pValuesSmoothed[i] = sum/n;
				}
				//					for (int i = 0; i < pValues.length; i++) {
				//						double sum = 0;
				//						for (int k = Math.max(0, i-n/2); k < Math.min(pValues.length, i-n/2+n); k++) {
				//							sum += pValues[k];
				//						}
				//						pValuesSmoothed[i] = sum/n;
				//					}
				//					pValues = pValuesSmoothed;


				lastPValueCensorThreshold = censorThreshold;
				pValuesChanged = true;
			}
		} else {
			lastPValueCensorThreshold = Double.NaN;
			pValueThresholds = null;
			pValues = null;
		}


		//				if (params != null && !Double.isNaN(bestThreshold) && (params.getChoiceParameterValue("scoreThresholdMethod").equals("Lowest p-value")))
		if (params != null && (params.getChoiceParameterValue("scoreThresholdMethod").equals("Lowest p-value"))) {
			int bestIdx = -1;
			double bestPValue = Double.POSITIVE_INFINITY;
			for (int i = pValueThresholds.length/10; i < pValueThresholds.length*9/10; i++) {
				if (pValues[i] < bestPValue) {
					bestIdx = i;
					bestPValue = pValues[i];
				}
			}
			thresholds = bestIdx >= 0 ? new double[]{pValueThresholds[bestIdx]} : new double[0];
		} else if (params != null && (params.getChoiceParameterValue("scoreThresholdMethod").equals("Lowest smoothed p-value"))) {
			int bestIdx = -1;
			double bestPValue = Double.POSITIVE_INFINITY;
			for (int i = pValueThresholds.length/10; i < pValueThresholds.length*9/10; i++) {
				if (pValuesSmoothed[i] < bestPValue) {
					bestIdx = i;
					bestPValue = pValuesSmoothed[i];
				}
			}
			thresholds = bestIdx >= 0 ? new double[]{pValueThresholds[bestIdx]} : new double[0];
		}


		// Split into different curves using the provided thresholds
		List<KaplanMeierData> kms = splitByThresholds(newScoreData, thresholds, censorThreshold, params != null && "Quartiles".equals(params.getChoiceParameterValue("scoreThresholdMethod")));

		//			for (KaplanMeier km : kms)
		//				km.censorAtTime(censorThreshold);
		////			kmHigh.censorAtTime(censorThreshold);
		////			kmLow.censorAtTime(censorThreshold);

		//			logger.info("High: " + kmHigh.toString());
		//			logger.info("Low: " + kmLow.toString());
		//			logger.info("Log rank comparison: {}", LogRankTest.computeLogRankTest(kmLow, kmHigh));

		if (plotter == null) {
			plotter = new KaplanMeierChartWrapper(survivalColumn + " time");
			//				plotter.setBorder(BorderFactory.createTitledBorder("Survival plot"));
			//				plotter.getCanvas().setWidth(300);
			//				plotter.getCanvas().setHeight(300);
		}
		KaplanMeierData[] kmArray = new KaplanMeierData[kms.size()];
		plotter.setKaplanMeierCurves(survivalColumn + " time", kms.toArray(kmArray));
		tableModel.setSurvivalCurves(thresholds, params != null && params.getChoiceParameterValue("scoreThresholdMethod").equals("Lowest p-value"), kmArray);


		// Bar width determined using 'Freedman and Diaconis' rule' (but overridden if this gives < 16 bins...)
		double barWidth = (2 * q3-q1) * Math.pow(numNonNaN, -1.0/3.0);
		int nBins = 100;
		if (!Double.isNaN(barWidth))
			barWidth = (int)Math.max(16, Math.ceil((maxVal - minVal) / barWidth));
		Histogram histogram = scoresValid ? new Histogram(newScoreData.scores, nBins) : null;
		if (histogramPanel == null) {
			GridPane paneHistogram = new GridPane();
			histogramPanel = new HistogramPanelFX();
			histogramPanel.getChart().setAnimated(false);
			histogramWrapper = new ThresholdedChartWrapper(histogramPanel.getChart());
			for (ObservableNumberValue val : threshProperties)
				histogramWrapper.addThreshold(val, ColorToolsFX.getCachedColor(240, 0, 0, 128));
			histogramWrapper.getPane().setPrefHeight(150);
			paneHistogram.add(histogramWrapper.getPane(), 0, 0);
			Tooltip.install(histogramPanel.getChart(), new Tooltip("Distribution of scores"));
			GridPane.setHgrow(histogramWrapper.getPane(), Priority.ALWAYS);
			GridPane.setVgrow(histogramWrapper.getPane(), Priority.ALWAYS);

			NumberAxis xAxis = new NumberAxis();
			xAxis.setLabel("Score threshold");
			NumberAxis yAxis = new NumberAxis();
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(1);
			yAxis.setTickUnit(0.1);
			yAxis.setAutoRanging(false);
			yAxis.setLabel("P-value");
			chartPValues = new LineChart<>(xAxis, yAxis);
			chartPValues.setAnimated(false);
			chartPValues.setLegendVisible(false);

			// Make chart so it can be navigated
			ChartTools.makeChartInteractive(chartPValues, xAxis, yAxis);
			pValuesChanged = true;
			Tooltip.install(chartPValues, new Tooltip("Distribution of p-values (log-rank test) comparing low vs. high for all possible score thresholds"));
			//				chartPValues.getYAxis().setAutoRanging(false);
			pValuesWrapper = new ThresholdedChartWrapper(chartPValues);
			for (ObservableNumberValue val : threshProperties)
				pValuesWrapper.addThreshold(val, ColorToolsFX.getCachedColor(240, 0, 0, 128));

			pValuesWrapper.getPane().setPrefHeight(150);
			paneHistogram.add(pValuesWrapper.getPane(), 0, 1);
			GridPane.setHgrow(pValuesWrapper.getPane(), Priority.ALWAYS);
			GridPane.setVgrow(pValuesWrapper.getPane(), Priority.ALWAYS);

			ContextMenu popup = new ContextMenu();
			ChartTools.addChartExportMenu(chartPValues, popup);

			RadioMenuItem miZoomY1 = new RadioMenuItem("0-1");
			miZoomY1.setOnAction(e -> {
				yAxis.setAutoRanging(false);
				yAxis.setUpperBound(1);
				yAxis.setTickUnit(0.2);
			});
			RadioMenuItem miZoomY05 = new RadioMenuItem("0-0.5");
			miZoomY05.setOnAction(e -> {
				yAxis.setAutoRanging(false);
				yAxis.setUpperBound(0.5);
				yAxis.setTickUnit(0.1);
			});
			RadioMenuItem miZoomY02 = new RadioMenuItem("0-0.2");
			miZoomY02.setOnAction(e -> {
				yAxis.setAutoRanging(false);
				yAxis.setUpperBound(0.2);
				yAxis.setTickUnit(0.05);
			});
			RadioMenuItem miZoomY01 = new RadioMenuItem("0-0.1");
			miZoomY01.setOnAction(e -> {
				yAxis.setAutoRanging(false);
				yAxis.setUpperBound(0.1);
				yAxis.setTickUnit(0.05);
			});
			RadioMenuItem miZoomY005 = new RadioMenuItem("0-0.05");
			miZoomY005.setOnAction(e -> {
				yAxis.setAutoRanging(false);
				yAxis.setUpperBound(0.05);
				yAxis.setTickUnit(0.01);
			});
			RadioMenuItem miZoomY001 = new RadioMenuItem("0-0.01");
			miZoomY001.setOnAction(e -> {
				yAxis.setAutoRanging(false);
				yAxis.setUpperBound(0.01);
				yAxis.setTickUnit(0.005);
			});
			ToggleGroup tgZoom = new ToggleGroup();
			miZoomY1.setToggleGroup(tgZoom);
			miZoomY05.setToggleGroup(tgZoom);
			miZoomY02.setToggleGroup(tgZoom);
			miZoomY01.setToggleGroup(tgZoom);
			miZoomY005.setToggleGroup(tgZoom);
			miZoomY001.setToggleGroup(tgZoom);
			Menu menuZoomY = new Menu("Set y-axis range");
			menuZoomY.getItems().addAll(miZoomY1, miZoomY05, miZoomY02, miZoomY01, miZoomY005, miZoomY001);

			MenuItem miCopyData = new MenuItem("Copy chart data");
			miCopyData.setOnAction(e -> {
				String dataString = ChartTools.getChartDataAsString(chartPValues);
				ClipboardContent content = new ClipboardContent();
				content.putString(dataString);
				Clipboard.getSystemClipboard().setContent(content);
			});

			popup.getItems().addAll(miCopyData, menuZoomY);
			chartPValues.setOnContextMenuRequested(e -> {
				popup.show(chartPValues, e.getScreenX(), e.getScreenY());
			});

			for (int col = 0; col < tableModel.getColumnCount(); col++) {
				TableColumn<Integer, String> column = new TableColumn<>(tableModel.getColumnName(col));
				int colNumber = col;
				column.setCellValueFactory(new Callback<CellDataFeatures<Integer, String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<Integer, String> p) {
						return new SimpleStringProperty((String)tableModel.getValueAt(p.getValue(), colNumber));
					}
				});

				column.setCellFactory(new Callback<TableColumn<Integer, String>, TableCell<Integer, String>>() {

					@Override
					public TableCell<Integer, String> call(TableColumn<Integer, String> param) {
						TableCell<Integer, String> cell = new TableCell<Integer, String>() {
							@Override
							protected void updateItem(String item, boolean empty) {
								super.updateItem(item, empty);
								setText(item);
								setTooltip(new Tooltip(item));
							}
						};
						return cell;
					}
				});


				table.getColumns().add(column);
			}
			table.setPrefHeight(250);
			table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
			table.maxHeightProperty().bind(table.prefHeightProperty());

			params = new ParameterList();
			//			maxTimePoint = 0;
			//			for (TMACoreObject core : hierarchy.getTMAGrid().getTMACoreList()) {
			//				double os = core.getMeasurementList().getMeasurementValue(TMACoreObject.KEY_OVERALL_SURVIVAL);
			//				double rfs = core.getMeasurementList().getMeasurementValue(TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL);
			//				if (os > maxTimePoint)
			//					maxTimePoint = os;
			//				if (rfs > maxTimePoint)
			//					maxTimePoint = rfs;
			//			}
			params.addIntParameter("censorTimePoints", "Max censored time", (int)(censorThreshold + 0.5), null, 0, (int)Math.ceil(maxTimePoint), "Latest time point beyond which data will be censored");
			//				params.addChoiceParameter("scoreThresholdMethod", "Threshold method", "Manual", Arrays.asList("Manual", "Median", "Log-rank test"));
			if (calculateAllPValues)
				// Don't include "Lowest smoothed p-value" - it's not an established method and open to misinterpretation...
				params.addChoiceParameter("scoreThresholdMethod", "Threshold method", "Median", Arrays.asList("Manual (1)", "Manual (2)", "Manual (3)", "Median", "Tertiles", "Quartiles", "Lowest p-value"));
//				params.addChoiceParameter("scoreThresholdMethod", "Threshold method", "Median", Arrays.asList("Manual (1)", "Manual (2)", "Manual (3)", "Median", "Tertiles", "Quartiles", "Lowest p-value", "Lowest smoothed p-value"));
			else
				params.addChoiceParameter("scoreThresholdMethod", "Threshold method", "Median", Arrays.asList("Manual (1)", "Manual (2)", "Manual (3)", "Median", "Tertiles", "Quartiles"));
			params.addDoubleParameter("threshold1", "Threshold 1", thresholds.length > 0 ? thresholds[0] : (minVal + maxVal)/2, null, "Threshold to distinguish between patient groups");
			params.addDoubleParameter("threshold2", "Threshold 2", thresholds.length > 1 ? thresholds[1] : (minVal + maxVal)/2, null, "Threshold to distinguish between patient groups");
			params.addDoubleParameter("threshold3", "Threshold 3", thresholds.length > 2 ? thresholds[2] : (minVal + maxVal)/2, null, "Threshold to distinguish between patient groups");
			params.addBooleanParameter("showAtRisk", "Show at risk", plotter.getShowAtRisk(), "Show number of patients at risk below the plot");
			params.addBooleanParameter("showTicks", "Show censored ticks", plotter.getShowCensoredTicks(), "Show ticks to indicate censored data");
			params.addBooleanParameter("showKey", "Show key", plotter.getShowKey(), "Show key indicating display of each curve");
			//				params.addBooleanParameter("useColor", "Use color", plotter.getUseColor(), "Show each curve in a different color");
			//			params.addBooleanParameter("useStrokes", "Use strokes", plotter.getUseStrokes(), "Show each curve with a differed line stroke");
			// Hide threshold parameters if threshold can't be used
			if (!scoresValid) {
				//					params.setHiddenParameters(true, "scoreThresholdMethod", "scoreThreshold");
				histogramPanel.getChart().setVisible(false);
			}
			panelParams = new ParameterPanelFX(params);
			panelParams.addParameterChangeListener(this);
			updateThresholdsEnabled();


			for (int i = 0; i < threshProperties.length; i++) {
				String p = "threshold" + (i + 1);
				threshProperties[i].addListener((v, o, n) -> {
					if (interactiveThresholds()) {
						// Need to do a decent double check with tolerance to text field value changing while typing
						if (!GeneralTools.almostTheSame(params.getDoubleParameterValue(p), n.doubleValue(), 0.0001))
							panelParams.setNumericParameterValue(p, n);
					}
				});
			}


			BorderPane paneBottom = new BorderPane();
			TitledPane paneOptions = new TitledPane("Options", panelParams.getPane());
			//				paneOptions.setCollapsible(false);
			Pane paneCanvas = new StackPane();
			paneCanvas.getChildren().add(plotter.getCanvas());

			GridPane paneLeft = new GridPane();
			paneLeft.add(paneOptions, 0, 0);
			paneLeft.add(table, 0, 1);
			GridPane.setHgrow(paneOptions, Priority.ALWAYS);
			GridPane.setHgrow(table, Priority.ALWAYS);
			paneBottom.setLeft(paneLeft);
			paneBottom.setCenter(paneHistogram);

			paneMain.setCenter(paneCanvas);	
			paneMain.setBottom(paneBottom);

			paneMain.setPadding(new Insets(10, 10, 10, 10));
		} else if (thresholds.length > 0) {
			// Ensure the sliders/text fields are set sensibly
			if (!GeneralTools.almostTheSame(thresholds[0], params.getDoubleParameterValue("threshold1"), 0.0001)) {
				panelParams.setNumericParameterValue("threshold1", thresholds[0]);
			}
			if (thresholds.length > 1 && !GeneralTools.almostTheSame(thresholds[1], params.getDoubleParameterValue("threshold2"), 0.0001)) {
				panelParams.setNumericParameterValue("threshold2", thresholds[1]);
			}
			if (thresholds.length > 2 && !GeneralTools.almostTheSame(thresholds[2], params.getDoubleParameterValue("threshold3"), 0.0001)) {
				panelParams.setNumericParameterValue("threshold3", thresholds[2]);
			}
		}

		if (histogram != null) {
			histogramPanel.getHistogramData().setAll(HistogramPanelFX.createHistogramData(histogram, false, (Color)null));
			histogramPanel.getChart().getXAxis().setLabel(scoreColumn);
			histogramPanel.getChart().getYAxis().setLabel("Count");

			ChartTools.addChartExportMenu(histogramPanel.getChart(), null);

			//				histogramWrapper.setVerticalLines(thresholds, ColorToolsFX.getCachedColor(240, 0, 0, 128));
			// Deal with threshold adjustment
			//				histogramWrapper.getThresholds().addListener((Observable o) -> generatePlot());
		}

		if (pValues != null) {
			// TODO: Raise earlier where p-value calculation is
			if (pValuesChanged) {
				ObservableList<XYChart.Data<Number, Number>> data = FXCollections.observableArrayList();
				for (int i = 0; i < pValueThresholds.length; i++) {
					double pValue = pValues[i];
					if (Double.isNaN(pValue))
						continue;
					data.add(new XYChart.Data<>(pValueThresholds[i], pValue, pValueThresholdsObserved[i]));
				}

				ObservableList<XYChart.Data<Number, Number>> dataSmoothed = null;
				if (pValuesSmoothed != null) {
					dataSmoothed = FXCollections.observableArrayList();
					for (int i = 0; i < pValueThresholds.length; i++) {
						double pValueSmoothed = pValuesSmoothed[i];
						if (Double.isNaN(pValueSmoothed))
							continue;
						dataSmoothed.add(new XYChart.Data<>(pValueThresholds[i], pValueSmoothed));
					}
				}


				// Don't bother showing the smoothed data... it tends to get in the way...
				//				if (dataSmoothed != null)
				//					chartPValues.getData().setAll(new XYChart.Series<>("P-values", data), new XYChart.Series<>("Smoothed P-values", dataSmoothed));
				//				else
				chartPValues.getData().setAll(new XYChart.Series<>("P-values", data));

				// Add line to show 0.05 significance threshold
				if (pValueThresholds.length > 1) {
					Data<Number, Number> sigData1 = new Data<>(pValueThresholds[0], 0.05);
					Data<Number, Number> sigData2 = new Data<>(pValueThresholds[pValueThresholds.length-1], 0.05);
					XYChart.Series<Number, Number> dataSignificant = new XYChart.Series<>("Significance 0.05", FXCollections.observableArrayList(
							sigData1,
							sigData2
							));
					chartPValues.getData().add(dataSignificant);
					sigData1.getNode().setVisible(false);
					sigData2.getNode().setVisible(false);
				}


				//					chartPValues.getData().get(0).getNode().setVisible(true);

				//					pValuesWrapper.clearThresholds();
				for (XYChart.Data<Number, Number> dataPoint : data) {
					if (!Boolean.TRUE.equals(dataPoint.getExtraValue()))
						dataPoint.getNode().setVisible(false);
				}
				//				if (dataSmoothed != null) {
				//					for (XYChart.Data<Number, Number> dataPoint : dataSmoothed) {
				//						dataPoint.getNode().setVisible(false);
				//					}
				//					chartPValues.getData().get(1).getNode().setOpacity(0.5);
				//				}

				//					int count = 0;					
				//					for (int i = 0; i < pValueThresholds.length; i++) {
				//						double pValue = pValues[i];
				//						if (Double.isNaN(pValue))
				//							continue;
				//						boolean observed = pValueThresholdsObserved[i];
				////						if (observed)
				////							pValuesWrapper.addThreshold(new ReadOnlyDoubleWrapper(pValueThresholds[i]), Color.rgb(0, 0, 0, 0.05));
				//						
				//						if (!observed) {
				////							StackPane pane = (StackPane)data.get(count).getNode();
				////							pane.setEffect(new DropShadow());
				//							data.get(count).getNode().setVisible(false);
				//						}
				//						count++;
				//					}
			}


			for (int i = 0; i < threshProperties.length; i++) {
				if (i < thresholds.length)
					threshProperties[i].set(thresholds[i]);
				else
					threshProperties[i].set(Double.NaN);
			}
			boolean isInteractive = interactiveThresholds();
			histogramWrapper.setIsInteractive(isInteractive);
			pValuesWrapper.setIsInteractive(isInteractive);

			chartPValues.setVisible(true);
		}
		//			else
		//				chartPValues.setVisible(false);


		// Store values for next time
		scoreData = newScoreData;
	}

	static List<KaplanMeierData> splitByThresholds(final KaplanMeierDisplay.ScoreData scoreData, final double[] thresholds, final double censorThreshold, final boolean usesQuartiles) {
		List<KaplanMeierData> kms = new ArrayList<>();
		int nThresholds = thresholds.length;
		// Ensure thresholds are sorted
		double[] sortedThresholds = thresholds.clone();
		Arrays.sort(sortedThresholds);
		for (int i = 0; i <= nThresholds; i++) {
			if (nThresholds == 0)
				kms.add(new KaplanMeierData("All"));
			else if (nThresholds == 1) {
				kms.add(new KaplanMeierData(i == 0 ? "Low" : "High"));
			} else if (nThresholds == 2 && sortedThresholds[0] < sortedThresholds[1]) {
				kms.add(new KaplanMeierData(i == 0 ? "Low" : (i == 1 ? "Moderate" : "High")));
			} else if (usesQuartiles) {
				kms.add(new KaplanMeierData("Quartile " + (i + 1)));					
			}
			else {
				if (i == 0)
					kms.add(new KaplanMeierData(String.format("x < %.2f", sortedThresholds[i])));
				else if (i == nThresholds)
					kms.add(new KaplanMeierData(String.format("x >= %.2f", sortedThresholds[i-1])));
				else
					kms.add(new KaplanMeierData(String.format("%.2f <= x < %.2f", sortedThresholds[i-1], sortedThresholds[i])));
			}
		}

		for (int i = 0; i < scoreData.survival.length; i++) {
			double s = scoreData.scores[i];
			if (Double.isNaN(s))
				continue;
			double surv = scoreData.survival[i];
			if (Double.isNaN(surv))
				continue;
			int t = sortedThresholds.length;
			while (t > 0) {
				if (s >= sortedThresholds[t-1])
					break;
				t--;
			}
			// Can be used to omit the exact threshold from contributing
			//				if (t > 0 && s == sortedThresholds[t-1]) {
			//					continue;
			//				}
			// Create an event, optionally censoring on a particular time if needed
			if (censorThreshold > 0 && surv > censorThreshold)
				kms.get(t).addEvent(censorThreshold, true);
			else
				kms.get(t).addEvent(surv, scoreData.censored[i]);
		}
		return kms;
	}



	/**
	 * Helper method to calculate best splits (in terms of log-rank test) used to identify extreme positive & negative phenotypes.
	 * 
	 * This is useful when looking at P53.
	 * 
	 * @param scoreData
	 * @param censorThreshold
	 * @return
	 */
	static double[] calculateOptimalExtremePositiveNegativeThresholds(final KaplanMeierDisplay.ScoreData scoreData, final double censorThreshold) {
		double[] thresholds = scoreData.scores.clone();
		Arrays.sort(thresholds);
		double t1Optimal = Double.NaN;
		double t2Optimal = Double.NaN;
		double bestP = Double.POSITIVE_INFINITY;
		double bestPSplit = Double.POSITIVE_INFINITY;
		int g1 = 0;
		int g2 = 0;
		int g3 = 0;
		//			int skip = scoreData.scores.length/10;
		int skip = thresholds.length/10;
		double median = thresholds[thresholds.length/2];
		for (int i = skip; i < thresholds.length-skip; i++) {
			double t1 = thresholds[i];
			if (t1 > median)
				break;
			for (int j = i+1; j < thresholds.length-skip; j++) {
				double t2 = thresholds[j];
				if (t2 < median)
					continue;
				List<KaplanMeierData> kmsTemp = splitByThresholds(scoreData, new double[]{t1, t2}, censorThreshold, false);
				// Add extreme positive event list to extreme negative
				kmsTemp.get(0).addEvents(kmsTemp.get(2).getEvents());
				LogRankResult test = LogRankTest.computeLogRankTest(kmsTemp.get(0), kmsTemp.get(1));
				double pValue = test.getPValue();
				if (pValue < bestP) {
					double split = (double)kmsTemp.get(0).nEvents() / (kmsTemp.get(0).nEvents() + kmsTemp.get(1).nEvents());
					if (Math.abs(split - 0.5) < bestPSplit) {
						bestP = pValue;
						bestPSplit = split;
						t1Optimal = t1;
						t2Optimal = t2;
						g3 = kmsTemp.get(2).getEvents().size();
						g2 = kmsTemp.get(1).getEvents().size();
						g1 = kmsTemp.get(0).getEvents().size() - g3; // Remember we added here...
					}
				}
			}
		}
		logger.info("Optimal split thresholds: {} and {} (p-value {}; group sizes {}, {} and {})",
				t1Optimal, t2Optimal, bestP, g1, g2, g3);
		return new double[]{bestP, t1Optimal, t2Optimal};
	}




	/**
	 * Returns true if the thresholds are interactive (i.e. not set based on stats)
	 * 
	 * @return
	 */
	private boolean interactiveThresholds() {
		return params != null && Arrays.asList("Manual (1)", "Manual (2)", "Manual (3)").contains(params.getChoiceParameterValue("scoreThresholdMethod"));
	}


	@Override
	public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
		if ("showTicks".equals(key)) {
			plotter.setShowCensoredTicks(parameterList.getBooleanParameterValue("showTicks"));
			return;
		}
		//		if ("useColor".equals(key)) {
		//			plotter.setUseColor(parameterList.getBooleanParameterValue("useColor"));
		//			return;
		//		}
		//		if ("useStrokes".equals(key)) {
		//			plotter.setUseStrokes(parameterList.getBooleanParameterValue("useStrokes"));
		//			return;
		//		}
		if ("showKey".equals(key)) {
			plotter.setShowKey(parameterList.getBooleanParameterValue("showKey"));
			return;
		}
		
		if ("showAtRisk".equals(key)) {
			plotter.setShowAtRisk(parameterList.getBooleanParameterValue("showAtRisk"));
			return;
		}

		// Enable/disable manual slider as required
		if ("scoreThresholdMethod".equals(key)) {
			updateThresholdsEnabled();
		}

		generatePlot();
	}



	private void updateThresholdsEnabled() {
		Object value = panelParams.getParameters().getChoiceParameterValue("scoreThresholdMethod");
		if ("Manual (1)".equals(value)) {
			panelParams.setParameterEnabled("threshold1", true);
			panelParams.setParameterEnabled("threshold2", false);
			panelParams.setParameterEnabled("threshold3", false);
		} else if ("Manual (2)".equals(value)) {
			panelParams.setParameterEnabled("threshold1", true);
			panelParams.setParameterEnabled("threshold2", true);
			panelParams.setParameterEnabled("threshold3", false);
		} else if ("Manual (3)".equals(value)) {
			panelParams.setParameterEnabled("threshold1", true);
			panelParams.setParameterEnabled("threshold2", true);
			panelParams.setParameterEnabled("threshold3", true);
		} else {
			panelParams.setParameterEnabled("threshold1", false);
			panelParams.setParameterEnabled("threshold2", false);
			panelParams.setParameterEnabled("threshold3", false);
		}
	}




	static String getLogRankComparisonName(final String conditionA, final String conditionB) {
		return String.format("%s vs %s log-rank (HR)", conditionA, conditionB);
	}



	// TODO: Fix this horrible, Swing-inspired design (a remnant of Swing days, not yet fully transferred)
	static class KaplanMeierTableModel {

		//			private static DecimalFormat df1 = new DecimalFormat("#.#");
		private static DecimalFormat df2 = new DecimalFormat("#.##");
		private static DecimalFormat df4 = new DecimalFormat("#.####");

		private List<String> names = new ArrayList<>();
		private List<String> values = new ArrayList<>();

		private TableView<Integer> table;

		KaplanMeierTableModel(final TableView<Integer> table) {
			this.table = table;
		}

		void setSurvivalCurves(final double[] thresholds, final boolean correctPValues, final KaplanMeierData...kms) {
			names.clear();
			values.clear();
			if (kms.length == 0)
				return;
			boolean multipleThresholds = thresholds.length > 1;
			int count = 0;
			for (double t : thresholds) {
				count++;
				if (multipleThresholds)
					names.add("Score threshold " + count);
				else
					names.add("Score threshold");
				values.add(df2.format(t));
			}

			names.add("Max time");
			double maxTime = Double.NEGATIVE_INFINITY;
			int nEvents = 0;
			int nObserved = 0;
			int nCensored = 0;
			for (KaplanMeierData km : kms) {
				maxTime = Math.max(maxTime, km.getMaxTime());
				nEvents += km.nEvents();
				nObserved += km.nObserved();
				nCensored += km.nCensored();
			}
			values.add(df2.format(maxTime));

			names.add("Total events");
			values.add(Integer.toString(nEvents));
			names.add("Num observed");
			values.add(Integer.toString(nObserved));
			names.add("Num censored");
			values.add(Integer.toString(nCensored));

			for (KaplanMeierData km : kms) {
				names.add(km.getName());
				values.add(km.nEvents() + " (" + km.nObserved() + " observed)");
				//					values.add(km.nEvents() + " (" + df1.format(km.nEvents()*100.0/nEvents) + "%)");
			}

			// Add pairwise log-rank tests
			for (int i = 0; i < kms.length; i++) {
				for (int j = i+1; j < kms.length; j++) {
					KaplanMeierData km1 = kms[i];
					KaplanMeierData km2 = kms[j];
					LogRankResult logRankResult = LogRankTest.computeLogRankTest(km1, km2);

					//						if (!Double.isNaN(pValue)) {
					names.add(getLogRankComparisonName(km1.getName(), km2.getName()));
					values.add(logRankResult.getResultString());

					//							names.add("Log-rank (" + km1.getName() + " vs. " + km2.getName() + ")");
					double pValue = logRankResult.getPValue();
					//							if (Double.isNaN(pValue))
					//								values.add("NaN");
					//							else if (pValue > 1e-3)
					//								values.add(df4.format(pValue));					
					//							else if (pValue > 1e-4)
					//								values.add(GeneralTools.getFormatter(5).format(pValue));					
					//							else if (pValue > 1e-5)
					//								values.add(GeneralTools.getFormatter(6).format(pValue));					
					//							else if (pValue > 1e-6)
					//								values.add(GeneralTools.getFormatter(7).format(pValue));					
					//							else
					//								values.add(GeneralTools.getFormatter(8).format(pValue));					
					////						}


					if (correctPValues) {
						names.add("Log-rank (corrected P-value, e=0.1)");

						//								pValue = 0.012;

						double pValueAdjustedQuick = -1.63*pValue*(1 + 2.35*Math.log(pValue)); // For e = 10%
						//								double pValueAdjustedQuick = -3.13*pValue*(1 + 1.65*Math.log(pValue)); // For e = 5%

						//								pValue = -1.63*pValue*(1 + 2.35*Math.log(pValue));
						//								pValue = -3.13*pValue*(1 + 1.65*Math.log(pValue));

						double epsilon = 0.1;
						//								pValue = 0.037; // For checking with Altman's paper...
						double z = (1 - pValue/2);
						NormalDistribution dist = new NormalDistribution();
						z = dist.inverseCumulativeProbability(1 - pValue/2);
						double phi = dist.density(z);
						//								System.err.println("PHI: " + phi + " for " + z);
						double pValueAdjusted = phi * (z - 1/z) * Math.log((1 - epsilon)*(1 - epsilon)/(epsilon*epsilon)) + 4 * phi/z;

						values.add(df4.format(pValueAdjusted));	

						logger.info("Original P-value: {}", pValue);
						logger.info("Quick adjusted P-value (epsilon = {}): {}", epsilon, pValueAdjustedQuick);
						logger.info("Full adjusted P-value (epsilon = {}): {}", epsilon, pValueAdjusted);
					}


					//							// Add Hazard ratio, if available
					//							if (!Double.isNaN(pValue) && Double.isFinite(logRankResult.hazardRatio)) {
					//								names.add("Hazard ratio (" + km1.getName() + " vs. " + km2.getName() + ")");
					//								values.add(String.format("%.3f (%.3f-%.3f)", logRankResult.hazardRatio, logRankResult.hazardRatioLowerConfidence, logRankResult.hazardRatioUpperConfidence));	
					//							}

				}
			}

			// If we have exactly 3 thresholds, try comparing extremes (useful for P53)
			if (kms.length == 3) {
				KaplanMeierData kmExtreme = new KaplanMeierData("Low+High");
				kmExtreme.addEvents(kms[0].getEvents());
				kmExtreme.addEvents(kms[2].getEvents());
				LogRankResult logRankResult = LogRankTest.computeLogRankTest(kmExtreme, kms[1]);
				names.add(getLogRankComparisonName(kmExtreme.getName(), kms[1].getName()));
				values.add(logRankResult.getResultString());
			}


			// Notify listeners
			List<Integer> list = new ArrayList<>();
			for (int i = 0; i < getRowCount(); i++)
				list.add(i);
			table.getItems().setAll(list);
		}


		public int getRowCount() {
			return names.size();
		}

		public int getColumnCount() {
			return 2;
		}

		public String getColumnName(int columnIndex) {
			if (columnIndex == 0)
				return "Name";
			else
				return "Value";
		}
		public String getValueAt(int rowIndex, int columnIndex) {
			if (columnIndex == 0)
				return names.get(rowIndex);
			else
				return values.get(rowIndex);
		}

	}

}