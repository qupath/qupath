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

import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

/**
 * Class for displaying RGB image using direct color model, but perhaps with brightness/contrast adjusted.
 * 
 * @author Pete Bankhead
 * @implNote this currently does not support {@link ChannelDisplayMode}
 */
class RGBDirectChannelInfo extends AbstractChannelInfo {

	public RGBDirectChannelInfo(final ImageData<BufferedImage> imageData) {
		super(imageData);
	}

	@Override
	public String getName() {
		return "Original";
	}

	@Override
	public String getValueAsString(BufferedImage img, int x, int y) {
		int rgb = getRGB(img, x, y, ChannelDisplayMode.COLOR);
		return ColorTools.red(rgb) + ", " + ColorTools.green(rgb) + ", " + ColorTools.blue(rgb);
	}

	@Override
	public int getRGB(BufferedImage img, int x, int y, ChannelDisplayMode mode) {
		return img.getRGB(x, y);
	}


	static int[] getRGBIntBuffer(BufferedImage img) {
		int type = img.getType();
		if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_ARGB_PRE) {
			return (int[])img.getRaster().getDataElements(0, 0, img.getWidth(), img.getHeight(), (int[])null);
			// The following code was better for Java 7/8 on a Mac, but terrible for Java 6
			// See http://www.jhlabs.com/ip/managed_images.html for some info
			//				DataBuffer db = img.getRaster().getDataBuffer();
			//				if (db instanceof DataBufferInt) {
			//						return ((DataBufferInt)db).getData();
			//				}
		}
		return null;
	}


	@Override
	public int[] getRGB(BufferedImage img, int[] rgb, ChannelDisplayMode mode) {
		
		// TODO: Add support for ChannelDisplayMode (if it makes sense!)
		boolean doInvert = mode == ChannelDisplayMode.INVERTED_COLOR || mode == ChannelDisplayMode.INVERTED_GRAYSCALE;
		boolean doGrayscale = mode == ChannelDisplayMode.GRAYSCALE || mode == ChannelDisplayMode.INVERTED_GRAYSCALE;
		
		// Try to get a data buffer directly, if possible
		int[] buffer = getRGBIntBuffer(img);
		if (buffer == null) {
			// If we wouldn't get a buffer, ask for the RGB values the slow way
			rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), rgb, 0, img.getWidth());
			buffer = rgb;
		} else if (rgb == null || rgb.length < buffer.length) {
			rgb = new int[img.getWidth() * img.getHeight()];
		}

		// Rescale only if we must
		float offset = getOffset();
		float scale = getScaleToByte();
		//			ColorTransformer.transformImage(buffer, buffer, ColorTransformMethod.OD_Normalized, offset, scale, false);
		if (offset != 0 || scale != 1 || doInvert || doGrayscale) {
			int ind = 0;
			for (int v : buffer) {
				if (doGrayscale) {
					// We convert to grayscale using weighted RGB values
					double r = ((ColorTools.red(v) - offset) * scale);
					double g = ((ColorTools.green(v) - offset) * scale);
					double b = ((ColorTools.blue(v) - offset) * scale);
					// Alternative conversion simply averaging RGB values
//					int value = ColorTools.do8BitRangeCheck((r + g + b) / 3.0);
					// Using weighting
					int value = ColorTools.do8BitRangeCheck((0.299 * r + 0.587 * g + 0.114 * b));
					rgb[ind] = ColorTools.packRGB(value, value, value);
				} else if (doInvert) {
					// Get the original RGB values
					double r = ((ColorTools.red(v) - offset) * scale);
					double g = ((ColorTools.green(v) - offset) * scale);
					double b = ((ColorTools.blue(v) - offset) * scale);
					
					// Pragmatic approach... TODO: find a more theoretically justified one!		
					// Dividing by two here gives a more visually useful result and avoids a very dark image 
					// (as otherwise each RGB value effectively gets subtracted twice)
					int r2 = ColorTools.do8BitRangeCheck((g + b)/2);
					int g2 = ColorTools.do8BitRangeCheck((r + b)/2);
					int b2 = ColorTools.do8BitRangeCheck((r + g)/2);
					
					// Alternative code without the division
//					int r2 = ColorTools.do8BitRangeCheck((g + b));
//					int g2 = ColorTools.do8BitRangeCheck((r + b));
//					int b2 = ColorTools.do8BitRangeCheck((r + g));

					rgb[ind] = (r2 << 16) + (g2 << 8) + b2;
				} else {
					int r = ColorTools.do8BitRangeCheck((ColorTools.red(v) - offset) * scale);
					int g = ColorTools.do8BitRangeCheck((ColorTools.green(v) - offset) * scale);
					int b = ColorTools.do8BitRangeCheck((ColorTools.blue(v) - offset) * scale);
					rgb[ind] = (r << 16) + (g << 8) + b;
				}
				ind++;
			}
		} else if (buffer != rgb) {
			System.arraycopy(buffer, 0, rgb, 0, rgb.length);
		}
		return rgb;
	}

	@Override
	public void updateRGBAdditive(BufferedImage img, int[] rgb, ChannelDisplayMode mode) {
		throw new UnsupportedOperationException(this + " does not support additive display");

	}

	@Override
	public boolean doesSomething() {
		return isBrightnessContrastRescaled();
	}

	@Override
	public boolean isAdditive() {
		return false;
	}

	@Override
	public Integer getColor() {
		return null;
	}

}