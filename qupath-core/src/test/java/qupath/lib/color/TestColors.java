/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;

@SuppressWarnings("javadoc")
public class TestColors {
	private static final double EPSILON = 1e-15; // smaller error
	private static final double EPSILON2 = 0.01; // bigger error allowed
//	private static final int MIN_RGB = 0;
	private static final int MAX_RGB = 255;
	
	@Test
	public void test_ColorDeconvMatrix3x3() {
		double[][] mat = new double[][] { 	{1, 2, 3},
											{0, 1, 4},
											{5, 6, 0} 	};

		assertEquals(mat[0][0],		1, 	EPSILON);
		assertEquals(mat[0][1], 	2, 	EPSILON);
		assertEquals(mat[0][2], 	3,	EPSILON);
		assertEquals(mat[1][0], 	0, 	EPSILON);
		assertEquals(mat[1][1], 	1, 	EPSILON);
		assertEquals(mat[1][2], 	4, 	EPSILON);
		assertEquals(mat[2][0], 	5, 	EPSILON);
		assertEquals(mat[2][1], 	6,	EPSILON);
		assertEquals(mat[2][2], 	0,	EPSILON);
											
		ColorDeconvMatrix3x3 cd = new ColorDeconvMatrix3x3(mat);
		
		double[][] cdi = cd.inverse();
		
		assertEquals(cdi[0][0],		-24, 	EPSILON);
		assertEquals(cdi[0][1], 	18, 	EPSILON);
		assertEquals(cdi[0][2], 	5, 		EPSILON);
		assertEquals(cdi[1][0], 	20, 	EPSILON);
		assertEquals(cdi[1][1], 	-15, 	EPSILON);
		assertEquals(cdi[1][2], 	-4, 	EPSILON);
		assertEquals(cdi[2][0], 	-5, 	EPSILON);
		assertEquals(cdi[2][1], 	4, 		EPSILON);
		assertEquals(cdi[2][2], 	1, 		EPSILON);
	}

	@Test
	public void test_ColorTransformer() {
		int rgb = 66051; // rgb value for testing - equivalent to r=1, g=2, b=3 (alpha=0)
		
		int rgb_t = ColorTransformer.getODNormalizedColor(rgb, 0.1, 0, 1); // norm = 3.734603883825189; (ODs) 0.6443896743247339, 0.5637840719571628, 0.5166328172223132 => 164.3193669528071, 143.7649383490765, 131.7413683916899 => (dec) 91, 112, 124 => (hex) 5B, 70, 7C = (dec) 5992572 
		assertEquals(rgb_t, 5992572); 
		int rgb_t2 = ColorTransformer.getODNormalizedColor(rgb, 4, 0, 1); // 0x00ffffff = 16777215
		assertEquals(rgb_t2, 16777215);
		
		// Deconvolve has been tested below
		
	}
	
