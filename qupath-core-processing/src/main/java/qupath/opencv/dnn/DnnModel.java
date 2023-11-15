/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021-2023 QuPath developers, The University of Edinburgh
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bytedeco.opencv.opencv_core.Mat;


/**
 * General interface for implementing a deep learning model in a pipeline using OpenCV.
 * <p>
 * It can encapsulate a custom method needed to convert the input Mat(s) into the appropriate format, 
 * and the output back into one or more Mats.
 * <p>
 * Implementations should provide convenience methods to both convert and predict for three common scenarios:
 * <ul>
 *   <li>Single input, single output; batch size 1</li>
 *   <li>Single or multiple inputs, single or multiple outputs; batch size 1</li>
 *   <li>Single input, single output; batch size &gt; 1</li>
 * </ul>
 * <p>
 * If only a single input and output are required, then only {@link #predict(Mat)}
 * needs to be implemented.
 * <p>
 * <b>Note: </b>This was originally implemented in QuPath v0.3.0, but simplified for
 *              QuPath v0.5.0. It no longer takes a generic parameter or requires
 *              'blob' and 'prediction' functions to be defined.
 *              This makes it easier to implement, and also to handle memory management.
 *              If you want the old behavior, see {@link AbstractDnnModel}.
 * 
 * @author Pete Bankhead
 * @see BlobFunction
 * @see PredictionFunction
 * @since 0.5.0
 */
public interface DnnModel extends AutoCloseable {
		
	/**
	 * Default input layer name. This should be used when the input layer name is known or 
	 * unimportant (e.g. the common case of a single input).
	 */
	String DEFAULT_INPUT_NAME = "input";
	
	/**
	 * Default output layer name. This should be used when the output layer name is known or 
	 * unimportant (e.g. the common case of a single output).
	 */
	String DEFAULT_OUTPUT_NAME = "output";
	
	/**
	 * Prediction function that can take multiple inputs.
	 * @param blobs
	 * @return
	 */
	Map<String, Mat> predict(Map<String, Mat> blobs);

	/**
	 * Prediction function that takes a single input and gives a single output.
	 * @param mat
	 * @return
	 */
	default Mat predict(Mat mat) {
		return predict(Map.of(DEFAULT_INPUT_NAME, mat)).get(DEFAULT_OUTPUT_NAME);
	}
	
	/**
	 * Prediction function that can take a batch of inputs and gives a corresponding
	 * batch of outputs.
	 * Each input is expected to have a single output.
	 * @param mats
	 * @return
	 */
	default List<Mat> batchPredict(List<? extends Mat> mats) {
		List<Mat> output = new ArrayList<>();
		for (var input : mats) {
			output.add(predict(input));
		}
		return output;
	}
	
	/**
	 * Close this model if it will not be needed again.
	 * Subclasses that require cleanup may override this.
	 * The default implementation does nothing.
	 */
	@Override
	default void close() throws Exception {}
	
}
