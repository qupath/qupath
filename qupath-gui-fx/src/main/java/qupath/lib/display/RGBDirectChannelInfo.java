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

import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

/**
 * Class for displaying RGB image using direct color model, but perhaps with brightness/contrast adjusted.
 * 
 * @author Pete Bankhead
 *
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
		int rgb = getRGB(img, x, y, false);
		return ColorTools.red(rgb) + ", " + ColorTools.green(rgb) + ", " + ColorTools.blue(rgb);
	}

	@Override
	public int getRGB(BufferedImage img, int x, int y, boolean useColorLUT) {
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
	public int[] getRGB(BufferedImage img, int[] rgb, boolean useColorLUT) {
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
		if (offset != 0 || scale != 1) {
			int ind = 0;
			for (int v : buffer) {
				int r = ColorTools.do8BitRangeCheck((ColorTools.red(v) - offset) * scale);
				int g = ColorTools.do8BitRangeCheck((ColorTools.green(v) - offset) * scale);
				int b = ColorTools.do8BitRangeCheck((ColorTools.blue(v) - offset) * scale);
				rgb[ind] = (r << 16) + (g << 8) + b;
				ind++;
			}
		} else if (buffer != rgb) {
			System.arraycopy(buffer, 0, rgb, 0, rgb.length);
		}
		return rgb;
	}

	@Override
	public void updateRGBAdditive(BufferedImage img, int[] rgb, boolean useColorLUT) {
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