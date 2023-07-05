package qupath.fx.prefs.converters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEnumConverter {

    private enum TestEnum {
        A, B, C, Longer, Short
    }

    @Test
    void testEnumConversion() {
        var converter = new EnumConverter<>(TestEnum.class);
        for (var val : TestEnum.values())
            testToStringFromString(converter, val);

        testToStringFromString(converter, null);
    }

    private static <T extends Enum> void testToStringFromString(EnumConverter<T> converter, T value) {
        String string = converter.toString(value);
        T converted = converter.fromString(string);
        assertEquals(value, converted);
    }

}