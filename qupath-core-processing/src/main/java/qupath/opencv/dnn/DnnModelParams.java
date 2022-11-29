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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
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

/**
 * Parameters to build a {@link DnnModel}.
 * These are used via {@link DnnModels#buildModel(DnnModelParams)}.
 * <p>
 * Many parameters are optional.
 * However as many as are available should be set, to maximize the chances 
 * of a {@link DnnModelBuilder} being available to build a model from the parameters.
 * <p>
 * <b>Warning!</b> The API for this class is unstable; it is likely to change in 
 * future releases.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class DnnModelParams {
	
	/**
	 * Default name to identify TensorFlow.
	 */
	public static final String FRAMEWORK_TENSORFLOW = "TensorFlow";
	
	/**
	 * Default name to identify TensorFlow Lite.
	 */
	public static final String FRAMEWORK_TF_LITE = "TFLite";
	
	/**
	 * Default name to identify ONNX Runtime.
	 */
	public static final String FRAMEWORK_ONNX_RUNTIME = "OnnxRuntime";
	
	/**
	 * Default name to identify OpenCV DNN.
	 */
	public static final String FRAMEWORK_OPENCV_DNN = "OpenCV DNN";
	
	/**
	 * Default name to identify PyTorch.
	 */
	public static final String FRAMEWORK_PYTORCH = "PyTorch";
	
	/**
	 * Default name to identify MXNet.
	 */
	public static final String FRAMEWORK_MXNET = "MxNet";

	
	
	private static final Logger logger = LoggerFactory.getLogger(DnnModelParams.class);

	private String framework;
	
	private String layout;
	private List<URI> uris;
	
	private boolean lazyInitialize = false;
	
	private Map<String, DnnShape> inputs;
	private Map<String, DnnShape> outputs;
	
	private DnnModelParams(DnnModelParams params) {
		if (params == null) {
			logger.debug("Creating default model params");
			return;
		}
		logger.debug("Creating model params from {}", params);
		this.framework = params.getFramework();
		this.layout = params.getLayout();
		this.uris = Collections.unmodifiableList(new ArrayList<>(params.getURIs()));
		this.inputs = params.inputs == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(params.getInputs()));
		this.outputs = params.outputs == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(params.getOutputs()));
		this.lazyInitialize = params.lazyInitialize;
	}
	
	/**
	 * Get the name of the deep learning framework that may be used.
	 * If null, consumers may try to infer this from any URIs.
	 * @return
	 */
	public String getFramework() {
		return framework;
	}
	
	/**
	 * Get the URIs associated with the model (e.g. weights and/or config files).
	 * @return
	 */
	public List<URI> getURIs() {
		return uris == null ? Collections.emptyList() : Collections.unmodifiableList(uris);
	}
	
	/**
	 * Get the requested inputs and their shapes.
	 * @return the inputs, if known, or null otherwise
	 */
	public Map<String, DnnShape> getInputs() {
		if (inputs == null)
			return null;
		return Collections.unmodifiableMap(inputs);
	}

	/**
	 * Get the expected outputs and their shapes.
	 * @return the outputs, if known, or null otherwise
	 */
	public Map<String, DnnShape> getOutputs() {
		if (outputs == null)
			return null;
		return Collections.unmodifiableMap(outputs);
	}

	/**
	 * Get a string representing the axes layout that the model expects as input.
	 * This should follow the Bioimage Model Zoo spec, and include only the characters "bixyczt".
	 * @return
	 */
	public String getLayout() {
		return layout;
	}
	
	/**
	 * Request that any model is loaded lazily on demand.
	 * <p>
	 * This can be useful to avoid blocking at some inopportune point in the code, 
	 * but means that any exceptions associated with model initialization will 
	 * probably not be thrown until the model is used.
	 * @return
	 */
	public boolean requestLazyInitialize() {
		return lazyInitialize;
	}
	
	/**
	 * Create a new params builder, initialized with the values from existing 
	 * params.
	 * @param params
	 * @return a new builder
	 */
	public static Builder builder(DnnModelParams params) {
		return new Builder(params);
	}

	/**
	 * Create a new params builder, with default values.
	 * @return a new builder
	 */
	public static Builder builder() {
		return builder(null);
	}
	
	

	/**
	 * Builder for {@link DnnModelParams}.
	 */
	public static class Builder {
		
		private static final Logger logger = LoggerFactory.getLogger(Builder.class);
		
		private DnnModelParams params;
		
		private Builder(DnnModelParams params) {
			this.params = new DnnModelParams(params);
		}
		
		/**
		 * Specify the URIs as files. These will be appended to any existing URIs.
		 * @param files
		 * @return
		 */
		public Builder files(File... files) {
			return URIs(Arrays.stream(files).map(f -> f.toPath().toUri()).collect(Collectors.toList()));
		}
		
		/**
		 * Specify the URIs as path objects. These will be appended to any existing URIs.
		 * @param paths
		 * @return
		 */
		public Builder paths(Path... paths) {
			return URIs(Arrays.stream(paths).map(p -> p.toUri()).collect(Collectors.toList()));
		}
		
		/**
		 * Specify the URIs. These will be appended to any existing URIs.
		 * @param uris
		 * @return
		 */
		public Builder URIs(URI... uris) {
			return URIs(Arrays.asList(uris));
		}
		
		/**
		 * Specify the URIs as a collection. These will be appended to any existing URIs.
		 * @param uris
		 * @return
		 */
		public Builder URIs(Collection<URI> uris) {
			if (params.uris == null)
				params.uris = new ArrayList<>(uris);
			else
				params.uris.addAll(uris);
			return this;
		}
		
		/**
		 * Optionally request lazy initialization.
		 * @param lazyInitialize
		 * @return
		 */
		public Builder lazyInitialize(boolean lazyInitialize) {
			params.lazyInitialize = lazyInitialize;
			return this;
		}
		
		/**
		 * Specify the deep learning framework that can use the model.
		 * <p>
		 * It is recommended to use one of the default names available as static variables
		 * in {@link DnnModelParams}.
		 * However, an extension might use some other unique identifier to ensure that it is 
		 * used in preference to some other implementation.
		 * @param framework
		 * @return
		 */
		public Builder framework(String framework) {
			params.framework = framework;
			return this;
		}
		
		/**
		 * Specify a string representing the axes layout that the model expects as input.
		 * This should follow the Bioimage Model Zoo spec, and include only the characters "bixyczt".
		 * @param layout
		 * @return
		 * @implNote the layout string is not currently checked for validity, but this may change in the future.
		 */
		public Builder layout(String layout) {
			params.layout = layout;
			return this;
		}
		
		/**
		 * Specify the shape for a single input, with the default input name.
		 * @param shape
		 * @return
		 */
		public Builder inputShape(long... shape) {
			return input(DnnModel.DEFAULT_INPUT_NAME, DnnShape.of(shape));
		}
		
		/**
		 * Specify the shape as a long array for a single input with a specified name.
		 * @param name
		 * @param shape
		 * @return
		 */
		public Builder input(String name, long... shape) {
			return input(name, DnnShape.of(shape));
		}
		
		/**
		 * Specify the shape for a single input with a specified name.
		 * @param name
		 * @param shape
		 * @return
		 */
		public Builder input(String name, DnnShape shape) {
			if (name == null)
				name = DnnModel.DEFAULT_INPUT_NAME;
			return inputs(Collections.singletonMap(name, shape));
		}
		
		/**
		 * Specify the shapes for one or more inputs.
		 * @param inputs
		 * @return
		 */
		public Builder inputs(Map<String, DnnShape> inputs) {
			if (inputs == null) {
				logger.warn("Provided inputs were null");
				return this;
			}
			if (inputs.isEmpty()) {
				logger.warn("Provided inputs were empty");
				return this;
			}
			if (params.inputs == null)
				params.inputs = new LinkedHashMap<>(inputs);
			else
				params.inputs.putAll(inputs);
			return this;
		}

		/**
		 * Specify the shape for a single output, with the default output name.
		 * @param shape
		 * @return
		 */
		public Builder outputShape(long... shape) {
			return output(DnnModel.DEFAULT_OUTPUT_NAME, DnnShape.of(shape));
		}

		/**
		 * Specify the shape as a long array for a single named output.
		 * @param name 
		 * @param shape
		 * @return
		 */
		public Builder output(String name, long... shape) {
			return output(name, DnnShape.of(shape));
		}

		/**
		 * Specify the shape for a single named output.
		 * @param name 
		 * @param shape
		 * @return
		 */
		public Builder output(String name, DnnShape shape) {
			if (name == null)
				name = DnnModel.DEFAULT_OUTPUT_NAME;
			return outputs(Collections.singletonMap(name, shape));
		}

		/**
		 * Specify the shapes for one or more outputs.
		 * These will be added to any existing outputs.
		 * @param outputs
		 * @return
		 */
		public Builder outputs(Map<String, DnnShape> outputs) {
			if (outputs == null) {
				logger.warn("Provided outputs were null");
				return this;
			}
			if (outputs.isEmpty()) {
				logger.warn("Provided outputs were empty");
				return this;
			}
			if (params.outputs == null)
				params.outputs = new LinkedHashMap<>(outputs);
			else
				params.outputs.putAll(outputs);
			return this;
		}
		
		/**
		 * Build the params.
		 * @return
		 */
		public DnnModelParams build() {
			return new DnnModelParams(params);
		}
		
	}
	
	
}
