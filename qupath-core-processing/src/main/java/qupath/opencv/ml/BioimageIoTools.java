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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.bioimageio.spec.BioimageIoSpec.Processing.Binarize;
import qupath.bioimageio.spec.BioimageIoSpec.BioimageIoModel;
import qupath.bioimageio.spec.BioimageIoSpec.Processing.Clip;
import qupath.bioimageio.spec.BioimageIoSpec.InputTensor;
import qupath.bioimageio.spec.BioimageIoSpec.OutputTensor;
import qupath.bioimageio.spec.BioimageIoSpec.Processing;
import qupath.bioimageio.spec.BioimageIoSpec.WeightsEntry;
import qupath.bioimageio.spec.BioimageIoSpec.Processing.ScaleLinear;
import qupath.bioimageio.spec.BioimageIoSpec.Processing.ScaleMeanVariance;
import qupath.bioimageio.spec.BioimageIoSpec.Processing.ScaleRange;
import qupath.bioimageio.spec.BioimageIoSpec.Processing.Sigmoid;
import qupath.bioimageio.spec.BioimageIoSpec.Processing.ZeroMeanUnitVariance;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.Padding;
import qupath.opencv.dnn.DnnModel;
import qupath.opencv.dnn.DnnModelParams;
import qupath.opencv.dnn.DnnModels;
import qupath.opencv.dnn.DnnShape;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

