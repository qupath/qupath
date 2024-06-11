/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.gui.tools.ColorToolsFX;

/**
 * JavaFX chart for displaying histograms.
 * If multiple histograms are shown, these will overlap with one another.
 * <p>
 * <b>Important!</b>> This implementation does not behave quite like the built-in JavaFX charts, by presenting the core
 * data within {@link javafx.scene.chart.XYChart.Series} objects.
 * Instead, the data is stored in {@link HistogramData} objects, which are then used to populate the chart.
 * <p>
 * One advantage of this is that it is easier to control the color of each histogram being displayed.
 * <p>
 * This behavior may change in the future, but for now the user must be careful to only interact with the data through
 * the list returned by {@link #getHistogramData()} (and not {@link AreaChart#getData()} directly).
 */
public class HistogramChart extends AreaChart<Number, Number> {

	private static final Logger logger = LoggerFactory.getLogger(HistogramChart.class);

	/**
	 * Enum to specify how the counts are displayed.
	 */
	public enum CountsTransformMode {
		/**
		 * Raw bin counts.
		 */
		RAW,
		/**
		 * Normalized bin counts, so that the sum of all counts is 1.0.
		 */
		NORMALIZED,
		/**
		 * Natural logarithm of raw bin counts.
		 * This is useful for displaying histograms with a wide range of counts.
		 * Bins with zero counts are displayed as zero.
		 * <p>
		 * <b>Important: </b> HistogramChart cannot currently adjust the tick display to indicate a log scale.
		 */
		LOGARITHM;

		public String toString() {
			switch (this) {
				case RAW:
					return "Raw";
				case NORMALIZED:
					return "Normalized";
				case LOGARITHM:
					return "Log10";
				default:
					throw new IllegalArgumentException("Unknown count transform mode: " + this);
			}
		}
	}

	/**
	 * Display mode for the histogram.
	 */
	public enum DisplayMode {
		/**
		 * Show as bars.
		 * This is the default, and is preferable for histograms with few bins.
		 */
		BAR,
		/**
		 * Show as areas.
		 * This can be preferable for histograms with many bins, where only the overall shape is imporant.
		 */
		AREA;

	}

	private final Map<HistogramData, Series<Number, Number>> seriesCache = new HashMap<>();

	private final ObservableList<HistogramData> histogramData = FXCollections.observableArrayList();

	private final ObjectProperty<CountsTransformMode> countsAxisMode = new SimpleObjectProperty<>(CountsTransformMode.RAW);

	private final ObjectProperty<DisplayMode> displayModeObjectProperty = new SimpleObjectProperty<>(DisplayMode.BAR);

	private final BooleanProperty hideIfEmptyProperty = new SimpleBooleanProperty(false);

	public HistogramChart() {
		super(new NumberAxis(), new NumberAxis());
		
		setHorizontalZeroLineVisible(false);
        setVerticalZeroLineVisible(false);

		var xAxis = getXAxis();
		var yAxis = getYAxis();

        xAxis.setLabel(null);
        yAxis.setLabel(null);
		xAxis.setTickLabelsVisible(true);
		xAxis.setTickMarkVisible(false);
		yAxis.setTickLabelsVisible(true);
		yAxis.setTickMarkVisible(false);
		setVerticalGridLinesVisible(false);
		setHorizontalGridLinesVisible(false);
		setLegendVisible(false);
		setCreateSymbols(false);

		displayModeObjectProperty.addListener((obs, was, is) -> updateSeries());
		countsAxisMode.addListener((obs, was, is) -> updateSeries());

		hideIfEmptyProperty.addListener((obs, was, is) -> updateVisibility());
		dataProperty().addListener((obs, was, is) -> updateVisibility());
		updateVisibility();

		histogramData.addListener(this::handleHistogramDataChange);
	}

	private void handleHistogramDataChange(ListChangeListener.Change<? extends HistogramData> c) {
		while (c.next()) {
			if (c.wasPermutated() || c.wasUpdated()) {
				// Ignore
			} else {
				for (HistogramData removedItem : c.getRemoved()) {
					var series = seriesCache.remove(removedItem);
					if (series != null)
						getData().remove(series);
					else
						logger.warn("Series not found for removed histogram data: {}", removedItem);
				}
				for (HistogramData addedItem : c.getAddedSubList()) {
					var series = seriesCache.computeIfAbsent(addedItem, this::createNewSeries);
					addedItem.updateSeries(series, displayModeObjectProperty.get(), countsAxisMode.get());
					getData().add(series);
				}
			}
		}
		updateVisibility();
	}

	private Series<Number, Number> createNewSeries(HistogramData histogramData) {
		Series<Number, Number> series = new Series<>();
		series.nodeProperty().addListener((v, o, n) -> histogramData.updateNodeColors(series));
		return series;
	}

