package qupath.opencv.ml.models;

import org.bytedeco.opencv.opencv_ml.Boost;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link Boost}.
 */
public class BoostClassifier extends AbstractTreeClassifier<Boost> {

    BoostClassifier() {
        super();
    }

    BoostClassifier(final Boost model) {
        super(model);
    }

    @Override
    Boost createStatModel() {
        return Boost.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return Boost.class;
    }


    @Override
    ParameterList createParameterList(Boost model) {
        ParameterList params = super.createParameterList(model);

//			int boostType = model.getBoostType();
        var weakCount = model.getWeakCount();
        double weightTrimRate = model.getWeightTrimRate();

        params.addIntParameter("weakCount", "Number of weak classifiers", weakCount, null, "Number of weak classifiers to train");
        params.addDoubleParameter("weightTrimRate", "Weight trim rate", weightTrimRate, null, 0, 1, "Threshold used to save computational time");

        return params;
    }

    @Override
    void updateModel(Boost model, ParameterList params, TrainData trainData) {
        super.updateModel(model, params, trainData);

        int weakCount = params.getIntParameterValue("weakCount");
        double weightTrimRate = params.getDoubleParameterValue("weightTrimRate");

        model.setWeakCount(weakCount);
        model.setWeightTrimRate(weightTrimRate);
    }

}
