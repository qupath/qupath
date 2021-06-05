/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.awt.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.util.Random;
import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestBufferedImageTools {
	
	private static BufferedImage testMask1;
	private static final int[] testArray1 = new int[250*250];
	
	private static BufferedImage testMask2;
	private static final int[] testArray2 = new int[12*12];
	
	private static BufferedImage testMask3;
	private static final int[] testArray3 = new int[8*8];
	
	private static BufferedImage RGBImage;
	
	@BeforeAll
	public static void init() {
		// First mask
		testMask1 = new BufferedImage(250, 250, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g2d = testMask1.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(10, 50, 25, 25);
		g2d.dispose();
		testMask1.getRaster().getPixels(0, 0, 250, 250, testArray1);

		// Second (downsample = 2.0) mask
		testMask2 = new BufferedImage(12, 12, BufferedImage.TYPE_BYTE_GRAY);
		g2d = testMask2.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, 12, 12);
		g2d.dispose();
		testMask2.getRaster().getPixels(0, 0, 12, 12, testArray2);
		
		// Third (downsample = 3.0) mask
		testMask3 = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
		g2d = testMask3.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, 8, 8);
		g2d.dispose();
		testMask3.getRaster().getPixels(0, 0, 8, 8, testArray3);
		
		// Random image
		RGBImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
	}
	
	
