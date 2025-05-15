package qupath.lib.lazy.interfaces;

import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestValueFormatter {

    static Collection<Locale> locales() {
        return Set.of(Locale.US, Locale.FRANCE, Locale.CHINESE);
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testInteger(Locale locale) {
        assertEquals("3", ValueFormatter.getStringValue(3, 2));
        assertEquals("3", ValueFormatter.getStringValue(3, -2));
        assertEquals("3", ValueFormatter.getStringValue(3, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testLong(Locale locale) {
        assertEquals("4", ValueFormatter.getStringValue(4L, 2));
        assertEquals("4", ValueFormatter.getStringValue(4L, -2));
        assertEquals("4", ValueFormatter.getStringValue(4L, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testDouble(Locale locale) {
        Locale.setDefault(locale);
        var sep = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245, 1));
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245, -1));
        assertEquals("5" + sep + "25", ValueFormatter.getStringValue(5.245, 2));
        assertEquals("5" + sep + "25", ValueFormatter.getStringValue(5.245, -2));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245, 3));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245, -3));
        assertEquals("5" + sep + "2450", ValueFormatter.getStringValue(5.245, 4));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245, -4));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testNegative(Locale locale) {
        Locale.setDefault(locale);
        var sep = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        assertEquals("-5" + sep + "2", ValueFormatter.getStringValue(-5.245, 1));
        assertEquals("-5" + sep + "2", ValueFormatter.getStringValue(-5.245, -1));
        assertEquals("-5" + sep + "25", ValueFormatter.getStringValue(-5.245, 2));
        assertEquals("-5" + sep + "25", ValueFormatter.getStringValue(-5.245, -2));
        assertEquals("-5" + sep + "245", ValueFormatter.getStringValue(-5.245, 3));
        assertEquals("-5" + sep + "245", ValueFormatter.getStringValue(-5.245, -3));
        assertEquals("-5" + sep + "2450", ValueFormatter.getStringValue(-5.245, 4));
        assertEquals("-5" + sep + "245", ValueFormatter.getStringValue(-5.245, -4));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testNaN(Locale locale) {
        assertEquals("NaN", ValueFormatter.getStringValue(Double.NaN, 5));
        assertEquals("NaN", ValueFormatter.getStringValue(Double.NaN, -5));
        assertEquals("NaN", ValueFormatter.getStringValue(Double.NaN, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testInfinity(Locale locale) {
        assertEquals("∞", ValueFormatter.getStringValue(Double.POSITIVE_INFINITY, 5));
        assertEquals("∞", ValueFormatter.getStringValue(Double.POSITIVE_INFINITY, -5));
        assertEquals("∞", ValueFormatter.getStringValue(Double.POSITIVE_INFINITY, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testNegativeInfinity(Locale locale) {
        assertEquals("-∞", ValueFormatter.getStringValue(Double.NEGATIVE_INFINITY, 5));
        assertEquals("-∞", ValueFormatter.getStringValue(Double.NEGATIVE_INFINITY, -5));
        assertEquals("-∞", ValueFormatter.getStringValue(Double.NEGATIVE_INFINITY, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testFloat(Locale locale) {
        Locale.setDefault(locale);
        var sep = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245f, 1));
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245f, -1));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245f, 3));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245f, -3));
        assertEquals("5" + sep + "2450", ValueFormatter.getStringValue(5.245f, 4));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245f, -4));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testNull(Locale locale) {
        assertEquals("", ValueFormatter.getStringValue(null, 1));
    }

    @ParameterizedTest
    @MethodSource("locales")
    void testString(Locale locale) {
        assertEquals("any string", ValueFormatter.getStringValue("any string", 5));
        assertEquals("any string", ValueFormatter.getStringValue("any string", -5));
        assertEquals("any string", ValueFormatter.getStringValue("any string", LazyValue.DEFAULT_DECIMAL_PLACES));
    }

}
