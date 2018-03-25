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

package qupath.opencv.tools;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import static org.bytedeco.javacpp.opencv_core.*;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.indexer.IntIndexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.tools.BrushTool;
import qupath.lib.images.stores.DefaultImageRegionStore;
import qupath.lib.regions.ImageRegion;

/**
 * Wand tool, which acts rather like the brush - except that it expands regions 
 * (sometimes rather too eagerly?) based upon local pixel values.
 * 
 * @author Pete Bankhead
 *
 */
public class WandToolCV extends BrushTool {
	
	final private static Logger logger = LoggerFactory.getLogger(WandToolCV.class);
	
	
	private Point2D pLast = null;
	private static int w = 149;
	private BufferedImage imgTemp = new BufferedImage(w, w, BufferedImage.TYPE_3BYTE_BGR);
	private BufferedImage imgSelected = new BufferedImage(w+2, w+2, BufferedImage.TYPE_BYTE_GRAY);
	private Mat mat = null; //new Mat(w, w, CV_8U);
	private Mat matMask = null; //new Mat(w, w, CV_8U);
	private Mat matSelected = null; //new Mat(w, w, CV_8U);
	private Scalar threshold = Scalar.all(1.0);
	private Point seed = new Point(w/2, w/2);
//	private Size morphSize = new Size(5, 5);
	private Mat strel = null;
	private Mat contourHierarchy = null;
	
	private Rectangle bounds = new Rectangle();
	
	private Size blurSize = new Size(31, 31);
	
	/**
	 * Paint overlays and allow them to influence the want
	 */
	private static BooleanProperty wandUseOverlays = PathPrefs.createPersistentPreference("wandUseOverlays", true);

	public static BooleanProperty wandUseOverlaysProperty() {
		return wandUseOverlays;
	}
	
	public static boolean getWandUseOverlays() {
		return wandUseOverlays.get();
	}
	
	public static void setWandUseOverlays(final boolean useOverlays) {
		wandUseOverlays.set(useOverlays);
	}
		
		
	
	/**
	 * Sigma value associated with Wand tool smoothing
	 */
	private static DoubleProperty wandSigmaPixels = PathPrefs.createPersistentPreference("wandSigmaPixels", 4.0);

	public static DoubleProperty wandSigmaPixelsProperty() {
		return wandSigmaPixels;
	}
	
	public static double getWandSigmaPixels() {
		return wandSigmaPixels.get();
	}
	
	public static void setWandSigmaPixels(final double sigma) {
		wandSigmaPixels.set(sigma);
	}
	
	
	/**
	 * Sensitivity value associated with the wand tool
	 */
	private static DoubleProperty wandSensitivityProperty = PathPrefs.createPersistentPreference("wandSensitivityPixels", 2.0);

	public static DoubleProperty wandSensitivityProperty() {
		return wandSensitivityProperty;
	}
	
	public static double getWandSensitivity() {
		return wandSensitivityProperty.get();
	}
	
	public static void setWandSensitivity(final double sensitivity) {
		wandSensitivityProperty.set(sensitivity);
	}
	
	

