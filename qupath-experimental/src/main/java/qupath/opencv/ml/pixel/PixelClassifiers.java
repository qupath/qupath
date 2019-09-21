package qupath.opencv.ml.pixel;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.classifiers.pixel.PixelClassifier;

/**
 * Static methods and classes for working with pixel classifiers.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifiers {
	
	/**
	 * Support for serializing PixelClassifiers to JSON, via Gson.
	 */
	private static class PixelClassifierTypeAdapterFactory implements TypeAdapterFactory {

		PixelClassifierTypeAdapterFactory() {}
		
		private static String typeName = "pixel_classifier_type";
		
		private final static RuntimeTypeAdapterFactory<PixelClassifier> pixelClassifierTypeAdapter = 
				RuntimeTypeAdapterFactory.of(PixelClassifier.class, typeName)
				.registerSubtype(OpenCVPixelClassifier.class)
				.registerSubtype(OpenCVPixelClassifierDNN.class);
		
		/**
		 * Register that a specific PixelClassifier implementation can be serialized to Json.
		 * @param cls
		 */
		public static void registerSubtype(Class<? extends PixelClassifier> cls) {
			pixelClassifierTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return pixelClassifierTypeAdapter.create(gson, type);
		}
		
	}
	
	private final static TypeAdapterFactory factory = new PixelClassifierTypeAdapterFactory();
	
	public static TypeAdapterFactory getTypeAdapterFactory() {
		return factory;
	}

}
