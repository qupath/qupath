package qupath.opencv.io;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Map;
import org.bytedeco.opencv.opencv_core.Size;

class SizeTypeAdapter extends TypeAdapter<Size> {

    @Override
    public void write(JsonWriter out, Size value) throws IOException {
        if (value == null || value.isNull())
            out.nullValue();
        else {
            out.beginObject();

            out.name("width");
            out.value(value.width());

            out.name("height");
            out.value(value.height());

            out.endObject();
        }
    }

    @Override
    public Size read(JsonReader in) throws IOException {
        in.beginObject();
        var map = Map.of(
                in.nextName().toLowerCase(), in.nextInt(),
                in.nextName().toLowerCase(), in.nextInt()
        );
        in.endObject();
        return new Size(map.get("width"), map.get("height"));
    }

}
