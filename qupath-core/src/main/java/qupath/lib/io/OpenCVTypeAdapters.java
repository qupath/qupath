package qupath.lib.io;

import java.io.IOException;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_ml.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;


/**
 * Helper classes for combining OpenCV's JSON serialization with Gson's.
 * <p>
 * Sample use:
 * <pre>
 * Gson gson = new GsonBuilder()
 * 				.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
 * 				.setPrettyPrinting()
 * 				.create();
 * 
 * Mat mat1 = Mat.eye(3, 3, CV_32F1).asMat();
 * String json = gson.toJson(mat1);
 * Mat mat2 = gson.fromJson(json, Mat.class);
 * </pre>
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVTypeAdapters {
	
	/**
	 * Get a TypeAdapterFactory to pass to a GsonBuilder to aid with serializing OpenCV objects 
	 * (e.g. Mat, StatModel).
	 * 
	 * @return
	 */
	public static TypeAdapterFactory getOpenCVTypeAdaptorFactory() {
		return new OpenCVTypeAdaptorFactory();
	}
	
	
	/**
	 * Get a TypeAdapter to pass to a GsonBuilder for a specific supported OpenCV class, 
	 * i.e. Mat, SparseMat or StatModel.
	 * 
	 * @param cls
	 * @return the required TypeAdaptor, or null if no supported adapter is available for the class.
	 */
	@SuppressWarnings("unchecked")
	public static <T> TypeAdapter<T> getTypeAdaptor(Class<T> cls) {
		if (Mat.class == cls)
			return (TypeAdapter<T>)new MatTypeAdapter();
		if (SparseMat.class == cls)
			return (TypeAdapter<T>)new SparseMatTypeAdapter();
		if (StatModel.class.isAssignableFrom(cls))
			return (TypeAdapter<T>)new StatModelTypeAdapter();
		return null;
	}
	
	
	/**
	 * TypeAdapterFactory that helps make OpenCV's serialization methods more compatible with custom JSON/Gson serialization.
	 */
	public static class OpenCVTypeAdaptorFactory implements TypeAdapterFactory {

		@SuppressWarnings("unchecked")
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return getTypeAdaptor((Class<T>)type.getRawType());
		}
		
	}
	
	
	/**
	 * TypeAdapter that helps include OpenCV-based objects within a Java object being serialized to JSON.
	 */
	public static abstract class OpenCVTypeAdapter<T> extends TypeAdapter<T> {
		
		Gson gson = new GsonBuilder().setLenient().create();

		@Override
		public void write(JsonWriter out, T value) throws IOException {
			boolean lenient = out.isLenient();
			String json = null;
			try (FileStorage fs = new FileStorage()) {
				fs.open("anything.json", FileStorage.FORMAT_JSON + FileStorage.WRITE + FileStorage.MEMORY);
				write(fs, value);
				json = fs.releaseAndGetString().getString().trim();
				
				JsonObject element = gson.fromJson(json.trim(), JsonObject.class);
				gson.toJson(element, out);
				
//				out.jsonValue(json);
//			} catch (Throwable e) {
//				e.printStackTrace();
			} finally {
				out.setLenient(lenient);
			}
		}
		
		abstract void write(FileStorage fs, T value);
		
		abstract T read(FileStorage fs);

		@Override
		public T read(JsonReader in) throws IOException {
			boolean lenient = in.isLenient();
			try {
				JsonElement element = JsonParser.parseReader(in);
				JsonObject obj = element.getAsJsonObject();
				String inputString = obj.toString();//obj.get("mat").toString();
				try (FileStorage fs = new FileStorage()) {
					fs.open(inputString, FileStorage.FORMAT_JSON + FileStorage.READ + FileStorage.MEMORY);
					return read(fs);
				}
			} finally {
				in.setLenient(lenient);
			}
		}
		
	}
	
	
	private static class MatTypeAdapter extends OpenCVTypeAdapter<Mat> {

		@Override
		void write(FileStorage fs, Mat value) {
			opencv_core.write(fs, "mat", value);
		}

		@Override
		Mat read(FileStorage fs) {
			return fs.getFirstTopLevelNode().mat();
		}
		
	}
	
	private static class SparseMatTypeAdapter extends OpenCVTypeAdapter<SparseMat> {

		@Override
		void write(FileStorage fs, SparseMat value) {
			opencv_core.write(fs, "sparsemat", value);
		}

		@Override
		SparseMat read(FileStorage fs) {
			SparseMat mat = new SparseMat();
			opencv_core.read(fs.getFirstTopLevelNode(), mat);
			return mat;
		}
		
	}
	
	
	private static class StatModelTypeAdapter extends TypeAdapter<StatModel> {
		
		Gson gson = new GsonBuilder().setLenient().create();

		@Override
		public void write(JsonWriter out, StatModel value) throws IOException {
			try (FileStorage fs = new FileStorage()) {
				fs.open("anything.json", FileStorage.FORMAT_JSON + FileStorage.WRITE + FileStorage.MEMORY);
				value.write(fs);
				String json = fs.releaseAndGetString().getString();
				
				out.beginObject();
				out.name("class");
				out.value(value.getClass().getSimpleName());
				out.name("statmodel");
				
				// jsonValue works for JsonWriter but not JsonTreeWriter, so we try to work around this...
				JsonObject element = gson.fromJson(json.trim(), JsonObject.class);
				gson.toJson(element, out);
//				out.jsonValue(obj.toString());
//				out.jsonValue(json);
				out.endObject();
			}
		}

		@Override
		public StatModel read(JsonReader in) throws IOException {
			
			boolean lenient = in.isLenient();
			
			try {
				JsonElement element = JsonParser.parseReader(in);
				
				JsonObject obj = element.getAsJsonObject();
				
				String className = obj.get("class").getAsString();
				
				// It's a bit roundabout... but toString() gives Strings that are too long and unsupported 
				// by OpenCV, so we take another tour through Gson.
				String modelString = new GsonBuilder().setPrettyPrinting().create().toJson(obj.get("statmodel"));
				
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
					FileNode fn = fs.root();
					model.read(fn);
					return model;
				}
			} finally {
				in.setLenient(lenient);
			}
		}
		
	}

}
