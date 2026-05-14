package qupath.lib.lazy.interfaces;

import java.util.function.Function;

/**
 * Interface that can generate a 'lazy' value from an object.
 * <p>
 * Values can have any type, but are generally expected to be {@link Boolean}, {@link Number} or {@link String}.
 *
 * @param <S> type of the input object used to determine the value
 * @param <T> type of the output value
 */
public interface LazyValue<S, T> {

    /**
     * Constant representing that the default number of decimal places should be used to convert floating
     * point numbers to strings.
     */
    int DEFAULT_DECIMAL_PLACES = Integer.MIN_VALUE;

    /**
     * Create a {@link LazyValue} with specified name and help text.
     * @param name name
     * @param helpText help text or description
     * @param fun function to calculate the value
     * @param valueType return type of the function
     * @return a new lazy value
     * @param <S> input type
     * @param <T> value type
     */
    static <S, T> LazyValue<S, T> create(String name, String helpText, Function<S, T> fun, Class<T> valueType) {
        return new DefaultLazyValue<>(name, helpText, fun, valueType);
    }

    /**
     * Create a {@link LazyNumericValue} with specified name and help text.
     * @param name name
     * @param helpText help text or description
     * @param fun function to calculate the value
     * @return a new lazy value
     * @param <S> input type
     */
    static <S> LazyNumericValue<S> createNumeric(String name, String helpText, Function<S, Number> fun) {
        return new DefaultNumericLazyValue<>(name, helpText, fun);
    }

    /**
     * Create a {@link LazyStringValue} with specified name and help text.
     * @param name name
     * @param helpText help text or description
     * @param fun function to calculate the value
     * @return a new lazy value
     * @param <S> input type
     */
    static <S> LazyStringValue<S> createString(String name, String helpText, Function<S, String> fun) {
        return new DefaultStringLazyValue<>(name, helpText, fun);
    }

    /**
     * Create a {@link LazyBooleanValue} with specified name and help text.
     * @param name name
     * @param helpText help text or description
     * @param fun function to calculate the value
     * @return a new lazy value
     * @param <S> input type
     */
    static <S> LazyBooleanValue<S> createBoolean(String name, String helpText, Function<S, Boolean> fun) {
        return new DefaultBooleanLazyValue<>(name, helpText, fun);
    }


    /**
     * The name of the value.
     * When showing a measurement table, this would be the column header.
     * @return the name of the value
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
     * Calculate a value from the input.
     * @param input the input that should be measured
     * @return the output value
     */
    T getValue(final S input);

    /**
     * Get a default string representation of an object measurement.
     * @param input the object to measure
     * @param decimalPlaces number of decimal places; if &lt; 0 then this will be calculated automatically
     * @return
     */
    default String getStringValue(final S input, final int decimalPlaces) {
        var val = getValue(input);
        return ValueFormatter.getStringValue(val, decimalPlaces);
    }

    /**
     * Get a default string representation of an object measurement.
     * If the value is numeric, it is converted to a string using the default number of decimal places.
     * @param input the object to measure
     * @return
     * @see #getStringValue(S, int)
     * @see #getValue(S)
     */
    default String getStringValue(final S input) {
        return getStringValue(input, DEFAULT_DECIMAL_PLACES);
    }

    /**
     * Optional help text that explained the value.
     * This may be displayed in a tooltip.
     * @return the help text, or null if no help text is available
     */
    String getHelpText();

}
