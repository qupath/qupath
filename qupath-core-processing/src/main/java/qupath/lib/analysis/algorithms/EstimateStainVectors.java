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

package qupath.lib.analysis.algorithms;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;

/**
 * Code for estimating stain vectors automatically from an image, or to launch an editor for visually/interactively modifying stain vectors.
 * <p>
 * Aspects of the automated method take inspiration from Macenko's 2009 paper
 * 'A METHOD FOR NORMALIZING HISTOLOGY SLIDES FOR QUANTITATIVE ANALYSIS'
 * although it also differs through its use of preprocessing and parameters, as well as its selection of an actual pixel value 
 * rather than projecting on the identified plane.
 * 
 * @author Pete Bankhead
 *
 */
public class EstimateStainVectors {
	
	final private static Logger logger = LoggerFactory.getLogger(EstimateStainVectors.class);
	
	/**
	 * Estimate two stains from a BufferedImage, with default parameter settings.
	 * @param img original RGB image
	 * @param stainsOriginal original stains, including the background (white) values for red, green and blue and stain names
	 * @param checkColors if true, avoid colors far from H&amp;E
	 * @return
	 */
	public static ColorDeconvolutionStains estimateStains(final BufferedImage img, final ColorDeconvolutionStains stainsOriginal, final boolean checkColors) {
		double maxStain = 1;
		double minStain = 0.05;
		double ignorePercentage = 1;
		return estimateStains(img, stainsOriginal, minStain, maxStain, ignorePercentage, checkColors);
	}
	
	/**
	 * Estimate two stains from a BufferedImage.
	 * @param img original RGB image
	 * @param stainsOriginal original stains, including the background (white) values for red, green and blue and stain names
	 * @param minStain minimum optical density to use
	 * @param maxStain maximum optical density to use
	 * @param ignorePercentage percentage of extrema pixels to ignore
	 * @param checkColors if true, avoid colors far from H&amp;E
	 * @return
	 */
	public static ColorDeconvolutionStains estimateStains(final BufferedImage img, final ColorDeconvolutionStains stainsOriginal, final double minStain, final double maxStain, final double ignorePercentage, final boolean checkColors) {
		int[] buf = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
		int[] rgb = buf;
		
		float[] red = ColorDeconvolutionHelper.getRedOpticalDensities(rgb, stainsOriginal.getMaxRed(), null);
		float[] green = ColorDeconvolutionHelper.getGreenOpticalDensities(rgb, stainsOriginal.getMaxGreen(), null);
		float[] blue = ColorDeconvolutionHelper.getBlueOpticalDensities(rgb, stainsOriginal.getMaxBlue(), null);
		
		return estimateStains(buf, red, green, blue, stainsOriginal, minStain, maxStain, ignorePercentage, checkColors);
	}
	
