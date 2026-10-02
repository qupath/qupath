package qupath.lib.gui.charts.impl;

import java.util.List;
import java.util.Optional;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;

/**
 * A canvas-based chart
 * @param <X> the x-axis type (numeric or string, probably)
 * @param <Y> the y-axis type (numeric or string, probably)
 */
public interface CanvasChart<X, Y> {
    // note that canvas could be an ObjectProperty

    /**
     * Get the canvas used to render points
     * @return the canvas
     */
    Canvas getCanvas();

    /**
     * Find the datapoint closest to plot area x,y coordinates
     * @param x the pixel x-coordinate
     * @param y the pixel y-coordinate
     * @return the nearest point that contains the input point
     */
    Optional<XYChart.Data<X, Y>> findDataPoint(double x, double y, double tolerance);


    // my best attempt at non-terrible non-clashing default colors. Could instead use something from ColorBrewer
    /**
     * A default color palette when colors aren't supplied
     * @return An immutable list of pre-defined colors.
     */
    default List<Color> getDefaultColors() {
        return List.of(
            Color.FIREBRICK, Color.DODGERBLUE, Color.FORESTGREEN,
            Color.GOLDENROD, Color.DARKMAGENTA, Color.TEAL,
            Color.DEEPPINK, Color.CHOCOLATE, Color.SLATEBLUE,
            Color.DARKSLATEGRAY);
    }
}
