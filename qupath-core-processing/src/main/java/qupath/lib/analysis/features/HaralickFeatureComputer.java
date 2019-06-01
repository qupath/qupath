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

package qupath.lib.analysis.features;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.stats.RunningStatistics;

/**
 * Static methods for computing Haralick texture features.
 * 
 * @author Pete Bankhead
 *
 */
public class HaralickFeatureComputer {
	
	/**
	 * Compute mean Haralick features from a SimpleImage, with optional masking.
	 * 
	 * @param ip input image
	 * @param bpMask binary mask (may be null)
	 * @param nBins number of bins in co-occurrence matrix
	 * @param minValue minimum value for binning in the co-occurrence matrix
	 * @param maxValue maximum value for binning in the co-occurrence matrix
	 * @param d separation between pixels considered adjacent, usually 1
	 * @return
	 */
	public static HaralickFeatures measureHaralick(final SimpleImage ip, final SimpleImage bpMask, final int nBins, final double minValue, final double maxValue, final int d) {
		return measureHaralick(ip, bpMask, 0, 0, ip.getWidth(), ip.getHeight(), nBins, minValue, maxValue, d);
	}
	
	/**
	 * Compute Haralick texture features within a specified bounding box (given by xx, yy, ww, and hh).
	 * <p>
	 * A mask can also optionally be applied within the bounding box (or may be null).  Only non-zero pixels in the mask are included.
	 * 
	 * @param ip
	 * @param bpMask
	 * @param xx
	 * @param yy
	 * @param ww
	 * @param hh
	 * @param nBins
	 * @param minValue
	 * @param maxValue
	 * @param d
	 * @return
	 */
	private static HaralickFeatures measureHaralick(final SimpleImage ip, final SimpleImage bpMask, final int xx, final int yy, final int ww, final int hh, final int nBins, double minValue, double maxValue, final int d) {
		
		// If we have NaNs, compute data min & max
		if (Double.isNaN(minValue) || Double.isNaN(maxValue)) {
			RunningStatistics stats = getStatistics(ip);
			minValue = stats.getMin();
			maxValue = stats.getMax();
		}
		
		// Create & update cooccurrance matrices
		CoocurranceMatrices matricies = updateCooccurrenceMatrices(null, ip, bpMask, xx, yy, ww, hh, nBins, minValue, maxValue, d);

		// Get features
		HaralickFeatures features = matricies.getMeanFeatures();

		return features;
	}
	
	/**
	 * Update existing CoocurranceMatrices with the values in a SimpleImage.
	 * @param matrices
	 * @param ip input image
	 * @param bpMask binary mask (may be null)
	 * @param nBins number of bins in co-occurrence matrix
	 * @param minValue minimum value for binning in the co-occurrence matrix
	 * @param maxValue maximum value for binning in the co-occurrence matrix
	 * @param d separation between pixels considered adjacent, usually 1
	 * @return
	 */
	public static CoocurranceMatrices updateCooccurrenceMatrices(final CoocurranceMatrices matrices, final SimpleImage ip, final SimpleImage bpMask, final int nBins, double minValue, double maxValue, final int d) {
		return updateCooccurrenceMatrices(matrices, ip, bpMask, 0, 0, ip.getWidth(), ip.getHeight(), nBins, minValue, maxValue, d);
	}
	
	private static CoocurranceMatrices updateCooccurrenceMatrices(CoocurranceMatrices matrices, final SimpleImage ip, final SimpleImage bpMask, final int xx, final int yy, final int ww, final int hh, final int nBins, double minValue, double maxValue, final int d) {
		// Create matrices if necessary
		if (matrices == null)
			matrices = new CoocurranceMatrices(nBins);
		
		// Dimensions
		int width = ip.getWidth();
		int height = ip.getHeight();
		
		double binDepth = (maxValue - minValue) / nBins;
		
		// Loop through pixels
		boolean noMask = bpMask == null;
		for (int y = yy; y < yy + hh; y++) {
			for (int x = xx; x < xx + ww; x++) {
				if (!noMask && bpMask.getValue(x, y) == 0)
					continue;
				// Extract binned pixel value
				int binValue = getBinValue(ip, x, y, minValue, binDepth, nBins);
				if (binValue < 0) 
					continue;
				// Test neighbors
				if (x < width-d && (noMask || bpMask.getValue(x+d, y) != 0))
					matrices.put0(binValue, getBinValue(ip, x+d, y, minValue, binDepth, nBins));

				if (y < height-d && (noMask || bpMask.getValue(x, y+d) != 0))
					matrices.put90(binValue, getBinValue(ip, x, y+d, minValue, binDepth, nBins));

				// Note (Pete): The angles here may differ from the original paper
				// Switching the order may help, but note that put45 and put135 are also called in another method (below)
				// so this change has been reverted for consistency... we may want to consider modifying this (or just renaming the methods) at some point
				// Here, it is assumed that the zero angle is -> (horizontal, left to right), and rotations are clockwise
				if (x < width-d && y < height-d && (noMask || bpMask.getValue(x+d, y+d) != 0))
					matrices.put45(binValue, getBinValue(ip, x+d, y+d, minValue, binDepth, nBins)); //J check with Haralick paper

				if (x >= d && y < height-d && (noMask || bpMask.getValue(x-d, y+d) != 0))
					matrices.put135(binValue, getBinValue(ip, x-d, y+d, minValue, binDepth, nBins)); 
			}			
		}
		return matrices;
	}
	
	
	
	
	
	private static int getBinValue(SimpleImage ip, int x, int y, double minValue, double binDepth, int nBins) {
		float val = ip.getValue(x, y);
		// If we have NaN, return -1 to indicate a NaN value
		if (Float.isNaN(val))
			return -1;
		int ind = (int)((val - minValue) / binDepth);
		if (ind < 0)
			return 0;
		else if (ind >= nBins)
			return nBins-1;
		return ind;
	}
	
	
	private static RunningStatistics getStatistics(SimpleImage img) {
		RunningStatistics stats = new RunningStatistics();
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				stats.addValue(img.getValue(x, y));
			}
		}
		return stats;
	}
	
}
