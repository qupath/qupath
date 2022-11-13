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

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import qupath.lib.io.UriResource;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.tools.OpenCVTools;

class DefaultBlobFunction implements BlobFunction<Mat>, UriResource {
	
	private final ImageOp preprocessing;
	private final Size inputSize;
	private final boolean crop;

	DefaultBlobFunction(ImageOp preprocessing, Size inputSize, boolean crop) {
		this.preprocessing = preprocessing;
		this.inputSize = inputSize;
		this.crop = crop;
	}
	
	@Override
	public Mat toBlob(Mat... mats) {
		
		// Preprocess the images
		var preprocessed = new Mat[mats.length];
		int ind = 0;
		for (var mat : mats) {
			var temp = mat.clone();
			
			if (preprocessing != null) {
				temp = preprocessing.apply(temp);
			}

			if (inputSize != null) {
				if (crop) {
					int w = inputSize.width();
					int h = inputSize.height();
					double factor = Math.max(
							w / (double)temp.cols(),
							h / (double)temp.rows()
							);
					opencv_imgproc.resize(temp, temp, new Size(), factor, factor, opencv_imgproc.INTER_LINEAR);
					int x = (temp.cols() - w) / 2;
					int y = (temp.rows() - h) / 2;
					temp.put(OpenCVTools.crop(temp, x, y, w, h));
				} else {
					opencv_imgproc.resize(temp, temp, inputSize, 0, 0, opencv_imgproc.INTER_LINEAR);
				}
			}
						
			preprocessed[ind] = temp;
			ind++;
		}
		
		// Convert images to blob
		return DnnTools.blobFromImages(preprocessed);
	}

	@Override
	public List<Mat> fromBlob(Mat blob) {
		// If 4D, we have an image (including batch)
		if (blob.dims() == 4)
			return DnnTools.imagesFromBlob(blob);
		// Otherwise, we have a non-image output with the rows corresponding to batches
		// TODO: Consider whether transposing is necessary/advisable
		int nRows = blob.rows();
		if (nRows == 1)
			return Collections.singletonList(blob);
		return IntStream.range(0, nRows).mapToObj(r -> blob.row(r)).collect(Collectors.toList());
	}

	@Override
	public Collection<URI> getURIs() throws IOException {
		if (preprocessing == null)
			return Collections.emptyList();
		return preprocessing.getURIs();
	}

	@Override
	public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
		if (preprocessing == null)
			return false;
		return preprocessing.updateURIs(replacements);
	}
	
}