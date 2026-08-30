package qupath.opencv.ml.models;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.tools.OpenCVTools;

/**
 * Classifier based on {@link ANN_MLP}.
 */
public class ANNClassifier extends OpenCVStatModel<ANN_MLP> {

    private static final Logger logger = LoggerFactory.getLogger(ANNClassifier.class);

    private int MAX_HIDDEN_LAYERS = 5;

    enum ActivationFunction {
        IDENTITY, SIGMOID_SYM, GAUSSIAN, RELU, LEAKY_RELU;

        public int getActivationFunction() {
            return switch (this) {
                case GAUSSIAN -> ANN_MLP.GAUSSIAN;
                case IDENTITY -> ANN_MLP.IDENTITY;
                case SIGMOID_SYM -> ANN_MLP.SIGMOID_SYM;
                case RELU -> ANN_MLP.RELU;
                case LEAKY_RELU -> ANN_MLP.LEAKYRELU;
                default -> ANN_MLP.SIGMOID_SYM;
            };
        }
    }

    enum TrainingMethod {
        BACKPROP, RPROP, ANNEAL;

        public int getTrainingMethod() {
            return switch (this) {
                case BACKPROP -> ANN_MLP.BACKPROP;
                case RPROP -> ANN_MLP.RPROP;
                case ANNEAL -> ANN_MLP.ANNEAL;
                default -> ANN_MLP.BACKPROP;
            };
        }
    }


    ANNClassifier() {
        super();
    }

    ANNClassifier(final ANN_MLP model) {
        super(model);
    }

    @Override
    ParameterList createParameterList(ANN_MLP model) {
        int[] layerSizes = getLayerSizes(model, MAX_HIDDEN_LAYERS + 2);
        var params = new ParameterList();

//			// Set activation function
//			params.addTitleParameter("Activation");
//			params.addChoiceParameter("activation", "Activation function", ActivationFunction.SIGMOID_SYM,
//					Arrays.asList(ActivationFunction.values()), "Choose activation function (only SIGMOID_SYM is fully supported)");
//			params.addDoubleParameter("activationAlpha", "Alpha", 1, null, "Alpha value (influences 'steepness')");
//			params.addDoubleParameter("activationBeta", "Beta", 1, null, "Alpha value (influences 'range')");

//			// Set train method
//			params.addTitleParameter("Training method");
//			params.addChoiceParameter("trainMethod", "Training method", TrainingMethod.RPROP,
//					Arrays.asList(TrainingMethod.values()), "Choose training method");
//			params.addDoubleParameter("trainParam1", "Training parameter 1", model.getRpropDW0(), null, "Passed to either setRpropDW0 or setBackpropWeightScale");
//			params.addDoubleParameter("trainParam2", "Training parameter 2", model.getRpropDWMin(), null, "Passed to either setRpropDWMin or setBackpropMomentumScale");

        // Hidden layer sizes
        params.addTitleParameter("Hidden layers");
        for (int i = 1; i < layerSizes.length; i++) {
            params.addIntParameter("hidden" + i, "Layer " + i, layerSizes[i - 1], "Nodes", "Size of first hidden layer (0 to omit layer)");
        }

        OpenCVClassifiers.addTerminationCriteriaParameters(params, model.getTermCriteria());

        return params;
    }

    @Override
    protected int getTrainFlags() {
        return ANN_MLP.NO_OUTPUT_SCALE;
    }

    @Override
    ANN_MLP createStatModel() {
        return ANN_MLP.create();
    }

    @Override
    Class<? extends StatModel> getStatModelClass() {
        return ANN_MLP.class;
    }

    /**
     * Get the layer sizes, including input, hidden and output layers.
     *
     * @return the layer sizes, or an empty array if there is no model available.
     */
    public int[] getLayerSizes() {
        return getLayerSizes(getStatModel(), 0);
    }

