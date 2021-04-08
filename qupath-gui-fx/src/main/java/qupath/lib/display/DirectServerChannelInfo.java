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
import java.awt.image.IndexColorModel;

import qupath.lib.color.ColorTransformer;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;

/**
 * ChannelInfo intended for use with a single or multichannel image (possibly fluorescence)
 * where the pixel's value is used to scale a single color according to a specified display range according to the following rules:
 * <ul>
 * <li>If the pixel's value is &gt;= maxDisplay, the pure color is used.</li>
 * <li>If the pixel's value is &lt;= minDisplay, the black is used.</li>
 * <li>Otherwise, a scaled version of the color is used</li>
 * </ul>
 * 
 * The end result is like having a lookup table (LUT) that stretches from black to the 'pure' color specified,
 * but without actually generating the LUT.
 * 
 * @author Pete Bankhead
 *
 */
public class DirectServerChannelInfo extends AbstractSingleChannelInfo {

	private int channel;

	transient private ColorModel cm;
	transient private int[] rgbLUT;
	private int rgb;
	//		private int rgb, r, g, b;

	/**
	 * Constructor.
	 * @param imageData the image
	 * @param channel the channel number (0-based index)
	 */
	public DirectServerChannelInfo(final ImageData<BufferedImage> imageData, int channel) {
		super(imageData);
		this.channel = channel;
		setLUTColor(imageData.getServer().getChannel(channel).getColor());
	}

	/**
	 * Get the channel number.
	 * @return
	 */
	public int getChannel() {
		return channel;
	}

	//		@Override
	//		public boolean isInteger() {
	//			return true;
	//		}

	@Override
	public String getName() {
		String name = "Channel " + (channel + 1);
		ImageData<BufferedImage> imageData = getImageData();
		String channelName = imageData == null ? null : imageData.getServer().getChannel(channel).getName();
		if (channelName == null) {
			return name;
		}
		String postfix = " (C" + (channel + 1) + ")";
		if (channelName.contains(name) || channelName.endsWith(postfix))
			return channelName;
		return channelName + postfix;		
	}


	void setLUTColor(int rgb) {
		setLUTColor(
				ColorTools.red(rgb),
				ColorTools.green(rgb),
				ColorTools.blue(rgb));
	}

	/**
	 * Set the 'maximum' color, which defines the lookup table to use.
	 * @param r red component (0-255)
	 * @param g green component (0-255)
	 * @param b blue component (0-255)
	 */
	public void setLUTColor(int r, int g, int b) {
		// Create a LUT
		rgbLUT = new int[256];
		byte[] rb = new byte[256];
		byte[] gb = new byte[256];
		byte[] bb = new byte[256];
		for (int i = 0; i < 256; i++) {
			rgbLUT[i] = ColorTools.packRGB(
					ColorTools.do8BitRangeCheck(r / 255.0 * i),
					ColorTools.do8BitRangeCheck(g / 255.0 * i),
					ColorTools.do8BitRangeCheck(b / 255.0 * i)
					);
			rb[i] = (byte)ColorTools.do8BitRangeCheck(r / 255.0 * i);
			gb[i] = (byte)ColorTools.do8BitRangeCheck(g / 255.0 * i);
			bb[i] = (byte)ColorTools.do8BitRangeCheck(b / 255.0 * i);
		}

		cm = new IndexColorModel(8, 256, rb, gb, bb);

		this.rgb = ColorTools.packRGB(r, g, b);
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
		return ColorTransformer.makeScaledRGBwithRangeCheck(value, minDisplay, 255.f/(maxDisplay - minDisplay), useColorLUT ? cm : null);
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

	@Override
	public boolean isMutable() {
		return false;
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