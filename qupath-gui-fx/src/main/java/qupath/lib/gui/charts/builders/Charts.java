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

package qupath.lib.gui.charts.builders;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.impl.CanvasChart;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
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


	public static void tryToOpen(ProjectImageEntry<BufferedImage> pie) {
		try {
			var current = QuPathGUI.getInstance().getViewer().getImageData();
			if (current != null) {
				current.setChanged(false);
			}
			QuPathGUI.getInstance().getViewer().setImageData(pie.readImageData());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}





	/**
	 * Try to select an object if possible (e.g. because a user clicked on it).
	 * @param pathObject the object to select
	 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
	 * @param centerObject if true, try to center it in a viewer (if possible)
	 */
	public static void tryToSelectObject(PathObject pathObject, QuPathViewer viewer, ImageData<?> imageData, boolean addToSelection, boolean centerObject) {
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

	public static void tryToSelectClass(PathClass pathClass,
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
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 *
		 * @param name  the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param x     x-values
		 * @param y     y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return a series of data
		 */
		public static <X, Y> XYChart.Series<X, Y> createSeries(String name, X[] x, Y[] y, List<?> extra) {
			List<XYChart.Data<X, Y>> data = new ArrayList<>();
			for (int i = 0; i < x.length; i++) {
				if (extra != null && i < extra.size())
					data.add(new XYChart.Data<>(x[i], y[i], extra.get(i)));
				else
					data.add(new XYChart.Data<>(x[i], y[i]));
			}
			return createSeries(name, data);
		}


		/**
		 * Create a series of data from existing data sets.
		 *
		 * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
		 * @param data the data points to plot
		 * @return a series of data
		 */
		static <X, Y> XYChart.Series<X, Y> createSeries(String name, Collection<XYChart.Data<X, Y>> data) {
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
		 * Create and add a scatterplot using arrays of numeric values.
		 *
		 * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x    x-values
		 * @param y    y-values
		 * @return this builder
		 */
		public T addSeries(String name, X[] x, Y[] y) {
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
		public T addSeries(String name, X[] x, Y[] y, Object[] extra) {
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
		public T addSeries(String name, X[] x, Y[] y, List<?> extra) {
			return addSeries(createSeries(name, x, y, extra));
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
		@SuppressWarnings("unchecked")
		public T addSeries(String name, Collection<X> x, Collection<Y> y, List<?> extra) {
			return addSeries(name, (X[]) x.toArray(), (Y[]) y.toArray(), extra == null ? null : FXCollections.observableArrayList(extra));
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
					double pixelTolerance = markerSize * 1.5;
					Optional<XYChart.Data<X, Y>> item = chart.findDataPoint(e.getX(), e.getY(), pixelTolerance);
					item.ifPresent((data) -> {
						if (data.getExtraValue() instanceof PathObject pathObject) {
							tryToSelect(
									pathObject,
									e.isShiftDown(),
									e.getClickCount() == 2);
						} else if (data.getExtraValue() instanceof ProjectImageEntry<?> pie && e.getClickCount() == 2) {
							Charts.tryToOpen((ProjectImageEntry<BufferedImage>) pie);
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


	/**
	 * Create a {@link ScatterChartBuilder} for generating a custom scatter plot.
	 * @return the builder
	 */
	public static ScatterChartBuilder scatterChart() {
		return new ScatterChartBuilder();
	}

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


}
