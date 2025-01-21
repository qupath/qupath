package qupath.lib.gui.charts;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public class ScatterPlotChart extends ScatterChart<Number, Number> {
    private final IntegerProperty rngSeed = new SimpleIntegerProperty(42);
    private final DoubleProperty pointOpacity = new SimpleDoubleProperty(1);
    private final DoubleProperty pointSize = new SimpleDoubleProperty(5);
    private final IntegerProperty maxPoints = new SimpleIntegerProperty(10000);
    private final BooleanProperty drawGrid = new SimpleBooleanProperty(true);
    private final BooleanProperty drawAxes = new SimpleBooleanProperty(true);
    private final StringProperty xLabel = new SimpleStringProperty();
    private final StringProperty yLabel = new SimpleStringProperty();
    private final ObservableList<Data<Number, Number>> data = FXCollections.observableArrayList(); // the entire possible dataset
    private final ObservableList<Series<Number, Number>> series = FXCollections.observableArrayList(); // the data that are plotted
    private final QuPathViewer viewer;


    public void setMaxPoints(int maxPoints) {
        this.maxPoints.set(maxPoints);
    }
    public void setDrawGrid(boolean drawGrid) {
        this.drawGrid.set(drawGrid);
    }
    public void setDrawAxes(boolean drawAxes) {
        this.drawAxes.set(drawAxes);
    }
    public void setRNG(int rngSeed) {
        this.rngSeed.set(rngSeed);
    }
    public void setPointOpacity(double pointOpacity) {
        this.pointOpacity.set(pointOpacity);
    }
    public void setPointSize(double pointSize) {
        this.pointSize.set(pointSize);
    }


    public ScatterPlotChart(QuPathViewer viewer) {
        super(new NumberAxis(), new NumberAxis());
        this.viewer = viewer;
        pointOpacity.addListener((obs, oldV, newV) -> updateChart());
        pointSize.addListener((obs, oldV, newV) -> updateChart());
        drawGrid.addListener((obs, oldV, newV) -> {
            setHorizontalGridLinesVisible(newV);
            setVerticalGridLinesVisible(newV);
        });
        drawAxes.addListener((obs, oldV, newV) -> {
            getXAxis().setTickLabelsVisible(newV);
            getYAxis().setTickLabelsVisible(newV);
        });
        maxPoints.addListener((obs, oldV, newV) -> {
            resampleAndUpdate();
        });
        xLabel.addListener((obs, oldV, newV) -> {
            getXAxis().setLabel(newV);
        });
        yLabel.addListener((obs, oldV, newV) -> {
            getYAxis().setLabel(newV);
        });
        rngSeed.addListener((obs, oldV, newV) -> resampleAndUpdate());
        setHorizontalGridLinesVisible(drawGrid.get());
        setVerticalGridLinesVisible(drawGrid.get());
        getXAxis().setTickLabelsVisible(drawGrid.get());
        getYAxis().setTickLabelsVisible(drawAxes.get());

        setAnimated(false);

        series.add(new Series<>());
        setData(series);

        resampleAndUpdate();
    }

    public void resampleAndUpdate() {
        Collections.shuffle(data, new Random(rngSeed.get()));
        series.getFirst().getData().clear();
        series.getFirst().getData().setAll(data.subList(0, Math.min(maxPoints.get(), data.size())));
        updateChart();
    }

    public void setDataFromMeasurements(Collection<PathObject> pathObjects, String xMeas, String yMeas) {
        xLabel(xMeas);
        yLabel(yMeas);
        this.data.clear();
        this.data.addAll(measurements(pathObjects, xMeas, yMeas).getData());
        resampleAndUpdate();
    }

    /**
     * Create a data series from two measurements for the specified objects.
     * @param pathObjects the objects to plot
     * @param xMeasurement the measurement to extract from each object's measurement list for the x location
     * @param yMeasurement the measurement to extract from each object's measurement list for the y location
     * @return a series of data
     */
    private Series<Number, Number> measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
        return series(
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
    private static <T> Series<Number, Number> series(String name, Collection<? extends T> collection, Function<T, Number> xFun, Function<T, Number> yFun) {
        return series(name,
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
    private static Series<Number, Number> series(String name, Collection<? extends Number> x, Collection<? extends Number> y) {
        return series(name,
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
    private static Series<Number, Number> series(String name, double[] x, double[] y) {
        return series(name, x, y, (List<?>)null);
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
    private static <T> Series<Number, Number> series(String name, double[] x, double[] y, T[] extra) {
        return series(name, x, y, extra == null ? null : Arrays.asList(extra));
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
    private static <T> Series<Number, Number> series(String name, double[] x, double[] y, List<T> extra) {
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
     * Create a series of data from existing data sets.
     * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
     * @param data the data points to plot
     * @return a series of data
     */
    public static Series<Number, Number> series(String name, Collection<Data<Number, Number>> data) {
        return new XYChart.Series<>(name, FXCollections.observableArrayList(data));
    }

    public void xLabel(String value) {
        this.xLabel.set(value);
    }

    public void yLabel(String value) {
        this.yLabel.set(value);
    }

    protected void updateChart() {
        setLegendVisible(false);

        // If we have a hierarchy, and PathObjects, make the plot live
        for (var s: series) {
            for (var d : s.getData()) {
                var extra = d.getExtraValue();
                var node = d.getNode();
                if (extra instanceof PathObject pathObject && node != null) {
                    Integer color = ColorToolsFX.getDisplayedColorARGB(pathObject);
                    String style = String.format(
                            "-fx-background-color: rgb(%d, %d, %d, %.2f); " +
                                    "-fx-background-radius: " + pointSize.intValue() + "px ; " +
                                    "-fx-padding: " + pointSize.intValue() + "px ; "
                            ,
                            ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), pointOpacity.get()
                    );
                    node.setStyle(style);
                    node.addEventHandler(MouseEvent.ANY, e -> {
                        if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
                            tryToSelect((PathObject)extra, viewer.getImageData(), viewer, e.isShiftDown(), e.getClickCount() == 2);
                        else if (e.getEventType() == MouseEvent.MOUSE_ENTERED)
                            node.setStyle(style + ";"
                                    + "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.5), 4, 0, 1, 1);");
                        else if (e.getEventType() == MouseEvent.MOUSE_EXITED)
                            node.setStyle(style);
                    });
                }
            }
        }
        requestChartLayout(); // this ensure CSS resizing is actually applied...
    }

    /**
     *
     * Try to select an object if possible (e.g. because a user clicked on it).
     * @param pathObject the object to select
     * @param addToSelection if true, add to an existing selection; if false, reset any current selection
     * @param centerObject if true, try to center it in a viewer (if possible)
     */
    private static void tryToSelect(PathObject pathObject, ImageData<?> imageData, QuPathViewer viewer, boolean addToSelection, boolean centerObject) {
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

}
