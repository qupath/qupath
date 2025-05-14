package qupath.lib.lazy.interfaces;

import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestValueFormatter {

    private static char sep;

    @BeforeAll
    static void setSep() {
        sep = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
    }

    @Test
    void testInteger() {
        assertEquals("3", ValueFormatter.getStringValue(3, 2));
        assertEquals("3", ValueFormatter.getStringValue(3, -2));
        assertEquals("3", ValueFormatter.getStringValue(3, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @Test
    void testLong() {
        assertEquals("4", ValueFormatter.getStringValue(4L, 2));
        assertEquals("4", ValueFormatter.getStringValue(4L, -2));
        assertEquals("4", ValueFormatter.getStringValue(4L, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @Test
    void testDouble() {
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245, 1));
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245, -1));
        assertEquals("5" + sep + "25", ValueFormatter.getStringValue(5.245, 2));
        assertEquals("5" + sep + "25", ValueFormatter.getStringValue(5.245, -2));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245, 3));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245, -3));
        assertEquals("5" + sep + "2450", ValueFormatter.getStringValue(5.245, 4));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245, -4));
    }

    @Test
    void testNegative() {
        assertEquals("-5" + sep + "2", ValueFormatter.getStringValue(-5.245, 1));
        assertEquals("-5" + sep + "2", ValueFormatter.getStringValue(-5.245, -1));
        assertEquals("-5" + sep + "25", ValueFormatter.getStringValue(-5.245, 2));
        assertEquals("-5" + sep + "25", ValueFormatter.getStringValue(-5.245, -2));
        assertEquals("-5" + sep + "245", ValueFormatter.getStringValue(-5.245, 3));
        assertEquals("-5" + sep + "245", ValueFormatter.getStringValue(-5.245, -3));
        assertEquals("-5" + sep + "2450", ValueFormatter.getStringValue(-5.245, 4));
        assertEquals("-5" + sep + "245", ValueFormatter.getStringValue(-5.245, -4));
    }

    @Test
    void testNaN() {
        assertEquals("NaN", ValueFormatter.getStringValue(Double.NaN, 5));
        assertEquals("NaN", ValueFormatter.getStringValue(Double.NaN, -5));
        assertEquals("NaN", ValueFormatter.getStringValue(Double.NaN, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @Test
    void testInfinity() {
        assertEquals("∞", ValueFormatter.getStringValue(Double.POSITIVE_INFINITY, 5));
        assertEquals("∞", ValueFormatter.getStringValue(Double.POSITIVE_INFINITY, -5));
        assertEquals("∞", ValueFormatter.getStringValue(Double.POSITIVE_INFINITY, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @Test
    void testNegativeInfinity() {
        assertEquals("-∞", ValueFormatter.getStringValue(Double.NEGATIVE_INFINITY, 5));
        assertEquals("-∞", ValueFormatter.getStringValue(Double.NEGATIVE_INFINITY, -5));
        assertEquals("-∞", ValueFormatter.getStringValue(Double.NEGATIVE_INFINITY, LazyValue.DEFAULT_DECIMAL_PLACES));
    }

    @Test
    void testFloat() {
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245f, 1));
        assertEquals("5" + sep + "2", ValueFormatter.getStringValue(5.245f, -1));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245f, 3));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245f, -3));
        assertEquals("5" + sep + "2450", ValueFormatter.getStringValue(5.245f, 4));
        assertEquals("5" + sep + "245", ValueFormatter.getStringValue(5.245f, -4));
    }

    @Test
    void testNull() {
        assertEquals("", ValueFormatter.getStringValue(null, 1));
    }

    @Test
    void testString() {
        assertEquals("any string", ValueFormatter.getStringValue("any string", 5));
        assertEquals("any string", ValueFormatter.getStringValue("any string", -5));
        assertEquals("any string", ValueFormatter.getStringValue("any string", LazyValue.DEFAULT_DECIMAL_PLACES));
    }

}
