package qupath.lib.gui.charts.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import javafx.application.Platform;
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
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
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

        Function<CanvasLayerChart.DataPoint<Number, Number, PathObject>, Number> colorFun = dp -> dp.getAssociatedObject().getMeasurements().get(colorVal).doubleValue();
        var layer = new CanvasLayerChart.ContinuousScatterLayer<>(new Canvas(), points, colormap, colorFun);
        var layer2 = new CanvasLayerChart.LinearTrendLayer<>(new Canvas(), points);
        chart.layers.add(layer2);
        chart.layers.add(layer);
        var scene = new Scene(chart);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(scene);
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
            }
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
        updateLegend();
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

        LinearTrendLayer(Canvas canvas, Collection<? extends DataPoint<Number, Number, T>> points) {
            super(canvas, points);
        }

        @Override
        public void updateCanvas(Canvas canvas, Axis<Number> xAxis, Axis<Number> yAxis) {
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            SimpleRegression regression = new SimpleRegression();
            double xSum = 0;
            double n = getData().size();
            for (DataPoint<Number, Number, ?> dataPoint : getData()) {
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
            g2d.setGlobalAlpha(1);
            g2d.setFill(Color.GREY);
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
            g2d.setFill(new Color(0.5, 0.5, 0.5, 0.5));
//            g2d.setFill(Color.BLACK);
            g2d.fillPolygon(allXPoints, allYPoints, nPoints * 2);
        }

        @Override
        public Node getLegend() {
            HBox node = new HBox();
            node.setSpacing(5);
            node.setAlignment(Pos.CENTER);
            StackPane pane = new StackPane();
            // todo parameterize
            double width = 24;
            double linewidth = 4;
            Polygon poly = new Polygon(
                    0, 0, width / 2, linewidth, width, 0,
                    width, width, width / 2, width - linewidth, 0, width

            );
            poly.setStroke(null);
            poly.setFill(new Color(0.5, 0.5, 0.5, 0.5));
            Line line = new Line(linewidth, width / 2, width - linewidth, (width / 2));
            line.setStrokeWidth(linewidth);
            pane.getChildren().addAll(poly, line);
            Label label = new Label("Foo");
            node.getChildren().addAll(pane, label);
            getCanvas().visibleProperty().addListener((obs) -> {
                if (getCanvas().isVisible()) {
                    node.setOpacity(1);
                } else {
                    node.setOpacity(0.2);
                }
            });
            node.setOnMouseClicked(_ -> this.getCanvas().setVisible(!this.getCanvas().isVisible()));
            return node;
        }

    }

    static abstract class NumberNumberLayer<T> implements PlotLayer<Number,Number> {
        private final Canvas canvas;
        private final ObservableList<? extends DataPoint<Number, Number, T>> data;

        public NumberNumberLayer(Canvas canvas, Collection<? extends DataPoint<Number,Number,T>> points) {
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

        public ObservableList<? extends DataPoint<Number, Number, T>> getData() {
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
            for (DataPoint<Number, Number, ?> dataPoint : data) {
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
        private final Function<DataPoint<Number, Number, T>, Number> colorFun;
        private final double min;
        private final double max;

        ContinuousScatterLayer(
                Canvas canvas,
                Collection<? extends DataPoint<Number, Number, T>> points,
                ColorMaps.ColorMap colorMap,
                Function<DataPoint<Number, Number, T>, Number> colorFun) {
            super(canvas, points);
            this.colorMap = colorMap;
            this.colorFun = colorFun;
            var colorVals = points.stream().map(colorFun).map(Number::doubleValue).toList();
            min = colorVals.stream().min(Double::compareTo).orElse(Double.MIN_VALUE);
            max = colorVals.stream().max(Double::compareTo).orElse(Double.MAX_VALUE);
        }

        List<Stop> createStops(int nStep) {
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
        Color getColor(DataPoint<Number, Number, T> point) {
            return ColorToolsFX.getCachedColor(colorMap.getColor(colorFun.apply(point).doubleValue(), min, max));
        }

        @Override
        public Node getLegend() {
            HBox box = new HBox(); // todo probably need to handle different orientations...
            box.setAlignment(Pos.CENTER);
            box.setSpacing(5);
            Label label = new Label("Bar");
            int width = 100;
            int height = 20;
            Rectangle symbol = new Rectangle(width, height);
            List<Stop> stops = createStops(100);
            var grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
            symbol.setFill(grad);
            box.getChildren().addAll(symbol, label);
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

        ScatterLayer(Canvas canvas,
                     Collection<? extends DataPoint<Number, Number, T>> points) {
            super(canvas, points);
            this.color = Color.BLACK;
            this.name = "Scatter";
        }

        ScatterLayer(Canvas canvas,
                     Collection<? extends DataPoint<Number, Number, T>> points,
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
            Collections.shuffle(getData());
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

        Color getColor(DataPoint<Number, Number, T> dataPoint) {
            return this.color;
        }

        @Override
        public Node getLegend() {
            HBox box = new HBox(); // todo probably need to handle different orientations...
            box.setAlignment(Pos.CENTER);
            box.setSpacing(5);
            Label label = new Label("Bar");
            Circle symbol = new Circle(5, color);
            box.getChildren().addAll(symbol, label);

            getCanvas().visibleProperty().addListener(_ -> {
                if (getCanvas().isVisible()) {
                    symbol.setOpacity(1);
                } else {
                    symbol.setOpacity(0.2);
                }
            });
            symbol.setOnMouseClicked(_ -> this.getCanvas().setVisible(!this.getCanvas().isVisible()));
            return box;
        }


        private double getMarkerRadius() {
            // todo
            return 2;
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
}
