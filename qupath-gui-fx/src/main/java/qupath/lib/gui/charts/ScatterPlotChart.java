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
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;

import java.util.Collection;
import java.util.Collections;
import java.util.Random;

/**
 * A scatter plot wrapping the JavaFX chart {@link ScatterChart} to add functionality like changing point size/opacity, selecting PathObjects in a QuPath viewer.
 */
public class ScatterPlotChart extends ScatterChart<Number, Number> {

    private static final Logger logger = LoggerFactory.getLogger(ScatterPlotChart.class);
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

    /**
     * Create an interactive scatter plot using the current viewer
     * @param viewer The QuPath viewer.
     */
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
            if (newV.intValue() < 0) {
                maxPoints.set(0);
            }
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

    /**
     * Set the maximum number of points that the plot will display.
     * Subsampling will be used to ensure only this number of points or fewer are displayed.
     * @param maxPoints The maximum number of elements
     */
    public void setMaxPoints(int maxPoints) {
        this.maxPoints.set(maxPoints);
    }

    /**
     * Set whether gridlines are drawn on the plot.
     * @param drawGrid Whether gridlines are drawn on the plot.
     */
    public void setDrawGrid(boolean drawGrid) {
        this.drawGrid.set(drawGrid);
    }

    /**
     * Set whether gridlines are drawn on the plot.
     * @param drawAxes Whether axis ticks are drawn on the plot.
     */
    public void setDrawAxes(boolean drawAxes) {
        this.drawAxes.set(drawAxes);
    }

    /**
     * Set the RNG seed for subsampling
     * @param rngSeed The random number generator seed
     */
    public void setRNG(int rngSeed) {
        this.rngSeed.set(rngSeed);
    }

    /**
     * Set point opacity
     * @param pointOpacity the point opacity
     */
    public void setPointOpacity(double pointOpacity) {
        this.pointOpacity.set(pointOpacity);
    }

    /**
     * Set point size
     * @param pointSize the point size
     */
    public void setPointSize(double pointSize) {
        this.pointSize.set(pointSize);
    }

    /**
     * Set the X axis title.
     * @param value The axis label
     */
    public void xLabel(String value) {
        this.xLabel.set(value);
    }

    /**
     * Set the Y axis title
     * @param value The axis label
     */
    public void yLabel(String value) {
        this.yLabel.set(value);
    }

    private void resampleAndUpdate() {
        Collections.shuffle(data, new Random(rngSeed.get()));
        series.getFirst().getData().clear();
        series.getFirst().getData().setAll(data.subList(0, Math.min(maxPoints.get(), data.size())));
        updateChart();
    }

    void setDataFromMeasurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
        xLabel(xMeasurement);
        yLabel(yMeasurement);
        this.data.clear();
        this.data.addAll(Charts.ScatterChartBuilder.createSeriesFromMeasurements(pathObjects, xMeasurement, yMeasurement).getData());
        resampleAndUpdate();
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
                            Charts.ScatterChartBuilder.tryToSelect((PathObject)extra, viewer, viewer.getImageData(), e.isShiftDown(), e.getClickCount() == 2);
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



}
