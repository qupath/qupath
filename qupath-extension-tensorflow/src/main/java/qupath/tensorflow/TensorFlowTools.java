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
import java.nio.FloatBuffer;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.tensorflow.Tensor;
import org.bytedeco.tensorflow.TensorShape;
import org.bytedeco.tensorflow.global.tensorflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.regions.Padding;
import qupath.opencv.ops.ImageOp;

/**
 * Helper methods for working with TensorFlow and QuPath, with the help of OpenCV.
 * 
 * @author Pete Bankhead
 */
public class TensorFlowTools {
	
	private final static Logger logger = LoggerFactory.getLogger(TensorFlowTools.class);

	/**
	 * Convert a {@link Mat} to a {@link Tensor}.
	 * <p>
	 * Currently this is rather limited in scope:
	 * <ul>
	 *   <li>output is TF_FLOAT (regardless of input)</li>
	 *   <li>input is assumed to be a 'standard' image (rows, columns, channels); output will have 1 pre-pended as a batch</li>
	 * </ul>
	 * This method may be replaced by something more customizable in the future. 
	 *
	 * @param mat the input {@link Mat}
	 * @return the converted {@link Tensor}
	 */
	public static Tensor convertToTensor(Mat mat) {
	    int w = mat.cols();
	    int h = mat.rows();
	    int nBands = mat.channels();
	    long[] shape = new long[] {1, h, w, nBands};
	    if (mat.depth() != opencv_core.CV_32F) {
	        var mat2 = new Mat();
	        mat.convertTo(mat2, opencv_core.CV_32F);
	        mat = mat2;
	    }
	    var tensor = new Tensor(tensorflow.TF_FLOAT, new TensorShape(shape));
	    FloatBuffer matBuffer = mat.createBuffer();
	    FloatBuffer tensorBuffer = tensor.createBuffer();
	    tensorBuffer.put(matBuffer);
	    return tensor;
	}

	/**
	 * Convert a {@link Tensor} to a {@link Mat}.
	 * Currently this is rather limited in scope:
	 * <ul>
	 *   <li>both input and output must be 32-bit floating point</li>
	 *   <li>the output is expected to be a 'standard' image (rows, columns, channels)</li>
	 * </ul>
	 * This method may be replaced by something more customizable in the future. 
	 * 
	 * @param tensor
	 * @return
	 */
	public static Mat convertToMat(Tensor tensor) {
	    var shape = tensor.shape().dim_sizes();
	    int n = (int)shape.size();
	    // Get the shape, stripping off the batch
	    int[] dims = new int[Math.max(3, n-1)];
	    for (int i = 1; i < n; i++) {
	    	dims[i-1] = (int)shape.get(i);
	    }	        	
	    Mat mat;
	    if (n <= 4) {
	    	int h = dims[0];
	    	int w = dims[1];
	    	int c = dims[2];
	    	mat = new Mat(h, w, opencv_core.CV_32FC(c));
	    } else {
	        mat = new Mat(dims, opencv_core.CV_32F);
	    }
	    transferBuffers(tensor.createBuffer(), mat.createBuffer());
	    return mat;
	}
	
	private static void transferBuffers(FloatBuffer bufferSource, FloatBuffer bufferTarget) {
    	bufferTarget.put(bufferSource);
    }

	/**
	 * Create an {@link ImageOp} to run a TensorFlow model with a single image input and output, 
	 * optionally specifying the input tile width and height.
	 * 
	 * @param modelPath
	 * @param tileWidth input tile width; ignored if &le; 0
	 * @param tileHeight input tile height; ignored if &le; 0
	 * @param padding amount of padding to add to each request
	 * @return the {@link ImageOp}
	 * @throws IllegalArgumentException if the model path is not a directory
	 */
	public static ImageOp createOp(String modelPath, int tileWidth, int tileHeight, Padding padding) throws IllegalArgumentException {
		var file = new File(modelPath);
		if (!file.isDirectory()) {
			logger.error("Invalid model path, not a directory! {}", modelPath);
			throw new IllegalArgumentException("Model path should be a directory!");
		}
		return new TensorFlowOp(modelPath, tileWidth, tileHeight, padding);
	}

}