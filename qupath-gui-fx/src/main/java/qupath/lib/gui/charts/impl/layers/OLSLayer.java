package qupath.lib.gui.charts.impl.layers;

import java.util.Arrays;
import java.util.Collection;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 * A layer summarizing DataPoints using ordinary least squares regression, optionally including uncertainty
 * @param <T>
 */
public class OLSLayer<T> extends AbstractNumericLayer<T> {
    private final String name;
    protected final Color color;
    protected static final double OPACITY_FACTOR = 0.5;
    private final BooleanProperty displayUncertainty = new SimpleBooleanProperty(true);
    private RegressionParams regressionParams;

    public OLSLayer(Collection<? extends DataPoint<Number, Number, T>> points) {
        this(points, "Trend", Color.GREY);
    }

    public OLSLayer(Collection<? extends DataPoint<Number, Number, T>> points, String name, Color color) {
        super(points);
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
    public void updateCanvas(Axis<Number> xAxis, Axis<Number> yAxis) {
        var g2d = canvas.getGraphicsContext2D();
        g2d.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (displayUncertainty.get()) {
            // draw the uncertainty polygon/ribbon
            g2d.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
            g2d.fillPolygon(
                    Arrays.stream(regressionParams.allXPoints).map(xAxis::getDisplayPosition).toArray(),
                    Arrays.stream(regressionParams.allYPoints).map(yAxis::getDisplayPosition).toArray(),
                    regressionParams.allXPoints.length
            );
        }

        // draw just the line
        g2d.setGlobalAlpha(1);
        g2d.setStroke(color);
        g2d.beginPath();
        double xMin = ((NumberAxis)xAxis).getLowerBound();
        double xMax = ((NumberAxis)xAxis).getUpperBound();
        g2d.moveTo(xAxis.getDisplayPosition(xMin), yAxis.getDisplayPosition(regressionParams.regression.predict(xMin)));
        g2d.lineTo(xAxis.getDisplayPosition(xMax), yAxis.getDisplayPosition(regressionParams.regression.predict(xMax)));
        g2d.stroke();


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
        Line line = new Line(linewidth, width / 2, width - linewidth, (width / 2));
        line.setStrokeWidth(linewidth);
        line.setStroke(color);
        if (displayUncertainty.get()) {
            Polygon poly = new Polygon(
                    0, 0, width / 2, linewidth, width, 0,
                    width, width, width / 2, width - linewidth, 0, width

            );
            poly.setStroke(null);
            poly.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
            symbol.getChildren().add(poly);
        }
        symbol.getChildren().add(line);

        Label label = new Label(name);
        legendItem.getChildren().addAll(symbol, label);
        CanvasLayerChart.setLegendItemListener(legendItem, this.getCanvas());
        return legendItem;
    }

}
