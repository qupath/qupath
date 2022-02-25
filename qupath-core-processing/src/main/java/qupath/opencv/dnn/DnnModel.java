/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.LoggerFactory;


/**
 * Wrapper for a deep learning model in a pipeline using OpenCV.
 * It can encapsulate a custom method needed to convert the input Mat(s) into the appropriate format, 
 * and the output back into one or more Mats.
 * <p>
 * This provides convenience methods to both convert and predict for three common scenarios:
 * <ul>
 *   <li>Single input, single output; batch size 1</li>
 *   <li>Single or multiple inputs, single or multiple outputs; batch size 1</li>
 *   <li>Single input, single output; batch size &gt; 1</li>
 * </ul>
 * 
 * 
 * @author Pete Bankhead
 * @param <T> 
 * @see BlobFunction
 * @see PredictionFunction
 * @version 0.3.0
 */
public interface DnnModel<T> extends AutoCloseable {
		
	/**
	 * Default input layer name. This should be used when the input layer name is known or 
	 * unimportant (e.g. the common case of a single input).
	 */
	public static final String DEFAULT_INPUT_NAME = "input";
	
	/**
	 * Default output layer name. This should be used when the output layer name is known or 
	 * unimportant (e.g. the common case of a single output).
	 */
	public static final String DEFAULT_OUTPUT_NAME = "output";
	
	/**
	 * Get the function that can convert one or more OpenCV Mats into a blob supported by the prediction function 
	 * for the first (or only) input.
	 * @return
	 */
	public BlobFunction<T> getBlobFunction();
	
	/**
	 * Get the function that can convert one or more OpenCV Mats into a blob supported by the prediction function for 
	 * a specified input layer.
	 * @param name 
	 * @return
	 */
	public BlobFunction<T> getBlobFunction(String name);
	
	/**
	 * Get the prediction function that can apply a prediction with one or more blobs as input.
	 * @return
	 */
	public PredictionFunction<T> getPredictionFunction();
	
	/**
	 * Convenience method to convert input image patches to a blobs, apply a {@link PredictionFunction} (optionally with multiple inputs/outputs),
	 * and convert the output to a standard {@link Mat}.
	 * <p>
	 * Note that this only supports a batch size of 1. For larger batches or more control, {@link #getBlobFunction(String)} and 
	 * {@link #getPredictionFunction()} should be used directly.
	 * 
	 * @param blobs
	 * @return
	 */
	public default Map<String, Mat> convertAndPredict(Map<String, Mat> blobs) {
		
		var input = blobs.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> getBlobFunction(e.getKey()).toBlob(e.getValue())));
		var prediction = getPredictionFunction().predict(input);
		
		// TODO: Consider warning for multiple outputs (although shouldn't happen because conversion is also covered within this method)
		var output = prediction.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> getBlobFunction(e.getKey()).fromBlob(e.getValue()).get(0)));
		
		return output;
	}

	/**
	 * Convenience method to convert a single image patch to a blob, apply the {@link PredictionFunction}, and convert the output to a standard {@link Mat}.
	 * <p>
	 * Note that this only supports a batch size of 1. For larger batches or more control, {@link #getBlobFunction(String)} and 
	 * {@link #getPredictionFunction()} should be used directly.
	 * 
	 * @param mat
	 * @return
	 */
	public default Mat convertAndPredict(Mat mat) {
		var blob = getBlobFunction().toBlob(mat);
		var prediction = getPredictionFunction().predict(blob);
		var output = getBlobFunction().fromBlob(prediction);
		if (output.size() > 1)
			LoggerFactory.getLogger(DnnModel.class).warn("Expected single output but got {} outputs - I will return the first only", output.size());
		return output.get(0);
	}
	
	/**
	 * Convenience method to convert one or more image patches to a blob, apply the {@link PredictionFunction}, and convert the output to standard Mats.
	 * This method is intended for cases where the batch size should be greater than one; for a batch size of one, {@link #convertAndPredict(Mat)} can 
	 * be used instead.
	 * @param mats
	 * @return
	 */
	public default List<Mat> batchConvertAndPredict(Mat... mats) {
		var blob = getBlobFunction().toBlob(mats);
		var prediction = getPredictionFunction().predict(blob);
		var output = getBlobFunction().fromBlob(prediction);
		return output;
	}
	
	/**
	 * Close this model if it will not be needed again.
	 * Subclasses that require cleanup may override this.
	 * A default, do-nothing implementation implementation is provided for backwards compatibility with v0.3.0.
	 * 
	 * @since v0.3.1
	 * @implNote this was introduced to provide a mechanism to deal with a GPU memory leak when 
	 *           using OpenCV and CUDA. New code using any DnnModel should call this method if it can 
	 *           be known that the model will not be needed again in the future.
	 */
	@Override
	public default void close() throws Exception {}
	
}