	@Test
	public void test_ColorDeconvHelper() throws Exception {
		double[] myODLUT = ColorDeconvolutionHelper.makeODLUT(100); // maxvalue = 100 for this test
		
		assertEquals(myODLUT.length, 256); // the created myODLUT goes from 0 to 255 by design (in class)
		
		// check on OutOfBounds error
		double val = ColorDeconvolutionHelper.makeODByLUT(256, myODLUT); // there was a bug here 
		assertEquals(val, Double.NaN, EPSILON);
		
		int rgb = 66051; // rgb value for testing - equivalent to r=1, g=2, b=3 (alpha=0)
		int[] rgb_buf_in = new int[]{ rgb };
		float[] rgb_buf_out = new float[rgb_buf_in.length];
		float[] rgb_red = new float[]{ ColorTransformer.getPixelValue(rgb, ColorTransformMethod.Red) };
		float[] rgb_green = new float[]{ ColorTransformer.getPixelValue(rgb, ColorTransformMethod.Green) };
		float[] rgb_blue = new float[]{ ColorTransformer.getPixelValue(rgb, ColorTransformMethod.Blue) };
		
		// Many of these functions are not directly used in CORE but rather in the GUI libraries
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities(rgb_red, MAX_RGB, true); // OD(r) = OD(1) = 2.40654018043
		assertEquals(rgb_red[0], 2.40654018043, EPSILON2);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities(rgb_green, MAX_RGB, true); // OD(r) = OD(1) = 2.10551018477
		assertEquals(rgb_green[0], 2.10551018477, EPSILON2);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities(rgb_blue, MAX_RGB, true); // OD(r) = OD(1) = 1.92941892571
		assertEquals(rgb_blue[0], 1.92941892571, EPSILON2);
	
		rgb_buf_out = ColorDeconvolutionHelper.getRedOpticalDensities(rgb_buf_in, MAX_RGB, null);
		assertEquals(rgb_buf_out[0], 2.40654018043, EPSILON2);
		rgb_buf_out = ColorDeconvolutionHelper.getGreenOpticalDensities(rgb_buf_in, MAX_RGB, null);
		assertEquals(rgb_buf_out[0], 2.10551018477, EPSILON2);
		rgb_buf_out = ColorDeconvolutionHelper.getBlueOpticalDensities(rgb_buf_in, MAX_RGB, null);
		assertEquals(rgb_buf_out[0], 1.92941892571, EPSILON2);
		
		// Checked visually median calculations - testing on input RGB matrix with 3 pixels [r g b]: [1 2 3; 3 1 2; 2 3 1]. Expected output: [0.577 0.577 0.577] 
		// Should median calculations not be created by using image histograms??? Just a suggestion...
		int rgb1 = ColorTools.makeRGB(1, 2, 3);
		int rgb2 = ColorTools.makeRGB(3, 1, 2);
		int rgb3 = ColorTools.makeRGB(2, 3, 1);
		int[] rgb_buf_med_in = new int[]{ rgb1, rgb2, rgb3 };
		StainVector median_SV = ColorDeconvolutionHelper.generateMedianStainVectorFromPixels("myMedian", rgb_buf_med_in, MAX_RGB, MAX_RGB, MAX_RGB);
		assertEquals(median_SV.getRed(), 0.577, EPSILON2);
		assertEquals(median_SV.getGreen(), 0.577, EPSILON2);
		assertEquals(median_SV.getBlue(), 0.577, EPSILON2);
		
		// Haven't tested refine vectors as it is stated it is meant for testing only
		
	}

	@Test
	public void test_ColorDeconvStains() throws Exception {
		StainVector mySV1 = StainVector.makeDefaultStainVector(StainVector.DefaultStains.HEMATOXYLIN);
		StainVector mySV2 = StainVector.makeDefaultStainVector(StainVector.DefaultStains.EOSIN);
		StainVector mySV2b = StainVector.createStainVector("eosin", 0.07, 0.99, 0.11); // alternative eosin, taken from Ruifrok
		StainVector mySV3 = StainVector.makeDefaultStainVector(StainVector.DefaultStains.DAB);
		StainVector mySVr1 = StainVector.makeResidualStainVector(mySV1, mySV2);
		StainVector mySVr2 = StainVector.makeResidualStainVector(mySV1, mySV3);
		
		ColorDeconvolutionStains myCDS1 = new ColorDeconvolutionStains("myCDS1", mySV1, mySV2, 255, 255, 255); // residual created automatically
		ColorDeconvolutionStains myCDS2 = new ColorDeconvolutionStains("myCDS1", mySV1, mySV2, mySV3, 255, 255, 255); 
		
		assertTrue(myCDS1.isH_E());
		assertFalse(myCDS2.isH_E());
		
		assertTrue(myCDS1.getStain(3).isResidual());
		assertEquals(myCDS1.getStain(3).arrayAsString(Locale.US), mySVr1.arrayAsString(Locale.US)); // check automatic residual same as above
		//assertEquals(myCDS1.getStainNumber(mySV3r), 3); // will need to override 'equals' to pass this test
		assertFalse(myCDS2.getStain(3).isResidual());
		
		myCDS1 = myCDS1.changeStain(mySV3, 2);
		assertFalse(myCDS1.isH_E());
		assertTrue(myCDS1.isH_DAB());
		assertTrue(myCDS1.getStain(3).isResidual());
		assertEquals(myCDS1.getStain(3).arrayAsString(Locale.US), mySVr2.arrayAsString(Locale.US)); // check automatic residual same as above
	
		myCDS2 = myCDS2.changeStain(mySV2b, 2);

		// From Ruifrok paper
		assertEquals(myCDS2.getStain(1).getArray()[0],	0.65, 	EPSILON2);
		assertEquals(myCDS2.getStain(1).getArray()[1],	0.70, 	EPSILON2);
		assertEquals(myCDS2.getStain(1).getArray()[2],	0.29, 	EPSILON2);
		assertEquals(myCDS2.getStain(2).getArray()[0],	0.07, 	EPSILON2);
		assertEquals(myCDS2.getStain(2).getArray()[1],	0.99, 	EPSILON2);
		assertEquals(myCDS2.getStain(2).getArray()[2],	0.11, 	EPSILON2);
		assertEquals(myCDS2.getStain(3).getArray()[0],	0.27, 	EPSILON2);
		assertEquals(myCDS2.getStain(3).getArray()[1],	0.57, 	EPSILON2);
		assertEquals(myCDS2.getStain(3).getArray()[2],	0.78, 	EPSILON2);
		
		double [][] myMatInv = myCDS2.getMatrixInverse();
		
		// From Ruifrok paper (although in paper appears transposed!!!)
		assertEquals(myMatInv[0][0],	1.878, 	EPSILON2);
		assertEquals(myMatInv[0][1], 	-1.008, EPSILON2);
		assertEquals(myMatInv[0][2], 	-0.556,	EPSILON2);
		assertEquals(myMatInv[1][0], 	-0.066,	EPSILON2);
		assertEquals(myMatInv[1][1], 	1.135, 	EPSILON2);
		assertEquals(myMatInv[1][2], 	-0.136,	EPSILON2);
		assertEquals(myMatInv[2][0], 	-0.602,	EPSILON2);
		assertEquals(myMatInv[2][1], 	-0.480,	EPSILON2);
		assertEquals(myMatInv[2][2], 	1.574, 	EPSILON2);
	}
	
