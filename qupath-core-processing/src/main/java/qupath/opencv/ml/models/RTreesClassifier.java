package qupath.opencv.ml.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Classifier based on {@link RTrees}.
 */
public class RTreesClassifier extends AbstractTreeClassifier<RTrees> {

    private static final Logger logger = LoggerFactory.getLogger(RTreesClassifier.class);

    private double[] featureImportance;

    RTreesClassifier() {
        super();
    }

    RTreesClassifier(final RTrees model) {
        super(model);
    }

    @Override
    RTrees createStatModel() {
        var model = RTrees.create();
        model.setMaxDepth(0);
        model.setTermCriteria(
                new TermCriteria(TermCriteria.COUNT, 50, 0));
        model.setCalculateVarImportance(true);
        return model;
    }

    @Override
    ParameterList createParameterList(RTrees model) {
        ParameterList params = super.createParameterList(model);

        int activeVarCount = model.getActiveVarCount();
        var termCrit = model.getTermCriteria();
        int maxTrees = termCrit.maxCount();
        double epsilon = termCrit.epsilon();
        boolean calcImportance = model.getCalculateVarImportance();

        params.addIntParameter("activeVarCount", "Active variable count", activeVarCount, null, "Number of features per tree node (if <=0, will use square root of number of features)");
        params.addIntParameter("maxTrees", "Maximum number of trees", maxTrees, null, "Maximum possible number of trees - but viewer may be used if 'Termination epsilon' is high");
        params.addDoubleParameter("epsilon", "Termination epsilon", epsilon, null, "Termination criterion - if this is high, viewer trees may be used for classification");
        params.addBooleanParameter("calcImportance", "Calculate variable importance", calcImportance, "Calculate estimate of each variable's importance (this impacts the results of the classifier!)");
        return params;
    }

    @Override
    public void train(TrainData trainData) {
        super.train(trainData);
        var trees = getStatModel();
        if (trees.getCalculateVarImportance()) {
//				synchronized (this) {
            var importance = trees.getVarImportance();
            var indexer = importance.createIndexer();
            int nFeatures = (int) indexer.size(0);
            featureImportance = new double[nFeatures];
            for (int r = 0; r < nFeatures; r++) {
                featureImportance[r] = indexer.getDouble(r);
            }
            indexer.release();
//				}
        } else
            featureImportance = null;
    }

    @Override
    protected int getTrainFlags() {
        return super.getTrainFlags();
    }

    /**
     * Check if the last time train was called, variable (feature) importance was calculated.
     *
     * @return
     * @see #getFeatureImportance()
     */
    public synchronized boolean hasFeatureImportance() {
        return featureImportance != null;
    }

    /**
     * Request the variable importance values from the last trained RTrees classifier, if available.
     *
     * @return the ordered array of importance values, or null if this is unavailable
     * @see #hasFeatureImportance()
     */
    public double[] getFeatureImportance() {
        return featureImportance == null ? null : featureImportance.clone();
    }

    @Override
    void updateModel(RTrees model, ParameterList params, TrainData trainData) {

        super.updateModel(model, params, trainData);

        int activeVarCount = params.getIntParameterValue("activeVarCount");
        int maxTrees = params.getIntParameterValue("maxTrees");
        double epsilon = params.getDoubleParameterValue("epsilon");
        boolean calcImportance = params.getBooleanParameterValue("calcImportance");

        int type = 0;
        if (maxTrees >= 1)
            type += TermCriteria.MAX_ITER;
        if (epsilon > 0)
            type += TermCriteria.EPS;
        var termCrit = new TermCriteria(type, maxTrees, epsilon);

        model.setActiveVarCount(activeVarCount);
        model.setUseSurrogates(false); // Not implemented, throws an exception
        model.setTermCriteria(termCrit);
        model.setCalculateVarImportance(calcImportance);
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return RTrees.class;
    }


    @Override
    public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
        // If we don't need probabilities, it's quite straightforward
        var model = getStatModel();
        if (probabilities == null) {
            model.predict(samples, results, RTrees.PREDICT_AUTO);
//				var idx = samples.createIndexer();
//				idx.release();
            results.convertTo(results, opencv_core.CV_32SC1);
            return;
        }

        // If we want probabilities, we can try our best using the votes
        var votes = new Mat();
        model.getVotes(samples, votes, RTrees.PREDICT_AUTO);

