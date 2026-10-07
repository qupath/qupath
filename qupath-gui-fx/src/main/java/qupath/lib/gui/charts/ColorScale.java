package qupath.lib.gui.charts;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;

/**
 * A simple color scale for mapping continuous or discrete values to colors in scatter charts and more.
 */
public interface ColorScale {

    /**
     * Prepare the color scale min, max and ticks
     * @param series the data to be plotted
     */
    void prepare(List<? extends XYChart.Series<?, ?>> series);

    /**
     * Get the color for a particular point in a particular series
     * @param series the series the point belongs to
     * @param data the data item itself
     * @return the color the point should be displayed in
     */
    Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);

    /**
     * Get a string representation of the category a point belongs to
     * @param series the series the point belongs to
     * @param data the data item itself
     * @return can be anything for continuous scales; it should be unused
     */
    String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);

    /**
     * Get the legend item for a particular point in a particular series.
     * <br>
     * Note that legend items should be shared between all points that should use the same legend representation;
     * i.e. they should be the <i>same</i> object (for example, for a continuous color scale all objects should use the same legend object).
     * @param series the series the point belongs to
     * @param data the data item itself
     * @return a Node
     */
    Node getLegendItem(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);
}
