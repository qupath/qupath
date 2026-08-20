package qupath.opencv.ml.models;

import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.StatModel;
import qupath.opencv.io.OpenCVTypeAdapters;

class OpenCVClassifierTypeAdapter extends TypeAdapter<OpenCVStatModel> {

    @Override
    public void write(JsonWriter out, OpenCVStatModel value) throws IOException {
        if (value instanceof EnsembleClassifier ensembleClassifier) {
            out.beginArray();
            for (var model : ensembleClassifier.getModels()) {
                write(out, model);
            }
            OpenCVTypeAdapters.getTypeAdaptor(StatModel.class).write(out, ensembleClassifier.getStatModel());
            out.endArray();
        } else {
            if (value instanceof AbstractOpenCVClassifierML<?> classifier) {
                OpenCVTypeAdapters.getTypeAdaptor(StatModel.class).write(out, classifier.getStatModel());
            } else {
                throw new IOException("Unable to serialize " + value);
            }
        }
    }

    @Override
    public OpenCVStatModel read(JsonReader in) throws IOException {
        var adaptor = OpenCVTypeAdapters.getTypeAdaptor(StatModel.class);
        if (in.peek() == JsonToken.BEGIN_ARRAY) {
            var array = JsonParser.parseReader(in).getAsJsonArray();
            List<OpenCVStatModel> allModels = new ArrayList<>();
            for (int i = 0; i < array.size() - 1; i++) {
                var obj = array.get(i);
                allModels.add(OpenCVClassifiers.wrapStatModel(adaptor.fromJsonTree(obj)));
            }
            var lastModel = adaptor.fromJsonTree(array.get(array.size() - 1));
            if (lastModel instanceof RTrees trees) {
                return new EnsembleClassifier(allModels, trees);
            } else {
                throw new IOException("Expected last model to be DTrees, instead it was " + lastModel);
            }
        }
        var statModel = adaptor.read(in);
        return OpenCVClassifiers.wrapStatModel(statModel);
    }

}
