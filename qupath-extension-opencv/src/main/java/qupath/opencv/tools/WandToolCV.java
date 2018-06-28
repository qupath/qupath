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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.DoubleProperty;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.BrushTool;
import qupath.lib.images.stores.DefaultImageRegionStore;

/**
 * Wand tool, which acts rather like the brush - except that it expands regions 
 * (somes rather too eagerly?) based upon local pixel values.
 * 
 * @author Pete Bankhead
 *
 */
public class WandToolCV extends BrushTool {
	
	final private static Logger logger = LoggerFactory.getLogger(WandToolCV.class);
	
	
	private Point2D pLast = null;
	private static int w = 149;
	private BufferedImage imgTemp = new BufferedImage(w, w, BufferedImage.TYPE_3BYTE_BGR);
	private Mat mat = null; //new Mat(w, w, CvType.CV_8U);
	private Mat matMask = null; //new Mat(w, w, CvType.CV_8U);
	private Scalar zero = new Scalar(0);
	private Scalar one = new Scalar(1);
	private Scalar threshold = new Scalar(1, 1, 1);
	private Point seed = new Point(w/2, w/2);
//	private Size morphSize = new Size(5, 5);
	private Mat strel = null;
	private Mat contourHierarchy = null;
	
	private Rectangle bounds = new Rectangle();
	
	private List<MatOfPoint> contours = new ArrayList<>();
	private Size blurSize = new Size(31, 31);
	
	
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

	}
	
	
	@Override
	protected Shape createShape(double x, double y, boolean useTiles) {
		
		if (mat == null)
			mat = new Mat(w, w, CvType.CV_8UC3);
		if (matMask == null)
			matMask = new Mat(w+2, w+2, CvType.CV_8U);

		
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
		g2d.fillRect(0, 0, w, w);
		double xStart = x-w*downsample*0.5;
		double yStart = y-w*downsample*0.5;
		bounds.setFrame(xStart, yStart, w*downsample, w*downsample);
		g2d.scale(1.0/downsample, 1.0/downsample);
		g2d.translate(-xStart, -yStart);
		regionStore.paintRegionCompletely(viewer.getServer(), g2d, bounds, viewer.getZPosition(), viewer.getTPosition(), viewer.getDownsampleFactor(), null, viewer.getImageDisplay(), 250);
		
		// We could optionally paint the hierarchy, so that it too influences the values
//		Collection<PathObject> pathObjects = viewer.getHierarchy().getObjectsForRegion(PathAnnotationObject.class, ImageRegion.createInstance(
//				(int)bounds.getX()-1, (int)bounds.getY()-1, (int)bounds.getWidth()+1, (int)bounds.getHeight()+1, viewer.getZPosition(), viewer.getTPosition()), null);
//		PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, bounds.getBounds(), pathObjects, viewer.getOverlayOptions(), null, downsample);

		g2d.dispose();
		
		// Put pixels into an OpenCV image
		byte[] buffer = ((DataBufferByte)imgTemp.getRaster().getDataBuffer()).getData();
		mat.put(0, 0, buffer);
		
//		Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2Lab);
//		blurSigma = 4;
		
		double blurSigma = Math.max(0.5, getWandSigmaPixels());
		double size = Math.ceil(blurSigma * 2) * 2 + 1;
		blurSize.width = size;
		blurSize.height = size;
		
		// Smooth a little
		Imgproc.GaussianBlur(mat, mat, blurSize, blurSigma);
		
//		Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2Lab);
		
		MatOfDouble mean = new MatOfDouble();
		MatOfDouble stddev = new MatOfDouble();
		Core.meanStdDev(mat, mean, stddev);
//		logger.trace(stddev.dump());

		double[] stddev2 = stddev.toArray();
		double scale = 1.0 / getWandSensitivity();
		if (scale < 0)
			scale = 0.01;
		for (int i = 0; i < stddev2.length; i++)
			stddev2[i] = stddev2[i]*scale;
		threshold.set(stddev2);

		mean.release();
		stddev.release();
		
		matMask.setTo(zero);
		Imgproc.circle(matMask, seed, w/2, one);
		Imgproc.floodFill(mat, matMask, seed, one, null, threshold, threshold, 4 | (2 << 8) | Imgproc.FLOODFILL_MASK_ONLY | Imgproc.FLOODFILL_FIXED_RANGE);
		Core.subtract(matMask, one, matMask);
		
		if (strel == null)
			strel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
		Imgproc.morphologyEx(matMask, matMask, Imgproc.MORPH_CLOSE, strel);
////		Imgproc.morphologyEx(matMask, matMask, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, size));
//		
////		threshold = new Scalar(10, 10, 10);
//		double[] stddev2 = stddev.toArray();
//		double scale = .5;
//		threshold = new Scalar(stddev2[0]*scale, stddev2[1]*scale, stddev2[2]*scale);
		
		contours.clear();
		if (contourHierarchy == null)
			contourHierarchy = new Mat();
		Imgproc.findContours(matMask, contours, contourHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//		logger.trace("Contours: " + contours.size());

		Path2D path = new Path2D.Float();
		boolean isOpen = false;
		for (MatOfPoint contour : contours){
			
			// Discard single pixels / lines
			if (contour.size().height <= 2)
				continue;
			
			// Create a polygon ROI
			boolean firstPoint = true;
	        for (Point p : contour.toArray()) {
	        	double xx = (p.x - w/2-1) * downsample + x;
	        	double yy = (p.y - w/2-1) * downsample + y;
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
