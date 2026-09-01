package qupath.opencv.ml;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.ROIs;
import qupath.lib.scripting.QP;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ml.models.statmodel.OpenCVStatModels;
import qupath.opencv.ml.objects.OpenCVMLClassifier;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Tests for serializing and deserializing pixel and object classifiers.
 * This includes checking that classifiers from v0.7.0 can be deserialized.
 * The script run in v0.7.0 to generate the test data is at
 * https://gist.github.com/petebankhead/797fe63ae7ec5f4866231f4176c3c5a7
 */
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


    static List<Arguments> provideModelsToTest() throws Exception {
        return Stream.of(
                OpenCVStatModels.Models.R_TREES,
                OpenCVStatModels.Models.ANN,
                OpenCVStatModels.Models.KNN,
                OpenCVStatModels.Models.LOGISTIC_REGRESSION
                )
                .map(Arguments::of)
                .toList();
    }

    @ParameterizedTest
    @MethodSource("provideModelsToTest")
    public void test_objectClassifierSerializeDeserialize(OpenCVStatModels.Models modelType) {
        try (var scope = new PointerScope()) {
            int nSamples = 100;
            Random rng = new Random(53424);
            var model = modelType.createTrainableModel();
            var data = new TrainingDataset();
            data.trainModel(model, nSamples, rng);
            var features = data.features;

            // Wrap into an object classifier
            var objectClassifier = OpenCVMLClassifier.create(model,
                    PathObjectFilter.DETECTIONS,
                    FeatureExtractors.createMeasurementListFeatureExtractor(features),
                    data.classes
            );

            // Create detections and predict
            var detections = createTestDetections(100, features, data.classes.size(), new Random(100));
            detections.forEach(PathObject::resetPathClass);
            objectClassifier.classifyObjects(null, detections, true);
            var predictions = detections.stream().map(PathObject::getClassification).toList();

            // Check we can deserialize a classifier
            var gson = GsonTools.getInstance();
            var json = gson.toJson(objectClassifier, ObjectClassifier.class);
            var objectClassifierCopy = gson.fromJson(json, ObjectClassifier.class);
            assertNotNull(objectClassifierCopy);

            // Check we get the same predictions as before
            detections.forEach(PathObject::resetPathClass);
            objectClassifierCopy.classifyObjects(null, detections, true);
            var predictionsCopy = detections.stream().map(PathObject::getClassification).toList();

            assertEquals(predictions, predictionsCopy);

            // Confirm we didn't accidentally compare the same things
            assertNotSame(objectClassifier, objectClassifierCopy);
            assertNotSame(predictions, predictionsCopy);
        }
    }


    @ParameterizedTest
    @MethodSource("provideModelsToTest")
    public void test_pixelClassifierSerializeDeserialize(OpenCVStatModels.Models modelType) {
        try (var scope = new PointerScope()) {
            int nSamples = 100;
            Random rng = new Random(53424);
            var model = modelType.createTrainableModel();
            var data = new TrainingDataset();
            data.trainModel(model, nSamples, rng);

            // Wrap in a pixel classifier
            var classes = data.classes;
            int nClasses = classes.size();
            var pixelClassifier = PixelClassifiers.createClassifier(
                    model,
                    ImageOps.buildImageDataOp(),
                    new PixelClassifierMetadata.Builder()
                            .classificationLabels(
                                    IntStream.range(0, nClasses)
                                            .mapToObj(Integer::valueOf)
                                            .collect(Collectors.toMap(i -> i, classes::get
                                                    )
                                            )
                            ).build(),
                    false);

            // Check we can deserialize a classifier
            var gson = GsonTools.getInstance();
            var json = gson.toJson(pixelClassifier, PixelClassifier.class);
            var pixelClassifierCopy = gson.fromJson(json, PixelClassifier.class);
            assertNotNull(pixelClassifierCopy);
            assertNotSame(pixelClassifier, pixelClassifierCopy);

        }

    }


    private static class TrainingDataset {

        public final List<String> features = List.of("Feature 1", "Feature 2", "Feature 3");
        public final List<PathClass> classes = Stream.of("First class", "Second class")
                .map(PathClass::getInstance)
                .toList();


        void trainModel(TrainableModel model, int nSamples, Random rng) {
            int nFeatures = features.size();
            int nClasses = classes.size();

            // Can't serialize a model until it has been trained, so create training samples and targets
            try (var scope = new PointerScope()) {
                var samples = new Mat(nSamples, nFeatures, opencv_core.CV_32F);
                var targets = new Mat(nSamples, 1, opencv_core.CV_32S);
                for (int i = 0; i < nSamples; i++) {
                    int target = rng.nextInt(nClasses);
                    opencv_core.randn(samples.row(i), new Mat(1.0 + target * 2), new Mat(0.5));
                    try (IntIndexer idx = targets.createIndexer()) {
                        idx.put(i, target);
                    }
                }
                var trainData = model.createTrainData(samples, targets, nClasses, null, false);
                model.train(trainData);
            }

        }

    }

    /**
     * Create a list of detections, which can be used for testing an object classifier.
     * @param nDetections number of detections to create
     * @param features feature names
     * @param nClasses number of target classes
     * @param rng generator or measurement values
     * @return a list of detections
     */
    private static List<PathObject> createTestDetections(int nDetections, List<String> features, int nClasses, Random rng) {
        var detections = new ArrayList<PathObject>();
        for (int i = 0; i < nDetections; i++) {
            int target = rng.nextInt(nClasses);
            detections.add(
                    createDetection(features, 1.0 + target*2, 0.4, rng)
            );
        }
        return detections;
    }


    /**
     * Create a detection object with measurement values with random values from a normal distribution.
     * @param features measurement names
     * @param mean mean of all measurements
     * @param std std deviation of all measurements
     * @param rng generator for measurement values
     * @return a new detection object
     */
    private static PathObject createDetection(List<String> features, double mean, double std, Random rng) {
        var pathObject = PathObjects.createDetectionObject(ROIs.createEmptyROI());
        try (var ml = pathObject.getMeasurementList()) {
            for (String name : features) {
                ml.put(name, rng.nextGaussian(mean, std));
            }
        }
        return pathObject;
    }


}