	private void updateSeries() {
		for (var entry : seriesCache.entrySet())
			entry.getKey().updateSeries(entry.getValue(), displayModeObjectProperty.get(), countsAxisMode.get());
	}

	private void updateVisibility() {
		if (hideIfEmptyProperty.get())
			setVisible(!getData().isEmpty());
	}

	/**
	 * Property controlling whether the chart should be hidden if there is no data.
	 * @return
	 */
	public BooleanProperty hideIfEmptyProperty() {
		return hideIfEmptyProperty;
	}

	/**
	 * Request that the chart is automatically hidden (visibility set to false) when there is no data.
	 * @param doHide
	 */
	public void setHideIfEmpty(boolean doHide) {
		hideIfEmptyProperty().set(doHide);
	}

	/**
	 * Query whether the chart is automatically hidden (visibility set to false) when there is no data.
	 * @return
	 */
	public boolean getHideIfEmpty() {
		return hideIfEmptyProperty().get();
	}

	/**
	 * Property to control how counts should be transformed before being
	 * shown in the histogram
	 * @return
	 */
	public ObjectProperty<CountsTransformMode> countsTransformProperty() {
		return countsAxisMode;
	}

	/**
	 * Get the current counts transform.
	 * @return
	 * @see #countsTransformProperty()
	 */
	public CountsTransformMode getCountsTransform() {
		return countsAxisMode.get();
	}

	/**
	 * Set the current counts transform.
	 * @param mode
	 * @see #countsTransformProperty()
	 */
	public void setCountsTransform(CountsTransformMode mode) {
		Objects.requireNonNull(mode, "CountsTransformMode cannot be null");
		countsAxisMode.set(mode);
	}

	/**
	 * Property to control how the histogram is displayed, either using areas or bars.
	 * @return
	 */
	public ObjectProperty<DisplayMode> displayModeProperty() {
		return displayModeObjectProperty;
	}

	/**
	 * Get the histogram display mode.
	 * @return
	 * @see #displayModeProperty()
	 */
	public DisplayMode getDisplayMode() {
		return displayModeObjectProperty.get();
	}

	/**
	 * Set the histogram display mode.
	 * @param mode
	 * @see #displayModeProperty()
	 */
	public void setDisplayMode(DisplayMode mode) {
		Objects.requireNonNull(mode, "DisplayMode cannot be null");
		displayModeObjectProperty.set(mode);
	}

	/**
	 * Get all histogram data objects.
	 * @return
	 */
	public ObservableList<HistogramData> getHistogramData() {
		return histogramData;
	}
	
	
	
	/**
	 * Create a HistogramData object to wrap a histogram &amp; some info about its display.
	 * 
	 * @param histogram
	 * @param color
	 * @return
	 */
	public static HistogramData createHistogramData(final Histogram histogram, final Color color) {
		return new HistogramData(histogram, color);
	}
	
	
	/**
	 * Create a HistogramData object to wrap a histogram &amp; some info about its display.
	 * 
	 * @param histogram
	 * @param color Packed RGB representation of the color.
	 * @return
	 */
	public static HistogramData createHistogramData(final Histogram histogram, final Integer color) {
		return new HistogramData(histogram, color == null ? null : ColorToolsFX.getCachedColor(color));
	}

	/**
	 * Request that tick labels are visible or not for both x and y axis.
	 * @param showTickLabels
	 */
	public void setShowTickLabels(boolean showTickLabels) {
		getXAxis().setTickLabelsVisible(showTickLabels);
		getYAxis().setTickLabelsVisible(showTickLabels);
	}
	
	
	/**
	 * Helper class for representing data that may be visualized with a {@link HistogramChart}.
	 */
	public static class HistogramData {

		private Histogram histogram;
		private Color colorStroke;
		private Color colorFill;

		/**
		 * Wrapper for histogram &amp; data relevant to its display.
		 * 
		 * @param histogram
		 * @param color
		 */
		public HistogramData(final Histogram histogram, final Color color) {
			this.histogram = histogram;
			setColor(color);
		}


		/**
		 * Set the histogram stroke color.
		 * @param color
		 */
		private void setColor(final Color color) {
			this.colorStroke = color;
			this.colorFill = color == null ? null :
					Color.color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity()/4);
		}
		
		/**
		 * Get the histogram stroke color.
		 * @return
		 */
		public Color getStroke() {
			return colorStroke;
		}
		
		/**
		 * Get the histogram fill color.
		 * @return
		 */
		public Color getFill() {
			return colorFill;
		}
		
