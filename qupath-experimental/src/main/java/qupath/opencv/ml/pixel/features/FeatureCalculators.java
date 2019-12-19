package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVDNN;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;
import qupath.opencv.ml.pixel.features.MultiscaleFeatureCalculator.TransformedFeatureComputer;
import qupath.opencv.tools.LocalNormalization.LocalNormalizationType;
import qupath.opencv.tools.LocalNormalization.SmoothingScale;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;

public class FeatureCalculators {
	
	private static Logger logger = LoggerFactory.getLogger(FeatureCalculators.class);
	
	public static void initialize() {
		FeatureCalculatorTypeAdapterFactory.registerSubtype(OpenCVFeatureCalculatorDNN.class);
	}
	
	static class FeatureCalculatorTypeAdapterFactory implements TypeAdapterFactory {

		public FeatureCalculatorTypeAdapterFactory() {}
		
		private static String typeName = "feature_calculator_type";
		
		private final static RuntimeTypeAdapterFactory<FeatureCalculator> featureCalculatorTypeAdapter = 
				RuntimeTypeAdapterFactory.of(FeatureCalculator.class, typeName)
					.registerSubtype(ExtractNeighborsFeatureCalculator.class)
					.registerSubtype(MultiscaleFeatureCalculator.class)
					.registerSubtype(ColorTransformFeatureCalculator.class);
		
		private static void registerSubtype(Class<? extends FeatureCalculator> cls) {
			featureCalculatorTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return featureCalculatorTypeAdapter.create(gson, type);
		}
		
	}
	
	private final static TypeAdapterFactory factory = new FeatureCalculatorTypeAdapterFactory();
	
	public static TypeAdapterFactory getTypeAdapterFactory() {
		return factory;
	}
	
	
	public static FeatureCalculator<BufferedImage> createColorTransformFeatureCalculator(ColorTransform... transforms) {
		return new ColorTransformFeatureCalculator(transforms);
	}
	
	public static FeatureCalculator<BufferedImage> createPatchFeatureCalculator(int size, String...inputChannels) {
		return new ExtractNeighborsFeatureCalculator(size, Arrays.stream(inputChannels).map(c -> ColorTransforms.createChannelExtractor(c)).toArray(ColorTransform[]::new));
	}

	
	public static FeatureCalculator<BufferedImage> createMultiscaleFeatureCalculator(
			String[] channels,
			double[] sigmaValues, LocalNormalizationType localNormalization, boolean do3D, MultiscaleFeature... features) {
		return createMultiscaleFeatureCalculator(
				Arrays.stream(channels).map(c -> ColorTransforms.createChannelExtractor(c)).collect(Collectors.toList()),
				sigmaValues, localNormalization, do3D, features);
	}
	
	public static FeatureCalculator<BufferedImage> createMultiscaleFeatureCalculator(
			Collection<ColorTransform> transforms, double[] sigmaValues, LocalNormalizationType localNormalization, boolean do3D, MultiscaleFeature... features) {
		List<SmoothingScale> scales = new ArrayList<>();
		for (double sigma : sigmaValues) {
			if (do3D)
				scales.add(SmoothingScale.get3DIsotropic(sigma));				
			else
				scales.add(SmoothingScale.get2D(sigma));				
		}
		
		List<TransformedFeatureComputer> computers = new ArrayList<>();
		for (var transform : transforms) {
			var builder = new TransformedFeatureComputer.Builder(transform);
			for (var scale : scales) {
				builder.addFeatures(scale, features);
			}
			computers.add(builder.build());
		}
		
		var calculator = new MultiscaleFeatureCalculator.Builder()
				.addFeatures(computers)
				.localNormalization(localNormalization)
				.build();
		
//		System.err.println(GsonTools.getInstance(true).toJson(calculator));
		
		return calculator;
	}
	
	/**
	 * Create a FeatureCalculator that only applies color transforms and local normalization.
	 * @param transforms
	 * @param localNormalization
	 * @return
	 */
	public static FeatureCalculator<BufferedImage> createNormalizingFeatureCalculator(
			Collection<ColorTransform> transforms, LocalNormalizationType localNormalization) {
		
		List<TransformedFeatureComputer> computers = new ArrayList<>();
		for (var transform : transforms) {
			var builder = new TransformedFeatureComputer.Builder(transform);
			builder.addIdentityFeature();
			computers.add(builder.build());
		}
		
		return new MultiscaleFeatureCalculator.Builder()
				.addFeatures(computers)
				.localNormalization(localNormalization)
				.build();
	}
	
	
	public static FeatureCalculator<BufferedImage> createDNNFeatureCalculator(
    		final OpenCVDNN model, int inputWidth, int inputHeight) {
		logger.warn("DNN support is a work in progress... proceed with caution");
		return new OpenCVFeatureCalculatorDNN(model, inputWidth, inputHeight);
	}
	
	
	static class MergedFeatureCalculator<T> implements FeatureCalculator<T> {
		
		private List<FeatureCalculator<T>> calculators;
		private ImmutableDimension inputSize;
		
		MergedFeatureCalculator(ImmutableDimension inputSize, Collection<FeatureCalculator<T>> calculators) {
			this.inputSize = inputSize;
			this.calculators = new ArrayList<>(calculators);
		}

		@Override
		public List<PixelFeature> calculateFeatures(ImageData<T> imageData, RegionRequest request) throws IOException {
			List<PixelFeature> features = new ArrayList<>();
			for (var calculator : calculators)
				features.addAll(calculator.calculateFeatures(imageData, request));
			return features;
		}

		@Override
		public ImmutableDimension getInputSize() {
			return inputSize;
		}

		@Override
		public boolean supportsImage(ImageData<T> imageData) {
			for (var calculator : calculators) {
				if (!calculator.supportsImage(imageData))
					return false;
			}
			return true;
		}
		
	}
	
	

}
