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
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.types.UInt8;

import qupath.lib.regions.Padding;
import qupath.opencv.ops.ImageOp;

/**
 * Helper methods for working with TensorFlow and QuPath, with the help of OpenCV.
 * 
 * @author Pete Bankhead
 */
public class TensorFlowTools {
	
	private final static Logger logger = LoggerFactory.getLogger(TensorFlowTools.class);
	
	static {
//		Loader.load(org.bytedeco.tensorflow.presets.tensorflow.class);
		logger.info("TensorFlow version {}", TensorFlow.version());
	}


	/**
	 * Convert a {@link Mat} to a {@link Tensor}.
	 * <p>
	 * This supports only a subset of Mats according to type, namely
	 * <ul>
	 *   <li>CV_8U</li>
	 *   <li>CV_32S</li>
	 *   <li>CV_32F</li>
	 *   <li>CV_64F</li>
	 * </ul>
	 * 
	 * The input is assumed to be a 'standard' image (rows, columns, channels); output will have 1 pre-pended as a batch.
	 * This behavior may change in the future!
	 *
	 * @param mat the input {@link Mat}
	 * @return the converted {@link Tensor}
	 * 
	 * @throws IllegalArgumentException if the depth is not supported
	 */
	public static <T> Tensor<T> convertToTensor(Mat mat) throws IllegalArgumentException {
	    int w = mat.cols();
	    int h = mat.rows();
	    int nBands = mat.channels();
	    long[] shape = new long[] {1, h, w, nBands};
	    
	    if (!mat.isContinuous())
	    	logger.warn("Converting non-continuous Mat to Tensor!");
	    
	    int depth = mat.depth();
	    if (depth == opencv_core.CV_32F) {
	    	FloatBuffer buffer = mat.createBuffer();
	    	return (Tensor<T>)Tensor.create(shape, buffer);
	    }
	    if (depth == opencv_core.CV_64F) {
	    	DoubleBuffer buffer = mat.createBuffer();
	    	return (Tensor<T>)Tensor.create(shape, buffer);
	    }
	    if (depth == opencv_core.CV_32S) {
	    	IntBuffer buffer = mat.createBuffer();
	    	return (Tensor<T>)Tensor.create(shape, buffer);
	    }
	    if (depth == opencv_core.CV_8U) {
	    	ByteBuffer buffer = mat.createBuffer();
	    	return (Tensor<T>)Tensor.create(UInt8.class, shape, buffer);
	    }
	    throw new IllegalArgumentException("Unsupported Mat depth! Must be 8U, 32S, 32F or 64F.");
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
	public static Mat convertToMat(Tensor<?> tensor) {
		long[] shape = tensor.shape();
	    // Get the shape, stripping off the batch
	    int n = shape.length;
	    int[] dims = new int[Math.max(3, n-1)];
	    for (int i = 1; i < n; i++) {
	    	dims[i-1] = (int)shape[i];
	    }
	    Mat mat = null;
	    switch (tensor.dataType()) {
		case DOUBLE:
		    mat = new Mat(dims[0], dims[1], opencv_core.CV_64FC(dims[2]));
		    DoubleBuffer buffer64F = mat.createBuffer();
		    tensor.writeTo(buffer64F);
		    return mat;
		case FLOAT:
		    mat = new Mat(dims[0], dims[1], opencv_core.CV_32FC(dims[2]));
		    FloatBuffer buffer32F = mat.createBuffer();
		    tensor.writeTo(buffer32F);
			return mat;
		case INT32:
			mat = new Mat(dims[0], dims[1], opencv_core.CV_32SC(dims[2]));
		    IntBuffer buffer32S = mat.createBuffer();
		    tensor.writeTo(buffer32S);
		    return mat;
		case INT64:
			// TODO: Consider the most sensible conversion
		    logger.warn("Converting INT64 tensor to Double Mat");
		    mat = new Mat(dims[0], dims[1], opencv_core.CV_64FC(dims[2]));
		    buffer64F = mat.createBuffer();
		    int length = buffer64F.limit();
		    LongBuffer buffer64S = LongBuffer.allocate(length);
		    tensor.writeTo(buffer64S);
		    for (int i = 0; i < length; i++)
		    	buffer64F.put(i, buffer64S.get(i));
		    return mat;
		case UINT8:
		    mat = new Mat(dims[0], dims[1], opencv_core.CV_8UC(dims[2]));
		    ByteBuffer buffer8U = mat.createBuffer();
		    tensor.writeTo(buffer8U);
			return mat;
		case BOOL:
		case STRING:
		default:
		    throw new IllegalArgumentException("Cannot convert " + tensor.dataType() + " tensor to Mat!");
	    }
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
		return createOp(modelPath, tileWidth, tileHeight, padding, null);
	}
	
	/**
	 * Create an {@link ImageOp} to run a TensorFlow model with a single image input and output, 
	 * optionally specifying the input tile width and height.
	 * 
	 * @param modelPath
	 * @param tileWidth input tile width; ignored if &le; 0
	 * @param tileHeight input tile height; ignored if &le; 0
	 * @param padding amount of padding to add to each request
	 * @param outputName optional name of the node to use for output (may be null)
	 * @return the {@link ImageOp}
	 * @throws IllegalArgumentException if the model path is not a directory
	 */
	public static ImageOp createOp(String modelPath, int tileWidth, int tileHeight, Padding padding, String outputName) throws IllegalArgumentException {
		var file = new File(modelPath);
		if (!file.isDirectory()) {
			logger.error("Invalid model path, not a directory! {}", modelPath);
			throw new IllegalArgumentException("Model path should be a directory!");
		}
		return new TensorFlowOp(modelPath, tileWidth, tileHeight, padding, outputName);
	}

}