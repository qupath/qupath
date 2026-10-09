package qupath.lib.gui.charts;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Builder for creating bar charts.
 */
public class BarChartBuilder extends Charts.XYCategoryChartBuilder<BarChartBuilder, BarChart<String, Number>> {

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
                .stream().map(xFun)
                .collect(
                        Collectors.groupingBy(Function.identity(), Collectors.counting())
                );
        return addSeries(Charts.XYChartBuilder.createSeries(name,
                classAndCount.entrySet().stream()
                .map(e -> {
                    if (e.getKey() == null)
                        return new XYChart.Data<>(PathClass.NULL_CLASS.toString(), (Number) e.getValue(), PathClass.NULL_CLASS);
                    else
                        return new XYChart.Data<>(e.getKey().toString(), (Number) e.getValue(), e.getKey());
                })
                .sorted(Comparator.comparing(XYChart.Data::getXValue))
                .toList()));
    }


    /**
     * Create a bar chart using a map of String values and associated numeric values.
     *
     * @param name the name of the data series (useful if multiple series will be plotted, otherwise may be null)
     * @param data a map of String values to associated numeric values
     * @return this builder
     */
    public BarChartBuilder series(String name, Map<String, Number> data) {
        var series = Charts.XYChartBuilder.createSeries(
                name,
                data.keySet().stream().toList(),
                data.values().stream().toList(),
                (List<?>) null
        );
        return addSeries(series);
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
                    var color = pathClass.getColor();
                    String style = String.format("-fx-background-color: rgba(%d,%d,%d,%.2f);",
                            ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), markerOpacity);
                    chart.setStyle(style);
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

    /**
     * Get the PathClass or {@link PathClass#NULL_CLASS} for the specified object
     * (but don't return null).
     *
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
