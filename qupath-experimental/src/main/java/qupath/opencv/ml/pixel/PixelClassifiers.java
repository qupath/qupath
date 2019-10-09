package qupath.opencv.ml.pixel;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;

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
				.registerSubtype(OpenCVPixelClassifierDNN.class)
				.registerSubtype(SimplePixelClassifier.class);
		
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

	/**
	 * Create a PixelClassifier that applies a threshold to a single image channel at a specified resolution.
	 * 
	 * @param transform transform to apply to input pixels
	 * @param inputResolution resolution at which to apply the threshold
	 * @param threshold threshold value to apply
	 * @param leqThreshold classification for pixels less than or equal to the threshold
	 * @param gtThreshold classification for pixels greater than the threshold
	 * @return
	 */
	public static PixelClassifier createThresholdingClassifier(
			ColorTransform transform,
			PixelCalibration inputResolution,
			double threshold,
			PathClass leqThreshold,
			PathClass gtThreshold) {
		return new SimplePixelClassifier(transform, inputResolution, threshold, leqThreshold, gtThreshold);
	}

}
