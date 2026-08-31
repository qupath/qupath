package qupath.opencv.ml.models;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.bytedeco.opencv.opencv_ml.StatModel;
import qupath.lib.io.GsonTools;

class OpenCVStatModelTypeAdapter extends TypeAdapter<OpenCVTrainableModel<?>> {

    @Override
    public void write(JsonWriter out, OpenCVTrainableModel<?> value) throws IOException {
        GsonTools.getInstance().toJson(value.getStatModel(), StatModel.class, out);
    }

    @Override
    public OpenCVTrainableModel<?> read(JsonReader in) throws IOException {
        StatModel model = GsonTools.getInstance().fromJson(in, StatModel.class);
        return new OpenCVTrainableModel(model);
    }

}
