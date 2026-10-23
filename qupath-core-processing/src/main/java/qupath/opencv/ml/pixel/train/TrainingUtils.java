package qupath.opencv.ml.pixel.train;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_ml.TrainData;

import java.util.List;

public class TrainingUtils {

    public static TrainData createTrainData(List<FeatureVector> features, TargetVector targets) {
        return TrainData.create(
                featuresToMat(features),
                opencv_ml.ROW_SAMPLE,
                targetsToMat(targets)
        );
    }

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
