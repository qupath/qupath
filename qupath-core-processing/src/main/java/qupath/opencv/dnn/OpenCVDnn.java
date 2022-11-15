/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.StringVector;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_dnn.ClassificationModel;
import org.bytedeco.opencv.opencv_dnn.DetectionModel;
import org.bytedeco.opencv.opencv_dnn.KeypointsModel;
import org.bytedeco.opencv.opencv_dnn.Model;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.bytedeco.opencv.opencv_dnn.SegmentationModel;
import org.bytedeco.opencv.opencv_dnn.TextDetectionModel_DB;
import org.bytedeco.opencv.opencv_dnn.TextDetectionModel_EAST;
import org.bytedeco.opencv.opencv_dnn.TextRecognitionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.io.UriResource;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

/**
 * Wrapper for an OpenCV Net, including essential metadata about how it should be used.
 * <p>
 * The main purpose of this is to support serializing models to JSON... kind of. In truth currently the paths 
 * to the original model files are serialized, since (to my knowledge) there is no way to save and reload a Net directly.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVDnn implements UriResource, DnnModel<Mat> {
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVDnn.class);
	
	
	/**
	 * Enum representing different classes of {@link Model} supported by OpenCV.
	 * These can be used as a more convenient way to run predictions.
	 */
	public static enum ModelType {
		
		/**
		 * Default {@link Model} class.
		 */
		DEFAULT,
		
		/**
		 * Refers to {@link DetectionModel}.
		 */
		DETECTION,
		
		/**
		 * Refers to {@link SegmentationModel}.
		 */
		SEGMENTATION,
		
		/**
		 * Refers to {@link ClassificationModel}.
		 */
		CLASSIFICATION,
		
		/**
		 * Refers to {@link KeypointsModel}.
		 */
		KEYPOINTS,
		
		/**
		 * Refers to {@link TextRecognitionModel}.
		 */
		TEXT_RECOGNITION,
		
		/**
		 * Refers to {@link TextDetectionModel_DB}.
		 */
		TEXT_DETECTION_DB,
		
		/**
		 * Refers to {@link TextDetectionModel_EAST}.
		 */
		TEXT_DETECTION_EAST;
	}
	
	private String name;
	
	private ModelType modelType = ModelType.DEFAULT;
	
	private URI pathModel;
	private URI pathConfig;
	private String framework;
	
	private int backend = DnnTools.useCuda() ? opencv_dnn.DNN_BACKEND_CUDA : opencv_dnn.DNN_BACKEND_DEFAULT;
	private int target = DnnTools.useCuda() ? opencv_dnn.DNN_TARGET_CUDA : opencv_dnn.DNN_TARGET_CPU;
		
	private boolean crop = false;
	private boolean swapRB = false;
	private Size size;
	private Scalar mean;
	private double scale;
	
	private Map<String, DnnShape> inputs;
	private Map<String, DnnShape> outputs;

	private transient boolean constructed = false;
	
