package qupath.lib.gui.charts.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;
import qupath.lib.scripting.QP;

public class CanvasLayerChart<X, Y> extends XYChart<X, Y> {
    private static final Logger logger = LoggerFactory.getLogger(CanvasLayerChart.class);

    public static void doStuff() {
        Collection<PathObject> pathObjects = QP.getDetectionObjects();
        String xVal = "Nucleus: Area";
        String yVal = "Nucleus: Perimeter";
        String colorVal = "Nucleus: Hematoxylin OD range";
        var points = pathObjects
                .stream().map(po ->
                        new CanvasLayerChart.PathObjectDataPoint<>(po,
                                pp -> pp.getMeasurements().get(xVal),
                                pp -> pp.getMeasurements().get(yVal))
                )
                .toList();
        var chart = new CanvasLayerChart<>(new NumberAxis(), new NumberAxis());

        var colormap = ColorMaps.getColorMaps().get("Viridis");

        Function<CanvasLayerChart.DataPoint<? extends Number, ? extends Number, PathObject>, Number> colorFun = dp -> dp.getAssociatedObject().getMeasurements().get(colorVal).doubleValue();
        var layer = new CanvasLayerChart.ContinuousScatterLayer<>(new Canvas(), points, colorVal, colormap, colorFun);
        var layer2 = new CanvasLayerChart.LinearTrendLayer<>(new Canvas(), points, "Trend", Color.RED);
        chart.layers.add(layer2);
        chart.layers.add(layer);
        var scene = new Scene(chart);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
        });

        var classes = pathObjects.stream().map(PathObject::getPathClass).distinct().toList();

        var categoricalLayers = classes.stream()
                .map(
                        pc -> {
                            var lpo = pathObjects.stream()
                                    .filter(po -> po.getPathClass().equals(pc))
                                    .map(po -> new CanvasLayerChart.PathObjectDataPoint<>(po,
                                            pp -> pp.getMeasurementList().get(xVal),
                                            pp -> pp.getMeasurementList().get(yVal)
                                    ))
                                    .toList();
                            var discreteLayer = new CanvasLayerChart.ScatterLayer<>(new Canvas(), lpo, pc.toString(), ColorToolsFX.getPathClassColor(pc));
                            return discreteLayer;
                        }
            )
                .toList();
        var chart2 = new CanvasLayerChart<>(new NumberAxis(), new NumberAxis());
        chart2.getLayers().addAll(categoricalLayers);
        var scene2 = new Scene(chart2);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(scene2);
            stage.show();
        });
    }


    private final ObservableList<PlotLayer<X, Y>> layers = FXCollections.observableArrayList();
    // todo per-layer?
    private boolean redrawNeeded;

    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     */
    public CanvasLayerChart(Axis<X> xAxis, Axis<Y> yAxis) {
        super(xAxis, yAxis);
        this.setData(FXCollections.observableArrayList());
        layers.addListener((ListChangeListener<PlotLayer<X, Y>>) c -> {
            if (c.next()) {
                c.getAddedSubList().forEach(layer -> {
                    // can't select parent of plot children (might be empty), but scenicView to the rescue
                    Pane plotContent = (Pane) lookup(".chart-content");
                    plotContent.widthProperty().addListener(_ -> redrawNeeded = true);
                    plotContent.heightProperty().addListener(_ -> redrawNeeded = true);
                    var canvas = layer.getCanvas();
                    getPlotChildren().add(canvas);
                    if (plotContent != null) {
                        canvas.widthProperty().bind(plotContent.widthProperty());
                        canvas.heightProperty().bind(plotContent.heightProperty());
                    }
                });
                updateLegend();
            }
        });
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });
    }

    public ObservableList<PlotLayer<X, Y>> getLayers() {
        return layers;
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        // probably-no-op
    }

    @Override
    protected void dataItemRemoved(Data<X, Y> item, Series<X, Y> series) {
        // probably-no-op
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        // probably-no-op
    }

    @Override
    protected void seriesAdded(Series<X, Y> series, int seriesIndex) {
        // probably-no-op
    }

    @Override
    protected void seriesRemoved(Series<X, Y> series) {
        // probably-no-op
    }

    @Override
    protected void layoutPlotChildren() {
        for (PlotLayer<X, Y> layer : layers) {
            // todo redraw only if needed
            layer.updateCanvas(layer.getCanvas(), getXAxis(), getYAxis());
        }
    }

    @Override
    protected void updateAxisRange() {
        for (PlotLayer<X, Y> layer : layers) {
            layer.updateAxes(getXAxis(), getYAxis());
        }
    }

    @Override
    protected void updateLegend() {
        HBox legend = new HBox();
        legend.setAlignment(Pos.CENTER);
        legend.setSpacing(10);
        for (PlotLayer<X, Y> layer : layers) {
            legend.getChildren().add(layer.getLegend());
        }
        setLegend(legend);
    }

    public interface PlotLayer<X, Y> {
        // may or may not be the same across objects
        Canvas getCanvas();

        void updateCanvas(Canvas canvas, Axis<X> xAxis, Axis<Y> yAxis);

        void updateAxes(Axis<X> xAxis, Axis<Y> yAxis);

        Node getLegend();
    }

    static class LinearTrendLayer<T> extends NumberNumberLayer<T> {
        private final String name;
        private final Color color;
        private static final double OPACITY_FACTOR = 0.5;

        LinearTrendLayer(Canvas canvas, Collection<? extends DataPoint<Number, Number, T>> points) {
            this(canvas, points, "Trend", Color.GREY);
        }

        LinearTrendLayer(Canvas canvas, Collection<? extends DataPoint<Number, Number, T>> points, String name, Color color) {
            super(canvas, points);
            this.name = name;
            this.color = color;
        }

        @Override
        public void updateCanvas(Canvas canvas, Axis<Number> xAxis, Axis<Number> yAxis) {
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            SimpleRegression regression = new SimpleRegression();
            double xSum = 0;
            double n = getData().size();
            for (DataPoint<? extends Number, ? extends Number, T> dataPoint : getData()) {
                double x = dataPoint.getX().doubleValue();
                double y = dataPoint.getY().doubleValue();
                xSum += x;

                regression.addData(x, y);
            }

            double xMean = xSum / n;
            double xMin = ((NumberAxis)xAxis).getLowerBound();
            double yMin = regression.predict(xMin);
            double xMax = ((NumberAxis)xAxis).getUpperBound();
            double yMax = regression.predict(xMax);

            final int nPoints = 200;
            double[] xPoints = new double[nPoints];
            double[] yLowerPoints = new double[nPoints];
            double[] yUpperPoints = new double[nPoints];

            double mse = regression.getMeanSquareError();
            double ssd = regression.getXSumSquares();
            var td = new TDistribution(n - 2);
            double ctv = td.inverseCumulativeProbability(0.975);

            double step = Math.abs(xMax - xMin) / (nPoints - 1);
            double x0 = xMin;

            // calculate confidence band at range of x values
            for (int i = 0; i < nPoints; i++) {
                double yHat0 = regression.predict(x0);
                double seY0Hat = Math.sqrt(mse * ((1 / n) + (Math.pow(x0 - xMean, 2) / ssd)));
                xPoints[i] = x0;
                yLowerPoints[i] = yHat0 - (ctv * seY0Hat);
                yUpperPoints[i] = yHat0 + (ctv * seY0Hat);
                x0 += step;
            }

            // draw just the line
            var g2d = canvas.getGraphicsContext2D();
            // todo somehow separate drawing code into path and uncertainty?
//            drawPath();
            g2d.setGlobalAlpha(1);
            g2d.setStroke(color);
            g2d.beginPath();
            g2d.moveTo(xAxis.getDisplayPosition(xMin), yAxis.getDisplayPosition(yMin));
            g2d.lineTo(xAxis.getDisplayPosition(xMax), yAxis.getDisplayPosition(yMax));
            g2d.stroke();

            // draw the polygon
            double[] allXPoints = new double[nPoints * 2];
            double[] allYPoints = new double[nPoints * 2];
            for (int i = 0; i < nPoints; i++) {
                allXPoints[i] = xAxis.getDisplayPosition(xPoints[i]);
                allYPoints[i] = yAxis.getDisplayPosition(yLowerPoints[i]);
                allXPoints[nPoints + i] = xAxis.getDisplayPosition(xPoints[xPoints.length - i - 1]);
                allYPoints[nPoints + i] = yAxis.getDisplayPosition(yUpperPoints[yUpperPoints.length - i - 1]);
            }
//            for (int i = 0; i < (nPoints * 2); i++) {
//                logger.info("{} {}", allXPoints[i], allYPoints[i]);
//            }
            g2d.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
//            g2d.setFill(Color.BLACK);
            g2d.fillPolygon(allXPoints, allYPoints, nPoints * 2);
        }

        @Override
        public Node getLegend() {
            HBox box = new HBox();
            box.setSpacing(5);
            box.setAlignment(Pos.CENTER);
            StackPane pane = new StackPane();
            // todo parameterize
            double width = 24;
            double linewidth = 4;
            Polygon poly = new Polygon(
                    0, 0, width / 2, linewidth, width, 0,
                    width, width, width / 2, width - linewidth, 0, width

            );
            poly.setStroke(null);
            poly.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
            Line line = new Line(linewidth, width / 2, width - linewidth, (width / 2));
            line.setStrokeWidth(linewidth);
            line.setStroke(color);
            pane.getChildren().addAll(poly, line);
            Label label = new Label(name);
            box.getChildren().addAll(pane, label);
            getCanvas().visibleProperty().addListener(_ -> {
                if (getCanvas().isVisible()) {
                    box.setOpacity(1);
                } else {
                    box.setOpacity(0.2);
                }
            });
            box.setOnMouseClicked(_ -> this.getCanvas().setVisible(!this.getCanvas().isVisible()));
            return box;
        }

    }

    static abstract class NumberNumberLayer<T> implements PlotLayer<Number, Number> {
        private final Canvas canvas;
        private final ObservableList<? extends DataPoint<? extends Number, ? extends Number, T>> data;

        public NumberNumberLayer(Canvas canvas, Collection<? extends DataPoint<? extends Number,? extends Number,T>> points) {
            this.canvas = canvas != null ? canvas: new Canvas();
            if (points == null) {
                points = Collections.emptyList();
            }
            this.data = FXCollections.observableArrayList(points);
        }

        @Override
        public Canvas getCanvas() {
            return canvas;
        }

        public ObservableList<? extends DataPoint<? extends Number, ? extends Number, T>> getData() {
            return data;
        }

        @Override
        public void updateAxes(Axis<Number> xAxis, Axis<Number> yAxis) {
            NumberAxis xValueAxis = (NumberAxis) xAxis;
            NumberAxis yValueAxis = (NumberAxis) yAxis;
            double xMin = Double.MAX_VALUE;
            double xMax = -Double.MAX_VALUE;
            double yMin = Double.MAX_VALUE;
            double yMax = -Double.MAX_VALUE;
            for (DataPoint<? extends Number, ? extends Number, T> dataPoint : data) {
                double x = dataPoint.getX().doubleValue();
                double y = dataPoint.getY().doubleValue();
                xMin = Math.min(xMin, x);
                xMax = Math.max(xMax, x);
                yMin = Math.min(yMin, y);
                yMax = Math.max(yMax, y);
            }
            xValueAxis.invalidateRange(List.of(xMin, xMax));
            yValueAxis.invalidateRange(List.of(yMin, yMax));
        }

    }

    static class ContinuousScatterLayer<T> extends ScatterLayer<T> {
        private final ColorMaps.ColorMap colorMap;
        private final Function<DataPoint<? extends Number, ? extends Number, T>, Number> colorFun;
        private final NumberAxis colorAxis = new NumberAxis();
        private final String name;

        ContinuousScatterLayer(
                Canvas canvas,
                Collection<? extends DataPoint<? extends Number, ? extends Number, T>> points,
                String name,
                ColorMaps.ColorMap colorMap,
                Function<DataPoint<? extends Number, ? extends Number, T>, Number> colorFun) {
            super(canvas, points);
            this.colorMap = colorMap;
            this.colorFun = colorFun;
            this.name = name;
            var colorVals = points.stream().map(colorFun).map(Number::doubleValue).toList();
            double min = colorVals.stream().min(Double::compareTo).orElse(Double.MIN_VALUE);
            double max = colorVals.stream().max(Double::compareTo).orElse(Double.MAX_VALUE);
            colorAxis.invalidateRange(List.of(min, max));
        }

        static List<Stop> createStops(ColorMaps.ColorMap colorMap, double min, double max, int nStep) {
            double range = Math.abs(max - min);
            double step = 1 / (((double)nStep) - 1);
            List<Stop> stops = new ArrayList<>();
            for (double current = 0; current <= 1; current+=step) {
                double cVal = min + (range * current);
                var color = ColorToolsFX.getCachedColor(colorMap.getColor(cVal, min, max));
                stops.add(new Stop(current, color));
            }
            return stops;
        }

        @Override
        Color getColor(DataPoint<? extends Number, ? extends Number, T> point) {
            return ColorToolsFX.getCachedColor(
                    colorMap.getColor(
                            colorFun.apply(point).doubleValue(),
                            colorAxis.getLowerBound(),
                            colorAxis.getUpperBound())
            );
        }

        @Override
        public Node getLegend() {
            HBox box = new HBox(); // todo probably need to handle different orientations...
            box.setAlignment(Pos.TOP_CENTER);
            box.setSpacing(5);
            Label label = new Label(name);
            int width = 100;
            int height = 20;


            Rectangle scaleRect = new Rectangle(width, height);
            List<Stop> stops = createStops(colorMap, colorAxis.getLowerBound(), colorAxis.getUpperBound(), 100);
            var grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
            scaleRect.setFill(grad);

            scaleRect.widthProperty().bind(colorAxis.widthProperty());

            VBox vBox = new VBox(scaleRect, colorAxis);
            box.getChildren().addAll(vBox, label);
            getCanvas().visibleProperty().addListener(_ -> {
                if (getCanvas().isVisible()) {
                    box.setOpacity(1);
                } else {
                    box.setOpacity(0.2);
                }
            });
            box.setOnMouseClicked(_ -> this.getCanvas().setVisible(!this.getCanvas().isVisible()));
            return box;
        }
    }

    static class ScatterLayer<T> extends NumberNumberLayer<T> {

        private final Color color;
        private final String name;
        private final DoubleProperty markerRadius = new SimpleDoubleProperty(2);
        private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);

        ScatterLayer(Canvas canvas,
                     Collection<? extends DataPoint<? extends Number, ? extends Number, T>> points) {
            super(canvas, points);
            this.color = Color.BLACK;
            this.name = "Scatter";
        }

        ScatterLayer(Canvas canvas,
                     Collection<? extends DataPoint<? extends Number, ? extends Number, T>> points,
                     String name,
                     Color color) {
            super(canvas, points);
            this.name = name;
            this.color = color;
        }

        @Override
        public void updateCanvas(Canvas canvas, Axis<Number> xAxis, Axis<Number> yAxis) {
            var context = canvas.getGraphicsContext2D();
            context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            context.setGlobalAlpha(1);
            context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            // todo not here but somewhere...
//            Collections.shuffle(getData());
            double rad = getMarkerRadius();
            for (var d: getData()) {
                var color = getColor(d);
                context.setFill(color);
                // fillOval uses bounding box coords
                context.fillOval(
                        xAxis.getDisplayPosition(d.getX()) - rad,
                        yAxis.getDisplayPosition(d.getY()) - rad,
                        rad * 2, rad * 2);

            }
        }

        Color getColor(DataPoint<? extends Number, ? extends Number, T> dataPoint) {
            return this.color;
        }

        @Override
        public Node getLegend() {
            HBox box = new HBox(); // todo probably need to handle different orientations...
            box.setAlignment(Pos.CENTER);
            box.setSpacing(5);
            Label label = new Label(name);
            Circle symbol = new Circle(5, color);
            box.getChildren().addAll(symbol, label);

            getCanvas().visibleProperty().addListener(_ -> {
                if (getCanvas().isVisible()) {
                    symbol.setOpacity(1);
                } else {
                    symbol.setOpacity(0.2);
                }
            });
            box.setOnMouseClicked(_ -> this.getCanvas().setVisible(!this.getCanvas().isVisible()));
            return box;
        }


        private double getMarkerRadius() {
            return markerRadius.get();
        }

        private double getMarkerOpacity() {
            return markerOpacity.get();
        }

    }

    public interface DataPoint<X, Y, T> {
        X getX();
        Y getY();

        T getAssociatedObject();
    }

    public static class PathObjectDataPoint<X, Y, T extends PathObject> implements DataPoint<X, Y, T> {
        private X x;
        private Y y;
        private final T associatedObject;
        private final Function<T, X> xFun;
        private final Function<T, Y> yFun;

        public PathObjectDataPoint(T associatedObject,  Function<T, X> xFun, Function<T, Y> yFun) {
            this.associatedObject = associatedObject;
            this.xFun = xFun;
            this.yFun = yFun;
        }

        // todo can't convert
//        public PathObjectDataPoint(T associatedObject, String xMeas, String yMeas) {
//            this(
//                associatedObject,
//                po -> Double.valueOf(po.getMeasurementList().get(xMeas)),
//                po -> Double.valueOf(po.getMeasurementList().get(yMeas))
//            );
//        }


        @Override
        public synchronized X getX() {
            synchronized (associatedObject) {
                if (x == null) {
                    x = xFun.apply(associatedObject);
                }
            }
            return x;
        }

        @Override
        public synchronized Y getY() {
            synchronized (associatedObject) {
                if (y == null) {
                    y = yFun.apply(associatedObject);
                }
            }
            return y;
        }

        @Override
        public T getAssociatedObject() {
            return associatedObject;
        }
    }

    static <T> Collection<T> resampleWithReplacement(Collection<T> objects) {
        List<T> outList = new ArrayList<>(objects.size());
        List<T> inList = new ArrayList<>(objects);
        Random random = new Random();
        for (int i = 0; i < objects.size(); i++) {
            int index = random.nextInt(objects.size());
            outList.set(i, inList.get(index));
        }
        return outList;
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
