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

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import qupath.lib.analysis.algorithms.SimpleModifiableImage;

/**
 * A very simple class to access pixel values from a 1-channel OpenCV Mat... 
 * although, be warned, it does no error checking and is likely to fail for many datatypes...
 * 
 * @author Pete Bankhead
 *
 */
public class PixelImageCV implements SimpleModifiableImage {
	
	private float[] pixels;
	private int width;
	private int height;
	
	public PixelImageCV(Mat mat) {
		// Extract dimensions and pixels
		this.width = (int)mat.size().width;
		this.height = (int)mat.size().height;
		
		pixels = new float[(int)mat.total()];
		if (mat.depth() == CvType.CV_32F)
			mat.get(0, 0, pixels);
		else {
			Mat mat2 = new Mat();
			mat.convertTo(mat2, CvType.CV_32F);
			mat2.get(0, 0, pixels);
		}
	}
	
	public void put(Mat mat) {
		if (mat.depth() == CvType.CV_32F)
			mat.put(0, 0, pixels);
		else {
			Mat mat2 = new Mat(new Size(width, height), CvType.CV_32F);		
			mat2.put(0, 0, pixels);
			mat2.convertTo(mat, mat.depth());
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