        int nVoteColumns = votes.cols();
        int nSamples = samples.rows();
        IntIndexer indexer = votes.createIndexer();

        int[] orderedClasses = new int[nVoteColumns];
        for (int c = 0; c < nVoteColumns; c++) {
            orderedClasses[c] = indexer.get(0, c);
        }

        // Preallocate output
        int maxClassInd = Arrays.stream(orderedClasses).max().orElse(nVoteColumns - 1) + 1;
        probabilities.create(nSamples, maxClassInd, opencv_core.CV_32FC1);
        probabilities.put(Scalar.ZERO);
        FloatIndexer idxProbabilities = probabilities.createIndexer();
        results.create(nSamples, 1, opencv_core.CV_32SC1);
        IntIndexer idxResults = results.createIndexer();

        long row = 1;
        for (var i = 0; i < nSamples; i++) {
            double sum = 0;
            int maxCount = -1;
            int maxInd = -1;
            for (long c = 0; c < nVoteColumns; c++) {
                int count = indexer.get(row, c);
                if (count > maxCount) {
                    maxCount = count;
                    maxInd = (int) c;
                }
                sum += count;
            }
            // Update probability estimates
            for (int c = 0; c < nVoteColumns; c++) {
                int count = indexer.get(row, c);
                idxProbabilities.put(i, orderedClasses[c], (float) (count / sum));
            }
            // Update prediction
            int prediction = orderedClasses[maxInd];
            idxResults.put(i, prediction);
            row++;
        }

        indexer.release();
        idxProbabilities.release();
        idxResults.release();
        votes.close();
    }

    /**
     * Log the variable importance, if this has been calculated.
     *
     * @param features the feature names. This is required for logging;
     *                 if unknown, {@link #getFeatureImportance()} may still be used.
     * @param level    the log level to use
     * @see #hasFeatureImportance()
     * @see #getFeatureImportance()
     * @see #logVariableImportance(List)
     */
    public void logVariableImportance(final List<String> features, Level level) {
        var importance = getFeatureImportance();
        if (importance == null) {
            logger.atLevel(level).log("Feature importance has not been calculated");
            return;
        }
        try {
            var sorted = IntStream.range(0, importance.length)
                    .boxed()
                    .sorted((a, b) -> -Double.compare(importance[a], importance[b]))
                    .mapToInt(i -> i).toArray();

            if (sorted.length != features.size()) {
                logger.warn("Length of variable importance array {} does not match length of feature names {}",
                        sorted.length, features.size());
                return;
            }

            var sb = new StringBuilder("Variable importance:");
            for (int ind : sorted) {
                sb.append("\n");
                sb.append(String.format("%.4f \t %s", importance[ind], features.get(ind)));
            }
            logger.atLevel(level).log(sb.toString());
        } catch (Exception e) {
            logger.warn("Error logging feature importance: {}", e.getMessage());
        }
    }

    /**
     * Log the variable importance, if this has been calculated, at the default INFO level.
     *
     * @param features the feature names
     */
    public void logVariableImportance(final List<String> features) {
        logVariableImportance(features, Level.INFO);
    }

    /**
     * Get the OOB error, if the model is trained and the OOB error is available.
     *
     * @return the OOB error, or 0 if not available.
     */
    public double getOOBError() {
        return getStatModel().getOOBError();
    }

    /**
     * Get a list of variable importance values.
     *
     * @param names the variable names.
     * @return a list of variable importance values, or an empty list if these are not available.
     * This occurs if importance has not been calculated, or the feature names array is
     * not of a matching length.
     * @see #getFeatureImportance()
     * @since v0.8.0
     */
    public List<VariableImportance> getVariableImportance(List<String> names) {
        double[] importance = getFeatureImportance();
        if (importance == null || importance.length != names.size())
            return List.of();
        List<VariableImportance> list = new ArrayList<>();
        for (int i = 0; i < importance.length; i++) {
            list.add(new VariableImportance(names.get(i), importance[i]));
        }
        return list;
    }

    /**
     * Record to store a feature (variable) name and its importance,
     * as calculated using RTrees.
     *
     * @param name       the variable name
     * @param importance the importance value
     */
    public record VariableImportance(String name, double importance) {
    }

}
