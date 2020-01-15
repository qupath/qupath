package qupath.opencv.ml.objects.features;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.Normalizer;
import qupath.opencv.ml.Preprocessing.PCAProjector;


public class FeatureExtractors {
	
	static class FeatureExtractorTypeAdapterFactory implements TypeAdapterFactory {

		public FeatureExtractorTypeAdapterFactory() {}
		
		private static String typeName = "feature_extractor_type";
		
		private final static RuntimeTypeAdapterFactory<FeatureExtractor> featureCalculatorTypeAdapter = 
				RuntimeTypeAdapterFactory.of(FeatureExtractor.class, typeName)
					.registerSubtype(DefaultFeatureExtractor.class)
					.registerSubtype(NormalizedFeatureExtractor.class)
					.registerSubtype(PCAProjectFeatureExtractor.class);
		
		private static void registerSubtype(Class<? extends FeatureExtractor> cls) {
			featureCalculatorTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return featureCalculatorTypeAdapter.create(gson, type);
		}
		
	}
	
	private final static TypeAdapterFactory factory = new FeatureExtractorTypeAdapterFactory();
	
	public static TypeAdapterFactory getTypeAdapterFactory() {
		return factory;
	}
	
	/**
	 * Create a {@link FeatureExtractor} that determines features for the {@link MeasurementList} of the {@link PathObject}.
	 * @param measurements list containing the measurement names
	 * @return the new {@link FeatureExtractor}
	 */
	public static FeatureExtractor createMeasurementListFeatureExtractor(List<String> measurements) {
		return new DefaultFeatureExtractor(measurements);
	}
	
	/**
	 * Wrap an existing {@link FeatureExtractor} to apply normalization prior to returning the values.
	 * @param extractor original (base) feature extractor
	 * @param normalizer {@link Normalizer} to apply to initial features
	 * @return the new {@link FeatureExtractor}
	 */
	public static FeatureExtractor createNormalizingFeatureExtractor(FeatureExtractor extractor, Normalizer normalizer) {
		return new NormalizedFeatureExtractor(extractor, normalizer);
	}
	
	
	/**
	 * Wrap an existing {@link FeatureExtractor} to apply PCA projection prior to returning the values.
	 * @param extractor original (base) feature extractor
	 * @param pca {@link PCAProjector} to apply to initial features
	 * @return the new {@link FeatureExtractor}
	 */
	public static FeatureExtractor createPCAProjectFeatureExtractor(FeatureExtractor extractor, PCAProjector pca) {
		return new PCAProjectFeatureExtractor(extractor, pca);
	}

}
