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

package qupath.imagej.processing;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;

import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

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

	/**
	 * Generate a QuPath ROI by thresholding an image channel image.
	 * 
	 * @param img the input image (any type)
	 * @param minThreshold minimum threshold; pixels &gt;= minThreshold will be included
	 * @param maxThreshold maximum threshold; pixels &lt;= maxThreshold will be included
	 * @param band the image band to threshold (channel)
	 * @param request a {@link RegionRequest} corresponding to this image, used to calibrate the coordinates.  If null, 
	 * 			we assume no downsampling and an origin at (0,0).
	 * @return
	 * 
	 * @see #thresholdToROI(ImageProcessor, TileRequest)
	 */
	public static ROI thresholdToROI(BufferedImage img, double minThreshold, double maxThreshold, int band, RegionRequest request) {
		int w = img.getWidth();
		int h = img.getHeight();
		float[] pixels = new float[w * h];
		img.getRaster().getSamples(0, 0, w, h, band, pixels);
		var fp = new FloatProcessor(w, h, pixels);
		
		fp.setThreshold(minThreshold, maxThreshold, ImageProcessor.NO_LUT_UPDATE);
		return thresholdToROI(fp, request);
	}

	/**
	 * Generate a QuPath ROI by thresholding an image channel image, deriving coordinates from a TileRequest.
	 * <p>
	 * This can give a more accurate result than depending on a RegionRequest because it is possible to avoid some loss of precision.
	 * 
	 * @param raster
	 * @param minThreshold
	 * @param maxThreshold
	 * @param band
	 * @param request
	 * @return
	 * 
	 * @see #thresholdToROI(ImageProcessor, RegionRequest)
	 */
	public static ROI thresholdToROI(Raster raster, double minThreshold, double maxThreshold, int band, TileRequest request) {
		int w = raster.getWidth();
		int h = raster.getHeight();
		float[] pixels = new float[w * h];
		raster.getSamples(0, 0, w, h, band, pixels);
		var fp = new FloatProcessor(w, h, pixels);
		
		fp.setThreshold(minThreshold, maxThreshold, ImageProcessor.NO_LUT_UPDATE);
		return thresholdToROI(fp, request);
	}

	/**
	 * Generate a QuPath ROI from an ImageProcessor.
	 * <p>
	 * It is assumed that the ImageProcessor has had its min and max threshold values set.
	 * 
	 * @param ip
	 * @param request
	 * @return
	 */
	public static ROI thresholdToROI(ImageProcessor ip, RegionRequest request) {
		// Need to check we have any above-threshold pixels at all
		int n = ip.getWidth() * ip.getHeight();
		boolean noPixels = true;
		double min = ip.getMinThreshold();
		double max = ip.getMaxThreshold();
		for (int i = 0; i < n; i++) {
			double val = ip.getf(i);
			if (val >= min && val <= max) {
				noPixels = false;
				break;
			}
		}
		if (noPixels)
			return null;
		    	
		// Generate a shape, using the RegionRequest if we can
		var roiIJ = new ThresholdToSelection().convert(ip);
		if (request == null)
			return IJTools.convertToROI(roiIJ, 0, 0, 1, ImagePlane.getDefaultPlane());
		return IJTools.convertToROI(
				roiIJ,
				-request.getX()/request.getDownsample(),
				-request.getY()/request.getDownsample(),
				request.getDownsample(), request.getPlane());
	}

	static ROI thresholdToROI(ImageProcessor ip, TileRequest request) {
		// Need to check we have any above-threshold pixels at all
		int n = ip.getWidth() * ip.getHeight();
		boolean noPixels = true;
		double min = ip.getMinThreshold();
		double max = ip.getMaxThreshold();
		for (int i = 0; i < n; i++) {
			double val = ip.getf(i);
			if (val >= min && val <= max) {
				noPixels = false;
				break;
			}
		}
		if (noPixels)
			return null;
		    	
		// Generate a shape, using the TileRequest if we can
		var roiIJ = new ThresholdToSelection().convert(ip);
		if (request == null)
			return IJTools.convertToROI(roiIJ, 0, 0, 1, ImagePlane.getDefaultPlane());
		return IJTools.convertToROI(
				roiIJ,
				-request.getTileX(),
				-request.getTileY(),
				request.getDownsample(), request.getPlane());
	}
	
}
