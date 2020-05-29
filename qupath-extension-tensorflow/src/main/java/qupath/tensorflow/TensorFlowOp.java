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
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.tensorflow.RunOptions;
import org.bytedeco.tensorflow.SavedModelBundle;
import org.bytedeco.tensorflow.SessionOptions;
import org.bytedeco.tensorflow.StringTensorPairVector;
import org.bytedeco.tensorflow.StringUnorderedSet;
import org.bytedeco.tensorflow.StringVector;
import org.bytedeco.tensorflow.Tensor;
import org.bytedeco.tensorflow.TensorShapeProto;
import org.bytedeco.tensorflow.TensorVector;
import org.bytedeco.tensorflow.global.tensorflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
	private String modelPath;
	private int tileWidth = 512;
	private int tileHeight = 512;
	
	private Padding padding;
	
	private transient TensorFlowBundle bundle;
	private transient Exception exception;

	TensorFlowOp(String modelPath, int tileWidth, int tileHeight, Padding padding) {
		super();
		logger.debug("Creating op from {}", modelPath);
		this.modelPath = modelPath;
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		if (padding == null)
			this.padding = Padding.empty();
		else
			this.padding = padding;
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
		if (tileWidth > 0 && tileHeight > 0)
			return OpenCVTools.applyTiled(m -> bundle.run(m), input, tileWidth, tileHeight, opencv_core.BORDER_REFLECT);
		else
			return bundle.run(input);
	}
	
	
	@Override
	public Padding getPadding() {
        return Padding.empty();
    }

    @Override
   public List<ImageChannel> getChannels(List<ImageChannel> channels) {
        var names = new ArrayList<String>();
        var name = getBundle().outputName;
        var outputShape = getBundle().outputShape;
        var nChannels = outputShape[outputShape.length-1];
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

        private SavedModelBundle bundle;
        private String inputName;

        private String outputName;
        private long[] inputShape;
    	private long[] outputShape;

    	private TensorFlowBundle(String pathModel) {
    		
    		var dir = new File(pathModel);
    		if (!dir.exists()) {
    			throw new IllegalArgumentException(pathModel + " does not exist!");
    		} else if (!dir.isDirectory() || dir.listFiles((FileFilter)f -> f.isFile() && f.getName().toLowerCase().endsWith(".pb")).length == 0) {
    			throw new IllegalArgumentException(pathModel + " is not a valid TensorFlow model directory!");    			
    		}
    		
            // If this fails, are both the main jars and the platform-specific jars present - for TensorFlow and MKL-DNN?
            var sessionOptions = new SessionOptions();

            var runOptions = new RunOptions();
            var tags = new StringUnorderedSet();
            tags.insert(tensorflow.kSavedModelTagServe());
            bundle = new SavedModelBundle();
            tensorflow.LoadSavedModel(
                    sessionOptions,
                    runOptions,
                    pathModel,
                    tags,
                    bundle
            );
            
            var sigdefMap = bundle.meta_graph_def().signature_def();
            logger.debug("Size: {}", sigdefMap.size());
            var map = sigdefMap.get(sigdefMap.begin().first());
            
            long nInputs = map.inputs().size();
            if (nInputs != 1) {
            	logger.warn("Only one input currently supported, but model supports {}", nInputs);
            }
            long nOutputs = map.outputs().size();
            if (nOutputs != 1) {
            	logger.warn("Only one output currently supported, but model supports {}", nOutputs);
            }

            var input = map.inputs().get(map.inputs().begin().first());
            var output = map.outputs().get(map.outputs().begin().first());
            
            inputName = input.name().getString();
            outputName = output.name().getString();
            
            inputShape = tensorShape(input.tensor_shape());
            outputShape = tensorShape(output.tensor_shape());
        }
        
    	private static long[] tensorShape(TensorShapeProto shape) {
        	long[] dims = new long[shape.dim_size()];
        	for (int i = 0; i < dims.length; i++) {
        		dims[i] = shape.dim(i).size();
        	}
        	return dims;
        }
        

        private Mat run(Mat mat) {
            var tensor = TensorFlowTools.convertToTensor(mat);

            var outputs = new TensorVector();
            var inputs = new StringTensorPairVector(
                    new String[] {inputName},
                    new Tensor[] {tensor}
            );

            var outputNames = new StringVector(outputName);
            var targetNodeNames = new StringVector();
            var status = bundle.session().Run(
                    inputs,
                    outputNames,
                    targetNodeNames,
                    outputs
            );
            var error = status.error_message();
            if (error != null)
                logger.error(error.getString());

            logger.debug("Number of outputs: {}", outputs.size());
            var outputTensor = outputs.get(0L);
            var output = TensorFlowTools.convertToMat(outputTensor);
            
            inputs.close();
            outputNames.close();
            targetNodeNames.close();
            outputTensor.close();
            
            return output;
        }

    }

	
}