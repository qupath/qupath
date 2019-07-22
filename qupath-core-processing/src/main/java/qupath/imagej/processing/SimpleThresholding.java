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

package qupath.imagej.processing;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Collection of static methods to threshold images, either with single global thresholds or 
 * using the pixel values of a second image.
 * <p>
 * Output is a ByteProcessor where 255 represents 'positive' pixels, and zero represents the background.
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleThresholding {
	
	/**
	 * Created a binary image by thresholding pixels to find where ip1 &gt;= ip2
	 * @param ip1
	 * @param ip2
	 * @return
	 */
	public static ByteProcessor greaterThanOrEqual(ImageProcessor ip1, ImageProcessor ip2) {
		ByteProcessor bp =  new ByteProcessor(ip1.getWidth(), ip1.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip1.getf(i) >= ip2.getf(i))
				bpPixels[i] = (byte)255;
		}
		return bp;
	}
	
	/**
	 * Created a binary image by thresholding pixels to find where ip1 &gt; ip2
	 * @param ip1
	 * @param ip2
	 * @return
	 */
	public static ByteProcessor greaterThan(ImageProcessor ip1, ImageProcessor ip2) {
		ByteProcessor bp =  new ByteProcessor(ip1.getWidth(), ip1.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip1.getf(i) > ip2.getf(i))
				bpPixels[i] = (byte)255;
		}
		return bp;
	}

	/**
	 * Created a binary image by thresholding pixels to find where ip1 &lt; threshold
	 * @param ip
	 * @param threshold
	 * @return
	 */
	public static ByteProcessor thresholdBelow(ImageProcessor ip, float threshold) {
		ByteProcessor bp =  new ByteProcessor(ip.getWidth(), ip.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip.getf(i) < threshold)
				bpPixels[i] = (byte)255;
		}
		return bp;
	}
	
	/**
	 * Created a binary image by thresholding pixels to find where ip1 &lt;= threshold
	 * @param ip
	 * @param threshold
	 * @return
	 */
	public static ByteProcessor thresholdBelowEquals(ImageProcessor ip, float threshold) {
		ByteProcessor bp =  new ByteProcessor(ip.getWidth(), ip.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip.getf(i) <= threshold)
				bpPixels[i] = (byte)255;
		}
		return bp;
	}

	/**
	 * Created a binary image by identifying pixels where ip1 == ip2
	 * @param ip1
	 * @param ip2
	 * @return
	 */
	public static ByteProcessor imagesEqual(ImageProcessor ip1, ImageProcessor ip2) {
		ByteProcessor bp =  new ByteProcessor(ip1.getWidth(), ip1.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip1.getf(i) == ip2.getf(i))
				bpPixels[i] = (byte)255;
		}
		return bp;
	}
	
	/**
	 * Created a binary image by thresholding pixels to find where ip1 &gt; threshold
	 * @param ip
	 * @param threshold
	 * @return
	 */
	public static ByteProcessor thresholdAbove(ImageProcessor ip, float threshold) {
		ByteProcessor bp =  new ByteProcessor(ip.getWidth(), ip.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip.getf(i) > threshold)
				bpPixels[i] = (byte)255;
		}
		return bp;
	}
	
		/**
		 * Created a binary image by thresholding pixels to find where ip1 &gt;= threshold
		 * @param ip
		 * @param threshold
		 * @return
		 */	public static ByteProcessor thresholdAboveEquals(ImageProcessor ip, float threshold) {
		ByteProcessor bp =  new ByteProcessor(ip.getWidth(), ip.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			if (ip.getf(i) >= threshold)
				bpPixels[i] = (byte)255;
		}
		return bp;
	}

	/**
	 * Created a binary image by thresholding pixels to find where ip &gt;= lowThreshold and ip &lt;= highThreshold
	 * @param ip
	 * @param lowThreshold
	 * @param highThreshold
	 * @return
	 */
	public static ByteProcessor thresholdBetween(ImageProcessor ip, float lowThreshold, float highThreshold) {
		ByteProcessor bp =  new ByteProcessor(ip.getWidth(), ip.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		for (int i = 0; i < bpPixels.length; i++) {
			float val = ip.getf(i);
			if (val >= lowThreshold && val <= highThreshold)
				bpPixels[i] = (byte)255;
		}
		return bp;
	}
	
}
