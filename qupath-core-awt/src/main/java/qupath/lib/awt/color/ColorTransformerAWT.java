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

package qupath.lib.awt.color;

import java.awt.Color;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DEFAULT_CD_STAINS;
import qupath.lib.common.ColorTools;
import qupath.lib.color.ColorTransformer;

/**
 * Several straightforward methods of manipulating RGB channels that may be used to enhance (or suppress)
 * primarily DAB staining, or otherwise assist in exploring IHC data.
 * More details on each method (in particular 'Blue normalized', here 'Blue chromaticity') are provided in:
 * 
 *  Brey, E. M., Lalani, Z., Johnston, C., Wong, M., McIntire, L. V., Duke, P. J., &amp; Patrick, C. W. (2003).
 *    Automated Selection of DAB-labeled Tissue for Immunohistochemical Quantification.
 *    Journal of Histochemistry &amp; Cytochemistry, 51(5)
 *    doi:10.1177/002215540305100503
 *    
 * Color deconvolution methods use default stain vectors - qupath.lib.color contains more flexible options for this.
 * 
 * @author Pete Bankhead
 */
public class ColorTransformerAWT {

	// Color models
	final protected static IndexColorModel ICM_RED = ColorToolsAwt.createIndexColorModel(Color.RED);
	final protected static IndexColorModel ICM_GREEN = ColorToolsAwt.createIndexColorModel(Color.GREEN);
	final protected static IndexColorModel ICM_BLUE = ColorToolsAwt.createIndexColorModel(Color.BLUE);
	final protected static IndexColorModel ICM_HUE = ColorToolsAwt.createHueColorModel();
	final private static IndexColorModel ICM_HEMATOXYLIN;
	final private static IndexColorModel ICM_EOSIN;
	final private static IndexColorModel ICM_DAB;

	final private static Map<ColorTransformer.ColorTransformMethod, ColorModel> COLOR_MODEL_MAP;

	static {
		ColorDeconvolutionStains stains_H_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_DAB);
		ICM_HEMATOXYLIN = ColorToolsAwt.getIndexColorModel(stains_H_DAB.getStain(1));
		ICM_DAB = ColorToolsAwt.getIndexColorModel(stains_H_DAB.getStain(2));