	@Test
	public void test_ColorDeconvolution() throws Exception {
		StainVector mySV1 = StainVector.makeDefaultStainVector(StainVector.DefaultStains.HEMATOXYLIN);
		StainVector mySV2 = StainVector.makeDefaultStainVector(StainVector.DefaultStains.EOSIN);
		StainVector mySV2b = StainVector.createStainVector("eosin", 0.07, 0.99, 0.11); // alternative eosin, taken from Ruifrok
		StainVector mySV3 = StainVector.makeDefaultStainVector(StainVector.DefaultStains.DAB);
		
		ColorDeconvolutionStains myCDS2 = new ColorDeconvolutionStains("myCDS1", mySV1, mySV2, mySV3, MAX_RGB, MAX_RGB, MAX_RGB); 
		
		myCDS2 = myCDS2.changeStain(mySV2b, 2);
		double [][] myMatInv = myCDS2.getMatrixInverse();
		
		int rgb = 66051; // pixel to deconvolve - 66051 is equivalent to r=1, g=2, b=3 (alpha=0)
		
		double r = (rgb & 0xff0000) >> 16;
		double g = (rgb & 0xff00) >> 8;
		double b = rgb & 0xff;
		
		assertEquals(r, 1, EPSILON);
		assertEquals(g, 2, EPSILON);
		assertEquals(b, 3, EPSILON);
		
		assertEquals(myCDS2.getMaxRed(), 255, EPSILON); // all max values = 255
		assertEquals(myCDS2.getMaxGreen(), 255, EPSILON);
		assertEquals(myCDS2.getMaxBlue(), 255, EPSILON);
		
		double odr = ColorDeconvolutionHelper.makeOD((rgb & 0xff0000) >> 16, myCDS2.getMaxRed()); // manual value = 2.40654018043
		assertEquals(odr, 2.40654018043, EPSILON2);		
		double odg = ColorDeconvolutionHelper.makeOD((rgb & 0xff00) >> 8, myCDS2.getMaxGreen()); // manual value = 2.10551018477
		assertEquals(odg, 2.10551018477, EPSILON2);		
		double odb = ColorDeconvolutionHelper.makeOD((rgb & 0xff), myCDS2.getMaxBlue()); // manual value = 1.92941892571
		assertEquals(odb, 1.92941892571, EPSILON2);		
		
		double deconv_pixel_ch0 = odr * myMatInv[0][0] + odg * myMatInv[1][0] + odb * myMatInv[2][0]; // selected channel = 0 
		double deconv_pixel_ch1 = odr * myMatInv[0][1] + odg * myMatInv[1][1] + odb * myMatInv[2][1]; // selected channel = 1
		double deconv_pixel_ch2 = odr * myMatInv[0][2] + odg * myMatInv[1][2] + odb * myMatInv[2][2]; // selected channel = 2
		
		// Quick check on deconvolve from ColorTransformer (here it is easier as I have already the values loaded)
		assertEquals(deconv_pixel_ch0, ColorTransformer.deconvolve(rgb, myMatInv, ColorDeconvolutionHelper.makeODLUT(MAX_RGB), 1), EPSILON2);
		assertEquals(deconv_pixel_ch1, ColorTransformer.deconvolve(rgb, myMatInv, ColorDeconvolutionHelper.makeODLUT(MAX_RGB), 2), EPSILON2);
		assertEquals(deconv_pixel_ch2, ColorTransformer.deconvolve(rgb, myMatInv, ColorDeconvolutionHelper.makeODLUT(MAX_RGB), 3), EPSILON2);

//		// Continue testing ColorDeconvolution
//		assertEquals((float)deconv_pixel_ch0, ColorDeconvolution.colorDeconvolveRGBPixel(rgb, myCDS2, 0), EPSILON);
//		assertEquals((float)deconv_pixel_ch1, ColorDeconvolution.colorDeconvolveRGBPixel(rgb, myCDS2, 1), EPSILON);
//		assertEquals((float)deconv_pixel_ch2, ColorDeconvolution.colorDeconvolveRGBPixel(rgb, myCDS2, 2), EPSILON);
		
		int[] buf = new int[]{ rgb, rgb, rgb, rgb, rgb, rgb, rgb, rgb, rgb, rgb }; // array of 10 rgb pixels with the same value
		float[] output_ch0 = new float[buf.length];
		float[] output_ch1 = new float[buf.length];
		float[] output_ch2 = new float[buf.length];
		
		output_ch0 = ColorTransformer.getTransformedPixels(buf, ColorTransformer.ColorTransformMethod.Stain_1, output_ch0, myCDS2);
		output_ch1 = ColorTransformer.getTransformedPixels(buf, ColorTransformer.ColorTransformMethod.Stain_2, output_ch1, myCDS2);
		output_ch2 = ColorTransformer.getTransformedPixels(buf, ColorTransformer.ColorTransformMethod.Stain_3, output_ch2, myCDS2);
//		output_ch1 = ColorDeconvolution.colorDeconvolveRGBArray(buf, myCDS2, 1, output_ch1);
//		output_ch2 = ColorDeconvolution.colorDeconvolveRGBArray(buf, myCDS2, 2, output_ch2);
		
		// Check all 3 channels (stains)
		for (int i = 0; i < buf.length; i++) {
			assertEquals(output_ch0[i], (float)deconv_pixel_ch0, EPSILON);
			assertEquals(output_ch1[i], (float)deconv_pixel_ch1, EPSILON);
			assertEquals(output_ch2[i], (float)deconv_pixel_ch2, EPSILON);
		}
		
//		int[] buf_output = new int[buf.length];
		
//		buf_output = ColorDeconvolution.colorDeconvolveReconvolveRGBArray(buf, myCDS2, myCDS2, false, buf_output);
		
//		for (int i = 0; i < buf.length; i++)
//			assertEquals(buf_output[i], buf[i], EPSILON);
		
		// Check functions don't break with extreme RGB values (no exceptions)
//		ColorDeconvolution.colorDeconvolveRGBPixel(MIN_RGB, myCDS2, 0);
//		ColorDeconvolution.colorDeconvolveRGBPixel(MAX_RGB, myCDS2, 0);
//		ColorDeconvolution.colorDeconvolveReconvolveRGBArray(new int[]{MIN_RGB, MAX_RGB}, myCDS2, myCDS2, false, buf_output);
		
	}
	
}

