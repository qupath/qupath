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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.color.ColorMaps;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Helper class for generating interactive charts.
 * 
 * @author Pete Bankhead
 */
public class Charts {


	/**
	 * Create a {@link ScatterChartBuilder} for generating a custom scatter plot.
	 * @return the builder
	 */
	public static ScatterChartBuilder scatterChart() {
		return new ScatterChartBuilder();
	}

	/**
	 * Create {@link BoxplotChartBuilder} for generating a custom box plot.
	 * @return the builder
	 */
	public static BoxplotChartBuilder boxPlot() {
		return new BoxplotChartBuilder();
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



	// See https://stackoverflow.com/questions/17164375/subclassing-a-java-builder-class/34741836#34741836
	// for a great description of what is going on here...
	abstract static class ChartBuilder<T extends ChartBuilder<T, S>, S extends Chart> {

		protected QuPathViewer viewer;
		protected ImageData<?> imageData;

		protected String title;
		protected boolean legendVisible = false;
		protected Side legendSide;

		protected double markerOpacity = 1.0;
		protected double markerSize = 1.0;

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
		 * Set the size of the marker (typically points or lines) on the chart
		 * @param value the marker size
		 * @return this builder
		 */
		public T markerSize(double value) {
			this.markerSize = value;
			return getThis();
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

			var chart = build();
			stage.setScene(new Scene(chart));
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
	public static class PieChartBuilder extends Charts.ChartBuilder<PieChartBuilder, PieChart> {

		private final Map<Object, Number> data = new LinkedHashMap<>();
		private final Function<Object, String> stringFun = null;

		private boolean tooltips = true;
		private boolean convertToPercentages = false;

		PieChartBuilder() {
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
		 *
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
		 *
		 * @param doConvert whether to convert proportions to percentages
		 * @return this builder
		 */
		public PieChartBuilder convertToPercentages(boolean doConvert) {
			this.convertToPercentages = doConvert;
			return this;
		}

		/**
		 * Request tooltips to be shown when the cursor hovers over the pie chart.
		 *
		 * @param showTooltips whether to show tooltips
		 * @return this builder
		 */
		public PieChartBuilder tooltips(boolean showTooltips) {
			this.tooltips = showTooltips;
			return this;
		}

		/**
		 * Add a slice to the pie.
		 *
		 * @param name  object the slice represents
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
				rgb = ((PathClass) key).getColor();
			else if (key instanceof PathObject)
				rgb = ColorToolsFX.getDisplayedColorARGB((PathObject) key);
			return rgb == null ? null : ColorToolsFX.getCachedColor(rgb);
		}


	}

	abstract static class XYChartBuilder<T extends XYChartBuilder<T, S, X, Y>, S extends XYChart<X, Y>, X, Y> extends ChartBuilder<T, S> {
		private static final Logger logger = LoggerFactory.getLogger(XYChartBuilder.class);

		protected String xLabel, yLabel;
		private ObservableList<XYChart.Series<X, Y>> series = FXCollections.observableArrayList();

		/**
		 *
		 */
		ObservableList<XYChart.Series<X,Y>> getSeries() {
			return series;
		}

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

		/**
		 * Create a data series from two measurements for the specified objects.
		 *
		 * @param pathObjects  the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the y location
		 * @return a series of data
		 */
		public static XYChart.Series<Number, Number> createSeriesFromMeasurements(
				Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
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
		public static <T, X, Y> XYChart.Series<X, Y> createSeries(
				String name,
				Collection<? extends T> collection,
				Function<T, X> xFun,
				Function<T, Y> yFun) {
			return createSeries(name,
					collection.stream()
							.map(p -> new XYChart.Data<>(xFun.apply(p), yFun.apply(p), p))
							.toList());
		}

		/**
		 * Create a series of data from existing data sets.
		 *
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param data the data points to plot
		 * @return a series of data
		 */
		public static <X, Y> XYChart.Series<X, Y> createSeries(String name, Collection<XYChart.Data<X, Y>> data) {
			return new XYChart.Series<>(name, FXCollections.observableArrayList(data));
		}

		/**
		 * Add values extracted from objects within a specified collection.
		 *
		 * @param <E>        The type of input for X and Y.
		 * @param name       the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param collection the objects to plot
		 * @param xFun       function capable of extracting a numeric value for the x location from each object in the collection
		 * @param yFun       function capable of extracting a numeric value for the y location from each object in the collection
		 * @return this builder
		 */
		public <E> T addSeries(
				String name,
				Collection<? extends E> collection,
				Function<E, X> xFun, Function<E, Y> yFun) {
			return addSeries(name,
					collection.stream()
							.map(p -> new XYChart.Data<>(xFun.apply(p), yFun.apply(p), p))
							.toList());
		}

		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return a series of data
		 */
		public static <X, Y> XYChart.Series<X, Y> createSeries(String name,
															   Collection<? extends X> x,
															   Collection<? extends Y> y,
															   List<?> extra) {
			List<XYChart.Data<X, Y>> data = new ArrayList<>();
			var xl = x.stream().toList();
			var yl = y.stream().toList();
			for (int i = 0; i < x.size(); i++) {
				if (extra != null && i < extra.size())
					data.add(new XYChart.Data<>(xl.get(i), yl.get(i), extra.get(i)));
				else
					data.add(new XYChart.Data<>(xl.get(i), yl.get(i)));
			}
			return createSeries(name, data);
		}

		/**
		 * Create and add a scatterplot series using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public T addSeries(String name, Collection<? extends X> x, Collection<? extends Y> y, List<?> extra) {
			return addSeries(createSeries(name, x, y, extra == null ? null : FXCollections.observableArrayList(extra)));
		}

		/**
		 * Create and add a scatterplot series using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @return this builder
		 */
		public T addSeries(String name, Collection<? extends X> x, Collection<? extends Y> y) {
			return addSeries(createSeries(name, x, y, null));
		}


		/**
		 * Create and add a scatterplot series from existing data.
		 *
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param data the data points to plot
		 * @return this builder
		 */
		public T addSeries(String name, Collection<XYChart.Data<X, Y>> data) {
			if (data instanceof ObservableList)
				series.add(new XYChart.Series<>(name, (ObservableList<XYChart.Data<X, Y>>) data));
			else
				series.add(new XYChart.Series<>(name, FXCollections.observableArrayList(data)));
			return getThis();
		}

		/**
		 * Create a scatterplot series from existing data.
		 *
		 * @param series the data points to plot
		 * @return this builder
		 */
		public T addSeries(XYChart.Series<X, Y> series) {
			this.series.add(series);
			return getThis();
		}


		EventHandler<MouseEvent> createCanvasMouseHandler(CanvasChart<X, Y> chart) {
			return e -> {
				if (e.getEventType() == MouseEvent.MOUSE_CLICKED) {
					logger.info("{} clicked", chart);
					Optional<XYChart.Data<X, Y>> item = chart.findDataPoint(e.getX(), e.getY());
					item.ifPresent((data) -> {
						if (data.getExtraValue() instanceof PathObject pathObject) {
							tryToSelect(
									pathObject,
									e.isShiftDown(),
									e.getClickCount() == 2);
						} else if (data.getExtraValue() instanceof ProjectImageEntry<?> pie && e.getClickCount() == 2) {
							Charts.tryToOpen((ProjectImageEntry<BufferedImage>) pie, viewer);
						}
					});
				}
			};
		}

		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 *
		 * @param pathObject     the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 * @param centerObject   if true, try to center it in a viewer (if possible)
		 */
		private void tryToSelect(PathObject pathObject, boolean addToSelection, boolean centerObject) {
			Charts.tryToSelectObject(pathObject, viewer, imageData, addToSelection, centerObject);
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
	 * Try to load and open the specified image entry in the QuPath GUI.
	 * @param projectImageEntry the image entry
	 */
	static void tryToOpen(ProjectImageEntry<BufferedImage> projectImageEntry, QuPathViewer viewer) {
		try {
			viewer.setImageData(projectImageEntry.readImageData());
		} catch (IOException e) {
			Dialogs.showErrorMessage(
					MessageFormat.format(
							QuPathResources.getString("Charts.errorOpeningImage"),
							projectImageEntry.getImageName()
					), e);
		}
	}

	/**
	 * Create a discrete color scale mapping PathClass and selected status to a live PathObjectHierarchy
	 * @param hierarchy the {@link PathObjectHierarchy}
	 * @return a color scale showing objects in their PathClass color, the default object color, or the selected object color as appropriate.
	 */
	static ColorScale createPathClassColorScale(PathObjectHierarchy hierarchy) {
		return new ColorScales.AbstractColorScale() {
			@Override
			public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				var po = ((PathObject) data.getExtraValue());
				if (hierarchy != null && hierarchy.getSelectionModel().isSelected(po)) {
					return ColorToolsFX.getCachedColor(PathPrefs.colorSelectedObjectProperty().get());
				}
				var pc = po.getPathClass();
				if (pc == null || pc == PathClass.NULL_CLASS)
					return ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get());
				else
					return ColorToolsFX.getPathClassColor(pc);
			}

			@Override
			public String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				var po = ((PathObject) data.getExtraValue());
				var pc = po.getPathClass();
				if (hierarchy != null && hierarchy.getSelectionModel().isSelected(po)) {
					return QuPathResources.getString("Charts.PathObjectScatterChart.selected");
				}
				pc = pc == null ? PathClass.NULL_CLASS : pc;
				return pc.toString();
			}
		};
	}


	/**
	 * Try to select an object if possible (e.g. because a user clicked on it).
	 * @param pathObject the object to select
	 * @param viewer the {@link QuPathViewer} to select the object in
	 * @param imageData the image data used to find the object
	 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
	 * @param centerObject if true, try to center it in a viewer (if possible)
	 */
	static void tryToSelectObject(PathObject pathObject, QuPathViewer viewer, ImageData<?> imageData, boolean addToSelection, boolean centerObject) {
		PathObjectHierarchy hierarchy = null;
		if (imageData != null)
			hierarchy = imageData.getHierarchy();
		else if (viewer != null)
			hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return;
		if (pathObject == null)
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

	/**
	 * Try to select all objects of a given class among a collection of objects
	 * @param pathClass the path class
	 * @param pathObjects the objects to select a subset of
	 * @param imageData the image data containing the objects
	 * @param viewer the QuPath viewer
	 * @param addToSelection whether to reset the selection or add the objects to it
	 */
	static void tryToSelectClass(PathClass pathClass,
								 Collection<PathObject> pathObjects,
								 ImageData<?> imageData,
								 QuPathViewer viewer,
								 boolean addToSelection) {
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
			hierarchy.getSelectionModel().setSelectedObjects(objects, objects.getFirst().getParent());
	}





	/**
	 * Builder for creating scatter charts.
	 */
	public static class ScatterChartBuilder extends Charts.XYNumberChartBuilder<ScatterChartBuilder, ScatterChart<Number, Number>> {

		private static final Logger logger = LoggerFactory.getLogger(ScatterChartBuilder.class);
		private static final Integer DEFAULT_MAX_DATAPOINTS = 1_000_000;

		private Integer maxDatapoints;
		private Random rnd = new Random();
		private ColorScale colorScale = ColorScales.colorBySeriesName();

		ScatterChartBuilder() {
		}

		/**
		 * Choose the maximum number of supported datapoints per series.
		 * ScatterCharts are rather 'heavyweight', and including many thousands of datapoints can cause
		 * severe performance issues due to high processing and memory requirements.
		 * <p>
		 * By default, datapoints will be randomly subsampled to a 'manageable number' where necessary,
		 * which can be customized with this setting.
		 *
		 * @param max the maximum number of data points to show per series
		 * @return this builder
		 * @see #unlimitedDatapoints()
		 */
		public ScatterChartBuilder limitDatapoints(int max) {
			this.maxDatapoints = max;
			return this;
		}

		/**
		 * Set the color scale for the chart
		 * @param colorScale the color scale
		 * @return this builder
		 */
		public ScatterChartBuilder colorScale(ColorScale colorScale) {
			this.colorScale = colorScale;
			return this;
		}

		/**
		 * Show all datapoints, without subsampling, even when this may cause performance issues.
		 * Use with caution.
		 *
		 * @return this builder
		 * @see #limitDatapoints(int)
		 */
		public ScatterChartBuilder unlimitedDatapoints() {
			maxDatapoints = -1;
			return this;
		}

		@Override
		protected String getDefaultWindowTitle() {
			return QuPathResources.getString("Charts.scatterChart");
		}

		/**
		 * Set the random number generator.
		 *
		 * @param rnd A random number generator
		 * @return A modified builder.
		 */
		public ScatterChartBuilder random(Random rnd) {
			this.rnd = rnd;
			return this;
		}


		/**
		 * Plot centroids for the specified objects using a fixed pixel calibration.
		 *<br>
		 * Objects are colored by their path class and selected status by default, or the default object color if unclassified and not selected.
		 * @param pathObjects the objects to plot
		 * @param cal         the pixel calibration used to convert the centroids into other units
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal) {
			xLabel("x (" + cal.getPixelWidthUnit() + ")");
			yLabel("y (" + cal.getPixelHeightUnit() + ")");
			Function<PathObject, Number> xFun = (PathObject p) -> PathObjectTools.getROI(p, true).getCentroidX() * cal.getPixelWidth().doubleValue();
			Function<PathObject, Number> yFun = (PathObject p) -> -PathObjectTools.getROI(p, true).getCentroidY() * cal.getPixelHeight().doubleValue();
			if (viewer != null) {
				colorScale(Charts.createPathClassColorScale(viewer.getHierarchy()));
			}
			return addSeries(null, pathObjects, xFun, yFun);
		}

		/**
		 * Plot centroids for the specified objects using a fixed pixel calibration.
		 *<br>
		 * Objects are colored by their path class and selected status by default, or the default object color if unclassified and not selected.
		 * @param pathObjects the objects to plot
		 * @param cal         the pixel calibration used to convert the centroids into other units
		 * @param colorMeasurement the measurement to extract from each object's measurement list for the point color
		 * @param colorMapName the color map name (e.g., "Viridis")
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal, String colorMeasurement, String colorMapName) {
			centroids(pathObjects, cal);
			colorScale(ColorScales.createMeasurementColorScale(colorMeasurement, ColorMaps.getColorMaps().get(colorMapName)));
			return this;
		}


		/**
		 * Plot centroids for the specified objects using a fixed pixel calibration.
		 *<br>
		 * Objects are colored by their path class and selected status by default, or the default object color if unclassified and not selected.
		 * @param pathObjects the objects to plot
		 * @param cal         the pixel calibration used to convert the centroids into other units
		 * @param colorMeasurement the measurement to extract from each object's measurement list for the point color
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal, String colorMeasurement) {
			centroids(pathObjects, cal);
			colorScale(ColorScales.createMeasurementColorScale(colorMeasurement, ColorMaps.getDefaultColorMap()));
			return this;
		}

		/**
		 * Plot centroids for the specified objects in pixel units.
		 *
		 * @param pathObjects the objects to plot.
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects) {
			var cal = imageData == null ? PixelCalibration.getDefaultInstance() : imageData.getServer().getPixelCalibration();
			return centroids(pathObjects, cal);
		}

		/**
		 * Plot centroids for the specified objects in pixel units.
		 *
		 * @param pathObjects the objects to plot.
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, String colorMeasurement, String colorMapName) {
			centroids(pathObjects);
			colorScale(ColorScales.createMeasurementColorScale(colorMeasurement, ColorMaps.getColorMaps().get(colorMapName)));
			return this;
		}

		/**
		 * Plot centroids for the specified objects in pixel units.
		 *
		 * @param pathObjects the objects to plot.
		 * @return this builder
		 */
		public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, String colorMeasurement) {
			centroids(pathObjects);
			colorScale(ColorScales.createMeasurementColorScale(colorMeasurement, ColorMaps.getDefaultColorMap()));
			return this;
		}

		/**
		 * Plot two measurements against one another for the specified objects.
		 * <br>
		 * Objects are colored by their path class and selected status by default, or the default object color if unclassified and not selected.
		 * @param pathObjects  the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the y location
		 * @return this builder
		 */
		public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
			xLabel(xMeasurement);
			yLabel(yMeasurement);
			Function<PathObject, Number> xFun = (PathObject p) -> p.getMeasurementList().get(xMeasurement);
			Function<PathObject, Number> yFun = (PathObject p) -> p.getMeasurementList().get(yMeasurement);
			if (viewer != null) {
				colorScale(Charts.createPathClassColorScale(viewer.getHierarchy()));
			}
			return addSeries(null, pathObjects, xFun, yFun);
		}

		/**
		 * Plot two measurements against one another for the specified objects, coloring points by a third measurement.
		 * @param pathObjects the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param colorMeasurement the measurement to extract from each object's measurement list for the point color
		 * @param colorMapName the color map name (e.g., "Viridis")
		 * @return this builder
		 */
		public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement, String colorMeasurement, String colorMapName) {
			measurements(pathObjects, xMeasurement, yMeasurement);
			colorScale(ColorScales.createMeasurementColorScale(colorMeasurement, ColorMaps.getColorMaps().get(colorMapName)));
			return this;
		}

		/**
		 * Plot two measurements against one another for the specified objects, coloring points by a third measurement.
		 * @param pathObjects the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param colorMeasurement the measurement to extract from each object's measurement list for the point color
		 * @return this builder
		 */
		public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement, String colorMeasurement) {
			measurements(pathObjects, xMeasurement, yMeasurement);
			colorScale(ColorScales.createMeasurementColorScale(colorMeasurement, ColorMaps.getDefaultColorMap()));
			return this;
		}



		/**
		 * Create and add a scatterplot using arrays of numeric values.
		 *
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x    x-values
		 * @param y    y-values
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(String name, double[] x, double[] y) {
			return addSeries(name, x, y, (List<?>) null);
		}

		/**
		 * Create and add a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra array of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(String name, double[] x, double[] y, Object[] extra) {
			return addSeries(name, x, y, extra == null ? null : Arrays.asList(extra));
		}

		/**
		 * Create and add a scatterplot series using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public ScatterChartBuilder addSeries(String name, double[] x, double[] y, List<?> extra) {
			return addSeries(createSeries(name, x, y, extra));
		}

		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return a series of data
		 */
		public static XYChart.Series<Number, Number> createSeries(String name,
																  double[] x,
																  double[] y,
																  List<?> extra) {
			List<XYChart.Data<Number, Number>> data = new ArrayList<>();
			for (int i = 0; i < x.length; i++) {
				if (extra != null && i < extra.size())
					data.add(new XYChart.Data<>(x[i], y[i], extra.get(i)));
				else
					data.add(new XYChart.Data<>(x[i], y[i]));
			}
			return createSeries(name, data);
		}

		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @return a series of data
		 */
		public static XYChart.Series<Number, Number> createSeries(String name,
																  double[] x,
																  double[] y) {
			return createSeries(name, x, y, (List<?>)null);
		}

		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @return a series of data
		 */
		public static XYChart.Series<Number, Number> createSeries(String name,
																  Collection<? extends Number> x,
																  Collection<? extends Number> y) {
			return createSeries(name, x, y, null);
		}

		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra extra values associated with each point
		 * @return a series of data
		 */
		public <T> XYChart.Series<Number, Number> createSeries(String name, double[] x, double[] y, T[] extra) {
			return createSeries(name, x, y, Arrays.stream(extra).toList());
		}

		@Override
		protected void updateChart(ScatterChart<Number, Number> chart) {
			super.updateChart(chart);
			chart.getData().setAll(getSeries());
			// element in chart_base.css disables different point shapes for different series
			chart.getStylesheets()
					.add(
							Objects.requireNonNull(
									getClass().getClassLoader().getResource("css/charts/chart_base.css")).toExternalForm()
					);
			if (chart instanceof CanvasChart<?,?>) {
				// we know this is always a number,number chart because it's all we build; if we ever change that this case is dangerous obviously
				@SuppressWarnings("unchecked") CanvasChart<Number, Number> canvasChart = (CanvasChart<Number, Number>) chart;
				canvasChart.getCanvas().addEventHandler(MouseEvent.ANY, createCanvasMouseHandler(canvasChart));
			}
		}

		@Override
		protected ScatterChart<Number, Number> createNewChart(Axis<Number> xAxis, Axis<Number> yAxis) {
			var chart = new CanvasScatterChart<>(xAxis, yAxis);
			chart.setColorScale(this.colorScale);
			if (viewer != null && viewer.getHierarchy() != null) {
				viewer.getHierarchy().getSelectionModel().addPathObjectSelectionListener((_, _, _) -> {
					chart.layoutPlotChildren();
					chart.updateLegend();
				});
				viewer.getHierarchy().addListener(_ -> {
					chart.layoutPlotChildren();
					chart.updateLegend();
				});
			}
			chart.setPointOpacity(this.markerOpacity);
			chart.setPointRadius(this.markerSize);
			return chart;
		}

		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 *
		 * @param pathObject     the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 * @param centerObject   if true, try to center it in a viewer (if possible)
		 */
		private void tryToSelect(PathObject pathObject, boolean addToSelection, boolean centerObject) {
			Charts.tryToSelectObject(pathObject, viewer, imageData, addToSelection, centerObject);
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
			for (var series : getSeries()) {
				List<XYChart.Data<Number, Number>> data = series.getData();
				if (data.size() > n) {
					logger.warn("Subsampling {} data points to {}", data.size(), n);
					var list = new ArrayList<>(data);
					Collections.shuffle(list, rnd);
					data = list.subList(0, n);
					series.getData().setAll(data);
				}
			}
		}
	}

	/**
	 * Builder for creating bar charts.
	 */
	public static class BarChartBuilder extends Charts.XYCategoryChartBuilder<BarChartBuilder, BarChart<String, Number>> {

		private final ObservableList<PathObject> pathObjects = FXCollections.observableArrayList();

		BarChartBuilder() {}

		@Override
		protected String getDefaultWindowTitle() {
			return QuPathResources.getString("Charts.barChart");
		}

		/**
		 * Plot values extracted from objects within a specified collection.
		 *
		 * @param <T>
		 * @param name       the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param collection the objects to plot
		 * @param xFun       function capable of extracting a categorical value for the x location from each object in the collection
		 * @return this builder
		 */
		public <T> BarChartBuilder series(String name, Collection<? extends T> collection, Function<T, PathClass> xFun) {
			var classAndCount = collection
					.stream()
					.map(xFun)
					.map(pc -> pc != null ? pc: PathClass.NULL_CLASS)
					.collect(
							Collectors.groupingBy(Function.identity(), Collectors.counting())
					);
			return addSeries(Charts.XYChartBuilder.createSeries(name,
					classAndCount.entrySet().stream()
							.map(e -> new XYChart.Data<>(e.getKey().toString(), (Number) e.getValue(), e.getKey()))
							.sorted(Comparator.comparing(XYChart.Data::getXValue))
							.toList()));
		}


		/**
		 * Add a series with the given labels and values
		 * @param name the series name
		 * @param labels the label values
		 * @param values the numeric values
		 * @return this builder
		 */
		public BarChartBuilder series(String name, Collection<String> labels, Collection<? extends Number> values) {
			String[] strings = new String[labels.size()];
			labels.toArray(strings);
			return series(name,
					strings,
					values.stream().mapToDouble(Number::doubleValue).toArray()
			);
		}

		/**
		 * Add a series with the given labels and values
		 * @param name the series name
		 * @param labels the label values
		 * @param values the numeric values
		 * @param extra extra values associated with bars
		 * @return this builder
		 */
		public <T> BarChartBuilder series(String name, String[] labels, double[] values, T[] extra) {
			List<XYChart.Data<String, Number>> data = new ArrayList<>();
			for (int i = 0; i < labels.length; i++) {
				XYChart.Data<String, Number> d = new XYChart.Data<>(labels[i], values[i]);
				if (extra != null) {
					d.setExtraValue(extra[i]);
				}
				data.add(d);
			}

			return addSeries(createSeries(name, data));

		}

		/**
		 * Add a series with the given labels and values
		 * @param name the series name
		 * @param labels the label values
		 * @param values the numeric values
		 * @param extra extra values associated with bars
		 * @return this builder
		 */
		public BarChartBuilder series(String name, String[] labels, double[] values, List<?> extra) {
			return series(name, labels, values, extra == null ? null : extra.toArray());
		}

		/**
		 * Add a series with the given labels and values
		 * @param name the series name
		 * @param labels the label values
		 * @param values the numeric values
		 * @return this builder
		 */
		public BarChartBuilder series(String name, String[] labels, double[] values) {
			return series(name, labels, values, (List<?>) null);
		}

		/**
		 * Create a bar chart using a map of String values and associated numeric values.
		 *
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param data a map of String values to associated numeric values
		 * @return this builder
		 */
		public BarChartBuilder series(String name, Map<String, ? extends Number> data) {
			return series(name, data.keySet(), data.values());
		}

		@Override
		protected void updateChart(BarChart<String, Number> chart) {
			super.updateChart(chart);
			chart.getData().setAll(getSeries());

			// If we have a hierarchy, and pathClasses, make the plot live
			for (var s : getSeries()) {
				for (var d : s.getData()) {
					var extra = d.getExtraValue();
					var node = d.getNode();
					if (extra instanceof PathClass pathClass && node != null) {
						Integer color = pathClass.getColor();
						if (color == null) {
							color = PathPrefs.colorDefaultObjectsProperty().get();
						}
						String style = String.format("-fx-background-color: rgba(%d,%d,%d,%.2f);",
								ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), markerOpacity);
						node.setStyle(style);
						node.addEventHandler(MouseEvent.ANY, e -> {
							if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
								tryToSelectClass(pathClass, e.isShiftDown());
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
		 *
		 * @param pathObjects the objects to plot
		 * @return this builder
		 */
		public BarChartBuilder classifications(Collection<? extends PathObject> pathObjects) {
			xLabel(QuPathResources.getString("Charts.classification"));
			yLabel(QuPathResources.getString("Charts.count"));
			this.pathObjects.addAll(pathObjects);
			return series(
					null,
					pathObjects,
					BarChartBuilder::getPathClassOrNullClass);
		}

		private static PathClass getPathClassOrNullClass(PathObject p) {
			return p.getPathClass() == null ? PathClass.NULL_CLASS : p.getPathClass();
		}

		@Override
		protected BarChart<String, Number> createNewChart(Axis<String> xAxis, Axis<Number> yAxis) {
			return new BarChart<>(xAxis, yAxis);
		}

		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 *
		 * @param pathClass      the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 */
		private void tryToSelectClass(PathClass pathClass, boolean addToSelection) {
			Charts.tryToSelectClass(pathClass, pathObjects, imageData, viewer, addToSelection);
		}

		@Override
		protected BarChartBuilder getThis() {
			return this;
		}
	}

	/**
	 * Builder for box plots
	 */
	public static class BoxplotChartBuilder extends Charts.XYCategoryChartBuilder<BoxplotChartBuilder, BoxplotChart<String, Number>> {
		private final ObservableList<PathObject> pathObjects = FXCollections.observableArrayList();
		private boolean showAllPoints = false;

		private static final Logger logger = LoggerFactory.getLogger(BoxplotChartBuilder.class);

		@Override
		protected BoxplotChart<String, Number> createNewChart(Axis<String> xAxis, Axis<Number> yAxis) {
			BoxplotChart<String, Number> chart = new BoxplotChart<>(xAxis, yAxis, showAllPoints);
			chart.setMarkerOpacity(markerOpacity);
			chart.setMarkerRadius(markerSize);
			return chart;
		}

		@Override
		protected BoxplotChartBuilder getThis() {
			return this;
		}

		/**
		 * Control whether to show all points on the boxplot, or just outliers
		 * @param value the new boolean value
		 * @return this builder
		 */
		public BoxplotChartBuilder showAllPoints(boolean value) {
			this.showAllPoints = value;
			return getThis();
		}

		/**
		 * Make a boxplot of a measurement split by class
		 * @param name the name of the plot
		 * @param collection the path objects to split and plot
		 * @param measurement the measurement to plot on the y-axis
		 * @return this builder
		 * @param <T> the type of {@link PathObject}
		 */
		public <T extends PathObject> BoxplotChartBuilder measurementByClass(String name, Collection<? extends T> collection, String measurement) {
			pathObjects.addAll(collection);
			logger.info("{} objects added", pathObjects.size());
			return addSeries(createSeries(name,
					collection,
					(T po) -> {
						var pc = po.getPathClass();
						return pc == null ? "UNCLASSIFIED" : pc.toString();
					},
					(T po) -> po.getMeasurementList().get(measurement)));
		}

		@Override
		protected void updateChart(BoxplotChart<String, Number> chart) {
			super.updateChart(chart);
			chart.getData().setAll(getSeries());
			chart.getCanvas().addEventHandler(MouseEvent.ANY, createCanvasMouseHandler(chart));
		}

	}

}
