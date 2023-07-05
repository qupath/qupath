package qupath.fx.prefs.converters;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TestLocaleConverter {

    @Test
    void testLocaleConversion() {
        testToStringFromString(Locale.US);

        // Test all available locales with different defaults set
        Locale.setDefault(Locale.US);
        for (var locale : Locale.getAvailableLocales())
            testToStringFromString(locale);

        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        for (var locale : Locale.getAvailableLocales())
            testToStringFromString(locale);

        Locale.setDefault(Locale.JAPANESE);
        for (var locale : Locale.getAvailableLocales())
            testToStringFromString(locale);

        Locale.setDefault(Locale.CANADA);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.ITALIAN);
        Locale.setDefault(Locale.Category.FORMAT, Locale.CHINA);
        for (var locale : Locale.getAvailableLocales())
            testToStringFromString(locale);

    }

    @Test
    void testNullLocaleConversion() {
        testToStringFromString(null);
    }

    @Test
    void testInvalidLocaleConversion() {
        var converter = new LocaleConverter();
        var locale = new Locale("abc");
        String string = converter.toString(locale);
        assertEquals(string, "abc");
        Locale localeConverted = converter.fromString(string);
        assertNull(localeConverted);
    }

    private static void testToStringFromString(Locale locale) {
        var converter = new LocaleConverter();
        String string = converter.toString(locale);
        Locale localeConverted = converter.fromString(string);
        assertEquals(locale, localeConverted);
    }

}