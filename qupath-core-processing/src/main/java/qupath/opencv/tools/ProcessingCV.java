/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.opencv.tools;

import static org.bytedeco.opencv.global.opencv_core.CV_32F;

import java.nio.FloatBuffer;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;

import qupath.lib.analysis.algorithms.MorphologicalReconstruction;
import qupath.lib.analysis.algorithms.Watershed;
import qupath.lib.analysis.images.SimpleModifiableImage;

/**
 * Static methods to enable existing code for watershed transforms and morphological reconstruction
 * to be applied to OpenCV images.
 * 
 * @author Pete Bankhead
 *
 */
public class ProcessingCV {

	/**
	 * Compute morphological reconstruction.
	 * @param matMarker
	 * @param matMask
	 * @return
	 */
	public static boolean morphologicalReconstruction(Mat matMarker, Mat matMask) {
		// Create simple pixel images
		PixelImageCV imMarker = new PixelImageCV(matMarker);
		PixelImageCV imMask = new PixelImageCV(matMask);
		boolean success = MorphologicalReconstruction.morphologicalReconstruction(imMarker, imMask);
		if (!success)
			return false;
		// Ensure pixels are set properly, then return
		imMarker.put(matMarker);
		return true;
	}

	/**
	 * Apply a watershed transform.
	 * @param mat intensity image
	 * @param matLabels starting locations
	 * @param conn8 if true, use 8-connectivity rather than 4-connectivity
	 */
	public static void doWatershed(Mat mat, Mat matLabels, boolean conn8) {
		ProcessingCV.doWatershed(mat, matLabels, Double.NEGATIVE_INFINITY, conn8);
	}

	/**
	 * Apply an intensity-constrained watershed transform, preventing regions from expanding to pixels below a specified minimum threshold
	 * @param mat intensity image
	 * @param matLabels starting locations
	 * @param minThreshold minimum threshold
	 * @param conn8 if true, use 8-connectivity rather than 4-connectivity
	 */
	public static void doWatershed(Mat mat, Mat matLabels, double minThreshold, boolean conn8) {
		PixelImageCV ip = new PixelImageCV(mat);
		PixelImageCV ipLabels = new PixelImageCV(matLabels);
		Watershed.doWatershed(ip, ipLabels, minThreshold, conn8);
		ipLabels.put(matLabels);
	}
	
	
	
	/**
	 * A very simple class to access pixel values from a 1-channel OpenCV Mat.
	 * <p>
	 * Be warned: it does no error checking and is likely to fail for many datatypes...
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class PixelImageCV implements SimpleModifiableImage {
		
		private float[] pixels;
		private int width;
		private int height;
		
		public PixelImageCV(Mat mat) {
			// Extract dimensions and pixels
			this.width = mat.cols();
			this.height = mat.rows();
			pixels = OpenCVTools.extractPixels(mat, null);
		}
		
		public void put(Mat mat) {
			if (mat.depth() != CV_32F) {
				Mat mat2 = new Mat(height, width, CV_32F, Scalar.ZERO);		
				mat2.convertTo(mat, mat.depth());
				FloatBuffer buffer = mat2.createBuffer();
				buffer.put(pixels);
				mat2.convertTo(mat, mat.depth());
				mat2.close();
			} else {
				FloatBuffer buffer = mat.createBuffer();
				buffer.put(pixels);
			}
		}
		
		
		@Override
		public float getValue(int x, int y) {
			return pixels[y*width + x];
		}
		
		@Override
		public void setValue(int x, int y, float val) {
			pixels[y*width + x] = val;
		}
		
		@Override
		public int getWidth() {
			return width;
		}
		
		@Override
		public int getHeight() {
			return height;
		}

		@Override
		public float[] getArray(boolean direct) {
			if (direct)
				return pixels;
			return pixels.clone();
		}
		
	}

}
