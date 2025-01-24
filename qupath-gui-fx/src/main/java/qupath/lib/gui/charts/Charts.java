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

package qupath.lib.gui.charts;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Helper class for generating interactive charts.
 * 
 * @author Pete Bankhead
 */
public class Charts {
	
	// See https://stackoverflow.com/questions/17164375/subclassing-a-java-builder-class/34741836#34741836 
	// for a great description of what is going on here...
	abstract static class ChartBuilder<T extends ChartBuilder<T, S>, S extends Chart> {
	
		protected QuPathViewer viewer;
		protected ImageData<?> imageData;
		
		protected String title;
		protected boolean legendVisible = false;
		protected Side legendSide;
		
		protected double markerOpacity = 1.0;
		
		protected double width = -1;
		protected double height = -1;
		
		private String windowTitle;
		private Window parent;
		
		protected abstract T getThis();
		
		/**
		 * Specify the chart title.
		 * @param title the title to display
		 * @return this builder
		 */
		public T title(String title) {
			this.title = title;
			return getThis();
		}
		
		/**
		 * Specify whether the legend should be shown or not.
		 * @param show if true, show the legend; otherwise hide the legend
		 * @return this builder
		 */
		public T legend(boolean show) {
			this.legendVisible = true;
			return getThis();
		}
		
		/**
		 * Specify the side of the chart where the legend should be shown.
		 * Valid values are {@code "top", "bottom", "left", "right"}. 
		 * <p>
		 * Any other value (including null) will result in the legend being hidden.
		 * 
		 * @param side the side where the legend should be shown
		 * @return this builder
		 */
		public T legend(String side) {
			if (side == null)
				return legend(false);
			switch (side.toLowerCase()) {
			case "top": 
				return legend(Side.TOP);
			case "bottom": 
				return legend(Side.BOTTOM);
			case "left": 
				return legend(Side.LEFT);
			case "right": 
				return legend(Side.RIGHT);
			default:
				return legend(false);
			}
		}
		
		/**
		 * Specify the side of the chart where the legend should be shown.
		 * If null, the legend will be hidden.
		 * @param side the side where the legend should be shown
		 * @return this builder
		 */
		public T legend(Side side) {
			this.legendSide = side;
			this.legendVisible = this.legendSide != null;
			return getThis();
		}
		
		/**
		 * Specify the marker opacity.
		 * @param opacity value between 0 (transparent) and 1 (opaque).
		 * @return this builder
		 */
		public T markerOpacity(double opacity) {
			this.markerOpacity = GeneralTools.clipValue(opacity, 0, 1);
			return getThis();
		}
		
		/**
		 * Specify an {@link ImageData} object. This can be used to make some charts 'live', e.g. if they 
		 * relate to objects within the hierarchy of this data.
		 * @param imageData the imageData to associated with this chart
		 * @return this builder
		 */
		public T imageData(ImageData<?> imageData) {
			this.imageData = imageData;
			return getThis();		
		}
		
		/**
		 * Specify a viewer. This can be used to make some charts 'live', e.g. if they 
		 * relate to objects within the viewer.
		 * @param viewer the viewer to associated with this chart
		 * @return this builder
		 */
		public T viewer(QuPathViewer viewer) {
			this.viewer = viewer;
			return getThis();
		}
		
		/**
		 * Set the preferred width of the chart.
		 * @param width preferred width
		 * @return this builder
		 */
		public T width(double width) {
			this.width = width;
			return getThis();
		}
		
		/**
		 * Set the preferred height of the chart.
		 * @param height preferred height
		 * @return this builder
		 */
		public T height(double height) {
			this.height = height;
			return getThis();
		}
		
		/**
		 * Set the preferred size of the chart.
		 * @param width preferred width
		 * @param height preferred height
		 * @return this builder
		 */
		public T size(double width, double height) {
			this.width = width;
			this.height = height;
			return getThis();
		}
		
		/**
		 * Set the parent window. If not set, QuPath will try to choose a sensible default.
		 * This is useful to avoid the chart falling 'behind' other windows when not in focus.
		 * <p>
		 * This is relevant only if {@link #show()} or {@link #toStage()} will be called.
		 * 
		 * @param parent the requested parent window
		 * @return this builder
		 */
		public T parent(Window parent) {
			this.parent = parent;
			return getThis();
		}
		
