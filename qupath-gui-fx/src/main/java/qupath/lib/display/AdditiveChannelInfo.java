/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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

import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class for combining channels additively, then providing controls on the resulting RGB image.
 * <p>
 * This was created to support the display of non-8-bit brightfield images in a similar way to
 * RGB images.
 *
 * @since v0.6.0
 */
public class AdditiveChannelInfo extends AbstractChannelInfo {

	private final List<DirectServerChannelInfo> channels;

	public AdditiveChannelInfo(final ImageData<BufferedImage> imageData,
							   final List<DirectServerChannelInfo> channels) {
		super(imageData);
		this.channels = channels == null ? List.of() : List.copyOf(channels);
		setMinMaxAllowed(0, 255);
		setClipToAllowed(true);
	}

	@Override
	public String getName() {
		return "Original (composite)";
	}

	@Override
	public String getValueAsString(BufferedImage img, int x, int y) {
		return channels.stream()
				.map(c -> c.getValueAsString(img, x, y))
				.collect(Collectors.joining(", "));
	}

	@Override
	public int getRGB(BufferedImage img, int x, int y, ChannelDisplayMode mode) {
		int r = 0, g = 0, b = 0;
		for (var channel : channels) {
			int val = channel.getRGB(img, x, y, mode);
			r += ColorTools.red(val);
			g += ColorTools.green(val);
			b += ColorTools.blue(val);
		}
		return (do8BitRangeCheck(r) << 16) +
				(do8BitRangeCheck(g) << 8) +
				do8BitRangeCheck(b);
	}

	@Override
	public int[] getRGB(BufferedImage img, int[] rgb, ChannelDisplayMode mode) {
		if (rgb == null) {
			rgb = new int[img.getWidth() * img.getHeight()];
		}
		for (var c : channels) {
			c.updateRGBAdditive(img, rgb, ChannelDisplayMode.COLOR);
		}
		return RGBDirectChannelInfo.transformRGB(rgb, rgb, mode, getOffset(), getScaleToByte());
	}

	@Override
	public void updateRGBAdditive(BufferedImage img, int[] rgb, ChannelDisplayMode mode) {
		throw new UnsupportedOperationException(this + " does not support additive display");

	}

	@Override
	public boolean doesSomething() {
		return !channels.isEmpty();
	}

	@Override
	public boolean isAdditive() {
		return false;
	}

	@Override
	public Integer getColor() {
		return null;
	}

	/**
	 * Get an unmodifiable list of the channels that are merged here for display.
	 * @return a list of channels
	 */
	public List<DirectServerChannelInfo> getChannels() {
		return channels;
	}

}
