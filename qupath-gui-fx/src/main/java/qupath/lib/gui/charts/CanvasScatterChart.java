package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Window;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanvasScatterChart<X extends Number,Y extends Number> extends ScatterChart<X,Y> implements CanvasChart<X,Y> {
    private static final Logger logger = LoggerFactory.getLogger(CanvasScatterChart.class);

    private final GeometryFactory gf;
    private Quadtree tree;
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;

    private final Map<Series<X,Y>, Boolean> seriesVisible = new HashMap<>();

    private final DoubleProperty pointRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty pointOpacity = new SimpleDoubleProperty(1);

    private final IntegerProperty randomSeed = new SimpleIntegerProperty(123);

    private final ObjectProperty<ColorScale> colorScaleObjectProperty = new SimpleObjectProperty<>(ColorScales.colorBySeriesName());
    private boolean treeRebuildNeeded;
    private boolean colorScaleDirty;

    public void setColorScale(ColorScale colorScale) {
        this.colorScaleObjectProperty.set(colorScale);
    }


    /**
     * Construct a CanvasScatterChart with the two axes and the defined color map.
     * @param xAxis the x-axis for this chart
     * @param yAxis the y-axis for this chart
     */
    public CanvasScatterChart(Axis<X> xAxis, Axis<Y> yAxis) {
        super(xAxis, yAxis);
        this.tree = new Quadtree();
        this.gf = new GeometryFactory();
        initProperties();
        getPlotChildren().add(canvas);
        colorScaleObjectProperty.addListener(_ -> {
            colorScaleDirty = true;
            redrawNeeded = true;
        });
    }

    private void initProperties() {
        // unsure if this is idiomatic
        pointOpacity.subscribe(this::redraw);
        pointOpacity.addListener((v, o, n) -> {
            if (n.doubleValue() <= 0 || n.doubleValue() > 1 || !Double.isFinite(n.doubleValue())) pointOpacity.set(o.doubleValue());
        });
        pointRadius.subscribe(this::redraw);
        pointRadius.addListener((v, o, n) -> {
            if (n.doubleValue() <= 0 || !Double.isFinite(n.doubleValue())) pointRadius.set(o.doubleValue());
        });
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });

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

    }

    @Override
    public Optional<Data<X,Y>> findDataPoint(double x, double y) {
        double tolerance = getPointRadius();

        logger.debug("Querying tree at X: {}, Y: {}; tol X: {}, tol Y: {}", x, y, tolerance, tolerance);
        Envelope search = new Envelope(
                (double)getXAxis().getValueForDisplay(x - tolerance),
                (double)getXAxis().getValueForDisplay(x + tolerance),
                (double)getYAxis().getValueForDisplay(y - tolerance),
                (double)getYAxis().getValueForDisplay(y + tolerance)
        );

        @SuppressWarnings("unchecked") List<Data<X,Y>> candidates = (List<Data<X,Y>>)tree.query(search);
        logger.debug("{} candidates found", candidates.size());
        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = getPointRadius();
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
     * The radius of points on this chart
     * @return the property corresponding to point radius
     */
    public DoubleProperty pointRadiusProperty() {
        return pointRadius;
    }

    /**
     * Get the current point radius
     * @return the point radius
     */
    public double getPointRadius() {
        return pointRadius.get();
    }

    /**
     * Set the point size
     * @param value the new value
     */
    public void setPointRadius(double value) {
        this.pointRadius.set(value);
    }

    /**
     * The opacity of points in this plot
     * @return the property corresponding to point opacity
     */
    public DoubleProperty pointOpacityProperty() {
        return pointOpacity;
    }

    /**
     * Get the current point opacity
     * @return the point opacity in [0, 1)
     */
    public double setPointOpacity() {
        return pointOpacity.get();
    }

    /**
     * Set the opacity used to draw points
     * @param value the new value in [0, 1)
     */
    public void setPointOpacity(double value) {
        this.pointOpacity.set(value);
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

    @Override
    public Canvas getCanvas() {
        return canvas;
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        insertPoint(item, tree);
        colorScaleDirty = true;
    }

    @Override
    protected void dataItemRemoved(Data<X, Y> item, Series<X, Y> series) {
        tree.remove(createEnvelope(item), item);
        removeDataItemFromDisplay(series, item);
        colorScaleDirty = true;
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        treeRebuildNeeded = true;
        colorScaleDirty = true;
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
        for (var item : series.getData()) {
            dataItemRemoved(item, series);
        }
        requestChartLayout();
    }

    private Envelope createEnvelope(Data<X, Y> item) {
        // cannot create tree with XY coords because axes may not be initiated
        // also keeping it on data scale means it does not need to be updated
        Point p = gf.createPoint(new Coordinate(
                item.getXValue().doubleValue(),
                item.getYValue().doubleValue()
        ));
        return p.getEnvelopeInternal();
    }

    @Override
    protected void layoutPlotChildren() {
        redraw();
    }

    private void redraw() {
        var context = canvas.getGraphicsContext2D();
        context.setGlobalAlpha(pointOpacity.get());
        context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double rad = getPointRadius();
        for (Series<X,Y> series : getData()) {
            if (seriesVisible.computeIfAbsent(series, _ -> true)) {
                for (var point : series.getData()) {
                    var color = colorScaleObjectProperty.get().getColor(series, point);
                    context.setFill(color);
                    // fillOval uses bounding box coords
                    context.fillOval(
                            getXAxis().getDisplayPosition(point.getXValue()) - rad,
                            getYAxis().getDisplayPosition(point.getYValue()) - rad,
                            rad * 2, rad * 2);
                }
            }
        }

    }

    @Override
    protected void updateLegend() {
        HBox container = new HBox(); // todo probably need to handle different orientations...
        container.setAlignment(Pos.CENTER);
        container.setSpacing(5);
        Set<Node> legendItems = new HashSet<>();
        if (getData() != null) {
            for (Series<X,Y> series : getData()) {
                for (var point : series.getData()) {
                    legendItems.add(
                            colorScaleObjectProperty.get().getLegendItem(series, point)
                    );
                }
            }
        }
        List<Node> legendList = new ArrayList<>(legendItems);
        container.getChildren().setAll(legendList);
        setLegend(container);
    }

    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            if (colorScaleDirty) {
                colorScaleObjectProperty.get().prepare(getData());
                colorScaleDirty = false;
            }
            if (redrawNeeded) {
                redraw();
                updateLegend();
                redrawNeeded = false;
            }
            if (treeRebuildNeeded) {
                tree = new Quadtree();
                for (Series<X,Y> series : getData()) {
                    for (var point : series.getData()) {
                        insertPoint(point, tree);
                    }
                }
                treeRebuildNeeded = false;
            }

        }
    };

    private void insertPoint(Data<X, Y> point, Quadtree tree) {
        if (Double.isFinite(point.getXValue().doubleValue()) && Double.isFinite(point.getYValue().doubleValue())) {
            tree.insert(createEnvelope(point), point);
        }
    }

}
