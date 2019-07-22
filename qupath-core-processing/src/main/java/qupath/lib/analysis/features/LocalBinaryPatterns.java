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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import qupath.lib.analysis.images.SimpleImage;

/**
 * Initial test implementation of Local Binary Patterns.
 * 
 * @author Pete Bankhead
 *
 */
public class LocalBinaryPatterns {
	
	// LUT for LBP using 8-neighbours, compressing to 36 histogram bins
	private static int[] lbf8map = new int[256];
	private static int nLBF8; // Should be 36

	// LUT for LBP using 8-neighbours, compressing to 10 histogram bins by using uniformity
	private static int[] lbf8UniformMap = new int[256];
	private static int nLBF8Uniform; // Should be 10
	
	// LUT for LBP using 8-neighbours, compressing to 10 histogram bins by using uniformity
	private static int[] lbf16UniformMap = new int[65536];
	private static int nLBF16Uniform; // Should be 18
	
	// Interpolated x & y offsets for 8 neighbours
	private static double[] xo8 = new double[8];
	private static double[] yo8 = new double[8];
	
	// Interpolated x & y offsets for 16 neighbours
	private static double[] xo16 = new double[16];
	private static double[] yo16 = new double[16];

	static {
		// Compute 8-neighbour LUTs
		Set<Integer> patternSet = new HashSet<>();
		for (int k = 0; k < 256; k++) {
			byte lbp = (byte)k;
			int pattern = k;
			for (int i = 1; i < 8; i++) {
				pattern = Math.min(pattern, (((lbp & 0xFF) >>> i) | (lbp << 8-i)) & 0xFF);
			}
			patternSet.add(pattern);
		}
		int count = 0;
		int countUniform = 0;
		Arrays.fill(lbf8UniformMap, 9); // There are 10 categories, with the last one mopping up all the entries that don't fit elsewhere
		for (Integer pattern : patternSet) {
			lbf8map[pattern] = count++;
			int uniformCount = ((pattern) ^ ((pattern >>> 1) | (pattern << 8-1))) & 0xFF;
			if (Integer.bitCount(uniformCount) <= 2) {
				lbf8UniformMap[pattern] = countUniform;
				
				// Update LUT for all shifts of the base pattern
				for (int i = 1; i < 8; i++) {
					int pattern2 = ((pattern >>> i) | (pattern << 8-i)) & 0xFF;
					lbf8UniformMap[pattern2] = countUniform;
				}
				countUniform++;
			}
		}
		nLBF8 = count;
		nLBF8Uniform = 10;
		
		
		// Compute 16-neighbour LUTs
		patternSet.clear();
		for (int k = 0; k < 65536; k++) {
			short lbp = (short)k;
			int pattern = k;
			for (int i = 1; i < 16; i++) {
				pattern = Math.min(pattern, (((lbp & 0xFFFF) >>> i) | (lbp << 16-i)) & 0xFFFF);
			}
			patternSet.add(pattern);
		}
		countUniform = 0;
		Arrays.fill(lbf16UniformMap, 17); // There are 18 categories, with the last one mopping up all the entries that don't fit elsewhere
		for (Integer pattern : patternSet) {
			int uniformCount = ((pattern) ^ ((pattern >>> 1) | (pattern << 16-1))) & 0xFFFF;
			if (Integer.bitCount(uniformCount) <= 2) {
				lbf16UniformMap[pattern] = countUniform;
				
				// Update LUT for all shifts of the base pattern
				for (int i = 1; i < 16; i++) {
					int pattern2 = ((pattern >>> i) | (pattern << 16-i)) & 0xFFFF;
					lbf16UniformMap[pattern2] = countUniform;
				}

				countUniform++;
			}
		}
		nLBF16Uniform = countUniform + 1;
		
		
		// Compute interpolated offsets for 8 neighbors assuming radius = 1
		double scale = 2 * Math.PI / 8;
		for (int i = 0; i < 8; i++) {
			xo8[i] = Math.cos(scale * i);
			yo8[i] = -Math.sin(scale * i);
		}
		// Tidy up the values close to 0 and +/-1
		tidyDoubleUnitArray(xo8);
		tidyDoubleUnitArray(yo8);
		
		
		// Compute interpolated offsets for 16 neighbors assuming radius = 1
		scale = 2 * Math.PI / 16;
		for (int i = 0; i < 16; i++) {
			xo16[i] = Math.cos(scale * i);
			yo16[i] = -Math.sin(scale * i);
		}
		// Tidy up the values close to 0 and +/-1
		tidyDoubleUnitArray(xo16);
		tidyDoubleUnitArray(yo16);
		
	}
	
