/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.opencv.io;

import java.io.IOException;
import java.util.Map;

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
		if (Scalar.class == cls)
			return (TypeAdapter<T>)new ScalarTypeAdapter();
		if (Size.class == cls)
			return (TypeAdapter<T>)new SizeTypeAdapter();
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
	
	
	private static class SizeTypeAdapter extends TypeAdapter<Size> {

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
	
	
	private static class ScalarTypeAdapter extends TypeAdapter<Scalar> {

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
	
	
	/**
	 * TypeAdapter that helps include OpenCV-based objects within a Java object being serialized to JSON.
	 * @param <T> 
	 */
	public abstract static class OpenCVTypeAdapter<T> extends TypeAdapter<T> {
		
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

		@Override
		public StatModel read(JsonReader in) throws IOException {
			
			boolean lenient = in.isLenient();
			
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
				in.setLenient(lenient);
			}
		}
		
	}

}