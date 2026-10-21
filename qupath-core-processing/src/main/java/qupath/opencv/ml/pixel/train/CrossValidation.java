package qupath.opencv.ml.pixel.train;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.ConfusionMatrix;
import qupath.opencv.ml.FeaturePreprocessor;
import qupath.opencv.ml.models.PredictionModel;
import qupath.opencv.ml.models.TrainableModel;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CrossValidation {

    private static final Logger logger = LoggerFactory.getLogger(CrossValidation.class);

	public static List<ConfusionMatrix<PathClass>> computeCrossValidation(
									TrainableModel model,
									ModelTrainer trainer,
									List<ClassifierTrainingData> trainingData,
									Random rng) {
		var modelCV = duplicateModel(model);
		List<ConfusionMatrix<PathClass>> matrices = new ArrayList<>();
		var firstData = trainingData.getFirst();
		var labels = firstData.getLabelMap(); // Labels should be identical, via PixelClassifierTraining
		String splitType = trainingData.size() + " images";
		if (trainingData.size() == 1) {
			logger.warn("Splitting the training data");
			int nSplits = Math.min(5, firstData.size() / 10);
			if (nSplits <= 1) {
				logger.error("No enough data to compute cross validation (size={})", firstData.size());
				return List.of();
			}
			trainingData = firstData.split(nSplits, rng);
			splitType = "single image";
		}

		try (var scope = new PointerScope()) {
			for (int i = 0; i < trainingData.size(); i++) {
				var holdOutData = trainingData.get(i);
				try (ClassifierTrainingData otherImages = ClassifierTrainingData.merge(
						trainingData.stream().filter(d -> d != holdOutData).toList()
				)) {
					var trainedModel = trainer.train(modelCV, otherImages);
					try (var holdOutTest = holdOutData.getTrainData()) {
						var confusion = evaluate(
								holdOutData.getName(),
								holdOutTest.getTrainSamples(),
								holdOutTest.getTrainNormCatResponses(),
								holdOutTest.getClassLabels(),
								trainedModel.model(),
								trainedModel.featurePreprocessor(),
								labels);
						matrices.add(confusion);
						logger.debug("Fold {}: Accuracy = {}, F1 = {}", i + 1, confusion.getAccuracy(), confusion.getF1());
					}
				}
			}
			if (matrices.size() > 1)
				matrices.addFirst(ConfusionMatrix.sum("All splits (" + splitType + ")", matrices));
			return matrices;
		}
	}


    /**
	 * Evaluate the predictions of a classifier.
	 * @param name name for confusion matrix
	 * @param samples the samples to use as input to the model
	 * @param normCatTargets the normalized categorical targets
	 * @param classLabels the class labels associated with the targets; see {@link org.bytedeco.opencv.opencv_ml.TrainData#getTrainNormCatResponses()}
	 * @param model the model to use for prediction
	 * @param preprocessor the preprocessor to apply to the samples
	 * @param labels the QuPath-friendly mapping of classes to integer labels
	 * @return
	 */
	private static ConfusionMatrix<PathClass> evaluate(String name, Mat samples, Mat normCatTargets, Mat classLabels, PredictionModel model,
                                                       FeaturePreprocessor preprocessor, Map<PathClass, Integer> labels) {
		if (preprocessor != null) {
			samples = samples.clone();
			preprocessor.apply(samples, false);
		}
		var confusion = new ConfusionMatrix<>(name, List.copyOf(labels.keySet()));
		IntBuffer bufferGroundTruth = normCatTargets.createBuffer();
		IntBuffer bufferClassList = classLabels.createBuffer();

		// This assumes that our labels are dense and start with 0... rather than being
		// all other the place, and potentially negative
		int n = labels.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
		PathClass[] pathClasses = new PathClass[n];
		for (var entry : labels.entrySet()) {
			pathClasses[entry.getValue()] = entry.getKey();
		}

		var testResults = new Mat();
		model.predict(samples, testResults, new Mat());
		IntBuffer bufferPrediction = testResults.createBuffer();
		int nTest = testResults.rows();
		for (int i = 0; i < nTest; i++) {
			int targetClassInd = bufferClassList.get(bufferGroundTruth.get(i));
			confusion.accumulate(
					pathClasses[targetClassInd],
					pathClasses[bufferPrediction.get(i)]
			);
		}
		return confusion;
	}


    private static TrainableModel duplicateModel(TrainableModel model) {
		var gson = GsonTools.getInstance();
		return gson.fromJson(
				gson.toJson(model, TrainableModel.class), TrainableModel.class
		);
	}

}
