package qupath.lib.gui.charts.impl.layers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.paint.Color;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.jspecify.annotations.NonNull;

/**
 * A layer summarizing datapoints into an orthogonal regression line, optimizing total least squares
 * @param <T> the type of object wrapped in the datapoints
 */
public class OrthogonalRegressionLayer<T> extends OLSLayer<T> {

    private @NonNull BootstrappedOrthogonalRegression regression;
    private final BooleanProperty doBootstrap = new SimpleBooleanProperty(false);

    /**
     * Create a layer summarizing datapoints in orthogonal regression
     * @param dataPoints the datapoints being summarized
     * @param name the layer name
     * @param color the layer display color
     */
    public OrthogonalRegressionLayer(Collection<? extends DataPoint<Number, Number, T>> dataPoints, String name, Color color) {
        super(dataPoints, name, color);
        data.addListener((InvalidationListener) _ -> regression = calculateRegression(data, doBootstrap.get()));
        regression = calculateRegression(data, doBootstrap.get());
    }

    @Override
    public void updateCanvas(Axis<Number> xAxis, Axis<Number> yAxis) {
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

        if (regression.xPoints != null) {
            g2d.setFill(color.deriveColor(1, 1, 1, OPACITY_FACTOR));
            g2d.fillPolygon(
                    Arrays.stream(regression.xPoints).map(xAxis::getDisplayPosition).toArray(),
                    Arrays.stream(regression.yPoints).map(yAxis::getDisplayPosition).toArray(),
                    regression.xPoints.length
            );
        }

    }

    private static <E> List<E> resample(List<E> input) {
        List<E> output = new ArrayList<>(input.size());
        Random random = new Random();
        for (int i = 0; i < input.size(); i++) {
            output.add(input.get(random.nextInt(input.size())));
        }
        return output;
    }


    private static @NonNull BootstrappedOrthogonalRegression calculateRegression(List<? extends DataPoint<? extends Number, ? extends Number, ?>> dataPoints, boolean doBootstrap) {
        OrthogonalRegression result = computeRegression(dataPoints);

        if (!doBootstrap) {
            return new BootstrappedOrthogonalRegression(result, null, null);
        }
        // todo don't bootstrap in draw loop idiot
        int nBootStraps = 1000;
        int nEvalPoints = 50;
        double step = Math.abs(result.xMax() - result.xMin()) / (nEvalPoints - 1);

        double[] xPoints = new double[nEvalPoints];
        // todo much better to parallelise this...
        // calculate confidence band at range of x values
        double x0 = result.xMin();
        for (int j = 0; j < nEvalPoints; j++) {
            xPoints[j] = x0 + j * step;
        }
        double[][] yVals =  new double[nEvalPoints][nBootStraps];
        IntStream.range(0, nBootStraps).forEach(i -> {
            var resample = resample(dataPoints);
            OrthogonalRegression reg = computeRegression(resample);
            double xx0 = result.xMin();
            for (int j = 0; j < nEvalPoints; j++) {
                yVals[j][i] = reg.predict(xx0);
                xx0 += step;
            }
        });

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

        return new BootstrappedOrthogonalRegression(result, allXPoints, allYPoints);
    }

    private record BootstrappedOrthogonalRegression(OrthogonalRegression regression, double[] xPoints, double[] yPoints) {
        double predict(double x) {
            return regression.predict(x);
        }
    }

    private static @NonNull OrthogonalRegression computeRegression(List<? extends DataPoint<? extends Number, ? extends Number, ?>> dataPoints) {
        double[] x = new double[dataPoints.size()];
        double[] y = new double[dataPoints.size()];
        double xSum = 0, ySum = 0, xySum = 0, xsqSum = 0, ysqSum = 0;
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE;
        for (int i = 0; i < dataPoints.size(); i++) {
            var dataPoint = dataPoints.get(i);
            x[i] = dataPoint.getX().doubleValue();
            y[i] = dataPoint.getY().doubleValue();
            xSum += x[i];
            ySum += y[i];
            xsqSum += x[i] * x[i];
            ysqSum += y[i] * y[i];
            xySum += x[i] * y[i];
            if (x[i] < xMin) {
                xMin = x[i];
            }
            if (x[i] > xMax) {
                xMax = x[i];
            }
        }
        xMin /= 2;
        xMax *= 2;
        double xBar = xSum / x.length;
        double yBar = ySum / y.length;
        double xyBar = xySum / y.length;
        double xsqBar = xsqSum / x.length;
        double ysqBar = ysqSum / y.length;
        double sxx = xsqBar - (xBar * xBar);
        double syy = ysqBar - (yBar * yBar);
        double sxy = xyBar - (xBar * yBar);

        double delta = 1; // this allows for non-orthogonal regression variants but we fix it at 1
        double slope = (syy - (delta * sxx) + Math.sqrt(Math.pow(syy - (delta * sxx), 2) + 4 * delta * Math.pow(sxy, 2))) / (2 * sxy);
        double intercept = yBar - slope * xBar;
        return new OrthogonalRegression(xMin, xMax, slope, intercept);
    }

    private record OrthogonalRegression(double xMin, double xMax, double slope, double intercept) {
        double predict(double x) {
            return intercept() + (slope() * x);
        }
    }

}