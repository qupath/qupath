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
import java.util.Arrays;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TFloat64;
import org.tensorflow.types.TInt32;
import org.tensorflow.types.TUint8;

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
	public static <T> Tensor convertToTensor(Mat mat) throws IllegalArgumentException {
	    int w = mat.cols();
	    int h = mat.rows();
	    int nBands = mat.channels();
	    var shape = Shape.of(1, h, w, nBands);
	    
	    if (!mat.isContinuous())
	    	logger.warn("Converting non-continuous Mat to Tensor!");
	    
	    int depth = mat.depth();
	    if (depth == opencv_core.CV_32F) {
	    	FloatBuffer buffer = mat.createBuffer();
	    	return TFloat32.tensorOf(shape, DataBuffers.of(buffer));
	    }
	    if (depth == opencv_core.CV_64F) {
	    	DoubleBuffer buffer = mat.createBuffer();
	    	return TFloat64.tensorOf(shape, DataBuffers.of(buffer));
	    }
	    if (depth == opencv_core.CV_32S) {
	    	IntBuffer buffer = mat.createBuffer();
	    	return TInt32.tensorOf(shape, DataBuffers.of(buffer));
	    }
	    if (depth == opencv_core.CV_8U) {
	    	ByteBuffer buffer = mat.createBuffer();
	    	return TUint8.tensorOf(shape, DataBuffers.of(buffer));
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
	public static Mat convertToMat(Tensor tensor) {
		long[] shape = tensor.shape().asArray();
	    // Get the shape, stripping off the batch
	    int n = shape.length;
	    int[] dims = new int[Math.max(3, n-1)];
//	    int[] dims = new int[n-1];
	    Arrays.fill(dims, 1);
	    for (int i = 1; i < n; i++) {
	    	dims[i-1] = (int)shape[i];
	    }
	    // Get total number of elements (pixels)
	    int size = 1;
	    for (long d : dims)
	    	size *= d;
	    Mat mat = null;
	    switch (tensor.dataType()) {
		case DT_BFLOAT16:
			break;
		case DT_BOOL:
			break;
		case DT_COMPLEX128:
			break;
		case DT_COMPLEX64:
			break;
		case DT_DOUBLE:
		    mat = new Mat(dims[0], dims[1], opencv_core.CV_64FC(dims[2]));
		    DoubleBuffer buffer64F = mat.createBuffer();
		    if (buffer64F.hasArray())
			    tensor.asRawTensor().data().asDoubles().read(buffer64F.array());
		    else {
			    double[] values = new double[size];
			    tensor.asRawTensor().data().asDoubles().read(values);
			    buffer64F.put(values);
		    }
		    return mat;
		case DT_FLOAT:
		    mat = new Mat(dims[0], dims[1], opencv_core.CV_32FC(dims[2]));
		    FloatBuffer buffer32F = mat.createBuffer();
		    if (buffer32F.hasArray())
			    tensor.asRawTensor().data().asFloats().read(buffer32F.array());
		    else {
			    float[] values = new float[size];
			    tensor.asRawTensor().data().asFloats().read(values);
			    buffer32F.put(values);
		    }
		    return mat;
		case DT_HALF:
			break;
		case DT_INT16:
			break;
		case DT_INT32:
			mat = new Mat(dims[0], dims[1], opencv_core.CV_32SC(dims[2]));
		    IntBuffer buffer32S = mat.createBuffer();
		    if (buffer32S.hasArray())
			    tensor.asRawTensor().data().asInts().read(buffer32S.array());
		    else {
		    	int[] values = new int[size];
			    tensor.asRawTensor().data().asInts().read(values);
			    buffer32S.put(values);
		    }
		    return mat;
		case DT_INT64:
			break;
		case DT_INT8:
			break;
		case DT_INVALID:
			break;
		case DT_QINT16:
			break;
		case DT_QINT32:
			break;
		case DT_QINT8:
			break;
		case DT_QUINT16:
			break;
		case DT_QUINT8:
			break;
		case DT_RESOURCE:
			break;
		case DT_STRING:
			break;
		case DT_UINT16:
			break;
		case DT_UINT32:
			break;
		case DT_UINT64:
			break;
		case DT_UINT8:
			mat = new Mat(dims[0], dims[1], opencv_core.CV_8UC(dims[2]));
		    ByteBuffer buffer8U = mat.createBuffer();
		    if (buffer8U.hasArray())
			    tensor.asRawTensor().data().read(buffer8U.array());
		    else {
		    	byte[] values = new byte[size];
			    tensor.asRawTensor().data().read(values);
			    buffer8U.put(values);
		    }
		    return mat;
		case DT_VARIANT:
			break;
		case UNRECOGNIZED:
			break;
		default:
			break;
	    }
	    throw new UnsupportedOperationException("Unsupported Tensor to Mat conversion for DataType " + tensor.dataType());
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
