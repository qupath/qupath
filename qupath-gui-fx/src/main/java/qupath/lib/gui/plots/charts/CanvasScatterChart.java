package qupath.lib.gui.plots.charts;

import com.sun.javafx.charts.Legend;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.ValueAxis;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
import javafx.util.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanvasScatterChart<X,Y> extends ScatterChart<X,Y> implements CanvasChart<X,Y> {
    private static final Logger logger = LoggerFactory.getLogger(CanvasScatterChart.class);

    private final Map<String, Color> colorMap = new HashMap<>();
    private final GeometryFactory gf;
    private final Quadtree tree;
    private int colorIdx = 0;
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;
    public static final int NO_SHUFFLE_SEED = -1;


    // List of all objects to display - we retain this only so that we can shuffle reproducibly if the seed changes
    private final ObservableList<Pair<String, Data<X,Y>>> allData = FXCollections.observableArrayList();
    // Shuffled objects - this is the main list we use, in preference to allData
    private final ObservableList<Pair<String, Data<X,Y>>> shuffledData = FXCollections.observableArrayList();

    private final DoubleProperty markerRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);

    // todo implement subsampling
    private final BooleanProperty autorangeToFullData = new SimpleBooleanProperty(true);
    private final IntegerProperty maxPoints = new SimpleIntegerProperty(1);
    private final IntegerProperty randomSeed = new SimpleIntegerProperty(123);

    private final Random random = new Random(randomSeed.get());


    /**
     * Construct a CanvasScatterChart with the two axes and the defined color map.
     * @param xAxis the x-axis for this chart
     * @param yAxis the y-axis for this chart
     */
    public CanvasScatterChart(Axis<X> xAxis, Axis<Y> yAxis) {
        this(xAxis, yAxis, Map.of());
    }

    /**
     * Construct a CanvasScatterChart with the two axes and the defined color map.
     * @param xAxis the x-axis for this chart
     * @param yAxis the y-axis for this chart
     * @param colorMap the mapping from series names to colors (can be empty)
     */
    public CanvasScatterChart(Axis<X> xAxis, Axis<Y> yAxis, Map<String, Color> colorMap) {
        super(xAxis, yAxis);
        this.colorMap.putAll(colorMap);
        this.tree = new Quadtree();
        this.gf = new GeometryFactory();
        initProperties();
    }

    private void initProperties() {
        // unsure if this is idiomatic
        markerOpacity.subscribe(this::redraw);
        markerOpacity.addListener((v, o, n) -> {
            if (n.doubleValue() <= 0 || n.doubleValue() > 1 || !Double.isFinite(n.doubleValue())) markerOpacity.set(o.doubleValue());
        });
        markerRadius.subscribe(this::redraw);
        markerRadius.addListener((v, o, n) -> {
            if (n.doubleValue() <= 0 || !Double.isFinite(n.doubleValue())) markerRadius.set(o.doubleValue());
        });
        randomSeed.addListener(_ -> {
            logger.warn("randomSeed currently does nothing in CanvasScatterChart");
        });
        maxPoints.addListener(_ -> {
            logger.warn("maxPoints currently does nothing in CanvasScatterChart");
        });
        autorangeToFullData.addListener(_ -> {
            logger.warn("autorangeToFullData currently does nothing in CanvasScatterChart");
        });

        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });
    }

    @Override
    public Optional<Data<X,Y>> findDataPoint(double x, double y, double tolerance) {
        // translate from canvas coords to data scale
        double width = getXAxis().getWidth();
        double height = getYAxis().getHeight();
        // we know they're valueAxes as there's only value or category for now...
        // but this assumption may not hold in future
        @SuppressWarnings("rawtypes") var vax = (ValueAxis)getXAxis();
        @SuppressWarnings("rawtypes") var vay = (ValueAxis)getYAxis();
        double rx = Math.abs(vax.getUpperBound() - vax.getLowerBound());
        double ry = Math.abs(vay.getUpperBound() - vay.getLowerBound());
        double xPerPix = rx / width;
        double yPerPix = ry / height;
        double tolX = tolerance * xPerPix;
        double tolY = tolerance * yPerPix;

        logger.debug("Querying tree at X: {}, Y: {}; tol X: {}, tol Y: {}", x, y, tolX, tolY);
        Envelope search = new Envelope(
                (double)getXAxis().getValueForDisplay(x - tolX),
                (double)getXAxis().getValueForDisplay(x + tolX),
                (double)getYAxis().getValueForDisplay(y - tolY),
                (double)getYAxis().getValueForDisplay(y + tolY)
        );

        List<Data<X,Y>> candidates = tree.query(search);
        logger.debug("{} candidates found", candidates.size());

        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = getMarkerRadius();
        // these are actually the xy mouse coords
        Coordinate clickCoord = new Coordinate(x, y);
        for (Data<X,Y> candidate : candidates) {
            // candidate values are on data scale
            double distance = new Coordinate(
                    getXAxis().getDisplayPosition(candidate.getXValue()),
                    getYAxis().getDisplayPosition(candidate.getYValue())
            ).distance(clickCoord);
            if (distance <= minDistance && distance < maxDistance) {
                minDistance = distance;
                closestPoint = candidate;
            }
        }
        logger.debug("Minimum distance {}", minDistance);
        return Optional.ofNullable(closestPoint);
    }

    /**
     * The radius of markers on this chart
     * @return the property corresponding to marker radius
     */
    public DoubleProperty markerRadiusProperty() {
        return markerRadius;
    }

    /**
     * Get the current marker radius
     * @return the marker radius
     */
    public double getMarkerRadius() {
        return markerRadius.get();
    }

    /**
     * Set the marker size
     * @param value the new value
     */
    public void setMarkerRadius(double value) {
        this.markerRadius.set(value);
    }

    /**
     * The opacity of markers in this plot
     * @return the property corresponding to marker opacity
     */
    public DoubleProperty markerOpacityProperty() {
        return markerOpacity;
    }

    /**
     * Get the current marker opacity
     * @return the marker opacity in [0, 1)
     */
    public double getMarkerOpacity() {
        return markerOpacity.get();
    }

    /**
     * Set the opacity used to draw markers
     * @param value the new value in [0, 1)
     */
    public void setMarkerOpacity(double value) {
        this.markerOpacity.set(value);
    }

    /**
     * Whether the plot autoranges to the full data, or only the visible data
     * @return the observable property
     */
    public BooleanProperty autorangeToFullDataProperty() {
        return this.autorangeToFullData;
    }

    /**
     * Get whether the plot autoranges to the full data, or only the visible data
     * @return the current value
     */
    public boolean getAutorangeToFull() {
        return this.autorangeToFullData.get();
    }

    /**
     * Control whether the plot autoranges to the full data, or only the visible data
     * @param value the new value
     */
    public void setAutorangeToFullData(boolean value) {
        this.autorangeToFullData.set(value);
    }

    /**
     * Get the observable property for the maximum number of points displayed
     * @return the property
     */
    public IntegerProperty maxPointsProperty() {
        return maxPoints;
    }

    /**
     * Get the current maximum number of points displayed
     * @return the current value
     */
    public double getMaxPoints() {
        return maxPoints.get();
    }

    /**
     * Set the current maximum points displayed
     * @param value the new value
     */
    public void setMaxPoints(int value) {
        this.maxPoints.set(value);
    }

    /**
     * The random seed property
     * @return the property
     */
    public IntegerProperty randomSeedProperty() {
        return randomSeed;
    }

    /**
     * Set the random seed
     * @return the current seed
     */
    public int getRandomSeed() {
        return randomSeed.get();
    }

    /**
     * Set the random seed
     * @param value the new value
     */
    public void setRandomSeed(int value) {
        this.random.setSeed(value);
    }

    @Override
    public Canvas getCanvas() {
        return canvas;
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        tree.insert(createEnvelope(item), item);
        allData.add(new Pair<>(series.getName(), item));
    }

    @Override
    protected void dataItemRemoved(Data<X, Y> item, Series<X, Y> series) {
        allData.stream()
                .filter(p -> p.getValue().equals(item))
                .findFirst()
                .ifPresent(allData::remove);
        tree.remove(createEnvelope(item), item);
        removeDataItemFromDisplay(series, item);
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        requestChartLayout();
    }

    @Override
    protected void seriesAdded(Series<X, Y> series, int seriesIndex) {
        for (int j = 0; j < series.getData().size(); j++) {
            var item = series.getData().get(j);
            dataItemAdded(series, j, item);
        }
    }

    @Override
    protected void seriesRemoved(Series<X, Y> series) {
        requestChartLayout();
    }

    private Envelope createEnvelope(Data<X, Y> item) {
        // cannot create tree with XY coords because axes may not be initiated
        // also keeping it on data scale means it does not need to be update
        Point p = gf.createPoint(new Coordinate(
                (Double) item.getXValue(),
                (Double) item.getYValue())
        );
        return p.getEnvelopeInternal();
    }

    @Override
    protected void layoutPlotChildren() {
        getPlotChildren().clear();
        getPlotChildren().add(canvas);

        // can't select parent of plot children (might be empty), but scenicView to the rescue
        Pane plotContent = (Pane) lookup(".chart-content");

        if (plotContent != null) {
            canvas.widthProperty().bind(plotContent.widthProperty());
            canvas.heightProperty().bind(plotContent.heightProperty());
            canvas.widthProperty().addListener((v, o, n) ->
                    redrawNeeded = true);
            canvas.heightProperty().addListener((v, o, n) ->
                    redrawNeeded = true);
        }

        redraw();
    }

    private void shuffleData() {
        var seed = randomSeed.get();
        if (seed == NO_SHUFFLE_SEED) {
            shuffledData.setAll(allData);
        } else {
            var toShuffle = new ArrayList<>(allData);
            Collections.shuffle(toShuffle, new Random(seed));
            shuffledData.setAll(toShuffle);
        }
    }

    private void redraw() {
        var context = canvas.getGraphicsContext2D();
        context.setGlobalAlpha(markerOpacity.get());
        context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        List<Pair<String, Data<X,Y>>> allPoints = new ArrayList<>();
        for (Series<X,Y> series : getData()) {
            for (Data<X, Y> elem: series.getData()) {
                var p = new Pair<>(series.getName(), elem);
                allPoints.add(p);
            }
        }
        Random random = new Random(123);
        Collections.shuffle(allPoints, random);
        double rad = getMarkerRadius();
        for (var pair: allPoints) {
            var color = getColor(pair.getKey());
            context.setFill(color);
            // fillOval uses bounding box coords
            context.fillOval(
                    getXAxis().getDisplayPosition(pair.getValue().getXValue()) - rad,
                    getYAxis().getDisplayPosition(pair.getValue().getYValue()) - rad,
                    rad * 2, rad * 2);

        }

    }

    @Override
    protected void updateLegend() {
        List<Legend.LegendItem> legendList = new ArrayList<>();
        if (getData() != null) {
            for (Series<X,Y> series : getData()) {
                legendList.add(createLegendItem(series));
            }
        }
        Legend legend = new Legend();
        legend.getItems().setAll(legendList);
        if (!legendList.isEmpty()) {
            setLegend(legend);
        } else {
            setLegend(null);
        }
    }

    Legend.LegendItem createLegendItem(Series<X,Y> series) {
        Legend.LegendItem legendItem = new Legend.LegendItem(series.getName());
        legendItem.setSymbol(new Circle(5, getColor(series.getName())));
        return legendItem;
    }

    private Color getColor(String name) {
        return colorMap.computeIfAbsent(name, _ -> getNextColor());
    }

    private Color getNextColor() {
        return getDefaultColors().get(colorIdx++);
    }

    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            handlePulse();
        }

    };

    private void handlePulse() {
        if (redrawNeeded) {
            redraw();
        }
        redrawNeeded = false;
    }

}
