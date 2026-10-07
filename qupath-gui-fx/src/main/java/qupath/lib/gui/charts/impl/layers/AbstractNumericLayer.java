package qupath.lib.gui.charts.impl.layers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;

abstract class AbstractNumericLayer<T> implements PlotLayer<Number, Number> {
    protected final Canvas canvas = new Canvas();
    protected final ObservableList<? extends DataPoint<? extends Number, ? extends Number, T>> data;

    AbstractNumericLayer(Collection<? extends DataPoint<? extends Number,? extends Number,T>> points) {
        if (points == null) {
            points = Collections.emptyList();
        }
        this.data = FXCollections.observableArrayList(points);
    }

    @Override
    public Canvas getCanvas() {
        return canvas;
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


    protected ObservableList<? extends DataPoint<? extends Number, ? extends Number, T>> getData() {
        return data;
    }

}
