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

package qupath.tensorflow;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.tensorflow.proto.framework.MetaGraphDef;
import org.tensorflow.proto.framework.TensorInfo;

import qupath.lib.images.servers.ImageChannel;
import qupath.lib.regions.Padding;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps.PaddedOp;
import qupath.opencv.tools.OpenCVTools;

/**
 * An {@link ImageOp} that runs a TensorFlow model for prediction.
 *
 * @author Pete Bankhead
 */
public class TensorFlowOp extends PaddedOp {

	private final static Logger logger = LoggerFactory.getLogger(TensorFlowOp.class);

	private final static int DEFAULT_TILE_SIZE = 512;

	private String modelPath;
	private int tileWidth = 512;
	private int tileHeight = 512;

	private Padding padding;

	// Identifier for the requested output node - may be null to use the default output
	private String outputName = null;

	private transient TensorFlowBundle bundle;
	private transient Exception exception;

	TensorFlowOp(String modelPath, int tileWidth, int tileHeight, Padding padding, String outputName) {
		super();
		logger.debug("Creating op from {}", modelPath);
		this.modelPath = modelPath;
		this.outputName = outputName;
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		if (padding == null)
			this.padding = Padding.empty();
		else
			this.padding = padding;

		// Correct tile sizes, if required
		correctTileSize();
	}

	private void correctTileSize() {
		var bundle = loadBundle(modelPath);
		if (!bundle.singleInput() || !bundle.singleOutput()) {
			logger.warn("Only a single input & output supported for an op!");
			logger.warn("Any other input/output will be dropped from {}", bundle);
		}
		var inputShape = bundle.getInput().getShape();
		long width = inputShape[2];
		long height = inputShape[1];
		if (width > 0 && width != tileWidth) {
			logger.warn("Updating tile width from {} to {}", tileWidth, width);
			tileWidth = (int)width;
		} else if (tileWidth <= 0) {
			logger.warn("Setting default tile width: {}", DEFAULT_TILE_SIZE);
			tileWidth = DEFAULT_TILE_SIZE;
		}

		if (height > 0 && height != tileHeight) {
			logger.warn("Updating tile height from {} to {}", tileHeight, height);
			tileHeight = (int)height;
		} else if (tileHeight <= 0) {
			logger.warn("Setting default tile height: {}", DEFAULT_TILE_SIZE);
			tileHeight = DEFAULT_TILE_SIZE;
		}
	}


	private TensorFlowBundle getBundle() {
		if (bundle == null && exception == null) {
			try {
				bundle = loadBundle(modelPath);
			} catch (Exception e) {
				logger.error("Unable to load bundle: " + e.getLocalizedMessage(), e);
				this.exception = e;
			}
		}
		return bundle;
	}

	// Not needed
	@Override
	protected Padding calculatePadding() {
		return padding;
	}

	@Override
	protected Mat transformPadded(Mat input) {
		var bundle = getBundle();
		if (exception != null)
			throw new RuntimeException(exception);

		String inputName = bundle.getInput().getName();
		String outputName2 = this.outputName == null ? bundle.getOutput().getName() : this.outputName;

		if (tileWidth > 0 && tileHeight > 0)
			return OpenCVTools.applyTiled(m -> run(bundle.bundle.session().runner(), m, inputName, outputName2), input, tileWidth, tileHeight, opencv_core.BORDER_REFLECT);
		else
			return run(bundle.bundle.session().runner(), input, inputName, outputName2);
	}


	private static Mat run(Runner runner, Mat mat, String inputName, String outputName) {

		if (mat.depth() != opencv_core.CV_32F) {
			var mat2 = new Mat();
			mat.convertTo(mat2, opencv_core.CV_32F);
			mat = mat2;
		}

		Mat result;
		try (var tensor = TensorFlowTools.convertToTensor(mat)) {

			List<Tensor> outputs = runner
					.feed(inputName, tensor)
					.fetch(outputName)
					.run();

			logger.debug("Number of outputs: {}", outputs.size());
			var outputTensor = outputs.get(0);
			result = TensorFlowTools.convertToMat(outputTensor);

			for (var output : outputs)
				output.close();

			return result;
		}
	}



	@Override
	public Padding getPadding() {
		return super.getPadding();
//        return Padding.empty();
    }

    @Override
   public List<ImageChannel> getChannels(List<ImageChannel> channels) {
        var names = new ArrayList<String>();
        var bundle = getBundle();
        var output = bundle.getOutput();
        long[] shape;
        String name = outputName;
        if (outputName == null || outputName.equals(output.getName())) {
        	name = output.getName();
        	shape = output.getShape();
        } else
        	shape = bundle.getOutputShape(outputName);
        if (shape == null) {
        	logger.warn("Cannot determine number of output channels - output shape is unknown!");
        	return channels;
        }
        var nChannels = shape[shape.length-1];
        for (int i = 0; i < nChannels; i++)
            names.add(name + " " + i);
        return ImageChannel.getChannelList(names.toArray(String[]::new));
    }




    private static Map<String, TensorFlowBundle> cachedBundles = new HashMap<>();

    private static TensorFlowBundle loadBundle(String path) {
    	return cachedBundles.computeIfAbsent(path, p -> new TensorFlowBundle(p));
    }


    private static class TensorFlowBundle {

    	private final static Logger logger = LoggerFactory.getLogger(TensorFlowBundle.class);

    	private String pathModel;
        private SavedModelBundle bundle;

        private List<SimpleTensorInfo> inputs;
        private List<SimpleTensorInfo> outputs;

