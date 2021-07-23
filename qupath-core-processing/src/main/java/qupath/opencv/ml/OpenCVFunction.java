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

package qupath.opencv.ml;

import java.util.Collections;
import java.util.Map;

import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.io.GsonTools;

/**
 * General interface that supports calling a function on one or more OpenCV {@link Mat} objects.
 * <p>
 * The primary intention of this interface is to provide a means to integrate machine learning libraries 
 * into existing QuPath pipelines that use OpenCV for processing (e.g. object or pixel classification).
 * <p>
 * The design is inspired by TensorFlow's Java API, switching Tensor for Mat.
 * <p>
 * Note that, where possible, implementations should support JSON serialization via Gson if they are 
 * intended to support serialization.
 * 
 * @author Pete Bankhead
 * @see GsonTools
 */
public interface OpenCVFunction {
	
	/**
	 * Call a function that takes one or more inputs to produce zero or more outputs.
	 * @param input map of input names and Mat
	 * @return map out output names and Mat
	 * @implSpec The default implementation supports only a single input, passing it (regardless of name) to
	 *           {@link #call(Mat)}. It returns either an empty map, or a map with a single entry named "output".
	 */
	public default Map<String, Mat> call(Map<String, Mat> input) {
		if (input.size() == 1) {
			var output = call(input.values().iterator().next());
			if (output == null)
				return Collections.emptyMap();
			return Map.of("output", output);
		}
		throw new IllegalArgumentException("Default call implementation only supports single input and single output!");
	}

	/**
	 * Call a function that takes a single input and provides a single output.
	 * @param input input to the function
	 * @return output of the function
	 */
	public Mat call(Mat input);

}
