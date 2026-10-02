package qupath.lib.gui.charts.builders;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.charts.impl.BoxplotChart;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.ProjectImageEntry;

public class BoxplotChartBuilder extends Charts.XYCategoryChartBuilder<BoxplotChartBuilder, BoxplotChart<String, Number>> {
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

    /**
     * Make a boxplot of string metadata values against numeric ones.
     * <br>
     * Note that currently we assume the x- values are String and the y- are numeric, but it could be the other way around!
     * @param projectImageEntries the images to collect the metadata values from
     * @param xMeasurement the metadata values to plot on the x-axis
     * @param yMeasurement the metadata values to plot on the y-axis
     */
    public BoxplotChartBuilder metadata(Collection<? extends ProjectImageEntry<BufferedImage>> projectImageEntries, String xMeasurement, String yMeasurement) {
        xLabel(xMeasurement);
        yLabel(yMeasurement);
        // todo neater way to deal with missing values
        var imageEntryList = projectImageEntries.stream()
                .filter(e -> e.getMetadata().get(xMeasurement) != null && e.getMetadata().get(yMeasurement) != null)
                .toList();
        String[] x = imageEntryList.stream()
                .map(it -> it.getMetadata().get(xMeasurement))
                .toArray(String[]::new);
        Number[] y = imageEntryList.stream().map(it -> {
            String yVal = it.getMetadata().get(yMeasurement);
            if (yVal == null) {
                return Double.NaN;
            }
            return Double.parseDouble(yVal);
        }).toArray(Number[]::new);
        return addSeries(
                createSeries(
                        null,
                        x,
                        y,
                        imageEntryList
        ));
    }

    @Override
    protected void updateChart(BoxplotChart<String, Number> chart) {
        super.updateChart(chart);
        chart.getData().setAll(getSeries());
        // todo refactor similar methods somehow
        chart.getCanvas().addEventHandler(MouseEvent.ANY, e -> {
            if (e.getEventType() == MouseEvent.MOUSE_CLICKED) {
                double pixelTolerance = markerOpacity * 1.5;
                Optional<XYChart.Data<String,Number>> item = chart.findDataPoint(e.getX(), e.getY(), pixelTolerance);
                item.ifPresent((data) ->
                        tryToSelect(
                            (PathObject) data.getExtraValue(),
                            e.isShiftDown(),
                            e.getClickCount() == 2));
            }
        });
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
