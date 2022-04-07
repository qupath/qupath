/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.text.DecimalFormat;

import qupath.lib.color.ColorToolsAwt;
import qupath.lib.color.ColorTransformer;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

/**
 * An implementation in which a pixel can be effectively represented by a single float value
 * 
 * @author Pete Bankhead
 */
abstract class AbstractSingleChannelInfo extends AbstractChannelInfo implements SingleChannelDisplayInfo {

	protected static final DecimalFormat df = new DecimalFormat("#.##");
	
	protected static final IndexColorModel CM_GRAYSCALE = ColorToolsAwt.createIndexColorModel(255, 255, 255, false);
	protected static final IndexColorModel CM_GRAYSCALE_INVERTED = ColorToolsAwt.createIndexColorModel(0, 0, 0, true);


	AbstractSingleChannelInfo(final ImageData<BufferedImage> imageData) {
		super(imageData);
	}
	
	/**
	 * Get a {@link ColorModel} to use with a specified {@link ChannelDisplayMode} when converting a value to RGB.
	 * @param mode
	 * @return
	 */
	protected abstract ColorModel getColorModel(ChannelDisplayMode mode);
	
	/**
	 * Get a suitable RGB value for displaying a pixel with the specified value
	 * 
	 * @param value
	 * @param mode 
	 * @return
	 */
	public int getRGB(float value, ChannelDisplayMode mode) {
		return ColorTransformer.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), getColorModel(mode));
	}
	

	private void updateRGBAdditive(float[] values, int[] rgb, ChannelDisplayMode mode) {
		int n = Math.min(values.length, rgb.length);
		for (int i = 0; i < n; i++)
			rgb[i] = updateRGBAdditive(values[i], rgb[i], mode);
	}

	private int[] getRGB(float[] values, int[] rgb, ChannelDisplayMode mode) {
		int n = values.length;
		if (rgb == null)
			rgb = new int[values.length];
		else if (rgb.length < n)
			n = rgb.length;

		//			long start = System.currentTimeMillis();
		for (int i = 0; i < n; i++)
			rgb[i] = getRGB(values[i], mode);
		//			System.out.println("Time: " + (System.currentTimeMillis() - start));
		return rgb;
	}

	@Override
	public int getRGB(BufferedImage img, int x, int y, ChannelDisplayMode mode) {
		return getRGB(getValue(img, x, y), mode);
	}

	private int updateRGBAdditive(float value, int rgb, ChannelDisplayMode mode) {
		// Don't do anything with an existing pixel if display range is 0, or it is lower than the min display
		if (maxDisplay == minDisplay)// || value <= minDisplay)
			return rgb;
		// Just return the (scaled) RGB value for this pixel if we don't have to update anything
		int rgbNew = getRGB(value, mode);
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
	public int[] getRGB(BufferedImage img, int[] rgb, ChannelDisplayMode mode) {
		// TODO: Consider caching (but must be threadsafe)
		float[] values = getValues(img, 0, 0, img.getWidth(), img.getHeight(), (float[])null);
		int[] result = getRGB(values, rgb, mode);
		return result;
	}

	@Override
	public void updateRGBAdditive(BufferedImage img, int[] rgb, ChannelDisplayMode mode) {
		if (!isAdditive())
			throw new UnsupportedOperationException(this + " does not support additive display");
		// TODO: Consider caching (but must be threadsafe)
		float[] values = getValues(img, 0, 0, img.getWidth(), img.getHeight(), (float[])null);
		updateRGBAdditive(values, rgb, mode);
	}


}