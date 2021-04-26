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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
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
	
	
	@Test
	public void testContinuous() {
		try (var scope = new PointerScope()) {
			var mat = Mat.eye(9, 7, opencv_core.CV_32F).asMat();
			var mat2 = mat.col(5);
			assertTrue(mat.isContinuous());
			assertFalse(mat2.isContinuous());

			// While we have a non-continuous array, best check that buffers & indexers still behave as expected
			FloatIndexer idx = mat2.createIndexer();
			assertEquals(idx.size(0), 9);
			assertEquals(idx.size(1), 1);
			float[] pixels = OpenCVTools.extractPixels(mat2, null);
			
			// Warning! Buffer does *not* work here, since it refers to the whole Mat, 
			// with a limit of 63. This is one reason why it's important to use buffers 
			// only with continuous Mats...
//			FloatBuffer buffer = mat2.createBuffer();
			for (int i = 0; i < 9; i++) {
				float val = i == 5 ? 1f : 0f;
				assertEquals(idx.get(i, 0), val);
				assertEquals(pixels[i], val);
//				assertEquals(buffer.get(i), val);
			}
			
			var mat3 = OpenCVTools.ensureContinuous(mat2, false);
			assertTrue(mat3.isContinuous());
			assertFalse(mat2.isContinuous());
			
			OpenCVTools.ensureContinuous(mat2, true);
			assertTrue(mat2.isContinuous());
			assertEquals(mat2.rows(), 9);
			assertEquals(mat2.cols(), 1);
			
			// Extracting a row maintains continuous data
			var mat4 = mat.row(5);
			assertTrue(mat4.isContinuous());
			OpenCVTools.ensureContinuous(mat4, true);
			assertTrue(mat4.isContinuous());
		}
	}
	

}