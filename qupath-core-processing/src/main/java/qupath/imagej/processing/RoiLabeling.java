/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2023 - 2024 QuPath developers, The University of Edinburgh
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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import qupath.lib.common.LogTools;

/**
 * Collection of static methods to help work with ROIs, binary &amp; labelled images in ImageJ.
 * <p>
 * This enables switching between different methods of representing regions as required.
 * <p>
 * Note that in v0.6.0 {@link IJProcessing} was introduced as the main class for working with Rois and labeled images.
 * 
 * @author Pete Bankhead
 *
 */
public class RoiLabeling {
	
	private static final Logger logger = LoggerFactory.getLogger(RoiLabeling.class);
	
	/**
	 * Create a binary image for pixels that have a higher value than their neighbors.
	 * Comparisons are made horizontally, vertically and diagonally. Pixels meeting the criterion 
	 * have the value 255, all others are 0.
	 * @param ip
	 * @return
	 */
	public static ByteProcessor findDirectionalMaxima(ImageProcessor ip) {
		ImageProcessor ip2 = ip.duplicate();
		ip2.invert();
		return findDirectionalMinima(ip2);
	}
	
	/**
	 * Create a binary image for pixels that have a lower value than their neighbors.
	 * Comparisons are made horizontally, vertically and diagonally. Pixels meeting the criterion 
	 * have the value 255, all others are 0.
	 * @param ip
	 * @return
	 */
	public static ByteProcessor findDirectionalMinima(ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		ByteProcessor bp = new ByteProcessor(w, h);
		for (int y = 1; y < h-1; y++) {
			for (int x = 1; x < w-1; x++) {
				// Get value
				float val = ip.getf(x, y);
				// Check nonzero neighbours
				if ((ip.getf(x-1, y) > val && ip.getf(x+1, y) > val) ||
						(ip.getf(x-1, y-1) > val && ip.getf(x+1, y+1) > val) ||
						(ip.getf(x, y-1) > val && ip.getf(x, y+1) > val) ||
						(ip.getf(x-1, y+1) > val && ip.getf(x+1, y-1) > val)) {
					// If we have 2 zero neighbours, in any direction, break the connection
					bp.setf(x, y, 255);
				}
					
			}			
		}
		return bp;
	}

	
	/**
	 * Convert a labelled image into a list of PolygonRois by tracing.
	 * <p>
	 * Note that labels are assumed not to contain any holes or nested ROIs; ROIs produced by this command will not contain holes.
	 * <p>
	 * Some entries in the resulting array may be null if this is not the case, or if not all labels are found.
	 * Otherwise, pixels with the integer label L will belong to the Roi in the output array at entry L-1
	 * 
	 * @param ipLabels
	 * @param n maximum number of labels
	 * @return
	 */
	public static PolygonRoi[] labelsToFilledROIs(ImageProcessor ipLabels, int n) {
		PolygonRoi[] rois = new PolygonRoi[n];
		int w = ipLabels.getWidth();
		int h = ipLabels.getHeight();
		ByteProcessor bpCompleted = new ByteProcessor(w, h);
		bpCompleted.setValue(255);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (bpCompleted.get(x, y) != 0)
					continue;
				float val = ipLabels.getf(x, y);
				if (val > 0 && val <= n) {
					Wand wand = new Wand(ipLabels);
					wand.autoOutline(x, y, val, val, Wand.EIGHT_CONNECTED);
					PolygonRoi roi = wandToRoi(wand);
					rois[(int)val-1] = roi;
					bpCompleted.fill(roi);
				}
			}
		}
		return rois;
	}
	
	
	
	/**
	 * Convert a labelled image into a list of PolygonRois by tracing.
	 * <p>
	 * Unlike labelsToFilledROIs, the order in which ROIs are returned is arbitrary.
	 * <p>
	 * Also, the multiple Rois may be created for the same label, if unconnected regions are used.
	 * 
	 * @param ipLabels
	 * @param conn8
	 * @return
	 */
	public static List<PolygonRoi> labelsToFilledRoiList(final ImageProcessor ipLabels, final boolean conn8) {
		List<PolygonRoi> rois = new ArrayList<>();
		int w = ipLabels.getWidth();
		int h = ipLabels.getHeight();
		ByteProcessor bpCompleted = new ByteProcessor(w, h);
		bpCompleted.setValue(255);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (bpCompleted.get(x, y) != 0)
					continue;
				float val = ipLabels.getf(x, y);
				if (val > 0) {
					Wand wand = new Wand(ipLabels);
					wand.autoOutline(x, y, val, val, conn8 ? Wand.EIGHT_CONNECTED : Wand.FOUR_CONNECTED);
					PolygonRoi roi = wandToRoi(wand);
					rois.add(roi);
					bpCompleted.fill(roi);
				}
			}
		}
		return rois;
	}

	
	/**
	 * Get filled Polygon ROIs using distinct labels, creating a map between labels and ROIs.
	 * <p>
	 * Note that discontinuous ROIs are not supported; if labelled regions are discontinuous,
	 * then ROIs detected earlier will be discarded from the map.
	 * 
	 * @param ip
	 * @param wandMode
	 * @return
	 */
	public static Map<Float, PolygonRoi> getFilledPolygonROIsFromLabels(ImageProcessor ip, int wandMode) {
//		Wand wand = new Wand(ip);
		double threshLower = ip.getMinThreshold();
		if (threshLower == ImageProcessor.NO_THRESHOLD)
			threshLower = Double.NEGATIVE_INFINITY;
		double threshHigher = ip.getMaxThreshold();
		if (threshHigher == ImageProcessor.NO_THRESHOLD)
			threshHigher = Double.POSITIVE_INFINITY;
		int w = ip.getWidth();
		int h = ip.getHeight();
//		List<PolygonRoi> rois = new ArrayList<>();
		ByteProcessor bpCompleted = new ByteProcessor(w, h);
		bpCompleted.setValue(255);
		TreeMap<Float, PolygonRoi> map = new TreeMap<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (bpCompleted.get(x, y) != 0)
					continue;
				float val = ip.getf(x, y);
				if (val >= threshLower && val <= threshHigher) {
					Wand wand = new Wand(ip);
					wand.autoOutline(x, y, threshLower, threshHigher, wandMode);
					PolygonRoi roi = wandToRoi(wand);
//					rois.add(roi);
					Float key = val;
					if (map.containsKey(key))
						logger.warn("Polygon ROI is being inserted twice into map for the same key {}", key);
					map.put(val, roi);
					bpCompleted.fill(roi);
				}
			}
		}
		return map;
	}
	
	/**
	 * Get filled PolygonRois for connected pixels with the same value in an image.
	 * Because this uses ImageJ's Wand tool, holes will be filled.
	 * <p>
	 * Note that this command applies any thresholds that were set in the ImageProcessor, returning 
	 * only Rois for values within these limits. Therefore to identify only non-zero pixels in a labelled image
	 * you may need to first call {@code ip.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);}
	 * @param ip
	 * @param wandMode
	 * @return
	 */
	public static List<PolygonRoi> getFilledPolygonROIs(ImageProcessor ip, int wandMode) {
//		Wand wand = new Wand(ip);
		double threshLower = ip.getMinThreshold();
		if (threshLower == ImageProcessor.NO_THRESHOLD)
			threshLower = Double.NEGATIVE_INFINITY;
		double threshHigher = ip.getMaxThreshold();
		if (threshHigher == ImageProcessor.NO_THRESHOLD)
			threshHigher = Double.POSITIVE_INFINITY;
		int w = ip.getWidth();
		int h = ip.getHeight();
		List<PolygonRoi> rois = new ArrayList<>();
		ByteProcessor bpCompleted = new ByteProcessor(w, h);
		bpCompleted.setValue(255);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (bpCompleted.get(x, y) != 0)
					continue;
				float val = ip.getf(x, y);
				if (val >= threshLower && val <= threshHigher) {
					Wand wand = new Wand(ip);
					wand.autoOutline(x, y, threshLower, threshHigher, wandMode);
					PolygonRoi roi = wandToRoi(wand);
					rois.add(roi);
					bpCompleted.fill(roi);
				}
			}
		}
		return rois;
	}
	
	/**
	 * Convert a traced outline from the ImageJ Wand into a PolygonRoi.
	 * @param wand
	 * @return
	 * @deprecated since v0.6.0; use {@link IJProcessing#wandToRoi(Wand)} instead.
	 */
	@Deprecated
	public static PolygonRoi wandToRoi(Wand wand) {
		return IJProcessing.wandToRoi(wand);
	}
	
	/**
	 * Fill holes in a binary image.
	 * <p>
	 * Assumes 255 is foreground, 0 is background.
	 * <p>
	 * Based on code in ImageJ's Binary class.
	 * 
	 * @param bp
	 */
	public static void fillHoles(ByteProcessor bp) {
		int w = bp.getWidth();
		int h = bp.getHeight();
		FloodFiller ff = new FloodFiller(bp);
		bp.setValue(127);
		for (int x = 0; x < w; x++) {
			if (bp.getPixel(x, 0) == 0)
				ff.fill8(x, 0);
			if (bp.getPixel(x, h-1) == 0)
				ff.fill8(x, h-1);
		}
		for (int y = 0; y < h; y++) {
			if (bp.getPixel(0, y) == 0)
				ff.fill8(0, y);
			if (bp.getPixel(w-1, y) == 0)
				ff.fill8(w-1, y);
		}
		for (int i = 0; i < w*h; i++) {
			if (bp.get(i) == 127)
				bp.set(i, 0);
			else
				bp.set(i, 255);
		}
	}
	
	/**
	 * Label ROIs by filling each pixel with an integer value corresponding to the index of the Roi 
	 * in the list + 1.
	 * @param ipLabels
	 * @param rois
	 * @return
	 */
	public static ImageProcessor labelROIs(ImageProcessor ipLabels, List<? extends Roi> rois) {
		int label = 0;
		for (Roi r : rois) {
			label++;
			ipLabels.setValue(label);
			ipLabels.fill(r);
		}
		return ipLabels;
	}
	
	
	/**
	 * Create a labelled image from above-threshold pixels for an image.
	 * @param ip
	 * @param threshold
	 * @param conn8
	 * @return labelled image, as a ShortProcessor (if possible) or FloatProcessor (if necessary)
	 */
	public static ImageProcessor labelImage(ImageProcessor ip, float threshold, boolean conn8) {
		return IJProcessing.labelImage(ip, conn8 ? 8 : 4, d -> d > threshold);
	}

	/**
	 * Create ROIs from labels in an image.
	 *
	 * @param ipLabels the labeled image; generally this should be a ByteProcessor or ShortProcessor
	 * @param n the total number of labels; often this is equal to the maximum value in the image
	 * @return an array of length n; output[i] is the ROI for label i+1, or null if no Roi is found
	 *         with that label.
	 * @deprecated since v0.6.0; use {@link IJProcessing#labelsToRoisArray(ImageProcessor)} or
	 *             {@link IJProcessing#labelsToRois(ImageProcessor)} instead.
	 */
	@Deprecated
	public static Roi[] labelsToConnectedROIs(ImageProcessor ipLabels, int n) {
		LogTools.warnOnce(logger, "labelsToConnectedROIs is deprecated, use labelsToRoisArray instead");
		var array = IJProcessing.labelsToRoisArray(ipLabels);
		if (n == array.length)
			return array;
		else
			return Arrays.copyOf(array, n);
	}

	
	/**
	 * Remove objects having small areas, defined in terms of pixels
	 * 
	 * @param bp
	 * @param minPixels minimum number of pixels in an object that should be kept
	 * @param conn8
	 */
	public static void removeSmallAreas(ByteProcessor bp, double minPixels, boolean conn8) {
		removeByAreas(bp, minPixels, Double.POSITIVE_INFINITY, conn8);
	}
	
	
	/**
	 * Remove objects containing &lt; minPixels or &gt; maxPixels.
	 * 
	 * @param bp
	 * @param minPixels
	 * @param maxPixels
	 * @param conn8
	 * @return the number of connected objects remaining.
	 */
	public static int removeByAreas(final ByteProcessor bp, final double minPixels, final double maxPixels, final boolean conn8) {
		int w = bp.getWidth();
		int h = bp.getHeight();
		int shortMax = 65535;
		ImageProcessor ipLabels = labelImage(bp, 0.5f, conn8);
		// Loop through pixels & compute a histogram
		long[] histogram = new long[shortMax+1];
		for (int i = 0; i < w*h; i++) {
			int val = (int)ipLabels.getf(i);
			if (val != 0)
				histogram[val] += 1;
		}
		// Create a thresholded histogram
		boolean[] histThresholded = new boolean[histogram.length];
		int nObjects = 0;
		for (int i = 0; i < histThresholded.length; i++) {
			boolean keepObject = histogram[i] >= minPixels && histogram[i] <= maxPixels;
			if (keepObject) {
				histThresholded[i] = true;
				nObjects++;
			}
		}
		// Loop through and set to zero and below threshold objects
		for (int i = 0; i < w*h; i++) {
			int val = (int)ipLabels.getf(i);
			if (val != 0 && !histThresholded[val])
				bp.set(i, 0);
		}
		return nObjects;
	}
	

	/**
	 * Fill in a region outside a specified ROI
	 * 
	 * @param ip
	 * @param roi
	 * @param value 
	 */
	public static void fillOutside(ImageProcessor ip, Roi roi, double value) {
		// Check we don't have a full rectangle
		if (roi.getType() == Roi.RECTANGLE && roi.getBounds().equals(new Rectangle(0, 0, ip.getWidth(), ip.getHeight())))
			return;
		// Appears to be a bug with ShapeRois so that filling outside can fail using ImageJ's own code
		ByteProcessor bpMask = new ByteProcessor(ip.getWidth(), ip.getHeight());
		bpMask.setValue(1);
		bpMask.fill(roi);
		if (value == 0)
			ip.copyBits(bpMask, 0, 0, Blitter.MULTIPLY);
		else {
			float floatValue = (float)value;
			byte[] px = (byte[])bpMask.getPixels();
			for (int i = 0; i < px.length; i++) {
				if (px[i] == (byte)0)
					ip.setf(i, floatValue);
			}
		}
	}


	/**
	 * Clear (i.e. set pixels to zero) in a region outside a specified ROI
	 * 
	 * @param ip
	 * @param roi
	 */
	public static void clearOutside(ImageProcessor ip, Roi roi) {
		fillOutside(ip, roi, 0);
	}


}
