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

package qupath.imagej.detect.dearray;

import java.awt.Polygon;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.RoiScaler;
import ij.plugin.filter.EDM;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.RankFilters;
import ij.process.AutoThresholder;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.processing.RoiLabeling;


/**
 * Static methods used by the TMA dearrayer plugin.
 * 
 * @author Pete Bankhead
 *
 */
public class TMADearrayer {

	
	static double[] computeDensities(ByteProcessor bp, Polygon polyGrid, double coreDiameterPx) {
		RankFilters rf = new RankFilters();
		FloatProcessor fp = bp.convertToFloatProcessor();
		fp.max(1.0);
		rf.rank(fp, coreDiameterPx*0.5, RankFilters.MEAN);
		double[] densities = new double[polyGrid.npoints];
		for (int i = 0; i < densities.length; i++) {
			int x = polyGrid.xpoints[i];
			int y = polyGrid.ypoints[i];
			if (x >= 0 && y >= 0 && x < bp.getWidth() && y < bp.getHeight())
				densities[i] = fp.getf(x, y);
		}
		return densities;
	}
	
	
	/**
	 * Detect TMA cores from a (low-resolution) whole slide image that has been thresholded to give a binary image.
	 * Cores should be circular and arranged in a grid pattern, although this may be (slightly) rotated
	 * and some cores may be missing (but at least a few should be present in all rows / columns of the grid).
	 * 
	 * @param bp - the TMA slide image, after thresholding.
	 * @param coreDiameterPx - approximate diameter of a single TMA core, in pixels
	 * @param nHorizontal - number of cores per row of the full grid
	 * @param nVertical - number of cores per column of the full grid
	 * @param roi - optional region of interest determining where cores should be found (may be useful if edge artifacts are problematic)
	 * @return Polygon in which points are arranged in order, row-by-row, starting from the top-left corner of the grid.
	 */
	public static TMAGridShape detectTMACoresFromBinary(ByteProcessor bp, double coreDiameterPx, int nHorizontal, int nVertical, Roi roi) {
		return detectTMACoresFromBinary(null, bp, coreDiameterPx, nHorizontal, nVertical, roi);
	}
	
	
	private static TMAGridShape detectTMACoresFromBinary(FloatProcessor fpOrig, ByteProcessor bp, double coreDiameterPx, int nHorizontal, int nVertical, Roi roi) {

		// Identify regions with areas close to the specified core area & high circularity
		// (Making sure we have a ByteProcessor.... we probably do, in which case there should be no duplication)
		Polygon polyDetected = new Polygon();
		ImageProcessor ipGood = identifyGoodCores(bp, coreDiameterPx, false, polyDetected);

		// Identify potential adjacent pairs, either to the right or below.
		// Use median of angles to estimate overall slide rotation (correcting for right/below differences)
		double angle = estimateRotation(polyDetected, coreDiameterPx);

		// Rotate the binary image & use profiles to identify the most likely grid layout.
		// Use Gaussian filter + sum trick, normalizing so the max sum should be == number of rows
		bp = (ByteProcessor)bp.duplicate();
		if (!Double.isNaN(angle)) {
			// Just in case we used labels, make sure we have clipped pixels to the same max intensity
			ipGood.setBackgroundValue(0);
			ipGood.max(1);
			ipGood.rotate(-angle);
			bp.setBackgroundValue(0);
			bp.rotate(-angle);
//			new ImagePlus("Good", ipGood.duplicate()).show();
		}

		// Identify the top peaks in rows and columns to get grid pattern
		ByteProcessor bpGood = (ByteProcessor)ipGood.convertToByte(false);
		int[] xLocs = new int[nHorizontal];
		int[] yLocs = new int[nVertical];
		int nHorizontalDetected = estimateGrid(bpGood, xLocs, (int)coreDiameterPx, false);
		if (nHorizontalDetected <= 0)
			return null;
		int nVerticalDetected = estimateGrid(bpGood, yLocs, (int)coreDiameterPx, true); 
		if (nVerticalDetected <= 0)
			return null;
		if (nHorizontalDetected < nHorizontal || nVerticalDetected < nVertical) {
			IJ.log("Ensure the grid labels are correct - I can detect at most " + nVerticalDetected + " rows and " + nHorizontalDetected + " columns with the current settings (" + nVertical + " and " + nHorizontal + " requested).");
//			return null;
		}
		Polygon polyGrid = new Polygon();
		for (int j = 0; j < nVerticalDetected; j++) {
			int y = yLocs[j];
			for (int i = 0; i < nHorizontalDetected; i++) {
				int x = xLocs[i];
				polyGrid.addPoint(x, y);
//				IJ.log(new Point(x, y).toString());
			}
		}
		
		// Improve grid coordinates
//		refineGridCoordinates(bp, polyGrid, coreDiameterPx);
//		IJHelpers.quickShowImage("Binary", bp);
		refineGridCoordinatesByShifting(bp, polyGrid, nHorizontalDetected, coreDiameterPx);
		
		if (!Double.isNaN(angle) && angle != 0) {
			// Rotate according to the angle computed previously
			// (Note this code is largely based on ImageJ's standard RoiRotator plugin)
			double xcenter = bpGood.getWidth() / 2;
			double ycenter = bpGood.getHeight() / 2;
			double theta = -angle*Math.PI/180.0;
			for (int i = 0; i < polyGrid.npoints; i++) {
				double dx = polyGrid.xpoints[i]-xcenter;
				double dy = ycenter-polyGrid.ypoints[i];
				double radius = Math.sqrt(dx*dx+dy*dy);
				double a = Math.atan2(dy, dx);
				double xNew = (xcenter + radius*Math.cos(a+theta));
				double yNew = (ycenter - radius*Math.sin(a+theta));
				polyGrid.xpoints[i] = (int)(xNew + 0.5);
				polyGrid.ypoints[i] = (int)(yNew + 0.5);
			}
		}
		
		return new TMAGridShape(polyGrid, nVerticalDetected, nHorizontalDetected);
	}
	
	
	
	
	static class TMAGridShape {
		