		/**
		 * Title to use for the window, if the chart is shown.
		 * <p>
		 * This is relevant only if {@link #show()} or {@link #toStage()} will be called.
		 * 
		 * @param title window title
		 * @return this builder
		 */
		public T windowTitle(String title) {
			windowTitle = title;
			return getThis();
		}

		/**
		 * Method that applies properties of this builder to the chart.
		 * Each subclass should call the method in the parent class to ensure its properties 
		 * have been applied.
		 * @param chart
		 */
		protected void updateChart(S chart) {
			chart.setTitle(title);
			if (legendSide != null)
				chart.setLegendSide(legendSide);
			chart.setLegendVisible(legendVisible);
			if (width > 0)
				chart.setPrefWidth(width);
			if (height > 0)
				chart.setPrefHeight(height);
		}
		
		protected abstract S createNewChart();
		
		/**
		 * Build a chart according to the specified parameters.
		 * @return the chart
		 */
		public S build() {
			var chart = createNewChart();
			updateChart(chart);
			return chart;
		}
		
		/**
		 * Get a window title to use for charts of this kind, assuming the user has not 
		 * specified one.
		 * @return a suitable title to use
		 */
		protected String getDefaultWindowTitle() {
			return "Chart";
		}
		
		/**
		 * Add the chart to a stage, but do not show it.
		 * 
		 * @return the stage containing this {@link Chart}.
		 * @see #show()
		 */
		public Stage toStage() {
			if (!Platform.isFxApplicationThread()) {
				return FXUtils.callOnApplicationThread(() -> toStage());
			}
			var stage = new Stage();
			
			// Figure out a suitable parent
			if (parent == null) {
				if (viewer != null && viewer.getView() != null)
					parent = viewer.getView().getScene().getWindow();
				else {
					var qupath = QuPathGUI.getInstance();
					if (qupath != null)
						parent = qupath.getStage();
				}
			}
			if (parent != null)
				stage.initOwner(parent);
						
			if (windowTitle != null)
				stage.setTitle(windowTitle);
			else
				stage.setTitle(GeneralTools.generateDistinctName(getDefaultWindowTitle(),
						Window.getWindows()
						.stream()
						.filter(w -> w instanceof Stage)
						.map(w -> ((Stage)w).getTitle())
						.collect(Collectors.toSet())));
			
			stage.setScene(new Scene(build()));
			
			return stage;
		}
		
		/**
		 * Add the chart to a stage, and show it in the Application thread.
		 * 
		 * @return the stage containing this {@link Chart}.
		 * @see #toStage()
		 */
		public Stage show() {
			if (!Platform.isFxApplicationThread())
				return FXUtils.callOnApplicationThread(() -> show());
			var stage = toStage();
			stage.show();
			return stage;
		}
		
	}
	
	
	/**
	 * Builder for creating pie charts.
	 */
	public static class PieChartBuilder extends ChartBuilder<PieChartBuilder, PieChart> {
		
		private Map<Object, Number> data = new LinkedHashMap<>();
		private Function<Object, String> stringFun = null;
		
		private boolean tooltips = true;
		private boolean convertToPercentages = false;
		
		private PieChartBuilder() {
			this.legendVisible = true;
		}

		@Override
		protected PieChartBuilder getThis() {
			return this;
		}

		@Override
		protected PieChart createNewChart() {
			var pieChart = new PieChart();
			pieChart.setAnimated(false); // Don't animate by default
			return pieChart;
		}
		
		@Override
		protected String getDefaultWindowTitle() {
			return "Pie Chart";
		}
		
		/**
		 * Specify data for the pie chart as a map.
		 * Keys refer to categories, and values are numeric determining the size of the corresponding slice.
		 * @param data the data map to show
		 * @return this builder
		 */
		public PieChartBuilder data(Map<?, ? extends Number> data) {
			for (var entry : data.entrySet()) {
				addSlice(entry.getKey(), entry.getValue());
			}
			return this;
		}
		
		/**
		 * Request that pie chart values are converted to percentages for tooltips.
		 * @param doConvert
		 * @return
		 */
		public PieChartBuilder convertToPercentages(boolean doConvert) {
			this.convertToPercentages = doConvert;
			return this;
		}
		
