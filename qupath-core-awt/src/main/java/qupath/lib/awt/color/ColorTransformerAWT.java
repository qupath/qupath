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



	public static int makeRGBwithRangeCheck(float v, ColorModel cm) {
		return ColorTransformerAWT.makeRGB(ColorTools.do8BitRangeCheck(v), cm);
	}

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


	public static void transformImage(int[] buf, int[] bufOutput, ColorTransformer.ColorTransformMethod method, float offset, float scale, boolean useColorLUT) {
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

	public static int makeRGB(int v, ColorModel cm) {
		// TODO: Consider inclusion of alpha
		if (cm == null)
			return (255<<24) + (v<<16) + (v<<8) + v;
		else
			return cm.getRGB(v);
	}


}
