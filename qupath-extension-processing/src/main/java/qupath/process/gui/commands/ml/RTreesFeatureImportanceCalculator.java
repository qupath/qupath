package qupath.process.gui.commands.ml;

import org.bytedeco.javacpp.PointerScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.progress.ProgressMonitor;
import qupath.opencv.ml.models.FeatureImportance;
import qupath.opencv.ml.models.statmodel.OpenCVStatModels;
import qupath.opencv.ml.pixel.train.ClassifierTrainingData;
import qupath.opencv.ml.pixel.train.FeatureImportanceCalculator;
import qupath.opencv.ml.pixel.train.PixelClassifierModelTrainer;

import java.util.List;

public class RTreesFeatureImportanceCalculator implements FeatureImportanceCalculator {

    private static final Logger logger = LoggerFactory.getLogger(RTreesFeatureImportanceCalculator.class);

	private final PixelClassifierModelTrainer trainer;

	RTreesFeatureImportanceCalculator(PixelClassifierModelTrainer trainer) {
		this.trainer = trainer;
	}

    @Override
    public List<FeatureImportance> calculateFeatureImportance(List<ClassifierTrainingData> trainingData,
															  List<String> featureNames,
															  ProgressMonitor progressMonitor) {
		if (trainingData.isEmpty()) {
			logger.warn("Can't compute variable importance without training data");
			return List.of();
		}
		var mergedTraining = ClassifierTrainingData.merge(trainingData);
		try (var scope = new PointerScope()) {
			progressMonitor.updateProgress("Training RTrees model", 0);
			var model = OpenCVStatModels.Models.R_TREES.createTrainableModel();
			trainer.train(model, mergedTraining);
			return model.getFeatureImportance(featureNames);
		} finally {
			progressMonitor.updateProgress("Done!", 1.0);
		}
	}

}
