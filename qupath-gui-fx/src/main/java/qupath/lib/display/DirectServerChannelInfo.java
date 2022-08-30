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

	private transient ColorModel cm;
	private transient ColorModel cmInverted;
	private transient int[] rgbLUT;
	private int rgb;

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
	
	@Override
	protected ColorModel getColorModel(ChannelDisplayMode mode) {
		switch (mode) {
		case INVERTED_GRAYSCALE:
//			return CM_GRAYSCALE_INVERTED;
		case GRAYSCALE:
			return CM_GRAYSCALE;
		case INVERTED_COLOR:
			return cmInverted;
		case COLOR:
		default:
			return cm;
		}
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
		// For inverted
		byte[] rbi = new byte[256];
		byte[] gbi = new byte[256];
		byte[] bbi = new byte[256];
		for (int i = 0; i < 256; i++) {
			rgbLUT[i] = ColorTools.packRGB(
					ColorTools.do8BitRangeCheck(r / 255.0 * i),
					ColorTools.do8BitRangeCheck(g / 255.0 * i),
					ColorTools.do8BitRangeCheck(b / 255.0 * i)
					);
			rb[i] = (byte)ColorTools.do8BitRangeCheck(r / 255.0 * i);
			gb[i] = (byte)ColorTools.do8BitRangeCheck(g / 255.0 * i);
			bb[i] = (byte)ColorTools.do8BitRangeCheck(b / 255.0 * i);
			
			rbi[i] = (byte)ColorTools.do8BitRangeCheck((255 - r) / 255.0 * i);
			gbi[i] = (byte)ColorTools.do8BitRangeCheck((255 - g) / 255.0 * i);
			bbi[i] = (byte)ColorTools.do8BitRangeCheck((255 - b) / 255.0 * i);
		}

		cm = new IndexColorModel(8, 256, rb, gb, bb);
		cmInverted = new IndexColorModel(8, 256, rbi, gbi, bbi);

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


}