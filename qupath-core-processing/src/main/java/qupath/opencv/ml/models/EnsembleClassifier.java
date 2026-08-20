package qupath.opencv.ml.models;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.opencv.tools.OpenCVTools;

public class EnsembleClassifier extends RTreesClassifier {

    private final List<OpenCVStatModel> models;

    EnsembleClassifier(List<OpenCVStatModel> models) {
        this.models = List.copyOf(models);
    }

    EnsembleClassifier(List<OpenCVStatModel> models, RTrees model) {
        super(model);
        this.models = List.copyOf(models);
    }

    List<OpenCVStatModel> getModels() {
        return models;
    }

    @Override
    public void trainWithLock(TrainData trainData) {
        int nClasses = (int) OpenCVTools.maximum(trainData.getClassLabels()) + 1;
        for (var initialModel : models) {
            try (var tempTrain = initialModel.createTrainData(
                    trainData.getTrainSamples(),
                    trainData.getTrainNormCatResponses(),
                    nClasses,
                    trainData.getTrainSampleWeights(),
                    false)) {
                initialModel.train(tempTrain);
            }
        }

        var trainSamples = trainData.getTrainSamples();
        // TODO: Consider whether it's better to include the samples or not (and, if a parameter, how to serialize it to JSON)
        updateInputData(trainSamples, true);
        var trainData2 = TrainData.create(trainSamples, trainData.getLayout(), trainData.getResponses(), null, null, trainData.getSampleWeights(), null);
        super.trainWithLock(trainData2);
    }

    @Override
    public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
        updateInputData(samples, true);
        super.predictWithLock(samples, results, probabilities);
    }


    private void updateInputData(Mat samples, boolean includeSamples) {
        try (var scope = new PointerScope()) {
            MatVector toMerge = new MatVector();
            if (includeSamples)
                toMerge.push_back(samples);
            for (var temp : models) {
                var matResults = new Mat();
                var matProb = temp.supportsProbabilities() ? new Mat() : null;
                temp.predict(samples, matResults, matProb);
                var toAdd = Objects.requireNonNullElse(matProb, matResults);
                toAdd.convertTo(toAdd, samples.type());
                toMerge.push_back(toAdd);
            }
            opencv_core.hconcat(toMerge, samples);
        }
    }

    @Override
    public String toString() {
        return "Ensemble " +
                models.stream().map(OpenCVStatModel::getName).collect(Collectors.joining("+"))
                + " + DTrees";
    }

}
