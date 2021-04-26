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

package qupath.lib.color;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;


/**
 * Static methods to assist with color deconvolution.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorDeconvolutionHelper {
	
	final private static Logger logger = LoggerFactory.getLogger(ColorDeconvolutionHelper.class);

	/**
	 * Convert a single pixel value to an optical density as {@code max(0, -log10(val/max)}. where {@code val} is clipped to be &gt;= 1.
	 * @param val
	 * @param max
	 * @return
	 */
	public static final double makeOD(double val, double max) {
		return Math.max(0, -Math.log10(Math.max(val, 1)/max));
	}
	
	/**
	 * Convert an int pixel to an optical density value using a pre-computed lookup table. 
	 * This is likely to be much faster than calling {@link #makeOD(double, double)}.
	 * @param val
	 * @param OD_LUT
	 * @return
	 * 
	 * @see #makeOD(double, double)
	 */
	public static final double makeODByLUT(int val, double[] OD_LUT) {
		if (val >= 0 && val < OD_LUT.length)
			return OD_LUT[val];
		return Double.NaN;
	}
	
	/**
	 * Convert a float pixel to an optical density value using a pre-computed lookup table. 
	 * This is likely to be much faster than calling {@link #makeOD(double, double)}, but involves 
	 * rounding the float first.
	 * @param val
	 * @param OD_LUT
	 * @return
	 * 
	 * @see #makeOD(double, double)
	 */
	public static final double makeODByLUT(float val, double[] OD_LUT) {
        return ColorDeconvolutionHelper.makeODByLUT((int)Math.round(val), OD_LUT);
    }	
	
	/**
	 * Create an optical density lookup table with 256 entries, normalizing to the specified background value.
	 * @param maxValue
	 * @return
	 * 
	 * @see #makeOD(double, double)
	 * @see #makeODByLUT(int, double[])
	 */
	public static double[] makeODLUT(double maxValue) {
		return ColorDeconvolutionHelper.makeODLUT(maxValue, 256);
	}

	/**
	 * Create an optical density lookup table, normalizing to the specified background value.
	 * @param maxValue background (white value)
	 * @param nValues number of values to include in the lookup table
	 * @return
	 */
	public static double[] makeODLUT(double maxValue, int nValues) {
		double[] OD_LUT = new double[nValues];
		for (int i = 0; i < nValues; i++) //J
			OD_LUT[i] = makeOD(i, maxValue);
		return OD_LUT;
	}


	/**
	 * For originally-8-bit images, optical densities can usually be computed faster by preallocating a LUT with the 0-255 required values.
	 * Otherwise, log values need to be calculated for every pixel (which can be relatively slow).
	 * 
	 * @param px
	 * @param maxValue
	 * @param use8BitLUT
	 */
	public static void convertPixelsToOpticalDensities(float[] px, double maxValue, boolean use8BitLUT) {
		if (use8BitLUT) {
			double[] od_lut = makeODLUT(maxValue, 256);
			for (int i = 0; i < px.length; i++)
				px[i] = (float)makeODByLUT(px[i], od_lut); 
		} else {
			for (int i = 0; i < px.length; i++)
				px[i] = (float)makeOD(px[i], maxValue);	
		}
	}


	/**
	 * Convert red channel of packed rgb pixel to optical density values, using a specified maximum value.
	 * 
	 * @param rgb
	 * @param maxValue
	 * @param px optional array used for output
	 * @return
	 */
	public static float[] getRedOpticalDensities(int[] rgb, double maxValue, float[] px) {
		if (px == null)
			px = new float[rgb.length];
		double[] od_lut = makeODLUT(maxValue, 256);
		for (int i = 0; i < px.length; i++)
			px[i] = (float)makeODByLUT(ColorTools.red(rgb[i]), od_lut);
		return px;
	}

	/**
	 * Convert red channel of packed rgb pixel to optical density values, using a specified maximum value.
	 * 
	 * @param rgb
	 * @param maxValue
	 * @param px optional array used for output
	 * @return
	 */
	public static float[] getGreenOpticalDensities(int[] rgb, double maxValue, float[] px) {
		if (px == null)
			px = new float[rgb.length];
		double[] od_lut = makeODLUT(maxValue, 256);
		for (int i = 0; i < px.length; i++)
			px[i] = (float)makeODByLUT(ColorTools.green(rgb[i]), od_lut);
		return px;
	}

	/**
	 * Convert red channel of packed rgb pixel to optical density values, using a specified maximum value.
	 * 
	 * @param rgb
	 * @param maxValue
	 * @param px optional array used for output
	 * @return
	 */
	public static float[] getBlueOpticalDensities(int[] rgb, double maxValue, float[] px) {
		if (px == null)
			px = new float[rgb.length];
		double[] od_lut = makeODLUT(maxValue, 256);
		for (int i = 0; i < px.length; i++)
			px[i] = (float)makeODByLUT(ColorTools.blue(rgb[i]), od_lut);
		return px;
	}


	/**
	 * Determine median RGB optical densities for an array of pixels (packed RGB), and combine these into a StainVector with the specified name.
	 * 
	 * @param name
	 * @param rgb
	 * @param redMax
	 * @param greenMax
	 * @param blueMax
	 * @return
	 */
	public static StainVector generateMedianStainVectorFromPixels(String name, int[] rgb, double redMax, double greenMax, double blueMax) {

		// TODO: Use getMedianRGB!  Should be no need to compute all optical densities...?

		// Extract the optical densities
		int n = rgb.length;
		float[] red = ColorTransformer.getSimpleTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.Red, null);
		float[] green = ColorTransformer.getSimpleTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.Green, null);
		float[] blue = ColorTransformer.getSimpleTransformedPixels(rgb, ColorTransformer.ColorTransformMethod.Blue, null);
		convertPixelsToOpticalDensities(red, redMax, n > 500);
		convertPixelsToOpticalDensities(green, greenMax, n > 500); //J
		convertPixelsToOpticalDensities(blue, blueMax, n > 500);

		// Normalize to unit length
