package qupath.opencv.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.bytedeco.opencv.opencv_ml.StatModel;
import qupath.opencv.ml.models.OpenCVClassifiers;
import qupath.opencv.ml.models.OpenCVStatModel;

public class OpenCVStatModelTypeAdapter extends TypeAdapter<OpenCVStatModel> {

    private static final Gson gson = new GsonBuilder()
            .serializeSpecialFloatingPointValues()
            .setStrictness(Strictness.LENIENT)
            .registerTypeAdapter(StatModel.class, new StatModelTypeAdapter())
            .create();

    @Override
    public void write(JsonWriter out, OpenCVStatModel value) throws IOException {
        gson.toJson(value.getStatModel(), StatModel.class, out);
//			new StatModelTypeAdapter().write(out, value.getStatModel());
    }

    @Override
    public OpenCVStatModel<?> read(JsonReader in) throws IOException {
        JsonObject obj = gson.fromJson(in, JsonObject.class);
        if (!obj.has("model-type")) {
            var statModel = gson.fromJson(obj, StatModel.class);
            return OpenCVClassifiers.wrapStatModel(statModel);
        } else {
            throw new IllegalArgumentException("Not a valid " + OpenCVStatModel.class.getSimpleName());
        }
//			if (json.)
//			gson.fromJson()
//			var statModel = new StatModelTypeAdapter().read(in);
//			return OpenCVClassifiers.wrapStatModel(statModel);
    }

}
