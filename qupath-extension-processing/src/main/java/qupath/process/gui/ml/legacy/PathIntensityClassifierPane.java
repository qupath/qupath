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

package qupath.process.gui.ml.legacy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.HistogramPanelFX;
import qupath.lib.gui.charts.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.images.ImageData;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;


/**
 * 
 * Panel for adjusting intensity classification thresholds.
 * <p>
 * Other objects can register as a change listener to be notified when anything happens, e.g. a slider moves or a different measurement is selected -
 * however this is deprecated, and may be removed in the future.
 * <p>
 * The reason for this is that the current implementation is a bit clumsy... rather than use a ParameterList for everything, it uses it for most features but
 * a separate combo box for the intensity feature - because the selection objects can change regularly depending upon the measurements that are present.
 * <p>
 * In the future, it is (tentatively) planned to address this and support ParameterChangeListeners instead.
 * 
 * @author Pete Bankhead
 *
 */
class PathIntensityClassifierPane implements PathObjectSelectionListener {
	
	final private static Logger logger = LoggerFactory.getLogger(PathIntensityClassifierPane.class);
	
	private GridPane pane = new GridPane();
	
	private ParameterPanelFX panelParameters;
	
	private QuPathGUI qupath;
	private ParameterList paramsIntensity;
	
	private ObservableList<String> availableMeasurements = FXCollections.observableArrayList();
	private FilteredList<String> filteredAvailableMeasurements = new FilteredList<>(availableMeasurements);

	private ComboBox<String> comboIntensities;
	private HistogramPanelFX panelHistogram;
	private ThresholdedChartWrapper thresholdWrapper;
	
	/**
	 * Constructor.
	 * @param qupath QuPath instance.
	 */
	public PathIntensityClassifierPane(final QuPathGUI qupath) {
		this.qupath = qupath;
		initialize();
		setFilter(s -> {
			String sl = s.toLowerCase();
			return (sl.contains("mean") || sl.contains("max")) && !sl.contains("smoothed");
		});
		setAvailableMeasurements(Collections.emptyList());
	}


