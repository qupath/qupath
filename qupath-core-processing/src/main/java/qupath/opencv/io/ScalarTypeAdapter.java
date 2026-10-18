package qupath.opencv.io;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.bytedeco.opencv.opencv_core.Scalar;

class ScalarTypeAdapter extends TypeAdapter<Scalar> {

    @Override
    public void write(JsonWriter out, Scalar value) throws IOException {
        if (value == null || value.isNull())
            out.nullValue();
        else {
            out.beginArray();
            for (int i = 0; i < 4; i++)
                out.value(value.get(i));
            out.endArray();
        }
    }

    @Override
    public Scalar read(JsonReader in) throws IOException {
        in.beginArray();
        double[] values = new double[4];
        int n = 0;
        while (in.hasNext() && n < values.length) {
            values[n] = in.nextDouble();
            n++;
        }
        in.endArray();
        if (n == 0)
            return new Scalar();
        else if (n == 1)
            return new Scalar(values[0]);
        else if (n == 2)
            return new Scalar(values[0], values[1]);
        else
            return new Scalar(values[0], values[1], values[2], values[3]);
    }

}