		ColorDeconvolutionStains stains_H_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_E);
		ICM_EOSIN = ColorToolsAwt.getIndexColorModel(stains_H_E.getStain(2));

		// Create a map for color models
		COLOR_MODEL_MAP = new HashMap<>();
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Red, ICM_RED);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Green, ICM_GREEN);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Blue, ICM_BLUE);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Red_chromaticity, ICM_RED);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Green_chromaticity, ICM_GREEN);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Blue_chromaticity, ICM_BLUE);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Hematoxylin_H_DAB, ICM_HEMATOXYLIN);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Hematoxylin_H_E, ICM_HEMATOXYLIN);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.DAB_H_DAB, ICM_DAB);
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Eosin_H_E, ICM_EOSIN);
		
		COLOR_MODEL_MAP.put(ColorTransformer.ColorTransformMethod.Hue, ICM_HUE);
	}


	/**
	 * Create a packed RGB pixel value by applying a {@code ColorModel} to a (possibly scaled and offset) floating point value.
	 * 
	 * @param v		  the 'raw' pixel value
	 * @param offset  an offset to subtract from the value
	 * @param scale   a scaling factor to apply after the offset subtraction
	 * @param cm      a {@code ColorModel} used to determine the output packed RGB value
	 * @return a packed RGB value after applying the transformations to {@code v}
	 */
	public static int makeScaledRGBwithRangeCheck(float v, float offset, float scale, ColorModel cm) {
		return ColorTransformerAWT.makeRGB(ColorTools.do8BitRangeCheck((v - offset) * scale), cm);
	}


	/**
	 * This does not guarantee a ColorModel will be returned!
	 * If it is not, then a default grayscale LUT should be used.
	 * 
	 * @param method
	 * @return
	 */
	public static ColorModel getDefaultColorModel(ColorTransformer.ColorTransformMethod method) {
		return COLOR_MODEL_MAP.get(method);
	}


	/**
	 * Apply a specified color transform to a packed (A)RGB array and output another (A)RGB array.
	 * <p>
	 * The aim is to perform fast transformations for visualization purposes, and <em>not</em> to obtain the 'raw' transformed values.
	 * 
	 * @param buf
	 * @param bufOutput
	 * @param method
	 * @param offset
	 * @param scale
	 * @param useColorLUT
	 * 
	 * @see qupath.lib.common.ColorTools
	 */
	public static void transformRGB(int[] buf, int[] bufOutput, ColorTransformer.ColorTransformMethod method, float offset, float scale, boolean useColorLUT) {
		//		System.out.println("Scale and offset: " + scale + ", " + offset);
		//		int[] buf = img.getRGB(0, 0, img.getWidth(), img.getHeight(), buf, 0, img.getWidth());
		ColorModel cm = useColorLUT ? COLOR_MODEL_MAP.get(method) : null;
		switch (method) {
		case Original:
			if (offset == 0 && scale == 1) {
				if (buf != bufOutput)
					System.arraycopy(buf, 0, bufOutput, 0, buf.length);
				return;
			}
			for (int i = 0; i < buf.length; i++) {
				int rgb = buf[i];
				int r = ColorTools.do8BitRangeCheck((ColorTools.red(rgb) - offset) * scale);
				int g = ColorTools.do8BitRangeCheck((ColorTools.green(rgb) - offset) * scale);
				int b = ColorTools.do8BitRangeCheck((ColorTools.blue(rgb) - offset) * scale);
				//				if (r != g)
				//					System.out.println(r + ", " + g + ", " + b);
				bufOutput[i] = ((r<<16) + (g<<8) + b) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			return;
		case Red:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTools.red(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Green:
			if (useColorLUT)
				cm = ICM_GREEN;
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTools.green(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Blue:
			if (useColorLUT)
				cm = ICM_BLUE;
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTools.blue(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case RGB_mean:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.rgbMean(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Red_chromaticity:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.redChromaticity(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Green_chromaticity:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.greenChromaticity(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Blue_chromaticity:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.blueChromaticity(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Green_divided_by_blue:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.greenOverBlue(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Brown:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.brown(buf[i]), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Hematoxylin_H_E:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.deconvolve(buf[i], ColorTransformer.inv_H_E, ColorTransformer.od_lut, 1), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Eosin_H_E:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.deconvolve(buf[i], ColorTransformer.inv_H_E, ColorTransformer.od_lut, 2), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Hematoxylin_H_DAB:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.deconvolve(buf[i], ColorTransformer.inv_H_DAB, ColorTransformer.od_lut, 1), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case DAB_H_DAB:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.deconvolve(buf[i], ColorTransformer.inv_H_DAB, ColorTransformer.od_lut, 2), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case Optical_density_sum:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.opticalDensitySum(buf[i], ColorTransformer.od_lut), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
			}
			break;
		case OD_Normalized:
			for (int i = 0; i < buf.length; i++) {
				bufOutput[i] = ColorTransformer.getODNormalizedColor(buf[i], 0.1, offset, scale);
			}
			break;			
		case White:
			Arrays.fill(bufOutput, ColorTools.MASK_RED | ColorTools.MASK_GREEN | ColorTools.MASK_BLUE);
			break;
		case Black:
			Arrays.fill(bufOutput, 0);
			break;
		default:
		}
	}

	private static int makeRGB(int v, ColorModel cm) {
		// TODO: Consider inclusion of alpha
		if (cm == null)
			return (255<<24) + (v<<16) + (v<<8) + v;
		else
			return cm.getRGB(v);
	}

	
	
	
	
	
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
	 * <p>
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
	
	
	/**
	 * Deconvolve RGB array with one set of stain vectors, and reconvolve with another - with optional scaling.
	 * <p>
	 * This supports in-place operation, i.e. buf and bufOutput can be the same array.
	 * Otherwise, if bufOutput == null, a new output array will be created.
	 * 
	 * @param buf
	 * @param stainsInput
	 * @param stainsOutput
	 * @param discardResidual
	 * @param bufOutput
	 * @param scale
	 * @param offset
	 * @return
	 */
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
