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
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import qupath.lib.color.ColorDeconvolutionStains.DefaultColorDeconvolutionStains;
import qupath.lib.common.ColorTools;

/**
 * Static methods for computing a range of color transforms for packed RGB values.
 * <p>
 * Several straightforward methods of manipulating RGB channels that may be used to enhance (or suppress)
 * primarily DAB staining, or otherwise assist in exploring IHC data.
 * More details on each method (in particular 'Blue normalized', here 'Blue chromaticity') are provided in:
 * <p>
 *  Brey, E. M., Lalani, Z., Johnston, C., Wong, M., McIntire, L. V., Duke, P. J., &amp; Patrick, C. W. (2003).
 *    Automated Selection of DAB-labeled Tissue for Immunohistochemical Quantification.
 *    Journal of Histochemistry &amp; Cytochemistry, 51(5)
 *    doi:10.1177/002215540305100503
 *    <p>
 * Color deconvolution methods use default stain vectors - qupath.lib.color contains more flexible options for this.
 * 
 * @author Pete Bankhead
 */
public class ColorTransformer {

	/**
	 * Enum consisting of color transforms that may be applied to RGB images.
	 */
	public enum ColorTransformMethod {
			/**
			 * Original image
			 */
			Original,
			/**
			 * Red channel only
			 */
			Red,
			/**
			 * Green channel only
			 */
			Green,
			/**
			 * Blue channel only
			 */
			Blue,
			/**
			 * Red channel optical densities
			 */
			Red_OD,
			/**
			 * Green channel optical densities
			 */
			Green_OD,
			/**
			 * Blue channel optical densities
			 */
			Blue_OD,
			/**
			 * Mean of red, green and blue channels
			 */
			RGB_mean,
			/**
			 * Hue value (from RGB-HSB transform)
			 */
			Hue,
			/**
			 * Saturation value (from RGB-HSB transform)
			 */
			Saturation,
			/**
			 * Brightness value (from RGB-HSB transform)
			 */
			Brightness,
			
			/**
			 * First stain after color deconvolution
			 */
			Stain_1,
			/**
			 * Second stain after color deconvolution
			 */
			Stain_2,
			/**
			 * Third stain after color deconvolution
			 */
			Stain_3,
			
			/**
			 * Sum of red, green and blue optical densities
			 */
			Optical_density_sum,
			/**
			 * Hematoxylin after color deconvolution with default H&amp;E stains
			 */
			Hematoxylin_H_E,
			/**
			 * Eosin after color deconvolution with default H&amp;E stains
			 */
			Eosin_H_E,
			/**
			 * Hematoxylin after color deconvolution with default H-DAB stains
			 */
			Hematoxylin_H_DAB,
			/**
			 * DAB after color deconvolution with default H-DAB stains
			 */
			DAB_H_DAB,
			
			/**
			 * Hematoxylin after color deconvolution with default H&amp;E stains and conversion to 8-bit
			 */
			Hematoxylin_H_E_8_bit,
			/**
			 * Eosin after color deconvolution with default H&amp;E stains and conversion to 8-bit
			 */
			Eosin_H_E_8_bit,
			/**
			 * Hematoxylin after color deconvolution with default H-DAB stains and conversion to 8-bit
			 */
			Hematoxylin_H_DAB_8_bit,
			/**
			 * DAB after color deconvolution with default H-DAB stains and conversion to 8-bit
			 */
			DAB_H_DAB_8_bit,
			/**
			 * Red chromaticity value, {@code red / max(1, red + green + blue)}
			 */
			Red_chromaticity, 
			/**
			 * Green chromaticity value, {@code green / max(1, red + green + blue)}
			 */
			Green_chromaticity, 
			/**
			 * Blue chromaticity value, {@code blue / max(1, red + green + blue)}
			 */
			Blue_chromaticity,
			/**
			 * Green value divided by blue value
			 */
			Green_divided_by_blue,
			/**
			 * RGB values normalized to OD vector (to reduce intensity information)
			 */
			OD_Normalized,
			/**
			 * Brown value, {@code (blue - (red + green)*0.3f)}
			 */
			Brown,		
			/**
			 * All pixels white
			 */
			White,
			/**
			 * All pixels black
			 */
			Black;
			
			@Override
		    public String toString() {
		        // Replace upper-case with a space in front, remove the first space.
		        return super.toString().replaceAll("_", " ");
		    }
		}
	
	
	// LUT for faster optical density computations (assuming white value of 255)
	private final static double[] od_lut;
	
	/**
	 * Inverse of default H-DAB color deconvolution matrix.
	 */
	private final static double[][] inv_H_DAB;
	private final static double[][] inv_H_E;

