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
import qupath.lib.display.ChannelDisplayInfo.ModifiableChannelDisplayInfo;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

abstract class AbstractChannelInfo implements ModifiableChannelDisplayInfo {

	private transient ImageData<BufferedImage> imageData;

	protected float minAllowed, maxAllowed;
	protected float minDisplay, maxDisplay;
	protected boolean clipToAllowed = false;

	// The 'channel' corresponds to the 'band' in Java parlance
	AbstractChannelInfo(final ImageData<BufferedImage> imageData) {
		this.imageData = imageData;
		this.minAllowed = 0;
		this.maxAllowed = (float)Math.pow(2, imageData.getServer().getPixelType().getBitsPerPixel()) - 1;
		this.minDisplay = 0;
		this.maxDisplay = maxAllowed;
	}

	protected ImageData<BufferedImage> getImageData() {
		return imageData;
	}

	protected ImageServer<BufferedImage> getImageServer() {
		return imageData.getServer();
	}

	/**
	 * Returns true if the min and max display are forced into the allowed range, false otherwise.
	 * 
	 * This makes it possible to either be strict about contrast settings or more flexible.
	 * 
	 * @return
	 */
	boolean doClipToAllowed() {
		return clipToAllowed;
	}

	/**
	 * Specify whether min/max display values should be clipped to fall within the allowed range.
	 * 
	 * This makes it possible to either be strict about contrast settings or more flexible.
	 * 
	 * @param clipToAllowed
	 */
	void setClipToAllowed(final boolean clipToAllowed) {
		this.clipToAllowed = clipToAllowed;
		if (clipToAllowed) {
			this.minDisplay = Math.min(Math.max(minDisplay, minAllowed), maxAllowed);
			this.maxDisplay = Math.min(Math.max(maxDisplay, minAllowed), maxAllowed);
		}
	}

	@Override
	public void setMinMaxAllowed(float minAllowed, float maxAllowed) {
		this.minAllowed = minAllowed;
		this.maxAllowed = maxAllowed;
		// Ensure max is not < min
		if (this.maxAllowed <= minAllowed)
			this.maxAllowed = minAllowed + 1;
		// Ensure display in allowed range
		setMinDisplay(minDisplay);
		setMaxDisplay(maxDisplay);
	}

	@Override
	public boolean isBrightnessContrastRescaled() {
		return minAllowed != minDisplay || maxAllowed != maxDisplay;
	}

	@Override
	public void setMinDisplay(float minDisplay) {
		this.minDisplay = clipToAllowed ? Math.max(minAllowed, minDisplay) : minDisplay;
	}

	@Override
	public void setMaxDisplay(float maxDisplay) {
		this.maxDisplay = clipToAllowed ? Math.min(maxAllowed, maxDisplay) : maxDisplay;
	}

	@Override
	public float getMinAllowed() {
		return minAllowed;
	}

	@Override
	public float getMaxAllowed() {
		return maxAllowed;
	}

	@Override
	public float getMinDisplay() {
		return minDisplay;
	}

	@Override
	public float getMaxDisplay() {
		return maxDisplay;
	}

	final static int do8BitRangeCheck(float v) {
		return v < 0 ? 0 : (v > 255 ? 255 : (int)v);
	}

	final static int do8BitRangeCheck(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}

	@Override
	public int updateRGBAdditive(BufferedImage img, int x, int y, int rgb, boolean useColorLUT) {
		// Just return the (scaled) RGB value for this pixel if we don't have to update anything
		int rgbNew = getRGB(img, x, y, useColorLUT);
		if (rgb == 0)
			return rgbNew;

		int r2 = ((rgbNew & ColorTools.MASK_RED) >> 16) + ((rgb & ColorTools.MASK_RED) >> 16);
		int g2 = ((rgbNew & ColorTools.MASK_GREEN) >> 8) + ((rgb & ColorTools.MASK_GREEN) >> 8);
		int b2 = (rgbNew & ColorTools.MASK_BLUE) + (rgb & ColorTools.MASK_BLUE);
		return (do8BitRangeCheck(r2) << 16) + 
				(do8BitRangeCheck(g2) << 8) + 
				do8BitRangeCheck(b2);
	}

	@Override
	public String toString() {
		return getName();
	}


	float getOffset() {
		return getMinDisplay();
	}

	float getScaleToByte() {
		return 255.f / (getMaxDisplay() - getMinDisplay());
	}


}