		/**
		 * Request tooltips to be shown when the cursor hovers over the pie chart.
		 * @param showTooltips
		 * @return
		 */
		public PieChartBuilder tooltips(boolean showTooltips) {
			this.tooltips = showTooltips;
			return this;
		}
		
		/**
		 * Add a slice to the pie.
		 * @param name object the slice represents
		 * @param value number that determines the proportion of the pie for the given slice
		 * @return this builder
		 */
		public PieChartBuilder addSlice(Object name, Number value) {
			data.put(name, value);
			return this;
		}
		
		@Override
		protected void updateChart(PieChart chart) {
			super.updateChart(chart);
			ChartTools.setPieChartData(chart, data,
					stringFun,
					PieChartBuilder::colorExtractor, convertToPercentages, tooltips);
		}
		
		static Color colorExtractor(Object key) {
			Integer rgb = null;
			if (key instanceof PathClass)
				rgb = ((PathClass)key).getColor();
			else if (key instanceof PathObject)
				rgb = ColorToolsFX.getDisplayedColorARGB((PathObject)key);
			return rgb == null ? null : ColorToolsFX.getCachedColor(rgb);
		}
		
		
	}
	
	
	abstract static class XYChartBuilder<T extends XYChartBuilder<T, S, X, Y>, S extends XYChart<X, Y>, X, Y> extends ChartBuilder<T, S> {
		
		protected String xLabel, yLabel;
		
		/**
		 * Specify the x-axis label.
		 * @param label the label to display
		 * @return this builder
		 */
		public T xLabel(String label) {
			this.xLabel = label;
			return getThis();
		}
		
		/**
		 * Specify the y-axis label.
		 * @param label the label to display
		 * @return this builder
		 */
		public T yLabel(String label) {
			this.yLabel = label;
			return getThis();
		}
		
	}
	
	
	abstract static class XYNumberChartBuilder<T extends XYNumberChartBuilder<T, S>, S extends XYChart<Number, Number>> extends XYChartBuilder<T, S, Number, Number> {

		private static final Logger logger = LoggerFactory.getLogger(XYNumberChartBuilder.class);

		protected abstract S createNewChart(Axis<Number> xAxis, Axis<Number> yAxis);

		private Double xLower, xUpper;
		private Double yLower, yUpper;
		
		/**
		 * Set the lower bound for the x-axis.
		 * @param lowerBound
		 * @return this builder
		 */
		public T xAxisMin(double lowerBound) {
			this.xLower = lowerBound;
			return getThis();
		}
		
		/**
		 * Set the lower bound for the y-axis.
		 * @param lowerBound
		 * @return this builder
		 */
		public T yAxisMin(double lowerBound) {
			this.yLower = lowerBound;
			return getThis();
		}
		
		/**
		 * Set the upper bound for the x-axis.
		 * @param upperBound
		 * @return this builder
		 */
		public T xAxisMax(double upperBound) {
			this.xUpper = upperBound;
			return getThis();
		}
		
		/**
		 * Set the upper bound for the y-axis.
		 * @param upperBound
		 * @return this builder
		 */
		public T yAxisMax(double upperBound) {
			this.xUpper = upperBound;
			return getThis();
		}
		
		/**
		 * Set the lower and upper bounds for the x-axis.
		 * @param lowerBound
		 * @param upperBound
		 * @return this builder
		 */
		public T xAxisRange(double lowerBound, double upperBound) {
			this.xLower = lowerBound;
			this.xUpper = upperBound;
			return getThis();
		}
		
		/**
		 * Set the lower and upper bounds for the y-axis.
		 * @param lowerBound
		 * @param upperBound
		 * @return this builder
		 */
		public T yAxisRange(double lowerBound, double upperBound) {
			this.yLower = lowerBound;
			this.yUpper = upperBound;
			return getThis();
		}
		
		
		@Override
		protected S createNewChart() {
			var xAxis = new NumberAxis();
			var yAxis = new NumberAxis();
			
			setBoundIfValid(xAxis.lowerBoundProperty(), xLower);
			setBoundIfValid(xAxis.upperBoundProperty(), xUpper);
			setBoundIfValid(yAxis.lowerBoundProperty(), yLower);
			setBoundIfValid(yAxis.upperBoundProperty(), yUpper);
			
			if (xLabel != null)
				xAxis.setLabel(xLabel);
			if (yLabel != null)
				yAxis.setLabel(yLabel);
			return createNewChart(xAxis, yAxis);
		}
		
