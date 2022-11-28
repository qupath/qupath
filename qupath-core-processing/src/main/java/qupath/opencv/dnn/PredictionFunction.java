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

import java.util.Collections;
import java.util.Map;

import qupath.lib.io.GsonTools;

/**
 * Prediction function, typically used with a deep learning framework.
 * <p>
 * The primary intention of this interface is to provide a means to integrate machine learning libraries 
 * into existing QuPath pipelines that use OpenCV for processing (e.g. object or pixel classification).
 * <p>
 * Note that, where possible, implementations should support JSON serialization via Gson if they are 
 * intended to support serialization.
 * 
 * @author Pete Bankhead
 * @param <T> 
 * @see BlobFunction
 * @see DnnModel
 * @see GsonTools
 * @version 0.3.0
 */
public interface PredictionFunction<T> {
	
	/**
	 * Default name to use for single input.
	 */
	public static final String DEFAULT_INPUT_NAME = "input";
	
	/**
	 * Default name to use for single output.
	 */
	public static final String DEFAULT_OUTPUT_NAME = "output";
	
	/**
	 * Call a function that takes one or more inputs to produce zero or more outputs.
	 * @param input map of input names and blobs
	 * @return map of output names and blobs
	 * @implNote The default implementation supports only a single input, passing it (regardless of name) to
	 *           {@link #predict(Object)}. It returns either an empty map, or a map with a single entry named "output".
	 */
	public default Map<String, T> predict(Map<String, T> input) {
		if (input.size() == 1) {
			var output = predict(input.values().iterator().next());
			if (output == null)
				return Collections.emptyMap();
			return Map.of(DEFAULT_OUTPUT_NAME, output);
		}
		throw new IllegalArgumentException("Default call implementation only supports single input and single output!");
	}

	/**
	 * Call a function that takes a single input and provides a single output.
	 * 
	 * @param input input to the function
	 * @return output of the function
	 */
	public T predict(T input);
	
	/**
	 * Get the required inputs.
	 * <p>
	 * Often, this is a singleton map with key {@link #DEFAULT_INPUT_NAME} for functions that take a single input.
	 * <p>
	 * If the shape is known, the axis order is typically NCHW.
	 * NCHW is used by OpenCV https://docs.opencv.org/4.5.2/d6/d0f/group__dnn.html#ga29f34df9376379a603acd8df581ac8d7
	 * and also by PyTorch; for TensorFlow some rearrangement may be needed.
	 * 
	 * @return
	 */
	public Map<String, DnnShape> getInputs();
	
	/**
	 * Get the output names mapped to the output shapes.
	 * <p>
	 * Often, this is a singleton map with key {@link #DEFAULT_OUTPUT_NAME} for functions that provide a single output.
	 * 
	 * @param inputShapes optional input shapes; if not provided, the output shapes are generally {@link DnnShape#UNKNOWN_SHAPE}
	 * @return
	 */
	public Map<String, DnnShape> getOutputs(DnnShape... inputShapes);
	
	

}
