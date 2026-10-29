package qupath.opencv.ml.pixel.train;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_ml.TrainData;

import java.util.List;

/**
 * Utility methods for training a pixel classifier.
 * @since v0.8.0
 */
public class TrainingUtils {

    /**
     * Convert features to an OpenCV {@link TrainData}.
     * @param features a list of feature vectors (samples)
     * @param targets target labels; the size of the vector should equal the number of samples
     * @return a train data
     */
    public static TrainData createTrainData(List<FeatureVector> features, TargetVector targets) {
        return TrainData.create(
                featuresToMat(features),
                opencv_ml.ROW_SAMPLE,
                targetsToMat(targets)
        );
    }

    /**
     * Convert a list of feature vectors (sampels) to a Mat.
     * @param features the samples
     * @return the Mat
     */
    public static Mat featuresToMat(List<FeatureVector> features) {
        if (features.isEmpty())
            return new Mat(0);
        int nSamples = features.size();
        var nFeatures = features.getFirst().size();
        var mat = new Mat(nSamples, nFeatures, opencv_core.CV_32FC1, Scalar.ZERO);
        try (FloatIndexer idx = mat.createIndexer()) {
            for (int i = 0; i < nSamples; i++) {
                var vector = features.get(i);
                for (int j = 0; j < nFeatures; j++) {
                    idx.put(i, j, vector.get(j));
                }
            }
        }
        return mat;
    }

    /**
     * Convert a target vector of labels to an integer Mat.
     * @param targets the target labels
     * @return the Mat
     */
    public static Mat targetsToMat(TargetVector targets) {
        var mat = new Mat(targets.size(), 1, opencv_core.CV_32SC1, Scalar.ZERO);
        try (IntIndexer indexer = mat.createIndexer()) {
            for (int i = 0; i < targets.size(); i++) {
                indexer.put(i, targets.get(i));
            }
        }
        return mat;
    }

}
