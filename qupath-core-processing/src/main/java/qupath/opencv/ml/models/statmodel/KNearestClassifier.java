package qupath.opencv.ml.models.statmodel;

import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link KNearest}.
 */
class KNearestClassifier extends AbstractOpenCVClassifier<KNearest> {

    KNearestClassifier() {
        super();
    }

    KNearestClassifier(final KNearest model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(KNearest model) {
        var params = new ParameterList();
        int defaultK = model.getDefaultK();
        params.addIntParameter("defaultK", "Default K", defaultK, null, "Number of nearest neighbors");
        return params;
    }

    @Override
    KNearest createStatModel() {
        return KNearest.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return KNearest.class;
    }

    @Override
    void updateModel(KNearest model, ParameterList params, TrainData trainData) {
        int defaultK = params.getIntParameterValue("defaultK");
        model.setDefaultK(defaultK);
        model.setIsClassifier(true);
    }

}
