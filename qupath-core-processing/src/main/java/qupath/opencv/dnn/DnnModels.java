/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


package qupath.opencv.dnn;

import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;

/**
 * Helper class for building new {@linkplain DnnModel DnnModels}.
 * <p>
 * This includes a {@link ServiceLoader} to support adding new implementations 
 * via extensions.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class DnnModels {
	
	private static final Logger logger = LoggerFactory.getLogger(DnnModels.class);
	
	@SuppressWarnings("rawtypes")
	private static final SubTypeAdapterFactory<DnnModel> dnnAdapter;
	
	@SuppressWarnings("rawtypes")
	private static final SubTypeAdapterFactory<BlobFunction> blobAdapter;
	
	@SuppressWarnings("rawtypes")
	private static final SubTypeAdapterFactory<PredictionFunction> predictionAdapter;
	
	static {
		
		blobAdapter = GsonTools.createSubTypeAdapterFactory(BlobFunction.class, "blob_fun")
				.registerSubtype(DefaultBlobFunction.class);
		
		predictionAdapter = GsonTools.createSubTypeAdapterFactory(PredictionFunction.class, "prediction_fun");
		
		dnnAdapter = GsonTools.createSubTypeAdapterFactory(DnnModel.class, "dnn_model")
				.registerSubtype(DefaultDnnModel.class)
				.registerSubtype(OpenCVDnn.class);
		
		ObjectClassifiers.ObjectClassifierTypeAdapterFactory.registerSubtype(OpenCVModelObjectClassifier.class);
		ObjectClassifiers.ObjectClassifierTypeAdapterFactory.registerSubtype(DnnObjectClassifier.class);

		GsonTools.getDefaultBuilder()
				.registerTypeAdapterFactory(blobAdapter)
				.registerTypeAdapterFactory(predictionAdapter)
				.registerTypeAdapterFactory(dnnAdapter);
		
	}
	
	
	/**
	 * Register a new {@link DnnModel} class for JSON serialization/deserialization.
	 * @param <T>
	 * @param subtype
	 * @param name
	 */
	@SuppressWarnings("rawtypes")
	public static <T extends DnnModel> void registerDnnModel(Class<T> subtype, String name) {
		if (name == null || name.isBlank())
			dnnAdapter.registerSubtype(subtype);
		else
			dnnAdapter.registerSubtype(subtype, name);
	}
	
	
	@SuppressWarnings("rawtypes")
	private static ServiceLoader<DnnModelBuilder> serviceLoader = ServiceLoader.load(DnnModelBuilder.class);
	
	
	/**
	 * Build a {@link DnnModel} from the given parameters.
	 * This queries all available {@linkplain DnnModelBuilder DnnModelBuilders} through a service loader.
	 * @param <T>
	 * @param params
	 * @return a new DnnModel, or null if no model could be built
	 */
	public static <T> DnnModel<T> buildModel(DnnModelParams params) {
		synchronized (serviceLoader) {
			for (DnnModelBuilder<?> builder : serviceLoader) {
				try {
					var model = builder.buildModel(params);
					if (model != null)
						return (DnnModel<T>)model;
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			}
		}
		return null;
	}
	

}
