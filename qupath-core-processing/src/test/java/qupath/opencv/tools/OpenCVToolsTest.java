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

package qupath.opencv.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.PointerScope;
import org.junit.jupiter.api.Test;

import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;

@SuppressWarnings("javadoc")
public class OpenCVToolsTest {

	/**
	 * Test creation of BufferedImages of different types, and conversions between BufferedImage, Mat and ImagePlus.
	 */
	@Test
	public void testImageConversions() {
		
		int width = 50;
		int height = 20;
		int nChannels = 4;
		int[] colors = IntStream.range(0, nChannels).map(i -> ImageChannel.getDefaultChannelColor(i)).toArray();
		var skipTypes = Arrays.asList(PixelType.INT8, PixelType.UINT32);
		var rand = new Random(100L);
		
		try (PointerScope scope = new PointerScope()) {
			
			for (var type : PixelType.values()) {
				if (skipTypes.contains(type))
					continue;
				
				double max = type.isFloatingPoint() ? 2 : Math.pow(2, type.getBitsPerPixel());
				double offset = type.isUnsignedInteger() ? 0 : -max / 2.0;
				
				var colorModel = ColorModelFactory.createColorModel(type, nChannels, false, colors);
				var raster = colorModel.createCompatibleWritableRaster(width, height);
				var buf = raster.getDataBuffer();
				int n = width * height;
				for (int b = 0; b < nChannels; b++) {
				    for (int i = 0; i < n; i++)
				        buf.setElemDouble(b, i, rand.nextDouble() * max + offset);
				}
				var img = new BufferedImage(colorModel, raster, false, null);
				
				// Convert to ImagePlus
				var imp = IJTools.convertToUncalibratedImagePlus(type.toString(), img);
				// Convert to Mat
				var mat = OpenCVTools.imageToMat(img);
				// Convert Mat to ImagePlus
				var imp2 = OpenCVTools.matToImagePlus(type.toString() + " from Mat", mat);
				
				// Check values
				float[] expected = null;
				for (int b = 0; b < nChannels; b++) {
					expected = raster.getSamples(0, 0, width, height, b, expected);
					float[] actual = (float[])imp.getStack().getProcessor(b+1).convertToFloatProcessor().getPixels();
					assertArrayEquals(expected, actual, 0.0001f);
					actual = (float[])imp2.getStack().getProcessor(b+1).convertToFloatProcessor().getPixels();
					assertArrayEquals(expected, actual, 0.0001f);
				}
				
			}
			
		}
	}

}