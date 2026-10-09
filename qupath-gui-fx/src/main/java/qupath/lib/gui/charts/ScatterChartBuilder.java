package qupath.lib.gui.charts;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Builder for creating scatter charts.
 */
public class ScatterChartBuilder extends Charts.XYNumberChartBuilder<ScatterChartBuilder, ScatterChart<Number, Number>> {

    private static final Logger logger = LoggerFactory.getLogger(ScatterChartBuilder.class);
    private static final Integer DEFAULT_MAX_DATAPOINTS = 10_000;

    private Integer maxDatapoints;
    private Random rnd = new Random();
    private final Map<String, Color> colorMap = new HashMap<>();

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

    public ScatterChartBuilder colorMap(Map<String, Color> colorMap) {
        this.colorMap.putAll(colorMap);
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
     * todo option to split by class
     * Plot centroids for the specified objects using a fixed pixel calibration.
     *
     * @param pathObjects the objects to plot
     * @param cal         the pixel calibration used to convert the centroids into other units
     * @return this builder
     */
    public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal) {
        xLabel("x (" + cal.getPixelWidthUnit() + ")");
        yLabel("y (" + cal.getPixelHeightUnit() + ")");
        return addSeries(
                null,
                pathObjects,
                (PathObject p) -> PathObjectTools.getROI(p, true).getCentroidX() * cal.getPixelWidth().doubleValue(),
                (PathObject p) -> -PathObjectTools.getROI(p, true).getCentroidY() * cal.getPixelHeight().doubleValue());
    }

    private Map<String, Color> makeColorMap() {
        Map<String, Color> colorMap = new HashMap<>();
        // if the scatter chart wraps PathObjects, fetch the class colors. Otherwise, trust the defaults
        var series = this.getSeries();
        if (series != null && !series.isEmpty() &&
                series.getFirst().getData() != null && series.getFirst().getData().getFirst() != null) {
            var data = series.getFirst().getData().getFirst();
            if (data.getExtraValue() instanceof PathObject) {
                colorMap.putAll(
                        this.getSeries().stream()
                                .map(XYChart.Series::getName)
                                .collect(Collectors.toMap(
                                        Function.identity(),
                                        (s) -> {
                                            if (s.equals("Unclassified")) {
                                                return ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get());
                                            } else {
                                                return ColorToolsFX.getCachedColor(PathClass.getInstance(s).getColor());
                                            }
                                        })
                                )
                );
            }
        }
        return colorMap;
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
     * Plot two measurements against one another for the specified objects.
     *
     * @param pathObjects  the objects to plot
     * @param xMeasurement the measurement to extract from each object's measurement list for the x location
     * @param yMeasurement the measurement to extract from each object's measurement list for the y location
     * @return this builder
     */
    public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
        return measurements(pathObjects, xMeasurement, yMeasurement, true);
    }

    /**
     * todo
     * @param pathObjects
     * @param xMeasurement
     * @param yMeasurement
     * @param seriesByClass
     * @return
     */
    public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement, boolean seriesByClass) {
        xLabel(xMeasurement);
        yLabel(yMeasurement);
        if (seriesByClass) {
            var pathClasses = pathObjects.stream().map(PathObject::getPathClass).distinct().toList();
            for (var pathClass : pathClasses) {
                addSeries(
                        pathClass == null ? PathClass.NULL_CLASS.toString() : pathClass.toString(),
                        pathObjects.stream().filter(po -> po.getPathClass() == pathClass).toList(),
                        (PathObject p) -> p.getMeasurementList().get(xMeasurement),
                        (PathObject p) -> p.getMeasurementList().get(yMeasurement)
                );
            }
            return this;
        } else {
            return addSeries(
                    null,
                    pathObjects,
                    (PathObject p) -> p.getMeasurementList().get(xMeasurement),
                    (PathObject p) -> p.getMeasurementList().get(yMeasurement));
        }
    }


    /**
     * Create and add a scatterplot using arrays of numeric values.
     *
     * @param name the name of the data series (useful if multiple series will be plot, otherwise may be null)
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
            // we know this is always a scatter chart because it's all we build; if we ever change that this case is dangerous obviously
            @SuppressWarnings("unchecked") CanvasChart<Number, Number> canvasChart = (CanvasChart<Number, Number>) chart;
            canvasChart.getCanvas().addEventHandler(MouseEvent.ANY, createCanvasMouseHandler(canvasChart));
        }
    }

    @Override
    protected ScatterChart<Number, Number> createNewChart(Axis<Number> xAxis, Axis<Number> yAxis) {
        // todo move the color code to a setter, and auto-set in the pathobject methods
        var chart = new CanvasScatterChart<>(xAxis, yAxis, colorMap);
        chart.setMarkerOpacity(this.markerOpacity);
        chart.setMarkerRadius(this.markerSize);
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
