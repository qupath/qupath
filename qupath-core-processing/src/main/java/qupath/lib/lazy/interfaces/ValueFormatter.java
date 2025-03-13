package qupath.lib.lazy.interfaces;

import qupath.lib.common.GeneralTools;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class for formatting numbers to have specified decimal places.
 */
class ValueFormatter {

    private static final Map<Locale, Map<Integer, NumberFormat>> formatters = new ConcurrentHashMap<>();

    /**
     * Get a string representation of an object for display in a table, or empty string if the object is null.
     * See {@link #formatNumber(double, int)} for more detail of how {@code decimalPlaces} is deciphered.
     */
    static String getStringValue(final Object val, final int decimalPlaces) {
        if (val == null)
            return "";
        if (val instanceof Number num) {
            if (isFloat(num))
                return formatNumber(num.doubleValue(), decimalPlaces);
        }
        return Objects.toString(val);
    }

    private static boolean isFloat(final Number val) {
        // Probably double or possibly float, so check those first - then easier to exclude known integers
        return (val instanceof Double || val instanceof Float || !isInteger(val));
    }

    private static boolean isInteger(final Number val) {
        return val instanceof Integer ||
                val instanceof Long ||
                val instanceof BigInteger ||
                val instanceof Byte ||
                val instanceof Short ||
                val instanceof AtomicInteger ||
                val instanceof AtomicLong;
    }

    private static String formatNumber(double val, int decimalPlaces) {
        if (Double.isNaN(val))
            return "NaN";
        if (decimalPlaces == 0)
            // If no decimal places, just round
            return Long.toString(Math.round(val));
        if (decimalPlaces == LazyValue.DEFAULT_DECIMAL_PLACES) {
            // Use default (adaptive) number of decimal places
            return applyDefaultFormatting(val);
        } else if (decimalPlaces == Integer.MAX_VALUE) {
            // For max value, use the default string representation
            return Double.toString(val);
        } else {
            // Get a formatter with a fixed number of decimal places
            var locale = Locale.getDefault(Locale.Category.FORMAT);
            var cache = formatters.computeIfAbsent(locale, k -> new ConcurrentHashMap<>());
            var format = cache.computeIfAbsent(decimalPlaces, n -> createFormat(locale, decimalPlaces));
            synchronized (format) {
                return format.format(val);
            }
        }
    }

    private static String applyDefaultFormatting(double val) {
        int dp;
        var absVal = Math.abs(val);
        if (absVal > 1000)
            dp = 1;
        else if (absVal > 10)
            dp = 2;
        else if (absVal > 1)
            dp = 3;
        else
            dp = 4;
        return GeneralTools.formatNumber(val, dp);
    }

    private static NumberFormat createFormat(Locale locale, int nDecimalPlaces) {
        var format = NumberFormat.getInstance(locale);
        format.setGroupingUsed(false); // Avoid adding extra commas or points!
        if (nDecimalPlaces < 0) {
            format.setMaximumFractionDigits(-nDecimalPlaces);
        } else {
            format.setMinimumFractionDigits(nDecimalPlaces);
            format.setMaximumFractionDigits(nDecimalPlaces);
        }
        return format;
    }




}
