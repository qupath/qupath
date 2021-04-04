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

package qupath.opencv.ml.pixel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

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
		
		private final static SubTypeAdapterFactory<PixelClassifier> pixelClassifierTypeAdapter = 
				GsonTools.createSubTypeAdapterFactory(PixelClassifier.class, typeName)
				.registerSubtype(OpenCVPixelClassifier.class);
		
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
	
	/**
	 * Get the {@link TypeAdapterFactory} default used for {@link PixelClassifier} objects.
	 * This is intended for internal use by QuPath, and should be registered with {@link GsonTools}.
	 * @return
	 */
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
		try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GsonTools.getInstance(true).toJson(classifier, writer);
		}
	}
	
	
	/**
	 * Convert an {@link ImageDataOp} into a simple classifier by adding an interpretation to the output labels.
	 * @param op
	 * @param inputResolution
	 * @param classifications
	 * @return
	 */
	public static PixelClassifier createClassifier(
			ImageDataOp op,
			PixelCalibration inputResolution,
			Map<Integer, PathClass> classifications) {
		
		var metadata = new PixelClassifierMetadata.Builder()
				.inputResolution(inputResolution)
				.classificationLabels(classifications)
				.inputShape(512, 512)
				.setChannelType(ImageServerMetadata.ChannelType.CLASSIFICATION)
				.build();
		
		return new OpenCVPixelClassifier(op, metadata);
	}

	/**
	 * Create a PixelClassifier based on an OpenCV StatModel and feature calculator.
	 * 
	 * @param statModel
	 * @param calculator
	 * @param metadata
	 * @param do8Bit
	 * @return
	 */
	public static PixelClassifier createClassifier(OpenCVStatModel statModel, ImageDataOp calculator, PixelClassifierMetadata metadata, boolean do8Bit) {
		var ops = new ArrayList<ImageOp>();
		boolean outputProbability = metadata.getOutputType() == ChannelType.PROBABILITY || metadata.getOutputType() == ChannelType.MULTICLASS_PROBABILITY;
		ops.add(ImageOps.ML.statModel(statModel, outputProbability));
		if (metadata.getOutputType() == ChannelType.PROBABILITY) {
			if (do8Bit)
				ops.add(ImageOps.Normalize.channelSum(255.0));
			else
				ops.add(ImageOps.Normalize.channelSum(1.0));				
		}
		if (do8Bit) {
			ops.add(ImageOps.Core.ensureType(PixelType.UINT8));
		}
		var op = calculator.appendOps(ops.toArray(ImageOp[]::new));
		return new OpenCVPixelClassifier(op, metadata);
	}
	

}