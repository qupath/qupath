package qupath.lib.gui.plots.display;

import javafx.beans.property.ObjectProperty;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;
import qupath.lib.gui.measure.PathTableData;

/**
 * A wrapper for a chart for displaying data about PathObject measurements and classes, and similar objects.
 */
public interface PlotDisplay<T> {

    ObjectProperty<PathTableData<T>> modelProperty();

    PathTableData<T> getModel();

    XYChart<?,?> getChart();

    /**
     * Return the name of this type of plot, e.g., "Box plot", "Scatter plot", "Histogram"
     * @return the type of plot
     */
    String getName();

    /**
     * Get the primary pane used to display the plot
     * @return the pane
     */
    Pane getPane();

    /**
     * Request the plot be updated with current settings
     */
    void requestReplot();

    /**
     * Clear the plot data and displayed plot.
     */
    default void clearPlot() {
        getChart().getData().clear();
    }

    /**
     * Update the data model underlying the data
     * @param model the data model
     */
    void setModel(PathTableData<T> model);


    /**
     * Update plot for specified data columns.
     * @param columns the names of the columns to show
     */
    void plotColumns(String... columns);
}
