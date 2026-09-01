/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.SparseMat;
import org.bytedeco.opencv.opencv_ml.StatModel;

import qupath.lib.io.GsonTools;
import qupath.opencv.ml.models.statmodel.TrainableStatModel;
import qupath.opencv.ml.models.PredictionModel;
import qupath.opencv.ml.models.TrainableModel;


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

	private static final GsonTools.SubTypeAdapterFactory<PredictionModel> predictionModelTypeAdapterFactory = GsonTools.createSubTypeAdapterFactory(
			PredictionModel.class,
			"model-type"
	).registerSubtype(TrainableStatModel.class);

	private static final GsonTools.SubTypeAdapterFactory<TrainableModel> trainableModelTypeAdapterFactory = GsonTools.createSubTypeAdapterFactory(
			TrainableModel.class,
			"model-type"
	).registerSubtype(TrainableStatModel.class);

	/**
	 * Register a new JSON-serializable {@link PredictionModel} or {@link TrainableModel},
	 * using the simple class name as "model-type".
	 * @param cls the JSON-serializable class
	 * @return
	 */
	public static void registerPredictionModel(Class<? extends PredictionModel> cls) {
		registerPredictionModel(cls, cls.getSimpleName());
	}

	/**
	 * Register a new JSON-serializable {@link PredictionModel} or {@link TrainableModel}.
	 * @param cls the JSON-serializable class
	 * @param label the "model-type" label; note that this must be unique.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static void registerPredictionModel(Class<? extends PredictionModel> cls, String label) {
		predictionModelTypeAdapterFactory.registerSubtype(cls, label);
		if (TrainableModel.class.isAssignableFrom(cls)) {
			registerTrainableModel((Class)cls, label);
		}
	}

	private static void registerTrainableModel(Class<? extends TrainableModel> cls, String label) {
		trainableModelTypeAdapterFactory.registerSubtype(cls, label);
	}
	
	/**
	 * Get a TypeAdapterFactory to pass to a GsonBuilder to aid with serializing OpenCV objects 
	 * (e.g. Mat, StatModel).
	 * 
	 * @return the type adapter factory
	 */
	public static TypeAdapterFactory getOpenCVTypeAdaptorFactory() {
		return new OpenCVTypeAdaptorFactory();
	}
	

	
	/**
	 * TypeAdapterFactory that helps make OpenCV's serialization methods more compatible with custom JSON/Gson serialization.
	 */
	public static class OpenCVTypeAdaptorFactory implements TypeAdapterFactory {

		@SuppressWarnings("unchecked")
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			var adaptor = getTypeAdaptor((Class<T>)type.getRawType());
			if (adaptor != null)
				return adaptor;
			if (TrainableModel.class.isAssignableFrom(type.getRawType()))
				return trainableModelTypeAdapterFactory.create(gson, type);
			if (PredictionModel.class.isAssignableFrom(type.getRawType()))
				return predictionModelTypeAdapterFactory.create(gson, type);
			return null;
		}

		private static <T> TypeAdapter<T> getTypeAdaptor(Class<T> cls) {
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


	}


}