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


package qupath.opencv.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.Padding;
import qupath.opencv.dnn.DnnModel;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

/**
 * Parameters required to build a classifier that operates on an image patch.
 * <p>
 * This is typically used to create a {@link PixelClassifier}.
 * It can also be used to create an {@link ObjectClassifier} that takes image patches as input 
 * (rather that features extracted from object ROIs or measurement lists).
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class PatchClassifierParams {
	
	private static final Logger logger = LoggerFactory.getLogger(PatchClassifierParams.class);

	private static int DEFAULT_PATCH_SIZE = 256;

	private List<ColorTransform> inputChannels;
	
	private int patchWidth = DEFAULT_PATCH_SIZE;
	private int patchHeight = DEFAULT_PATCH_SIZE;
	private Padding halo = null;
	
	private PixelCalibration inputResolution = null;
	private ChannelType outputChannelType;

	private List<ImageOp> preprocessingOps;
	private ImageOp predictionOp;
	private List<ImageOp> postprocessingOps;

	private Map<Integer, PathClass> outputClasses = new LinkedHashMap<>();

	private PatchClassifierParams() {}

	private PatchClassifierParams(PatchClassifierParams params) {
		this.inputChannels = params.inputChannels == null ? null : new ArrayList<>(params.inputChannels);
		this.patchWidth =  params.patchWidth;
		this.patchHeight = params.patchHeight;
		this.halo = params.halo;
		this.inputResolution = params.inputResolution;
		this.inputChannels = new ArrayList<>(params.inputChannels);
		this.outputChannelType = params.outputChannelType;
		this.preprocessingOps = params.preprocessingOps == null ? null : new ArrayList<>(params.preprocessingOps);
		this.postprocessingOps = params.postprocessingOps == null ? null : new ArrayList<>(params.postprocessingOps);
		this.predictionOp = params.predictionOp;
		this.outputClasses = params.outputClasses == null ? null : new LinkedHashMap<>(params.outputClasses);
	}

	/**
	 * Get the channels to extract from the image as input to the model.
	 * @return
	 */
	public List<ColorTransform> getInputChannels() {
		return inputChannels == null ? Collections.emptyList() : new ArrayList<>(inputChannels);
	}

	/**
	 * Get the width of a patch, in pixels.
	 * @return
	 */
	public int getPatchWidth() {
		return patchWidth;
	}

	/**
	 * Get the height of a patch, in pixels.
	 * @return
	 */
	public int getPatchHeight() {
		return patchHeight;
	}
	
	/**
	 * Get the 'halo' around the output.
	 * This can be used to determine appropriate padding to 
	 * avoid tile boundary artifacts. 
	 * Can be null or empty.
	 * @return
	 */
	public Padding getHalo() {
		return halo == null ? Padding.empty() : halo;
	}

	/**
	 * Get the requested input resolution for the image.
	 * @return
	 */
	public PixelCalibration getInputResolution() {
		return inputResolution;
	}

	/**
	 * Get the requested output channel type.
	 * @return
	 */
	public ChannelType getOutputChannelType() {
		return outputChannelType;
	}

	/**
	 * Get the classifications for the output.
	 * Keys to the map are generally channel numbers of the output 
	 * (zero-based), or could be labels in a single-channel labeled image.
	 * @return
	 */
	public Map<Integer, PathClass> getOutputClasses() {
		return new LinkedHashMap<>(outputClasses);
	}

	/**
	 * Get any preprocessing steps that should be applied.
	 * @return
	 */
	public List<ImageOp> getPreprocessing() {
		return preprocessingOps == null ? Collections.emptyList() : new ArrayList<>(preprocessingOps);
	}

	/**
	 * Get the image op used for prediction only.
	 * This is applied after any preprocessing steps, but before 
	 * any postprocessing steps.
	 * @return
	 */
	public ImageOp getPredictionOp() {
		return predictionOp;
	}

	/**
	 * Get any postprocessing steps that should be applied after prediction.
	 * @return
	 */
	public List<ImageOp> getPostprocessing() {
		return postprocessingOps == null ? Collections.emptyList() : new ArrayList<>(postprocessingOps);
	}
	
	
	/**
	 * Build a pixel classifier using these parameters
	 * @param params
	 * @return
	 */
	public static PixelClassifier buildPixelClassifier(PatchClassifierParams params) {

		int pad = 0;
		var padding = params.halo;
		if (padding != null && !padding.isEmpty()) {
			if (padding.isSymmetric())
				pad = padding.getX1();
			else {
				logger.warn("Only symmetric padding is supported - {} will be ignored", padding);
			}
		}
		
		var dataOp = ImageOps.buildImageDataOp(params.getInputChannels());
		var ops = new ArrayList<ImageOp>();
		if (params.getPreprocessing() != null)
			ops.addAll(params.getPreprocessing());

		if (params.getPredictionOp() != null)
			ops.add(params.getPredictionOp());

		if (params.getPostprocessing() != null)
			ops.addAll(params.getPostprocessing());
		
		dataOp = dataOp.appendOps(ops.toArray(ImageOp[]::new));
		
		var metadata = new PixelClassifierMetadata.Builder()
				.inputShape(params.getPatchWidth(), params.getPatchHeight())
				.classificationLabels(params.getOutputClasses())
				.setChannelType(params.getOutputChannelType())
				.inputResolution(params.getInputResolution())
				.inputPadding(pad)
				.outputPixelType(dataOp.getOutputType(PixelType.FLOAT32))
				.build();


		return PixelClassifiers.createClassifier(dataOp, metadata);

	}

	/**
	 * Create a builder to generate new patch classifier params.
	 * @return
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Create a builder to generate new patch classifier params, 
	 * initialized with the values from an existing parameter object.
	 * @param params the existing parameters, used to initialize the builder
	 * @return
	 */
	public static Builder builder(PatchClassifierParams params) {
		return new Builder(params);
	}
	
	/**
	 * Builder class to create {@link PatchClassifierParams}.
	 */
	public static class Builder {
		
		private PatchClassifierParams params;
		
		private Padding dnnPadding;
		private DnnModel<?> dnnModel;
		private String[] dnnOutputNames;
		
		private Builder() {
			this(null);
		}
		
		private Builder(PatchClassifierParams params) {
			this.params = params == null ? new PatchClassifierParams() : new PatchClassifierParams(params);
		}
		
		/**
		 * Define the input channels using channel names.
		 * @param channels
		 * @return
		 * @see #inputChannels(int...)
		 */
		public Builder inputChannels(String... channels) {
			return inputChannels(Arrays
						.stream(channels)
						.map(c -> ColorTransforms.createChannelExtractor(c))
						.collect(Collectors.toList()));
		}
		
		/**
		 * Define the input channels using (zero-based) channel numbers.
		 * @param channels
		 * @return
		 * @see #inputChannels(String...)
		 */
		public Builder inputChannels(int... channels) {
			return inputChannels(Arrays
						.stream(channels)
						.mapToObj(c -> ColorTransforms.createChannelExtractor(c))
						.collect(Collectors.toList()));
		}
		
		/**
		 * Define the input channels from a collection of color transforms.
		 * An ordered collection (e.g. list) should be used, since the iteration order 
		 * is important.
		 * @param channels
		 * @return
		 */
		public Builder inputChannels(Collection<? extends ColorTransform> channels) {
			this.params.inputChannels = new ArrayList<>(channels);
			return this;
		}
		
		/**
		 * Define the input resolution using a pixel calibration and a scaling factor.
		 * @param cal input calibration; if null, a default calibration will be used
		 * @param downsample scaling factor (1.0 to use the calibration directly)
		 * @return
		 */
		public Builder inputResolution(PixelCalibration cal, double downsample) {
			if (cal == null)
				cal = PixelCalibration.getDefaultInstance();
			return inputResolution(cal.createScaledInstance(downsample, downsample));
		}
		
		/**
		 * Define the input resolution using a pixel calibration object.
		 * @param cal
		 * @return
		 */
		public Builder inputResolution(PixelCalibration cal) {
			this.params.inputResolution = cal;
			return this;
		}
		
		/**
		 * Define a halo that is symmetric in x and y.
		 * @param padding padding value, to be added both before and after rows and columns.
		 * @return
		 * @see #halo(Padding)
		 */
		public Builder halo(int padding) {
			return halo(Padding.symmetric(padding));
		}
		
		/**
		 * Define a halo using a padding object.
		 * @param halo
		 * @return
		 * @see #halo(int)
		 */
		public Builder halo(Padding halo) {
			this.params.halo = halo;
			return this;
		}
		
		/**
		 * Define the requested square patch size.
		 * @param patchSize width and height of the patch
		 * @return
		 */
		public Builder patchSize(int patchSize) {
			return patchSize(patchSize, patchSize);
		}
		
		/**
		 * Define the requested patch size.
		 * @param patchWidth requested patch width
		 * @param patchHeight requested patch height
		 * @return
		 */
		public Builder patchSize(int patchWidth, int patchHeight) {
			this.params.patchWidth = patchWidth;
			this.params.patchHeight = patchHeight;
			return this;
		}
		
		/**
		 * Define the preprocessing steps from an array.
		 * Note that any existing preprocessing steps in the builder will 
		 * be replaced by those provided here.
		 * @param preprocessingOps
		 * @return
		 */
		public Builder preprocessing(ImageOp... preprocessingOps) {
			this.params.preprocessingOps = Arrays.asList(preprocessingOps);
			return this;
		}
		
		/**
		 * Define the preprocessing steps from a collection.
		 * Note that any existing preprocessing steps in the builder will 
		 * be replaced by those provided here.
		 * @param preprocessingOps
		 * @return
		 */
		public Builder preprocessing(Collection<? extends ImageOp> preprocessingOps) {
			this.params.preprocessingOps = new ArrayList<>(preprocessingOps);
			return this;
		}
		
		/**
		 * Define the prediction image op, to be applied after preprocessing 
		 * and before postprocessing.
		 * @param predictionOp
		 * @return
		 * @see #prediction(DnnModel, Padding, String...)
		 */
		public Builder prediction(ImageOp predictionOp) {
			this.params.predictionOp = predictionOp;
			return this;
		}
		
		/**
		 * Define the DNN to be used for prediction, to be applied after preprocessing 
		 * and before postprocessing.
		 * @param model 
		 * @param padding 
		 * @param outputNames 
		 * @return
		 * @see #prediction(ImageOp)
		 */
		public Builder prediction(DnnModel<?> model, Padding padding, String... outputNames) {
			this.dnnModel = model;
			this.dnnPadding = padding;
			this.dnnOutputNames = outputNames;
			return this;
		}
		
		/**
		 * Define the postprocessing steps from an array.
		 * Note that any existing postprocessing steps in the builder will 
		 * be replaced by those provided here.
		 * @param postprocessingOps
		 * @return
		 */
		public Builder postprocessing(ImageOp... postprocessingOps) {
			this.params.postprocessingOps = Arrays.asList(postprocessingOps);
			return this;
		}
		
		/**
		 * Define the postprocessing steps from a collection.
		 * Note that any existing postprocessing steps in the builder will 
		 * be replaced by those provided here.
		 * @param postprocessingOps
		 * @return
		 */
		public Builder postprocessing(Collection<? extends ImageOp> postprocessingOps) {
			this.params.postprocessingOps = new ArrayList<>(postprocessingOps);
			return this;
		}

		/**
		 * Define the channel type for the output.
		 * @param channelType
		 * @return
		 */
		public Builder outputChannelType(ChannelType channelType) {
			this.params.outputChannelType = channelType;
			return this;
		}
		
		/**
		 * Define the classifications for the output as a map.
		 * @param outputClasses
		 * @return
		 * @see #outputClassNames(Map)
		 * @see #outputClasses(PathClass...)
		 * @see #outputClassNames(String...)
		 */
		public Builder outputClasses(Map<Integer, PathClass> outputClasses) {
			this.params.outputClasses = new LinkedHashMap<>(outputClasses);
			return this;
		}
		
		/**
		 * Define the classifications for the output as an array.
		 * @param outputClasses
		 * @return
		 * @see #outputClasses(Map)
		 * @see #outputClassNames(Map)
		 * @see #outputClassNames(String...)
		 */
		public Builder outputClasses(PathClass... outputClasses) {
			var map = new LinkedHashMap<Integer, PathClass>();
			for (int i = 0; i < outputClasses.length; i++) {
				map.put(Integer.valueOf(i), outputClasses[i]);
			}
			return outputClasses(map);
		}
		
		/**
		 * Define the classifications for the output as an array of classification names.
		 * @param outputClasses
		 * @return
		 * @see #outputClasses(PathClass...)
		 * @see #outputClassNames(String...)
		 */
		public Builder outputClassNames(String... outputClasses) {
			return outputClasses(Arrays.stream(outputClasses).map(c -> PathClass.fromString(c)).toArray(PathClass[]::new));
		}
		
		/**
		 * Define the classifications for the output as a map with string values.
		 * @param outputClasses
		 * @return
		 * @see #outputClasses(Map)
		 * @see #outputClasses(PathClass...)
		 * @see #outputClassNames(String...)
		 */
		public Builder outputClassNames(Map<Integer, String> outputClasses) {
			return outputClasses(
					outputClasses.entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> PathClass.fromString(e.getValue()))));
		}
		
		/**
		 * Build the patch classifier parameters.
		 * @return
		 */
		public PatchClassifierParams build() {
			// Update prediction op
			var params2 = new PatchClassifierParams(this.params);
			if (dnnModel != null) {
				if (params2.predictionOp == null) {
					params2.predictionOp = ImageOps.ML.dnn(dnnModel, params2.patchWidth, params2.patchHeight, dnnPadding, dnnOutputNames);
				} else throw new IllegalArgumentException("Both DnnModel and prediction op were provided - only one is allowed");
			}
			return params2;
		}

	}

}