	static {
		od_lut = ColorDeconvolutionHelper.makeODLUT(255, 256);
		ColorDeconvolutionStains stains_H_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_DAB);
		inv_H_DAB = stains_H_DAB.getMatrixInverse();
		ColorDeconvolutionStains stains_H_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_E);
		inv_H_E = stains_H_E.getMatrixInverse();
	}

	/**
	 * Create a 'normalized' color for visualization.
	 * <p>
	 * This is achieved by converting RGB values to optical densities, putting the RGB ODs into
	 * a 3x1 vector and normalizing this to unit length, then rescaling the result to give an RGB representation.
	 * Because of the somewhat strange rescaling involved, the final RGB values produced should not be over-interpreted -
	 * this is really intended for visualization, such as when interactively looking for regions of single stains
	 * when selecting color deconvolution stain vectors.
	 * 
	 * @param rgb original 8-bit RGB values
	 * @param minOD the minimum OD; pixels with an OD less than this will be considered unstained, and shown as white
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

	/**
	 * Get default maximum value to use with a specific transform method.
	 * <p>
	 * Where this is well-defined, this should be the maximum possible value after the transform.
	 * @param method
	 * @return
	 */
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
	 * Apply a color transform to all pixels in a packed (A)RGB array.
	 * <p>
	 * This method is *only* compatible with color transforms that do not require a {@link ColorDeconvolutionStains} object -
	 * other transforms will throw an {@link IllegalArgumentException}.
	 * 
	 * @param buf the input pixel buffer to be transformed
	 * @param method the transfor method to apply
	 * @param pixels optional output array to store the results; if null or of the wrong length, a new array will be created
	 * @return either the input array {@code pixels}, or a new array if required
	 */
	public static float[] getSimpleTransformedPixels(final int[] buf, final ColorTransformMethod method, float[] pixels) {
		return getTransformedPixels(buf, method, pixels, null);
	}
	

	/**
	 * Apply a color transform to all pixels in a packed (A)RGB array.
	 * 
	 * @param buf the input pixel buffer to be transformed
	 * @param method the transfor method to apply
	 * @param pixels optional output array to store the results; if null or of the wrong length, a new array will be created
	 * @param stains a {@link ColorDeconvolutionStains} object, required for some transforms (and ignored otherwise).
	 * @return either the input array {@code pixels}, or a new array if required
	 */
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
	
	/**
	 * Extract optical density sum value from a packed RGB pixel.
	 * @param rgb
	 * @param od_lut lookup table to aid with fast optical density calculations
	 * @return
	 */
	public static float opticalDensitySum(int rgb, double[] od_lut) {
		// Extract RGB values & convert to optical densities using a lookup table
		if (od_lut == null)
			od_lut = ColorTransformer.od_lut;
		double r = od_lut[(rgb & 0xff0000) >> 16];
		double g = od_lut[(rgb & 0xff00) >> 8];
		double b = od_lut[rgb & 0xff];
		// Sum optical densities
		return (float)(r + g + b);
	}
	
	/**
	 * Extract optical density sum value from a packed RGB pixel.
	 * 
	 * @param rgb
	 * @param od_lut_red red lookup table to aid with fast optical density calculations
	 * @param od_lut_green green lookup table to aid with fast optical density calculations
	 * @param od_lut_blue blue lookup table to aid with fast optical density calculations
	 * @return
	 */
	public static float opticalDensitySum(int rgb, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut_red[(rgb & 0xff0000) >> 16];
		double g = od_lut_green[(rgb & 0xff00) >> 8];
		double b = od_lut_blue[rgb & 0xff];
		// Sum optical densities
		return (float)(r + g + b);
	}
	
	
	static float deconvolve(int rgb, double[][] invMat, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue, int stain) {
		// Extract RGB values & convert to optical densities using a lookup table
		double r = od_lut_red[(rgb & 0xff0000) >> 16];
		double g = od_lut_green[(rgb & 0xff00) >> 8];
		double b = od_lut_blue[rgb & 0xff];
		// Apply deconvolution & store the results
		return (float)(r * invMat[0][stain-1] + g * invMat[1][stain-1] + b * invMat[2][stain-1]);
}
	

	static float deconvolve(int rgb, double[][] invMat, double[] od_lut, int stain) {
			// Extract RGB values & convert to optical densities using a lookup table
			double r = od_lut[(rgb & 0xff0000) >> 16];
			double g = od_lut[(rgb & 0xff00) >> 8];
			double b = od_lut[rgb & 0xff];
			// Apply deconvolution & store the results
			return (float)(r * invMat[0][stain-1] + g * invMat[1][stain-1] + b * invMat[2][stain-1]);
	}

	private static int deconvolve8bit(int rgb, double[][] invMat, double[] od_lut_red, double[] od_lut_green, double[] od_lut_blue, int stain) {
		// Apply deconvolution & store the results
		return ColorTools.do8BitRangeCheck(Math.exp(-deconvolve(rgb, invMat, od_lut_red, od_lut_green, od_lut_blue, stain)) * 255);
	}

	/**
	 * Extract mean of RGB values from a packed RGB pixel.
	 * @param rgb
	 * @return
	 */
	public static float rgbMean(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return (r + g + b)/3f;
	}

	/**
	 * Extract red chromaticity value from a packed RGB pixel, {@code red / max(1, red + green + blue)}
	 * @param rgb
	 * @return
	 */
	public static float redChromaticity(int rgb) {
		float r = (rgb & ColorTools.MASK_RED) >> 16;
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return r / Math.max(1, r + g + b);
	}

	/**
	 * Extract green chromaticity value from a packed RGB pixel, {@code green / max(1, red + green + blue)}
	 * @param rgb
	 * @return
	 */
	public static float greenChromaticity(int rgb) {
		float r = (rgb & ColorTools.MASK_RED) >> 16;
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return g / Math.max(1, r + g + b);
	}

	/**
	 * Extract blue chromaticity value from a packed RGB pixel, {@code blue / max(1, red + green + blue)}
	 * @param rgb
	 * @return
	 */
	public static float blueChromaticity(int rgb) {
		float r = (rgb & ColorTools.MASK_RED) >> 16;
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return b / Math.max(1, r + g + b);
	}

	/**
	 * Extract green over blue value from a packed RGB pixel, {@code green / max(1, blue)}
	 * @param rgb
	 * @return
	 */
	public static float greenOverBlue(int rgb) {
		float g = (rgb & ColorTools.MASK_GREEN) >> 8;
		float b = rgb & ColorTools.MASK_BLUE;
		return g / Math.max(b, 1);
	}

	/**
	 * Extract brown value, {@code (blue - (red + green)*0.3f)}
	 * @param rgb
	 * @return
	 */
	public static float brown(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return (b - (r + g)*0.3f);
	}
	
	/**
	 * Extract hue value from RGB-to-HSB transform.
	 * @param rgb
	 * @return
	 */
	public static float hue(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return Color.RGBtoHSB(r, g, b, null)[0];
	}

	/**
	 * Extract saturation value from RGB-to-HSB transform.
	 * @param rgb
	 * @return
	 */
	public static float saturation(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return Color.RGBtoHSB(r, g, b, null)[1];
	}
	
	/**
	 * Extract brightness value from RGB-to-HSB transform.
	 * @param rgb
	 * @return
	 */
	public static float brightness(int rgb) {
		int r = (rgb & ColorTools.MASK_RED) >> 16;
		int g = (rgb & ColorTools.MASK_GREEN) >> 8;
		int b = rgb & ColorTools.MASK_BLUE;
		return Color.RGBtoHSB(r, g, b, null)[2];
	}
	
	/**
	 * Get the value of a single packed RGB pixel after applying a specified color transform method.
	 * @param rgb
	 * @param method
	 * @return
	 */
	public static float getPixelValue(int rgb, ColorTransformMethod method) {
		return getPixelValue(rgb, method, null);
	}

	/**
	 * Get the value of a single packed RGB pixel after applying a specified color transform method, with color deconvolution stains provided.
	 * @param rgb
	 * @param method
	 * @param stains
	 * @return
	 */
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
	
	
	
	
	
	
	
	
	
	// Color models
	final private static IndexColorModel ICM_RED = ColorToolsAwt.createIndexColorModel(Color.RED);
	final private static IndexColorModel ICM_GREEN = ColorToolsAwt.createIndexColorModel(Color.GREEN);
	final private static IndexColorModel ICM_BLUE = ColorToolsAwt.createIndexColorModel(Color.BLUE);
	final private static IndexColorModel ICM_HUE = ColorToolsAwt.createHueColorModel();
	final private static IndexColorModel ICM_HEMATOXYLIN;
	final private static IndexColorModel ICM_EOSIN;
	final private static IndexColorModel ICM_DAB;

	final private static Map<ColorTransformer.ColorTransformMethod, ColorModel> COLOR_MODEL_MAP;
	
	
	static {
		ColorDeconvolutionStains stains_H_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_DAB);
		ICM_HEMATOXYLIN = ColorToolsAwt.getIndexColorModel(stains_H_DAB.getStain(1));
		ICM_DAB = ColorToolsAwt.getIndexColorModel(stains_H_DAB.getStain(2));

		ColorDeconvolutionStains stains_H_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_E);
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
		return makeRGB(ColorTools.do8BitRangeCheck((v - offset) * scale), cm);
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
				bufOutput[i] = makeScaledRGBwithRangeCheck(ColorTransformer.opticalDensitySum(buf[i], null), offset, scale, cm) & ~ColorTools.MASK_ALPHA | (buf[i] & ColorTools.MASK_ALPHA);
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
	
	/**
	 * Calculate the color deconvolved value for a single pixel, stored as a packed (A)RGB int.
	 * @param rgb
	 * @param stains
	 * @param channel
	 * @return
	 */
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
	 * <p>
	 * Note: If {@code stainsInput} is null, the returned array will be filled with zeros.
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
		else if (stainsInput == null)
			Arrays.fill(bufOutput, 0);
		
		// Handle case where we have no stains
		if (stainsInput == null)
			return bufOutput;
		
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