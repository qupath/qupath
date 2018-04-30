/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.display;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.text.DecimalFormat;

import qupath.lib.awt.color.ColorToolsAwt;
import qupath.lib.awt.color.ColorTransformerAWT;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;

/**
 * Interface used to control the display of single channels of image data, where
 * 'single channel' means one value per pixel (in Java's parlance, one band for the
 * SampleModel).  This applies not only to the 'default' channels in an image -
 * e.g. red, green and blue for an RGB image - but also to 'derived' channels computed
 * by a transformation, e.g. color deconvolution.
 * 
 * The primary uses are:
 * 	- to extract floating point pixel values for the channel from a BufferedImage
 * 		(either directly, or via some color transformation that may involve
 * 		 more than one channel/band from the BufferedImage)
 * 	- to generate RGB pixel values suitable for visualizing the raw channel values
 * 		extracted above, including the storage of any lookup tables required
 * 	- to store min/max display values, which influence the lookup table mapping to RGB
 * 		(i.e. to store brightness/contrast values)
 * 	- to update an existing RGB value, to facilitate creating composite images that depict
 *      the values of multiple channels in a single, merged visualization
 * 
 * As such, its uses lie somewhere between Java's SampleModel and ColorModel classes.
 * 
 * Its reason for existing is that sometimes we need to be able to adjust the display of channels
 * individually and to create merges - particularly in the case of fluorescence images - but
 * to simplify whole slide image support we need to be able to do this on-the-fly.
 * Switching the ColorModel for an existing BufferedImage is somewhat awkward, and when caching
 * image tiles we want to be able to keep the original ColorModels intact - otherwise ColorModels
 * for recently-seen tiles might be different from the ColorModels of tiles that have been in the cache
 * for longer, even though they were read from the same image servers.  Furthermore, 'unknown' image types
 * (i.e. not standard RGB/BGR/single-channel images) don't always behave nicely with Graphics objects
 * if we want to paint or scale them.
 * 
 * Using the ChannelDisplayInfo approach means that during repainting a RGB(A) image can be produced on-the-fly
 * without needing to create a new image with the desired ColorModel for painting.
 * This potentially ends up requiring a bit more computation that is really necessary - and it may be optimized
 * better in the future - but it was the simplest method I could come up with to provide the features I wanted...
 * 
 * 
 * @author Pete Bankhead
 *
 */
public interface ChannelDisplayInfo {

	/**
	 * Set the maximum permissible range for the image display.
	 * For an 8-bit image, that should be 0 and 255.
	 * For a 16-bit image, fewer bits might actually be used... therefore the full range of 0-2^16-1 may be too much.
	 * Also, for a 32-bit floating point image the limits are rather harder to define in a general way.
	 * This method makes it possible to restrict the permissible range to something sensible.
	 * Brightness/contrast/min/max sliders may make use of this.
	 * 
	 * @param minAllowed
	 * @param maxAllowed
	 */
	public abstract void setMinMaxAllowed(float minAllowed, float maxAllowed);

	/**
	 * Get the channel name.  This may also be returned by the toString() method.
	 * 
	 * @return
	 */
	public abstract String getName();

	/**
	 * Set the min display value for this channel.
	 * Note that it is *strongly* advised to use <code>ImageDisplay.setMinMaxDisplay</code> instead 
	 * since this helps ensure that the <code>ImageDisplay</code> fires appropriate events etc.
	 * 
	 * @see ImageDisplay
	 * 
	 * @param minDisplay
	 */
	@Deprecated
	public abstract void setMinDisplay(float minDisplay);

	/**
	 * Set the max display value for this channel.
	 * Note that it is *strongly* advised to use <code>ImageDisplay.setMinMaxDisplay</code> instead 
	 * since this helps ensure that the <code>ImageDisplay</code> fires appropriate events etc.
	 * 
	 * @see ImageDisplay
	 * 
	 * @param maxDisplay
	 */
	@Deprecated
	public abstract void setMaxDisplay(float maxDisplay);
	
	/**
	 * Get the min display value.
	 * This is used to control the brightness/contrast when painting.
	 * 
	 * @return
	 */
	public abstract float getMinDisplay();

	/**
	 * Get the max display value.
	 * This is used to control the brightness/contrast when painting.
	 * 
	 * @return
	 */
	public abstract float getMaxDisplay();

	/**
	 * Get the min allowed display value.
	 * This is only a hint.
	 * 
	 * @return
	 */
	public abstract float getMinAllowed();

