package qupath.opencv.ml;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.bytedeco.javacpp.PointerScope;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.scripting.QP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestClassifierSerialization {

    static {
        // Workaround to ensure GsonBuilder is sufficiently initialized
        new QP();
    }

    static List<Arguments> provideObjectClassifierPaths() throws Exception {
        var path = Paths.get(TestClassifierSerialization.class.getResource("/data/classifiers").toURI());
        return Files.walk(path)
                .filter(p -> p.getFileName().toString().endsWith("obj-classifier.json"))
                .map(Arguments::of)
                .toList();
    }

    static List<Arguments> providePixelClassifierPaths() throws Exception {
        var path = Paths.get(TestClassifierSerialization.class.getResource("/data/classifiers").toURI());
        return Files.walk(path)
                .filter(p -> p.getFileName().toString().endsWith("pixel-classifier.json"))
                .map(Arguments::of)
                .toList();
    }

    private static List<PathObject> readDetections() throws Exception {
        try (var stream = Files.newInputStream(
                Paths.get(
                        TestClassifierSerialization.class.getResource("/data/classifiers/objects.json").toURI()
                )
        )) {
            return PathIO.readObjectsFromGeoJSON(stream);
        }
    }

    private static List<String> readTargetClassifications(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return GsonTools.getInstance().fromJson(reader, new TypeToken<List<String>>() {});
        }
    }

    @ParameterizedTest
    @MethodSource("provideObjectClassifierPaths")
    public void test_objectClassifierDeserialize(Path path) throws Exception {
        try (var scope = new PointerScope()) {
            try (var reader = Files.newBufferedReader(path)) {
                // Ensure classifier can be deserialized
                var classifier = GsonTools.getInstance().fromJson(reader, ObjectClassifier.class);
                assertNotNull(classifier);

                // Ensure classifier actually works
                var detections = readDetections();
                assertFalse(detections.isEmpty());
                classifier.classifyObjects(null, detections, true);
                var actualClasses = detections.stream().map(PathObject::getClassification).toList();

                var targetClasses = readTargetClassifications(
                        path.getParent().resolve(
                                path.getFileName().toString().replace(".json", "-targets.json")));
                assertEquals(targetClasses, actualClasses);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("providePixelClassifierPaths")
    public void test_pixelClassifierDeserialize(Path path) throws Exception {
        try (var scope = new PointerScope()) {
            try (var reader = Files.newBufferedReader(path)) {
                // Ensure classifier can be deserialized
                var classifier = GsonTools.getInstance().fromJson(reader, PixelClassifier.class);
                assertNotNull(classifier);
            }
        }
    }

}
