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

package qupath.imagej.color;

import qupath.lib.color.ColorDeconvMatrix3x3;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.StainVector;
import ij.process.Blitter;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;

/**
 * Color deconvolution methods intended for use with ImageJ.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorDeconvolutionIJ {
	
	
	public static FloatProcessor convertToOpticalDensitySum(ColorProcessor cp, double maxRed, double maxGreen, double maxBlue) {
		FloatProcessor fp = cp.toFloat(0, null);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fp.getPixels(), maxRed, true);

		FloatProcessor fpTemp = cp.toFloat(1, null);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fpTemp.getPixels(), maxGreen, true);
		fp.copyBits(fpTemp, 0, 0, Blitter.ADD);
		
		fpTemp = cp.toFloat(2, fpTemp);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fpTemp.getPixels(), maxBlue, true);
		fp.copyBits(fpTemp, 0, 0, Blitter.ADD);
		return fp;
	}
	
	/**
	 * Zero-based channel (0 = Red, 1 = Green, 2 = Blue)
	 */
	private static FloatProcessor convertToOpticalDensities(ColorProcessor cp, double maxValue, int channel) {
		FloatProcessor fp = cp.toFloat(channel, null);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fp.getPixels(), maxValue, true);
		return fp;
	}
	
	private static FloatProcessor[] convertToOpticalDensities(ColorProcessor cp, double maxRed, double maxGreen, double maxBlue) {
		FloatProcessor fpRed = convertToOpticalDensities(cp, maxRed, 0);
		FloatProcessor fpGreen = convertToOpticalDensities(cp, maxGreen, 1);
		FloatProcessor fpBlue = convertToOpticalDensities(cp, maxBlue, 2);
		return new FloatProcessor[]{fpRed, fpGreen, fpBlue};
	}
	
	
		
	public static FloatProcessor[] colorDeconvolve(ColorProcessor cp, StainVector stain1, StainVector stain2, StainVector stain3) {
		return colorDeconvolve(cp, stain1, stain2, stain3, false);
	}

	private static FloatProcessor[] colorDeconvolve(ColorProcessor cp, StainVector stain1, StainVector stain2, StainVector stain3, boolean clipNegative) {
		return colorDeconvolve(cp, stain1, stain2, stain3, 255, 255, 255, clipNegative);
	}
	
	private static FloatProcessor[] colorDeconvolve(ColorProcessor cp, StainVector stain1, StainVector stain2, StainVector stain3, double maxRed, double maxGreen, double maxBlue, boolean clipNegative) {
//		IJ.log(String.format("Max values: %.2f, %.2f, %.2f", maxRed, maxGreen, maxBlue));
		FloatProcessor[] fpRGBODs = convertToOpticalDensities(cp, maxRed, maxGreen, maxBlue);
		boolean deconvolved = colorDeconvolveRGBOpticalDensities(fpRGBODs[0], fpRGBODs[1], fpRGBODs[2], stain1, stain2, stain3, clipNegative);
		if (deconvolved) {
			return fpRGBODs;
		}
		else
			return new FloatProcessor[0];
	}
	
	
	private static boolean colorDeconvolveRGBOpticalDensities(FloatProcessor fpRed, FloatProcessor fpGreen, FloatProcessor fpBlue, StainVector stain1, StainVector stain2, StainVector stain3, boolean clipNegative) {
		// Make stain3 orthogonal, if it wasn't supplied
		if (stain3 == null)
			stain3 = StainVector.makeResidualStainVector(stain1, stain2);
		
		// Generate and invert matrix
		// TODO: Absolutely no idea if this is correct... it would be very surprising if so...
		ColorDeconvMatrix3x3 mat3x3 = new ColorDeconvMatrix3x3(new double[][]{stain1.getArray(), stain2.getArray(), stain3.getArray()});
		double[][] matInv = mat3x3.inverse();
		double[] stain1Inv = matInv[0];
		double[] stain2Inv = matInv[1];
		double[] stain3Inv = matInv[2];
		
//		IJ.log(mat3x3.toString());
//		IJ.log("INVERSE:");
//		IJ.log(new ColorDeconvMatrix3x3(matInv).toString());
//		IJ.log("Stain 1:");
//		IJ.log(stain1Inv[0] + ", " + stain1Inv[1] + ", " + stain1Inv[2]);
//		IJ.log(stain1.toString());
//		IJ.log(stain2.toString());
//		IJ.log(stain3.toString());

		
		// Extract pixels
		float[] pxRed = (float[])fpRed.getPixels();
		float[] pxGreen = (float[])fpGreen.getPixels();
		float[] pxBlue = (float[])fpBlue.getPixels();
				
		for (int i = 0; i < pxRed.length; i++) {
			double r = pxRed[i];
			double g = pxGreen[i];
			double b = pxBlue[i];		
			
			double o1 = r * stain1Inv[0] + g * stain2Inv[0] + b * stain3Inv[0];
			double o2 = r * stain1Inv[1] + g * stain2Inv[1] + b * stain3Inv[1];
			double o3 = r * stain1Inv[2] + g * stain2Inv[2] + b * stain3Inv[2];
			
//			double sumOrig = r + g + b;
//			double sumDeconv = 	o1 + o2 + o3;
//			double sumOrig2 = r*r + g*g + b*b;
//			double sumDeconv2 = o1*o1 + o2*o2 + o3*o3;
//			IJ.log(String.format("Orig: %.3f, Deconv: %.3f, Orig^2: %.3f, Deconv^2: %.3f", sumOrig, sumDeconv, sumOrig2, sumDeconv2));
//			IJ.log(sumOrig + ", \t" + sumDeconv);
			
//			// The following should be equal....
//			IJ.log(String.format("Red: %.3f, Red by OD: %.3f", r, stain1.getArray()[0]*o1 + stain2.getArray()[0]*o2 + stain3.getArray()[0]*o3));

			
			// Clip zeros, if necessary
			if (clipNegative) {
				o1 = Math.max(0, o1);
				o2 = Math.max(0, o2);
				o3 = Math.max(0, o3);
			}
			
			pxRed[i] = (float)o1;
			pxGreen[i] = (float)o2;
			pxBlue[i] = (float)o3;
		}
		return true;
	}
	
	

}
