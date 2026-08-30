package qupath.opencv.ml.models;

import com.google.gson.annotations.JsonAdapter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Wrapper class for a {@link StatModel}, which standardizes how training may be performed and
 * parameters can be set.
 */
public interface TrainableModel extends PredictionModel {

    /**
     * Classifier can handle missing (NaN) values
     *
     * @return true if NaNs are supported, false otherwise
     */
    boolean supportsMissingValues();

    /**
     * Classifier has already been trained and is ready to predict.
     *
     * @return true if the classifier is trained, false otherwise
     */
    boolean isTrained();

    /**
     * Classifier is able to handle more than one outputs for a single sample.
     *
     * @return true if multiclass classification is supported, false otherwise
     */
    boolean supportsMulticlass();

    /**
     * Classifier can be trained interactively  (i.e. quickly).
     *
     * @return true if interactive classification is supported, false otherwise
     */
    boolean supportsAutoUpdate();

    /**
     * Classifier can output a prediction confidence (expressed between 0 and 1),
     * so may be interpreted as a probability... even if it isn't necessarily one.
     *
     * @return true if (pseudo-)probabilities can be provided
     */
    boolean supportsProbabilities();

    /**
     * Retrieve a list of adjustable parameter that can be used to customize the classifier.
     * After making changes to the {@link ParameterList}, the classifier should be retrained
     * before being used.
     *
     * @return the parameter list for this classifier
     */
    ParameterList getParameterList();

    /**
     * Create training data in the format required by this classifier.
     *
     * @param samples
     * @param targets
     * @param nLabels      total number of labels, which are 0-nLabels-1
     * @param weights      optional weights
     * @param doMulticlass
     * @return
     * @see #train(TrainData)
     */
    TrainData createTrainData(Mat samples, Mat targets, int nLabels, Mat weights, boolean doMulticlass);

    /**
     * Train the classifier using data in an appropriate format.
     *
     * @param trainData
     * @see #createTrainData(Mat, Mat, int, Mat, boolean)
     */
    void train(TrainData trainData);


}