	/**
	 * 
	 * Check colors only currently applies to H&amp;E.
	 * 
	 * @param rgbPacked
	 * @param redOD
	 * @param greenOD
	 * @param blueOD
	 * @param stainsOriginal
	 * @param minStain
	 * @param maxStain
	 * @param ignorePercentage
	 * @param checkColors
	 * @return
	 */
	public static ColorDeconvolutionStains estimateStains(final int[] rgbPacked, final float[] redOD, final float[] greenOD, final float[] blueOD, final ColorDeconvolutionStains stainsOriginal, final double minStain, final double maxStain, final double ignorePercentage, final boolean checkColors) {
		
		double alpha = ignorePercentage / 100;
		
		int n = rgbPacked.length;
		if (redOD.length != n || greenOD.length != n || blueOD.length != n)
			throw new IllegalArgumentException("All pixel arrays must be the same length!");
		int[] rgb = Arrays.copyOf(rgbPacked, n);
		float[] red = Arrays.copyOf(redOD, n);
		float[] green = Arrays.copyOf(greenOD, n);
		float[] blue = Arrays.copyOf(blueOD, n);
		
		// Check if we do color sanity test
		boolean doColorTestForHE = checkColors && stainsOriginal.isH_E();
//		boolean doColorTestForHDAB = checkColors && stainsOriginal.isH_DAB();
		boolean doGrayTest = checkColors && (stainsOriginal.isH_E() || stainsOriginal.isH_DAB());
		double sqrt3 = 1/Math.sqrt(3);
		double grayThreshold = Math.cos(0.15);
		
		// Loop through and discard pixels that are too faintly or densely stained
		int keepCount = 0;
		double maxStainSq = maxStain*maxStain;
		for (int i = 0; i < rgb.length; i++) {
			float r = red[i];
			float g = green[i];
			float b = blue[i];
			double magSquared = r*r + g*g + b*b;
			if (magSquared > maxStainSq || r < minStain || g < minStain || b < minStain || magSquared <= 0)
				continue;
			// Check for consistency with H&E staining, if required (i.e. only keep red/pink/purple/blue pixels and the like)
			if (doColorTestForHE && (r > g || b > g)) {
				continue;
			}
//			// Check for consistency with H-DAB staining, if required (i.e. only keep red/pink/purple/blue pixels and the like)
//			if (doColorTestForHDAB && (r > g)) {
//				continue;
//			}
			// Exclude very 'gray' pixels
			if (doGrayTest && (r*sqrt3 + g*sqrt3 + b*sqrt3) / Math.sqrt(magSquared) >= grayThreshold) {
				continue;				
			}
			// Update the arrays
			red[keepCount] = r;
			green[keepCount] = g;
			blue[keepCount] = b;
			rgb[keepCount] = rgb[i];
			keepCount++;
		}
		if (keepCount <= 1)
			throw new IllegalArgumentException("Not enough pixels remain after applying stain thresholds!");
		
		// Trim the arrays
		if (keepCount < rgb.length) {
			red = Arrays.copyOf(red, keepCount);
			green = Arrays.copyOf(green, keepCount);
			blue = Arrays.copyOf(blue, keepCount);
			rgb = Arrays.copyOf(rgb, keepCount);
		}
		
		
		double[][] cov = new double[3][3];
		cov[0][0] = covariance(red, red);
		cov[1][1] = covariance(green, green);
		cov[2][2] = covariance(blue, blue);
		cov[0][1] = covariance(red, green);
		cov[0][2] = covariance(red, blue);
		cov[1][2] = covariance(green, blue);
		cov[2][1] = cov[1][2];
		cov[2][0] = cov[0][2];
		cov[1][0] = cov[0][1];
		
		RealMatrix mat = MatrixUtils.createRealMatrix(cov);
		logger.debug("Covariance matrix:\n {}", getMatrixAsString(mat.getData()));
		
		EigenDecomposition eigen = new EigenDecomposition(mat);
		
		double[] eigenValues = eigen.getRealEigenvalues();
		int[] eigenOrder = rank(eigenValues);
		double[] eigen1= eigen.getEigenvector(eigenOrder[2]).toArray();
		double[] eigen2 = eigen.getEigenvector(eigenOrder[1]).toArray();
		logger.debug("First eigenvector: " + getVectorAsString(eigen1));
		logger.debug("Second eigenvector: " + getVectorAsString(eigen2));
		
		double[] phi = new double[keepCount];
		for (int i = 0; i < keepCount; i++) {
			double r = red[i];
			double g = green[i];
			double b = blue[i];
			phi[i] = Math.atan2(
					r*eigen1[0] + g*eigen1[1] + b*eigen1[2],
					r*eigen2[0] + g*eigen2[1] + b*eigen2[2]);
		}
		
		/*
		 * Rather than projecting onto the plane (which might be a bit wrong),
		 * select the vectors directly from the data.
		 * This is effectively like a region selection, but where the region has
		 * been chosen automatically.
		 */
		int[] inds = rank(phi);
		int ind1 = inds[Math.max(0, (int)(alpha * keepCount + .5))];
		int ind2 = inds[Math.min(inds.length-1, (int)((1 - alpha) * keepCount + .5))];
		
		// Create new stain vectors
		StainVector s1 = StainVector.createStainVector(stainsOriginal.getStain(1).getName(), red[ind1], green[ind1], blue[ind1]);
		StainVector s2 = StainVector.createStainVector(stainsOriginal.getStain(2).getName(), red[ind2], green[ind2], blue[ind2]);
		
		// If working with H&E, we can use the simple heuristic of comparing the red values
		if (stainsOriginal.isH_E()) {
			// Need to check within the stain vectors (*not* original indexed values) because normalisation is important (I think... there were errors before)
			if (s1.getRed() < s2.getRed()) {
				s1 = StainVector.createStainVector(stainsOriginal.getStain(1).getName(), red[ind2], green[ind2], blue[ind2]);
				s2 = StainVector.createStainVector(stainsOriginal.getStain(2).getName(), red[ind1], green[ind1], blue[ind1]);
			}
		} else {
			// Check we've got the closest match - if not, switch the order
			double angle11 = StainVector.computeAngle(s1, stainsOriginal.getStain(1));
			double angle12 = StainVector.computeAngle(s1, stainsOriginal.getStain(2));
			double angle21 = StainVector.computeAngle(s2, stainsOriginal.getStain(1));
			double angle22 = StainVector.computeAngle(s2, stainsOriginal.getStain(2));
			if (Math.min(angle12, angle21) < Math.min(angle11, angle22)) {
				s1 = StainVector.createStainVector(stainsOriginal.getStain(1).getName(), red[ind2], green[ind2], blue[ind2]);
				s2 = StainVector.createStainVector(stainsOriginal.getStain(2).getName(), red[ind1], green[ind1], blue[ind1]);
			}			
		}
		
		ColorDeconvolutionStains stains = new ColorDeconvolutionStains(stainsOriginal.getName(), s1, s2, stainsOriginal.getMaxRed(), stainsOriginal.getMaxGreen(), stainsOriginal.getMaxBlue());
		
		
		return stains;
		
	}
	
	
	/*
	 * Adapted from ImageJ's Tools class, which has the following note:
	 *  Returns a sorted list of indices of the specified double array.
	 *  Modified from: http://stackoverflow.com/questions/951848 by N.Vischer.
	 */
	static int[] rank(double[] values) {
		int n = values.length;
		final Integer[] indexes = new Integer[n];
		final Double[] data = new Double[n];
		for (int i=0; i<n; i++) {
			indexes[i] = Integer.valueOf(i);
			data[i] = Double.valueOf(values[i]);
		}
		Arrays.sort(indexes, new Comparator<Integer>() {
			@Override
			public int compare(final Integer o1, final Integer o2) {
				return data[o1].compareTo(data[o2]);
			}
		});
		int[] indexes2 = new int[n];
		for (int i=0; i<n; i++)
			indexes2[i] = indexes[i].intValue();
		return indexes2;
	}
	
	
	static String getMatrixAsString(final double[][] data) {
		StringBuilder sb = new StringBuilder();
		for (double[] row : data) {
			for (double d : row) {
				sb.append(d).append(",\t");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	static String getVectorAsString(final double[] data) {
		StringBuilder sb = new StringBuilder();
		for (double d : data) {
			sb.append(d).append(", ");
		}
		return sb.toString();
	}
	
	
	/**
	 * Calculate covariance of two vectors, which must have the same length.
	 * @param x
	 * @param y
	 * @return
	 */
	static double covariance(final float[] x, final float[] y) {
		
		int n = x.length;
		if (n != y.length)
			throw new IllegalArgumentException("Cannot compute covariance - array lengths are not the same");
		
		double xMean = 0;
		for (float v : x)
			xMean += ((double)v)/n;

		double yMean = 0;
		for (float v : y)
			yMean += ((double)v)/n;

		double result = 0;
        for (int i = 0; i < n; i++) {
            double xDev = x[i] - xMean;
            double yDev = y[i] - yMean;
            result += xDev * yDev / n;
//            result += (xDev * yDev - result) / (i + 1);
        }
        return result;
	}
	
	
	
	
	
	/**
	 * Subsample an array so that it contains no more than maxEntries.
	 * No guarantee is made that the resulting array will contain *exactly* maxEntries,
	 * but rather equal spacing between entries will be used.
	 * <p>
	 * If arr.length &lt;= maxEntries, the array is returned unchanged.
	 * 
	 * @param arr
	 * @param maxEntries
	 * @return
	 */
	public static int[] subsample(final int[] arr, final int maxEntries) {
		if (arr.length <= maxEntries)
			return arr;
		
		int spacing = (int)Math.ceil(arr.length / (double)maxEntries);
		int[] arr2 = new int[arr.length / spacing];
		for (int i = 0; i < arr2.length; i++) {
			arr2[i] = arr[i * spacing];
		}
		
		
		logger.debug("Array with {} entries subsampled to have {} entries (max requested {})", arr.length, arr2.length, maxEntries);
		
		return arr2;
	}
	
	
	/**
	 * Smooth out compression artefacts by running 3x3 filter twice (roughly approximates a small Gaussian filter).
	 * 
	 * @param img
	 * @return
	 */
	public static BufferedImage smoothImage(final BufferedImage img) {
		ConvolveOp op = new ConvolveOp(new Kernel(3, 3, new float[]{1f/9f, 1f/9f, 1f/9f, 1f/9f, 1f/9f, 1f/9f, 1f/9f, 1f/9f, 1f/9f}), ConvolveOp.EDGE_NO_OP, null);
		BufferedImage img2 = op.filter(img, null);
		return op.filter(img2, null);
	}
	
	
	
	
	/**
	 * Get the mode from an array of (packed) RGB pixel values.
	 * 
	 * @param rgb
	 * @return an array with 3 entries giving the Red, Green &amp; Blue values (in order) corresponding to the mode
	 * of each channel from the packed RGB pixel array.
	 */
	public static int[] getModeRGB(final int[] rgb) {
		int[] rh = new int[256];
		int[] gh = new int[256];
		int[] bh = new int[256];
		for (int v : rgb) {
			rh[ColorTools.red(v)]++;
			gh[ColorTools.green(v)]++;
			bh[ColorTools.blue(v)]++;
		}
		int rMax = 0, gMax = 0, bMax = 0;
		int rMaxCount = -1, gMaxCount = -1, bMaxCount = -1;
		for (int i = 255; i >= 0; i--) {
			if (rh[i] > rMaxCount) {
				rMaxCount = rh[i];
				rMax = i;
			}
			if (gh[i] > gMaxCount) {
				gMaxCount = gh[i];
				gMax = i;
			}
			if (bh[i] > bMaxCount) {
				bMaxCount = bh[i];
				bMax = i;
			}
		}
		return new int[]{rMax, gMax, bMax};
	}

}
