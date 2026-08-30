package qupath.opencv.ml.models;

import org.bytedeco.opencv.opencv_ml.DTrees;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Abstract class to wrap StatModels that derive from DTrees.
 * <p>
 * These share some parameters, but not all actually work
 * (i.e. OpenCV throws an exception when attempting to change the defaults).
 * Therefore, care needs to be taken to expose only options that work as adjustable parameters.
 * @param <T>
 */
abstract class AbstractTreeClassifier<T extends DTrees> extends OpenCVStatModel<T> {

    AbstractTreeClassifier() {
        super();
    }

    AbstractTreeClassifier(final T model) {
        super(model);
    }


    @Override
    ParameterList createParameterList(T model) {

        int maxDepth = Math.min(model.getMaxDepth(), 1000);
        int minSampleCount = model.getMinSampleCount();
//			float regressionAccuracy = model.getRegressionAccuracy();
        boolean use1SERule = model.getUse1SERule();

        // Unused parameters
//			int cvFolds = model.getCVFolds(); // Not implemented
//			int maxCategories = model.getMaxCategories();
//			boolean truncatePrunedTree = model.getTruncatePrunedTree();
//			boolean useSurrogates = model.getUseSurrogates(); // Not implemented in OpenCV at this time

        // TODO: Consider use of priors
//			model.getPriors(null);

        ParameterList params = new ParameterList()
//					.addIntParameter("cvFolds", "Cross-validation folds", cvFolds, "Number of cross-validation folds to use when building the tree")
                .addIntParameter("maxDepth", "Maximum tree depth", maxDepth, null, "Maximum possible tree depth")
                .addIntParameter("minSampleCount", "Minimum sample count", minSampleCount, null, "Minimum number of samples per node")
//					.addDoubleParameter("regressionAccuracy", "Regression accuracy", regressionAccuracy, null, "Termination criterion")
                .addBooleanParameter("use1SERule", "Use 1SE rule", use1SERule, "Harsher pruning, more compact tree");

        return params;
    }

    @Override
    void updateModel(T model, ParameterList params, TrainData trainData) {

//			int cvFolds = params.getIntParameterValue("cvFolds");
        int maxDepth = params.getIntParameterValue("maxDepth");
        int minSampleCount = params.getIntParameterValue("minSampleCount");
//			float regressionAccuracy = params.getDoubleParameterValue("regressionAccuracy").floatValue();
        boolean use1SERule = params.getBooleanParameterValue("use1SERule");

//			model.setCVFolds(cvFolds < 1 ? 1 : cvFolds);
        model.setCVFolds(0);
        model.setMaxDepth(maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth);
        model.setMinSampleCount(Math.max(minSampleCount, 1));
//			model.setRegressionAccuracy(regressionAccuracy < 1e-6f ? 1e-6f : regressionAccuracy);
        model.setUse1SERule(use1SERule);
    }


}
