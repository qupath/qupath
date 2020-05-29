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
import java.awt.image.ColorModel;

import qupath.lib.color.ColorTransformer;
import qupath.lib.images.ImageData;

class RBGColorTransformInfo extends AbstractSingleChannelInfo {

	private transient int[] buffer = null;
	private ColorTransformer.ColorTransformMethod method;
	private transient ColorModel colorModel;
	
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
	public int getRGB(float value, boolean useColorLUT) {
		return ColorTransformer.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), useColorLUT ? colorModel : null);
		//		transformer.transformImage(buf, bufOutput, method, offset, scale, useColorLUT);
		// TODO Auto-generated method stub
		//		return 0;
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



}