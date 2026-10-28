package qupath.opencv.ml.pixel.train;

import qupath.lib.progress.ProgressMonitor;
import qupath.opencv.ml.models.FeatureImportance;

import java.util.List;

/**
 * Interface for methods to calculate the importance of specific features when training a classifier.
 */
public interface FeatureImportanceCalculator {

    /**
     * Calculate feature importance, with progress monitoring.
     * @param trainingData training data that may be used to determine feature importance
     * @param featureNames feature names
     * @param progressMonitor the progress monitor to use for lengthy calculations
     * @return a list of feature importance, if an empty list if the importance could not be calculated
     */
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

    /**
     * Name to display for the user to identify this calculator.
     * @return
     */
    default String getName() {
        return getClass().getSimpleName();
    }

}
