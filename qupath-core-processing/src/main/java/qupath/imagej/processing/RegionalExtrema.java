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

package qupath.imagej.processing;

import java.awt.Rectangle;

import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * Calculate the regional maximum in an image, with the help of morphological reconstruction.
 * 
 * @author Pete Bankhead
 *
 */
public class RegionalExtrema {
	
	
	/**
	 * Replace all potential local maxima - as determined by effectively comparing the image with itself after
	 * applying a 3x3 maximum filter - with the lowest possible value via setf(x, y, Float.NEGATIVE_INFINITY).
	 * These can then be filled in by morphological reconstruction on the way to finding 'true' maxima.
	 * 
	 * @param ip
	 * @param threshold
	 * @param x1
	 * @param x2
	 * @param y1
	 * @param y2
	 * @return
	 */
	private static ImageProcessor getMaximaLabels(ImageProcessor ip, float threshold, int x1, int x2, int y1, int y2) {
		float minVal = (ip instanceof FloatProcessor) ? Float.NEGATIVE_INFINITY : 0;
		ImageProcessor ip2 = ip.duplicate();
		for (int y = y1+1; y < y2-1; y++) {
			float val = ip.getf(x1, y);
			float nextVal = ip.getf(x1+1, y);
			for (int x = x1+1; x < x2-1; x++) {
				float lastVal = val;
				val = nextVal;
				nextVal = ip.getf(x+1, y);
				if (val < threshold || val < lastVal || val < nextVal)
					continue;
				// We have a value >= its horizontal neighbours... now test the verticals
				if (val >= ip.getf(x-1, y-1) && val >= ip.getf(x, y-1) && val >= ip.getf(x+1, y-1) && 
						val >= ip.getf(x-1, y+1) && val >= ip.getf(x, y+1) && val >= ip.getf(x+1, y+1))
					ip2.setf(x, y, minVal);
			}
		}
		ip2.setRoi(ip.getRoi());
		return ip2;
	}
	
	
	public static ImageProcessor findRegionalMaxima(ImageProcessor ip, float threshold, boolean outputBinary) {
//		float minVal = (ip instanceof FloatProcessor) ? Float.NEGATIVE_INFINITY : 0;

		Rectangle bounds = ip.getRoi();
		int x1, x2, y1, y2;
		if (bounds == null) {
			x1 = 0;
			x2 = ip.getWidth();
			y1 = 0;
			y2 = ip.getHeight();
		} else {
			x1 = bounds.x;
			x2 = bounds.x + bounds.width;
			y1 = bounds.y;
			y2 = bounds.y + bounds.height;
		}
		
		
//		long startTime = System.currentTimeMillis();
		ImageProcessor ip2 = getMaximaLabels(ip, threshold, x1, x2, y1, y2);
		

		MorphologicalReconstruction.morphologicalReconstruction(ip2, ip);
		
		
		// Determine the height of the maxima
		ImageProcessor ipOutput;
		if (outputBinary)
			ipOutput = SimpleThresholding.greaterThan(ip, ip2);
		else {
			ip2.copyBits(ip, 0, 0, Blitter.DIFFERENCE);
			ipOutput = ip2;
		}
		
//		// Apply a mask, if there is one
//		byte[] mask = ip.getMaskArray();
//		if (mask != null) {
//			for (int i = 0; i < mask.length; i++)
//				if (mask[i] == 0)
//					ipOutput.set(i, 0);
//		}
		return ipOutput;
	}
	
	

}
