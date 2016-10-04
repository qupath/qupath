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

package qupath.opencv;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Simple plugin to attempt a very fast cell count.
 * 
 * @author Pete Bankhead
 *
 */
public class CellCountsCV extends AbstractTileableDetectionPlugin<BufferedImage> {

	private static Logger logger = LoggerFactory.getLogger(CellCountsCV.class);
	
	transient private FastCellCounter cellCounter;

	static class FastCellCounter implements ObjectDetector<BufferedImage> {

		// TODO: REQUEST DOWNSAMPLE IN PLUGINS
		private double x, y;
		private List<PathObject> pathObjects = new ArrayList<>();


		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			// Reset any detected objects
			pathObjects.clear();

			// Extract parameters
			double magnification = params.getDoubleParameterValue("magnification");
			boolean hasMicrons = imageData != null && imageData.getServer() != null && imageData.getServer().hasPixelSizeMicrons();
			double threshold = params.getDoubleParameterValue("threshold");
			boolean doDoG = params.getBooleanParameterValue("doDoG");
			boolean ensureMainStain = params.getBooleanParameterValue("ensureMainStain");

			// Get the region info
			double downsample = imageData.getServer().getMagnification() / magnification;
			if (Double.isNaN(downsample) || downsample < 1)
				downsample = 1;
//			Rectangle bounds = AwtTools.getBounds(pathROI);
			x = pathROI.getBoundsX();
			y = pathROI.getBoundsY();
			
			// Get the filter size
			double gaussianSigma;
			double backgroundRadius;
			if (hasMicrons) {
				gaussianSigma = params.getDoubleParameterValue("gaussianSigmaMicrons") / (imageData.getServer().getAveragedPixelSizeMicrons() * downsample);
				backgroundRadius = params.getDoubleParameterValue("backgroundRadiusMicrons") / (imageData.getServer().getAveragedPixelSizeMicrons() * downsample);;
			}
			else {
				gaussianSigma = params.getDoubleParameterValue("gaussianSigmaPixels");
				backgroundRadius = params.getDoubleParameterValue("backgroundRadiusPixels");
			}
			logger.debug("Fast cell counting with Gaussian sigma {} pixels", gaussianSigma);

			// Read the buffered image
			ImageServer<BufferedImage> server = imageData.getServer();
			BufferedImage img = server.readBufferedImage(RegionRequest.createInstance(server.getPath(), downsample, pathROI));

			/*
			 * Color deconvolution
			 */
			
			// Get hematoxylin channel
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			float[][] pxDeconvolved = WatershedNucleiCV.colorDeconvolve(img, stains.getStain(1).getArray(), stains.getStain(2).getArray(), null, 2);
			float[] pxHematoxylin = pxDeconvolved[0];
			
			// Convert to OpenCV Mat
			int width = img.getWidth();
			int height = img.getHeight();
			Mat matOrig = new Mat(height, width, CvType.CV_32FC1);
			
			// It seems OpenCV doesn't use the array directly, so no need to copy...
			matOrig.put(0, 0, pxHematoxylin);
			
			/*
			 * Detection
			 */
			
			// Subtract opened image
			if (backgroundRadius > 0) {
				Mat matBG = new Mat();
				int size = (int)Math.round(backgroundRadius) * 2 + 1;
				Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(size, size));
				Imgproc.morphologyEx(matOrig, matBG, Imgproc.MORPH_OPEN, kernel);
				Core.subtract(matOrig, matBG, matOrig);
			}
			
			
			
			// Apply Gaussian filter
			int gaussianWidth = (int)(Math.ceil(gaussianSigma * 3) * 2 + 1);
			Mat mat = new Mat(); // From now on, work with the smoothed image
			Imgproc.GaussianBlur(matOrig, mat, new Size(gaussianWidth, gaussianWidth), gaussianSigma);
			
			// Ensure cells selected only where hematoxlin > eosin/DAB
			Mat matValid = null;
			if (ensureMainStain) {
				float[] pxStain2 = pxDeconvolved[1];
				Mat matStain2 = new Mat(height, width, CvType.CV_32FC1);
				matStain2.put(0, 0, pxStain2);
				Imgproc.GaussianBlur(matStain2, matStain2, new Size(gaussianWidth, gaussianWidth), gaussianSigma);
				matValid = new Mat();
				Core.compare(mat, matStain2, matValid, Core.CMP_GE);
			}
				
			
			// Apply basic threshold to identify potential nucleus pixels
			Mat matThresh = new Mat();
			Core.compare(mat, new Scalar(threshold), matThresh, Core.CMP_GE);
			if (matValid != null)
				Core.min(matThresh, matValid, matThresh);
			
			// Do Difference of Gaussians, if required
			if (doDoG) {
				double sigma2 = gaussianSigma * 1.6;
				int gaussianWidth2 = (int)(Math.ceil(sigma2 * 3) * 2 + 1);
				Mat mat2 = new Mat();
				// Apply filter to the original
				Imgproc.GaussianBlur(matOrig, mat2, new Size(gaussianWidth2, gaussianWidth2), sigma2);
				Core.subtract(mat, mat2, mat);
			}
			