    	private MetaGraphDef metaGraphDef;

    	private TensorFlowBundle(String pathModel) {

    		this.pathModel = pathModel;

    		var dir = new File(pathModel);
    		if (!dir.exists()) {
    			throw new IllegalArgumentException(pathModel + " does not exist!");
    		} else if (!dir.isDirectory()) {
    			throw new IllegalArgumentException(pathModel + " is not a valid TensorFlow model directory!");
    		}


    		// Load the bundle
    		bundle = SavedModelBundle.load(pathModel, "serve");

    		metaGraphDef = bundle.metaGraphDef();
			
    		for (var entry : metaGraphDef.getSignatureDefMap().entrySet()) {
    			var sigdef = entry.getValue();
    			if (inputs == null || inputs.isEmpty()) {
    				logger.info("Found SignatureDef: {} (method={})", entry.getKey(), sigdef.getMethodName());
    				inputs = sigdef.getInputsMap().values().stream().map(t -> new SimpleTensorInfo(t)).collect(Collectors.toList());
    				outputs = sigdef.getOutputsMap().values().stream().map(t -> new SimpleTensorInfo(t)).collect(Collectors.toList());
    			} else {
    				logger.warn("Extra SignatureDef found - will be ignored ({}, method={})", entry.getKey(), sigdef.getMethodName());
    			}
    		}

    		if (inputs.size() != 1) {
    			logger.warn("Inputs: {}", inputs);
    		}
    		if (outputs.size() != 1) {
    			logger.warn("Outputs: {}", outputs);
    		}

        	logger.info("Loaded {}", this);
        }

    	/**
    	 * Get the path to the model (a directory).
    	 * @return
    	 */
    	public String getModelPath() {
    		return pathModel;
    	}

    	public long[] getOutputShape(String name) {
    		var op = bundle.graph().operation(name);
    		if (op == null)
    			return null;
    		int nOutputs = op.numOutputs();
    		if (nOutputs > 1) {
    			logger.warn("Operation {} has {} outputs!", name, nOutputs);
    		} else if (nOutputs == 0)
    			return new long[0];
    		var shapeObject = op.output(0).shape();
    		long[] shape = new long[shapeObject.numDimensions()];
    		for (int i = 0; i < shape.length; i++)
    			shape[i] = shapeObject.size(i);
    		return shape;
    	}

    	/**
    	 * Get information about the first required output (often the only one).
    	 * @return
    	 */
    	public SimpleTensorInfo getInput() {
    		return inputs == null || inputs.isEmpty() ? null : inputs.get(0);
    	}

    	/**
    	 * Get information about all required inputs, or an empty list if no information is available.
    	 * @return
    	 */
    	public List<SimpleTensorInfo> getInputs() {
    		return inputs == null ? Collections.emptyList() : Collections.unmodifiableList(inputs);
    	}

    	/**
    	 * Get the first provided output (often the only one).
    	 * @return
    	 */
    	public SimpleTensorInfo getOutput() {
    		return outputs == null || outputs.isEmpty() ? null : outputs.get(0);
    	}

    	/**
    	 * Get information about all provided outputs, or an empty list if no information is available.
    	 * @return
    	 */
    	public List<SimpleTensorInfo> getOutputs() {
    		return outputs == null ? Collections.emptyList() : Collections.unmodifiableList(outputs);
    	}

    	/**
    	 * Returns true if the model takes a single input.
    	 * @return
    	 */
    	public boolean singleInput() {
    		return inputs != null && inputs.size() == 1;
    	}

    	/**
    	 * Returns true if the model provides a single output.
    	 * @return
    	 */
    	public boolean singleOutput() {
    		return outputs != null && outputs.size() == 1;
    	}

        @Override
        public String toString() {
        	if (singleInput() && singleOutput())
            	return String.format("TensorFlow bundle: %s, (input=%s, output=%s)",
            			getModelPath(), getInput(), getOutput());
        	return String.format("TensorFlow bundle: %s, (inputs=%s, outputs=%s)",
        			getModelPath(), getInputs(), getOutputs());
//        	return String.format("TensorFlow bundle: %s, (input%s [%s], output=%s [%s])",
//        			pathModel, inputName, arrayToString(inputShape), outputName, arrayToString(outputShape));
        }

    }

    /**
     * Helper class for parsing the essential info for an input/output tensor.
     */
    public static class SimpleTensorInfo {

    	private TensorInfo info;
    	private String name;
    	private long[] shape;

    	SimpleTensorInfo(TensorInfo info) {
    		this.info = info;
    		this.name = info.getName();
			if (info.hasTensorShape()) {
				var dims = info.getTensorShape().getDimList();
				shape = new long[dims.size()];
				for (int i = 0; i < dims.size(); i++) {
					var d = dims.get(i);
					shape[i] = d.getSize();
				}
			}
    	}

    	TensorInfo getInfo() {
    		return info;
    	}

    	/**
    	 * Get any name associated with the tensor.
    	 * @return
    	 */
    	public String getName() {
    		return name;
    	}

    	/**
    	 * Get the tensor shape as an array of long.
    	 * @return
    	 */
    	public long[] getShape() {
    		return shape == null ? null : shape.clone();
    	}

    	@Override
    	public String toString() {
    		if (shape == null) {
    			return name + " (no shape)";
    		} else {
    			return name + " (" +
    					LongStream.of(shape).mapToObj(l -> Long.toString(l)).collect(Collectors.joining(", "))
    							+ ")";
    		}
    	}

    }


}
