package qupath.fx.prefs.converters;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPathConverter {

    private static List<Path> providePaths() {
        return Arrays.asList(
                Paths.get("."),
                Paths.get(".").toAbsolutePath(),
                Paths.get(".").resolve("anything.txt"),
                Paths.get("/some/other/file"),
                null);
    }

    @ParameterizedTest
    @MethodSource("providePaths")
    void testToStringFromString(Path value) {
        var converter = new PathConverter();
        String string = converter.toString(value);
        Path converted = converter.fromString(string);
        assertEquals(value, converted);
    }

}