	/**
	 * Get the max allowed display value.
	 * This is only a hint.
	 * 
	 * @return
	 */
	public abstract float getMaxAllowed();

	/**
	 * Returns true if this channel can be used additively to create a composite image display;
	 * returns false if this channel wants all the color information to itself, so can't be displayed with others.
	 * 
	 * @return
	 */
	public abstract boolean isAdditive();

	/**
	 * Returns true if rescaling according to min &amp; max display levels is applied, false if the full display range is used.
	 * 
	 * @return
	 */
	public boolean isBrightnessContrastRescaled();
	
	/**
	 * Get a string representation of a pixel's value.
	 * This might be a single number, or 3 numbers for an RGB image where the channel includes all values.
	 * 
	 * @param img
	 * @param x
	 * @param y
	 * @return
	 */
	public String getValueAsString(BufferedImage img, int x, int y);

	/**
	 * Get the RGB value that would be used to display a particular pixel
	 * 
	 * @param img
	 * @param x
	 * @param y
	 * @param useColorLUT
	 * @return
	 */
	public abstract int getRGB(BufferedImage img, int x, int y, boolean useColorLUT);
	
	/**
	 * Get the RGB values that would be used to display all the pixels of an image
	 * 
	 * @param img
	 * @param rgb
	 * @param useColorLUT
	 * @return
	 */
	public abstract int[] getRGB(BufferedImage img, int[] rgb, boolean useColorLUT);
	
	/**
	 * Update an existing pixel (packed RGB) additively using the color used to display a specified one
	 * 
	 * @param img
	 * @param x
	 * @param y
	 * @param rgb
	 * @return
	 */
	public abstract int updateRGBAdditive(BufferedImage img, int x, int y, int rgb, boolean useColorLUT);
	
	/**
	 * Update an array of existing pixels (packed RGB) additively using the colors to display a specified image.
	 * May throw an UnsupportedOperationException if isAdditive() returns false;
	 * 
	 * @param img
	 * @param rgb
	 * @param useColorLUT
	 */
	public void updateRGBAdditive(BufferedImage img, int[] rgb, boolean useColorLUT);
	
	
	/**
	 * Optional operation.
	 * Some implementing classes may use buffers to improve performance when applying transforms.
	 * Running this command is an instruction that the class may not be used for a while, so this memory can (and should) be freed.
	 */
	public void resetBuffers();


	/**
	 * Returns true if this does something - anything.
	 * Returns false if not, e.g. if we have an RGB image, with no transformations of any kind applied (e.g. brightness/contrast)
	 */
	public boolean doesSomething();
	
	
	/**
	 * Predominate color used when this ChannelDisplayInfo uses a Color LUT (e.g. Color.RED for a red channel).
	 * Returns null if there is no appropriate color choice, or the image is RGB.
	 * @return
	 */
	public Integer getColor();
	





	static abstract class AbstractChannelInfo implements ChannelDisplayInfo {

		protected float minAllowed, maxAllowed;
		protected float minDisplay, maxDisplay;
		protected boolean clipToAllowed = false;

		// The 'channel' corresponds to the 'band' in Java parlance
		public AbstractChannelInfo(int nBits) {
			this.minAllowed = 0;
			this.maxAllowed = (float)Math.pow(2, nBits) - 1;
			this.minDisplay = 0;
			this.maxDisplay = maxAllowed;
		}

		/**
		 * Returns true if the min and max display are forced into the allowed range, false otherwise.
		 * 
		 * This makes it possible to either be strict about contrast settings or more flexible.
		 * 
		 * @return
		 */
		public boolean doClipToAllowed() {
			return clipToAllowed;
		}
		
