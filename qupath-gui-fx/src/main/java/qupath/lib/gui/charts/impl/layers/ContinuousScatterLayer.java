package qupath.lib.gui.charts.impl.layers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;

/**
 * A scatter layer mapping point color/fill to a continuous variable
 * @param <T> the type of object being wrapped in datapoints
 */
public class ContinuousScatterLayer<T> extends ScatterLayer<T> {
    private final ColorMaps.ColorMap colorMap;
    private final Function<DataPoint<? extends Number, ? extends Number, T>, Number> colorFun;
    private final NumberAxis colorAxis = new NumberAxis();
    private final String name;

    /**
     * Create a scatter layer wrapping T objects and mapping color/fill to a continuous variable.
     * @param points the datapoints
     * @param name the layer name
     * @param colorMap the mapping from number to color
     * @param colorVarFun a function used to extract the numeric value mapped to color
     */
    public ContinuousScatterLayer(
            Collection<? extends DataPoint<? extends Number, ? extends Number, T>> points,
            String name,
            ColorMaps.ColorMap colorMap,
            Function<DataPoint<? extends Number, ? extends Number, T>, Number> colorVarFun) {
        super(points);
        this.colorMap = colorMap;
        this.colorFun = colorVarFun;
        this.name = name;
        var colorVals = points.stream().map(colorVarFun).map(Number::doubleValue).toList();
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
        CanvasLayerChart.setLegendItemListener(legendContainer, this.getCanvas());
        return legendContainer;
    }

}