	/*
	 * This isn't very beautiful at all... but it rounds values close to 0 and +/-1 to be exactly those values
	 */
	private static void tidyDoubleUnitArray(final double[] arr) {
		for (int i = 0; i < arr.length; i++) {
			double d = arr[i];
			if (Math.abs(d) < 0.00001)
				arr[i] = 0;
			else {
				double signum = Math.signum(d);
				if (Math.abs(d - signum) < 0.00001)
					arr[i] = signum;
			}
		}
	}
	
	
	
	
//	public static void main(String[] args) {
//		System.out.println("Size: " + nLBF8);
//		System.out.println(Arrays.toString(lbf8map));
//
//		System.out.println("Size uniform: " + nLBF8Uniform);
//		System.out.println(Arrays.toString(lbf8UniformMap));
//		
//		System.out.println("Size uniform 16: " + nLBF16Uniform);
////		// Just to check there really are the correct number of entries...
////		Arrays.sort(lbf16UniformMap);
////		System.out.println(Arrays.toString(lbf16UniformMap));
//		
//		System.out.println("X offsets (8): " + Arrays.toString(xo8));
//		System.out.println("Y offsets (8): " + Arrays.toString(yo8));
//		
//		System.out.println("X offsets (16): " + Arrays.toString(xo16));
//		System.out.println("Y offsets (16): " + Arrays.toString(yo16));
//		
//		
//		// TODO: Move to test code location
//		SimpleImage img = SimpleImages.createFloatImage(new float[]{1f, 2f, 3f, 4f}, 2, 2);
//		double[] xx = {0, 0.25, 0.5, 0.75, 1};
//		for (double y = 0; y <= 1; y += 0.25) {
//			for (double x : xx)
////			for (double x : new double[]{0.5})
//				System.out.println(String.format("(%.2f, %.2f): %.4f", x, y, getInterpolatedPixel(img, x, y)));
//		}
//	}
	
	
	private static int computeLocalBinaryPattern(final SimpleImage img, final int x, final int y) {
		
		float val = img.getValue(x, y);
		if (Float.isNaN(val))
			return -1;
		byte lbp = 0;
		
		if (img.getValue(x-1, y-1) >= val)
			lbp |= (byte)128;
		if (img.getValue(x, y-1) >= val)
			lbp |= (byte)64;
		if (img.getValue(x+1, y-1) >= val)
			lbp |= (byte)32;
		if (img.getValue(x-1, y) >= val)
			lbp |= (byte)16;
		if (img.getValue(x+1, y) >= val)
			lbp |= (byte)8;
		if (img.getValue(x-1, y+1) >= val)
			lbp |= (byte)4;
		if (img.getValue(x, y+1) >= val)
			lbp |= (byte)2;
		if (img.getValue(x+1, y+1) >= val)
			lbp |= (byte)1;
		
		
		// Compress for rotational invariance (only need to use if nor normalized)
		int pattern = lbp & 0xFF;
//		for (int i = 1; i < 8; i++) {
//			pattern = Math.min(pattern, (((lbp & 0xFF) >>> i) | (lbp << 8-i)) & 0xFF);
//		}
		
		return lbf8UniformMap[pattern];
	}
	
	
	
	
	