//	private OpenCVDnn() {}

	/**
	 * Build the OpenCV {@link Net}. This is a lower-level function than {@link #buildModel()}, which provides 
	 * more options to query the network architecture but does not incorporate any preprocessing steps.
	 * @return
	 */
	public Net buildNet() {
		var fileModel = pathModel == null ? null : Paths.get(pathModel).toFile().getAbsolutePath();
		var fileConfig = pathConfig == null ? null : Paths.get(pathConfig).toFile().getAbsolutePath();
		var net = opencv_dnn.readNet(fileModel, fileConfig, framework);
		initializeNet(net);
		constructed = true;
		return net;
	}
	
	/**
	 * Build a model, specifying the {@link ModelType}.
	 * @param <T>
	 * @param type
	 * @return
	 * @see #buildModel()
	 */
	public <T extends Model> T buildModel(ModelType type) {
		if (type == null)
			type = ModelType.DEFAULT;
		var net = buildNet();
		@SuppressWarnings("unchecked")
		T model = (T)buildModel(type, net);
		initializeModel(model);
		return model;
	}
	
	private static Model buildModel(ModelType type, Net net) {
		switch (type) {
		case CLASSIFICATION:
			return new ClassificationModel(net);
		case DETECTION:
			return new DetectionModel(net);
		case SEGMENTATION:
			return new SegmentationModel(net);
		case KEYPOINTS:
			return new KeypointsModel(net);
		case TEXT_RECOGNITION:
			return new TextRecognitionModel(net);
		case TEXT_DETECTION_DB:
			return new TextDetectionModel_DB(net);
		case TEXT_DETECTION_EAST:
			return new TextDetectionModel_EAST(net);
		case DEFAULT:
		default:
			return new Model(net);
		}
	}
		
	/**
	 * Build a model. The return type is determined by the {@link ModelType}.
	 * @param <T>
	 * @return
	 * @see #buildModel(ModelType)
	 */
	public <T extends Model> T buildModel() {
		return buildModel(modelType);
	}
	
	
	/**
	 * Initialize the model with the same preprocessing defined here (i.e. input size, mean, scale, crop, swapRB).
	 * @param model
	 */
	public void initializeModel(Model model) {
		model.setInputCrop(crop);
		model.setInputSwapRB(swapRB);
		if (mean != null)
			model.setInputMean(mean);
		if (Double.isFinite(scale))
			model.setInputScale(scale);
		if (size != null)
			model.setInputSize(size);
	}
	
	
	/**
	 * Build a generic {@link PredictionFunction} from this dnn.
	 * @return
	 */
	private PredictionFunction<Mat> createPredictionFunction() {
		return new OpenCVNetFunction();
	}
	
	
	private void initializeNet(Net net) {
		switch (target) {
		case opencv_dnn.DNN_TARGET_CUDA:
		case opencv_dnn.DNN_TARGET_CUDA_FP16:
			int count = opencv_core.getCudaEnabledDeviceCount();
			if (count < 0)
				logger.warn("Unable to set CUDA target - driver may be missing or unavailable (device count = {})", count);
			else if (count == 0)
				logger.warn("Unable to set CUDA target - OpenCV not compiled with CUDA support (device count = {})", count);
			else if (backend != opencv_dnn.DNN_BACKEND_CUDA) {
				logger.warn("Must specify CUDA backend to use CUDA target - request will be ignored");
			} else {
				logger.debug("Setting CUDA backend and target ({}:{})", backend, target);
				net.setPreferableBackend(backend);
				net.setPreferableTarget(target);
			}
			break;
		case opencv_dnn.DNN_TARGET_OPENCL:
		case opencv_dnn.DNN_TARGET_OPENCL_FP16:
			if (!opencv_core.haveOpenCL())
				logger.warn("Cannot set OpenCL target - OpenCL is unavailable on this platform");
			else if (backend == opencv_dnn.DNN_BACKEND_CUDA) {
				logger.warn("Cannot set CUDA backend and OpenCL target");
			} else {
				logger.debug("Setting OpenCL backend and target ({}:{})", backend, target);
				net.setPreferableBackend(backend);
				net.setPreferableTarget(target);
			}
			break;
		case opencv_dnn.DNN_TARGET_CPU:
		case opencv_dnn.DNN_TARGET_FPGA:
		case opencv_dnn.DNN_TARGET_HDDL:
		case opencv_dnn.DNN_TARGET_MYRIAD:
		case opencv_dnn.DNN_TARGET_VULKAN:
		default:
			net.setPreferableBackend(backend);
			net.setPreferableBackend(target);
			break;
		}
	}
	
	/**
	 * Get a user-readable name for this model, or null if no name is specified.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	
	/**
	 * Get scale factors to be applied to preprocess input.
	 * @return the scale value if specified, or null if default scaling should be used
	 * @see #getMean()
	 */
	public Double getScale() {
		return scale;
	}
	
	/**
	 * Get the type of the model that would be built with {@link #buildModel()}.
	 * @return
	 */
	public ModelType getModelType() {
		return modelType;
	}
	
	/**
	 * Get means which should be subtracted for preprocessing.
	 * @return the mean value if specified, or null if OpenCV's default should be used (likely to be zero)
	 * @see #getScale()
	 */
	public Scalar getMean() {
		return mean == null ? null : new Scalar(mean);
	}
	