	public WandToolCV(QuPathGUI qupath) {
		super(qupath);
		
		// Add preference to adjust Wand tool behavior
		qupath.getPreferencePanel().addPropertyPreference(wandSigmaPixelsProperty(), Double.class,
				"Wand smoothing",
				"Drawing tools",
				"Set the smoothing used by the wand tool - higher values lead to larger, smoother regions (default = 4.0)");
		
		qupath.getPreferencePanel().addPropertyPreference(wandSensitivityProperty(), Double.class,
				"Wand sensitivity",
				"Drawing tools",
				"Set the sensitivity of the wand tool - lower values make it pay less attention to local intensities, and act more like the brush tool (default = 2.0)");

		qupath.getPreferencePanel().addPropertyPreference(wandUseOverlaysProperty(), Boolean.class,
				"Wand use overlays",
				"Drawing tools",
				"Use image overlay information to influence the regions created with the wand tool");
		
	}
	
	
	@Override
	protected Shape createShape(double x, double y, boolean useTiles, Shape addToShape) {
		
		if (mat == null)
			mat = new Mat(w, w, CV_8UC3);
		if (matMask == null)
			matMask = new Mat(w+2, w+2, CV_8U);
		if (matSelected == null)
			matSelected = new Mat(w+2, w+2, CV_8U);

		
		if (pLast != null && pLast.distanceSq(x, y) < 4)
			return new Path2D.Float();
		
		long startTime = System.currentTimeMillis();
		
		QuPathViewer viewer = getViewer();
		if (viewer == null)
			return new Path2D.Float();
		
		double downsample = viewer.getDownsampleFactor();
		
		DefaultImageRegionStore regionStore = viewer.getImageRegionStore();
		
		// Paint the image as it is currently being viewed
		Graphics2D g2d = imgTemp.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.setClip(0, 0, w, w);
		g2d.fillRect(0, 0, w, w);
		double xStart = x-w*downsample*0.5;
		double yStart = y-w*downsample*0.5;
		bounds.setFrame(xStart, yStart, w*downsample, w*downsample);
		g2d.scale(1.0/downsample, 1.0/downsample);
		g2d.translate(-xStart, -yStart);
		regionStore.paintRegionCompletely(viewer.getServer(), g2d, bounds, viewer.getZPosition(), viewer.getTPosition(), viewer.getDownsampleFactor(), null, viewer.getImageDisplay(), 250);
		// Optionally include the overlay information when using the wand
		float opacity = viewer.getOverlayOptions().getOpacity();
		if (opacity > 0 && getWandUseOverlays()) {
			ImageRegion region = ImageRegion.createInstance(
					(int)bounds.getX()-1, (int)bounds.getY()-1, (int)bounds.getWidth()+1, (int)bounds.getHeight()+1, viewer.getZPosition(), viewer.getTPosition());
			if (opacity < 0)
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			for (PathOverlay overlay : viewer.getOverlayLayers().toArray(new PathOverlay[0])) {
				overlay.paintOverlay(g2d, region, downsample, null, true);
			}
		}
		
		// Create a mask for the current shape, if required
		// Because of the later morphological operations, this helps avoid tiny fragmented regions/gaps being generated
		boolean hasMask = false;
		if (addToShape != null) {
			g2d = imgSelected.createGraphics();
			g2d.setColor(Color.BLACK);
			g2d.fillRect(0, 0, imgSelected.getWidth(), imgSelected.getHeight());
			g2d.setColor(Color.WHITE);
			// Fill in the center region, around the click
			g2d.fillRect((w+2)/2-1, (w+2)/2-1, 3, 3);
			g2d.scale(1.0/downsample, 1.0/downsample);
			g2d.translate(-xStart+downsample*0.5, -yStart+downsample*0.5);
			// Fill in the selected shape
			g2d.fill(addToShape);
			g2d.dispose();
			byte[] buffer = ((DataBufferByte)imgSelected.getRaster().getDataBuffer()).getData(0);
		    ByteBuffer matBuffer = matSelected.createBuffer();
		    matBuffer.clear();
		    matBuffer.put(buffer);
		    hasMask = true;
		} else
			matSelected.put(Scalar.ZERO);
		
		// Put pixels into an OpenCV image
		byte[] buffer = ((DataBufferByte)imgTemp.getRaster().getDataBuffer()).getData();
	    ByteBuffer matBuffer = mat.createBuffer();
	    matBuffer.put(buffer);
//		mat.put(0, 0, buffer);
		
//		opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_BGR2Lab);
//		blurSigma = 4;
		
		double blurSigma = Math.max(0.5, getWandSigmaPixels());
		int size = (int)Math.ceil(blurSigma * 2) * 2 + 1;
		blurSize.width(size);
		blurSize.height(size);
		
		// Smooth a little
		opencv_imgproc.GaussianBlur(mat, mat, blurSize, blurSigma);
		
//		opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_RGB2Lab);
		
		Mat mean = new Mat();
		Mat stddev = new Mat();
		// Could optionally base the threshold on the masked region... but for now we don't
//		if (hasMask)
//			meanStdDev(mat, mean, stddev, matSelected.apply(new Rect(1, 1, w, w)));
//		else
			meanStdDev(mat, mean, stddev);

		DoubleBuffer stddevBuffer = stddev.createBuffer();
		double[] stddev2 = new double[3];
		stddevBuffer.get(stddev2);
		double scale = 1.0 / getWandSensitivity();
		if (scale < 0)
			scale = 0.01;
		for (int i = 0; i < stddev2.length; i++)
			stddev2[i] = stddev2[i]*scale;
		threshold.put(stddev2);

		mean.release();
		stddev.release();
		
		matMask.put(Scalar.ZERO);
		opencv_imgproc.circle(matMask, seed, w/2, Scalar.ONE);
		opencv_imgproc.floodFill(mat, matMask, seed, Scalar.ONE, null, threshold, threshold, 4 | (2 << 8) | opencv_imgproc.FLOODFILL_MASK_ONLY | opencv_imgproc.FLOODFILL_FIXED_RANGE);
		subtractPut(matMask, Scalar.ONE);
		
		if (hasMask)
			opencv_core.orPut(matMask, matSelected);
		
		if (strel == null)
			strel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, new Size(5, 5));
		opencv_imgproc.morphologyEx(matMask, matMask, opencv_imgproc.MORPH_CLOSE, strel);
		if (hasMask)
			opencv_core.orPut(matMask, matSelected);
//		opencv_imgproc.morphologyEx(matMask, matMask, opencv_imgproc.MORPH_OPEN, strel);
////		opencv_imgproc.morphologyEx(matMask, matMask, opencv_imgproc.MORPH_OPEN, opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, size));
//		
		MatVector contours = new MatVector();
		if (contourHierarchy == null)
			contourHierarchy = new Mat();
		opencv_imgproc.findContours(matMask, contours, contourHierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);
//		logger.trace("Contours: " + contours.size());