		/**
		 * Specify whether min/max display values should be clipped to fall within the allowed range.
		 * 
		 * This makes it possible to either be strict about contrast settings or more flexible.
		 * 
		 * @param clipToAllowed
		 */
		public void setClipToAllowed(final boolean clipToAllowed) {
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
		
		final public static int do8BitRangeCheck(float v) {
			return v < 0 ? 0 : (v > 255 ? 255 : (int)v);
		}

		final public static int do8BitRangeCheck(int v) {
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
		
		
		public float getOffset() {
			return getMinDisplay();
		}

		public float getScaleToByte() {
			return 255.f / (getMaxDisplay() - getMinDisplay());
		}


	}

	
	static class RGBColorReconvolution extends AbstractChannelInfo {
		
		private ImageDisplay imageDisplay;
		private ColorDeconvolutionStains stainsTarget;
		private boolean discardResidual;

		public RGBColorReconvolution(ImageDisplay imageDisplay, ColorDeconvolutionStains stainsTarget, boolean discardResidual) {
			super(8);
			this.imageDisplay = imageDisplay;
			this.stainsTarget = stainsTarget;
			this.discardResidual = discardResidual;
		}

		@Override
		public String getName() {
			return "Reconvolve standard stains";
		}

		@Override
		public boolean isAdditive() {
			return false;
		}

		@Override
		public String getValueAsString(BufferedImage img, int x, int y) {
			int rgb = getRGB(img, x, y, false);
			return ColorTools.red(rgb) + ", " + ColorTools.green(rgb) + ", " + ColorTools.blue(rgb);
		}

		@Override
		public int getRGB(BufferedImage img, int x, int y, boolean useColorLUT) {
			int[] arr = new int[]{img.getRGB(x, y)};
			return ColorTransformerAWT.colorDeconvolveReconvolveRGBArray(arr, imageDisplay.getImageData().getColorDeconvolutionStains(), stainsTarget, discardResidual, arr)[0];
		}

		@Override
		public int[] getRGB(BufferedImage img, int[] rgb, boolean useColorLUT) {
			int[] buffer = RGBDirectChannelInfo.getRGBIntBuffer(img);
			if (buffer == null)
				buffer = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
			return ColorTransformerAWT.colorDeconvolveReconvolveRGBArray(buffer, imageDisplay.getImageData().getColorDeconvolutionStains(), stainsTarget, discardResidual, rgb, getScaleToByte(), -getOffset());
		}

		@Override
		public void updateRGBAdditive(BufferedImage img, int[] rgb, boolean useColorLUT) {
			throw new UnsupportedOperationException(this + " does not support additive display");
		}

		@Override
		public void resetBuffers() {}

		@Override
		public boolean doesSomething() {
			return !stainsTarget.equals(imageDisplay.getImageData().getColorDeconvolutionStains());
		}
		
		@Override
		public Integer getColor() {
			return null;
		}
		
	}



	public static interface SingleChannelDisplayInfo extends ChannelDisplayInfo {
		
		public abstract float getValue(BufferedImage img, int x, int y);
		
		public abstract float[] getValues(BufferedImage img, int x, int y, int w, int h, float[] array);
		
//		public boolean isInteger();
		
	}


	/**
	 * An implementation in which a pixel can be effectively represented by a single float value
	 * 
	 * @author Pete Bankhead
	 */
	static abstract class AbstractSingleChannelInfo extends AbstractChannelInfo implements SingleChannelDisplayInfo {
		
		protected static final DecimalFormat df = new DecimalFormat("#.##");
		
		private float[] values = null; // Buffer in which to store values
		
		
		public AbstractSingleChannelInfo(int nBits) {
			super(nBits);
		}

		/**
		 * Get a suitable RGB value for displaying a pixel with the specified value
		 * 
		 * @param value
		 * @return
		 */
		public abstract int getRGB(float value, boolean useColorLUT);
				
		public void updateRGBAdditive(float[] values, int[] rgb, boolean useColorLUT) {
			int n = Math.min(values.length, rgb.length);
			for (int i = 0; i < n; i++)
				rgb[i] = updateRGBAdditive(values[i], rgb[i], useColorLUT);
		}
		
		public int[] getRGB(float[] values, int[] rgb, boolean useColorLUT) {
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
		
		public int updateRGBAdditive(float value, int rgb, boolean useColorLUT) {
			// Don't do anything with an existing pixel if display range is 0, or it is lower than the min display
			if (maxDisplay == minDisplay || value <= minDisplay)
				return rgb;
			// Just return the (scaled) RGB value for this pixel if we don't have to update anything
			int rgbNew = getRGB(value, useColorLUT);
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
		public String getValueAsString(BufferedImage img, int x, int y) {
			return df.format(getValue(img, x, y));
		}
		
		
		@Override
		public int[] getRGB(BufferedImage img, int[] rgb, boolean useColorLUT) {
			values = getValues(img, 0, 0, img.getWidth(), img.getHeight(), values);
			int[] result = getRGB(values, rgb, useColorLUT);
			return result;
		}
		
		@Override
		public void updateRGBAdditive(BufferedImage img, int[] rgb, boolean useColorLUT) {
			if (!isAdditive())
				throw new UnsupportedOperationException(this + " does not support additive display");
			values = getValues(img, 0, 0, img.getWidth(), img.getHeight(), values);
			updateRGBAdditive(values, rgb, useColorLUT);
		}
		
		@Override
		public void resetBuffers() {
			values = null;
		}
		
		
	}
	
	
	/**
	 * Class for displaying RGB image using direct color model, but perhaps with brightness/contrast adjusted.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class RGBDirectChannelInfo extends AbstractChannelInfo {
		
		public RGBDirectChannelInfo() {
			super(8);
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
		
		
		protected static int[] getRGBIntBuffer(BufferedImage img) {
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
		public void resetBuffers() {}

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
	
	
	
	/**
	 * Class for displaying RGB image after normalizing RGB optical densities, and thresholding unstained pixels.
	 * 
	 * TODO: Consider if this is generally worthwhile enough to keep.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class RGBNormalizedChannelInfo extends RGBDirectChannelInfo {
		
		public RGBNormalizedChannelInfo() {
			super();
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
			ColorTransformerAWT.transformRGB(buffer, rgb, ColorTransformer.ColorTransformMethod.OD_Normalized, offset, scale, false);
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
	
	
	
	
	static class RBGColorTransformInfo extends AbstractSingleChannelInfo {

		private int[] buffer = null;
		private ColorTransformer.ColorTransformMethod method;
		private ColorModel colorModel;
		
		private transient Integer color = null;

		public RBGColorTransformInfo(ColorTransformer.ColorTransformMethod method) {
			super(8);
			this.method = method;
			setMinMaxAllowed(0, ColorTransformer.getDefaultTransformedMax(method));
			setMinDisplay(0);
			setMaxDisplay(getMaxAllowed());
			
			colorModel = ColorTransformerAWT.getDefaultColorModel(method);
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
			return ColorTransformerAWT.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), useColorLUT ? colorModel : null);
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


	}
	
	
	
	
	static class RBGColorDeconvolutionInfo extends AbstractSingleChannelInfo {

		private int stainNumber;
		private ImageDisplay imageDisplay;
		private ColorDeconvolutionStains stains;
		private ColorModel colorModel = null;
		private transient Integer color;
		
		private ColorTransformMethod method;

		public RBGColorDeconvolutionInfo(ImageDisplay imageDisplay, ColorTransformMethod method) {
			super(8);
			this.imageDisplay = imageDisplay;
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

		public final void ensureStainsUpdated() {
			if (imageDisplay == null || imageDisplay.getImageData() == null) {
				stains = null;
				return;
			}
			stains = imageDisplay.getImageData().getColorDeconvolutionStains();
			if (stainNumber < 0) {
				color = ColorTools.makeRGB(255, 255, 255);
				colorModel = ColorTransformerAWT.getDefaultColorModel(method);
			} else {
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
				return ColorTransformerAWT.colorDeconvolveRGBPixel(rgb, stains, stainNumber-1);
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
			return ColorTransformerAWT.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), useColorLUT ? colorModel : null);
			//		transformer.transformImage(buf, bufOutput, method, offset, scale, useColorLUT);
			// TODO Auto-generated method stub
			//		return 0;
		}

		@Override
		public String getName() {
			ensureStainsUpdated();
			if (method != null)
				return method.toString();
			if (stains == null)
				return "Stain " + stainNumber + " (missing)";
			return stains.getStain(stainNumber).getName();
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
		
//		@Override
//		public boolean isInteger() {
//			return false;
//		}

	}






	/**
	 * ChannelInfo intended for use with a single or multichannel image (possibly fluorescence)
	 * where the pixel's value is used to scale a single color according to a specified display range.
	 *
	 * If the pixel's value is &gt;= maxDisplay, the pure color is used.
	 *
	 * If the pixel's value is &lt;= minDisplay, the black is used.
	 *
	 * Otherwise, a scaled version of the color is used
	 *
	 * The end result is like having a lookup table (LUT) that stretches from black to the 'pure' color specified,
	 * but without actually generating the LUT.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class MultiChannelInfo extends AbstractSingleChannelInfo {
		
		private String name;
		private int channel;

		transient private ColorModel cm;
		transient private int[] rgbLUT;
		private int rgb;
//		private int rgb, r, g, b;

		// The 'channel' corresponds to the 'band' in Java parlance
		public MultiChannelInfo(String name, int nBits, int channel, int r, int g, int b) {
			super(nBits);
			this.name = name;
			this.channel = channel;
			setLUTColor(r, g, b);
		}
		
//		@Override
//		public boolean isInteger() {
//			return true;
//		}
		
		@Override
		public String getName() {
			return name;
		}


		public void setLUTColor(int rgb) {
			setLUTColor(
					ColorTools.red(rgb),
					ColorTools.green(rgb),
					ColorTools.blue(rgb));
		}

		public void setLUTColor(int r, int g, int b) {
			// Create a LUT
			rgbLUT = new int[256];
			byte[] rb = new byte[256];
			byte[] gb = new byte[256];
			byte[] bb = new byte[256];
			for (int i = 0; i < 256; i++) {
				rgbLUT[i] = ColorTools.makeRGB(
						ColorTools.do8BitRangeCheck(r / 255.0 * i),
						ColorTools.do8BitRangeCheck(g / 255.0 * i),
						ColorTools.do8BitRangeCheck(b / 255.0 * i)
						);
				rb[i] = (byte)ColorTools.do8BitRangeCheck(r / 255.0 * i);
				gb[i] = (byte)ColorTools.do8BitRangeCheck(g / 255.0 * i);
				bb[i] = (byte)ColorTools.do8BitRangeCheck(b / 255.0 * i);
			}
			
			cm = new IndexColorModel(8, 256, rb, gb, bb);
			
			this.rgb = ColorTools.makeRGB(r, g, b);
//			this.rgb = (r << 16) + (g << 8) + b;
			
//			this.r = do8BitRangeCheck(r);
//			this.g = do8BitRangeCheck(g);
//			this.b = do8BitRangeCheck(b);
//			this.rgb = (r << 16) + (g << 8) + b;
		}

		@Override
		public float getValue(BufferedImage img, int x, int y) {
			return img.getRaster().getSampleFloat(x, y, channel);
		}

		@Override
		public float[] getValues(BufferedImage img, int x, int y, int w, int h, float[] array) {
			if (array == null || array.length < w * h)
				array = new float[w * h];
//			long start = System.currentTimeMillis();
			float[] samples = img.getRaster().getSamples(x, y, w, h, channel, array);
//			System.err.println("Time here: " + (System.currentTimeMillis() - start));
			return samples;
		}

		@Override
		public int getRGB(float value, boolean useColorLUT) {
			return ColorTransformerAWT.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), useColorLUT ? cm : null);
			
//			// Don't do anything if display range is 0, or it is lower than the min display
//			if (maxDisplay == minDisplay || value <= minDisplay)
//				return 0;
//			// Return pixel unchanged if it is maximal
//			if (value >= maxDisplay) {
//				if (useColorLUT)
//					return rgb;
//				else
//					return ColorTransformerAWT.makeRGB(255, null);
//			}
//			// Scale the pixel brightness according to its value
//			float scale = (value - minDisplay) / (maxDisplay - minDisplay);
//			if (useColorLUT)
////				return cm.getRGB(ColorTools.do8BitRangeCheck(value * scale));
//				return rgbLUT[ColorTools.do8BitRangeCheck(255*scale)];
//			else
//				return ColorTransformerAWT.makeRGBwithRangeCheck(255*scale, null);
		}

		@Override
		public boolean doesSomething() {
			return true;
		}

		@Override
		public boolean isAdditive() {
			return true;
		}
		
		@Override
		public Integer getColor() {
			return rgb;
		}

		//	@Override
		//	public int updateRGBAdditive(float value, int rgb) {
		//		// Just return the (scaled) RGB value for this pixel if we don't have to update anything
		//		if (rgb == 0)
		//			return getRGB(value);
		//		// Don't do anything with an existing pixel if display range is 0, or it is lower than the min display
		//		if (maxDisplay == minDisplay || value <= minDisplay)
		//			return rgb;
		//		//		// Also nothing to do if the pixel is white
		//		//		if ((rgb & 0xffffff) == 16777215) {
		//		//			// TODO: REMOVE THIS
		//		//			System.out.println("I AM SKIPPING A WHITE PIXEL!");
		//		//			return rgb;
		//		//		}
		//		// Figure out how much to scale the pixel's color - zero scale indicates black
		//		float scale = (value - minDisplay) / (maxDisplay - minDisplay);
		//		if (scale >= 1)
		//			scale = 1;
		//		// Do the scaling & combination
		//		float r2 = r * scale + ((rgb & ColorTransformer.MASK_RED) >> 16);
		//		float g2 = g * scale + ((rgb & ColorTransformer.MASK_GREEN) >> 8);
		//		float b2 = b * scale + (rgb & ColorTransformer.MASK_BLUE);
		//		return do8BitRangeCheck(r2) << 16 + 
		//				do8BitRangeCheck(g2) << 8 + 
		//				do8BitRangeCheck(b2);
		//	}

	}

}