package qupath.lib.gui.charts.impl.layers;

import java.util.Collection;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class ScatterLayer<T> extends AbstractNumericLayer<T> {

    private final Color color;
    private final String name;
    private final DoubleProperty markerRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);

    public ScatterLayer(Collection<? extends DataPoint<? extends Number, ? extends Number, T>> points) {
        super(points);
        this.color = Color.BLACK;
        this.name = "Scatter";
    }

    public ScatterLayer(Collection<? extends DataPoint<? extends Number, ? extends Number, T>> points,
                        String name,
                        Color color) {
        super(points);
        this.name = name;
        this.color = color;
    }

    @Override
    public void updateCanvas(Axis<Number> xAxis, Axis<Number> yAxis) {
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
        CanvasLayerChart.setLegendItemListener(legendItem, this.getCanvas());
        return legendItem;
    }

    private double getMarkerRadius() {
        return markerRadius.get();
    }

    private double getMarkerOpacity() {
        return markerOpacity.get();
    }

}
