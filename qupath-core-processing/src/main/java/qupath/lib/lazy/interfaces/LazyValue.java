package qupath.lib.lazy.interfaces;

import qupath.lib.common.GeneralTools;
import qupath.lib.objects.PathObject;

/**
 * Interface that can generate a 'lazy' measurement for a {@link PathObject}.
 * @param <T>
 */
public interface LazyValue<T> {

    /**
     * The name of the measurement.
     * @return the name of the measurement
     */
    String getName();

    /**
     * Get the generic type of the measurement.
     * @return
     */
    Class<T> getMeasurementType();

    /**
     * Check whether the value returned by this measurement is an instance of {@link Number}.
     * @return
     */
    default boolean isNumeric() {
        return Number.class.isAssignableFrom(getMeasurementType());
    }

    /**
     * Check whether the value returned by this measurement is an instance of {@link String}.
     * @return
     */
    default boolean isString() {
        return String.class.isAssignableFrom(getMeasurementType());
    }

    /**
     * Check whether the value returned by this measurement is an instance of {@link Boolean}.
     * @return
     */
    default boolean isBoolean() {
        return Boolean.class.isAssignableFrom(getMeasurementType());
    }

    /**
     * Create a binding that represents a lazily-computed measurement for the provided objects.
     * @param pathObject the object that should be measured
     * @return a binding that can return the measurement value
     */
    T getValue(final PathObject pathObject);

    /**
     * Get a default string representation of an object measurement.
     * @param pathObject the object to measure
     * @param decimalPlaces number of decimal places; if &lt; 0 then this will be calculated automatically
     * @return
     */
    default String getStringValue(final PathObject pathObject, final int decimalPlaces) {
        var val = getValue(pathObject);
        return switch (val) {
            case null -> null;
            case Number num -> formatNumber(num.doubleValue(), decimalPlaces);
            default -> val.toString();
        };
    }

    /**
     * Get a default string representation of an object measurement.
     * If the value is numeric, it is converted to a string using the default number of decimal places.
     * @param pathObject the object to measure
     * @return
     * @see #getStringValue(PathObject, int)
     * @see #getValue(PathObject)
     */
    default String getStringValue(final PathObject pathObject) {
        return getStringValue(pathObject, -1);
    }

    /**
     * Optional help text that explained the measurement.
     * This may be displayed in a tooltip.
     * @return the help text, or null if no help text is available
     */
    String getHelpText();


    private static String formatNumber(double val, int decimalPlaces) {
        if (Double.isNaN(val))
            return "NaN";
        if (decimalPlaces == 0)
            return Long.toString(Math.round(val));
        int dp = decimalPlaces;
        // Format in some sensible way
        if (decimalPlaces < 0) {
            if (val > 1000)
                dp = 1;
            else if (val > 10)
                dp = 2;
            else if (val > 1)
                dp = 3;
            else
                dp = 4;
        }
        return GeneralTools.formatNumber(val, dp);
    }

}
