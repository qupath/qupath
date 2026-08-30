package qupath.opencv.ml.models;

import com.google.gson.annotations.JsonAdapter;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.UMat;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.Boost;
import org.bytedeco.opencv.opencv_ml.DTrees;
import org.bytedeco.opencv.opencv_ml.EM;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.PixelType;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.io.OpenCVTypeAdapters;

/**
 * Abstract implementation of {@link TrainableModel} that is based upon {@link StatModel},
 * part of the OpenCV machine learning module.
 * @param <T>
 * @implNote For compatibility with QuPath v0.7.0 and earlier, the default JSON serialization
 *           stores only the StatModel that is wrapped, not any associated parameters.
 */
public abstract class OpenCVStatModel<T extends StatModel> implements TrainableModel {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVStatModel.class);

    @JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
    private T model;
    private transient ParameterList params; // Should take defaults from the serialized model

    transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    abstract ParameterList createParameterList(T model);

    abstract T createStatModel();

    abstract void updateModel(T model, ParameterList params, TrainData trainData);

    OpenCVStatModel() {}

    OpenCVStatModel(T model) {
        this.model = model;
        params = createParameterList(model);
    }

    /**
     * Returns false (the default value).
     */
    @Override
    public boolean supportsMulticlass() {
        return false;
    }

    /**
     * Returns true (the default value).
     */
    @Override
    public boolean supportsAutoUpdate() {
        return true;
    }

    @Override
    public boolean supportsProbabilities() {
        var model = getStatModel();
        return model instanceof RTrees ||
                model instanceof ANN_MLP ||
                model instanceof org.bytedeco.opencv.opencv_ml.NormalBayesClassifier;
    }

    /**
     * Get the {@link StatModel} wrapped by this classifier.
     * Note that this should not be modified externally.
     * @return
     */
    public T getStatModel() {
        if (model == null)
            model = createStatModel();
        return model;
    }

    @Override
    public boolean isTrained() {
        return getStatModel().isTrained();
    }

    @Override
    public ParameterList getParameterList() {
        if (params == null)
            params = createParameterList(getStatModel());
        return params;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public TrainData createTrainData(Mat samples, Mat targets, int nLabels, Mat weights, boolean doMulticlass) {
        if (doMulticlass && !supportsMulticlass())
            logger.warn("Multiclass classification requested, but not supported");
        if (useUMat()) {
            UMat uSamples = samples.getUMat(opencv_core.ACCESS_READ);
            UMat uTargets = targets.getUMat(opencv_core.ACCESS_READ);
            if (weights == null || weights.empty())
                return TrainData.create(uSamples, opencv_ml.ROW_SAMPLE, uTargets);
            UMat uWeights = weights.getUMat(opencv_core.ACCESS_READ);
            return TrainData.create(uSamples, opencv_ml.ROW_SAMPLE, uTargets, null, null, uWeights, null);
        }

        if (weights == null || weights.empty())
            return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets);
        else
            return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets, null, null, weights, null);
    }

    boolean useUMat() {
        return false;
    }

    @Override
    public void train(TrainData trainData) {
        lock.writeLock().lock();
        try {
            trainWithLock(trainData);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Implement trainWithLock rather than train directly to ensure a lock is set
     * when training, which can be used to prevent prediction occurring simultaneously.
     *
     * @param trainData
     * @see #predictWithLock
     */
    public void trainWithLock(TrainData trainData) {
        var statModel = getStatModel();
        opencv_core.setRNGSeed(1012);
        updateModel(statModel, getParameterList(), trainData);
        statModel.train(trainData, getTrainFlags());
    }

    protected int getTrainFlags() {
        return 0;
    }

    abstract Class<? extends StatModel> getStatModelClass();

    @Override
    public String getName() {
        var cls = getStatModelClass();

        if (ANN_MLP.class.equals(cls))
            return "Artificial neural network (ANN_MLP)";
        else if (RTrees.class.equals(cls))
            return "Random trees (RTrees)";
        else if (Boost.class.equals(cls))
            return "Boosted trees (Boost)";
        else if (DTrees.class.equals(cls))
            return "Decision tree (DTrees)";
        else if (EM.class.equals(cls))
            return "Expectation maximization";
        else if (KNearest.class.equals(cls))
            return "K nearest neighbor";
        else if (LogisticRegression.class.equals(cls))
            return "Logistic regression";
        else if (org.bytedeco.opencv.opencv_ml.NormalBayesClassifier.class.equals(cls))
            return "Normal Bayes classifier";

        return getStatModel().getClass().getSimpleName();
    }

    /**
     * Default implementation calling {@link #predictWithLock(Mat, Mat, Mat)},
     * which is the method that subclasses should override.
     * <p>
     * If results originally had more than 1 column, it will be returned as probabilities
     * (if probabilities is not null);
     * {@code probabilities} will be an empty matrix (i.e. no probabilities calculated).
     */
    @Override
    public void predict(Mat samples, Mat results, Mat probabilities) {
        lock.readLock().lock();
        try {
            predictWithLock(samples, results, probabilities);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Implement predictWithLock rather than predict to ensure predict is not called while
     * training.
     * <p>
     * The default implementation calls
     * <pre>
     * statModel.predict(samples, results, 0);
     * </pre>
     * before attempting to sanitize the outcome so that results always contains a signed int Mat containing
     * classifications.
     *
     * @param samples
     * @param results
     * @param probabilities
     * @see #trainWithLock
     */
    protected void predictWithLock(Mat samples, Mat results, Mat probabilities) {
        var statModel = getStatModel();
        statModel.predict(samples, results, 0);

        int nSamples = results.rows();

        if (results.cols() > 1) {
            var indexer = results.createIndexer();
            int nClasses = results.cols();

            var matResultsNew = new Mat(nSamples, 1, opencv_core.CV_32SC1);
            IntIndexer idxResults = matResultsNew.createIndexer();
            if (probabilities != null) {
                probabilities.create(nSamples, nClasses, opencv_core.CV_32FC1);
                probabilities.put(results);
            }

            var inds = new long[2];
            for (int row = 0; row < nSamples; row++) {
                double maxValue = Double.NEGATIVE_INFINITY;
                int maxInd = -1;
                inds[0] = row;
                for (long c = 0; c < nClasses; c++) {
                    inds[1] = c;
                    double val = indexer.getDouble(inds);
                    if (val > maxValue) {
                        maxValue = val;
                        maxInd = (int) c;
                    }
                }
                idxResults.put(row, maxInd);
            }
            indexer.release();
            idxResults.release();
            results.put(matResultsNew);
            matResultsNew.close();
        } else {
            results.convertTo(results, opencv_core.CV_32SC1);
            if (probabilities != null) {
                // Ensure we have an empty matrix for probabilities
                probabilities.create(0, 0, opencv_core.CV_32FC1);
            }
        }
    }

    /**
     * Tree classifiers in OpenCV support missing values, others do not.
     */
    @Override
    public boolean supportsMissingValues() {
        return DTrees.class.isAssignableFrom(getStatModelClass());
    }

    @Override
    public PixelType getOutputType(boolean requestProbabilities) {
        return requestProbabilities ? PixelType.FLOAT32 : PixelType.INT32;
    }

    @Override
    public void close() {
        var model = getStatModel();
        if (model != null)
            model.close();
    }

}
