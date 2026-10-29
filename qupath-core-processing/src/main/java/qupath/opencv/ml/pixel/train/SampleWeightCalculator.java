package qupath.opencv.ml.pixel.train;

import java.util.List;

/**
 * Calculate sample weights for classifier training.
 * <p>
 * This is typically used to weight specific target labels, or address label imbalance.
 * @since v0.8.0
 */
@FunctionalInterface
public interface SampleWeightCalculator {

    /**
     * Calculate weights for each sample.
     * @param samples list of samples
     * @param targets target labels
     * @return an array of weights with length {@code features.size()}, or {@code null} if no weights are calculated.
     */
    float[] calculateWeights(List<FeatureVector> samples, TargetVector targets);

}