	private static int computeLocalBinaryPattern16(final SimpleImage img, final int x, final int y, double radius) {
		
		float val = img.getValue(x, y);
		if (Float.isNaN(val))
			return -1;
		short lbp = 0;
		
		for (int i = 0; i < 16; i++) {
			if (getInterpolatedPixel(img, x+xo16[i]*radius, y+yo16[i]*radius) >= val)
				lbp |= (short)(1 << i);
		}
		
		int pattern = lbp & 0xFFFF;		
		
		return lbf16UniformMap[pattern];
	}
	
	
	
	
	private static float getInterpolatedPixel(final SimpleImage img, final double x, final double y) {
		// Code based (very very loosely) on http://www.bytefish.de/blog/local_binary_patterns/
		// Relative indices
        int fx = (int)x;
        int fy = (int)y;
        int cx = fx+1;
        int cy = fy+1;
        // Fractional part
        double ty = y - fy;
        double tx = x - fx;
        // Set interpolation weights
        double w1 = (1 - tx) * (1 - ty);
        double w2 =      tx  * (1 - ty);
        double w3 = (1 - tx) *      ty;
        double w4 =      tx  *      ty;
        // Compute the value
        // By checking weights first, this avoids requesting out-of-image pixels (assuming x & y were ok)
        float value = 0;
        if (w1 > 0)
        	value += w1*img.getValue(fx, fy);
        if (w2 > 0)
        	value += w2*img.getValue(cx, fy);
        if (w3 > 0)
        	value += w3*img.getValue(fx, cy);
        if (w4 > 0)
        	value += w4*img.getValue(cx, cy);
        return value;
	}
	
	
	
	
	/**
	 * Compute normalized LBP histogram for x &gt;= x1 and x &lt; x2 and y &gt;= y1 and y &lt; y2.
	 * 
	 * @param img
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	private static double[] computeLocalBinaryPatterns(final SimpleImage img, final int x1, final int y1, final int x2, final int y2) {
//		double[] hist = new double[nLBF8]; // TODO: Change to uniform representation
		double[] hist = new double[nLBF8Uniform];
		int n = 0;
		for (int y = y1; y < y2; y++) {
			for (int x = x1; x < x2; x++) {
				int v = computeLocalBinaryPattern(img, x, y);
				if (v >= 0) {
					hist[v]++;
					n++;
				}
			}			
		}
		for (int i = 0; i < hist.length; i++) {
			hist[i] /= n;
		}
		return hist;
	}

	
	
	private static double[] computeLocalBinaryPatterns16(final SimpleImage img, final int x1, final int y1, final int x2, final int y2, final double radius) {
		double[] hist = new double[nLBF16Uniform];
		int n = 0;
		for (int y = y1; y < y2; y++) {
			for (int x = x1; x < x2; x++) {
				int v = computeLocalBinaryPattern16(img, x, y, radius);
				if (v >= 0) {
					hist[v]++;
					n++;
				}
			}			
		}
		for (int i = 0; i < hist.length; i++) {
			hist[i] /= n;
		}
		return hist;
	}
	
	/**
	 * Compute local binary pattern descriptor for a SimpleImage.
	 * <p>
	 * Note: This method is experimental and requires further testing (or possible removal).
	 * 
	 * @param img
	 * @param radius
	 * @return
	 */
	public static double[] computeLocalBinaryPatterns16(final SimpleImage img, final double radius) {
		int r2 = (int)Math.ceil(radius);
		return computeLocalBinaryPatterns16(img, r2, r2, img.getWidth()-r2, img.getHeight()-r2, radius);
	}
	
	
	private static double[] computeLocalBinaryPatterns(final SimpleImage img) {
		return computeLocalBinaryPatterns(img, 1, 1, img.getWidth()-1, img.getHeight()-1);
	}
	
	
//	/**
//	 * Compute LBP histogram for x >= x1 and x < x2 and y >= y1 and y < y2.
//	 * 
//	 * @param img
//	 * @param x1
//	 * @param y1
//	 * @param x2
//	 * @param y2
//	 * @return
//	 */
//	public static int[] computeLocalBinaryPatterns(final SimpleImage img, final int x1, final int y1, final int x2, final int y2) {
////		int[] hist = new int[nLBF8]; // TODO: Change to uniform representation
//		int[] hist = new int[nLBF8Uniform];
//		for (int y = y1; y < y2; y++) {
//			for (int x = x1; x < x2; x++) {
//				int v = computeLocalBinaryPattern(img, x, y);
//				if (v >= 0)
//					hist[v]++;
//			}			
//		}
//		return hist;
//	}
//
//	
//	public static int[] computeLocalBinaryPatterns(final SimpleImage img) {
//		return computeLocalBinaryPatterns(img, 1, 1, img.getWidth()-1, img.getHeight()-1);
//	}
	

}
