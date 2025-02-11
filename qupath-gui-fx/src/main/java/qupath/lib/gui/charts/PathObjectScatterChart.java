package qupath.lib.gui.charts;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * An interactive {@link ScatterChart} implementation for showing large(ish) numbers of {@link PathObject},
 * optionally linked to a {@link QuPathViewer}.
 */
public class PathObjectScatterChart extends ScatterChart<Number, Number> {

    private static final Logger logger = LoggerFactory.getLogger(PathObjectScatterChart.class);

    private final IntegerProperty rngSeed = new SimpleIntegerProperty(42);
    private final DoubleProperty pointOpacity = new SimpleDoubleProperty(1);
    private final DoubleProperty pointSize = new SimpleDoubleProperty(5);
    private final IntegerProperty maxPoints = new SimpleIntegerProperty(10000);

    private final List<Data<Number, Number>> data = new ArrayList<>(); // the entire possible dataset
    private final QuPathViewer viewer;

    private final List<PathClass> pathClasses = new ArrayList<>();

    private final Effect shadow = new DropShadow(
            BlurType.THREE_PASS_BOX,
            new Color(0, 0, 0, 0.5d),
            4, 0, 1, 1);

    private final Map<Node, PathObject> nodeMap = new WeakHashMap<>();
    private final EventHandler<MouseEvent> nodeEventHandler = this::handleMouseEvent;

    private final Map<PathObject, Data<Number, Number>> dataMap = new HashMap<>();


    /**
     * Create an interactive scatter plot using the current viewer
     * @param viewer The QuPath viewer.
     */
    public PathObjectScatterChart(QuPathViewer viewer) {
        super(new NumberAxis(), new NumberAxis());
        this.viewer = viewer;
        maxPoints.addListener((obs, oldV, newV) -> {
            if (newV.intValue() < 0) {
                maxPoints.set(0);
            }
            resampleAndUpdate();
        });
        rngSeed.addListener((obs, oldV, newV) -> resampleAndUpdate());
        setAnimated(false);
        resampleAndUpdate();
        this.setLegendVisible(true);
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

    @Override
    protected void updateLegend() {
        List<Label> legendList = new ArrayList<>();
        var series = getData();
        int nSeries = series == null ? 0 : Math.min(series.size(), pathClasses.size());
        for (int i = 0; i < nSeries; i++) {
            var pathClass = pathClasses.get(i);
            var circle = new Circle();
            // Binding to radius & opacity doesn't work great, as we might not see the colors
            circle.setRadius(5.0);
            Integer rgb;
            if (pathClass == PathClass.NULL_CLASS)
                rgb = PathPrefs.colorDefaultObjectsProperty().get();
            else
                rgb = pathClass.getColor();
            circle.setFill(ColorToolsFX.getCachedColor(rgb));
            var item = new Label(pathClass.toString(), circle);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setContentDisplay(ContentDisplay.LEFT);
            legendList.add(item);
        }
        if (!legendList.isEmpty()) {
            var legend = new TilePane();
            legend.getChildren().setAll(legendList);
            setLegend(legend);
        } else {
            setLegend(null);
        }
    }

    private void resampleAndUpdate() {
        int n = maxPoints.get();
        var dataToSet = data;
        if (n < data.size()) {
            // Randomly subsample, if needed
            var dataCopy = new ArrayList<>(data);
            Collections.shuffle(dataCopy, new Random(rngSeed.get()));
            dataToSet = dataCopy.subList(0, n);
        }
        List<Series<Number, Number>> allSeries = new ArrayList<>();
        for (PathClass pc : pathClasses) {
            var toCheck = pc == null ? PathClass.NULL_CLASS : pc;
            var data = dataToSet.stream()
                    .filter(d -> ((PathObject)d.getExtraValue()).getPathClass() == toCheck)
                    .toList();
            var series = new Series<Number, Number>();
            series.setName(pc == null ? "Unclassified" : pc.toString());
            series.getData().setAll(data);
            allSeries.add(series);
        }
        // It can help to add the longest series first; when there are a lot of points, this slightly reduces
        // the risk of completely obscuring small series
        allSeries.sort(Comparator.comparingInt((Series<Number, Number> s) -> s.getData().size()).reversed());

        getData().setAll(allSeries);
        updateChart();
        updateLegend();
    }

    void setDataFromTable(Collection<? extends PathObject> pathObjects,
                          PathTableData<PathObject> model,
                          String xMeasurement, String yMeasurement) {
        this.data.clear();
        Set<PathClass> pathClasses = new HashSet<>();
        var newDataMap = new HashMap<PathObject, Data<Number, Number>>();
        if (xMeasurement != null && yMeasurement != null) {
            for (var pathObject : pathObjects) {
                double x = model.getNumericValue(pathObject, xMeasurement);
                double y = model.getNumericValue(pathObject, yMeasurement);
                var item = getItem(pathObject, x, y);
                this.data.add(item);
                newDataMap.put(pathObject, item);

                var pathClass = pathObject.getPathClass();
                pathClasses.add(Objects.requireNonNullElse(pathClass, PathClass.NULL_CLASS));
            }
        }
        // We don't want to store lots of unused entries in the data map, so trim it down to size
        if (dataMap.size() > newDataMap.size()) {
            dataMap.clear();
            dataMap.putAll(newDataMap);
        }
        // Sort the classes so that they appear nicely in the legend
        this.pathClasses.clear();
        this.pathClasses.addAll(
                pathClasses.stream()
                        .sorted()
                        .toList());
        getXAxis().setLabel(xMeasurement);
        getYAxis().setLabel(yMeasurement);
        resampleAndUpdate();
    }

    private Data<Number, Number> getItem(PathObject pathObject, Number x, Number y) {
        var item = dataMap.computeIfAbsent(pathObject, p -> new Data<>());
        item.setXValue(x);
        item.setYValue(y);
        item.setExtraValue(pathObject);
        Circle circle;
        if (item.getNode() instanceof Circle c) {
            circle = c;
        } else {
            circle = createSymbol();
            nodeMap.put(circle, pathObject);
            item.setNode(circle);
        }
        // Ensure we are coloring properly (this might have changed)
        circle.setFill(ColorToolsFX.getDisplayedColor(pathObject));
        return item;
    }

    private Circle createSymbol() {
        var circle = new Circle();
        circle.radiusProperty().bind(pointSize);
        circle.opacityProperty().bind(pointOpacity);
        circle.addEventHandler(MouseEvent.ANY, nodeEventHandler);
        return circle;
    }


    private void updateChart() {
        setLegendVisible(true);
        requestChartLayout(); // this ensure CSS resizing is actually applied...
    }


    @Override
    protected void dataItemRemoved(Data<Number,Number> item, Series<Number,Number> series) {
        final Node symbol = item.getNode();
        if (symbol != null)
            nodeMap.remove(symbol);
        super.dataItemRemoved(item, series);
    }

    private void handleMouseEvent(MouseEvent event) {
        if (event.getSource() instanceof Circle circle) {
            if (event.getEventType() == MouseEvent.MOUSE_ENTERED)
                circle.setEffect(shadow);
            else if (event.getEventType() == MouseEvent.MOUSE_EXITED)
                circle.setEffect(null);
            else if (event.getEventType() == MouseEvent.MOUSE_CLICKED && viewer != null) {
                var pathObject = nodeMap.getOrDefault(circle, null);
                if (pathObject != null) {
                    Charts.ScatterChartBuilder.tryToSelect(
                            pathObject, viewer, viewer.getImageData(),
                            event.isShiftDown(), event.getClickCount() == 2);
                }
            }
        }
    }

}
