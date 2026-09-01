package qupath.opencv.ml.models.statmodel;

import org.bytedeco.opencv.opencv_ml.SVMSGD;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link SVMSGD}.
 */
class SVMSGDClassifier extends AbstractOpenCVClassifier<SVMSGD> {

    SVMSGDClassifier() {
        super();
    }

    SVMSGDClassifier(final SVMSGD model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(SVMSGD model) {
        var params = new ParameterList();
        return params;
    }

    @Override
    SVMSGD createStatModel() {
        return SVMSGD.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return SVMSGD.class;
    }

    @Override
    public boolean supportsAutoUpdate() {
        return false;
    }

    @Override
    void updateModel(SVMSGD model, ParameterList params, TrainData trainData) {
        // TODO Auto-generated method stub
    }

}
