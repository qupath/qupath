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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Trainer for an {@link TrainableModel} to use with a {@link PixelClassifier}.
 * <p>
 * This effectively snapshots the required settings in its constructor, so that the trainer could be reused
 * for multiple models.
 * <p>
 * Its purpose is to simplify the process of training multiple models using identical settings but different
 * data, for example for cross-validation.
 */
public class PixelClassifierModelTrainer {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierModelTrainer.class);

    private final PixelCalibration resolution;
    private final int maxSamples;
    private final FeatureNormalization featureNormalization;
    private final ImageDataOp baseFeatureCalculator;
    private final SampleWeightCalculator sampleWeightCalculator;
    private final int rngSeed;


    private PixelClassifierModelTrainer(PixelCalibration resolution,
                                       ImageDataOp baseFeatureCalculator,
                                        int maxSamples,
                                       FeatureNormalization featureNormalization,
                                       SampleWeightCalculator sampleWeightCalculator,
                                       int rngSeed) {
        this.resolution = resolution;
        this.maxSamples = maxSamples;
        this.baseFeatureCalculator = baseFeatureCalculator;
        // FeatureNormalization is inconveniently mutable, so make a defensive copy
        this.featureNormalization = featureNormalization == null ? null : FeatureNormalization.create(featureNormalization);
        this.sampleWeightCalculator = sampleWeightCalculator;
        this.rngSeed = rngSeed;
    }

    public TrainedPixelClassifierModel train(TrainableModel model, ClassifierTrainingData trainingData) {

        opencv_core.setRNGSeed(rngSeed);

        var labels = trainingData.getLabelMap();
        var samples = trainingData.getSamples();
        var targets = trainingData.getTargets();
        var weights = createWeights(samples, targets);

        try (var trainData = TrainingUtils.createTrainData(trainingData.getSamples(), trainingData.getTargets())) {
            if (maxSamples > 0 && trainData.getNTrainSamples() > maxSamples)
                trainData.setTrainTestSplit(maxSamples, true);
            else
                trainData.shuffleTrainTest();

            // Apply feature preprocessing, if we need to
            var preprocessor = featureNormalization == null ? null : featureNormalization.build(trainData.getTrainSamples(), false);
            ImageDataOp featureCalculator;
            boolean usePreprocessor = preprocessor != null && preprocessor.doesSomething();
            if (usePreprocessor) {
                var preprocessingOp = ImageOps.ML.preprocessor(preprocessor);
                featureCalculator = baseFeatureCalculator.appendOps(preprocessingOp);
            } else {
                featureCalculator = baseFeatureCalculator;
            }

            // Create TrainData in an appropriate format (e.g. labels or one-hot encoding)
            var trainSamples = trainData.getTrainSamples();
            var trainTargets = trainData.getTrainResponses();
            if (usePreprocessor)
                preprocessor.apply(trainSamples, false);

            // Using getTrainNormCatResponses() causes confusion if classes are not represented
            var countsIndexedByLabels = createCountsIndexedByLabels(trainData.getTrainResponses(), labels);

            Duration trainingTime;
            int nLabels = labels.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            try (var modelTrainData = model.createTrainData(trainSamples, trainTargets, nLabels, weights, false)) {
                //		 logger.info("Training data: {} x {}, Target data: {} x {}", trainSamples.rows(), trainSamples.cols(), trainResponses.rows(), trainResponses.cols());
                long startTime = System.nanoTime();
                model.train(modelTrainData);
                long endTime = System.nanoTime();
                trainingTime = Duration.ofNanos(endTime - startTime);

                return new TrainedPixelClassifierModel(
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

    private Mat createWeights(List<FeatureVector> features, TargetVector targets) {
        if (sampleWeightCalculator == null)
            return null;
        float[] weights = sampleWeightCalculator.calculateWeights(features, targets);
        return new Mat(weights);
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

    /**
     * Create a new builder for this model trainer.
     * @param baseFeatureCalculator main feature calculator, applied before any feature preprocessing
     * @return a builder
     */
    public static Builder builder(ImageDataOp baseFeatureCalculator) {
        return new Builder(baseFeatureCalculator);
    }


    public static class Builder {

        private PixelCalibration resolution;
        private int maxSamples = 100_000;
        private FeatureNormalization featureNormalization;
        private ImageDataOp baseFeatureCalculator;
        private SampleWeightCalculator sampleWeightCalculator;
        private int rngSeed;

        private Builder(ImageDataOp baseFeatureCalculator) {
            Objects.requireNonNull(baseFeatureCalculator, "Base feature calculator cannot be null.");
            this.baseFeatureCalculator = baseFeatureCalculator;
        }

        public Builder resolution(PixelCalibration resolution) {
            this.resolution = resolution;
            return this;
        }

        public Builder maxSamples(int maxSamples) {
            this.maxSamples = maxSamples;
            return this;
        }

        public Builder featureNormalization(FeatureNormalization featureNormalization) {
            this.featureNormalization = featureNormalization;
            return this;
        }

        public Builder weightCalculator(SampleWeightCalculator sampleWeightCalculator) {
            this.sampleWeightCalculator = sampleWeightCalculator;
            return this;
        }

        public Builder rngSeed(int rngSeed) {
            this.rngSeed = rngSeed;
            return this;
        }

        public PixelClassifierModelTrainer build() {
            return new PixelClassifierModelTrainer(
                    resolution,
                    baseFeatureCalculator,
                    maxSamples,
                    featureNormalization,
                    sampleWeightCalculator,
                    rngSeed
            );
        }

    }

}
