package qupath.lib.gui.charts.display;

import javafx.beans.property.ObjectProperty;
import javafx.scene.layout.Pane;
import qupath.lib.gui.measure.PathTableData;

/**
 * A wrapper for a chart for displaying data about PathObject measurements and classes, and similar objects.
 */
public interface PlotDisplay<T> {

    /**
     * The observable property underlying the data model
     * @return the observable
     */
    ObjectProperty<PathTableData<T>> modelProperty();

    /**
     * Get the data model underlying the data
     * @return the data model
     */
    default PathTableData<T> getModel() {
        return modelProperty().get();
    }

    /**
     * Update the data model underlying the data
     * @param model the data model
     */
    default void setModel(PathTableData<T> model) {
        modelProperty().set(model);
    }

    /**
     * Get the primary pane used to display the plot
     * @return the pane
     */
    Pane getPane();

    /**
     * Request the plot be updated with current settings
     */
    void requestReplot();

}
