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

package qupath.opencv.ml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Wrapper for an OpenCV Net, including essential metadata about how it should be used.
 * <p>
 * The main purpose of this is to support serializing models to JSON... kind of. In truth currently the paths 
 * to the original model files are serialized, since (to my knowledge) there is no way to save and reload a Net directly.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVDNN {
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVDNN.class);
	
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
	
	private String pathModel;
	private String pathConfig;
	private String framework;
	
	private int backend = opencv_dnn.DNN_BACKEND_DEFAULT;
	private int target = opencv_dnn.DNN_TARGET_CPU;
	
	private boolean crop = false;
	private boolean swapRB = false;
	private Size size;
	private Scalar mean;
	private double scale;

		
	private OpenCVDNN() {}

	/**
	 * Build the OpenCV {@link Net}. This is a lower-level function than {@link #buildModel()}, which provides 
	 * more options to query the network architecture but does not incorporate any preprocessing steps.
	 * @return
	 * @throws IOException 
	 */
	public Net buildNet() {
		var net = opencv_dnn.readNet(pathModel, pathConfig, framework);
		initializeNet(net);
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
	 * @param type
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
	 * Build a generic {@link OpenCVFunction} from this dnn.
	 * @return
	 */
	public OpenCVFunction buildFunction() {
		return new DnnFunction(this);
	}
	
	
	private void initializeNet(Net net) {
		switch (target) {
		case opencv_dnn.DNN_TARGET_CUDA:
		case opencv_dnn.DNN_TARGET_CUDA_FP16:
			int count = opencv_core.getCudaEnabledDeviceCount();
			if (count <= 0)
				logger.warn("Unable to set CUDA target - reported CUDA device count {}", count);
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
				logger.warn("Cannot set OpenCL target - OpenCL is unavailable");
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
	 * @see #getMeans()
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
	
	/**
	 * If true, preprocessing should involve swapping red and blue channels.
	 * @return
	 */
	public boolean doSwapRB() {
		return swapRB;
	}
	
	/**
	 * Get the path to the model.
	 * @return
	 */
	public String getModelPath() {
		return pathModel;
	}

	/**
	 * Get the path to the model configuration, if required.
	 * @return
	 */
	public String getConfigPath() {
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
	 * Helper class to build an {@link OpenCVDNN}.
	 */
	public static class Builder {
		
		private String name;
		
		private ModelType modelType = ModelType.DEFAULT;
		
		private String pathModel;
		private String pathConfig;
		private String framework;
		
		private Size size = null;
		private Scalar mean = null;
		private double scale = 1.0;
		private boolean swapRB = false;
		
		private int backend = opencv_dnn.DNN_BACKEND_DEFAULT;
		private int target = opencv_dnn.DNN_TARGET_CPU;
		
		/**
		 * Path to the model file.
		 * @param pathModel
		 */
		private Builder(String pathModel) {
			this.pathModel = pathModel;
			try {
				this.name = new File(pathModel).getName();
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
		
		/**
		 * Request that red and blue channels are switch (QuPath uses RGB by default).
		 * @return
		 */
		public Builder swapRB() {
			this.swapRB = true;
			return this;
		}
		
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
		 * Set the model type, used by {@link OpenCVDNN#buildModel()}.
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
		 * Build a new {@link OpenCVDNN}.
		 * @return
		 */
		public OpenCVDNN build() {
			OpenCVDNN dnn = new OpenCVDNN();
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
			return dnn;
		}
		
	}
	
	
		
	
	
	
	
	

	
	
	static class DnnFunction implements OpenCVFunction {
		
		private OpenCVDNN dnn;
		
		private transient Net net;
		private transient List<String> outputLayerNames;
		
		DnnFunction(OpenCVDNN dnn) {
			this.dnn = dnn;
			ensureInitialized();
		}
		
		
		private void ensureInitialized() {
			if (net == null) {
				synchronized (this) {
					if (net == null) {
						net = dnn.buildNet();
						outputLayerNames = new ArrayList<>();
						var names = net.getUnconnectedOutLayersNames();
						for (var bp : names.get()) {
							outputLayerNames.add(bp.getString());
						}
					}
				}
			}
		}
		
	

		@Override
		public Mat call(Mat input) {
					
			var blob = blobFromImage(input);
			Mat pred;
			
			synchronized(net) {
				net.setInput(blob);
				// We need to clone so that we can release the lock
				pred = net.forward().clone();
			}
			System.err.println(Arrays.toString(pred.createIndexer().sizes()));
			var result = imageFromBlob(pred);
			
			blob.close();
			pred.close();
			
			return result;
			
		}
		
		
		private static Mat blobFromImage(Mat mat) {
			
			if (mat.depth() != opencv_core.CV_32F) {
				var mat2 = new Mat();
				mat.convertTo(mat2, opencv_core.CV_32F);
				mat2 = mat;
			}
			
			Mat blob = null;
			int nChannels = mat.channels();
			if (nChannels == 1 || nChannels == 3 || nChannels == 4) {
				blob = opencv_dnn.blobFromImage(mat);
			} else {
				// TODO: Don't have any net to test this with currently...
				logger.warn("Attempting to reshape an image with " + nChannels + " channels - this may not work! "
						+ "Only 1, 3 and 4 supported.");
				// Blob is a 4D Tensor [NCHW]
				int[] shape = new int[4];
				Arrays.fill(shape, 1);
				int nRows = mat.size(0);
				int nCols = mat.size(1);
				shape[1] = nChannels;
				shape[2] = nRows;
				shape[3] = nCols;
				//    		for (int s = 1; s <= Math.min(nDims, 3); s++) {
				//    			shape[s] = mat.size(s-1);
				//    		}
				blob = new Mat(shape, opencv_core.CV_32F);
				var idxBlob = blob.createIndexer();
				var idxMat = mat.createIndexer();
				long[] indsBlob = new long[4];
				long[] indsMat = new long[4];
				for (int r = 0; r < nRows; r++) {
					indsMat[0] = r;
					indsBlob[2] = r;
					for (int c = 0; c < nCols; c++) {
						indsMat[1] = c;
						indsBlob[3] = c;
						for (int channel = 0; channel < nChannels; channel++) {
							indsMat[2] = channel;
							indsBlob[1] = channel;
							double val = idxMat.getDouble(indsMat);
							idxBlob.putDouble(indsBlob, val);
						}    			        			
					}    			
				}
				idxBlob.close();
				idxMat.close();
			}
			
			return blob;
		}
		
		private static Mat imageFromBlob(Mat blob) {
			var vec = new MatVector();
			opencv_dnn.imagesFromBlob(blob, vec);
			Mat output;
			if (vec.size() == 1) {
				output = vec.get(0L);
			} else {
				output = new Mat();
				opencv_core.merge(vec, output);
			}
			return output;
		}
		
		
		@SuppressWarnings("unchecked")
		@Override
		public Map<String, Mat> call(Map<String, Mat> input) {
			
			ensureInitialized();
			
			// If we have one input and one output, use simpler method
			if (input.size() == 1 && outputLayerNames.size() == 1) {
				var output = call(input.values().iterator().next());
				return Map.of(outputLayerNames.get(0), output);
			}
			
			// Preallocate output so we can use PointerScope
			Map<String, Mat> result = new LinkedHashMap<>();
			for (var name : outputLayerNames) {
				result.put(name, new Mat());
			}
			
			try (var scope = new PointerScope()) {
				// Create blobs
				var blobs = new LinkedHashMap<String, Mat>();
				for (var entry : input.entrySet()) {
					blobs.put(entry.getKey(), blobFromImage(entry.getValue()));
				}
				
				// Prepare output
				var outputLayerNamesVector = new StringVector(outputLayerNames.toArray(String[]::new));
				var output = new MatVector();
						
				synchronized(net) {
					// Only use input names if we have more than one input (usually we don't)
					boolean singleInput = blobs.size() == 1;
					for (var entry : blobs.entrySet()) {
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
	
				// Convert blobs to images
				for (var entry : result.entrySet()) {
					var blob = entry.getValue();
					blob.put(imageFromBlob(blob));
				}
	
			}
			
			return result;
		}
		
	}

}