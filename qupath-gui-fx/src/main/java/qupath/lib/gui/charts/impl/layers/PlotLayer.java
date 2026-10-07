package qupath.lib.gui.charts.impl.layers;


import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;

/**
 * Simple interface for a layer on a canvas plot
 * @param <X> the X-axis type, probably String or Number
 * @param <Y> the Y-axis type, probably String or Number
 */
public interface PlotLayer<X, Y> {
    /**
     * Get the canvas used by the layer to plot itself.
     * @return
     */
    Canvas getCanvas();

    /**
     * Request the layer to update its canvas, e.g. if the plot is resized
     * @param xAxis X-axis
     * @param yAxis Y-axis
     */
    void updateCanvas(Axis<X> xAxis, Axis<Y> yAxis);

    /**
     * Update the X- and Y-axis ranges
     * @param xAxis the X-axis
     * @param yAxis the Y-axis
     */
    void updateAxes(Axis<X> xAxis, Axis<Y> yAxis);

    /**
     * Get or create a legend for this layer
     * @return a node, ideally with useful information and interaction
     */
    Node getLegend();
}

