/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.opencv.processing;

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

	public static boolean morphologicalReconstruction(Mat ipMarker, Mat ipMask) {
		// Create simple pixel images
		PixelImageCV imMarker = new PixelImageCV(ipMarker);
		PixelImageCV imMask = new PixelImageCV(ipMask);
		boolean success = MorphologicalReconstruction.morphologicalReconstruction(imMarker, imMask);
		if (!success)
			return false;
		// Ensure pixels are set properly, then return
		imMarker.put(ipMarker);
		return true;
	}

	public static void doWatershed(Mat mat, Mat matLabels, boolean conn8) {
		ProcessingCV.doWatershed(mat, matLabels, Double.NEGATIVE_INFINITY, conn8);
	}

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
				mat2.release();
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
		
	}

}
