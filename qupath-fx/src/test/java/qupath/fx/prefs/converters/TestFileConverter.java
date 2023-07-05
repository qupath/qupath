package qupath.fx.prefs.converters;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFileConverter {

    private static List<File> provideFiles() {
        return Arrays.asList(
                new File("."),
                new File(".").getAbsoluteFile(),
                new File("/some/other/file"),
                null);
    }

    @ParameterizedTest
    @MethodSource("provideFiles")
    void testToStringFromString(File value) {
        var converter = new FileConverter();
        String string = converter.toString(value);
        File converted = converter.fromString(string);
        assertEquals(value, converted);
    }

}