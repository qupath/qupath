package qupath.opencv.ml.models;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.bytedeco.opencv.opencv_ml.StatModel;
import qupath.opencv.io.OpenCVTypeAdapters;

class OpenCVClassifierTypeAdapter extends TypeAdapter<OpenCVStatModel> {

    @Override
    public void write(JsonWriter out, OpenCVStatModel value) throws IOException {
        if (value instanceof AbstractOpenCVClassifierML<?> classifier) {
            OpenCVTypeAdapters.getTypeAdaptor(StatModel.class).write(out, classifier.getStatModel());
        } else {
            throw new IOException("Unable to serialize " + value);
        }
    }

    @Override
    public OpenCVStatModel read(JsonReader in) throws IOException {
        var adaptor = OpenCVTypeAdapters.getTypeAdaptor(StatModel.class);
        var statModel = adaptor.read(in);
        return OpenCVClassifiers.wrapStatModel(statModel);
    }

}
