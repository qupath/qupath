package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.io.GsonTools;
import qupath.opencv.ml.pixel.ValueToClassification.ThresholdClassifier;
import qupath.opencv.ml.pixel.features.FeatureCalculator;
import qupath.opencv.ml.pixel.features.FeatureCalculators;
import qupath.opencv.processor.Transformer;
import qupath.opencv.processor.Transformers.ImageDataTransformer;

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
	 * Read a standard pixel classifier from a file.
	 * @param path the file containing the classifier
	 * @return 
	 * @throws IOException
	 */
	public static PixelClassifier readClassifier(Path path) throws IOException {
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return GsonTools.getInstance().fromJson(reader, PixelClassifier.class);
		}
	}
	
	/**
	 * Write a pixel classifier to a file.
	 * @param classifier
	 * @param path
	 * @throws IOException
	 */
	public static void writeClassifier(PixelClassifier classifier, Path path) throws IOException {
		try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
			GsonTools.getInstance(true).toJson(classifier, writer);
		}
	}
	
	
	public static PixelClassifier createThresholdingClassifier(
			ImageDataTransformer transformer,
			PixelCalibration inputResolution,
			ThresholdClassifier thresholder) {
		return new SimplePixelClassifier(transformer, inputResolution, thresholder);
	}

//	/**
//	 * Create a PixelClassifier that applies a threshold to a single image channel at a specified resolution.
//	 * 
//	 * @param transform transform to apply to input pixels
//	 * @param inputResolution resolution at which to apply the threshold
//	 * @param thresholder thresholder used to determine classifications
//	 * @return
//	 */
//	public static PixelClassifier createThresholdingClassifier(
//			ColorTransform transform,
//			PixelCalibration inputResolution,
//			ThresholdClassifier thresholder) {
//		return createThresholdingClassifier(
//				FeatureCalculators.createColorTransformFeatureCalculator(transform),
//				inputResolution, thresholder);
//	}
//	
//	public static PixelClassifier createThresholdingClassifier(
//			FeatureCalculator<BufferedImage> featureCalculator,
//			PixelCalibration inputResolution,
//			ThresholdClassifier thresholder) {
//		return new SimplePixelClassifier(featureCalculator, inputResolution, thresholder);
//	}

}
