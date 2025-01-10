package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import qupath.lib.objects.PathObject;

/**
 * Interface that can generate a 'lazy' measurement for a {@link PathObject}.
 * @param <T>
 */
interface MeasurementBuilder<T> {

    /**
     * The name of the measurement.
     * @return the name of the measurement
     */
    String getName();

    /**
     * Create a binding that represents a lazily-computed measurement for the provided objects.
     * @param pathObject the object that should be measured
     * @return a binding that can return the measurement value
     */
    Binding<T> createMeasurement(final PathObject pathObject);

    /**
     * Optional help text that explained the measurement.
     * This may be displayed in a tooltip.
     * @return the help text, or null if no help text is available
     */
    String getHelpText();

}
