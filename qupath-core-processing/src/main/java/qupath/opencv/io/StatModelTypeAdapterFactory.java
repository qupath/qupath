package qupath.opencv.io;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import org.bytedeco.opencv.opencv_ml.StatModel;

public class StatModelTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (StatModel.class.isAssignableFrom(type.getRawType()))
            return (TypeAdapter<T>)new StatModelTypeAdapter();
        return null;
    }

}
