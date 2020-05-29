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

import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

class RBGColorDeconvolutionInfo extends AbstractSingleChannelInfo {

	private transient int stainNumber;
	private transient ColorDeconvolutionStains stains;
	private transient ColorModel colorModel = null;
	private transient Integer color;

	private ColorTransformMethod method;

	public RBGColorDeconvolutionInfo(final ImageData<BufferedImage> imageData, ColorTransformMethod method) {
		super(imageData);
		this.method = method;
		switch (method) {
		case Stain_1:
			stainNumber = 1;
			break;
		case Stain_2:
			stainNumber = 2;
			break;
		case Stain_3:
			stainNumber = 3;
			break;
		default:
			stainNumber = -1;
		}
		setMinMaxAllowed(0f, 3f);
		setMinDisplay(0);
		setMaxDisplay(1.5f);
	}

	final void ensureStainsUpdated() {
		ImageData<BufferedImage> imageData = getImageData();
		stains = imageData == null ? null : imageData.getColorDeconvolutionStains();
		if (stainNumber < 0) {
			color = ColorTools.makeRGB(255, 255, 255);
			colorModel = ColorTransformer.getDefaultColorModel(method);
		} else if (stains != null) {
			color = stains.getStain(stainNumber).getColor();
			colorModel = ColorToolsAwt.getIndexColorModel(stains.getStain(stainNumber));
		}
	}

	@Override
	public float getValue(BufferedImage img, int x, int y) {
		ensureStainsUpdated();
		if (stains == null)
			return 0f;
		int rgb = img.getRGB(x, y);
		if (method == null)
			return ColorTransformer.colorDeconvolveRGBPixel(rgb, stains, stainNumber-1);
		else if (method == ColorTransformMethod.Optical_density_sum) {
			int r = ColorTools.red(rgb);
			int g = ColorTools.green(rgb);
			int b = ColorTools.blue(rgb);
			return (float)(ColorDeconvolutionHelper.makeOD(r, stains.getMaxRed()) +
					ColorDeconvolutionHelper.makeOD(g, stains.getMaxGreen()) + 
					ColorDeconvolutionHelper.makeOD(b, stains.getMaxBlue()));
		} else
			return ColorTransformer.getPixelValue(rgb, method, stains);
	}

	@Override
	public synchronized float[] getValues(BufferedImage img, int x, int y, int w, int h, float[] array) {
		ensureStainsUpdated();
		if (stains == null) {
			if (array == null)
				return new float[w * h];
			return array;
		}
		int[] buffer = RGBDirectChannelInfo.getRGBIntBuffer(img);
		if (buffer == null)
			buffer = img.getRGB(x, y, w, h, null, 0, w);
		return ColorTransformer.getTransformedPixels(buffer, method, array, stains);
	}

	@Override
	public int getRGB(float value, boolean useColorLUT) {
		return ColorTransformer.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), useColorLUT ? colorModel : null);
		//		transformer.transformImage(buf, bufOutput, method, offset, scale, useColorLUT);
		// TODO Auto-generated method stub
		//		return 0;
	}

	@Override
	public String getName() {
		ensureStainsUpdated();
		if (stainNumber > 0) {
			if (stains == null)
				return "Stain " + stainNumber + " (missing)";
			else
				return stains.getStain(stainNumber).getName();
		}
		if (method != null)
			return method.toString();
		return "Unknown color deconvolution transform";
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
		if (color == null)
			ensureStainsUpdated();
		return color;
	}

	@Override
	public boolean isMutable() {
		return true;
	}


	//		@Override
	//		public boolean isInteger() {
	//			return false;
	//		}

}