		private static void setBoundIfValid(DoubleProperty prop, Double val) {
			if (val != null && Double.isFinite(val))
				prop.set(val);
		}
		
	}
	
	
	/**
	 * Builder for creating scatter charts.
	 */
	public static class ScatterChartBuilder extends XYNumberChartBuilder<ScatterChartBuilder, ScatterChart<Number, Number>> {

		private static final Logger logger = LoggerFactory.getLogger(ScatterChartBuilder.class);

		private ObservableList<Series<Number, Number>> series = FXCollections.observableArrayList();
		
		private Integer DEFAULT_MAX_DATAPOINTS = 10_000;
		private Integer maxDatapoints;
		private Random rnd = new Random();

		private ScatterChartBuilder() {}
		
		/**
		 * Choose the maximum number of supported datapoints per series.
		 * Scattercharts are rather 'heavyweight', and including many thousands of datapoints can cause 
		 * severe performance issues due to high processing and memory requirements.
		 * <p>
		 * By default, datapoints will be randomly subsampled to a 'manageable number' where necessary, 
		 * which can be customized with this setting.
		 * 
		 * @param max the maximum number of data points to show per series
		 * @return this builder
		 * 
		 * @see #unlimitedDatapoints()
		 */
		public ScatterChartBuilder limitDatapoints(int max) {
			this.maxDatapoints = max;
			return this;
		}
		
		/**
		 * Show all datapoints, without subsampling, even when this may cause performance issues.
		 * Use with caution.
		 * 
		 * @return this builder
		 * 
		 * @see #limitDatapoints(int)
		 */
		public ScatterChartBuilder unlimitedDatapoints() {
			maxDatapoints = -1;
			return this;
		}
		
		@Override
		protected String getDefaultWindowTitle() {
			return "Scatter Chart";
		}

		/**
		 * Set the random number generator.
		 * @param rnd A random number generator
		 * @return A modified builder.
		 */
		public ScatterChartBuilder random(Random rnd) {
			this.rnd = rnd;
			return this;
		}

		/**
		 * Plot centroids for the specified objects using a fixed pixel calibration.
		 * @param pathObjects the objects to plot
		 * @param cal the pixel calibration used to convert the centroids into other units
		 * @return this builder
		 */
		public <T> ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal) {
			xLabel("x (" + cal.getPixelWidthUnit() + ")");
			yLabel("y (" + cal.getPixelHeightUnit() + ")");
			return addSeries(
					null,
					pathObjects,
					(PathObject p) -> PathObjectTools.getROI(p, true).getCentroidX() * cal.getPixelWidth().doubleValue(),
					(PathObject p) -> -PathObjectTools.getROI(p, true).getCentroidY() * cal.getPixelHeight().doubleValue());
		}
		
