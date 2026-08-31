package qupath.opencv.ml.models.statmodel;

import java.util.Arrays;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link LogisticRegression}.
 */
class LogisticRegressionClassifier extends AbstractOpenCVClassifier<LogisticRegression> {

    enum Regularization {
        DISABLE, L1, L2;

        public int getRegularization() {
            return switch (this) {
                case L1 -> LogisticRegression.REG_L1;
                case L2 -> LogisticRegression.REG_L2;
                default -> LogisticRegression.REG_DISABLE;
            };
        }

        @Override
        public String toString() {
            return switch (this) {
                case L1 -> "L1";
                case L2 -> "L2";
                default -> "None";
            };
        }
    }

    LogisticRegressionClassifier() {
        super();
    }

    LogisticRegressionClassifier(final LogisticRegression model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(LogisticRegression model) {
        var params = new ParameterList();
        double learningRate = model.getLearningRate();
        int nIterations = model.getIterations();
        int reg = model.getRegularization();
        Regularization defaultReg = Regularization.DISABLE;
        for (Regularization temp : Regularization.values()) {
            if (reg == temp.getRegularization()) {
                defaultReg = temp;
                break;
            }
        }
//			int miniBatchSize = model.getMiniBatchSize();

        params.addTitleParameter("Logistic regression options");
        params.addDoubleParameter("learningRate", "Learning rate", learningRate);
        params.addIntParameter("nIterations", "Number of iterations", nIterations);
//			params.addIntParameter("miniBatchSize", "Mini batch size", miniBatchSize);
        params.addChoiceParameter("regularization", "Regularization", defaultReg, Arrays.asList(Regularization.values()));

        OpenCVClassifiers.addTerminationCriteriaParameters(params, model.getTermCriteria());
        return params;
    }

    @Override
    public TrainData createTrainData(Mat samples, Mat targets, int nLabels, Mat weights, boolean doMulticlass) {
        targets.convertTo(targets, opencv_core.CV_32F);
        return super.createTrainData(samples, targets, nLabels, weights, doMulticlass);
    }

    @Override
    LogisticRegression createStatModel() {
        return LogisticRegression.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return LogisticRegression.class;
    }

    @Override
    void updateModel(LogisticRegression model, ParameterList params, TrainData trainData) {
        double learningRate = params.getDoubleParameterValue("learningRate");
        int nIterations = params.getIntParameterValue("nIterations");
        Regularization regularization = (Regularization) params.getChoiceParameterValue("regularization");
        model.setRegularization(regularization.getRegularization());

        model.setLearningRate(learningRate);
        model.setIterations(nIterations);

        model.setTermCriteria(OpenCVClassifiers.updateTermCriteria(params, model.getTermCriteria()));
    }

}
