package qupath.opencv.ml.models.statmodel;

import java.util.List;
import java.util.Objects;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.images.servers.PixelType;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ml.models.FeatureImportance;

/**
 * A trainable model that uses an OpenCV {@code StatModel} for prediction.
 * @param <T> the type of the StatModel.
 */
public class TrainableStatModel<T extends StatModel> implements TrainableModel {

    private transient volatile AbstractOpenCVClassifier<T> wrapper;

    // This *should* be T, but that makes de/serialization with Gson more complicated.
    // By tolerating the casts here, we keep the awkwardness limited to be within this class.
    // For example, see https://github.com/google/gson/issues/2563
    private final StatModel model;

    TrainableStatModel(T model) {
        this.model = model;
        this.wrapper = OpenCVStatModels.wrap(model); // Call here so that any exception would be thrown early
    }

    TrainableStatModel(AbstractOpenCVClassifier<T> wrapper) {
        Objects.requireNonNull(wrapper);
        this.wrapper = wrapper;
        this.model = wrapper.getStatModel();
    }

    private synchronized AbstractOpenCVClassifier<T> getWrapper() {
        if (wrapper == null) {
            synchronized (this) {
                if (wrapper == null) {
                    wrapper = OpenCVStatModels.wrap(getStatModel());
                }
            }
        }
        return wrapper;
    }

    public T getStatModel() {
        return (T)model;
    }

    @Override
    public boolean supportsMissingValues() {
        return getWrapper().supportsMissingValues();
    }

    @Override
    public boolean isTrained() {
        return getWrapper().isTrained();
    }

    @Override
    public boolean supportsMulticlass() {
        return getWrapper().supportsMulticlass();
    }

    @Override
    public boolean supportsAutoUpdate() {
        return getWrapper().supportsAutoUpdate();
    }

    @Override
    public boolean supportsProbabilities() {
        return getWrapper().supportsProbabilities();
    }

    @Override
    public ParameterList getParameterList() {
        return getWrapper().getParameterList();
    }

    @Override
    public TrainData createTrainData(Mat samples, Mat targets, int nLabels, Mat weights, boolean doMulticlass) {
        return getWrapper().createTrainData(samples, targets, nLabels, weights, doMulticlass);
    }

    @Override
    public void train(TrainData trainData) {
        getWrapper().train(trainData);
    }

    @Override
    public String getName() {
        return getWrapper().getName();
    }

    @Override
    public void predict(Mat samples, Mat results, Mat probabilities) {
        getWrapper().predict(samples, results, probabilities);
    }

    @Override
    public PixelType getOutputType() {
        return getWrapper().getOutputType();
    }

    @Override
    public List<FeatureImportance> getFeatureImportance(List<String> names) {
        return getWrapper().getFeatureImportance(names);
    }

    @Override
    public void close() throws Exception {
        getWrapper().close();
    }

    @Override
    public String toString() {
        return getWrapper().toString();
    }

}
