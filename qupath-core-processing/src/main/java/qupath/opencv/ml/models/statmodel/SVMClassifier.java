package qupath.opencv.ml.models.statmodel;

import org.bytedeco.opencv.opencv_ml.SVM;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link SVM}.
 */
class SVMClassifier extends AbstractOpenCVClassifier<SVM> {

    SVMClassifier() {
        super();
    }

    SVMClassifier(final SVM model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(SVM model) {
        var params = new ParameterList();
        return params;
    }

    @Override
    SVM createStatModel() {
        return SVM.create();
    }

    @Override
    public boolean supportsAutoUpdate() {
        return false;
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return SVM.class;
    }

    @Override
    void updateModel(SVM model, ParameterList params, TrainData trainData) {
        // TODO Auto-generated method stub
    }

}
