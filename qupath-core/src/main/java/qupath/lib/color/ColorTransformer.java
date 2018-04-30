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

import java.awt.Color;
import java.util.Arrays;

import qupath.lib.color.ColorDeconvolutionStains.DEFAULT_CD_STAINS;
import qupath.lib.common.ColorTools;

/**
 * Static methods for computing a range of color transforms for packed RGB values.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorTransformer {

	public enum ColorTransformMethod {
			Original,
			Red,
			Green,
			Blue,
			Red_OD,
			Green_OD,
			Blue_OD,
			RGB_mean,
			Hue,
			Saturation,
			Brightness,
			
			Stain_1,
			Stain_2,
			Stain_3,
			
			Stain_1_projection,
			Stain_2_projection,
			Stain_3_projection,
			Stain_1_rejection,
			Stain_2_rejection,
			Stain_3_rejection,
			
	//		Brightness,
	//		Red_optical_density,
	//		Green_optical_density,
	//		Blue_optical_density,
			Optical_density_sum,
			Hematoxylin_H_E,
			Eosin_H_E,
			Hematoxylin_H_DAB,
			DAB_H_DAB,
			Hematoxylin_H_E_8_bit,
			Eosin_H_E_8_bit,
			Hematoxylin_H_DAB_8_bit,
			DAB_H_DAB_8_bit,
			Red_chromaticity, 
			Green_chromaticity, 
			Blue_chromaticity,
			Green_divided_by_blue,
			OD_Normalized,
			Brown,		
			White,
			Black;
			
			@Override
		    public String toString() {
		        // Replace upper-case with a space in front, remove the first space.
		        return super.toString().replaceAll("_", " ");
		    }
		}
	
	
	// LUT for faster optical density computations (assuming white value of 255)
	public final static double[] od_lut;

	static {
		od_lut = ColorDeconvolutionHelper.makeODLUT(255, 256);
		ColorDeconvolutionStains stains_H_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_DAB);
		inv_H_DAB = stains_H_DAB.getMatrixInverse();
		ColorDeconvolutionStains stains_H_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_E);
		inv_H_E = stains_H_E.getMatrixInverse();
	}

	/**
	 * Create a 'normalized' color by converting RGB values to optical densities, putting the RGB ODs into
	 * a 3x1 vector and normalizing this to unit length, then rescaling the result to give an RGB representation.
	 * Because of the somewhat strange rescaling involved, the final RGB values produced should not be over-interpreted -
	 * this is really intended for visualization, such as when interactively looking for regions of single stains
	 * when selecting color deconvolution stain vectors.
	 * 
	 * @param rgb original 8-bit RGB values
	 * @param minOD the minimum OD - pixels with an OD less than this will be considered unstained, and shown as white
	 * @param offset brightness and contrast offset
	 * @param scale brightness and contrast scale value
	 * @return normalized color, as packed RGB value
	 */
	public static int getODNormalizedColor(int rgb, double minOD, float offset, float scale) {
		double r_od = od_lut[ColorTools.red(rgb)];
		double g_od = od_lut[ColorTools.green(rgb)];
		double b_od = od_lut[ColorTools.blue(rgb)];
		double norm = Math.sqrt(r_od*r_od + g_od*g_od + b_od*b_od);
		if (norm < minOD) {
			norm = minOD;
			//Jreturn ((255<<16) + (255<<8) + 255) & ~ColorTools.MASK_ALPHA | (rgb & ColorTools.MASK_ALPHA);
			return 0x00ffffff | (rgb & ColorTools.MASK_ALPHA); //J Slightly faster?
		}
		int r = 255-ColorTools.do8BitRangeCheck((255*r_od/norm - offset) * scale);
		int g = 255-ColorTools.do8BitRangeCheck((255*g_od/norm - offset) * scale);
		int b = 255-ColorTools.do8BitRangeCheck((255*b_od/norm - offset) * scale);
		return ((r<<16) + (g<<8) + b) & ~ColorTools.MASK_ALPHA | (rgb & ColorTools.MASK_ALPHA);
	}

	public static float getDefaultTransformedMax(ColorTransformMethod method) {
		float deconvMax = 2.5f;
		float chromaticityMax = 1.f;
		switch (method) {
		case Black:
			return 1;
		case Blue:
			return 255;
		case Blue_chromaticity:
			return chromaticityMax;
		case Brown:
			return 255;
		case Stain_1:
		case Stain_2:
		case Stain_3:
		case Hematoxylin_H_DAB:
		case Hematoxylin_H_E:
		case DAB_H_DAB:
		case Eosin_H_E:
			return deconvMax;
		case Green:
			return 255;
		case Green_chromaticity:
			return chromaticityMax;
		case Green_divided_by_blue:
			return 255;
		case Optical_density_sum:
			return deconvMax * 2;
		case Hue:
			return 1;
		case Brightness:
			return 1;
		case Saturation:
			return 1;
		case Original:
			return 255;
		case RGB_mean:
			return 255;
		case Red:
			return 255;
		case Red_chromaticity:
			return chromaticityMax;
		case White:
			return 1;
		default:
			return 255;
		}
	}
	
	
	/**
	 * This method is *only* compatible with color transforms that do not require a ColorDeconvolutionStains object -
	 * other transforms will throw an illegal argument exception.
	 * 
	 * @param buf
	 * @param method
	 * @param pixels
	 * @return
	 */
	public static float[] getSimpleTransformedPixels(final int[] buf, final ColorTransformMethod method, float[] pixels) {
		return getTransformedPixels(buf, method, pixels, null);
	}
	

	public static float[] getTransformedPixels(final int[] buf, ColorTransformMethod method, float[] pixels, final ColorDeconvolutionStains stains) {
		if (pixels == null || pixels.length != buf.length)
			pixels = new float[buf.length];
		
		double[] od_lut, od_lut_red, od_lut_green, od_lut_blue;
		double[][] inverse;
		
		switch (method) {
		case Red:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTools.red(buf[i]);
			}
			break;
		case Green:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTools.green(buf[i]);
			}			break;
		case Blue:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTools.blue(buf[i]);
			}
			break;
		case Red_OD:
			od_lut = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = (float)od_lut[ColorTools.red(buf[i])];
			}
			break;
		case Green_OD:
			od_lut = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = (float)od_lut[ColorTools.green(buf[i])];
			}
			break;
		case Blue_OD:
			od_lut = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = (float)od_lut[ColorTools.blue(buf[i])];
			}
			break;
		case RGB_mean:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.rgbMean(buf[i]);
			}
			break;
		case Red_chromaticity:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.redChromaticity(buf[i]);
			}
			break;
		case Green_chromaticity:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.greenChromaticity(buf[i]);
			}
			break;
		case Blue_chromaticity:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.blueChromaticity(buf[i]);
			}
			break;
		case Green_divided_by_blue:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.greenOverBlue(buf[i]);
			}
			break;
		case Brown:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.brown(buf[i]);
			}
			break;
		case Hematoxylin_H_E:
			if (stains == null || !stains.isH_E()) {
				throw new IllegalArgumentException("No valid H&E stains supplied!");
			}
		case Stain_1:
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 1);
			}
			break;
		case Eosin_H_E:
			if (stains == null || !stains.isH_E()) {
				throw new IllegalArgumentException("No valid H&E stains supplied!");
			}
		case Stain_2:
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 2);
			}
			break;
		case Stain_3:
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 3);
			}
			break;
		case Hematoxylin_H_DAB:
			if (stains == null || !stains.isH_DAB()) {
				throw new IllegalArgumentException("No valid H-DAB stains supplied!");
			}
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 1);
			}
			break;
		case DAB_H_DAB:
			if (stains == null || !stains.isH_DAB()) {
				throw new IllegalArgumentException("No valid H-DAB stains supplied!");
			}
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 2);
			}
			break;
			
		case Stain_1_projection:
			if (stains == null) {
				throw new IllegalArgumentException("No valid stains supplied!");
			}
			double rStain = stains.getStain(1).getRed();
			double gStain = stains.getStain(1).getGreen();
			double bStain = stains.getStain(1).getBlue();
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = getStainProjection(buf[i], rStain, gStain, bStain, od_lut_red, od_lut_green, od_lut_blue);
			}
			break;
		case Stain_2_projection:
			if (stains == null) {
				throw new IllegalArgumentException("No valid stains supplied!");
			}
			rStain = stains.getStain(2).getRed();
			gStain = stains.getStain(2).getGreen();
			bStain = stains.getStain(2).getBlue();
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = getStainProjection(buf[i], rStain, gStain, bStain, od_lut_red, od_lut_green, od_lut_blue);
			}
			break;
		case Stain_3_projection:
			if (stains == null) {
				throw new IllegalArgumentException("No valid stains supplied!");
			}
			rStain = stains.getStain(3).getRed();
			gStain = stains.getStain(3).getGreen();
			bStain = stains.getStain(3).getBlue();
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = getStainProjection(buf[i], rStain, gStain, bStain, od_lut_red, od_lut_green, od_lut_blue);
			}
			break;
			
		case Stain_1_rejection:
			if (stains == null) {
				throw new IllegalArgumentException("No valid stains supplied!");
			}
			rStain = stains.getStain(1).getRed();
			gStain = stains.getStain(1).getGreen();
			bStain = stains.getStain(1).getBlue();
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = getStainRejection(buf[i], rStain, gStain, bStain, od_lut_red, od_lut_green, od_lut_blue);
			}
			break;
		case Stain_2_rejection:
			if (stains == null) {
				throw new IllegalArgumentException("No valid stains supplied!");
			}
			rStain = stains.getStain(2).getRed();
			gStain = stains.getStain(2).getGreen();
			bStain = stains.getStain(2).getBlue();
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = getStainRejection(buf[i], rStain, gStain, bStain, od_lut_red, od_lut_green, od_lut_blue);
			}
			break;
		case Stain_3_rejection:
			if (stains == null) {
				throw new IllegalArgumentException("No valid stains supplied!");
			}
			rStain = stains.getStain(3).getRed();
			gStain = stains.getStain(3).getGreen();
			bStain = stains.getStain(3).getBlue();
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = getStainRejection(buf[i], rStain, gStain, bStain, od_lut_red, od_lut_green, od_lut_blue);
			}
			break;
			
			
		case Hematoxylin_H_E_8_bit:
			if (stains == null || !stains.isH_E()) {
				throw new IllegalArgumentException("No valid H&E stains supplied!");
			}
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 1);
			}
			break;
		case Eosin_H_E_8_bit:
			if (stains == null || !stains.isH_E()) {
				throw new IllegalArgumentException("No valid H&E stains supplied!");
			}
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 2);
			}
			break;
		case Hematoxylin_H_DAB_8_bit:
			if (stains == null || !stains.isH_DAB()) {
				throw new IllegalArgumentException("No valid H-DAB stains supplied!");
			}
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 1);
			}
			break;
		case DAB_H_DAB_8_bit:
			if (stains == null || !stains.isH_DAB()) {
				throw new IllegalArgumentException("No valid H-DAB stains supplied!");
			}
			od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
			od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
			od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
			inverse = stains.getMatrixInverse();
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], inverse, od_lut_red, od_lut_green, od_lut_blue, 2);
			}
			break;
			
		case Hue:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.hue(buf[i]);
			}
			break;
		case Saturation:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.saturation(buf[i]);
			}
			break;
		case Brightness:
			for (int i = 0; i < buf.length; i++) {
				pixels[i] = ColorTransformer.brightness(buf[i]);
			}
			break;

			