	private PathObjectHierarchy getHierarchy() {
		ImageData<?> imageData = qupath.getImageData();
		return imageData == null ? null : imageData.getHierarchy();
	}

	
	private void updateIntensityHistogram() {
		String selected = comboIntensities.getSelectionModel().getSelectedItem();
		PathObjectHierarchy hierarchy = getHierarchy();
//		if (!"None".equals(selected) || hierarchy == null)
		if ("None".equals(selected) || hierarchy == null) {
			if (panelHistogram != null)
				panelHistogram.getHistogramData().clear();
			return;
		}
		// Try to make a histogram & set it in the panel
//		PathObject pathObjectSelected = hierarchy.getSelectionModel().getSelectedPathObject();
		// For now, always use all objects (not direct descendants only)
		Collection<PathObject> pathObjects = null;
//		if (pathObjectSelected == null || !pathObjectSelected.hasChildren())
			pathObjects = hierarchy.getDetectionObjects();
//		else
//			pathObjects = hierarchy.getDescendantObjects(pathObjectSelected, pathObjects, PathDetectionObject.class);			
			
//		Histogram histogram = Histogram.makeMeasurementHistogram(pathObjects, (String)selected, 256);
		
		double[] values = Histogram.getMeasurementValues(pathObjects, (String)selected);
		Histogram histogram = new Histogram(values, 128);

		// Compute quartile values
		Arrays.sort(values);
		int nNaNs = 0;
		// NaNs should be at the end of the list
		for (int i = values.length-1; i >= 0; i--) {
			if (Double.isNaN(values[i]))
				nNaNs++;
			else
				break;
		}
		int nValues = values.length - nNaNs; // Should be same as histogram.getCountSum() ?
		assert nValues == histogram.getCountSum();
		if (nValues > 0) {
			double median = values[nValues/2];
			double quartile1 = values[(int)(nValues/4 + .5)];
			double quartile3 = values[(int)(nValues*3/4 + .5)];
			logger.info(String.format("%s Quartile 1: %.4f", selected, quartile1));
			logger.info(String.format("%s Median: %.4f", selected, median));
			logger.info(String.format("%s Quartile 3: %.4f", selected, quartile3));
			RunningStatistics stats = StatisticsHelper.computeRunningStatistics(values);
			logger.info(String.format("%s Mean: %.4f", selected, stats.getMean()));
			logger.info(String.format("%s Std.Dev.: %.4f", selected, stats.getStdDev()));
			panelHistogram.getHistogramData().setAll(HistogramPanelFX.createHistogramData(histogram, true, (Integer)null));
		} else
			panelHistogram.getHistogramData().clear();
		
		updateHistogramThresholdLines();
	}
	
	
	private void updateHistogramThresholdLines() {
		if (panelHistogram == null)
			return;
		double[] x;
		if (paramsIntensity.getBooleanParameterValue("single_threshold"))
			x = new double[]{paramsIntensity.getDoubleParameterValue("threshold_1")};
		else
			x = new double[]{
				paramsIntensity.getDoubleParameterValue("threshold_1"),
				paramsIntensity.getDoubleParameterValue("threshold_2"),
				paramsIntensity.getDoubleParameterValue("threshold_3")};
		thresholdWrapper.setThresholds(Color.rgb(0, 0, 0, 0.25), x);
//		panelHistogram.setVerticalLines(x, null);
	}
	
	
	private void initialize() {
		panelHistogram = new HistogramPanelFX();
		panelHistogram.setShowTickLabels(false);
		panelHistogram.getChart().getXAxis().setVisible(false);
		panelHistogram.getChart().getXAxis().setTickMarkVisible(false);
		panelHistogram.getChart().getYAxis().setVisible(false);
		panelHistogram.getChart().getYAxis().setTickMarkVisible(false);
		panelHistogram.getChart().setMinHeight(10);
		panelHistogram.getChart().setMinWidth(10);
		panelHistogram.getChart().setVisible(false);
		panelHistogram.getChart().setAnimated(false);
		thresholdWrapper = new ThresholdedChartWrapper(panelHistogram.getChart());
		
		comboIntensities = new ComboBox<>();
		comboIntensities.setOnAction(e -> {
				String selected = comboIntensities.getSelectionModel().getSelectedItem();
				logger.trace("Intensities selected: {}", selected);
				updateIntensityHistogram();
		});
		comboIntensities.setTooltip(new Tooltip("Select an intensity feature for thresholding, e.g. to sub-classify objects according to levels of positive staining"));

		double threshold_1 = 0.2;
		double threshold_2 = 0.4;
		double threshold_3 = 0.6;
		paramsIntensity = new ParameterList()
				.addDoubleParameter("threshold_1", "Threshold 1+", threshold_1, null, 0, 1.5, "Set first intensity threshold, if required (lowest)")
				.addDoubleParameter("threshold_2", "Threshold 2+", threshold_2, null, 0, 1.5, "Set second intensity threshold, if required (intermediate)")
				.addDoubleParameter("threshold_3", "Threshold 3+", threshold_3, null, 0, 1.5, "Set third intensity threshold, if required (highest)")
				.addBooleanParameter("single_threshold", "Use single threshold", false, "Use only the first intensity threshold to separate positive & negative objects");
		
		pane.add(new Label("Intensity feature: "), 0, 0);
		pane.add(comboIntensities, 1, 0);
		comboIntensities.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(comboIntensities, Priority.ALWAYS);
		
		this.panelParameters = new ParameterPanelFX(paramsIntensity);
		this.panelParameters.addParameterChangeListener(new ParameterChangeListener() {

			@Override
			public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
				if ("single_threshold".equals(key)) {
					boolean single = paramsIntensity.getBooleanParameterValue("single_threshold");
					panelParameters.setParameterEnabled("threshold_2", !single);
					panelParameters.setParameterEnabled("threshold_3", !single);
				}
				updateHistogramThresholdLines();
			}
			
		});
//		pane.add(panelParameters.getPane(), 0, 1, 2, 1);
//		GridPane.setFillWidth(panelParameters.getPane(), Boolean.FALSE);
//		
//		pane.add(thresholdWrapper.getPane(), 2, 1, 1, 1);
////		thresholdWrapper.getPane().setMinSize(10, 10);
////		GridPane.setHgrow(thresholdWrapper.getPane(), Priority.ALWAYS);
//		GridPane.setFillHeight(thresholdWrapper.getPane(), Boolean.TRUE);
		
		
		BorderPane paneBorder = new BorderPane();
		paneBorder.setLeft(panelParameters.getPane());
		paneBorder.setCenter(thresholdWrapper.getPane());
		pane.add(paneBorder, 0, 1, 2, 1);

		
		pane.setVgap(5);
		pane.setHgap(5);
//		if (addTitle)
//			setBorder(BorderFactory.createTitledBorder("Intensity feature"));
	}
	
	
	Pane getPane() {
		return pane;
	}
	
	
	
	/**
	 * Returns a PathIntensityClassifier, or null if none was requested by the user's interactions with this JPanel.
	 * @return
	 */
	public PathObjectClassifier getIntensityClassifier() {
		String intensityMeasurement = comboIntensities.getSelectionModel().getSelectedItem();
		PathObjectClassifier intensityClassifier = null;
		if (intensityMeasurement != null && !intensityMeasurement.equals("None")) {
			boolean singleThreshold = paramsIntensity.getBooleanParameterValue("single_threshold");
			double t1 = paramsIntensity.getDoubleParameterValue("threshold_1");
//			PathClass baseClass = PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.TUMOR);
			PathClass baseClass = null;
			if (singleThreshold) {
				intensityClassifier = PathClassifierTools.createIntensityClassifier(
						baseClass,
						intensityMeasurement, t1);
			} else {
				double t2 = Math.max(t1, paramsIntensity.getDoubleParameterValue("threshold_2"));
				double t3 = Math.max(t2, paramsIntensity.getDoubleParameterValue("threshold_3"));
				intensityClassifier = PathClassifierTools.createIntensityClassifier(
						baseClass,
						intensityMeasurement,
						t1, t2, t3);
			}
		}
		return intensityClassifier;
	}
	
	
	void addThresholdParameterChangeListener(final ParameterChangeListener listener) {
		this.panelParameters.addParameterChangeListener(listener);
	}

	void removeThresholdParameterChangeListener(final ParameterChangeListener listener) {
		this.panelParameters.removeParameterChangeListener(listener);
	}
	
	ReadOnlyObjectProperty<String> intensityFeatureProperty() {
		return comboIntensities.getSelectionModel().selectedItemProperty();
	}
	
	
	/**
	 * Set a filter to display only a subset of the available measurements.
	 * 
	 * By default, the current filter shows only measurements with 'mean' or 'max' in their name, and not 'smoothed'.
	 * 
	 * This default behavior may change, so if there is any particular required filter it is better to set it explicitly.
	 * 
	 * @param filter
	 */
	public void setFilter(final Predicate<String> filter) {
		if (filter == null)
			filteredAvailableMeasurements.setPredicate(null);
		else {
			if (filter.test("None"))
				filteredAvailableMeasurements.setPredicate(filter);
			else
				// Always accept "None" as well
				filteredAvailableMeasurements.setPredicate(filter.or(s -> "None".equals(s)));
		}
	}
	
	
	/**
	 * Set the available measurements, optionally filtering them to permit only measurements containing the text filter (case insensitive).
	 * 
	 * @param measurements
	 */
	public void setAvailableMeasurements(final Collection<String> measurements) {
		
		String selected = comboIntensities.getSelectionModel().getSelectedItem();
		
		availableMeasurements.setAll(measurements);
		
//		if (filter != null) {
//			String[] filterLower = new String[filter.length];
//			for (int i = 0; i < filter.length; i++)
//				filterLower[i] = filter[i].toLowerCase();
//			
//			List<String> listPossible = new ArrayList<>();
//			for (String m : measurements) {
//				String mLower = m.toLowerCase();
//				for (String f : filterLower) {
//					if (mLower.contains(f)) {
//						listPossible.add(m);
//						break;
//					}
//				}
////				if (m.toLowerCase().contains(filterLower))
////					listPossible.add(m);
//			}
//			comboIntensities.getItems().setAll(listPossible);
//		} else
//			comboIntensities.getItems().setAll(measurements);
		
		// Ensure we have 'none'
		if (availableMeasurements.isEmpty() || !availableMeasurements.get(0).equals("None"))
			availableMeasurements.add(0, "None");
		
		if (filteredAvailableMeasurements.contains(selected))
			comboIntensities.getSelectionModel().select(selected);
		else
			comboIntensities.getSelectionModel().select("None");

		
		comboIntensities.setItems(filteredAvailableMeasurements);
		
//		// Update the choice parameter
//		parameterSelectedFeature = new ChoiceParameter<>("Intensity feature", modelIntensities.selectedFeature, modelIntensities.measurements, "Choose intensity feature");
	}
	


//	@Override
//	public void featureListChanged(PathClassifierData classifierData) {
//		modelIntensities.setAvailableMeasurements(classifierData.getAvailableFeatureNames());
//	}
//
//
//	@Override
//	public void imageDataChanged(PathClassifierData classifierData, ImageData imageDataOld, ImageData imageDataNew) {
//		if (imageDataOld == imageDataNew)
//			return;
//		if (imageDataOld != null && imageDataOld.getHierarchy() != null) {
//			imageDataOld.getHierarchy().getSelectionModel().removePathObjectSelectionListener(this);
//		}
//		if (imageDataNew != null && imageDataNew.getHierarchy() != null) {
//			imageDataNew.getHierarchy().getSelectionModel().addPathObjectSelectionListener(this);
//		}
//	}


	@Override
	public void selectedPathObjectChanged(final PathObject pathObjectSelected, final PathObject previousObject, Collection<PathObject> allSelected) {
		updateIntensityHistogram();
	}
	
}