//J		int counter = 0;
		for (int i = 0; i < n; i++) {	
			double r = red[i];
			double g = green[i];
			double b = blue[i];

			double denominator = Math.sqrt(r*r + g*g + b*b);

			red[i] = (float)(r / denominator); 
			green[i] = (float)(g / denominator);
			blue[i] = (float)(b / denominator);
		}

		// Sort to compute medians
		Arrays.sort(red);
		Arrays.sort(green);
		Arrays.sort(blue);
		int medianInd = n / 2;

		// Compute median vectors
		double r = red[medianInd];
		double g = green[medianInd];
		double b = blue[medianInd];

		return StainVector.createStainVector(name, r, g, b); 
	}


	/**
	 * Determine median of RGB values.
	 * The median of each channel is computed separately.
	 * 
	 * @param rgb array of packed RGB values
	 * @return
	 */
	public static int getMedianRGB(int[] rgb) {

		int n = rgb.length;
		// Extract medians for each channel
		int[] temp = new int[n];
		for (int i = 0; i < rgb.length; i++)
			temp[i] = ColorTools.red(rgb[i]);
		int rMedian = getMedian(temp);

		for (int i = 0; i < rgb.length; i++)
			temp[i] = ColorTools.green(rgb[i]);
		int gMedian = getMedian(temp);

		for (int i = 0; i < rgb.length; i++)
			temp[i] = ColorTools.blue(rgb[i]);
		int bMedian = getMedian(temp);

		return ColorTools.packRGB(rMedian, gMedian, bMedian);
	}


	private static int getMedian(int[] array) {
		Arrays.sort(array);
		return array[array.length/2];
	}


	/**
	 * Attempt to automatically refine color deconvolution stains based upon pixel values.
	 * <p>
	 * Warning!  This is really only for testing - it has not be very extensively validated.
	 * <p>
	 * The method used is based very loosely on that of Macenko et al. (2009), but avoids eigenvalue computations in favor of
	 * determining an initial stain inputs.
	 * 
	 * TODO: Improve automatic stain vector refinement.
	 * 
	 * 
	 * @param rgb
	 * @param stains
	 * @param minStain minimum optical density for each RGB channel (default 0.15)
	 * @param percentageClipped 
	 * @return
	 */
	public static ColorDeconvolutionStains refineColorDeconvolutionStains(final int[] rgb, final ColorDeconvolutionStains stains, final double minStain, final double percentageClipped) {

		logger.warn("WARNING!  Stain vector refinement is only for testing - treat the results with caution!");
		
		int n = rgb.length;
		double[] whiteValues = estimateWhiteValues(rgb);

		// Get optical densities
		float[] redOD = ColorDeconvolutionHelper.getRedOpticalDensities(rgb, whiteValues[0], null);
		float[] greenOD = ColorDeconvolutionHelper.getGreenOpticalDensities(rgb, whiteValues[1], null);
		float[] blueOD = ColorDeconvolutionHelper.getBlueOpticalDensities(rgb, whiteValues[2], null);

		// Create a basic stain mask
		boolean[] mask = createStainMask(redOD, greenOD, blueOD, minStain, stains.isH_DAB() || stains.isH_E(), true, null);

		// Count masked pixels
		int nnz = 0;
		for (boolean m : mask) {
			if (m)
				nnz++;
		}


		// Project normalized optical densities onto reference vectors to assess similarity
		double[] stain1 = stains.getStain(1).getArray();
		double[] stain2 = stains.getStain(2).getArray();
		float[] stain1Proj = new float[nnz];
		float[] stain2Proj = new float[nnz];
		int[] indices = new int[nnz];
		int ind = 0;
		for (int i = 0; i < n; i++) {
			if (!mask[i])
				continue;

			double r = redOD[i];
			double g = greenOD[i];
			double b = blueOD[i];
			double norm = Math.sqrt(r*r + g*g + b*b);

			stain1Proj[ind] = (float)((r * stain1[0] + g * stain1[1] + b * stain1[2]) / norm);
			stain2Proj[ind] = (float)((r * stain2[0] + g * stain2[1] + b * stain2[2]) / norm);
			indices[ind] = i;
			ind++;
		}

		// Sort projected values to ascertain thresholds (TODO: make this more efficient)
		float[] temp = Arrays.copyOf(stain1Proj, nnz);
		Arrays.sort(temp);
		float stain1Threshold = temp[(int)(nnz * .98)];
		System.arraycopy(stain2Proj, 0, temp, 0, nnz);
		Arrays.sort(temp);
		float stain2Threshold = temp[(int)(nnz * .98)];

		// Loop once again and determine above-threshold means for each channel
		double r1Sum = 0;
		double g1Sum = 0;
		double b1Sum = 0;
		int n1 = 0;
		double r2Sum = 0;
		double g2Sum = 0;
		double b2Sum = 0;
		int n2 = 0;
		for (int i = 0; i < indices.length; i++) {
			ind = indices[i];
			if (stain1Proj[i] >= stain1Threshold) {
				r1Sum += redOD[ind];
				g1Sum += greenOD[ind];
				b1Sum += blueOD[ind];
				n1++;
			}
			if (stain2Proj[i] >= stain2Threshold) {
				r2Sum += redOD[ind];
				g2Sum += greenOD[ind];
				b2Sum += blueOD[ind];
				n2++;
			}
		}

		// Create refined stains using means - these determine the main plane upon which pixels containing these stains are expected to fall
		StainVector stainBase1 = StainVector.createStainVector("Basis 1", r1Sum/n1, g1Sum/n1, b1Sum/n1);
		StainVector stainBase2 = StainVector.createStainVector("Basis 2", r2Sum/n2, g2Sum/n2, b2Sum/n2);

		// Create orthonormal vectors
		stainBase1 = StainVector.createStainVector("Basis 1", (stainBase1.getRed() + stainBase2.getRed())/2, (stainBase1.getGreen() + stainBase2.getGreen())/2, (stainBase1.getBlue() + stainBase2.getBlue())/2);
		StainVector stainNorm = StainVector.makeResidualStainVector(stainBase1, stainBase2);
		stainBase2 = StainVector.makeOrthogonalStainVector("Basis 2", stainBase1, stainNorm, false);



		double[] base1 = stainBase1.getArray();
		double[] base2 = stainBase2.getArray();
		double[] angles = new double[nnz];
//		Arrays.fill(angles, Double.POSITIVE_INFINITY);
		int nAngles = 0;
//		for (int i = 0; i < indices.length; i++) {
//			ind = indices[i];
//		ind = 0;
		for (int i = 0; i < n; i++) {
			if (!mask[i])
				continue;

			// TODO: Consider omitting angles too far from the required basis
			double r = redOD[i];
			double g = greenOD[i];
			double b = blueOD[i];

			double proj1 = r*base1[0] + g*base1[1] + b*base1[2];
			double proj2 = r*base2[0] + g*base2[1] + b*base2[2];
			angles[nAngles] = Math.atan2(proj2, proj1);

			nAngles++;
			if (nAngles == nnz)
				break;
		}
		Arrays.sort(angles);
		double alpha = Math.min(Math.max(0.1, percentageClipped), 25) / 100.;
		double minAngle = angles[(int)(nAngles * alpha)];
		double maxAngle = angles[nAngles - (int)(nAngles * alpha) - 1];

		// Compute new stain vectors
		double cos = Math.cos(minAngle);
		double sin = Math.sin(minAngle);
		StainVector stain2Refined = StainVector.createStainVector(stains.getStain(2).getName(), 
				base1[0]*cos + base2[0]*sin, base1[1]*cos + base2[1]*sin, base1[2]*cos + base2[2]*sin);
		cos = Math.cos(maxAngle);
		sin = Math.sin(maxAngle);
		StainVector stain1Refined = StainVector.createStainVector(stains.getStain(1).getName(), 
				base1[0]*cos + base2[0]*sin, base1[1]*cos + base2[1]*sin, base1[2]*cos + base2[2]*sin);




		return new ColorDeconvolutionStains(stains.getName(), stain1Refined, stain2Refined, whiteValues[0], whiteValues[1], whiteValues[2]);
	}




	private static boolean[] createStainMask(float[] redOD, float[] greenOD, float[] blueOD, double stainThreshold, boolean excludeGray, boolean excludeUncommonColors, boolean[] mask) {
		if (mask == null) {
			mask = new boolean[redOD.length];
			Arrays.fill(mask, true);
		}

		double gray = 1/Math.sqrt(3);
		double grayThreshold = Math.cos(0.2);
		for (int i = 0; i < mask.length; i++) {

			double r = redOD[i];
			double g = greenOD[i];
			double b = blueOD[i];

			//			if (r + g + b < stainThreshold)
			//				mask[i] = false;

			// All colors must exceed threshold
			if (r < stainThreshold || g < stainThreshold || b < stainThreshold)
				mask[i] = false;
			else if (excludeGray) {
				double norm = Math.sqrt(r*r + g*g + b*b);
				if (r*gray + g*gray + b*gray >= grayThreshold * norm)
					mask[i] = false;
			} else if (excludeUncommonColors &&
					((g < r && r <= b) || (g <= b && b < r))) {
				// Remove colors that can easily be determined as far from the expected pink/purple/brown of common stains in pathology
				// See http://en.wikipedia.org/wiki/Hue (inverted for ODs)
				mask[i] = false;
			}

		}

		return mask;
	}



	/**
	 * Estimate white (background) values for a brightfield image.
	 * <p>
	 * The algorithm computes histograms for each RGB channel, and takes the mode
	 * of the histogram in the region of the histogram &gt; the mean value for that channel.
	 * 
	 * @param rgb an array of packed RGB values
	 * @return an array containing estimated [red, green and blue] background values
	 */
	public static double[] estimateWhiteValues(int[] rgb) {
		// Create RGB histograms
		int[] countsRed = new int[256];
		int[] countsGreen = new int[256];
		int[] countsBlue = new int[256];
		double meanRed = 0;
		double meanGreen = 0;
		double meanBlue = 0;
		double scale = 1.0/rgb.length;

		for (int val : rgb) {

			int red = ColorTools.red(val);
			int green = ColorTools.green(val);
			int blue = ColorTools.blue(val);

			// Update counts
			countsRed[red]++;
			countsGreen[green]++;
			countsBlue[blue]++;

			// Update means
			meanRed += red * scale;
			meanGreen += green * scale;
			meanBlue += blue * scale;

		}

		// Determine modes above the mean
		int modeRedCount = Integer.MIN_VALUE;
		int modeGreenCount = Integer.MIN_VALUE;
		int modeBlueCount = Integer.MIN_VALUE;
		int modeRed = 0;
		int modeGreen = 0;
		int modeBlue = 0;
		for (int i = 0; i < 256; i++) {
			if (i > meanRed && countsRed[i] >= modeRedCount) {
				modeRedCount = countsRed[i];
				modeRed = i;
			}

			if (i > meanGreen && countsGreen[i] >= modeGreenCount) {
				modeGreenCount = countsGreen[i];
				modeGreen = i;
			}

			if (i > meanBlue && countsBlue[i] >= modeBlueCount) {
				modeBlueCount = countsBlue[i];
				modeBlue = i;
			}

		}

		return new double[]{modeRed, modeGreen, modeBlue};

	}
}