		/**
		 * Plot centroids for the specified objects in pixel units.
		 * @param pathObjects the objects to plot.
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects) {
			var cal = imageData == null ? PixelCalibration.getDefaultInstance() : imageData.getServer().getPixelCalibration();
			return centroids(pathObjects, cal);
		}
		
		/**
		 * Plot two measurements against one another for the specified objects.
		 * @param pathObjects the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the y location
		 * @return this builder
		 */
		public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
			xLabel(xMeasurement);
			yLabel(yMeasurement);
			return addSeries(
					null,
					pathObjects,
					(PathObject p) -> p.getMeasurementList().get(xMeasurement),
					(PathObject p) -> p.getMeasurementList().get(yMeasurement));
		}


		/**
		 * Add values extracted from objects within a specified collection.
		 * @param <T> The type of input for X and Y.
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param collection the objects to plot
		 * @param xFun function capable of extracting a numeric value for the x location from each object in the collection
		 * @param yFun function capable of extracting a numeric value for the y location from each object in the collection
		 * @return this builder
		 */
		public <T> ScatterChartBuilder addSeries(String name, Collection<? extends T> collection, Function<T, Number> xFun, Function<T, Number> yFun) {
			return addSeries(name,
					collection.stream()
					.map(p -> new XYChart.Data<>(xFun.apply(p), yFun.apply(p), p))
					.toList());
		}

		/**
		 * Create and add a scatterplot using collections of numeric values.
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x The x variable
		 * @param y The y variable
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(String name, Collection<? extends Number> x, Collection<? extends Number> y) {
			return addSeries(name,
					x.stream().mapToDouble(Number::doubleValue).toArray(),
					y.stream().mapToDouble(Number::doubleValue).toArray());
		}

		/**
		 * Create and add a scatterplot using arrays of numeric values.
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(String name, double[] x, double[] y) {
			return addSeries(name, x, y, (List<?>)null);
		}

		/**
		 * Create and add a scatterplot using collections of numeric values, with an associated custom object.
		 * @param <T> The type of custom object.
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @param extra array of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public <T> ScatterChartBuilder addSeries(String name, double[] x, double[] y, T[] extra) {
			return addSeries(name, x, y, extra == null ? null : Arrays.asList(extra));
		}

		/**
		 * Create and add a scatterplot series using collections of numeric values, with an associated custom object.
		 * @param <T> The type of custom object.
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public <T> ScatterChartBuilder addSeries(String name, double[] x, double[] y, List<T> extra) {
			return addSeries(createSeries(name, x, y, extra));
		}

		/**
		 * Create and add a scatterplot series from existing data.
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param data the data points to plot
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(String name, Collection<Data<Number, Number>> data) {
			if (data instanceof ObservableList)
				series.add(new XYChart.Series<>(name, (ObservableList<Data<Number, Number>>)data));
			else
				series.add(new XYChart.Series<>(name, FXCollections.observableArrayList(data)));
			return this;
		}

		/**
		 * Create a scatterplot series from existing data.
		 * @param series the data points to plot
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(Series<Number, Number> series) {
			this.series.add(series);
			return this;
		}

		/**
		 * Create a data series from two measurements for the specified objects.
		 * @param pathObjects the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the y location
		 * @return a series of data
		 */
		public static Series<Number, Number> createSeriesFromMeasurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
			return createSeries(
					null,
					pathObjects,
					(PathObject p) -> p.getMeasurementList().get(xMeasurement),
					(PathObject p) -> p.getMeasurementList().get(yMeasurement));
		}

		/**
		 * Create a data series extracted from objects within a specified collection.
		 *
		 * @param <T>        The type of input for X and Y.
		 * @param name       the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param collection the objects to plot
		 * @param xFun       function capable of extracting a numeric value for the x location from each object in the collection
		 * @param yFun       function capable of extracting a numeric value for the y location from each object in the collection
		 * @return a series of data
		 */
		public static <T> Series<Number, Number> createSeries(String name, Collection<? extends T> collection, Function<T, Number> xFun, Function<T, Number> yFun) {
			return createSeries(name,
					collection.stream()
							.map(p -> new XYChart.Data<>(xFun.apply(p), yFun.apply(p), p))
							.toList());
		}

		/**
		 * Create a series using collections of numeric values.
		 *
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x    The x variable
		 * @param y    The y variable
		 * @return a series of data
		 */
		public static Series<Number, Number> createSeries(String name, Collection<? extends Number> x, Collection<? extends Number> y) {
			return createSeries(name,
					x.stream().mapToDouble(Number::doubleValue).toArray(),
					y.stream().mapToDouble(Number::doubleValue).toArray());
		}

		/**
		 * Create a scatterplot using arrays of numeric values.
		 *
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x    x-values
		 * @param y    y-values
		 * @return a series of data
		 */
		public static Series<Number, Number> createSeries(String name, double[] x, double[] y) {
			return createSeries(name, x, y, (List<?>)null);
		}

		/**
		 * Create a series of data using collections of numeric values, with an associated custom object.
		 *
		 * @param <T>   The type of custom object.
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra array of values to associate with each data point; should be the same length as x and y
		 * @return a series of data
		 */
		public static <T> Series<Number, Number> createSeries(String name, double[] x, double[] y, T[] extra) {
			return createSeries(name, x, y, extra == null ? null : Arrays.asList(extra));
		}

		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param <T>   The type of custom object.
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return a series of data
		 */
		public static <T> Series<Number, Number> createSeries(String name, double[] x, double[] y, List<T> extra) {
			List<Data<Number, Number>> data = new ArrayList<>();
			for (int i = 0; i < x.length; i++) {
				if (extra != null && i < extra.size())
					data.add(new Data<>(x[i], y[i], extra.get(i)));
				else
					data.add(new Data<>(x[i], y[i]));
			}
			return createSeries(name, data);
		}

		/**
		 * Create a series of data from existing data sets.
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param data the data points to plot
		 * @return a series of data
		 */
		private static Series<Number, Number> createSeries(String name, Collection<Data<Number, Number>> data) {
			return new XYChart.Series<>(name, FXCollections.observableArrayList(data));
		}

		@Override
		protected void updateChart(ScatterChart<Number, Number> chart) {
			super.updateChart(chart);
			chart.getData().setAll(series);
			
			// If we have a hierarchy, and PathObjects, make the plot live
			for (var s : series) {
				for (var d : s.getData()) {
					var extra = d.getExtraValue();
					var node = d.getNode();
					if (extra instanceof PathObject && node != null) {
						PathObject pathObject = (PathObject)extra;
						Integer color = ColorToolsFX.getDisplayedColorARGB(pathObject);
						String style = String.format("-fx-background-color: rgb(%d,%d,%d,%.2f);",
								ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), markerOpacity);
						node.setStyle(style);
						node.addEventHandler(MouseEvent.ANY, e -> {
							if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
								tryToSelect((PathObject)extra, e.isShiftDown(), e.getClickCount() == 2);
							else if (e.getEventType() == MouseEvent.MOUSE_ENTERED)
								node.setStyle(style + ";"
										+ "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
							else if (e.getEventType() == MouseEvent.MOUSE_EXITED)
								node.setStyle(style);
						});
					}
				}
			}
			
		}
		
		@Override
		protected ScatterChart<Number, Number> createNewChart(Axis<Number> xAxis, Axis<Number> yAxis) {
			return new ScatterChart<>(xAxis, yAxis);
		}


		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 * @param pathObject the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 * @param centerObject if true, try to center it in a viewer (if possible)
		 */
		private void tryToSelect(PathObject pathObject, boolean addToSelection, boolean centerObject) {
			tryToSelect(pathObject, viewer, imageData, addToSelection, centerObject);
		}

		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 * @param pathObject the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 * @param centerObject if true, try to center it in a viewer (if possible)
		 */
		public static void tryToSelect(PathObject pathObject, QuPathViewer viewer, ImageData<?> imageData, boolean addToSelection, boolean centerObject) {
			PathObjectHierarchy hierarchy = null;
			if (imageData != null)
				hierarchy = imageData.getHierarchy();
			else if (viewer != null)
				hierarchy = viewer.getHierarchy();
			if (hierarchy == null)
				return;
			if (addToSelection)
				hierarchy.getSelectionModel().selectObjects(Collections.singletonList(pathObject));
			else
				hierarchy.getSelectionModel().setSelectedObject(pathObject);
			if (centerObject && viewer != null) {
				var roi = pathObject.getROI();
				viewer.setCenterPixelLocation(roi.getCentroidX(), roi.getCentroidY());
			}
		}


		@Override
		protected ScatterChartBuilder getThis() {
			return this;
		}

		@Override
		public ScatterChart<Number, Number> build() {
			subsampleSeries();
			return super.build();
		}

		/**
		 * Perform data subsampling to ensure that each series contains <= maxDatapoints.
		 */
		private void subsampleSeries() {
			int n = maxDatapoints == null ? DEFAULT_MAX_DATAPOINTS : maxDatapoints;
			for (var series: this.series) {
				List<Data<Number, Number>> data = series.getData();
				if (data.size() > n) {
					logger.warn("Subsampling {} data points to {}", data.size(), n);
					var list = new ArrayList<>(data);
					Collections.shuffle(list, rnd);
					data = list.subList(0, n);
				}
				series.getData().setAll(data);
			}
		}
	}
	
	/**
	 * Create a {@link ScatterChartBuilder} for generating a custom scatter plot.
	 * @return the builder
	 */
	public static ScatterChartBuilder scatterChart() {
		return new ScatterChartBuilder();
	}
	
	/**
	 * Create a {@link PieChartBuilder} for generating a custom pie chart.
	 * @return the builder
	 */
	public static PieChartBuilder pieChart() {
		return new PieChartBuilder();
	}

	/**
	 * Create a {@link ScatterChartBuilder} for generating a custom scatter plot.
	 * @return the builder
	 */
	public static BarChartBuilder barChart() {
		return new BarChartBuilder();
	}

	abstract static class XYCategoryChartBuilder<T extends XYCategoryChartBuilder<T, S>, S extends XYChart<String, Number>> extends XYChartBuilder<T, S, String, Number> {

		protected abstract S createNewChart(Axis<String> xAxis, Axis<Number> yAxis);

		private Double yLower, yUpper;

		/**
		 * Set the lower bound for the y-axis.
		 * @param lowerBound
		 * @return this builder
		 */
		public T yAxisMin(double lowerBound) {
			this.yLower = lowerBound;
			return getThis();
		}


		/**
		 * Set the upper bound for the y-axis.
		 * @param upperBound
		 * @return this builder
		 */
		public T yAxisMax(double upperBound) {
//			this.xUpper = upperBound;
			return getThis();
		}

		/**
		 * Set the lower and upper bounds for the y-axis.
		 * @param lowerBound
		 * @param upperBound
		 * @return this builder
		 */
		public T yAxisRange(double lowerBound, double upperBound) {
			this.yLower = lowerBound;
			this.yUpper = upperBound;
			return getThis();
		}


		@Override
		protected S createNewChart() {
			var xAxis = new CategoryAxis();
			var yAxis = new NumberAxis();

			setBoundIfValid(yAxis.lowerBoundProperty(), yLower);
			setBoundIfValid(yAxis.upperBoundProperty(), yUpper);

			if (xLabel != null)
				xAxis.setLabel(xLabel);
			if (yLabel != null)
				yAxis.setLabel(yLabel);
			return createNewChart(xAxis, yAxis);
		}

		private static void setBoundIfValid(DoubleProperty prop, Double val) {
			if (val != null && Double.isFinite(val))
				prop.set(val);
		}

	}

	/**
	 * Builder for creating scatter charts.
	 */
	public static class BarChartBuilder extends XYCategoryChartBuilder<BarChartBuilder, BarChart<String, Number>> {

		private final ObservableList<Series<String, Number>> series = FXCollections.observableArrayList();
		private final ObservableList<PathObject> pathObjects = FXCollections.observableArrayList();

		private BarChartBuilder() {}

		@Override
		protected String getDefaultWindowTitle() {
			return "Bar Chart";
		}

		/**
		 * Plot values extracted from objects within a specified collection.
		 * @param <T>
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param collection the objects to plot
		 * @param xFun function capable of extracting a numeric value for the x location from each object in the collection
		 * @return this builder
		 */
		public <T> BarChartBuilder series(String name, Collection<? extends T> collection, Function<T, PathClass> xFun) {
			var classAndCount = collection
					.stream().map(xFun)
					.collect(
							Collectors.groupingBy(Function.identity(), Collectors.counting())
					);
			return series(name, classAndCount.entrySet().stream()
					.map(e -> {
						if (e.getKey() == null)
							return new Data<>(PathClass.NULL_CLASS.toString(), (Number)e.getValue(), PathClass.NULL_CLASS);
						else
							return new Data<>(e.getKey().toString(), (Number)e.getValue(), e.getKey());
					})
					.sorted(Comparator.comparing(Data::getXValue))
					.toList());
		}

		/**
		 * Create a bar chart using collections of numeric values.
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x
		 * @param y
		 * @return this builder
		 */
		public <T extends Number> BarChartBuilder series(String name, Collection<? extends String> x, Collection<T> y) {
			return series(name,
					x.stream().map(String::valueOf).toArray(String[]::new),
					y.stream().mapToDouble(Number::doubleValue).toArray());
		}

		/**
		 * Create a bar chart using a map of String values and associated numeric values.
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param data a map of String values to associated numeric values
		 * @return this builder
		 */
		public <T extends Number> BarChartBuilder series(String name, Map<String, T> data) {
			return series(name,
					data.keySet().toArray(String[]::new),
					data.values().stream().mapToDouble(Number::doubleValue).toArray(),
					(List<?>)null);
		}

		/**
		 * Create a bar chart using arrays of String values and associated numeric values.
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @return this builder
		 */
		public BarChartBuilder series(String name, String[] x, double[] y) {
			return series(name, x, y, (List<?>)null);
		}

		/**
		 * Create a bar chart using collections String values and associated numeric values, with an associated custom object.
		 * @param <T>
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @param extra array of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public <T> BarChartBuilder series(String name, String[] x, double[] y, T[] extra) {
			return series(name, x, y, extra == null ? null : Arrays.asList(extra));
		}

		/**
		 * Create a bar chart using collections of String values and associated numeric values, with an associated custom object.
		 * @param <T>
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public <T> BarChartBuilder series(String name, String[] x, double[] y, List<T> extra) {
			List<Data<String, Number>> data = new ArrayList<>();
			for (int i = 0; i < x.length; i++) {
				if (extra != null && i < extra.size())
					data.add(new Data<>(x[i], y[i], extra.get(i)));
				else
					data.add(new Data<>(x[i], y[i]));
			}
			return series(name, data);
		}

		/**
		 * Create a bar chart from existing data plots.
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param data the data points to plot
		 * @return this builder
		 */
		public BarChartBuilder series(String name, Collection<Data<String, Number>> data) {
			if (data instanceof ObservableList)
				series.add(new XYChart.Series<>(name, (ObservableList)data));
			else
				series.add(new XYChart.Series<>(name, FXCollections.observableArrayList(data)));
			return this;
		}

		@Override
		protected void updateChart(BarChart<String, Number> chart) {
			super.updateChart(chart);
			chart.getData().setAll(series);

			// If we have a hierarchy, and pathClasses, make the plot live
			for (var s : series) {
				for (var d : s.getData()) {
					var extra = d.getExtraValue();
					var node = d.getNode();
					if (extra instanceof PathClass && node != null) {
						PathClass pathClass = (PathClass)extra;
						Integer color = pathClass.getColor();
						if (color == null)
							color = ColorTools.packRGB(127, 127, 127);
						String style = String.format("-fx-background-color: rgb(%d,%d,%d,%.2f);",
								ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), markerOpacity);
						node.setStyle(style);
						node.addEventHandler(MouseEvent.ANY, e -> {
							if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
								tryToSelect(pathClass, e.isShiftDown(), e.getClickCount() == 2);
							else if (e.getEventType() == MouseEvent.MOUSE_ENTERED)
								node.setStyle(style + ";"
										+ "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
							else if (e.getEventType() == MouseEvent.MOUSE_EXITED)
								node.setStyle(style);
						});
					}
				}
			}

		}

		/**
		 * Plot two measurements against one another for the specified objects.
		 * @param pathObjects the objects to plot
		 * @return this builder
		 */
		public BarChartBuilder classifications(Collection<? extends PathObject> pathObjects) {
			xLabel("Classification");
			yLabel("Count");
			this.pathObjects.addAll(pathObjects);
			return series(
					null,
					pathObjects,
					BarChartBuilder::getPathClassOrNullClass);
		}

		/**
		 * Get the PathClass or {@link PathClass#NULL_CLASS} for the specified object
		 * (but don't return null).
		 * @param p
		 * @return
		 */
		private static PathClass getPathClassOrNullClass(PathObject p) {
			return p.getPathClass() == null ? PathClass.NULL_CLASS : p.getPathClass();
		}

		@Override
		protected BarChart<String, Number> createNewChart(Axis<String> xAxis, Axis<Number> yAxis) {
			return new BarChart<>(xAxis, yAxis);
		}

		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 * @param pathClass the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 * @param centerObject if true, try to center it in a viewer (if possible)
		 */
		private void tryToSelect(PathClass pathClass, boolean addToSelection, boolean centerObject) {
			PathObjectHierarchy hierarchy = null;
			if (imageData != null)
				hierarchy = imageData.getHierarchy();
			else if (viewer != null)
				hierarchy = viewer.getHierarchy();
			if (hierarchy == null)
				return;
			var comparePathClass = pathClass == PathClass.NULL_CLASS ? null : pathClass;
			var objects = pathObjects.stream()
					.filter(p -> Objects.equals(comparePathClass, p.getPathClass()))
					.toList();
			if (addToSelection)
				hierarchy.getSelectionModel().selectObjects(objects);
			else if (!objects.isEmpty())
				hierarchy.getSelectionModel().setSelectedObjects(objects, objects.get(0).getParent());
		}


		@Override
		protected BarChartBuilder getThis() {
			return this;
		}

	}


}
