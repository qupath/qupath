package qupath.lib.gui.tools;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

public class ChartBuilder {
	
	public enum ChartType {
		Area,
		Scatter,
		Line
	}
	
	private QuPathViewer viewer;
	private ImageData<?> imageData;
	
	private ChartType type;
	private String title;
	private boolean legendVisible;
	
	private String xLabel, yLabel;
	private double markerOpacity = 1.0;
	
	private double width = -1;
	private double height = -1;
	
	private ObservableList<Series<Number, Number>> series = FXCollections.observableArrayList();
	
	public ChartBuilder(ChartType type) {
		this.type = type;
	}
	
	public static ChartBuilder buildScatterPlot() {
		return new ChartBuilder(ChartType.Scatter);
	}
	
	public static ChartBuilder buildLinePlot() {
		return new ChartBuilder(ChartType.Line);
	}
	
	public static ChartBuilder buildAreaPlot() {
		return new ChartBuilder(ChartType.Area);
	}
	
	public ChartBuilder title(String title) {
		this.title = title;
		return this;
	}
	
	public ChartBuilder legend() {
		return legend(true);
	}
	
	public ChartBuilder legend(boolean show) {
		this.legendVisible = show;
		return this;
	}
	
	public ChartBuilder markerOpacity(double opacity) {
		this.markerOpacity = GeneralTools.clipValue(opacity, 0, 1);
		return this;
	}
	
	
	public <T> ChartBuilder centroids(Collection<? extends PathObject> pathObjects) {
		var cal = imageData == null ? PixelCalibration.getDefaultInstance() : imageData.getServer().getPixelCalibration();
		return centroids(pathObjects, cal);
	}
	
	
	public <T> ChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal) {
		xLabel("x (" + cal.getPixelWidthUnit() + ")");
		yLabel("y (" + cal.getPixelHeightUnit() + ")");
		return series(
				null,
				pathObjects,
				(PathObject p) -> PathObjectTools.getROI(p, true).getCentroidX() * cal.getPixelWidth().doubleValue(),
				(PathObject p) -> -PathObjectTools.getROI(p, true).getCentroidY() * cal.getPixelHeight().doubleValue());
	}
	
	
	public <T> ChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
		xLabel(xMeasurement);
		yLabel(yMeasurement);
		return series(
				null,
				pathObjects,
				(PathObject p) -> p.getMeasurementList().getMeasurementValue(xMeasurement),
				(PathObject p) -> p.getMeasurementList().getMeasurementValue(yMeasurement));
	}
	
	public <T> ChartBuilder series(String name, Collection<? extends T> collection, Function<T, Number> xFun, Function<T, Number> yFun) {
		return series(name,
				collection.stream()
				.map(p -> new XYChart.Data<>(xFun.apply(p), yFun.apply(p), p))
				.collect(Collectors.toList()));
	}

	public ChartBuilder series(String name, Collection<? extends Number> x, Collection<? extends Number> y) {
		return series(name,
				x.stream().mapToDouble(xx -> xx.doubleValue()).toArray(),
				y.stream().mapToDouble(yy -> yy.doubleValue()).toArray());
	}
	
	public ChartBuilder series(String name, double[] x, double[] y) {
		return series(name, x, y, (List)null);
	}
	
	public <T> ChartBuilder series(String name, double[] x, double[] y, T[] extra) {
		return series(name, x, y, extra == null ? null : Arrays.asList(extra));
	}
	
	public <T> ChartBuilder series(String name, double[] x, double[] y, List<T> extra) {
		ObservableList<Data<Number, Number>> data = FXCollections.observableArrayList();
		for (int i = 0; i < x.length; i++) {
			if (extra != null && i < extra.size())
				data.add(new Data<>(x[i], y[i], extra.get(i)));
			else
				data.add(new Data<>(x[i], y[i]));				
		}
		series.add(new XYChart.Series<>(name, data));
		return this;
	}
	
	public <T> ChartBuilder series(String name, List<Data<Number, Number>> data) {
		if (data instanceof ObservableList)
			series.add(new XYChart.Series<>(name, (ObservableList<Data<Number, Number>>)data));
		else
			series.add(new XYChart.Series<>(name, FXCollections.observableArrayList(data)));
		return this;
	}

	public ChartBuilder xLabel(String label) {
		this.xLabel = label;
		return this;
	}
	
	public ChartBuilder yLabel(String label) {
		this.yLabel = label;
		return this;
	}
	
	public ChartBuilder imageData(ImageData<?> imageData) {
		this.imageData = imageData;
		return this;		
	}
	
	public ChartBuilder viewer(QuPathViewer viewer) {
		this.viewer = viewer;
		return this;
	}
	
	public ChartBuilder width(double width) {
		this.width = width;
		return this;
	}
	
	public ChartBuilder height(double height) {
		this.height = height;
		return this;
	}
	
	public ChartBuilder size(double width, double height) {
		this.width = width;
		this.height = height;
		return this;
	}
	
	public Chart build() {
		XYChart<Number, Number> chart;
		var xAxis = new NumberAxis();
		var yAxis = new NumberAxis();
		switch(type) {
		case Area:
			chart = new AreaChart<>(xAxis, yAxis, series);
			break;
		case Line:
			chart = new LineChart<>(xAxis, yAxis, series);
			break;
		case Scatter:
			chart = new ScatterChart<>(xAxis, yAxis, series);			
			break;
		default:
			throw new IllegalArgumentException("Unknown chart type " + type);
		}
		chart.setTitle(title);
		chart.setLegendVisible(legendVisible);
		
		if (xLabel != null)
			xAxis.setLabel(xLabel);
		if (yLabel != null)
			yAxis.setLabel(yLabel);
		
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
		
		return chart;
	}
	
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
	
	
	public Stage toStage() {
		return toStage(title);
	}
	
	public Stage toStage(String windowTitle) {
		var stage = new Stage();
		if (windowTitle != null)
			stage.setTitle(windowTitle);
		stage.setScene(new Scene(build()));
		return stage;
	}

}
