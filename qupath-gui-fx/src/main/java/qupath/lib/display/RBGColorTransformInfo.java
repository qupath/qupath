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

import qupath.lib.color.ColorTransformer;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

class RBGColorTransformInfo extends AbstractSingleChannelInfo {

	private transient int[] buffer = null;
	private ColorTransformer.ColorTransformMethod method;
	private transient ColorModel colorModel;
	private transient ColorModel colorModelInverted = null;
	
	private boolean isMutable;
	private transient Integer color = null;

	public RBGColorTransformInfo(final ImageData<BufferedImage> imageData, final ColorTransformer.ColorTransformMethod method, final boolean isMutable) {
		super(imageData);
		this.method = method;
		this.isMutable = isMutable;
		setMinMaxAllowed(0, ColorTransformer.getDefaultTransformedMax(method));
		setMinDisplay(0);
		setMaxDisplay(getMaxAllowed());
		
		colorModel = ColorTransformer.getDefaultColorModel(method);
		if (colorModel != null)
			color = colorModel.getRGB(255);			
		if (colorModel instanceof IndexColorModel) {
			colorModelInverted = invertColorModel((IndexColorModel)colorModel);
		}
	}
	
	private static IndexColorModel invertColorModel(IndexColorModel cm) {
		
		int n = cm.getMapSize();
		int bits = cm.getPixelSize();
		if (bits > 8)
			throw new IllegalArgumentException("Unable to invert color model with " + bits + " bits per pixel");
		
		// For inverted
		byte[] rbi = new byte[n];
		byte[] gbi = new byte[n];
		byte[] bbi = new byte[n];
		for (int i = 0; i < n; i++) {
			int r = cm.getRed(i);
			int g = cm.getGreen(i);
			int b = cm.getBlue(i);
			rbi[i] = (byte)ColorTools.do8BitRangeCheck((255 - r) / 255.0 * i);
			gbi[i] = (byte)ColorTools.do8BitRangeCheck((255 - g) / 255.0 * i);
			bbi[i] = (byte)ColorTools.do8BitRangeCheck((255 - b) / 255.0 * i);
		}
		return new IndexColorModel(bits, n, rbi, gbi, bbi);
	}
	
	@Override
	public String getName() {
		return method.toString();
	}


	@Override
	public float getValue(BufferedImage img, int x, int y) {
		int rgb = img.getRGB(x, y);
		return ColorTransformer.getPixelValue(rgb, method);
	}

	@Override
	public synchronized float[] getValues(BufferedImage img, int x, int y, int w, int h, float[] array) {
		// Try to get the RGB buffer directly
		buffer = RGBDirectChannelInfo.getRGBIntBuffer(img);
		// If we didn't get a buffer the fast way, we need to get one the slow way...
		if (buffer == null)
			buffer = img.getRGB(x, y, w, h, buffer, 0, w);
		return ColorTransformer.getSimpleTransformedPixels(buffer, method, array);
	}


	@Override
	protected ColorModel getColorModel(ChannelDisplayMode mode) {
		switch (mode) {
		case INVERTED_GRAYSCALE:
		case GRAYSCALE:
			return CM_GRAYSCALE;
//			return CM_GRAYSCALE_INVERTED;
		case INVERTED_COLOR:
			return colorModelInverted;
		case COLOR:
		default:
			return colorModel;
		}
	}
	
	
	@Override
	public boolean doesSomething() {
		return true;
	}

	@Override
	public boolean isAdditive() {
		switch (method) {
		case Red:
		case Green:
		case Blue:
			return true;
		default:
			return false;
		}
	}
	
	@Override
	public Integer getColor() {
		return color;
	}
	
	@Override
	public boolean isMutable() {
		return isMutable;
	}

	@Override
	public ColorTransformer.ColorTransformMethod getMethod() {
		return method;
	}

}