package qupath.opencv.ml.pixel.train;

import qupath.lib.progress.ProgressMonitor;
import qupath.opencv.ml.models.FeatureImportance;

import java.util.List;

@FunctionalInterface
public interface FeatureImportanceCalculator {

    List<FeatureImportance> calculateFeatureImportance(List<ClassifierTrainingData> trainingData,
                                                               List<String> featureNames,
                                                               ProgressMonitor progressMonitor);

    /**
     * Calculate feature importance, without progress monitoring.
     * @param trainingData training data that may be used to determine feature importance
     * @param featureNames feature names
     * @return a list of feature importance, if an empty list if the importance could not be calculated
     */
    default List<FeatureImportance> calculateFeatureImportance(List<ClassifierTrainingData> trainingData,
                                                       List<String> featureNames) {
        return calculateFeatureImportance(trainingData, featureNames, ProgressMonitor.NULL);
    }

}
