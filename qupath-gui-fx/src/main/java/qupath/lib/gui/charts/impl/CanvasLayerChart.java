package qupath.lib.gui.charts.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
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
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;
import qupath.lib.scripting.QP;

public class CanvasLayerChart<X, Y> extends Region {
    private static final Logger logger = LoggerFactory.getLogger(CanvasLayerChart.class);
    private final Axis<X> xAxis;
    private final Axis<Y> yAxis;
    private final StackPane stackPane = new StackPane();
    private final GridPane gridPane = new GridPane();
    private final BorderPane borderPane = new BorderPane();
    private final Canvas baseCanvas = new Canvas();
    // todo draw top if not empty
    private final StringProperty titleProperty = new SimpleStringProperty("");
    private final ObjectProperty<Side> legendSide = new SimpleObjectProperty<>(Side.BOTTOM);

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
        var xax1 = new NumberAxis();
        xax1.setLabel(xVal);
        var yax1 = new NumberAxis();
        yax1.setLabel(yVal);
        var chart = new CanvasLayerChart<>(xax1, yax1);

        var colormap = ColorMaps.getColorMaps().get("Viridis");

        Function<CanvasLayerChart.DataPoint<? extends Number, ? extends Number, PathObject>, Number> colorFun = dp -> dp.getAssociatedObject().getMeasurements().get(colorVal).doubleValue();
        var layer = new CanvasLayerChart.ContinuousScatterLayer<>(new Canvas(), points, colorVal, colormap, colorFun);
        var layer2 = new CanvasLayerChart.LinearTrendLayer<>(new Canvas(), points, "OLS", Color.RED);
        var layer3 = new CanvasLayerChart.DemingTrendLayer<>(new Canvas(), points, "Orthogonal", Color.RED);

        chart.layers.add(layer2);
        chart.layers.add(layer3);
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
        var xax2 = new NumberAxis();
        xax2.setLabel(xVal);
        var yax2 = new NumberAxis();
        yax2.setLabel(yVal);

        var chart2 = new CanvasLayerChart<>(xax2, yax2);
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
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        setPadding(new Insets(10, 30, 10, 30));
        getChildren().add(borderPane);

        borderPane.setCenter(gridPane);
//        borderPane.setMinSize(0, 0);
        BorderPane.setMargin(gridPane, new Insets(10, 10, 10, 10));

        gridPane.add(yAxis, 0, 0);
        gridPane.add(stackPane, 1, 0);
        gridPane.add(xAxis, 1, 1);
        yAxis.setSide(Side.LEFT);
        xAxis.setSide(Side.BOTTOM);
        GridPaneUtils.setToExpandGridPaneHeight(stackPane);
        GridPaneUtils.setToExpandGridPaneWidth(stackPane);
        baseCanvas.widthProperty().bind(stackPane.widthProperty());
        baseCanvas.heightProperty().bind(stackPane.heightProperty());
        stackPane.getChildren().add(baseCanvas);

        stackPane.setMinSize(0, 0);

