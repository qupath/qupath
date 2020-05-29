/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.display;

import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

/**
 * An implementation in which a pixel can be effectively represented by a single float value
 * 
 * @author Pete Bankhead
 */
abstract class AbstractSingleChannelInfo extends AbstractChannelInfo implements SingleChannelDisplayInfo {

	protected static final DecimalFormat df = new DecimalFormat("#.##");


	AbstractSingleChannelInfo(final ImageData<BufferedImage> imageData) {
		super(imageData);
	}

	/**
	 * Get a suitable RGB value for displaying a pixel with the specified value
	 * 
	 * @param value
	 * @param useColorLUT 
	 * @return
	 */
	public abstract int getRGB(float value, boolean useColorLUT);

	private void updateRGBAdditive(float[] values, int[] rgb, boolean useColorLUT) {
		int n = Math.min(values.length, rgb.length);
		for (int i = 0; i < n; i++)
			rgb[i] = updateRGBAdditive(values[i], rgb[i], useColorLUT);
	}

	private int[] getRGB(float[] values, int[] rgb, boolean useColorLUT) {
		int n = values.length;
		if (rgb == null)
			rgb = new int[values.length];
		else if (rgb.length < n)
			n = rgb.length;

		//			long start = System.currentTimeMillis();
		for (int i = 0; i < n; i++)
			rgb[i] = getRGB(values[i], useColorLUT);
		//			System.out.println("Time: " + (System.currentTimeMillis() - start));
		return rgb;
	}

	@Override
	public int getRGB(BufferedImage img, int x, int y, boolean useColorLUT) {
		return getRGB(getValue(img, x, y), useColorLUT);
	}

	private int updateRGBAdditive(float value, int rgb, boolean useColorLUT) {
		// Don't do anything with an existing pixel if display range is 0, or it is lower than the min display
		if (maxDisplay == minDisplay || value <= minDisplay)
			return rgb;
		// Just return the (scaled) RGB value for this pixel if we don't have to update anything
		int rgbNew = getRGB(value, useColorLUT);
		if (rgb == 0)
			return rgbNew;
		if (rgbNew == 0)
			return rgb;

		int r2 = ((rgbNew & ColorTools.MASK_RED) >> 16) + ((rgb & ColorTools.MASK_RED) >> 16);
		int g2 = ((rgbNew & ColorTools.MASK_GREEN) >> 8) + ((rgb & ColorTools.MASK_GREEN) >> 8);
		int b2 = (rgbNew & ColorTools.MASK_BLUE) + (rgb & ColorTools.MASK_BLUE);

		return (do8BitRangeCheck(r2) << 16) + 
				(do8BitRangeCheck(g2) << 8) + 
				do8BitRangeCheck(b2);
	}

	@Override
	public String getValueAsString(BufferedImage img, int x, int y) {
		return df.format(getValue(img, x, y));
	}


	@Override
	public int[] getRGB(BufferedImage img, int[] rgb, boolean useColorLUT) {
		// TODO: Consider caching (but must be threadsafe)
		float[] values = getValues(img, 0, 0, img.getWidth(), img.getHeight(), (float[])null);
		int[] result = getRGB(values, rgb, useColorLUT);
		return result;
	}

	@Override
	public void updateRGBAdditive(BufferedImage img, int[] rgb, boolean useColorLUT) {
		if (!isAdditive())
			throw new UnsupportedOperationException(this + " does not support additive display");
		// TODO: Consider caching (but must be threadsafe)
		float[] values = getValues(img, 0, 0, img.getWidth(), img.getHeight(), (float[])null);
		updateRGBAdditive(values, rgb, useColorLUT);
	}


}