    private int[] getLayerSizes(ANN_MLP model, int defaultLength) {
        if (model == null)
            return new int[defaultLength];
        lock.writeLock().lock();
        try {
            Mat sizes = getStatModel().getLayerSizes();
            if (!sizes.empty()) {
                var idx = sizes.createIndexer();
                int n = (int) sizes.total();
                int[] layerSizes = new int[n];
                for (int i = 0; i < n; i++)
                    layerSizes[i] = (int) idx.getDouble(i);
                idx.release();
                return layerSizes;
            } else {
                return new int[defaultLength];
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public TrainData createTrainData(Mat samples, Mat targets, int nLabels, Mat weights, boolean doMulticlass) {
        if (doMulticlass) {
            var indexer = targets.createIndexer();
            var targets2 = new Mat(targets.rows(), targets.cols(), opencv_core.CV_32FC1, Scalar.all(-1.0));
            FloatIndexer idxTargets = targets2.createIndexer();
            int nRows = targets.rows();
            int nCols = targets.cols();
            long[] inds = new long[2];
            for (int r = 0; r < nRows; r++) {
                for (int c = 0; c < nCols; c++) {
                    inds[0] = r;
                    inds[1] = c;
                    double val = indexer.getDouble(inds);
                    if (val > 0)
                        idxTargets.put(inds, 1f);
                }
            }
            targets.put(targets2);
            targets2.close();
        } else {
            IntBuffer buffer = OpenCVTools.ensureContinuous(targets, false).createBuffer();
            int[] vals = new int[targets.rows()];
            buffer.get(vals);
            var targets2 = new Mat(targets.rows(), nLabels + 1, opencv_core.CV_32FC1, Scalar.all(-1.0));
            FloatIndexer idxTargets = targets2.createIndexer();
            int row = 0;
            for (var v : vals) {
                idxTargets.put(row, v, 1f);
                row++;
            }
            targets.put(targets2);
            targets2.close();
        }

        return super.createTrainData(samples, targets, nLabels, weights, doMulticlass);
    }


    @Override
    public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
        // Extract parameters
//			var params = getParameterList();
//			var activation = (ActivationFunction)params.getChoiceParameterValue("activation");
//			double beta = params.getDoubleParameterValue("activationBeta");

        // For now, we only support SIGMOID_SYM as an activation function
        // (Not least because we must save/reload models, and there is no get method for this)
        boolean isSigmoidSym = true;
        double beta = 1.0;

        // Compute raw values
        if (probabilities == null)
            probabilities = new Mat();
        super.predictWithLock(samples, results, probabilities);

        // Convert to the range 0-1 if we can
        if (isSigmoidSym) {

            var indexer = probabilities.createIndexer();
            long[] inds = new long[2];
            long rows = indexer.size(0); // previously .rows()
            long cols = indexer.size(1); // previously .cols()
            double scale = 0.5 / beta;
            double offset = 0.5;

            for (long r = 0; r < rows; r++) {
                inds[0] = r;
//				double max = 0;
                for (long c = 0; c < cols; c++) {
                    inds[1] = c;
                    double val = indexer.getDouble(inds) * scale + offset;
//					val = val > 1 ? 1 : val;
//					val = val < 0 ? 0 : val;
                    indexer.putDouble(inds, val);
//					max = Math.max(max, val);
                }
            }
            indexer.release();
        }
        // TODO: Consider softmax for identity or relu activations
    }

    @Override
    void updateModel(ANN_MLP model, ParameterList params, TrainData trainData) {
        int nMeasurements = trainData.getNVars();
        int nClasses = trainData.getResponses().cols();

        var layers = new double[MAX_HIDDEN_LAYERS + 2];
        layers[0] = nMeasurements;
        int n = 1;
        for (int i = 1; i <= MAX_HIDDEN_LAYERS; i++) {
            String name = "hidden" + i;
            if (!params.containsKey(name))
                continue;
            int size = params.getIntParameterValue(name);
            // Every layer needs more than one neuron
            if (size > 1) {
                layers[n] = size;
                n++;
            }
        }
        layers[n] = nClasses;
        n++;
        if (n < layers.length)
            layers = Arrays.copyOf(layers, n);

        var mat = new Mat(n, 1, opencv_core.CV_64F, Scalar.ZERO);
        DoubleIndexer idx = mat.createIndexer();
        for (int i = 0; i < n; i++)
            idx.put(i, layers[i]);
        idx.release();

        model.setLayerSizes(mat);

        // Set other parameters
//			var activation = (ActivationFunction)params.getChoiceParameterValue("activation");
//			double activationAlpha = params.getDoubleParameterValue("activationAlpha");
//			double activationBeta = params.getDoubleParameterValue("activationBeta");
//			model.setActivationFunction(activation.getActivationFunction(), activationAlpha, activationBeta);
        model.setActivationFunction(ANN_MLP.SIGMOID_SYM, 1, 1);

//			var trainMethod = (TrainingMethod)params.getChoiceParameterValue("trainMethod");
//			double param1 = params.getDoubleParameterValue("trainParam1");
//			double param2 = params.getDoubleParameterValue("trainParam2");
//			model.setTrainMethod(trainMethod.getTrainingMethod(), param1, param2);

        // Set termination criterion
        model.setTermCriteria(OpenCVClassifiers.updateTermCriteria(params, model.getTermCriteria()));

        logger.debug("Initializing ANN with layer sizes: " + GeneralTools.arrayToString(Locale.getDefault(Locale.Category.FORMAT), layers, 0));
    }

}