/**
 * Helper class for working with Bioimage Model Zoo model specs, and attempting to 
 * replicating the processing within QuPath.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class BioimageIoTools {
	
	private static final Logger logger = LoggerFactory.getLogger(BioimageIoTools.class);
	
	
	private static URI resolveUri(URI base, String relative) {
		if (base.toString().startsWith("jar:file:")) {
			return URI.create("jar:" + URI.create(base.toString().substring(4)).resolve(relative));
		}
		return base.resolve(relative);
	}
	
	
	/**
	 * Attempt to build a {@link DnnModel} that follows this spec as closely as possible.
	 * <p>
	 * In some instances, the processing steps requires by the spec might not currently be 
	 * supported by QuPath - see {@link #transformToOp(Processing)} for more information.
	 * @param spec
	 * @return
	 */
	public static DnnModel<?> buildDnnModel(BioimageIoModel spec) {
		// Try to find compatible weights
		DnnModel<?> dnn = null;
		
		// Check for weights in the order that we're likely to support them
		// This is based upon the fact that we will most likely be relying upon 
		// Deep Java Library, although *conceivably* we could have some extension 
		// that supports weights beyond what DJL currently handles
		for (var key : Arrays.asList(
				WeightsEntry.TORCHSCRIPT, // Should work on all platforms
				WeightsEntry.TENSORFLOW_SAVED_MODEL_BUNDLE, // Should work on most platforms (not Apple Silicon)
				WeightsEntry.ONNX, // Should work... if installed (usually isn't)
				WeightsEntry.TENSORFLOW_JS, // Probably won't work
				WeightsEntry.PYTORCH_STATE_DICT,
				WeightsEntry.KERAS_HDF5)) {
			try {
				var weights = spec.getWeights(key);
				if (weights == null)
					continue;
				var baseUri = spec.getBaseURI();
				var basePath = GeneralTools.toPath(baseUri);
				var relativeSource = weights.getSource();
				// For now, don't handle URLs
				if (relativeSource.toLowerCase().startsWith("http:") || relativeSource.toLowerCase().startsWith("https:")) {
					logger.debug("Don't support source {}", relativeSource);
					continue;
				}
				// Try to get the weights for the source
				URI source = null;
				if (basePath == null) {
					source = resolveUri(baseUri, relativeSource);
				} else {
					Path pathWeights = null;
					// We can't currently handle zipped weights - so see if we can find unzipped weights
					if (relativeSource.toLowerCase().endsWith(".zip")) {
						pathWeights = basePath.resolve(relativeSource.substring(0, relativeSource.length()-4));
						if (!Files.exists(pathWeights)) {
							logger.warn("Please unzip the model weights to {}", pathWeights);
							continue;
						}
					} else {
						pathWeights = basePath.resolve(relativeSource);
						if (!Files.exists(pathWeights)) {
							logger.warn("Can't find model weights at {}", pathWeights);
							continue;
						}
					}
					source = pathWeights.toUri();
				}
				String frameworkName = null;
				switch (key) {
				case ONNX:
					frameworkName = DnnModelParams.FRAMEWORK_ONNX_RUNTIME;
					break;
				case TENSORFLOW_SAVED_MODEL_BUNDLE:
					frameworkName = DnnModelParams.FRAMEWORK_TENSORFLOW;
					break;
				case TORCHSCRIPT:
					frameworkName = DnnModelParams.FRAMEWORK_PYTORCH;
					break;
				case PYTORCH_STATE_DICT:
				case TENSORFLOW_JS:
				case KERAS_HDF5:
				default:
					// *Conceivably* we could support these via some as-yet-unwritten extension... but we probably don't
					break;
				}
				String axes = spec.getInputs().get(0).getAxes();
				var inputShapeMap = spec.getInputs().stream().collect(Collectors.toMap(i -> i.getName(), i -> getShape(i)));
				var inputShape = inputShapeMap.size() == 1 ? inputShapeMap.values().iterator().next() : null; // Need a single input, otherwise can't be used for output
				var outputShapeMap = spec.getOutputs().stream().collect(Collectors.toMap(o -> o.getName(), o -> getShape(o, inputShape)));
				
				var params = DnnModelParams.builder()
						.framework(frameworkName)
						.URIs(source)
						.layout(axes)
						.inputs(inputShapeMap)
						.outputs(outputShapeMap)
						.build();
				
				dnn = DnnModels.buildModel(params);
				if (dnn != null) {
					logger.info("Loaded model {}", dnn);
				} else {
					logger.warn("Unable to build model for weights {} (source={}, framework={})", key, source, frameworkName);
				}
			} catch (Exception e) {
				logger.warn("Unsupported weights: {}", key);
				logger.error(e.getLocalizedMessage(), e);
			}
			if (dnn != null)
				break;
		}
		return dnn;
	}
	
	private static DnnShape getShape(InputTensor spec) {
		return DnnShape.of(Arrays.stream(spec.getShape().getShapeMin()).mapToLong(i -> i).toArray());
	}

	private static DnnShape getShape(OutputTensor outputSpec, DnnShape inputShape) {
		if (inputShape == null) {
			if (Arrays.stream(outputSpec.getShape().getScale()).anyMatch(s -> s != 0))
				logger.warn("Attempting to infer scaled output shape, but input shape is not available");
			return DnnShape.of(Arrays.stream(outputSpec.getShape().getOffset()).mapToLong(i -> (long)i).toArray());
		}
		int n = inputShape.numDimensions();
		long[] shape = new long[n];
		double[] scales = outputSpec.getShape().getScale();
		double[] offsets = outputSpec.getShape().getOffset();
		for (int i = 0; i < scales.length; i++) {
			shape[i] = Math.round(inputShape.get(i) * scales[i] + offsets[i] * 2);
		}
		return DnnShape.of(shape);
	}
	
	/**
	 * Create an instance of {@link PatchClassifierParams} from a model spec.
	 * This encapsulates some key information QuPath needs for building a model in a way that can 
	 * be modified and updated according to user requirements.
	 * @param model the model spec to initialize the parameters
	 * @param inputOps optional additional preprocessing ops to apply, before any in the model spec are added
	 * @return
	 */
	public static PatchClassifierParams buildPatchClassifierParams(BioimageIoModel model, ImageOp... inputOps) {
		return buildPatchClassifierParams(model, -1, -1, inputOps);
	}
	
	/**
	 * Create an instance of {@link PatchClassifierParams} from a model spec.
	 * This encapsulates some key information QuPath needs for building a model in a way that can 
	 * be modified and updated according to user requirements.
	 * @param modelSpec the model spec to initialize the parameters
	 * @param preferredTileWidth preferred tile width, or -1 to automatically determine this; the width will be updated based on the spec
	 * @param preferredTileHeight preferred tile height, or -1 to automatically determine this; the height will be updated based on the spec
	 * @param inputOps optional additional preprocessing ops to apply, before any in the model spec are added
	 * @return
	 */
	public static PatchClassifierParams buildPatchClassifierParams(BioimageIoModel modelSpec, int preferredTileWidth, int preferredTileHeight, ImageOp...inputOps) {

		var inputs = modelSpec.getInputs();
		if (inputs.size() != 1)
			throw new UnsupportedOperationException("Only single inputs currently supported! Model requires " + inputs.size());

		var outputs = modelSpec.getOutputs();
		if (outputs.size() != 1)
			throw new UnsupportedOperationException("Only single outputs currently supported! Model requires " + outputs.size());

		var input = inputs.get(0);
		var output = outputs.get(0);
		
		// Get dimensions and padding
		var axes = input.getAxes();
		int indChannels = axes.indexOf("c");
		int indX = axes.indexOf("x");
		int indY = axes.indexOf("y");
		int[] shapeMin = input.getShape().getShapeMin();
		int[] shapeStep = input.getShape().getShapeMin();
		int width = shapeMin[indX];
		int height = shapeMin[indY];
		int nChannelsIn = shapeMin[indChannels];
		int widthStep = shapeStep[indX];
		int heightStep = shapeStep[indY];
		long[] inputShape = Arrays.stream(shapeMin).mapToLong(i -> i).toArray();
		
		// Update tile sizes
		if (preferredTileWidth <= 0)
			preferredTileWidth = 512;
		if (preferredTileHeight <= 0)
			preferredTileHeight = 512;
		width = BioimageIoTools.updateLength(width, widthStep, preferredTileWidth);
		height = BioimageIoTools.updateLength(height, heightStep, preferredTileHeight);
		inputShape[indX] = width;
		inputShape[indY] = height;
		
		int[] outputShape = output.getShape().getShape();
		if (outputShape == null || outputShape.length == 0) {
			// Calculate output shape
			double[] outputShapeScale = output.getShape().getScale();
			double[] outputShapeOffset = output.getShape().getOffset();
			outputShape = new int[outputShapeScale.length];
			for (int i = 0; i < outputShape.length; i++) {
				outputShape[i] = (int)Math.round(inputShape[i] * outputShapeScale[i] + outputShapeOffset[i]);
			}
		}
		int nChannelsOut = (int)outputShape[indChannels];

		// Determine padding
		// TODO: Consider halo for input?!
		Padding padding = Padding.empty();
		var halo = output.getHalo();
		if (halo != null && halo.length != 0) {
			padding = Padding.getPadding(halo[indX], halo[indY]);
			
		}
		
		List<ImageOp> preprocessing = new ArrayList<>();
		for (var op : inputOps) {
			preprocessing.add(op);
		}
		preprocessing.add(ImageOps.Core.ensureType(PixelType.FLOAT32)); // Expected by bioimage.io
		if (input.getPreprocessing() != null) {
			for (var transform : input.getPreprocessing()) {
				var op = BioimageIoTools.transformToOp(transform);
				if (op == null)
					logger.warn("Unsupported preprocessing transform: {}", transform);
				else
					preprocessing.add(op);
			}
		}
		
		// Try to find compatible weights
		DnnModel<?> dnn = buildDnnModel(modelSpec);
		
		if (dnn == null)
			throw new UnsupportedOperationException("Unable to create a DnnModel for " + modelSpec.getName() + 
					".\nCheck 'View > Show log' for more details.");
		
		List<ImageOp> postprocessing = new ArrayList<>();
		if (output.getPostprocessing() != null) {
			for (var transform : output.getPostprocessing()) {
				var op = BioimageIoTools.transformToOp(transform);
				if (op == null)
					logger.warn("Unsupported postprocessing transform: {}", transform);
				else
					postprocessing.add(op);
			}
		}
		
		var labels = new LinkedHashMap<Integer, PathClass>();
		for (int c = 0; c < nChannelsOut; c++) {
			labels.put(c, PathClass.getInstance("Class " + c));
		}
		
		return PatchClassifierParams.builder()
				.inputChannels(IntStream.range(0, nChannelsIn).toArray())
				.patchSize(width, height)
				.halo(padding)
				.preprocessing(preprocessing)
				.prediction(dnn, padding)
				.postprocessing(postprocessing)
				.outputClasses(labels)
				.outputChannelType(ChannelType.PROBABILITY)
				.build();
	}
	
	
	private static int updateLength(int minLength, int step, int targetLength) {
		if (targetLength <= minLength || step <= 0)
			return minLength;
		return minLength + (int)((double)(targetLength - minLength) / step) * step;
	}
	
	/**
	 * Create an {@link ImageOp} that applies the specified transforms sequentially.
	 * <p>
	 * <b>Important!</b>
	 * This method is experimental and subject to change in future versions.
	 * <p>
	 * Not all Bioimage Model Zoo transforms are supported 
	 * by QuPath ops, and ops are generally per tile whereas model zoo transforms 
	 * sometimes require global statistics (e.g. for normalization).
	 * <p>
	 * This method will make an attempt to return a suitable op, but it may 
	 * not be able to incorporate all steps.
	 * 
	 * @param transforms
	 * @return
	 */
	public static ImageOp transformsToOp(Collection<? extends Processing> transforms) {
		List<ImageOp> ops = new ArrayList<>();
		ops.add(ImageOps.Core.ensureType(PixelType.FLOAT32)); // Expected input for bioimage-ip
		for (var transform : transforms) {
			var op = transformToOp(transform);
			if (op != null)
				ops.add(op);
		}
		return ops.size() == 1 ? ops.get(0) : ImageOps.Core.sequential(ops);
	}
	
	
	/**
	 * Create an {@link ImageOp} that applies the specified transforms, if possible.
	 * <p>
	 * <b>Important!</b>
	 * This method is experimental and subject to change in future versions.
	 * <p>
	 * Not all Bioimage Model Zoo transforms are supported 
	 * by QuPath ops, and ops are generally per tile whereas model zoo transforms 
	 * sometimes require global statistics (e.g. for normalization).
	 * <p>
	 * This method will make an attempt to return a suitable op, but may 
	 * return null if no such op is available.
	 * 
	 * @param transform
	 * @return
	 */
	public static ImageOp transformToOp(Processing transform) {
		
		if (transform instanceof Binarize) {
			var binarize = (Binarize)transform;
			return ImageOps.Threshold.threshold(binarize.getThreshold());
		}
		
		if (transform instanceof Clip) {
			var clip = (Clip)transform;
			return ImageOps.Core.clip(clip.getMin(), clip.getMax());
		}
		
		if (transform instanceof ScaleLinear) {
			var scale = (ScaleLinear)transform;
			// TODO: Consider axes
			return ImageOps.Core.sequential(
					ImageOps.Core.multiply(scale.getGain()),
					ImageOps.Core.add(scale.getOffset())					
					);
		}

		if (transform instanceof ScaleMeanVariance) {
			// TODO: Figure out if possible to somehow support ScaleMeanVariance
			var scale = (ScaleMeanVariance)transform;
			logger.warn("Unsupported transform {} - cannot access reference tensor {}", transform, scale.getReferenceTensor());
			return null;
//			var mode = warnIfUnsupportedMode(transform.getName(), scale.getMode(), List.of(ProcessingMode.PER_SAMPLE));
		}

		if (transform instanceof ScaleRange) {
			var scale = (ScaleRange)transform;
			var mode = warnIfUnsupportedMode(transform.getName(), scale.getMode(), List.of(Processing.ProcessingMode.PER_SAMPLE));
			assert mode == Processing.ProcessingMode.PER_SAMPLE; // TODO: Consider how to support per dataset
			var axes = scale.getAxes();
			boolean perChannel = false;
			if (axes != null)
				perChannel = !axes.contains("c");
			else
				logger.warn("Axes not specified for {} - channels will be normalized jointly", transform);
			return ImageOps.Normalize.percentile(scale.getMinPercentile(), scale.getMaxPercentile(), perChannel, scale.getEps());
		}

		if (transform instanceof Sigmoid) {
			return ImageOps.Normalize.sigmoid();
		}
		
		if (transform instanceof ZeroMeanUnitVariance) {
			var zeroMeanUnitVariance = (ZeroMeanUnitVariance)transform;
			var mode = warnIfUnsupportedMode(transform.getName(), zeroMeanUnitVariance.getMode(), List.of(Processing.ProcessingMode.PER_SAMPLE, Processing.ProcessingMode.FIXED));
			if (mode == Processing.ProcessingMode.PER_SAMPLE) {
				var axes = zeroMeanUnitVariance.getAxes();
				boolean perChannel = false;
				if (axes != null) {
					perChannel = !axes.contains("c");
					// Try to check axes are as expected
					if (!(sameAxes(axes, "xy") || sameAxes(axes, "xyc")))
						logger.warn("Unsupported axes {} for {} - I will use {} instead", axes, transform.getName(), perChannel ? "xy" : "xyc");
				} else
					logger.warn("Axes not specified for {} - channels will be normalized jointly", transform);

				return ImageOps.Normalize.zeroMeanUnitVariance(perChannel, zeroMeanUnitVariance.getEps());
//				return ImageOps.Normalize.zeroMeanUnitVariance(perChannel, zeroMeanUnitVariance.getEps());
			} else {
				assert mode == Processing.ProcessingMode.FIXED;
				double[] std = zeroMeanUnitVariance.getStd();
				// In specification, eps is added
				for (int i = 0; i < std.length; i++)
					std[i] += zeroMeanUnitVariance.getEps();
				return ImageOps.Core.sequential(
						ImageOps.Core.subtract(zeroMeanUnitVariance.getMean()),
						ImageOps.Core.divide(std)
						);
			}
		}

		logger.warn("Unknown transform {} - cannot convert to ImageOp", transform);
		return null;
	}
	
	
	private static boolean sameAxes(String input, String target) {
		if (Objects.equals(input, target))
			return true;
		if (input == null || target == null || input.length() != target.length())
			return false;
		var inputArray = input.toLowerCase().toCharArray();
		var targetArray = target.toLowerCase().toCharArray();
		Arrays.sort(inputArray);
		Arrays.sort(targetArray);
		return Arrays.equals(inputArray, targetArray);
	}
	
	
	private static Processing.ProcessingMode warnIfUnsupportedMode(String transformName, Processing.ProcessingMode mode, List<Processing.ProcessingMode> allowed) {
		if (mode == null || mode == Processing.ProcessingMode.PER_DATASET) {
			logger.warn("Unsupported mode {} for {}, will be switched to {}", mode, transformName, allowed.get(0));
			return allowed.get(0);
		}
		return mode;
	}

}
