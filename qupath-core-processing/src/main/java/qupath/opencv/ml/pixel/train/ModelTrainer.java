package qupath.opencv.ml.pixel.train;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOps;

import java.nio.IntBuffer;
import java.time.Duration;
import java.util.Map;

/**
 * Trainer for an {@link TrainableModel} to use with a {@link PixelClassifier}.
 * <p>
 * This effectively snapshots the required settings in its constructor, so that the trainer could be reused
 * for multiple models.
 * <p>
 * Its purpose is to simplify the process of training multiple models using identical settings but different
 * data, for example for cross-validation.
 */
public class ModelTrainer {

    private static final Logger logger = LoggerFactory.getLogger(ModelTrainer.class);

    private final PixelCalibration resolution;
    private final int actualMaxSamples;
    private final FeatureNormalization featureNormalization;
    private final ImageDataOp baseFeatureCalculator;
    private final boolean reweightSamples;
    private final int rngSeed;

    public ModelTrainer(PixelCalibration resolution,
                        ImageDataOp baseFeatureCalculator) {
        this(resolution, 100_000, new FeatureNormalization(),
                baseFeatureCalculator, false, 123);
    }

    // TODO: Note that FeatureNormalization is mutable; ideally, this should only use immutable objects
    public ModelTrainer(PixelCalibration resolution,
                 int actualMaxSamples,
                 FeatureNormalization featureNormalization,
                 ImageDataOp baseFeatureCalculator,
                 boolean reweightSamples,
                 int rngSeed) {
        this.resolution = resolution;
        this.actualMaxSamples = actualMaxSamples;
        this.baseFeatureCalculator = baseFeatureCalculator;
        this.featureNormalization = featureNormalization;
        this.reweightSamples = reweightSamples;
        this.rngSeed = rngSeed;
    }

    public TrainedModel train(TrainableModel model, ClassifierTrainingData trainingData) {

        opencv_core.setRNGSeed(rngSeed);

        var labels = trainingData.getLabelMap();

        try (var trainData = trainingData.getTrainData()) {
            if (actualMaxSamples > 0 && trainData.getNTrainSamples() > actualMaxSamples)
                trainData.setTrainTestSplit(actualMaxSamples, true);
            else
                trainData.shuffleTrainTest();

            // Apply feature preprocessing, if we need to
            var preprocessor = featureNormalization.build(trainData.getTrainSamples(), false);
            ImageDataOp featureCalculator;
            if (preprocessor.doesSomething()) {
                var preprocessingOp = ImageOps.ML.preprocessor(preprocessor);
                featureCalculator = baseFeatureCalculator.appendOps(preprocessingOp);
            } else {
                featureCalculator = baseFeatureCalculator;
            }

            // Using getTrainNormCatResponses() causes confusion if classes are not represented
            var targets = trainData.getTrainResponses();
            var countsIndexedByLabels = createCountsIndexedByLabels(targets, labels);
            Mat weights = reweightSamples ? createWeights(targets, countsIndexedByLabels) : null;

            // Create TrainData in an appropriate format (e.g. labels or one-hot encoding)
            var trainSamples = trainData.getTrainSamples();
            preprocessor.apply(trainSamples, false);

            Duration trainingTime;
            int nLabels = labels.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            try (var modelTrainData = model.createTrainData(trainSamples, targets, nLabels, weights, false)) {
                //		 logger.info("Training data: {} x {}, Target data: {} x {}", trainSamples.rows(), trainSamples.cols(), trainResponses.rows(), trainResponses.cols());
                long startTime = System.nanoTime();
                model.train(modelTrainData);
                long endTime = System.nanoTime();
                trainingTime = Duration.ofNanos(endTime - startTime);

                return new TrainedModel(
                        model,
                        featureCalculator,
                        preprocessor,
                        labels,
                        countsIndexedByLabels,
                        resolution,
                        trainingTime
                );
            }
        }
    }

    private static int[] createCountsIndexedByLabels(Mat targets, Map<PathClass, Integer> labels) {
        int n = (int) targets.total();
        IntBuffer buffer = targets.createBuffer();
        var countsIndexedByLabels = new int[labels.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1];
        for (int i = 0; i < n; i++) {
            countsIndexedByLabels[buffer.get(i)] += 1;
        }
        return countsIndexedByLabels;
    }

    private static Mat createWeights(Mat targets, int[] countsIndexedByLabels) {
        int n = (int) targets.total();
        IntBuffer buffer = targets.createBuffer();
        Mat weights = new Mat(n, 1, opencv_core.CV_32FC1);
        try (FloatIndexer bufferWeights = weights.createIndexer()) {
            float[] weightArray = new float[countsIndexedByLabels.length];
            for (int i = 0; i < weightArray.length; i++) {
                int c = countsIndexedByLabels[i];
                weightArray[i] = c == 0 ? 1 : (float) n / c;
            }
            for (int i = 0; i < n; i++) {
                int label = buffer.get(i);
                bufferWeights.put(i, weightArray[label]);
            }
        }
        return weights;
    }

}
