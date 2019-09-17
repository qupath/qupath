package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVDNN;
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
				RuntimeTypeAdapterFactory.of(FeatureCalculator.class, typeName);
		
		private static void registerSubtype(Class<? extends FeatureCalculator> cls) {
			featureCalculatorTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return featureCalculatorTypeAdapter.create(gson, type);
		}
		
	}
	
	
	public static FeatureCalculator<BufferedImage> createPatchFeatureCalculator(double pixelSizeMicrons, int size, int...inputChannels) {
		return new ExtractNeighborsFeatureCalculator(pixelSizeMicrons, size, inputChannels);
	}
	
	
	public static FeatureCalculator<BufferedImage> createMultiscaleFeatureCalculator(PixelCalibration cal, int[] channels, double[] sigmaValues, double localNormalizeSigma, boolean do3D, MultiscaleFeature... features) {
		return new MultiscaleFeatureCalculator(cal, channels, sigmaValues, localNormalizeSigma, do3D, features);
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
		
	}
	
	

}