		final public int nVertical;
		final public int nHorizontal;
		final public Polygon polyGrid;
		
		private TMAGridShape(final Polygon polyGrid, final int nVertical, final int nHorizontal) {
			this.polyGrid = polyGrid;
			this.nVertical = nVertical;
			this.nHorizontal = nHorizontal;
		}
		
	}
	
	
	
	
	
	/**
	 * Identify regions in a binary image likely to correspond to complete TMA cores,
	 * based both on size and circularity.
	 * @param bp - The binary image to process; 0 should be background, 255 foreground
	 * @param coreDiameterPx - Typical core diameter, in pixels
	 * @param labelCores - TRUE if the output should be a labelled image (unique integer value per core), otherwise a binary image will be returned
	 * @param polyCentroids - A Polygon to which the centroids of the detected regions will be added (if not null).
	 * @return Binary or labelled image showing containing only the regions of bp with shapes & sizes corresponding to likely complete TMA cores.
	 */
	private static ImageProcessor identifyGoodCores(ByteProcessor bp, double coreDiameterPx, boolean labelCores, Polygon polyCentroids) {
		double estimatedArea = Math.PI * (coreDiameterPx * coreDiameterPx) * 0.25;
		double minArea = estimatedArea * 0.5;
		double maxArea = estimatedArea * 2.0;
		return identifyGoodCores(bp, minArea, maxArea, 0.8, labelCores, polyCentroids);
	}
	
	
	/**
	 * Identify regions in a binary image likely to correspond to complete TMA cores,
	 * based both on size and circularity.
	 * @param bp - The binary image to process; 0 should be background, 255 foreground
	 * @param minArea - Minimum area of a region to keep (in pixels)
	 * @param maxArea - Maximum area of a region to keep (in pixels)
	 * @param minCircularity - Minimum circularity of a region to keep.
	 * @param labelCores - TRUE if the output should be a labelled image (unique integer value per core), otherwise a binary image will be returned
	 * @param polyCentroids - A Polygon to which the centroids of the detected regions will be added (if not null).
	 * @return Binary or labelled image showing containing only the regions of bp with shapes & sizes corresponding to likely complete TMA cores.
	 */	
	private static ImageProcessor identifyGoodCores(ByteProcessor bp, double minArea, double maxArea, double minCircularity, boolean labelCores, Polygon polyCentroids) {
		// Create a binary image of only the roundest structures of approximately the correct area
		bp.setThreshold(127, 512, ImageProcessor.NO_LUT_UPDATE);
		int options = labelCores ? ParticleAnalyzer.SHOW_ROI_MASKS : ParticleAnalyzer.SHOW_MASKS;
		int measurements = Measurements.CENTROID;
		ResultsTable rt = new ResultsTable();
		ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements, rt, minArea, maxArea, minCircularity, 1.0);
		pa.setHideOutputImage(true);
		pa.analyze(new ImagePlus("Temp", bp), bp);
		if (polyCentroids != null) {
			for (int i = 0; i < rt.getCounter(); i++) {
				int x = (int)(rt.getValueAsDouble(ResultsTable.X_CENTROID, i) + .5);
				int y = (int)(rt.getValueAsDouble(ResultsTable.Y_CENTROID, i) + .5);
				polyCentroids.addPoint(x, y);
			}
		}
		return pa.getOutputImage().getProcessor();
	}
	
	/**
	 * Make a binary image depicting (hopefully) the TMA cores.
	 * The diameter of each core, in pixels, is used to determine filter sizes:
	 * 	- for background estimation (morphological opening)
	 * 	- for morphological cleaning of the thresholded image
	 * The threshold itself is computed using the triangle method.
	 * @param ip - the TMA slide image - this should show cores as dark-on-light (or in colour).
	 * 				Fluorescence images may be inverted beforehand.
	 * @param coreDiameterPx - typical diameter of a TMA core, given in pixels
	 * @param roi - optionally null; everything outside the ROI will be set to zero.
	 * @param isFluorescence - true if this is a fluorescence image, false if it is brightfield.
	 * @return ByteProcessor with detected regions having values 255, background having the value 0
	 */
	public static ByteProcessor makeBinaryImage(ImageProcessor ip, double coreDiameterPx, Roi roi, boolean isFluorescence) {
		// Ensure we have a grayscale duplicate of the image
		if (ip instanceof ColorProcessor)
			ip = ip.convertToByte(false);
		else
			ip = ip.duplicate();
		ip.resetRoi();
		
		// Apply small median filter (helps remove JPEG artifacts)
		RankFilters rf = new RankFilters();
		rf.rank(ip, 1.0, RankFilters.MEDIAN);
		
		// If this is a brightfield image, invert it
		if (!isFluorescence)
			ip.invert();

//		// Subtract from a morphological-opened image, with the filter size slightly bigger than the core size
//		double filterRadius = coreDiameterPx * 0.6;
//		ImageProcessor ip2 = ip.duplicate();
//		System.err.println("Starting");
//		long start = System.currentTimeMillis();
//		rf.rank(ip2, filterRadius, RankFilters.MIN);
//		rf.rank(ip2, filterRadius, RankFilters.MAX);
//		long end = System.currentTimeMillis();
//		System.err.println("Duration: " + (end - start));
//		ip.copyBits(ip2, 0, 0, Blitter.SUBTRACT);
		
		// Subtract from a morphological-opened image, with the filter size slightly bigger than the core size
		// Update 15/10/2016 - Because the filter size is likely to be very large (maybe radius 40-50 pixels?), downsample first for performance
		double filterRadius = coreDiameterPx * 0.6;
		ImageProcessor ip2 = ip.duplicate();
		double downsample = Math.round(filterRadius / 10);
		if (downsample > 1) {
			ip2 = ip.resize((int)(ip.getWidth() / downsample + 0.5), (int)(ip.getHeight() / downsample + 0.5), true);
//			long start = System.currentTimeMillis();
			rf.rank(ip2, filterRadius/downsample, RankFilters.MIN);
			rf.rank(ip2, filterRadius/downsample, RankFilters.MAX);
			ip2 = ip2.resize(ip.getWidth(), ip.getHeight());
//			long end = System.currentTimeMillis();
//			System.err.println("Duration: " + (end - start));
		}
		ip.copyBits(ip2, 0, 0, Blitter.SUBTRACT);
		
		// Smooth slightly
		ip.smooth();
		
		// Estimate a threshold using the triangle method
		ip.setAutoThreshold(AutoThresholder.Method.Triangle, true);
		
		// Apply the threshold
		ByteProcessor bp = new ByteProcessor(ip.getWidth(), ip.getHeight());
		byte[] bpPixels = (byte[])bp.getPixels();
		double threshold = ip.getMinThreshold();
		for (int i = 0; i < bpPixels.length; i++)
			bpPixels[i] = (ip.getf(i) > threshold) ? (byte)255 : 0;
		
		// Apply (gentle) morphological cleaning
		filterRadius = Math.max(1.0, coreDiameterPx * 0.02);
		rf.rank(bp, filterRadius, RankFilters.MAX);
		rf.rank(bp, filterRadius, RankFilters.MIN);
		rf.rank(bp, filterRadius, RankFilters.MIN);
		rf.rank(bp, filterRadius, RankFilters.MAX);
		
		// Fill holes
		RoiLabeling.fillHoles(bp);
		
		// Remove everything outside the ROI
		if (roi != null && roi.isArea()) {
			ip.setValue(0);
			ip.fillOutside(roi);
		}
		
		return bp;
	}
	
	
	
	private static boolean pointTooCloseX(Collection<Point> points, Point newPoint, double minDistance) {
		for (Point p : points)
			if (Math.abs(p.x - newPoint.x) < minDistance)
				return true;
		return false;
	}
	
	private static List<Point> processTopRowOfPoints(List<Point> sortedPoints, double coreDiameterPx, int nPointsPerRow) {
		// Take the first point and add it to the row being processed
		ArrayList<Point> pointRow = new ArrayList<>(nPointsPerRow);
		pointRow.add(sortedPoints.remove(0));
		// Continue adding points until we have enough, making sure adequate spacing or at least one core diameter between them
		Iterator<Point> iter = sortedPoints.iterator();
		while (iter.hasNext()) {
			Point p = iter.next();
			// Any point with an x-coordinate within 1 core diameter is too close - must be from a lower row
			if (!pointTooCloseX(pointRow, p, coreDiameterPx)) {
				iter.remove();
				pointRow.add(p);
				// We might have enough points now
				if (pointRow.size() == nPointsPerRow) {
					break;
				}
			}
		}
		// Sort the points by x coordinate
		Collections.sort(pointRow, new Comparator<Point>() {
			@Override
			public int compare(Point p1, Point p2) {
				return Integer.valueOf(p1.x).compareTo(p2.x);
			}
		});
			
		return pointRow;
	}
	
	
	/**
	 * Given a polygon representing TMA core centroids in any order, fit this to a coordinate grid.
	 * Essentially this amounts to sorting the points starting from the top left,
	 * continuing along each row.
	 * In practice the process is more complicated (and less certain to be correct...)
	 * because coordinates in the same TMA row cannot be trusted to occur with the same image y coordinate,
	 * similarly coordinates in the same TMA column can have different x coordinates.
	 * @param poly - The original centroid coordinates; this should have exactly nHorizontal * nVertical points.
	 * @param coreDiameterPx - Approximate diameter of one TMA core (used to determine potential variability between core centroids)
	 * @param nHorizontal - The number of cores in each row
	 * @param nVertical - The number of cores in each column
	 * @return A polygon of TMA cores, with the order going from top-left, one row at a time.
	 * If the input polygon has the wrong number of points, null is returned.
	 */
	public static Polygon fitCorePolygonToGrid(Polygon poly, double coreDiameterPx, int nHorizontal, int nVertical) {
		// Extract the points & check we have the right number
		if (poly.npoints != (nHorizontal * nVertical)) {
			return null;
		}
		
		// Sort the points, top to bottom
		ArrayList<Point> points = new ArrayList<>();
		for (int i = 0; i < poly.npoints; i++)
			points.add(new Point(poly.xpoints[i], poly.ypoints[i]));
		Collections.sort(points, new Comparator<Point>() {
			@Override
			public int compare(Point p1, Point p2) {
				return Integer.valueOf(p1.y).compareTo(p2.y);
			}
		});
		
		// Loop through the rows, constructing a polygon with the coords in order
		Polygon poly2 = new Polygon();
		for (int j = 0; j < nVertical; j++) {
			List<Point> pointRow = processTopRowOfPoints(points, coreDiameterPx, nHorizontal);
			for (int i = 0; i < pointRow.size(); i++) {
				Point p = pointRow.get(i);
				poly2.addPoint(p.x, p.y);
			}
		}
		return poly2;
	}
	
	
	
	private static double estimateRotation(Polygon poly, double coreDiameterPx) {
		ArrayList<Double> angles = new ArrayList<>();
		for (int i = 0; i < poly.npoints; i++) {
			int x = poly.xpoints[i];
			int y = poly.ypoints[i];
			for (int j = 0; j < poly.npoints; j++) {
				int x2 = poly.xpoints[j];
				int y2 = poly.ypoints[j];
				if ((x2 > x) && (x2 - x) < coreDiameterPx*2 && Math.abs(y - y2) < coreDiameterPx) {
					double angle = (180.0/Math.PI) * Math.atan2(y2 - y, x2 - x);
					angles.add(angle);
				}
			}
		}
		
		// If we have no angles, we don't know the rotation...
		if (angles.isEmpty())
			return Double.NaN;
		
		// Return the median angle
		Collections.sort(angles);
		if (angles.size() % 2 == 0)
			return 0.5 * (angles.get(angles.size()/2) + angles.get(angles.size()/2-1));
		else
			return angles.get(angles.size()/2);			
	}
	
	
	
	private static boolean checkNewIndSeparated(int inds[], int newInd, int nInds, int minSeparation) {
		for (int i = 0; i < nInds; i++) {
			if (Math.abs(newInd - inds[i]) < minSeparation)
				return false;
		}
		return true;
	}
	
	private static int estimateGrid(ByteProcessor bp, int[] locs, int minSeparation, boolean doHorizontal) {
		int nMaxima = locs.length;
		int[] maxima = new int[nMaxima];
		Arrays.fill(maxima, -1);
		
		ImagePlus impTemp = new ImagePlus("Temp", bp);
		impTemp.setRoi(new Roi(0, 0, bp.getWidth(), bp.getHeight()));
		double[] prof = new ProfilePlot(impTemp, doHorizontal).getProfile();
		// Find the top nMaxima peaks with sufficient separation
		double tolerance = 0.0;
		int[] peakLocs = MaximumFinder.findMaxima(prof, tolerance, false);
		//int[] peakLocs = new int[nMaxima];
//		if (peakLocs.length < nMaxima) {
//			Arrays.sort(peakLocs);
//			for (int i = 0; i < peakLocs.length; i++)
//				locs[i] = peakLocs[i];
//			return peakLocs.length;
//		}
		int n = 0;
		for (int p : peakLocs) {
			if (checkNewIndSeparated(maxima, p, n, minSeparation)) {
				maxima[n] = p;
				n++;
				if (n == nMaxima)
					break;
			}
		}	
		// Sort the maxima in ascending order now
		Arrays.sort(maxima);
		// Put in as many maxima as we have
		int counter = 0;
		for (int m : maxima) {
			if (counter == nMaxima)
				break;
			if (m < 0)
				continue;
			locs[counter] = m;
			counter++;
		}
		return counter;
	}
	
	

	private static Roi roiFromWand(Wand wand) {
		return new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.POLYGON);
	}
	
	
	// Find the location of the maximum within a ROI; resolve ties by selecting the maximum closest to a specified point
	private static Point findClosestMaximumInROI(ImageProcessor ip, Roi roi, Point p) {
		Rectangle bounds = roi.getBounds();
		Float maxVal = Float.NEGATIVE_INFINITY;
		Double minDist = Double.POSITIVE_INFINITY;
		Point maxPoint = null;
		int y1 = Math.max(0, bounds.y);
		int y2 = Math.min(ip.getHeight(), bounds.y+bounds.height);
		int x1 = Math.max(0, bounds.x);
		int x2 = Math.min(ip.getWidth(), bounds.x+bounds.width);
		for (int y = y1; y < y2; y++) {
			for (int x = x1; x < x2; x++) {
				if (roi.contains(x, y)) {
					float val = ip.getf(x, y);
					if (val > maxVal || (val == maxVal && p.distance(x, y) < minDist)) {
						maxVal = val;
						maxPoint = new Point(x, y);
						minDist = p.distance(x, y);
					}
				}
			}			
		}
		return maxPoint;
	}
	
	
	private static void refineGridCoordinatesByShifting(ByteProcessor bp, Polygon polyGrid, int nHorizontal, double coreDiameterPx) {
		// Create a binary image containing regions to consider - exclude very small & very large regions
		// Use watershed segmentation to split up round structures
		new EDM().toWatershed(bp);
		double estimatedArea = Math.PI * coreDiameterPx * coreDiameterPx * 0.25;
		FloatProcessor fpDensity = identifyGoodCores(bp, estimatedArea * 0.1, estimatedArea * 2.0, 0, false, null).convertToFloatProcessor();
		fpDensity.max(1);
		
//		new ImagePlus("Density-Orig", fpDensity.duplicate()).show();
		
		// Loop through the grid & see examples where the estimated point already falls within a likely core
		double minDiameter = coreDiameterPx * 0.7;
		Wand wand = new Wand(bp);
		boolean[] confirmed = new boolean[polyGrid.npoints];
		for (int i = 0; i < polyGrid.npoints; i++) {
			int x = polyGrid.xpoints[i];
			int y = polyGrid.ypoints[i];
			// If grid location already falls within a convincingly large ROI, use its bounding box centre
//			IJ.log(new Point(x, y).toString());
			boolean inside = fpDensity.getf(x, y) > 0;
			if (inside) {
				wand.autoOutline(x, y, 0.0, Wand.FOUR_CONNECTED);
				Roi roi = roiFromWand(wand);
				Rectangle bounds = roi.getBounds();
				if (bounds.width >= minDiameter && bounds.height >= minDiameter) {
					polyGrid.xpoints[i] = bounds.x + bounds.width/2;
					polyGrid.ypoints[i] = bounds.y + bounds.height/2;					
					// Introduce a large density penalty to prevent missing cores from encroaching on this area
					// (Scale the ROI slightly first to make this more effective)
					roi = RoiScaler.scale(roi, 1, 1, true);
					fpDensity.setValue(-10000);
					fpDensity.fill(roi);
					// Mark ROI as confirmed
					confirmed[i] = true;
				}
			}
		}
		
		// Create (effectively) a Voronoi image based on the current centroid estimate
		ByteProcessor bpTest = new ByteProcessor(bp.getWidth(), bp.getHeight());
		for (int i = 0; i < polyGrid.npoints; i++) {
			if (!confirmed[i]) {
				int x = polyGrid.xpoints[i];
				int y = polyGrid.ypoints[i];
				bpTest.setf(x, y, 255);
			}
		}
		// Need to try/catch this, as might return null if thread is interrupted
		try {
			FloatProcessor fpEDM = new EDM().makeFloatEDM(bpTest, (byte)255, false);
			// I never quite understood why, but this causes a fatal (compilation) error with Java 1.6 on the Mac - so instead avoid FloatBlitter
//			fpEDM.multiply(-1);
//			fpEDM.copyBits(bpTest, 0, 0, Blitter.ADD);
			for (int i = 0; i < fpEDM.getWidth() * fpEDM.getHeight(); i++) {
				fpEDM.setf(i, bpTest.getf(i) - fpEDM.getf(i));
			}
			
			ByteProcessor bpRegions = new MaximumFinder().findMaxima(fpEDM, 255, ImageProcessor.NO_THRESHOLD, MaximumFinder.SEGMENTED, false, false);
			// Penalise boundaries - when refining uncertain cores, we don't want to move into the territory of another core
			byte[] pxRegions = (byte[])bpRegions.getPixels();
			float[] pxDensity = (float[])fpDensity.getPixels();
			for (int i = 0; i < pxRegions.length; i++) {
				if (pxRegions[i] == 0)
					pxDensity[i] = -10000;
			}
		} catch (Exception e) {
			return;
		}
				
		// Apply a mean filter to determine local unassigned densities
//		new ImagePlus("Density_before", fpDensity.duplicate()).show();
//		long start = System.currentTimeMillis();
		// Note: this is another bottleneck... filter size can be large
		new RankFilters().rank(fpDensity, coreDiameterPx * 0.5, RankFilters.MEAN);
//		System.err.println("Time: " + (System.currentTimeMillis() - start));
//		fpDensity.min(-1);
//		new ImagePlus("Density", fpDensity.duplicate()).show();
		
		// Find local maxima within each unassigned core region, with a preference towards the maximum closest to the original estimate
		for (int i = 0; i < polyGrid.npoints; i++) {
			if (!confirmed[i]) {
				int x = polyGrid.xpoints[i];
				int y = polyGrid.ypoints[i];
				OvalRoi roiRegion = new OvalRoi(x-coreDiameterPx*0.5, y-coreDiameterPx*0.5, coreDiameterPx, coreDiameterPx);
				Point maxPoint = findClosestMaximumInROI(fpDensity, roiRegion, new Point(x, y));
				if (maxPoint != null) {
					polyGrid.xpoints[i] = maxPoint.x;
					polyGrid.ypoints[i] = maxPoint.y;
					confirmed[i] = true;
				}
			}
		}
		
	}

}