//		case Hematoxylin_H_E_8_bit:
//			for (int i = 0; i < buf.length; i++) {
//				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], ColorTransformer.inv_H_E, od_lut, 1);
//			}
//			break;
//		case Eosin_H_E_8_bit:
//			for (int i = 0; i < buf.length; i++) {
//				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], ColorTransformer.inv_H_E, od_lut, 2);
//			}
//			break;
//		case Hematoxylin_H_DAB_8_bit:
//			for (int i = 0; i < buf.length; i++) {
//				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], ColorTransformer.inv_H_DAB, od_lut, 1);
//			}
//			break;
//		case DAB_H_DAB_8_bit:
//			for (int i = 0; i < buf.length; i++) {
//				pixels[i] = ColorTransformer.deconvolve8bit(buf[i], ColorTransformer.inv_H_DAB, od_lut, 2);
//			}
//			break;
			
		case White:
			Arrays.fill(pixels, 255);
			break;
		case Black:
			Arrays.fill(pixels, 0);
			break;
		case Optical_density_sum:
			if (stains != null) {
				od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
				od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
				od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
				for (int i = 0; i < buf.length; i++) {
					pixels[i] = ColorTransformer.opticalDensitySum(buf[i], od_lut_red, od_lut_green, od_lut_blue);
				}			
			} else {
				od_lut = ColorDeconvolutionHelper.makeODLUT(255.0);
				for (int i = 0; i < buf.length; i++) {
					pixels[i] = ColorTransformer.opticalDensitySum(buf[i], od_lut);
				}			
			}
			break;
		case Original:
			return null;
		}
		return pixels;
	}
	
	
	/**
	 * Calculate magnitude of rejection of pixel OD onto stain OD.
	 * 
	 * @param rgb
	 * @param rStain
	 * @param gStain
	 * @param bStain
	 * @param od_lut_red
	 * @param od_lut_green
	 * @param od_lut_blue
	 * @return
	 */
	public static float getStainRejection(int rgb, double rStain, double gStain, double bStain, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut_red[(rgb & 0xff0000) >> 16];
		double g = od_lut_green[(rgb & 0xff00) >> 8];
		double b = od_lut_blue[rgb & 0xff];
		
		double projection = (r*rStain + g*gStain + b*bStain);
//		return (float)(projection / Math.sqrt(r*r + g*g + b*b));
		double rejection = Math.sqrt(r*r+g*g+b*b - projection*projection);
//		return projection > rejection ? 1 : 0;
		return (float)rejection;
	}
	
	/**
	 * Calculate magnitude of projection of pixel OD onto stain OD.
	 * 
	 * @param rgb
	 * @param rStain
	 * @param gStain
	 * @param bStain
	 * @param od_lut_red
	 * @param od_lut_green
	 * @param od_lut_blue
	 * @return
	 */
	public static float getStainProjection(int rgb, double rStain, double gStain, double bStain, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut_red[(rgb & 0xff0000) >> 16];
		double g = od_lut_green[(rgb & 0xff00) >> 8];
		double b = od_lut_blue[rgb & 0xff];
		
		double projection = (r*rStain + g*gStain + b*bStain);
		return (float)projection;
		
//		return (float)(r*rStain + g*gStain + b*bStain);
	}
	

	public static float opticalDensitySum(int rgb, double[] od_lut) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut[(rgb & 0xff0000) >> 16];
		double g = od_lut[(rgb & 0xff00) >> 8];
		double b = od_lut[rgb & 0xff];
		// Sum optical densities
		return (float)(r + g + b);
	}
	
	
	public static float opticalDensitySum(int rgb, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut_red[(rgb & 0xff0000) >> 16];
		double g = od_lut_green[(rgb & 0xff00) >> 8];
		double b = od_lut_blue[rgb & 0xff];
		// Sum optical densities
		return (float)(r + g + b);
	}
	
	
	public static float deconvolve(int rgb, double[][] invMat, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue, int stain) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut_red[(rgb & 0xff0000) >> 16];
		double g = od_lut_green[(rgb & 0xff00) >> 8];
		double b = od_lut_blue[rgb & 0xff];
		// Apply deconvolution & store the results
		return (float)(r * invMat[0][stain-1] + g * invMat[1][stain-1] + b * invMat[2][stain-1]);
}
	

	public static float deconvolve(int rgb, double[][] invMat, double[] od_lut, int stain) {
			// Extract RGB values & convert to optical densities using a lookup table
			double r = od_lut[(rgb & 0xff0000) >> 16];
			double g = od_lut[(rgb & 0xff00) >> 8];
			double b = od_lut[rgb & 0xff];
			// Apply deconvolution & store the results
			return (float)(r * invMat[0][stain-1] + g * invMat[1][stain-1] + b * invMat[2][stain-1]);
	}

	public static int deconvolve8bit(int rgb, double[][] invMat, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue, int stain) {
		// Apply deconvolution & store the results
		return ColorTools.do8BitRangeCheck(Math.exp(-deconvolve(rgb, invMat, od_lut_red, od_lut_green, od_lut_blue, stain)) * 255);
	}

	public static float rgbMean(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return (r + g + b)/3f;
	}

	public static float redChromaticity(int rgb) {
		float r = (rgb & ColorTools.MASK_RED) >> 16;
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return r / Math.max(1, r + g + b);
	}

	public static float greenChromaticity(int rgb) {
		float r = (rgb & ColorTools.MASK_RED) >> 16;
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return g / Math.max(1, r + g + b);
	}

	public static float blueChromaticity(int rgb) {
		float r = (rgb & ColorTools.MASK_RED) >> 16;
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return b / Math.max(1, r + g + b);
	}

	public static float greenOverBlue(int rgb) {
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return g / Math.max(b, 1);
	}

	public static float brown(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return (b - (r + g)*0.3f);
	}
	
	public static float hue(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return Color.RGBtoHSB(r, g, b, null)[0];
	}

	public static float saturation(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return Color.RGBtoHSB(r, g, b, null)[1];
	}
	
	public static float brightness(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return Color.RGBtoHSB(r, g, b, null)[2];
	}
	
	public static float getPixelValue(int rgb, ColorTransformMethod method) {
		return getPixelValue(rgb, method, null);
	}

	public static float getPixelValue(int rgb, ColorTransformMethod method, ColorDeconvolutionStains stains) {
		switch (method) {
		case Red:
			return ColorTools.red(rgb);
		case Green:
			return ColorTools.green(rgb);
		case Blue:
			return ColorTools.blue(rgb);
		case RGB_mean:
			return rgbMean(rgb);
		case Red_chromaticity:
			return redChromaticity(rgb);
		case Green_chromaticity:
			return greenChromaticity(rgb);
		case Blue_chromaticity:
			return blueChromaticity(rgb);
		case Green_divided_by_blue:
			return greenOverBlue(rgb);
		case Brown:
			return brown(rgb);
		case Hematoxylin_H_E:
			return deconvolve(rgb, ColorTransformer.inv_H_E, od_lut, 1);
		case Eosin_H_E:
			return deconvolve(rgb, ColorTransformer.inv_H_E, od_lut, 2);
		case Hematoxylin_H_DAB:
			return deconvolve(rgb, ColorTransformer.inv_H_DAB, od_lut, 1);
		case DAB_H_DAB:
			return deconvolve(rgb, ColorTransformer.inv_H_DAB, od_lut, 2);
		case White:
			return Float.NaN;
		case Black:
			return Float.NaN;
		case Optical_density_sum:
			return opticalDensitySum(rgb, od_lut);
		case Hue:
			return hue(rgb);
		case Saturation:
			return saturation(rgb);
		case Original:
			return Float.NaN;
			
			
		case Stain_1:
			if (stains != null) {
				double[] od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
				double[] od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
				double[] od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
				double[][] inverse = stains.getMatrixInverse();
				return ColorTransformer.deconvolve(rgb, inverse, od_lut_red, od_lut_green, od_lut_blue, 1);
			}
			return Float.NaN;
		case Stain_2:
			if (stains != null) {
				double[] od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
				double[] od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
				double[] od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
				double[][] inverse = stains.getMatrixInverse();
				return ColorTransformer.deconvolve(rgb, inverse, od_lut_red, od_lut_green, od_lut_blue, 2);
			}
			return Float.NaN;
		case Stain_3:
			if (stains != null) {
				double[] od_lut_red = ColorDeconvolutionHelper.makeODLUT(stains.getMaxRed());
				double[] od_lut_green = ColorDeconvolutionHelper.makeODLUT(stains.getMaxGreen());
				double[] od_lut_blue = ColorDeconvolutionHelper.makeODLUT(stains.getMaxBlue());
				double[][] inverse = stains.getMatrixInverse();
				return ColorTransformer.deconvolve(rgb, inverse, od_lut_red, od_lut_green, od_lut_blue, 3);
			}
			return Float.NaN;
		default:
			return Float.NaN;
		}
	}

	// Matrices for color deconvolution
	public final static double[][] inv_H_DAB;
	public final static double[][] inv_H_E;

}
