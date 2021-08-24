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

import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Convert OpenCV Mats into blobs (tensors) for use with a deep learning framework.
 * 
 * @author Pete Bankhead
 *
 * @param <T> preferred tensor format for the framework
 * 
 * @see PredictionFunction
 * @see DnnModel
 * @version 0.3.0
 */
public interface BlobFunction<T> {
	
	/**
	 * Convert one or more mats to a blob. 
	 * This is intended primarily for cases where each input mat corresponds to an image, 
	 * and the length of the input array corresponds to the batch size.
	 * @param mats
	 * @return
	 */
	public T toBlob(Mat... mats);

	/**
	 * Convert a blob (generally the result of a prediction) to a list of mats.
	 * The length of the output list corresponds to the batch size.
	 * <p>
	 * Note that while this is typically used for blobs that are images, implementing classes 
	 * should sensibly handle cases where the number of dimensions indicates a different kind of 
	 * output.
	 * 
	 * @param blob
	 * @return
	 */
	public List<Mat> fromBlob(T blob);
	
}