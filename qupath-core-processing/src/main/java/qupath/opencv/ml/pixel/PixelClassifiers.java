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

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
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
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;
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
		
		private static final SubTypeAdapterFactory<PixelClassifier> pixelClassifierTypeAdapter = 
				GsonTools.createSubTypeAdapterFactory(PixelClassifier.class, typeName)
				.registerSubtype(OpenCVPixelClassifier.class)
				.registerSubtype(ThresholdPixelClassifier.class);
		
		private static final TypeAdapterFactory classifierFunctionTypeAdapter = GsonTools.createSubTypeAdapterFactory(ClassifierFunction.class, "classifier_function")
				.registerSubtype(ClassifierGreaterEquals.class)
				.registerSubtype(ClassifierGreater.class)
				.registerSubtype(MultiThresholdClassifierFunction.class);

		
		/**
		 * Register that a specific PixelClassifier implementation can be serialized to Json.
		 * @param cls
		 */
		public static void registerSubtype(Class<? extends PixelClassifier> cls) {
			pixelClassifierTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			var adapter = pixelClassifierTypeAdapter.create(gson, type);
			if (adapter == null)
				return classifierFunctionTypeAdapter.create(gson, type);
			else
				return adapter;
		}
		
	}
	
	private static final TypeAdapterFactory factory = new PixelClassifierTypeAdapterFactory();
		
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
			GsonTools.getInstance(true).toJson(classifier, PixelClassifier.class, writer);
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
	 * Convert an {@link ImageDataOp} into a simple classifier.
	 * @param op
	 * @param metadata
	 * @return
	 */
	public static PixelClassifier createClassifier(ImageDataOp op, PixelClassifierMetadata metadata) {
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
	
	
	
static class ThresholdClassifierBuilder {
		
		private PixelCalibration inputResolution;
		private ClassifierFunction fun;
		private Map<Integer, PathClass> labels;
		
		public PixelClassifier build() {
			var metadata = new PixelClassifierMetadata.Builder()
					.classificationLabels(labels)
					.setChannelType(ChannelType.CLASSIFICATION)
					.inputResolution(inputResolution)
					.build();
			
			return new ThresholdPixelClassifier(metadata, fun);
		}
		
	}
	
	static interface ClassifierFunction {
		
		public int predict(float[] input);
		
	}
	
	abstract static class SingleThresholdClassifierFunction implements ClassifierFunction {
		
		private int band;
		protected float threshold;
		
		SingleThresholdClassifierFunction(int band, float threshold) {
			this.band = band;
			this.threshold = threshold;
		}

		@Override
		public int predict(float[] input) {
			return predict(input[band]);
		}
		
		protected abstract int predict(float input);
		
	}
	
	static class ClassifierGreaterEquals extends SingleThresholdClassifierFunction {
		
		ClassifierGreaterEquals(int band, float threshold) {
			super(band, threshold);
		}

		@Override
		protected int predict(float input) {
			if (Float.isNaN(input))
				return 0;
			return input >= threshold ? 1 : 0;
		}

		
	}
	
	static class ClassifierGreater extends SingleThresholdClassifierFunction {
		
		ClassifierGreater(int band, float threshold) {
			super(band, threshold);
		}

		@Override
		protected int predict(float input) {
			if (Float.isNaN(input))
				return 0;
			return input > threshold ? 1 : 0;
		}
		
	}
	
	static class MultiThresholdClassifierFunction implements ClassifierFunction {
		
		private int n;
		private int[] bands;
		protected float[] thresholds;
		
		MultiThresholdClassifierFunction(Map<Integer, ? extends Number> thresholds) {
			this.n = thresholds.size();
			this.bands = new int[n];
			this.thresholds = new float[n];
			int i = 0;
			for (var entry : thresholds.entrySet()) {
				this.bands[i] = entry.getKey();
				this.thresholds[i] = entry.getValue().floatValue();
				i++;
			}
		}
		
		MultiThresholdClassifierFunction(int[] bands, float[] thresholds) {
			this.n = bands.length;
			this.bands = bands.clone();
			this.thresholds = thresholds.clone();
		}

		@Override
		public int predict(float[] input) {
			for (int i = 0; i < n; i++) {
				float val = input[bands[i]];
				if (Float.isNaN(val) || val < thresholds[i])
					return 0;
			}
			return 1;
		}
				
	}
	
	
	/**
	 * Create a new {@link PixelClassifier} that applies a threshold to one channel of an image.
	 * 
	 * @param inputResolution resolution at which the threshold should be applied
	 * @param channel the channel to threshold (zero-based)
	 * @param threshold the threshold value to apply
	 * @param below the classification for pixels below the threshold (must not be null)
	 * @param aboveEquals the classification for pixels greater than or equal to the threshold (must not be null)
	 * @return the pixel classifier
	 */
	public static PixelClassifier createThresholdClassifier(PixelCalibration inputResolution, int channel, double threshold, PathClass below, PathClass aboveEquals) {
		var fun = createThresholdFunction(channel, threshold);
		var labels = Map.of(0, below, 1, aboveEquals);
		return createThresholdClassifier(inputResolution, labels, fun);
	}
	
	
	/**
	 * Create a new {@link PixelClassifier} that applies a threshold to one or more channels of an image.
	 * 
	 * @param inputResolution resolution at which the threshold should be applied
	 * @param thresholds map between channel numbers (zero-based) and thresholds
	 * @param below the classification for pixels whose values are below the threshold in any channel
	 * @param aboveEquals the classification for pixels whose values are greater than or equal to the threshold in all channels
	 * @return the pixel classifier
	 */
	public static PixelClassifier createThresholdClassifier(PixelCalibration inputResolution, Map<Integer, ? extends Number> thresholds, PathClass below, PathClass aboveEquals) {
		var fun = createThresholdFunction(thresholds);
		var labels = Map.of(0, below, 1, aboveEquals);
		return createThresholdClassifier(inputResolution, labels, fun);
	}
	
	
	static ClassifierFunction createThresholdFunction(Map<Integer, ? extends Number> thresholds) {
		if (thresholds.size() == 1) {
			var entry = thresholds.entrySet().iterator().next();
			return createThresholdFunction(entry.getKey(), entry.getValue().doubleValue());
		}
		return new MultiThresholdClassifierFunction(thresholds);
	}
	
	static ClassifierFunction createThresholdFunction(int channel, double threshold) {
		return new ClassifierGreaterEquals(channel, (float)threshold);
	}
	
	
	static PixelClassifier createThresholdClassifier(PixelCalibration inputResolution, Map<Integer, PathClass> labels, ClassifierFunction fun) {
		
		var metadata = new PixelClassifierMetadata.Builder()
				.classificationLabels(labels)
				.setChannelType(ChannelType.CLASSIFICATION)
				.inputResolution(inputResolution)
				.build();

		return new ThresholdPixelClassifier(metadata, fun);
	}
	
	
	
	static class ThresholdPixelClassifier implements PixelClassifier {
		
		private PixelClassifierMetadata metadata;
		private ClassifierFunction fun;
		
		private transient IndexColorModel cm;
		
		ThresholdPixelClassifier(PixelClassifierMetadata metadata, ClassifierFunction fun) {
			this.metadata = metadata;
			this.fun = fun;
		}

		@Override
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			return (metadata.getInputNumChannels() == imageData.getServer().nChannels());
		}
		
		protected IndexColorModel getColorModel() {
			if (cm != null)
				return cm;
			synchronized(this) {
				if (cm == null)
					cm = ColorModelFactory.getIndexedClassificationColorModel(metadata.getClassificationLabels());
			}
			return cm;
		}
		
		protected BufferedImage createOutputImage(BufferedImage imgInput) {
			return new BufferedImage(imgInput.getWidth(), imgInput.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, getColorModel());
		}

		@Override
		public BufferedImage applyClassification(ImageData<BufferedImage> imageData, RegionRequest request)
				throws IOException {
			
			var img = imageData.getServer().readRegion(request);
			var raster = img.getRaster();
			
			int w = img.getWidth();
			int h = img.getHeight();

			var imgOutput = createOutputImage(img);
			var rasterOutput = imgOutput.getRaster();

			float[] px = new float[raster.getNumBands()];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					px = raster.getPixel(x, y, px);
					int output = fun.predict(px);
					rasterOutput.setSample(x, y, 0, output);
				}
			}
			
			return imgOutput;
		}

		@Override
		public PixelClassifierMetadata getMetadata() {
			return metadata;
		}
		
		
	}
	

}