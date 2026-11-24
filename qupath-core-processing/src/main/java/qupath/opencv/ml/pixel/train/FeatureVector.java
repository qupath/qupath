package qupath.opencv.ml.pixel.train;

import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * Lightweight wrapper around a float array, which does not permit its contents to be modified.
 * @since v0.8.0
 */
public class FeatureVector {

    private final float[] features;

    private FeatureVector(float[] features) {
        this.features = features;
    }

    /**
     * Create a feature vector that wraps a duplicate of the provided float array.
     * <p>
     * Note that this makes a defensive copy of the provided array.
     * Therefore, it is possible to reuse the same array used as input when building a list of feature vectors.
     * @param features the features
     * @return a new feature vector
     */
    public static FeatureVector create(float[] features) {
        Objects.requireNonNull(features);
        return new FeatureVector(features.clone());
    }

    /**
     * Get the feature at the specified index.
     * @param index the index
     * @return the feature
     */
    public float get(int index) {
        return features[index];
    }

    /**
     * The number of features in the feature vector.
     * @return the feature vector size
     */
    public int size() {
        return features.length;
    }

    /**
     * Get a read-only float buffer containing the contents of this feature vector.
     * @return the float buffer
     */
    public FloatBuffer asFloatBuffer() {
        return FloatBuffer.wrap(features).asReadOnlyBuffer();
    }

}
