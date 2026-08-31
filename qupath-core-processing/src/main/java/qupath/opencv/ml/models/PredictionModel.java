package qupath.opencv.ml.models;

import org.bytedeco.opencv.opencv_core.Mat;
import qupath.lib.images.servers.PixelType;

/**
 * Wrapper for a model that can be used for prediction,
 * for example within an object or pixel classifier.
 * <p>
 * This is a new interface introduced in v0.8.0 to provide more flexibility.
 * The closest match to the previous code is {@link AbstractOpenCVClassifier},
 * which is a more complicated interface that only supports an OpenCV {@code StatModel}.
 *
 * @since v0.8.0
 */
public interface PredictionModel extends AutoCloseable {

    /**
     * User-friendly, readable name for the classifier
     *
     * @return the classifier name
     */
    String getName();

    /**
     * Apply classification, optionally requesting probability estimates.
     * <p>
     * Not all models are capable of estimating probability values, in which case
     * probabilities will be null (if not supplied) or an empty matrix.
     * <p>
     * Note also that if probabilities are required, these will not necessarily be normalized
     * between 0 and 1 (although they generally are).  They represent a best-effort for the
     * model to provide confidence values, but are not (necessarily) strictly probabilities.
     * <p>
     * For example, RTrees estimates probabilities based on the proportion of votes for the 'winning'
     * classification.
     *
     * @param samples       the input samples
     * @param results       a Mat to receive the results; the type is given by {@link #getOutputType()}
     * @param probabilities a Mat to receive probability estimates, or null if probabilities are not needed.
     *                      If returned, probabilities should be in {@link PixelType#FLOAT32}.
     */
    void predict(Mat samples, Mat results, Mat probabilities);

    /**
     * Get the output type of model predictions.
     * This is the output for the {@code results} parameter in {@link #predict(Mat, Mat, Mat)}.
     * @return the type of the model output
     */
    PixelType getOutputType();


}
