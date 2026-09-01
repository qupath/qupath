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
import org.bytedeco.opencv.opencv_core.FileNode;
import org.bytedeco.opencv.opencv_core.FileStorage;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.Boost;
import org.bytedeco.opencv.opencv_ml.DTrees;
import org.bytedeco.opencv.opencv_ml.EM;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.NormalBayesClassifier;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.SVM;
import org.bytedeco.opencv.opencv_ml.SVMSGD;
import org.bytedeco.opencv.opencv_ml.StatModel;

class StatModelTypeAdapter extends TypeAdapter<StatModel> {

    private final Gson gson = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

    @Override
    public void write(JsonWriter out, StatModel value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        try (FileStorage fs = new FileStorage()) {
            fs.open("anything.json", FileStorage.FORMAT_JSON + FileStorage.WRITE + FileStorage.MEMORY);
//				value.write(fs);

            // Change v0.3.0 - for KNearest (at least) it's important to write using the default name, otherwise the model cannot be loaded again
            value.write(fs, value.getDefaultName());
            String json = fs.releaseAndGetString().getString();

            out.beginObject();
            out.name("class");
            out.value(value.getClass().getSimpleName());
            out.name("statmodel");

            // jsonValue works for JsonWriter but not JsonTreeWriter, so we try to work around this...
            JsonObject element = gson.fromJson(json.trim(), JsonObject.class);

            gson.toJson(element, out);
            out.endObject();
        }
    }

    // model.read(FileNode) is reported to be deprecated in JavaCPP,
// but I can't find evidence in OpenCV that this is correct -
// and I can't find a replacement.
// The warning appears on every QuPath launch, so I'd like to remove it
// so that (real, actionable) warnings don't get ignored.
    @SuppressWarnings("deprecation")
    @Override
    public StatModel read(JsonReader in) throws IOException {

        var strictness = in.getStrictness();

        try {
            JsonElement element = JsonParser.parseReader(in);

            JsonObject obj = element.getAsJsonObject();

            String className = obj.get("class").getAsString();

            // It's a bit roundabout... but toString() gives Strings that are too long and unsupported
            // by OpenCV, so we take another tour through Gson.
            var objStatModel = obj.get("statmodel");
            String modelString = new GsonBuilder().setPrettyPrinting().create().toJson(objStatModel);

            // In QuPath v0.2 we didn't use OpenCV's default name for the classifier, in which case it would be insert as the root -
            // but this failed for KNearest, so now we need to use the name & cope with old classifiers
            boolean useRoot = objStatModel.isJsonObject() && objStatModel.getAsJsonObject().has("format");

            StatModel model = null;

            if (RTrees.class.getSimpleName().equals(className))
                model = RTrees.create();
            else if (DTrees.class.getSimpleName().equals(className))
                model = DTrees.create();
            else if (Boost.class.getSimpleName().equals(className))
                model = Boost.create();
            else if (EM.class.getSimpleName().equals(className))
                model = EM.create();
            else if (LogisticRegression.class.getSimpleName().equals(className))
                model = LogisticRegression.create();
            else if (SVM.class.getSimpleName().equals(className))
                model = SVM.create();
            else if (SVMSGD.class.getSimpleName().equals(className))
                model = SVMSGD.create();
            else if (NormalBayesClassifier.class.getSimpleName().equals(className))
                model = NormalBayesClassifier.create();
            else if (KNearest.class.getSimpleName().equals(className))
                model = KNearest.create();
            else if (ANN_MLP.class.getSimpleName().equals(className))
                model = ANN_MLP.create();
            else
                throw new IOException("Unknown StatModel class name " + className);

            // Load from the JSON data
            try (FileStorage fs = new FileStorage()) {
                fs.open(modelString, FileStorage.FORMAT_JSON + FileStorage.READ + FileStorage.MEMORY);
                FileNode fn;
                if (useRoot)
                    fn = fs.root();
                else
                    fn = fs.getFirstTopLevelNode();
                model.read(fn);
                return model;
            }
        } finally {
            in.setStrictness(strictness);
        }
    }

}
