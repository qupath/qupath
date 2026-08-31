package qupath.opencv.ml.models;

import com.google.gson.annotations.JsonAdapter;
import java.util.Objects;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_ml.TrainData;
import qupath.lib.images.servers.PixelType;
import qupath.lib.plugins.parameters.ParameterList;

@JsonAdapter(OpenCVStatModelTypeAdapter.class)
public class OpenCVTrainableModel<T extends StatModel> implements TrainableModel {

    private transient volatile AbstractOpenCVClassifier<T> wrapper;

    private T model;

    // Only for JSON serialization
    private OpenCVTrainableModel() {}

    OpenCVTrainableModel(T model) {
        this.model = model;
        this.wrapper = OpenCVClassifiers.wrap(model); // Call here so that any exception would be thrown early
    }

    OpenCVTrainableModel(AbstractOpenCVClassifier<T> wrapper) {
        Objects.requireNonNull(wrapper);
        this.wrapper = wrapper;
        this.model = wrapper.getStatModel();
    }

    private synchronized AbstractOpenCVClassifier<T> getWrapper() {
        if (wrapper == null) {
            synchronized (this) {
                if (wrapper == null) {
                    wrapper = OpenCVClassifiers.wrap(model);
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
    public PixelType getOutputType(boolean requestProbabilities) {
        return getWrapper().getOutputType(requestProbabilities);
    }

    @Override
    public void close() throws Exception {
        getWrapper().close();
    }

}