//	/**
//	 * If true, preprocessing should involve swapping red and blue channels.
//	 * @return
//	 */
//	public boolean doSwapRB() {
//		return swapRB;
//	}
	
	/**
	 * Get the path to the model.
	 * @return
	 */
	public URI getModelUri() {
		return pathModel;
	}

	/**
	 * Get the path to the model configuration, if required.
	 * @return
	 */
	public URI getConfigUri() {
		return pathConfig;
	}
	
	/**
	 * Get the framework used to create the model.
	 * @return
	 */
	public String getFramework() {
		return framework;
	}
	
	
	/**
	 * Create a new builder.
	 * @param pathModel
	 * @return
	 */
	public static Builder builder(String pathModel) {
		return new Builder(pathModel);
	}
	
	
	
	
	
	
	/**
	 * Helper class to build an {@link OpenCVDnn}.
	 */
	public static class Builder {
		
		private String name;
		
		private ModelType modelType = ModelType.DEFAULT;
		
		private URI pathModel;
		private URI pathConfig;
		private String framework;
		
		private Size size = null;
		private Scalar mean = null;
		private double scale = 1.0;
		private boolean swapRB = false;
		
		private int backend = DnnTools.useCuda() ? opencv_dnn.DNN_BACKEND_CUDA : opencv_dnn.DNN_BACKEND_DEFAULT;
		private int target = DnnTools.useCuda() ? opencv_dnn.DNN_TARGET_CUDA : opencv_dnn.DNN_TARGET_CPU;
		
		private Map<String, DnnShape> outputs;
		
		/**
		 * Path to the model file.
		 * @param pathModel
		 */
		private Builder(String pathModel) {
			this(new File(pathModel).toURI());
		}
		
		private Builder(URI pathModel) {
			this.pathModel = pathModel;
			try {
				this.name = Paths.get(pathModel).getFileName().toString();
			} catch (Exception e) {
				logger.debug("Unable to set default Net name from {} ({})", pathModel, e.getLocalizedMessage());
			}
		}
				
		/**
		 * Specify the framework (used to identify the appropriate loader for the model).
		 * @param name
		 * @return
		 */
		public Builder framework(String name) {
			this.framework = name;
			return this;
		}
		
		/**
		 * Path to config file (if required).
		 * @param pathConfig
		 * @return
		 */
		public Builder config(String pathConfig) {
			return config(new File(pathConfig).toURI());
		}
		
		/**
		 * Path to config file (if required).
		 * @param pathConfig
		 * @return
		 */
		public Builder config(URI pathConfig) {
			this.pathConfig = pathConfig;
			return this;
		}
		
		/**
		 * User-friendly name to use with this model.
		 * @param name
		 * @return
		 */
		public Builder name(String name) {
			this.name = name;
			return this;
		}
		
		/**
		 * Specify OpenCL target. It probably won't help, but perhaps worth a try.
		 * @return
		 */
		public Builder opencl() {
			this.backend = opencv_dnn.DNN_BACKEND_OPENCV;
			this.target = opencv_dnn.DNN_TARGET_OPENCL;
			return this;
		}
		
		/**
		 * Specify OpenCL target with 16-bit floating point. 
		 * It probably won't help, but perhaps worth a try.
		 * @return
		 */
		public Builder opencl16() {
			this.backend = opencv_dnn.DNN_BACKEND_OPENCV;
			this.target = opencv_dnn.DNN_TARGET_OPENCL_FP16;
			return this;
		}
		
		/**
		 * Request CUDA backend and target, if available.
		 * @return
		 */
		public Builder cuda() {
			this.backend = opencv_dnn.DNN_BACKEND_CUDA;
			this.target = opencv_dnn.DNN_TARGET_CUDA;
			return this;
		}
		
		/**
		 * Request CPU backend and target, if available.
		 * @return
		 */
		public Builder cpu() {
			this.backend = opencv_dnn.DNN_BACKEND_OPENCV;
			this.target = opencv_dnn.DNN_TARGET_CPU;
			return this;
		}
		
		/**
		 * Request CUDA backend and target, if available, with 16-bit floating point.
		 * @return
		 */
		public Builder cuda16() {
			this.backend = opencv_dnn.DNN_BACKEND_CUDA;
			this.target = opencv_dnn.DNN_TARGET_CUDA_FP16;
			return this;
		}
		
		/**
		 * Specify the target, e.g. {@code opencv_dnn.DNN_TARGET_CUDA}.
		 * @param target
		 * @return
		 * @see #cuda()
		 * @see #opencl()
		 */
		public Builder target(int target) {
			this.target = target;
			return this;
		}
		
		/**
		 * Specify the backend, e.g. {@code opencv_dnn.DNN_BACKEND_CUDA}.
		 * @param backend
		 * @return
		 * @see #cuda()
		 * @see #opencl()
		 */
		public Builder backend(int backend) {
			this.backend = backend;
			return this;
		}
		
//		/**
//		 * Request that red and blue channels are switch (QuPath uses RGB by default).
//		 * @return
//		 */
//		public Builder swapRB() {
//			this.swapRB = true;
//			return this;
//		}
		
		/**
		 * Mean values which should be subtracted from the image channels before input to the {@link Net}.
		 * @param mean
		 * @return
		 */
		public Builder mean(Scalar mean) {
			this.mean = mean;
			return this;
		}
		
		/**
		 * Scale values, by which channels should be multiplied (after mean subtraction) before input to the {@link Net}.
		 * @param scale
		 * @return
		 */
		public Builder scale(double scale) {
			this.scale = scale;
			return this;
		}
			
		/**
		 * Input width and height.
		 * @param width
		 * @param height
		 * @return
		 */
		public Builder size(int width, int height) {
			this.size = new Size(width, height);
			return this;
		}
		
		/**
		 * Input width and height.
		 * @param size
		 * @return
		 */
		public Builder size(Size size) {
			return size(size.width(), size.height());
		}
		
		/**
		 * Set the model type, used by {@link OpenCVDnn#buildModel()}.
		 * @param type 
		 * @return
		 */
		public Builder modelType(ModelType type) {
			this.modelType = type;
			return this;
		}
		
		/**
		 * Set the model type to be {@link ModelType#CLASSIFICATION}.
		 * @return
		 */
		public Builder classification() {
			return modelType(ModelType.CLASSIFICATION);
		}
		
		/**
		 * Set the model type to be {@link ModelType#SEGMENTATION}.
		 * @return
		 */
		public Builder segmentation() {
			return modelType(ModelType.SEGMENTATION);
		}
		
		/**
		 * Set the model type to be {@link ModelType#DETECTION}.
		 * @return
		 */
		public Builder detection() {
			return modelType(ModelType.DETECTION);
		}
		
		/**
		 * Set the layer outputs. Usually this isn't necessary, but it provides a means to output features 
		 * prior to any final classification.
		 * @param layers
		 * @return
		 */
		public Builder outputs(String... layers) {
			this.outputs = Arrays.stream(layers).collect(Collectors.toMap(n -> n, n -> DnnShape.UNKNOWN_SHAPE));
			return this;
		}
		
		/**
		 * Set the layer outputs and shapes. Usually this isn't necessary, but it provides a means to output features 
		 * prior to any final classification.
		 * @param outputs
		 * @return
		 */
		public Builder outputs(Map<String, DnnShape> outputs) {
			this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
			return this;
		}
		
		/**
		 * Build a new {@link OpenCVDnn}.
		 * @return
		 */
		public OpenCVDnn build() {
			OpenCVDnn dnn = new OpenCVDnn();
			dnn.pathModel = pathModel;
			dnn.pathConfig = pathConfig;
			dnn.framework = framework;
			dnn.name = name;
			dnn.modelType = modelType == null ? ModelType.DEFAULT : modelType;
			
			dnn.size = size == null ? null : new Size(size);
			dnn.backend = backend;
			dnn.target = target;
			
			dnn.mean = mean == null ? null : new Scalar(mean);
			dnn.scale = scale;
			dnn.swapRB = swapRB;
			dnn.outputs = outputs;
			return dnn;
		}
		
	}
	
	
	