			// Apply max filter to help find maxima
			Mat matMax = new Mat(mat.size(), mat.type());
			Imgproc.dilate(mat, matMax, new Mat());

			// Apply potential maxima threshold by locating pixels where mat == matMax,
			// i.e. a pixel is equal to the maximum of its 8 neighbours
			// (Note: this doesn’t deal with points of inflection, but with 32-bit this is likely to be rare enough
			// not to be worth the considerably extra computational cost; may need to confirm there are no rounding errors)
			Mat matMaxima = new Mat();
			Core.compare(mat, matMax, matMaxima, Core.CMP_EQ);
			
			// Compute AND of two binary images
			// This finds the potential nucleus pixels that are also local maxima in the processed image
			Core.min(matThresh, matMaxima, matMaxima);
			
			
			/*
			 * Create objects
			 */
			
			// Create path objects from contours
			// This deals with the fact that maxima located within matMaxima (a binary image) aren’t necessarily
			// single pixels, but should be treated as belonging to the same cell		
			List<MatOfPoint> contours = new ArrayList<>();
			Imgproc.findContours(matMaxima, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
			ArrayList<qupath.lib.geom.Point2> points = new ArrayList<>();

			Shape shape = pathROI instanceof PathArea ? PathROIToolsAwt.getShape(pathROI) : null;
			Integer color = ColorTools.makeRGB(0, 255, 0);
			for (MatOfPoint contour : contours){

				// Create a polygon ROI
				points.clear();
				for (Point p : contour.toArray())
					points.add(new qupath.lib.geom.Point2(p.x * downsample + x, p.y * downsample + y));

				// Add new polygon if it is contained within the ROI
				ROI tempROI = null;
				if (points.size() == 1) {
					double radius = 6;
					qupath.lib.geom.Point2 p = points.get(0);
					if (shape != null && !shape.contains(p.getX(), p.getY())) {
						continue;
					}
					tempROI = new EllipseROI(p.getX()-radius, p.getY()-radius, radius*2, radius*2);
				}
				else
					tempROI = new PolygonROI(points);

				if (pathROI instanceof RectangleROI || PathObjectTools.containsROI(pathROI, tempROI)) {
					PathObject pathObject = new PathDetectionObject(tempROI);
					pathObject.setColorRGB(color);
					pathObjects.add(pathObject);
				}
			}
			System.out.println("Found " + pathObjects.size() + " contours");

			return pathObjects;
		}

		@Override
		public String getLastResultsDescription() {
			return "Detected " + pathObjects.size() + " cells";
		}

	}


	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList().
				addDoubleParameter("magnification", "Requested magnification", 5, null, "Magnification at which the detection should be run").
				addDoubleParameter("backgroundRadiusPixels", "Background radius", 5, "px", "Filter size to estimate background; should be > the largest nucleus radius").
				addDoubleParameter("backgroundRadiusMicrons", "Background radius", 10, GeneralTools.micrometerSymbol(), "Filter size to estimate background; should be > the largest nucleus radius").
				addDoubleParameter("gaussianSigmaPixels", "Gaussian sigma", 1, "px", "Smoothing filter uses to reduce spurious peaks").
				addDoubleParameter("gaussianSigmaMicrons", "Gaussian sigma", 2, GeneralTools.micrometerSymbol(), "Smoothing filter uses to reduce spurious peaks").
				addDoubleParameter("threshold", "Threshold", 0.1, "Hematoxylin intensity threshold").
				addBooleanParameter("doDoG", "Use Difference of Gaussians", true, "Apply Difference of Gaussians filter prior to detection - this tends to detect more nuclei, but may detect too many").
				addBooleanParameter("ensureMainStain", "Hematoxylin predominant", false, "Accept detection only if haematoxylin value is higher than that of the second deconvolved stain");
		boolean hasMicrons = imageData != null && imageData.getServer() != null && imageData.getServer().hasPixelSizeMicrons();
		params.getParameters().get("gaussianSigmaPixels").setHidden(hasMicrons);
		params.getParameters().get("gaussianSigmaMicrons").setHidden(!hasMicrons);
		params.getParameters().get("backgroundRadiusPixels").setHidden(hasMicrons);
		params.getParameters().get("backgroundRadiusMicrons").setHidden(!hasMicrons);
		return params;
	}

	@Override
	public String getName() {
		return "Fast cell counts";
	}

	@Override
	public String getDescription() {
		return "Perform a fast, low-resolution count of nuclei in a whole slide image using a peak-finding approach";
	}

	@Override
	public String getLastResultsDescription() {
		return cellCounter == null ? "" : cellCounter.getLastResultsDescription();
	}

//	@Override
//	protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
////		if (detector == null)
//			detector = new FastNucleusDetector();
//		tasks.add(createRunnableTask(detector, getCurrentParameterList(imageData), imageData, parentObject));
//	}

	@Override
	protected double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
		// TODO Auto-generated method stub
		return 2;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		cellCounter = new FastCellCounter();
		return cellCounter;
	}

	/**
	 * No overlap... aim is speed.
	 */
	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 0;
	}


}