		Path2D path = new Path2D.Float();
		boolean isOpen = false;
		for (long i = 0; i < contours.size(); i++){
			
			Mat contour = contours.get(i);
			
			// Discard single pixels / lines
			if (contour.size().height() <= 2)
				continue;
			
			// Create a polygon ROI
			boolean firstPoint = true;
			IntIndexer idxrContours = contour.createIndexer();
	        for (long r = 0; r < idxrContours.rows(); r++) {
	        		int px = idxrContours.get(r, 0L, 0L);
	        		int py = idxrContours.get(r, 0L, 1L);
		        	double xx = (px - w/2-1) * downsample + x;
		        	double yy = (py - w/2-1) * downsample + y;
		        	if (firstPoint) {
		        		path.moveTo(xx, yy);
		        		firstPoint = false;
			        	isOpen = true;
		        	} else
		        		path.lineTo(xx, yy);
		       }
		}
		if (isOpen)
			path.closePath();
		
		long endTime = System.currentTimeMillis();
		logger.trace(getClass().getSimpleName() + " time: " + (endTime - startTime));
		
		if (pLast == null)
			pLast = new Point2D.Double(x, y);
		else
			pLast.setLocation(x, y);
		
		return path;
		
	}
	
	
	/**
	 * Don't actually need the diameter for calculations here, but it's helpful for setting the cursor
	 */
	protected double getBrushDiameter() {
		QuPathViewer viewer = getViewer();
		if (viewer == null)
			return w / 8;
		else
			return w * getViewer().getDownsampleFactor() / 8;
//		if (viewer == null)
//			return PathPrefs.getBrushDiameter();
//		else
//			return PathPrefs.getBrushDiameter() * getViewer().getDownsampleFactor();
	}

}
