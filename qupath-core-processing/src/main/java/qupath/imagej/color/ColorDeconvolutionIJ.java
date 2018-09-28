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

import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
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
	
	/**
	 * Calculate optical density values for the red, green and blue channels, then add these all together.
	 * 
	 * @param cp
	 * @param maxRed
	 * @param maxGreen
	 * @param maxBlue
	 * @return
	 */
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
	 * Apply color deconvolution, outputting 3 'stain' images in the same order as the stain vectors.
	 * 
	 * @param cp      input RGB color image
	 * @param stains  color deconvolution stain vectors
	 * @return array containing three {@code FloatProcessor}s, representing the deconvolved stains
	 */
	public static FloatProcessor[] colorDeconvolve(ColorProcessor cp, ColorDeconvolutionStains stains) {
		int width = cp.getWidth();
		int height = cp.getHeight();
		int[] rgb = (int[])cp.getPixels();
		FloatProcessor fpStain1 = new FloatProcessor(width, height, ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_1, null, stains));
		FloatProcessor fpStain2 = new FloatProcessor(width, height, ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_2, null, stains));
		FloatProcessor fpStain3 = new FloatProcessor(width, height, ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_3, null, stains));
		return new FloatProcessor[] {fpStain1, fpStain2, fpStain3};
	}
	
	
}
