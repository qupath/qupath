package qupath.opencv.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.bytedeco.opencv.opencv_core.FileStorage;

/**
 * TypeAdapter that helps include OpenCV-based objects within a Java object being serialized to JSON.
 *
 * @param <T>
 */
abstract class AbstractOpenCVTypeAdapter<T> extends TypeAdapter<T> {

    private final Gson gson = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        var strictness = out.getStrictness();
        String json = null;
        try (FileStorage fs = new FileStorage()) {
            fs.open("anything.json", FileStorage.FORMAT_JSON + FileStorage.WRITE + FileStorage.MEMORY);
            write(fs, value);
            json = fs.releaseAndGetString().getString().trim();

            JsonObject element = gson.fromJson(json.trim(), JsonObject.class);
            gson.toJson(element, out);
        } finally {
            out.setStrictness(strictness);
        }
    }

    abstract void write(FileStorage fs, T value);

    abstract T read(FileStorage fs);

    @Override
    public T read(JsonReader in) throws IOException {
        var strictness = in.getStrictness();
        try {
            JsonElement element = JsonParser.parseReader(in);
            JsonObject obj = element.getAsJsonObject();
            String inputString = obj.toString();//obj.get("mat").toString();
            try (FileStorage fs = new FileStorage()) {
                fs.open(inputString, FileStorage.FORMAT_JSON + FileStorage.READ + FileStorage.MEMORY);
                return read(fs);
            }
        } finally {
            in.setStrictness(strictness);
        }
    }

}
