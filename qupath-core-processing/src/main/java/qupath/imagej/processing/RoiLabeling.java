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
import ij.plugin.filter.RankFilters;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatPolygon;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

/**
 * Collection of static methods to help work with ROIs, binary &amp; labelled images in ImageJ.
 * <p>
 * This enables switching between different methods of representing regions as required.
 * 
 * @author Pete Bankhead
 *
 */
public class RoiLabeling {
	
	static Logger logger = LoggerFactory.getLogger(RoiLabeling.class);
	
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
	 * Experimental 8-connected ROI creation; non-zero pixels considered within objects
	 * <p>
	 * TODO: Improve experimental getFillPolygonROIs!!!  Consider efficiency (i.e. no min filter) &amp; standard ImageJ compatibility
	 * 			Using standard ImageJ draw/fill, the original binary image is NOT reconstructed.
	 * 
	 * @param ip
	 * @return
	 */
	public static List<PolygonRoi> getFilledPolygonROIsExperimental(ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();

		List<PolygonRoi> rois = new ArrayList<>();
		ByteProcessor bpCompleted = new ByteProcessor(w, h);
		bpCompleted.copyBits(ip, 0, 0, Blitter.COPY);
		new RankFilters().rank(bpCompleted, 0.5, RankFilters.MIN);
		for (int i = 0; i < w*h; i++) {
			if (ip.getf(i) != 0 && bpCompleted.getf(i) == 0)
				bpCompleted.setf(i, 1);
			else
				bpCompleted.setf(i, 0);
		}
//		new ImagePlus("Completed", bpCompleted.duplicate()).show();
		bpCompleted.setValue(255);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (ip.get(x, y) == 0)
					continue;
				if (bpCompleted.getf(x, y) > 1)
					continue;

				// Begin tracing contour
				FloatPolygon poly = new FloatPolygon();
				int xx = x;
				int yy = y;
				boolean angleChanged = true;
				int lastAngle = -1;
				while (true) {
					// If the angle is unchanged from that of the previously-added point, simplify the polygon by updating the last point
//					if (angleChanged)
//						poly.addPoint(xx, yy);
//					else {
//						poly.xpoints[poly.npoints-1] = xx;
//						poly.ypoints[poly.npoints-1] = yy;
//					}
					if (angleChanged)
						poly.addPoint(xx+.5, yy+.5);
					else {
						poly.xpoints[poly.npoints-1] = xx+.5f;
						poly.ypoints[poly.npoints-1] = yy+.5f;
					}
					
					bpCompleted.setf(xx, yy, 255f);
					// Trace clockwise
					if (yy-1 >= 0 && bpCompleted.getf(xx, yy-1) == 1) {
						yy -= 1;
						angleChanged = lastAngle != 1;
						lastAngle = 1;
						continue;
					}
					if (yy-1 >= 0 && xx+1 < w && bpCompleted.getf(xx+1, yy-1) == 1) {
						yy -= 1;
						xx += 1;
						angleChanged = lastAngle != 2;
						lastAngle = 2;
						continue;
					}
					if (xx+1 < w && bpCompleted.getf(xx+1, yy) == 1) {
						xx += 1;
						angleChanged = lastAngle != 3;
						lastAngle = 3;
						continue;
					}
					if (yy+1 < h) {
						if (xx+1 < w && bpCompleted.getf(xx+1, yy+1) == 1) {
							yy += 1;
							xx += 1;
							angleChanged = lastAngle != 4;
							lastAngle = 4;
							continue;
						}
						if (bpCompleted.getf(xx, yy+1) == 1) {
							yy += 1;
							angleChanged = lastAngle != 1;
							lastAngle = 1;
							continue;
						}
						if (xx-1 < w && bpCompleted.getf(xx-1, yy+1) == 1) {
							yy += 1;
							xx -= 1;
							angleChanged = lastAngle != 2;
							lastAngle = 2;
							continue;
						}
					}
					if (xx-1 >= 0) {
						if (bpCompleted.getf(xx-1, yy) == 1) {
							xx -= 1;
							angleChanged = lastAngle != 3;
							lastAngle = 3;
							continue;
						}
						if (yy-1 >= 0 && bpCompleted.getf(xx-1, yy-1) == 1) {
							yy -= 1;
							xx -= 1;
							angleChanged = lastAngle != 4;
							lastAngle = 4;
							continue;
						}
					}
					break;
				}
				PolygonRoi roi = new PolygonRoi(poly, Roi.POLYGON);
				bpCompleted.fill(roi);
				if (poly.npoints > 2)
					rois.add(roi);
			}
		}
		return rois;
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
	 * Create ROIs from labels in an image.
	 * <p>
	 * This differs from labelsToConnectedROIs in that the ROIs created may be
	 * disconnected and contain holes.
	 * 
	 * @param ipLabels
	 * @param n
	 * @return
	 */
	public static Roi[] labelsToConnectedROIs(ImageProcessor ipLabels, int n) {
		Roi[] rois = new Roi[n];
		int w = ipLabels.getWidth();
		int h = ipLabels.getHeight();
		ByteProcessor bpCompleted = new ByteProcessor(w, h);
		bpCompleted.setValue(255);
		ThresholdToSelection tts = new ThresholdToSelection();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (bpCompleted.get(x, y) != 0)
					continue;
				float val = ipLabels.getf(x, y);
				if (val > 0 && val <= n) {
					Wand wand = new Wand(ipLabels);
					ipLabels.resetThreshold();
					wand.autoOutline(x, y, val, val, Wand.EIGHT_CONNECTED);
					Roi roi = wandToRoi(wand);
					
					// Check if ROI contains holes, and create if necessary
					ipLabels.setRoi(roi);
					ImageStatistics stats = ipLabels.getStatistics();
					if (stats.max != stats.min || rois[(int)val-1] != null) {
						ipLabels.setThreshold(val-0.25, val+0.25, ImageProcessor.NO_LUT_UPDATE);
						roi = tts.convert(ipLabels);
					}
					
					rois[(int)val-1] = roi;
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
	 */
	public static PolygonRoi wandToRoi(Wand wand) {
		// TODO: CONSIDER IF SHOULD BE TRACED OR POLYGON!
//		PolygonRoi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.POLYGON);
		
		PolygonRoi roi;
		
		// The Wand can have far too many points (1000, when fewer are needed) - so used trimmed arrays where this is the case
		if (wand.xpoints.length > wand.npoints * 1.25)
			roi = new PolygonRoi(Arrays.copyOf(wand.xpoints, wand.npoints), Arrays.copyOf(wand.ypoints, wand.npoints), wand.npoints, Roi.TRACED_ROI);
		else
			roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.TRACED_ROI);
		
		
		// TODO: Consider simplifying wand perimeters along diagonals
//		Polygon poly = new Polygon();
//		int lastAngle = -1;
//		boolean angleChanged = true;
//		int lastX = -1000;
//		int lastY = -1000;
//		for (int i = 0; i < wand.npoints; i++) {
//			int x = wand.xpoints[i];
//			int y = wand.ypoints[i];
//			
//			if (Math.abs(lastX - x) <= 1 && Math.abs(lastY - y) <= 1) {
//				
//			} else {
//				angleChanged = true;
//				lastAngle = -1;
//			}
//			
//			if (angleChanged)
//				poly.addPoint(x, y);
//			else {
//				poly.xpoints[poly.npoints-1] = x;
//				poly.ypoints[poly.npoints-1] = y;
//			}
//			lastX = x;
//			lastY = y;
//		}
		
		return roi;
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
		int w = ip.getWidth();
		int h = ip.getHeight();
		short shortMax = (short)65535;
		ImageProcessor sp = new ShortProcessor(w, h);
		short[] pxShort = (short[])sp.getPixels();
		for (int i = 0; i < w*h; i++) {
			if (ip.getf(i) > threshold)
				pxShort[i] = shortMax;
		}
		// Loop through and flood fill
		FloodFiller ff = new FloodFiller(sp);
		double label = 0;
		double maxSupported = 65535;
		for (int i = 0; i < pxShort.length; i++) {
			if (pxShort[i] == shortMax) {
				label++;
				// We would overflow the max int value for a ShortProcessor - convert now to 32-bit
				if (label == maxSupported) {
					sp = sp.convertToFloatProcessor();
					ff = new FloodFiller(sp);
					maxSupported = -1;
				}
				sp.setValue(label);
				if (conn8)
					ff.fill8(i % w, i / w);
				else
					ff.fill(i % w, i / w);
			}
		}
		sp.setMinAndMax(0, label);
		return sp;
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
	
	
	
//	private static final boolean binaryOn(ByteProcessor bp, int x, int y) {
//		return bp.get(x, y) != 0;
//	}
//	
//	
//	public static ByteProcessor removeSpurs(ByteProcessor bp, ByteProcessor bpOutput) {
//		int w = bp.getWidth();
//		int h = bp.getHeight();
//		if (bpOutput == null)
//			bpOutput = new ByteProcessor(w, h);
//		int[] xOffsets = new int[]{-1, 0, 1, 1, 1, 0, -1, -1};
//		int[] yOffsets = new int[]{-1, -1, -1, 0, 1, 1, 1, 0};
//		for (int y = 0; y < h; y++) {
//			for (int x = 0; x < w; x++) {
//				boolean val = binaryOn(bp, x, y);
//				if (!val)
//					continue;
//				// Loop around the central pixel & count the 'on' neighbors
//				// Remove pixel if it only has one group of continuous 'on' neighbors
//				int count = 0;
//				boolean lastOn = x-1 >= 0 && binaryOn(bp, x-1, y);
//				for (int i = 0; i < xOffsets.length; i++) {
//					int xx = x + xOffsets[i];
//					int yy = y + yOffsets[i];
//					// Check if we are outside the image
//					if (xx < 0 || xx >= w || yy < 0 || yy >= h) {
//						lastOn = false;
//					} else {
//						if (binaryOn(bp, xx, yy)) {
//							if (!lastOn) {
//								count++;
//								lastOn = true;
//							}
//						} else
//							lastOn = false;
//					}
//				}
//				if (count > 1)
//					bpOutput.set(x, y, (byte)255);
//				else
//					bpOutput.set(x, y, 0);
////				if (count <= 1)
////					bp.set(x, y, 0);
//			}
//		}
//		return bpOutput;
//	}


	/**
	 * Fill in a region outside a specified ROI
	 * 
	 * @param ip
	 * @param roi
	 */
	public static void fillOutside(ImageProcessor ip, Roi roi, double value) {
		// Check we don't have a full rectangle
		if (roi.getType() == Roi.RECTANGLE && roi.getBounds().equals(new Rectangle(0, 0, ip.getWidth(), ip.getHeight())))
			return;
//		if (roi instanceof ShapeRoi) {
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
//		} else {
//			ip.setValue(value);
//			ip.fillOutside(roi);
//		}
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
	
	
	
	/**
	 * Starting from all white pixels (value = 255) on a ROI's boundary,
	 * fill the pixels with black
	 * 
	 * @param bp
	 * @param roi
	 * @param clearValue
	 */
	public static void clearBoundary(ByteProcessor bp, Roi roi, double clearValue) {
		ByteProcessor bpEdgeMask = new ByteProcessor(bp.getWidth(), bp.getHeight());
		bpEdgeMask.setValue(255);
		if (roi == null)
			bpEdgeMask.fill();
		else {
			bpEdgeMask.fill(roi);
		}
		bpEdgeMask.filter(ByteProcessor.MIN);
		bpEdgeMask.invert();
		bpEdgeMask.copyBits(bp, 0, 0, Blitter.AND);
		bpEdgeMask = MorphologicalReconstruction.binaryReconstruction(bpEdgeMask, bp, false);
		bp.copyBits(bpEdgeMask, 0, 0, Blitter.SUBTRACT);
//
//		ImagePlus imp = new ImagePlus("Edge", bp.duplicate());
//		imp.setRoi(roi);
//		imp.show();

//		ByteProcessor bpEdgeMask = new ByteProcessor(bp.getWidth(), bp.getHeight());
//		bpEdgeMask.setValue(255);
//		bpEdgeMask.setLineWidth(2);
//		if (roi == null)
//			bpEdgeMask.draw(new Roi(0, 0, bp.getWidth(), bp.getHeight()));
//		else
//			bpEdgeMask.draw(roi);
//		bpEdgeMask.copyBits(bp, 0, 0, Blitter.AND);
//		bpEdgeMask = MorphologicalReconstruction.binaryReconstruction(bpEdgeMask, bp, false);
//		bp.copyBits(bpEdgeMask, 0, 0, Blitter.SUBTRACT);
//		new ImagePlus("Edge", bp.duplicate()).show();
	}
	
	
	
//	public static void removeSpurs(ByteProcessor bp) {
//		int w = bp.getWidth();
//		int h = bp.getHeight();
//		for (int y = 0; y < h; y++) {
//			for (int x = 0; x < w; x++) {
//				boolean val = binaryOn(bp, x, y);
//				if (!val)
//					continue;
//				// Count the number of 'on' neighbors, and set pixel to zero if < 2 (including pixel itself)
//				int count = 0;
//				for (int yy = Math.max(y-1, 0); yy <= Math.min(y+1, h-1); yy++) {
//					for (int xx = Math.max(x-1, 0); xx <= Math.min(x+1, w-1); xx++) {
//						if (binaryOn(bp, xx, yy)) {
//							count++;
//							if (count > 2)
//								break;
//						}
//					}
//				}
//				if (count <= 2)
//					bp.set(x, y, 0);
//			}
//		}
//	}

}
