package qupath.lib.classifiers.pixel;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

public class PixelClassifiers {
	
	public static class PixelClassifierTypeAdapterFactory implements TypeAdapterFactory {

		public PixelClassifierTypeAdapterFactory() {}
		
		private static String typeName = "pixel_classifier_type";
		
		private final static RuntimeTypeAdapterFactory<PixelClassifier> pixelClassifierTypeAdapter = 
				RuntimeTypeAdapterFactory.of(PixelClassifier.class, typeName);
		
		public static void registerSubtype(Class<? extends PixelClassifier> cls) {
			pixelClassifierTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return pixelClassifierTypeAdapter.create(gson, type);
		}
		
	}

}
