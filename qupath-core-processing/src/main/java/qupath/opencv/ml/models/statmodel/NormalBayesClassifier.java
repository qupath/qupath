package qupath.opencv.ml.models.statmodel;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link org.bytedeco.opencv.opencv_ml.NormalBayesClassifier}.
 */
class NormalBayesClassifier extends AbstractOpenCVClassifier<org.bytedeco.opencv.opencv_ml.NormalBayesClassifier> {

    NormalBayesClassifier() {
        super();
    }

    NormalBayesClassifier(final org.bytedeco.opencv.opencv_ml.NormalBayesClassifier model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(org.bytedeco.opencv.opencv_ml.NormalBayesClassifier model) {
        var params = new ParameterList();
        params.addTitleParameter("No parameters to adjust!");
        return params;
    }

    @Override
    org.bytedeco.opencv.opencv_ml.NormalBayesClassifier createStatModel() {
        return org.bytedeco.opencv.opencv_ml.NormalBayesClassifier.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return org.bytedeco.opencv.opencv_ml.NormalBayesClassifier.class;
    }

    @Override
    void updateModel(org.bytedeco.opencv.opencv_ml.NormalBayesClassifier model, ParameterList params, TrainData trainData) {
    }

    @Override
    public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
        var model = getStatModel();
        if (probabilities == null)
            probabilities = new Mat();
        model.predictProb(samples, results, probabilities, 0);
    }
}
