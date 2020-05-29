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

import qupath.lib.color.ColorTransformer;
import qupath.lib.images.ImageData;

/**
 * Class for displaying RGB image after normalizing RGB optical densities, and thresholding unstained pixels.
 * 
 * TODO: Consider if this is generally worthwhile enough to keep.
 * 
 * @author Pete Bankhead
 *
 */
class RGBNormalizedChannelInfo extends RGBDirectChannelInfo {
	
	public RGBNormalizedChannelInfo(final ImageData<BufferedImage> imageData) {
		super(imageData);
	}

	@Override
	public String getName() {
		return "Normalized OD colors";
	}

	@Override
	public int getRGB(BufferedImage img, int x, int y, boolean useColorLUT) {
		return ColorTransformer.getODNormalizedColor(img.getRGB(x, y), 0.1, 0, 1);
	}
	
	
	@Override
	public int[] getRGB(BufferedImage img, int[] rgb, boolean useColorLUT) {
		// Try to get a data buffer directly, if possible
		int[] buffer = getRGBIntBuffer(img);
		if (buffer == null) {
			// If we wouldn't get a buffer, ask for the RGB values the slow way
			rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), rgb, 0, img.getWidth());
			buffer = rgb;
		} else if (rgb == null)
			rgb = new int[img.getWidth() * img.getHeight()];

		// Rescale only if we must
		float offset = getOffset();
		float scale = getScaleToByte();
		ColorTransformer.transformRGB(buffer, rgb, ColorTransformer.ColorTransformMethod.OD_Normalized, offset, scale, false);
		return rgb;
	}

	@Override
	public boolean doesSomething() {
		return true;
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