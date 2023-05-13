package qupath.fx.prefs.converters;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestUriConverter {

    private static List<URI> provideUris() {
        return Arrays.asList(
                Paths.get(".").toUri(),
                Paths.get(".").toAbsolutePath().toUri(),
                Paths.get(".").resolve("anything.txt").toUri(),
                Paths.get("/some/other/file").toUri(),
                URI.create("https://www.github.com"),
                null);
    }

    @ParameterizedTest
    @MethodSource("provideUris")
    void testToStringFromString(URI value) {
        var converter = new UriConverter();
        String string = converter.toString(value);
        URI converted = converter.fromString(string);
        assertEquals(value, converted);
    }

}