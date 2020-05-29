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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
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
	static abstract class ChartBuilder<T extends ChartBuilder<T, S>, S extends Chart> {
	
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
				return GuiTools.callOnApplicationThread(() -> toStage());
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
				return GuiTools.callOnApplicationThread(() -> show());
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
		private Map<Object, Function<Object, String>> stringFun = new LinkedHashMap<>();
		
		private PieChartBuilder() {
			this.legendVisible = true;
		}

		@Override
		protected PieChartBuilder getThis() {
			return this;
		}

		@Override
		protected PieChart createNewChart() {
			return new PieChart();
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
			var dataMap = new LinkedHashMap<Object, PieChart.Data>();
			double sum = 0;
			for (var entry : data.entrySet()) {
				var key = entry.getKey();
				double val = entry.getValue().doubleValue();
				sum += val;
				
				String str;
				Function<Object, String> fun = stringFun.get(key);
				if (fun == null)
					str = key == null ? "No key" : key.toString();
				else
					str = fun.apply(key);
				
				var d = new PieChart.Data(str, val);
				dataMap.put(key, d);
			}
			chart.getData().setAll(dataMap.values());
			for (var entry : dataMap.entrySet()) {
				var key = entry.getKey();
				var data = entry.getValue();
				var str = data.getName();
				var node = data.getNode();
				var val = this.data.get(key).doubleValue();
				
				String tip = String.format("%s (%.1f %%)", str, val/sum*100.0);
				Tooltip.install(node, new Tooltip(tip));
				
				// Set to a meaningful color... if we have one
				Integer rgb = null;
				if (key instanceof PathClass)
					rgb = ((PathClass)key).getColor();
				else if (key instanceof PathObject)
					rgb = ColorToolsFX.getDisplayedColorARGB((PathObject)key);
				if (rgb != null) {
					node.setStyle(String.format("-fx-background-color: rgb(%d, %d, %d)",
							ColorTools.red(rgb),
							ColorTools.green(rgb),
							ColorTools.blue(rgb)));
				}

			}
		}
		
		
	}
	
	
	static abstract class XYChartBuilder<T extends XYChartBuilder<T, S, X, Y>, S extends XYChart<X, Y>, X, Y> extends ChartBuilder<T, S> {
		
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
	
	
	static abstract class XYNumberChartBuilder<T extends XYNumberChartBuilder<T, S>, S extends XYChart<Number, Number>> extends XYChartBuilder<T, S, Number, Number> {
		
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
	public static class ScatterchartBuilder extends XYNumberChartBuilder<ScatterchartBuilder, ScatterChart<Number, Number>> {
	
		private ObservableList<Series<Number, Number>> series = FXCollections.observableArrayList();
		
		private Integer DEFAULT_MAX_DATAPOINTS = 10_000;
		private Integer maxDatapoints;

		private ScatterchartBuilder() {}
		
		/**
		 * Choose the maximum number of supported datapoints.
		 * Scattercharts are rather 'heavyweight', and including many thousands of datapoints can cause 
		 * severe performance issues due to high processing and memory requirements.
		 * <p>
		 * By default, datapoints will be randomly subsampled to a 'manageable number' where necessary, 
		 * which can be customized with this setting.
		 * 
		 * @param max the maximum number of data points to show
		 * @return this builder
		 * 
		 * @see #unlimitedDatapoints()
		 */
		public ScatterchartBuilder limitDatapoints(int max) {
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
		public ScatterchartBuilder unlimitedDatapoints() {
			maxDatapoints = -1;
			return this;
		}
		
		@Override
		protected String getDefaultWindowTitle() {
			return "Scatter Chart";
		}
		
		/**
		 * Plot centroids for the specified objects using a fixed pixel calibration.
		 * @param pathObjects the objects to plot
		 * @param cal the pixel calibration used to convert the centroids into other units
		 * @return this builder
		 */
		public <T> ScatterchartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal) {
			xLabel("x (" + cal.getPixelWidthUnit() + ")");
			yLabel("y (" + cal.getPixelHeightUnit() + ")");
			return series(
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
		public ScatterchartBuilder centroids(Collection<? extends PathObject> pathObjects) {
			var cal = imageData == null ? PixelCalibration.getDefaultInstance() : imageData.getServer().getPixelCalibration();
			return centroids(pathObjects, cal);
		}
		
		/**
		 * Plot two measurements against one another for the specified objects.
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param pathObjects the objects to plot
		 * @param xMeasurement the measurement to extract from each object's measurement list for the x location
		 * @param yMeasurement the measurement to extract from each object's measurement list for the y location
		 * @return this builder
		 */
		public ScatterchartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
			xLabel(xMeasurement);
			yLabel(yMeasurement);
			return series(
					null,
					pathObjects,
					(PathObject p) -> p.getMeasurementList().getMeasurementValue(xMeasurement),
					(PathObject p) -> p.getMeasurementList().getMeasurementValue(yMeasurement));
		}
		
		/**
		 * Plot values extracted from objects within a specified collection.
		 * @param name 
		 * @param <T> 
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param collection the objects to plot
		 * @param xFun function capable of extracting a numeric value for the x location from each object in the collection
		 * @param yFun function capable of extracting a numeric value for the y location from each object in the collection
		 * @return this builder
		 */
		public <T> ScatterchartBuilder series(String name, Collection<? extends T> collection, Function<T, Number> xFun, Function<T, Number> yFun) {
			return series(name,
					collection.stream()
					.map(p -> new XYChart.Data<>(xFun.apply(p), yFun.apply(p), p))
					.collect(Collectors.toList()));
		}

		/**
		 * Create a scatterplot using collections of numeric values.
		 * @param name 
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x
		 * @param y
		 * @return this builder
		 */
		public ScatterchartBuilder series(String name, Collection<? extends Number> x, Collection<? extends Number> y) {
			return series(name,
					x.stream().mapToDouble(xx -> xx.doubleValue()).toArray(),
					y.stream().mapToDouble(yy -> yy.doubleValue()).toArray());
		}
		
		/**
		 * Create a scatterplot using arrays of numeric values.
		 * @param name 
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @return this builder
		 */
		public ScatterchartBuilder series(String name, double[] x, double[] y) {
			return series(name, x, y, (List<?>)null);
		}
		
		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 * @param name 
		 * @param <T> 
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @param extra array of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public <T> ScatterchartBuilder series(String name, double[] x, double[] y, T[] extra) {
			return series(name, x, y, extra == null ? null : Arrays.asList(extra));
		}
		
		/**
		 * Create a scatterplot using collections of numeric values, with an associated custom object.
		 * @param name 
		 * @param <T> 
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param x x-values
		 * @param y y-values
		 * @param extra list of values to associate with each data point; should be the same length as x and y
		 * @return this builder
		 */
		public <T> ScatterchartBuilder series(String name, double[] x, double[] y, List<T> extra) {
			List<Data<Number, Number>> data = new ArrayList<>();
			for (int i = 0; i < x.length; i++) {
				if (extra != null && i < extra.size())
					data.add(new Data<>(x[i], y[i], extra.get(i)));
				else
					data.add(new Data<>(x[i], y[i]));				
			}
			return series(name, data);
		}
		
		/**
		 * Create a scatterplot from existing data plots.
		 * @param name 
		 * @name the name of the data series (useful if multiple series will be plot, otherwise may be null)
		 * @param data the data points to plot
		 * @return this builder
		 */
		public ScatterchartBuilder series(String name, Collection<Data<Number, Number>> data) {
			int n;
			if (maxDatapoints == null) {
				n = DEFAULT_MAX_DATAPOINTS;
			} else
				n = maxDatapoints.intValue();
			if (data.size() > n) {
				System.err.println("Subsampling " + data.size() + " data points to " + n);
				var list = new ArrayList<>(data);
				Collections.shuffle(list);
				data = list.subList(0, n);
			}
			if (data instanceof ObservableList)
				series.add(new XYChart.Series<>(name, (ObservableList<Data<Number, Number>>)data));
			else
				series.add(new XYChart.Series<>(name, FXCollections.observableArrayList(data)));
			return this;
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
			return new ScatterChart<Number, Number>(xAxis, yAxis);
		}

		/**
		 * Try to select an object if possible (e.g. because a user clicked on it).
		 * @param pathObject the object to select
		 * @param addToSelection if true, add to an existing selection; if false, reset any current selection
		 * @param centerObject if true, try to center it in a viewer (if possible)
		 */
		private void tryToSelect(PathObject pathObject, boolean addToSelection, boolean centerObject) {
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
		protected ScatterchartBuilder getThis() {
			return this;
		}
		
	}
	
	/**
	 * Create a {@link ScatterchartBuilder} for generating a custom scatter plot.
	 * @return the builder
	 */
	public static ScatterchartBuilder scatterChart() {
		return new ScatterchartBuilder();
	}
	
	/**
	 * Create a {@link PieChartBuilder} for generating a custom pie chart.
	 * @return the builder
	 */
	public static PieChartBuilder pieChart() {
		return new PieChartBuilder();
	}
	

}