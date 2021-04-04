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

package qupath.opencv.ml.objects.features;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.objects.features.Preprocessing.PCAProjector;

/**
 * Helper class for extracting features from objects, used along with {@linkplain ObjectClassifier ObjectClassifiers}.
 * 
 * @author Pete Bankhead
 *
 */
public class FeatureExtractors {
	
	static class FeatureExtractorTypeAdapterFactory implements TypeAdapterFactory {

		public FeatureExtractorTypeAdapterFactory() {}
		
		private static String typeName = "feature_extractor_type";
		
		private final static SubTypeAdapterFactory<FeatureExtractor> featureCalculatorTypeAdapter = 
				GsonTools.createSubTypeAdapterFactory(FeatureExtractor.class, typeName)
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
	
	/**
	 * Get the {@link TypeAdapterFactory} default used for {@link FeatureExtractor} objects.
	 * This is intended for internal use by QuPath, and should be registered with {@link GsonTools}.
	 * @return
	 */
	public static TypeAdapterFactory getTypeAdapterFactory() {
		return factory;
	}
	
	/**
	 * Create a {@link FeatureExtractor} that determines features for the {@link MeasurementList} of the {@link PathObject}.
	 * @param measurements list containing the measurement names
	 * @return the new {@link FeatureExtractor}
	 */
	public static <T> FeatureExtractor<T> createMeasurementListFeatureExtractor(List<String> measurements) {
		return new DefaultFeatureExtractor<>(measurements);
	}
	
	/**
	 * Wrap an existing {@link FeatureExtractor} to apply normalization prior to returning the values.
	 * @param extractor original (base) feature extractor
	 * @param normalizer {@link Normalizer} to apply to initial features
	 * @return the new {@link FeatureExtractor}
	 */
	public static <T> FeatureExtractor<T> createNormalizingFeatureExtractor(FeatureExtractor<T> extractor, Normalizer normalizer) {
		return new NormalizedFeatureExtractor<>(extractor, normalizer);
	}
	
	
	/**
	 * Wrap an existing {@link FeatureExtractor} to apply PCA projection prior to returning the values.
	 * @param extractor original (base) feature extractor
	 * @param pca {@link PCAProjector} to apply to initial features
	 * @return the new {@link FeatureExtractor}
	 */
	public static <T> FeatureExtractor<T> createPCAProjectFeatureExtractor(FeatureExtractor<T> extractor, PCAProjector pca) {
		return new PCAProjectFeatureExtractor<>(extractor, pca);
	}

}