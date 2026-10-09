package qupath.lib.gui.charts.builders;

import java.util.Collection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.charts.impl.BoxplotChart;
import qupath.lib.objects.PathObject;

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

    @Override
    protected void updateChart(BoxplotChart<String, Number> chart) {
        super.updateChart(chart);
        chart.getData().setAll(getSeries());
        chart.getCanvas().addEventHandler(MouseEvent.ANY, createCanvasMouseHandler(chart));
    }

}
