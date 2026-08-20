package qupath.opencv.ml.models;

import org.bytedeco.opencv.opencv_ml.DTrees;
import org.bytedeco.opencv.opencv_ml.StatModel;

/**
 * Classifier based on {@link DTrees}.
 */
public class DTreesClassifier extends AbstractTreeClassifier<DTrees> {

    DTreesClassifier() {
        super();
    }

    DTreesClassifier(final DTrees model) {
        super(model);
    }

    @Override
    DTrees createStatModel() {
        return DTrees.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return DTrees.class;
    }

}