        layers.addListener((ListChangeListener<PlotLayer<X, Y>>) c -> {
            if (c.next()) {
                c.getAddedSubList().forEach(layer -> {
                    gridPane.widthProperty().addListener(_ -> redrawNeeded = true);
                    gridPane.heightProperty().addListener(_ -> redrawNeeded = true);
                    var canvas = layer.getCanvas();
                    stackPane.getChildren().add(canvas);
                    canvas.widthProperty().bind(stackPane.widthProperty());
                    canvas.heightProperty().bind(stackPane.heightProperty());
                });
                updateAxisRange();
                updateLegend();
                updatePlot();
            }
        });
        borderPane.sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });
    }

    @Override
    protected void layoutChildren() {
        Insets insets = getPadding();
        double x = insets.getLeft();
        double y = insets.getTop();
        double w = getWidth() - insets.getLeft() - insets.getRight();
        double h = getHeight() - insets.getTop() - insets.getBottom();

        for (Node child: getManagedChildren()) {
            if (child.isManaged()) {
                layoutInArea(child, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
            }
        }
    }

    public ObservableList<PlotLayer<X, Y>> getLayers() {
        return layers;
    }

    protected void updatePlot() {
        for (PlotLayer<X, Y> layer : layers) {
            // todo redraw only if needed
            layer.updateCanvas(layer.getCanvas(), getXAxis(), getYAxis());
        }
    }

    protected void updateAxisRange() {
        for (PlotLayer<X, Y> layer : layers) {
            layer.updateAxes(getXAxis(), getYAxis());
        }
        baseCanvas.getGraphicsContext2D().clearRect(0, 0, baseCanvas.getWidth(), baseCanvas.getHeight());
        var xTicks = xAxis.getTickMarks();
        // todo abstract this over x and y, plus enable line styles
        for (var tick: xTicks) {
            var g2d = baseCanvas.getGraphicsContext2D();
            g2d.setGlobalAlpha(0.2);
            var color = Color.GRAY;
            g2d.setStroke(color);
            g2d.beginPath();
            g2d.moveTo(tick.getPosition(), 0);
            g2d.lineTo(tick.getPosition(), baseCanvas.getHeight());
            g2d.stroke();
        }
        var yTicks = yAxis.getTickMarks();
        for (var tick: yTicks) {
            var g2d = baseCanvas.getGraphicsContext2D();
            g2d.setGlobalAlpha(0.2);
            var color = Color.GRAY;
            g2d.setStroke(color);
            g2d.beginPath();
            g2d.moveTo(0, tick.getPosition());
            g2d.lineTo(baseCanvas.getWidth(), tick.getPosition());
            g2d.stroke();
        }
    }


    public Axis<Y> getYAxis() {
        return yAxis;
    }

    public Axis<X> getXAxis() {
        return xAxis;
    }

    protected void updateLegend() {
        HBox legend = new HBox();
        legend.setAlignment(Pos.CENTER);
        legend.setSpacing(10);
        for (PlotLayer<X, Y> layer : layers) {
            legend.getChildren().add(layer.getLegend());
        }
        setLegend(legend);
    }

    private void setLegend(Node legend) {
        // todo move or change me pls
        BorderPane.setMargin(legend, new Insets(10, 10, 10, 10));
        borderPane.setBottom(legend);
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
        protected final Color color;
        protected static final double OPACITY_FACTOR = 0.5;
        private RegressionParams regressionParams;

        LinearTrendLayer(Canvas canvas, Collection<? extends DataPoint<Number, Number, T>> points) {
            this(canvas, points, "Trend", Color.GREY);
        }

        LinearTrendLayer(Canvas canvas, Collection<? extends DataPoint<Number, Number, T>> points, String name, Color color) {
            super(canvas, points);
            this.name = name;
            this.color = color;
            this.data.addListener((InvalidationListener) _ -> calcOLS());
            calcOLS();
        }

        private void calcOLS() {
            SimpleRegression regression = new SimpleRegression();
            double xSum = 0;
            double xMin = Double.MAX_VALUE;
            double xMax = -Double.MAX_VALUE;
            int n = getData().size();
            for (DataPoint<? extends Number, ? extends Number, T> dataPoint : getData()) {
                double x = dataPoint.getX().doubleValue();
                double y = dataPoint.getY().doubleValue();
                xSum += x;
                if (x < xMin) {
                    xMin = x;
                }
                if (x > xMax) {
                    xMax = x;
                }
                regression.addData(x, y);
            }
            // expand a lot just in case
            xMin = xMin / 2;
            xMax = xMax * 2;

            double xMean = xSum / n;


            double mse = regression.getMeanSquareError();
            double ssd = regression.getXSumSquares();
            var td = new TDistribution((double) n - 2);
            double ctv = td.inverseCumulativeProbability(0.975);

            int nEvalPoints = 200;
            double step = Math.abs(xMax - xMin) / (nEvalPoints - 1);
            double x0 = xMin;

            double[] xPoints = new double[nEvalPoints];
            double[] yLowerPoints = new double[nEvalPoints];
            double[] yUpperPoints = new double[nEvalPoints];

            // calculate confidence band at range of x values
            for (int i = 0; i < nEvalPoints; i++) {
                double yHat0 = regression.predict(x0);
                double seY0Hat = Math.sqrt(mse * ((1 / (double) n + (Math.pow(x0 - xMean, 2) / ssd))));
                xPoints[i] = x0;
                yLowerPoints[i] = yHat0 - (ctv * seY0Hat);
                yUpperPoints[i] = yHat0 + (ctv * seY0Hat);
                x0 += step;
            }

            double[] allXPoints = new double[nEvalPoints * 2];
            double[] allYPoints = new double[nEvalPoints * 2];
            for (int i = 0; i < nEvalPoints; i++) {
                // first n points forward in X
                allXPoints[i] = xPoints[i];
                allYPoints[i] = yLowerPoints[i];
                // last n points backwards in X from the top
                allXPoints[nEvalPoints + i] = xPoints[xPoints.length - i - 1];
                allYPoints[nEvalPoints + i] = yUpperPoints[yUpperPoints.length - i - 1];
            }
            this.regressionParams = new RegressionParams(
                    regression, xMean, allXPoints, allYPoints
            );
        }

        private record RegressionParams(
                SimpleRegression regression,
                double xMean,
                double[] allXPoints, double[] allYPoints) {

        }

        @Override
        public void updateCanvas(Canvas canvas, Axis<Number> xAxis, Axis<Number> yAxis) {
            var g2d = canvas.getGraphicsContext2D();
            g2d.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

            // todo somehow separate drawing code into path/line and uncertainty?
            // draw just the line
            g2d.setGlobalAlpha(1);
            g2d.setStroke(color);
            g2d.beginPath();
            double xMin = ((NumberAxis)xAxis).getLowerBound();
            double xMax = ((NumberAxis)xAxis).getUpperBound();
            g2d.moveTo(xAxis.getDisplayPosition(xMin), yAxis.getDisplayPosition(regressionParams.regression.predict(xMin)));
            g2d.lineTo(xAxis.getDisplayPosition(xMax), yAxis.getDisplayPosition(regressionParams.regression.predict(xMax)));
            g2d.stroke();

            // draw the polygon
            g2d.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
            g2d.fillPolygon(
                    Arrays.stream(regressionParams.allXPoints).map(xAxis::getDisplayPosition).toArray(),
                    Arrays.stream(regressionParams.allYPoints).map(yAxis::getDisplayPosition).toArray(),
                    regressionParams.allXPoints.length
            );
        }

        @Override
        public Node getLegend() {
            HBox legendItem = new HBox();
            legendItem.setSpacing(5);
            legendItem.setAlignment(Pos.CENTER);
            StackPane symbol = new StackPane();
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
            symbol.getChildren().addAll(poly, line);
            Label label = new Label(name);
            legendItem.getChildren().addAll(symbol, label);
            setLegendItemListener(legendItem, this.getCanvas());
            return legendItem;
        }

    }

    static class DemingTrendLayer<T> extends LinearTrendLayer<T> {

        private OrthogonalRegression regression;

        DemingTrendLayer(Canvas canvas, Collection<? extends DataPoint<Number, Number, T>> dataPoints, String name, Color color) {
            super(canvas, dataPoints, name, color);
            data.addListener((InvalidationListener) observable -> regression = calculateRegression(data));
            regression = calculateRegression(data);
        }

        @Override
        public void updateCanvas(Canvas canvas, Axis<Number> xAxis, Axis<Number> yAxis) {
            double xMin = ((NumberAxis)xAxis).getLowerBound();
            double yMin = regression.predict(xMin);
            double xMax = ((NumberAxis)xAxis).getUpperBound();
            double yMax = regression.predict(xMax);

            var g2d = canvas.getGraphicsContext2D();
            g2d.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g2d.setGlobalAlpha(1);
            g2d.setStroke(color);
            g2d.beginPath();
            g2d.moveTo(xAxis.getDisplayPosition(xMin), yAxis.getDisplayPosition(yMin));
            g2d.lineTo(xAxis.getDisplayPosition(xMax), yAxis.getDisplayPosition(yMax));
            g2d.stroke();


            // todo don't bootstrap in draw loop idiot
            int nBootStraps = 1000;
            int nEvalPoints = 50;
            double step = Math.abs(xMax - xMin) / (nEvalPoints - 1);

            double[] xPoints = new double[nEvalPoints];

            // todo much better to parallelise this...
            // calculate confidence band at range of x values
            double[][] yVals =  new double[nEvalPoints][nBootStraps];
            for (int i = 0; i < nBootStraps; i++) {
                var res = resample(data);
                var reg = calculateRegression(res);
                double x0 = xMin;
                for (int j = 0; j < nEvalPoints; j++) {
                    yVals[j][i] = reg.predict(x0);
                    xPoints[j] = x0;
                    x0 += step;
                }
            }

            double[] yLowerPoints = new double[nEvalPoints];
            double[] yUpperPoints = new double[nEvalPoints];
            for (int j = 0; j < nEvalPoints; j++) {
                var perc = new Percentile();
                perc.setData(yVals[j]);
                yLowerPoints[j] = perc.evaluate(2.5);
                yUpperPoints[j] = perc.evaluate(97.5);
            }

            double[] allXPoints = new double[nEvalPoints * 2];
            double[] allYPoints = new double[nEvalPoints * 2];
            for (int i = 0; i < nEvalPoints; i++) {
                // first n points forward in X
                allXPoints[i] = xPoints[i];
                allYPoints[i] = yLowerPoints[i];
                // last n points backwards in X from the top
                allXPoints[nEvalPoints + i] = xPoints[xPoints.length - i - 1];
                allYPoints[nEvalPoints + i] = yUpperPoints[yUpperPoints.length - i - 1];
            }

            g2d.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
            g2d.fillPolygon(
                    Arrays.stream(allXPoints).map(xAxis::getDisplayPosition).toArray(),
                    Arrays.stream(allYPoints).map(yAxis::getDisplayPosition).toArray(),
                    allXPoints.length
            );

        }

        private static <E> List<E> resample(List<E> input) {
            List<E> output = new ArrayList<>(input.size());
            Random random = new Random();
            for (int i = 0; i < input.size(); i++) {
                output.add(input.get(random.nextInt(input.size())));
            }
            return output;
        }


        private static @NonNull OrthogonalRegression calculateRegression(List<? extends DataPoint<? extends Number, ? extends Number, ?>> dataPoints) {
            double[] x = new double[dataPoints.size()];
            double[] y = new double[dataPoints.size()];
            double xSum = 0, ySum = 0, xySum = 0, xsqSum = 0, ysqSum = 0;
            for (int i = 0; i < dataPoints.size(); i++) {
                var dataPoint = dataPoints.get(i);
                x[i] = dataPoint.getX().doubleValue();
                y[i] = dataPoint.getY().doubleValue();
                xSum += x[i];
                ySum += y[i];
                xsqSum += x[i] * x[i];
                ysqSum += y[i] * y[i];
                xySum += x[i] * y[i];
            }
            double xBar = xSum / x.length;
            double yBar = ySum / y.length;
            double xyBar = xySum / y.length;
            double xsqBar = xsqSum / x.length;
            double ysqBar = ysqSum / y.length;
            double sxx = xsqBar - (xBar * xBar);
            double syy = ysqBar - (yBar * yBar);
            double sxy = xyBar - (xBar * yBar);

            // todo delta
            double delta = 1;
            double beta1 = (syy - (delta * sxx) + Math.sqrt(Math.pow(syy - (delta * sxx), 2) + 4 * delta * Math.pow(sxy, 2))) / (2 * sxy);
            double beta0 = yBar - beta1 * xBar;
            return new OrthogonalRegression(beta1, beta0);
        }

        private record OrthogonalRegression(double beta1, double beta0) {
            protected double predict(double x) {
                return beta0() + (beta1() * x);
            }
        }
    }

    static abstract class NumberNumberLayer<T> implements PlotLayer<Number, Number> {
        private final Canvas canvas;
        protected final ObservableList<? extends DataPoint<? extends Number, ? extends Number, T>> data;

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
            HBox legendContainer = new HBox(); // todo probably need to handle different orientations...
            legendContainer.setAlignment(Pos.TOP_CENTER);
            legendContainer.setSpacing(5);
            Label label = new Label(name);
            int width = 100;
            int height = 20;

            Rectangle scaleRect = new Rectangle(width, height);
            List<Stop> stops = createStops(colorMap, colorAxis.getLowerBound(), colorAxis.getUpperBound(), 100);
            var grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
            scaleRect.setFill(grad);

            scaleRect.widthProperty().bind(colorAxis.widthProperty());

            VBox scale = new VBox(scaleRect, colorAxis);
            legendContainer.getChildren().addAll(scale, label);
            setLegendItemListener(legendContainer, this.getCanvas());
            return legendContainer;
        }

    }

    private static void setLegendItemListener(Pane legendItem, Canvas canvas) {
        canvas.visibleProperty().addListener(_ -> {
            if (canvas.isVisible()) {
                legendItem.setOpacity(1);
            } else {
                legendItem.setOpacity(0.2);
            }
        });
        legendItem.setOnMouseClicked(_ -> canvas.setVisible(!canvas.isVisible()));
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
            HBox legendItem = new HBox(); // todo probably need to handle different orientations...
            legendItem.setAlignment(Pos.CENTER);
            legendItem.setSpacing(5);
            Label label = new Label(name);
            Circle symbol = new Circle(5, color);
            legendItem.getChildren().addAll(symbol, label);
            setLegendItemListener(legendItem, this.getCanvas());
            return legendItem;
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

    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            handlePulse();
        }

    };

    private void handlePulse() {
        if (redrawNeeded) {
            updateAxisRange();
            updatePlot();
        }
        redrawNeeded = false;
    }
}
