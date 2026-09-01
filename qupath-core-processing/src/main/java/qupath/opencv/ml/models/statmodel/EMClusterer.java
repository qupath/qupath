package qupath.opencv.ml.models.statmodel;

import org.bytedeco.opencv.opencv_ml.EM;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Clusterer based on {@link EM}.
 */
class EMClusterer extends AbstractOpenCVClassifier<EM> {

    EMClusterer() {
        super();
    }

    EMClusterer(final EM model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(EM model) {
        var params = new ParameterList();

        int nClusters = model.getClustersNumber();
        params.addIntParameter("nClusters", "Number of clusters", nClusters);

        return params;
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return EM.class;
    }


    @Override
    EM createStatModel() {
        return EM.create();
    }

    @Override
    void updateModel(EM model, ParameterList params, TrainData trainData) {
        model.setClustersNumber(params.getIntParameterValue("nClusters"));
    }

}
