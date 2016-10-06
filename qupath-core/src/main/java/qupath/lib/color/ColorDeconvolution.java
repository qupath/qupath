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

package qupath.lib.color;

import qupath.lib.common.ColorTools;

/**
 * Static methods for computing color deconvolved values from packed 8-bit RGB arrays.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorDeconvolution {
	
	private final static double POW_10_INC = 0.0015;
	private final static double[] POW_10_TABLE = new double[2500];
	private final static double LOG_10 = Math.log(10);
	
	static {
		
		/*
		 * We need a lot of 10^x calculations, which will ultimately be scaled & converted to 8-bit unsigned integers.
		 * Compute a LUT that can be used to vastly speed up the process to enable reconvolution to be possible
		 * while browsing an image.
		 */
		for (int i = 0; i < POW_10_TABLE.length; i++) {
			POW_10_TABLE[i] = Math.exp(-LOG_10 * i * POW_10_INC);
		}
		
	}
	
	public static float[] colorDeconvolveRGBArray(int[] buf, double[] stain1, double[] stain2, double[] stain3, int channel, float[] output) {
		if (output == null || output.length != buf.length)
			output = new float[buf.length];
		
		if (stain3 == null)
			stain3 = StainVector.cross3(stain1, stain2);
		double[][] stainMat = new double[][]{stain1, stain2, stain3};
		ColorDeconvMatrix3x3 mat3x3 = new ColorDeconvMatrix3x3(stainMat);
		double[][] matInv = mat3x3.inverse();
		double[] stain1Inv = matInv[0];
		double[] stain2Inv = matInv[1];
		double[] stain3Inv = matInv[2];
			
		// Apply color deconvolution
		double[] od_lut = ColorDeconvolutionHelper.makeODLUT(255, 256);
		for (int i = 0; i < buf.length; i++) {
			int c = buf[i];
			// Extract RGB values & convert to optical densities using a lookup table
 			double r = od_lut[(c & 0xff0000) >> 16];
			double g = od_lut[(c & 0xff00) >> 8];
			double b = od_lut[c & 0xff];
			// Apply deconvolution & store the results
			output[i] = (float)(r * stain1Inv[channel] + g * stain2Inv[channel] + b * stain3Inv[channel]);
		}
		return output;
	}
	
	public static float[] colorDeconvolveRGBArray(int[] buf, ColorDeconvolutionStains stains, int channel, float[] output) {
		if (output == null || output.length != buf.length)
			output = new float[buf.length];
		
		double[][] matInv = stains.getMatrixInverse();

		double scaleRed = matInv[0][channel];
		double scaleGreen = matInv[1][channel];
		double scaleBlue = matInv[2][channel];

		double maxRed = stains.getMaxRed(); //J
		double maxGreen = stains.getMaxGreen(); //J
		double maxBlue = stains.getMaxBlue(); //J
		
		// Apply color deconvolution
		//J Only need 3 if they are different from each other - may be a bit faster? (depends on whether we expect them to be the same more often)
		double[] od_lut_red = ColorDeconvolutionHelper.makeODLUT(maxRed); //J
		double[] od_lut_green = maxRed == maxGreen ? od_lut_red : ColorDeconvolutionHelper.makeODLUT(maxGreen); //J
		double[] od_lut_blue = maxBlue == maxRed ? od_lut_red : maxBlue == maxGreen ? od_lut_green : ColorDeconvolutionHelper.makeODLUT(maxBlue); //J
		//J double[] od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
		//J double[] od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
		//J double[] od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
		for (int i = 0; i < buf.length; i++) {
			int c = buf[i];
//			if (c == 1330268745) // This occurred frequently on the bottom row in at least one SVS file...
//				logger.info("here: " + ColorTransformer.red(c) + ", " + ColorTransformer.green(c) + ", " + ColorTransformer.blue(c));
			// Extract RGB values & convert to optical densities using a lookup table
			double r = od_lut_red[(c & 0xff0000) >> 16];
			double g = od_lut_green[(c & 0xff00) >> 8];
			double b = od_lut_blue[c & 0xff];
			// Apply deconvolution & store the results
			output[i] = (float)(r * scaleRed + g * scaleGreen + b * scaleBlue);
		}
		return output;
	}
	
	
	public static float colorDeconvolveRGBPixel(int rgb, ColorDeconvolutionStains stains, int channel) {
		double[][] matInv = stains.getMatrixInverse();
		double scaleRed = matInv[0][channel];
		double scaleGreen = matInv[1][channel];
		double scaleBlue = matInv[2][channel];
			
		// Apply color deconvolution
		double r = ColorDeconvolutionHelper.makeOD((rgb & 0xff0000) >> 16, stains.getMaxRed());
		double g = ColorDeconvolutionHelper.makeOD((rgb & 0xff00) >> 8, stains.getMaxGreen());
		double b = ColorDeconvolutionHelper.makeOD((rgb & 0xff), stains.getMaxBlue());
		
		return (float)(r * scaleRed + g * scaleGreen + b * scaleBlue);
	}
	
	
	
	/**
	 * Approximate 10^x, using a LUT for common values
	 * @param x
	 * @return
	 */
	private static double pow10Approx(double x) {
		int ind = (int)(x / POW_10_INC);
		if (ind < 0 || ind > POW_10_TABLE.length)
			return Math.exp(-LOG_10 * x);
		return POW_10_TABLE[ind];
	}
	
	
	/**
	 * Deconvolve RGB array with one set of stain vectors, and reconvolve with another.
	 * This supports in-place operation, i.e. buf and bufOutput can be the same array.
	 * Otherwise, if bufOutput == null, a new output array will be created.
	 * 
	 * @param buf
	 * @param stainsInput
	 * @param stainsOutput
	 * @param discardResidual
	 * @param bufOutput
	 * @return
	 */
	public static int[] colorDeconvolveReconvolveRGBArray(int[] buf, ColorDeconvolutionStains stainsInput, ColorDeconvolutionStains stainsOutput, boolean discardResidual, int[] bufOutput) {
		return colorDeconvolveReconvolveRGBArray(buf, stainsInput, stainsOutput, discardResidual, bufOutput, 1f, 0f);
	}
	
	
	public static int[] colorDeconvolveReconvolveRGBArray(int[] buf, ColorDeconvolutionStains stainsInput, ColorDeconvolutionStains stainsOutput, boolean discardResidual, int[] bufOutput, float scale, float offset) {
		if (bufOutput == null || bufOutput.length < buf.length)
			bufOutput = new int[buf.length];
		
		double[][] matInv = stainsInput.getMatrixInverse();
		
		// Extract input values
		double s00 = matInv[0][0];
		double s01 = matInv[0][1];
		double s02 = matInv[0][2];
		double s10 = matInv[1][0];
		double s11 = matInv[1][1];
		double s12 = matInv[1][2];
		double s20 = matInv[2][0];
		double s21 = matInv[2][1];
		double s22 = matInv[2][2];
		// If the third stain isn't actually a residual, we shouldn't discard it
		discardResidual = discardResidual && stainsInput.getStain(3).isResidual();
		
//		discardResidual = false;
		
		// Extract output values
		double d00 = stainsOutput.getStain(1).getRed();
		double d01 = stainsOutput.getStain(1).getGreen();
		double d02 = stainsOutput.getStain(1).getBlue();
		double d10 = stainsOutput.getStain(2).getRed();
		double d11 = stainsOutput.getStain(2).getGreen();
		double d12 = stainsOutput.getStain(2).getBlue();
		double d20 = stainsOutput.getStain(3).getRed();
		double d21 = stainsOutput.getStain(3).getGreen();
		double d22 = stainsOutput.getStain(3).getBlue();
		
		// Output maxima
		double maxRed = stainsOutput.getMaxRed();
		double maxGreen = stainsOutput.getMaxGreen();
		double maxBlue = stainsOutput.getMaxBlue();
		
		// Apply color deconvolution
		double[] od_lut_red = ColorDeconvolutionHelper.makeODLUT(stainsInput.getMaxRed());
		double[] od_lut_green = ColorDeconvolutionHelper.makeODLUT(stainsInput.getMaxGreen());
		double[] od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stainsInput.getMaxBlue());
		
		for (int i = 0; i < buf.length; i++) {
			int c = buf[i];
			// Extract RGB values & convert to optical densities using a lookup table
			double r = od_lut_red[(c & 0xff0000) >> 16];
			double g = od_lut_green[(c & 0xff00) >> 8];
			double b = od_lut_blue[c & 0xff];
			
			// Apply deconvolution
			double stain1 = r*s00 + g*s10 + b*s20;
			double stain2 = r*s01 + g*s11 + b*s21;
//			double stain3 = r*s02 + g*s12 + b*s22;
			double stain3 = discardResidual ? 0 : r*s02 + g*s12 + b*s22;
			
//			// Apply reconvolution & convert back to 8-bit (or thereabouts)
//			r = Math.pow(10, -stain1 * d00 - stain2 * d10 - stain3 * d20) * maxRed;
//			g = Math.pow(10, -stain1 * d01 - stain2 * d11 - stain3 * d21) * maxGreen;
//			b = Math.pow(10, -stain1 * d02 - stain2 * d12 - stain3 * d22) * maxBlue;
			// This implementation is considerably faster than Math.pow... but still not very fast
//			if (discardResidual) {
//				r = Math.exp(-log10 * (stain1 * d00 + stain2 * d10)) * maxRed;
//				g = Math.exp(-log10 * (stain1 * d01 + stain2 * d11)) * maxGreen;
//				b = Math.exp(-log10 * (stain1 * d02 + stain2 * d12)) * maxBlue;
//			} else {
//				r = Math.exp(-log10 * (stain1 * d00 + stain2 * d10 + stain3 * d20)) * maxRed;
//				g = Math.exp(-log10 * (stain1 * d01 + stain2 * d11 + stain3 * d21)) * maxGreen;
//				b = Math.exp(-log10 * (stain1 * d02 + stain2 * d12 + stain3 * d22)) * maxBlue;
//			}

			if (discardResidual) {
				r = pow10Approx(stain1 * d00 + stain2 * d10) * maxRed;
				g = pow10Approx(stain1 * d01 + stain2 * d11) * maxGreen;
				b = pow10Approx(stain1 * d02 + stain2 * d12) * maxBlue;
			} else {
				r = pow10Approx(stain1 * d00 + stain2 * d10 + stain3 * d20) * maxRed;
				g = pow10Approx(stain1 * d01 + stain2 * d11 + stain3 * d21) * maxGreen;
				b = pow10Approx(stain1 * d02 + stain2 * d12 + stain3 * d22) * maxBlue;
			}


//			// This is pretty odd, but about the same speed as the exp method
//			r = Arrays.binarySearch(od_lut_red2, (stain1 * d00 + stain2 * d10 + stain3 * d20));
//			r = r < 0 ? 256 + r : 255 - r;
//			g = Arrays.binarySearch(od_lut_green2, (stain1 * d01 + stain2 * d11 + stain3 * d21));
//			g = g < 0 ? 256 + g : 255 - g;
//			b = Arrays.binarySearch(od_lut_blue2, (stain1 * d02 + stain2 * d12 + stain3 * d22));
//			b = b < 0 ? 256 + b : 255 - b;
			
////			// This is pretty odd, but about the same speed as the exp method
//			r = getIndex(od_lut_red, (stain1 * d00 + stain2 * d10 + stain3 * d20));
//			g = getIndex(od_lut_green, (stain1 * d01 + stain2 * d11 + stain3 * d21));
//			b = getIndex(od_lut_blue, (stain1 * d02 + stain2 * d12 + stain3 * d22));
			
//			// Confirming, it really is the exp that makes it slow...
//			r = 255 - log10 * (stain1 * d00 + stain2 * d10 + stain3 * d20) * 50;
//			g = 255 - log10 * (stain1 * d01 + stain2 * d11 + stain3 * d21) * 50;
//			b = 255 - log10 * (stain1 * d02 + stain2 * d12 + stain3 * d22) * 50;
			

			// Store the result
			bufOutput[i] = (ColorTools.do8BitRangeCheck((r + offset) * scale) << 16) +
						(ColorTools.do8BitRangeCheck((g + offset) * scale) << 8) +
						ColorTools.do8BitRangeCheck((b + offset) * scale);
			
//			bufOutput[i] = ColorTransformer.makeRGBwithRangeCheck((float)(stain2)*100, null);
		}
		return bufOutput;
	}
	
	
	
}
