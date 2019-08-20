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

package qupath.lib.analysis.features;

import java.io.IOException;
import org.junit.Test;

// TODO: Implement Haralick tests... this file is kept only to provide a starting point!
@SuppressWarnings("javadoc")
public class TestHaralick {
//	private final int PIXELS_SHORT_DIM = 4;
//	//private final double EPSILON = 1e-15;
	
	@Test
	public void test_values() {
//		final int NUM_GRAY_VALUES = 4; 
//		final int DISTANCE = 1;
//
//		float[] pixels_short = new float[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 2, 2, 2, 2, 2, 3, 3}; // from Haralick's paper
//		
//		FloatArraySimpleImage img = new FloatArraySimpleImage(pixels_short, PIXELS_SHORT_DIM, PIXELS_SHORT_DIM);
//		
//		// Check values for 0, 45, 90 and 135 matrices as compared to Haralick's paper - this has to be done with a breakpoint
//		HaralickFeatures hf = HaralickFeatureComputer.measureHaralick(img, null, NUM_GRAY_VALUES, 0, 3, DISTANCE);
		
	}
	
	@Test
	public void test_image1() throws IOException { 
//		final int NUM_GRAY_VALUES = 256; 
//		final int DISTANCE = 1;
//		final int IMAGE1_DIM = 170; // 170x170 gray image
//		BufferedImage img = null;
//		
//		assertNotNull("Test file missing", 
//	               getClass().getResource("/Image1.jpg"));
//		try{
//			img = ImageIO.read(getClass().getResourceAsStream("/Image1.jpg"));
//		} catch (IOException e) {  }
//		
//		float[] pixels = new float[IMAGE1_DIM * IMAGE1_DIM];
//				
//		pixels = img.getData().getPixels(0, 0, IMAGE1_DIM, IMAGE1_DIM, pixels);
//		
//		//List pixelsB = Arrays.asList(pixels);
//				
//		FloatArraySimpleImage newimg = new FloatArraySimpleImage(pixels, IMAGE1_DIM, IMAGE1_DIM);
//
//		Arrays.sort(pixels); // sorting for max and min
//		
//		HaralickFeatures hf = HaralickFeatureComputer.measureHaralick(newimg, null, NUM_GRAY_VALUES, pixels[0], pixels[pixels.length-1], DISTANCE);
//		
//		//FloatProcessor fp = new FloatProcessor(IMAGE1_DIM, IMAGE1_DIM, pixels);
//		//ByteProcessor bp = new ByteProcessor(fp, false);
//
//		//List<double[]> features = descriptor.getFeatures();
//		
//		// Can hard-code values taken from external source that we can trust (first argument)
//       	//assertEquals(features.get(0)[0], hf.getFeature(0), EPSILON); // f1
//       	//assertEquals(features.get(0)[1], hf.getFeature(1), EPSILON); // f2
//       	//assertEquals(features.get(0)[2], hf.getFeature(2), EPSILON); // f3
//       	//assertEquals(features.get(0)[3], hf.getFeature(3), EPSILON); // f4
//       	//assertEquals(features.get(0)[4], hf.getFeature(4), EPSILON); // f5
//       	//assertEquals(features.get(0)[5], hf.getFeature(5), EPSILON); // f6
//       	//assertEquals(features.get(0)[6], hf.getFeature(6), EPSILON); // f7
//       	//assertEquals(features.get(0)[7], hf.getFeature(7), EPSILON); // f8
//       	//assertEquals(features.get(0)[8], hf.getFeature(8), EPSILON); // f9
//       	//assertEquals(features.get(0)[9], hf.getFeature(9), EPSILON); // f10
//       	//assertEquals(features.get(0)[10], hf.getFeature(10), EPSILON); // f11
//       	//assertEquals(features.get(0)[11], hf.getFeature(11), EPSILON); // f12
//       	//assertEquals(features.get(0)[12], hf.getFeature(12), EPSILON); // f13
//       	//assertEquals(features.get(0)[13], hf.getFeature(13), EPSILON); // f14
        
	}
	
}