		private void updateSeries(Series<Number, Number> series, DisplayMode displayMode, CountsTransformMode countsDisplay) {
			List<Data<Number,Number>> data;
			if (histogram == null || histogram.nBins() == 0)
				data = Collections.emptyList();
			else if (displayMode == DisplayMode.AREA)
				data = createDataForAreaPlot(histogram, (h, i) -> getCount(countsDisplay, h, i));
			else
				data = createDataForBarPlot(histogram, (h, i) -> getCount(countsDisplay, h, i));
			series.getData().setAll(data);
			updateNodeColors(series);
		}

		private static double getCount(CountsTransformMode mode, Histogram histogram, int bin) {
			switch (mode) {
				case RAW:
					return histogram.getCountsForBin(bin);
				case NORMALIZED:
					return histogram.getNormalizedCountsForBin(bin);
				case LOGARITHM:
					// Count should also be an integer; for the histogram, display 0 as 0 rather than -Infinity
					double count = histogram.getCountsForBin(bin);
					return count == 0 ? 0 : Math.log10(count);
				default:
					throw new IllegalStateException("Unknown mode: " + mode);
			}
		}

		private static List<Data<Number,Number>> createDataForAreaPlot(Histogram histogram, BiFunction<Histogram, Integer, Double> countFun) {
			List<Data<Number,Number>> data = new ArrayList<>();
			// Add leftmost bin edge (to stop it looking cut off strangely)
			data.add(new XYChart.Data<>(histogram.getBinLeftEdge(0), 0));
			for (int i = 0; i < histogram.nBins(); i++) {
				// For a 'jagged' appearance with single points use the following
				XYChart.Data<Number, Number> dataElement = new XYChart.Data<>(
						(histogram.getBinLeftEdge(i) / 2 + histogram.getBinRightEdge(i) / 2),
						countFun.apply(histogram, i));
				data.add(dataElement);
			}
			// Add rightmost bin edge (to stop it looking cut off strangely)
			data.add(new XYChart.Data<>(histogram.getBinRightEdge(histogram.nBins()-1), 0));
			return data;
		}

		private static List<Data<Number,Number>> createDataForBarPlot(Histogram histogram, BiFunction<Histogram, Integer, Double> countFun) {
			List<Data<Number,Number>> data = new ArrayList<>();
			// Add leftmost bin edge (to stop it looking cut off strangely)
			data.add(new XYChart.Data<>(histogram.getBinLeftEdge(0), 0));
			for (int i = 0; i < histogram.nBins(); i++) {
				// For a proper, 'bar-like' appearance use the following
				XYChart.Data<Number,Number> dataElement = new XYChart.Data<>(
						histogram.getBinLeftEdge(i),
						0);
				data.add(dataElement);
				double val = countFun.apply(histogram, i);
				dataElement = new XYChart.Data<>(
						histogram.getBinLeftEdge(i),
						val);
				data.add(dataElement);
				dataElement = new XYChart.Data<>(
						histogram.getBinRightEdge(i),
						val);
				data.add(dataElement);
				dataElement = new XYChart.Data<>(
						histogram.getBinRightEdge(i),
						0);
				data.add(dataElement);
			}
			// Add rightmost bin edge (to stop it looking cut off strangely)
			data.add(new XYChart.Data<>(histogram.getBinRightEdge(histogram.nBins()-1), 0));
			return data;
		}
		
		
		
		void updateNodeColors(Series<Number, Number> series) {
			// Set the colors, if we can
			if (series.getNode() != null) {
				try {
					Group group = (Group)series.getNode();
					Path seriesLine = (Path)group.getChildren().get(1);
					Path fillPath = (Path)group.getChildren().get(0);
					if ((colorStroke != null || colorFill != null)) {
						seriesLine.setStroke(colorStroke);
						seriesLine.setStyle(null);
						fillPath.setFill(colorFill);
						fillPath.setStyle(null);
					} else {
						// Note: Resetting the stroke and fill to null does not seem possible (or straightforward)
						// and if we try this the CSS may not be applied - so we assume the stroke and fill
						// were not previously set to anything else.
						seriesLine.setStyle("-fx-stroke: -fx-text-base-color; -fx-opacity: 0.4;");
						fillPath.setStyle("-fx-fill: -fx-text-base-color; -fx-opacity: 0.15;");
					}
				} catch (Exception e) {
					logger.error("Failed to set colors for series {}", series);
				}
			}
		}

//		/**
//		 * Set a new histogram.
//		 * This will cause any chart using this series to be updated.
//		 * It is possible to set the same histogram as is contained here originally,
//		 * in order to enforce updates to charts (useful because the histogram itself is not Observable).
//		 * @param histogram
//		 * @param color
//		 */
//		public void setHistogram(final Histogram histogram, final Color color) {
//			this.histogram = histogram;
//			if (color != null)
//				setColor(color);
//		}
		
		/**
		 * Get the histogram.
		 * @return
		 */
		public Histogram getHistogram() {
			return histogram;
		}

		
	}


}