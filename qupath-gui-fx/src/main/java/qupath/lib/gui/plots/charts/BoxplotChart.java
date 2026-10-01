package qupath.lib.gui.plots.charts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.ValueAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.jspecify.annotations.NonNull;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A node-based BoxplotChart
 * @param <X> string or numeric x-axis type
 * @param <Y> string or numeric y-axis type
 */
public class BoxplotChart<X, Y> extends XYChart<X, Y> implements CanvasChart<X, Y> {
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;

    // hackery abounds: use a single private node to mark data points being drawn or not for the findObject method
    private final Node node = new Circle();

    private static final Logger logger = LoggerFactory.getLogger(BoxplotChart.class);
    private final Orientation orientation;
    private final Random random = new Random(42);
    protected final CategoryAxis categoryAxis;
    protected final ValueAxis<Number> valueAxis;
    protected Function<Data<X, Y>, String> getCategory;
    protected Function<Data<X, Y>, Number> getNumeric;

    private final BooleanProperty drawAllPoints = new SimpleBooleanProperty(false);
    private final DoubleProperty markerRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);
    private final IntegerProperty randomSeed = new SimpleIntegerProperty(0);

    // to enable us to lookup points...
    private final Map<Data<X,Y>, Double> jitterValues = new HashMap<>();

    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     */
    public BoxplotChart(Axis<X> xAxis, Axis<Y> yAxis) {
        this(xAxis, yAxis, true);
    }

    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     * @param drawAllPoints whether to draw all points, or only the outliers (outside 1.5*IQR)
     */
    public BoxplotChart(Axis<X> xAxis, Axis<Y> yAxis, boolean drawAllPoints) {
        super(xAxis, yAxis);
        setDrawAllPoints(drawAllPoints);
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();
        });

        Pane plotContent = (Pane) lookup(".chart-content");
        if (plotContent != null) {
            canvas.widthProperty().bind(plotContent.widthProperty());
            canvas.heightProperty().bind(plotContent.heightProperty());
        }

        if (!((xAxis instanceof CategoryAxis && yAxis instanceof ValueAxis) || (yAxis instanceof CategoryAxis && xAxis instanceof ValueAxis))) {
            throw new IllegalArgumentException("Illegal axis types: must supply one Category and one Value axis");
        }
        if (xAxis instanceof CategoryAxis) {
            categoryAxis = (CategoryAxis) xAxis;
            //noinspection unchecked - we know we have one of each
            valueAxis = (ValueAxis<Number>) yAxis;
            orientation = Orientation.HORIZONTAL;
            getCategory = d -> (String) d.getXValue();
            getNumeric = d -> (Number)d.getYValue();
        } else {
            categoryAxis = (CategoryAxis) yAxis;
            //noinspection unchecked - we know we have one of each
            valueAxis = (ValueAxis<Number>) xAxis;
            orientation = Orientation.VERTICAL;
            getNumeric = d -> (Number)d.getXValue();
            getCategory = d -> (String) d.getYValue();
        }

        if (getData() == null) {
            setData(FXCollections.observableArrayList());
        }
        markerRadius.addListener((_) -> layoutPlotChildren());
        markerOpacity.addListener((_, o, n) -> {
            double d = n.doubleValue();
            if (d <= 0 || d > 1 || !Double.isFinite(d)) {
                logger.debug("Marker opacity out of bounds: {}", d);
                markerOpacity.set(o.doubleValue());
                return;
            }
            layoutPlotChildren();
        });
        this.drawAllPoints.addListener(_ -> layoutPlotChildren());
        this.randomSeed.addListener(_ -> {
            jitterValues.clear();
            layoutPlotChildren();
        });
    }

    /**
     * The size of markers on this chart
     * @return the property corresponding to marker size
     */
    public DoubleProperty markerRadiusProperty() {
        return markerRadius;
    }

    /**
     * Set the marker size (diameter)
     * @param value the new value
     */
    public void setMarkerRadius(double value) {
        if (value <= 0 || !Double.isFinite(value)) return;
        this.markerRadius.set(value);
    }

    /**
     * Get the marker size (diameter)
     * @return the current value
     */
    public double getMarkerRadius() {
        return markerRadius.get();
    }

    /**
     * The random seed used to jitter points
     * @return the property corresponding to random seed
     */
    public IntegerProperty randomSeedProperty() {
        return randomSeed;
    }

    /**
     * Set the random seed
     * @param value the new value
     */
    public void setRandomSeed(int value) {
        this.markerRadius.set(value);
    }

    /**
     * Get the random seed
     * @return the current value
     */
    public int getRandomSeed() {
        return randomSeed.get();
    }

    /**
     * Whether to draw all points, or only outliers
     * @return the corresponding boolean property
     */
    public BooleanProperty drawAllPointsProperty() {
        return drawAllPoints;
    }

    /**
     * Control whether to draw all points, or only outliers
     * @param value the new value
     */
    public void setDrawAllPoints(boolean value) {
        this.drawAllPoints.set(value);
    }

    /**
     * Retrieve whether to draw all points, or only outliers
     * @return the current value
     */
    public boolean getDrawAllPoints() {
        return drawAllPoints.get();
    }

    /**
     * The opacity of markers in this plot
     * @return the property corresponding to marker opacity
     */
    public DoubleProperty markerOpacityProperty() {
        return markerOpacity;
    }

    /**
     * Set the opacity of markers in this plot
     * @param value the new value. Must be in (0,1].
     */
    public void setMarkerOpacity(double value) {
        this.markerOpacity.set(value);
    }

    /**
     * Get the current marker opacity
     * @return the current value
     */
    public double getMarkerOpacity() {
        return markerOpacity.get();
    }


    protected double getJitterValue(Data<X,Y> data) {
        return jitterValues.computeIfAbsent(data, (_) -> jitter());
    }

    /**
     * Get the orientation of the plot
     * @return the immutable orientation
     */
    protected Orientation getOrientation() {
        return orientation;
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        requestChartLayout();
    }

    private Node createPoint() {
        var symbol = new StackPane();
        symbol.setAccessibleRole(AccessibleRole.TEXT);
        symbol.setAccessibleRoleDescription("Point");
        symbol.setFocusTraversable(false);
        return symbol;
    }

    @Override
    protected void dataItemRemoved(Data<X, Y> item, Series<X, Y> series) {
        removeDataItemFromDisplay(series, item);
        requestChartLayout();
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        item.setNode(createPoint());
        getPlotChildren().add(item.getNode());
        requestChartLayout();
    }

    @Override
    protected void seriesAdded(Series<X, Y> series, int seriesIndex) {
        for (int j = 0; j < series.getData().size(); j++) {
            Data<X, Y> item = series.getData().get(j);
            dataItemAdded(series, j, item);
        }
        requestChartLayout();
    }

    @Override
    protected void seriesChanged(ListChangeListener.Change<? extends Series> c) {
        requestChartLayout();
    }

    @Override
    protected void seriesRemoved(Series<X, Y> series) {
        requestChartLayout();
    }


    @Override
    protected void layoutPlotChildren() {
        random.setSeed(randomSeed.get()); // todo probably parameterise this
        Map<String, List<Data<X, Y>>> valuesByCategory = collectValuesByCategory();
        resetPlotChildren();
        for (var entry: valuesByCategory.entrySet()) {
            String category = entry.getKey();
            List<Data<X,Y>> datas = entry.getValue();
            var boxParams = calculateBoxParams(datas);

            // todo if multiple series, need to dodge the boxes and adjust width
            // see bargap and categorygap in barchart
            double catPos = categoryAxis.getDisplayPosition(category);
            drawBox(boxParams, catPos);
            for (var data: datas) {
                drawPoint(data, catPos, boxParams);
            }
        }
    }

    protected void resetPlotChildren() {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    protected void drawBox(BoxParams boxParams, double catPos) {
        Group box = makeBox(
                catPos,
                valueAxis.getDisplayPosition(boxParams.lowWhisk),
                valueAxis.getDisplayPosition(boxParams.lowQuartile),
                valueAxis.getDisplayPosition(boxParams.median),
                valueAxis.getDisplayPosition(boxParams.upQuartile),
                valueAxis.getDisplayPosition(boxParams.upWhisk),
                categoryAxis.getCategorySpacing() * 0.75
        );
        getPlotChildren().add(box);
    }

    // todo series handling
    /**
     * The method used to draw a data point. This is by default quite inefficient and can be overridden.
     * @param data the data point
     * @param catPos the category position
     * @param boxParams the boxplot parameters
     */
    protected void drawPoint(Data<X, Y> data, double catPos, BoxParams boxParams) {
        double value = getNumeric.apply(data).doubleValue();
        double valPos = valueAxis.getDisplayPosition(value);
        if (!getDrawAllPoints()) {
            if ((value > boxParams.lowWhisk()) && (value < boxParams.upWhisk())) {
                return;
            }
        }
        data.setNode(node);

        var j = getJitterValue(data);
        double x = getOrientation() == Orientation.VERTICAL ?  valPos: catPos + j;
        double y = getOrientation() == Orientation.VERTICAL ? catPos + j: valPos;
        var context = canvas.getGraphicsContext2D();
        context.setFill(Color.BLACK);
        context.setGlobalAlpha(getMarkerOpacity());
        // nudge points based on point radius (i.e., don't center them on the top left of the point).
        double rad = getMarkerRadius();
        context.fillOval(x - rad, y - rad, rad * 2, rad * 2);
    }

    protected record BoxParams(double lowWhisk, double lowQuartile, double median, double upQuartile, double upWhisk) {}

    private BoxParams calculateBoxParams(List<Data<X, Y>> datas) {
        // sort for the sake of binary search; percentile could cope with unsorted
        double[] doubles = datas.stream().map(getNumeric)
                .mapToDouble(Number::doubleValue)
                .sorted()
                .toArray();

        Percentile percentile = new Percentile();
        percentile.setData(doubles);

        // basic quantities
        double lq = percentile.evaluate(25);
        double median = percentile.evaluate(50);
        double uq = percentile.evaluate(75);
        double iqr = uq - lq;
        // traditional boxplot whiskers are 1.5 * IQR
        double lf = lq - (1.5 * iqr);
        double uf = uq + (1.5 * iqr);
        double lowWhisk = searchForWhisker(doubles, lf);
        double upWhisk = searchForWhisker(doubles, uf);

        return new BoxParams(lowWhisk, lq, median, uq, upWhisk);
    }

    // todo this should in future handle series, I think
    private @NonNull Map<String, List<Data<X, Y>>> collectValuesByCategory() {
        Map<String, List<Data<X,Y>>> valuesByCategory = new LinkedHashMap<>();
        for (var series : getData()) {
            for (var data : series.getData()) {
                valuesByCategory
                        .computeIfAbsent(getCategory.apply(data), (_) -> new ArrayList<>())
                        .add(data);
            }
        }
        return valuesByCategory;
    }

    private static double searchForWhisker(double[] doubles, double lf) {
        int idx = Arrays.binarySearch(doubles, lf);
        // binary search returns -(low + 1) if not found
        if (idx < 0) {
            idx = Math.clamp(-(int) idx - 1, 0, doubles.length - 1);
        }
        return doubles[idx];
    }

    // todo control jitter width + seed
    private double jitter() {
        double spacing = categoryAxis.getCategorySpacing() / 6;
        return random.nextDouble(-spacing, spacing);
    }

    private Line makeWhisker(double catPos, double startPos, double endPos) {
        if (orientation == Orientation.VERTICAL) {
            return new Line(startPos, catPos, endPos, catPos);
        } else {
            return new Line(catPos, startPos, catPos, endPos);
        }
    }

    private Line makeLine(double catPos, double valPos, double width) {
        if (orientation == Orientation.VERTICAL) {
            return new Line(valPos, catPos - (width/2), valPos,catPos + (width/2));
        } else {
            return new Line(catPos - (width/2), valPos, catPos + (width/2), valPos);
        }
    }

    private Rectangle makeRect(double catPos, double lowPos, double highPos, double boxSize) {
        if (orientation == Orientation.VERTICAL) {
            return new Rectangle(lowPos, catPos - (boxSize / 2), Math.abs(highPos  - lowPos), boxSize);
        } else {
            double minY = Math.min(lowPos, highPos);
            double height = Math.abs(lowPos - highPos);
            return new Rectangle(catPos - (boxSize / 2), minY, boxSize, height);
        }
    }


    private Group makeBox(double categoryPosition, double lowWhisk, double lq, double median, double uq, double upWhisk, double width) {
        Line lowWhisker = makeWhisker(categoryPosition, lowWhisk, lq);
        Line lowCap = makeLine(categoryPosition, lowWhisk, width / 2);

        Line medLine = makeLine(categoryPosition, median, width);

        Line highWhisker = makeWhisker(categoryPosition, upWhisk, uq);
        Line highCap = makeLine(categoryPosition, upWhisk, width / 2);
        // todo fill by category somehow? or is grey fine
        Rectangle rect = makeRect(categoryPosition, lq, uq, width);
        rect.setFill(Color.TRANSPARENT);
        rect.setStroke(Color.GRAY);

        Group group = new Group();
        group.getChildren().addAll(
                rect,
                lowWhisker,
                lowCap,
                highWhisker,
                highCap,
                medLine
        );
        return group;
    }

    @Override
    protected void updateAxisRange() {
        var data = getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        Set<String> categories = new LinkedHashSet<>();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Series<X,Y> series: data) {
            for (Data<X,Y> item: series.getData()) {
                double val = getNumeric.apply(item).doubleValue();
                min = Math.min(min, val);
                max = Math.max(max, val);
                categories.add(getCategory.apply(item));
            }
        }
        if (categoryAxis != null && !categories.isEmpty()) {
            categoryAxis.setCategories(FXCollections.observableArrayList(categories));
        }
        if (valueAxis != null && valueAxis.isAutoRanging() && min <= max) {
            valueAxis.invalidateRange(List.of(min, max));
        }
    }

    @Override
    public Optional<Data<X,Y>> findDataPoint(double x, double y, double tolerance) {
        String category = categoryAxis.getValueForDisplay(getOrientation() == Orientation.HORIZONTAL ? x : y);
        double value = valueAxis.getValueForDisplay(getOrientation() == Orientation.HORIZONTAL ? y : x).doubleValue();

        List<Data<X,Y>> candidates = new ArrayList<>();
        for (var series: getData()) {
            for (var item: series.getData()) {
                // only drawn items have non-null nodes, and they all share one...
                if (item.getNode() == null) {
                    continue;
                }
                if (getCategory.apply(item).equals(category)) {
                    double itemValue = getNumeric.apply(item).doubleValue();
                    if (itemValue < (value + getMarkerRadius()) && itemValue > (value - getMarkerRadius())) {
                        candidates.add(item);
                    }
                }
            }
        }
        logger.debug("{} candidates found", candidates.size());

        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = getMarkerRadius() / 2;

        Coordinate clickCoord = new Coordinate(x, y);
        for (Data<X,Y> candidate : candidates) {
            // candidate values are on data scale
            double cx = getXAxis().getDisplayPosition(candidate.getXValue());
            double cy = getYAxis().getDisplayPosition(candidate.getYValue());
            if (getOrientation() == Orientation.HORIZONTAL) {
                cx += getJitterValue(candidate);
            } else {
                cy += getJitterValue(candidate);
            }
            Coordinate candidateCoord = new Coordinate(cx, cy);
            double distance = candidateCoord.distance(clickCoord);
            logger.debug("Distance from {} to {}: {}", clickCoord, candidateCoord, distance);
            if (distance <= minDistance && distance < maxDistance) {
                minDistance = distance;
                closestPoint = candidate;
            }
        }
        logger.debug("Min distance found {}", minDistance);
        return Optional.ofNullable(closestPoint);
    }

    @Override
    public Canvas getCanvas() {
        return this.canvas;
    }

    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            handlePulse();
        }

    };

    private void handlePulse() {
        if (redrawNeeded) {
            layoutPlotChildren();
        }
        redrawNeeded = false;
    }

}