//	static class OpenCVDnnFunction implements DnnF

	
	
	class OpenCVNetFunction implements PredictionFunction<Mat>, AutoCloseable { //, UriResource {
				
		private transient Net net;
		private transient List<String> outputLayerNames;
		private transient StringVector outputLayerNamesVector;
		
		// Experimental code - can use a ThreadLocal Net, but doesn't seem to improve performance overall
		// and can even slightly reduce it
		// (tested 06/22 using StarDist on an Apple M1 Max with 32 GB RAM)
//		private transient ThreadLocal<Net> localNet = ThreadLocal.withInitial(() -> {
//			ensureInitialized();
//			if (net != null) {
//				var net2 = buildNet();
//				net2.retainReference();
//				return net2;
//			}
//			return net;
//		});

				
		OpenCVNetFunction() {
			ensureInitialized();
		}
		
		private void ensureInitialized() {
			if (net == null || net.isNull()) {
				synchronized (this) {
					if (net == null || net.isNull()) {
						net = buildNet();
						net.retainReference();
						outputLayerNames = new ArrayList<>();
						if (outputs != null && !outputs.isEmpty())
							outputLayerNames.addAll(outputs.keySet());
						else {
							var names = net.getUnconnectedOutLayersNames();
							for (var bp : names.get()) {
								outputLayerNames.add(bp.getString());
							}
						}
						outputLayerNamesVector = new StringVector(outputLayerNames.toArray(String[]::new));
						outputLayerNamesVector.retainReference();
					}
				}
			}
		}
		
		
		private Net getNet() {
			ensureInitialized();
			return net;
//			boolean doParallel = false;
//			if (doParallel)
//				return localNet.get();
//			else
//				return net;
		}
		

		@Override
		public Mat predict(Mat input) {
			
			var net = getNet();
//			var net = buildNet();
			
			synchronized(net) {
				net.setInput(input);
				// We need to clone so that we can release the lock
				if (outputLayerNames.size() > 1)
					logger.warn("Single output requested for multi-output model - only the first will be returned");
				return net.forward(outputLayerNames.get(0)).clone();
//				return net.forward().clone();
			}
		}
		
		
		@SuppressWarnings("unchecked")
		@Override
		public Map<String, Mat> predict(Map<String, Mat> input) {
			
			var net = getNet();
			
			// If we have one input and one output, use simpler method
			if (input.size() == 1 && outputLayerNames.size() == 1) {
				var output = predict(input.values().iterator().next());
				return Map.of(outputLayerNames.get(0), output);
			}
			if (outputLayerNamesVector == null || outputLayerNamesVector.isNull()) {
				outputLayerNamesVector = new StringVector(outputLayerNames.toArray(String[]::new));
			}
			
			// Preallocate output so we can use PointerScope
			Map<String, Mat> result = new LinkedHashMap<>();
			for (var name : outputLayerNames) {
				result.put(name, new Mat());
			}
			
			try (var scope = new PointerScope()) {
				
				// Prepare output
				var output = new MatVector();
						
				synchronized(net) {
					// Only use input names if we have more than one input (usually we don't)
					boolean singleInput = input.size() == 1;
					for (var entry : input.entrySet()) {
						if (singleInput)
							net.setInput(entry.getValue());
						else
							net.setInput(entry.getValue(), entry.getKey(), 1.0, null);
					}
					net.forward(output, outputLayerNamesVector);
					
					// Clone so we can release the lock
					var mats = output.get();
					int i = 0;
					for (var name : outputLayerNames) {
						result.get(name).put(mats[i].clone());
						i++;
					}
				}
	
			}
			
			return result;
		}


//		@Override
//		public Collection<URI> getUris() throws IOException {
//			return OpenCVDnn.this.getUris();
//		}
//
//
//		@Override
//		public boolean updateUris(Map<URI, URI> replacements) throws IOException {
//			return OpenCVDnn.this.updateUris(replacements);
//		}


		@Override
		public Map<String, DnnShape> getInputs() {
			if (inputs != null)
				return inputs;
			// Can find no way to get input names... so we resort to this
			return Collections.singletonMap(DEFAULT_INPUT_NAME, DnnShape.UNKNOWN_SHAPE);
		}


		@Override
		public Map<String, DnnShape> getOutputs(DnnShape... inputShapes) {
			if (outputs != null)
				return outputs;
			return DnnTools.getOutputLayers(net, inputShapes);
		}

		@Override
		public synchronized void close() throws Exception {
			if (net != null) {
				logger.debug("Closing {}", net);
				net.close();
				net.deallocate();
			}
			if (outputLayerNamesVector != null) {
				outputLayerNamesVector.close();
				outputLayerNamesVector.deallocate();
			}
		}
		
	}











	@Override
	public Collection<URI> getURIs() throws IOException {
		var list = new ArrayList<URI>();
		if (pathModel != null)
			list.add(pathModel);
		if (pathConfig != null)
			list.add(pathConfig);
		return list;
	}

	@Override
	public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
		if (constructed)
			throw new UnsupportedOperationException("URIs cannot be updated after construction!");
		boolean changes = false;
		for (var entry : replacements.entrySet()) {
			if (entry.getKey() == null || Objects.equals(entry.getKey(), entry.getValue()))
				continue;
			if (Objects.equals(pathModel, entry.getKey())) {
				pathModel = entry.getValue();
				changes = true;
			}
			if (Objects.equals(pathConfig, entry.getKey())) {
				pathConfig = entry.getValue();
				changes = true;
			}
		}
		return changes;
	}
	
	private transient PredictionFunction<Mat> predFun;
	private transient BlobFunction<Mat> blobFun;

	
	private BlobFunction<Mat> createBlobFunction() {
		var ops = new ArrayList<ImageOp>();
		if (mean != null) {
			// TODO: Trim scalar array if needed
			double[] scalarArray = new double[4];
			mean.get(scalarArray);
			ops.add(ImageOps.Core.subtract(scalarArray));
		}
		if (scale != 1)
			ops.add(ImageOps.Core.multiply(scale));
		
		ImageOp preprocess;
		if (ops.isEmpty())
			preprocess = null;
		else if (ops.size() == 1)
			preprocess = ops.get(0);
		else
			preprocess = ImageOps.Core.sequential(ops);
		
		return new DefaultBlobFunction(preprocess, size, crop);
	}

	@Override
	public BlobFunction<Mat> getBlobFunction() {
		if (blobFun == null) {
			synchronized(this) {
				if (blobFun == null)
					blobFun = createBlobFunction();
			}
		}
		return blobFun;
	}

	@Override
	public BlobFunction<Mat> getBlobFunction(String name) {
		return getBlobFunction();
	}
	
	@Override
	public PredictionFunction<Mat> getPredictionFunction() {
		if (predFun == null) {
			synchronized(this) {
				if (predFun == null)
					predFun = createPredictionFunction();
			}
		}
		return predFun;
	}
	
	@Override
	public void close() throws Exception {
		if (predFun instanceof AutoCloseable) {
			((AutoCloseable)predFun).close();
		}
	}

}