//	@Test
//	public void test_swapRedBlue() {
//		int[] types = new int[] {BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB
//				// These do work
////				BufferedImage.TYPE_INT_BGR, BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_4BYTE_ABGR,
//				// These don't work
////				BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_4BYTE_ABGR_PRE
//				};
//		var rand = new Random(100L);
//		int w = 100;
//		int h = 200;
//		for (int type : types) {
//			var img = new BufferedImage(w, h, type);
//			int[] rgb = rand.ints(w * h).toArray();
//			img.setRGB(0, 0, w, h, rgb, 0, w);
//			boolean hasAlpha = img.getAlphaRaster() != null;
//			BufferedImageTools.swapRedBlue(img);
//			int[] rgb2 = img.getRGB(0, 0, w, h, null, 0, w);
//			for (int i = 0; i < w*h; i++) {
//				int v1 = rgb[i];
//				int v2 = rgb2[i];
//				// Check that alpha is preserved, if available
//				if (hasAlpha)
//					assertEquals(ColorTools.alpha(v1), ColorTools.alpha(v2));
//				else
//					assertEquals(255, ColorTools.alpha(v2));
//				assertEquals(ColorTools.green(v1), ColorTools.green(v2));
//				assertEquals(ColorTools.red(v1), ColorTools.blue(v2));
//				assertEquals(ColorTools.blue(v1), ColorTools.red(v2));
//			}
//		}
//	}
	
	
	@Test
	public void test_swapRGBOrder() {
		int[] types = new int[] {BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB};
		var rand = new Random(100L);
		int w = 100;
		int h = 200;
		for (int type : types) {
			var img = new BufferedImage(w, h, type);
			int[] rgb = rand.ints(w * h).toArray();
			img.setRGB(0, 0, w, h, rgb, 0, w);
			boolean hasAlpha = img.getAlphaRaster() != null;
			// If we have alpha, need to mask it
			if (!hasAlpha)
				rgb = getRGB(img);
			
			// Conversion to RGB should have no change
			var imgRGB = BufferedImageTools.duplicate(img);
			assertArrayEquals(rgb, getOrderedPixels(imgRGB, "RGB"));
			
			// Conversion to RBG
			int[] rbg = getOrderedPixels(img, "RBG");
			for (int i = 0; i < w*h; i++) {
				int v1 = rgb[i];
				int v2 = rbg[i];
				assertEquals(ColorTools.alpha(v2), ColorTools.alpha(v2));
				assertEquals(ColorTools.red(v2), ColorTools.red(v1));
				assertEquals(ColorTools.green(v2), ColorTools.blue(v1));
				assertEquals(ColorTools.blue(v2), ColorTools.green(v1));
			}
			
			// Conversion to GRB
			int[] grb = getOrderedPixels(img, "GRB");
			for (int i = 0; i < w*h; i++) {
				int v1 = rgb[i];
				int v2 = grb[i];
				assertEquals(ColorTools.alpha(v2), ColorTools.alpha(v2));
				assertEquals(ColorTools.red(v2), ColorTools.green(v1));
				assertEquals(ColorTools.green(v2), ColorTools.red(v1));
				assertEquals(ColorTools.blue(v2), ColorTools.blue(v1));
			}
			
			// Conversion to GBR
			int[] gbr = getOrderedPixels(img, "GBR");
			for (int i = 0; i < w*h; i++) {
				int v1 = rgb[i];
				int v2 = gbr[i];
				assertEquals(ColorTools.alpha(v2), ColorTools.alpha(v2));
				assertEquals(ColorTools.red(v2), ColorTools.green(v1));
				assertEquals(ColorTools.green(v2), ColorTools.blue(v1));
				assertEquals(ColorTools.blue(v2), ColorTools.red(v1));
			}
			
			// Conversion to BRG
			int[] brg = getOrderedPixels(img, "BRG");
			for (int i = 0; i < w*h; i++) {
				int v1 = rgb[i];
				int v2 = brg[i];
				assertEquals(ColorTools.alpha(v2), ColorTools.alpha(v2));
				assertEquals(ColorTools.red(v2), ColorTools.blue(v1));
				assertEquals(ColorTools.green(v2), ColorTools.red(v1));
				assertEquals(ColorTools.blue(v2), ColorTools.green(v1));
			}
			
			// Conversion to BGR
			int[] bgr = getOrderedPixels(img, "BGR");
			for (int i = 0; i < w*h; i++) {
				int v1 = rgb[i];
				int v2 = bgr[i];
				assertEquals(ColorTools.alpha(v2), ColorTools.alpha(v2));
				assertEquals(ColorTools.red(v2), ColorTools.blue(v1));
				assertEquals(ColorTools.green(v2), ColorTools.green(v1));
				assertEquals(ColorTools.blue(v2), ColorTools.red(v1));
			}
			
			
		}
	}
	
	private static int[] getOrderedPixels(BufferedImage img, String order) {
		var img2 = BufferedImageTools.duplicate(img);
		BufferedImageTools.swapRGBOrder(img2, order);
		return getRGB(img2);
	}
	
	private static int[] getRGB(BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}
	
	

	@Test
	public void test_createROIMask() {
		// createROIMask(int, int, ROI, RegionRequest)
		var roiMask = BufferedImageTools.createROIMask(250, 250, ROIs.createRectangleROI(10, 50, 25, 25, ImagePlane.getDefaultPlane()), RegionRequest.createInstance("", 1.0, 0, 0, 250, 250));
		int[] maskArray = new int[250*250];
		roiMask.getRaster().getPixels(0, 0, 250, 250, maskArray);
		assertArrayEquals(testArray1, maskArray);
		
		// createROIMask(ROI, double)
		var roiMask2 = BufferedImageTools.createROIMask(ROIs.createRectangleROI(10, 50, 25, 25, ImagePlane.getDefaultPlane()), 2.0);
		int[] maskArray2 = new int[12*12];
		roiMask2.getRaster().getPixels(0, 0, 12, 12, maskArray2);
		assertArrayEquals(testArray2, maskArray2);
		
		// CreateROIMask(Shape, double)
		var roiMask3 = BufferedImageTools.createROIMask(new Rectangle2D.Double(10, 50, 25, 25), 3.0);
		int[] maskArray3 = new int[8*8];
		roiMask3.getRaster().getPixels(0, 0, 8, 8, maskArray3);
		assertArrayEquals(testArray3, maskArray3);
		
		// Check is8bit? method
		assertFalse(BufferedImageTools.is8bitColorType(testMask1.getType()));
		assertFalse(BufferedImageTools.is8bitColorType(testMask2.getType()));
		assertFalse(BufferedImageTools.is8bitColorType(testMask3.getType()));
		assertTrue(BufferedImageTools.is8bitColorType(RGBImage.getType()));
	}
	
	@Test
	public void test_ensureBufferedImageType() {
		// Not testing the actual values, since this is up to Graphics2D's drawImage()
		assertEquals(BufferedImage.TYPE_BYTE_GRAY, BufferedImageTools.ensureBufferedImageType(testMask1, BufferedImage.TYPE_BYTE_GRAY).getType());
		assertEquals(BufferedImage.TYPE_INT_RGB, BufferedImageTools.ensureBufferedImageType(testMask1, BufferedImage.TYPE_INT_RGB).getType());
		assertEquals(BufferedImage.TYPE_INT_BGR, BufferedImageTools.ensureBufferedImageType(testMask1, BufferedImage.TYPE_INT_BGR).getType());
		assertEquals(BufferedImage.TYPE_INT_ARGB, BufferedImageTools.ensureBufferedImageType(testMask1, BufferedImage.TYPE_INT_ARGB).getType());
	}
	
	@Test
	public void test_ensureBufferedImage() {
		Image img = new BufferedImage(250, 250, BufferedImage.TYPE_INT_RGB);
		assertEquals(img, BufferedImageTools.ensureBufferedImage(img));
		// TODO: Check with other types of images
		
	}
	
	@Test
	public void test_duplicate() {
		BufferedImage imgCopy = BufferedImageTools.duplicate(testMask1);
		int[] arrayCopy = new int[imgCopy.getWidth() * imgCopy.getWidth()];
		imgCopy.getRaster().getPixels(0, 0, imgCopy.getWidth(), imgCopy.getHeight(), arrayCopy);
		assertEquals(testMask1.getType(), imgCopy.getType());
		assertArrayEquals(testArray1, arrayCopy);

		BufferedImage imgCopy2 = BufferedImageTools.duplicate(testMask2);
		int[] arrayCopy2 = new int[imgCopy2.getWidth() * imgCopy2.getWidth()];
		imgCopy2.getRaster().getPixels(0, 0, imgCopy2.getWidth(), imgCopy2.getHeight(), arrayCopy2);
		assertEquals(testMask2.getType(), imgCopy2.getType());
		assertArrayEquals(testArray2, arrayCopy2);
		
		BufferedImage imgCopy3 = BufferedImageTools.duplicate(testMask3);
		int[] arrayCopy3 = new int[imgCopy3.getWidth() * imgCopy3.getWidth()];
		imgCopy3.getRaster().getPixels(0, 0, imgCopy3.getWidth(), imgCopy3.getHeight(), arrayCopy3);
		assertEquals(testMask3.getType(), imgCopy3.getType());
		assertArrayEquals(testArray3, arrayCopy3);
	}
	
	@Test
	public void test_setValues() {
		/** Byte **/
		// Normal value
		DataBuffer bufferByte = new DataBufferByte(64, 3);
		BufferedImageTools.setValues(bufferByte, 33_000);
		for (int i = 0; i < bufferByte.getSize(); i++) {
			for (int band = 0; band < bufferByte.getNumBanks(); band++) {
				assertEquals(33_000%256, bufferByte.getElem(i));				
			}
		}
		
		// Negative value
		BufferedImageTools.setValues(bufferByte, -64);
		for (int i = 0; i < bufferByte.getSize(); i++) {
			for (int band = 0; band < bufferByte.getNumBanks(); band++) {
				assertEquals(-64+256, bufferByte.getElem(band, i));
			}
		}
		
		/** Integer **/
		// Normal value
		DataBuffer bufferInt = new DataBufferInt(64, 6);
		BufferedImageTools.setValues(bufferInt, 33_000);
		for (int i = 0; i < bufferInt.getSize(); i++) {
			for (int band = 0; band < bufferInt.getNumBanks(); band++) {
				assertEquals(33_000, bufferInt.getElem(band, i));
			}
		}
		
		// Negative value
		BufferedImageTools.setValues(bufferInt, -5);
		for (int i = 0; i < bufferInt.getSize(); i++) {
			for (int band = 0; band < bufferInt.getNumBanks(); band++) {
				assertEquals(-5, bufferInt.getElem(band, i));
			}
		}
		
		/** Unsigned short **/
		// Normal value
		DataBuffer bufferUShort = new DataBufferUShort(64);
		BufferedImageTools.setValues(bufferUShort, 156);
		for (int i = 0; i < bufferUShort.getSize(); i++) {
			assertEquals(156, bufferUShort.getElem(i));
		}
		
		// Negative value
		BufferedImageTools.setValues(bufferUShort, -10);
		for (int i = 0; i < bufferUShort.getSize(); i++) {
			assertEquals(65536-10, bufferUShort.getElem(i));
		}
		
		/** Short **/
		// Normal value
		DataBuffer bufferShort = new DataBufferShort(64);
		BufferedImageTools.setValues(bufferShort, 156);
		for (int i = 0; i < bufferShort.getSize(); i++) {
			assertEquals(156, bufferShort.getElem(i));
		}
		
		// Negative value
		BufferedImageTools.setValues(bufferShort, -200);
		for (int i = 0; i < bufferShort.getSize(); i++) {
			assertEquals((short)-200, bufferShort.getElem(i));
		}
		
		// 33_000 > Short.MAX_VALUE
		BufferedImageTools.setValues(bufferShort, 33_000);
		for (int i = 0; i < bufferShort.getSize(); i++) {
			assertEquals((short)33_000, bufferShort.getElem(i));
		}
		
		/** Float **/
		// Normal value
		DataBuffer bufferFloat = new DataBufferFloat(64);
		BufferedImageTools.setValues(bufferFloat, 5.216);
		for (int i = 0; i < bufferFloat.getSize(); i++) {
			assertEquals((float)5.216, bufferFloat.getElemDouble(i), 0.0002);
		}
		
		// Negative value
		BufferedImageTools.setValues(bufferFloat, -5.216);
		for (int i = 0; i < bufferFloat.getSize(); i++) {
			assertEquals(-5.216, bufferFloat.getElemDouble(i), 0.0002);
		}
		
		/** Double **/
		// Normal value
		DataBuffer bufferDouble = new DataBufferDouble(64, 1);
		BufferedImageTools.setValues(bufferDouble, 5.216);
		for (int i = 0; i < bufferDouble.getSize(); i++) {
			assertEquals(5.216, bufferDouble.getElemDouble(i), 0.0002);
		}
		
		// Negative value
		BufferedImageTools.setValues(bufferDouble, -5.216);
		for (int i = 0; i < bufferDouble.getSize(); i++) {
			assertEquals(-5.216, bufferDouble.getElemDouble(i), 0.0002);
		}
	}
	
	@Test
	public void test_resize() {
		// Just checking the final size here, no pixel values
		var resizedImg1 = BufferedImageTools.resize(testMask1, 100, 150, false);
		assertEquals(100, resizedImg1.getWidth());
		assertEquals(150, resizedImg1.getHeight());
		
		var resizedImg2 = BufferedImageTools.resize(testMask2, 10, 15, false);
		assertEquals(10, resizedImg2.getWidth());
		assertEquals(15, resizedImg2.getHeight());
		
		var resizedImg3 = BufferedImageTools.resize(testMask3, 300, 480, true);
		assertEquals(300, resizedImg3.getWidth());
		assertEquals(480, resizedImg3.getHeight());
	}
	
	@Test
	public void test_unsignedIntHistogram() {
		Random random = new Random(0);
		long[] manualCount = new long[Short.MAX_VALUE*2 + 2];
		long[] counts = new long[Short.MAX_VALUE*2 + 2];
		var img = new BufferedImage(250, 250, BufferedImage.TYPE_USHORT_GRAY);
		var raster = img.getRaster();
		for (int i = 0; i < img.getWidth() * img.getHeight(); i++) {
			int value = random.nextInt(Short.MAX_VALUE*2);
			raster.getDataBuffer().setElem(i, value);
			if (testArray1[i] != 0)
				manualCount[value] += 1;
		}

		BufferedImageTools.computeUnsignedIntHistogram(img.getRaster(), counts, testMask1.getRaster());
		var hist1 = BufferedImageTools.computeUnsignedIntHistogram(img.getRaster(), null, testMask1.getRaster());
		assertArrayEquals(counts, hist1);
		assertArrayEquals(manualCount, hist1);
	}
	
	@Test
	public void test_computeArgMaxHistogram() {
		var img = new BufferedImage(250, 250, BufferedImage.TYPE_INT_RGB);
		int[] data = new SplittableRandom(0).ints(3*250*250, 0, 256).toArray();
		var raster = img.getRaster();
	    raster.setPixels(0, 0, 250, 250, data);
		int nBands = raster.getNumBands();
		
		long[] manualCount = new long[nBands];
		long[] manualCountNoMask = new long[nBands];		
		long[] counts = new long[nBands];
		int iterator1 = 0;
		int iterator2 = 0;
		for (int i = 0; i < img.getWidth() * img.getHeight(); i++) {
			int maxValue = -1;
			int index = -1;
			for (int band = 0; band < nBands; band++) {
				if (data[iterator2] > maxValue) {
					maxValue = data[iterator2];
					index = band;
				}
				iterator2++;
			}
			manualCountNoMask[index]++;
			
			if (testArray1[i] == 0) {
				iterator1 += 3;
				continue;
			}
			maxValue = -1;
			index = -1;
			for (int band = 0; band < nBands; band++) {
				if (data[iterator1] > maxValue) {
					maxValue = data[iterator1];
					index = band;
				}
				iterator1++;
			}
			manualCount[index]++;
		}
		
		BufferedImageTools.computeArgMaxHistogram(raster, counts, testMask1.getRaster());
		var hist1 = BufferedImageTools.computeArgMaxHistogram(raster, null, testMask1.getRaster());
		var hist2 = BufferedImageTools.computeArgMaxHistogram(raster, null, null);
		assertArrayEquals(counts, hist1);
		assertArrayEquals(manualCount, hist1);
		assertArrayEquals(manualCountNoMask, hist2);
	}
	
	@Test
	public void test_computeAboveThresholdCounts() {
		var img = new BufferedImage(250, 250, BufferedImage.TYPE_INT_RGB);
		int[] data = new SplittableRandom(0).ints(3*250*250, 0, 256).toArray();
		var raster = img.getRaster();
		raster.setPixels(0, 0, 250, 250, data);
		
		double[] thresholds = new SplittableRandom().doubles(10, 0, 256).toArray();
		for (double threshold: thresholds) {			
			for (int band = 0; band < raster.getNumBands(); band++) {
				// Count manually
				long count = 0L;
				long countNoMask = 0L;
				for (int x = 0; x < img.getWidth(); x++) {
					for (int y = 0; y < img.getHeight(); y++) {
						if (raster.getSampleDouble(x, y, band) > threshold) {
							countNoMask++;
							if (testMask1.getRaster().getSample(x, y, 0) == 0)
								continue;
							count++;
						}
					}
				}
				assertEquals(countNoMask, BufferedImageTools.computeAboveThresholdCounts(raster, band, threshold, null));			
				assertEquals(count, BufferedImageTools.computeAboveThresholdCounts(raster, band, threshold, testMask1.getRaster()));			
			}
		}
	}
}