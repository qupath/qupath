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


import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolution;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.PathClasses;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.DetectionPluginTools;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.ShapeSimplifierAwt;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;

/**
 * Simple command to detect tumor regions stained with cytokeratin.
 * 
 * @author Pete Bankhead
 *
 */
public class DetectCytokeratinCV extends AbstractDetectionPlugin<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(DetectCytokeratinCV.class);
	
	transient private CytokeratinDetector detector;
	
	
	static class CytokeratinDetector implements ObjectDetector<BufferedImage> {
	
		// TODO: REQUEST DOWNSAMPLE IN PLUGINS
		private List< PathObject> pathObjects = new ArrayList<>();
		
		transient private RegionRequest lastRequest = null;
		transient private BufferedImage img = null;
		
		
		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			// Reset any detected objects
			pathObjects.clear();
			
			
			// Parse parameters
			double downsample = Math.max(1, params.getIntParameterValue("downsampleFactor"));
			
//			.addIntParameter("downsampleFactor", "Downsample factor", 2, "", 1, 8, "Amount to downsample image prior to detection - higher values lead to smaller images (and faster but less accurate processing)")
//			.addDoubleParameter("gaussianSigmaMicrons", "Gaussian sigma", 5, GeneralTools.micrometerSymbol(), "Gaussian filter size - higher values give a smoother (less-detailed) result")
//			.addDoubleParameter("thresholdTissue", "Tissue threshold", 0.1, "OD units", "Threshold to use for tissue detection (used to create stroma annotation)")
//			.addDoubleParameter("thresholdDAB", "DAB threshold", 0.1, "OD units", "Threshold to use for cytokeratin detection (used to create tumour annotation)")
//			.addIntParameter("separationDistanceMicrons", "Separation distance", 1, GeneralTools.micrometerSymbol(), "Approximate space to create between tumour & stroma classes when they occur side-by-side");

			
			double thresholdTissue = params.getDoubleParameterValue("thresholdTissue");
			double thresholdDAB = params.getDoubleParameterValue("thresholdDAB");
			double gaussianSigmaMicrons = params.getDoubleParameterValue("gaussianSigmaMicrons");
			double separationDistanceMicrons = params.getDoubleParameterValue("separationDistanceMicrons");
			
			// Derive more useful values
			double pixelSize = imageData.getServer().getAveragedPixelSizeMicrons() * downsample;
			double gaussianSigma = gaussianSigmaMicrons / pixelSize;
			int separationDiameter = 0;
			if (separationDistanceMicrons > 0) {
				separationDiameter = (int)(separationDistanceMicrons / pixelSize * 2 + .5);
				// Ensure we have an odd value or zero (will be used for filter size if non-zero)
				if (separationDiameter > 0 && separationDiameter % 2 == 0)
					separationDiameter++;
			}
			
			// Read the image, if necessary
			RegionRequest request = RegionRequest.createInstance(imageData.getServerPath(), downsample, pathROI);
			if (img == null || !request.equals(lastRequest)) {
				img = imageData.getServer().readBufferedImage(request);
				lastRequest = request;
			}
			
			int w = img.getWidth();
			int h = img.getHeight();
			
			// Extract the color deconvolved channels
			// TODO: Support alternative stain vectors
			if (!imageData.isBrightfield()) {
				logger.error("Only brightfield images are supported!");
				return Collections.emptyList();
			}
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			// Since we relaxed the strict rule this needs to be H-DAB, at least print a warning if it is not
			if (!stains.isH_DAB()) {
				logger.warn("{} was originally designed for H-DAB staining - here, {} will be used in place of hematoxylin and {} in place of DAB",
						this.getClass().getSimpleName(), stains.getStain(1).getName(), stains.getStain(2).getName());
			}
			int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
			
			float[] pxHematoxylin = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 0, null);
			float[] pxDAB = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 1, null);
			
			// Create OpenCV Mats
			Mat matOD = new Mat(h, w, CvType.CV_32FC1);
			Mat matDAB = new Mat(h, w, CvType.CV_32FC1);
			// It seems OpenCV doesn't use the array directly, so no need to copy...
			matOD.put(0, 0, pxHematoxylin);
			matDAB.put(0, 0, pxDAB);
			
			// Add the DAB to the hematoxylin values
			Core.add(matOD, matDAB, matOD);
			
			// If the third channel isn't a residual channel, add it too
			if (!stains.getStain(3).isResidual()) {
				float[] pxThird = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 2, null);
				Mat matThird = new Mat(h, w, CvType.CV_32FC1);
				matThird.put(0, 0, pxThird);
				Core.add(matOD, matThird, matOD);
			}
			
			// Apply Gaussian filter
			Size gaussSize = new Size();
			Imgproc.GaussianBlur(matOD, matOD, gaussSize, gaussianSigma);
			Imgproc.GaussianBlur(matDAB, matDAB, gaussSize, gaussianSigma);
			
			// Threshold
			Mat matBinaryTissue = new Mat();
			if (thresholdTissue > 0)
				Core.compare(matOD, new Scalar(thresholdTissue), matBinaryTissue, Core.CMP_GT);
			Mat matBinaryDAB = new Mat();
			if (thresholdDAB > 0)
				Core.compare(matDAB, new Scalar(thresholdDAB), matBinaryDAB, Core.CMP_GT);
			
			// Ensure everything in the DAB image is removed from the tissue image
			if (!matBinaryTissue.empty() && !matBinaryDAB.empty())
				Core.subtract(matBinaryTissue, matBinaryDAB, matBinaryTissue);
			
			// Cleanup as required
			if (separationDiameter > 0 && !matBinaryTissue.empty() && !matBinaryDAB.empty()) {
				Mat strel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(separationDiameter, separationDiameter));
				Imgproc.erode(matBinaryTissue, matBinaryTissue, strel);
				Imgproc.erode(matBinaryDAB, matBinaryDAB, strel);
			}
			
			
			
			
			
			Area areaTissue = getArea(matBinaryTissue);
			Area areaDAB = getArea(matBinaryDAB);
			AffineTransform transform = AffineTransform.getTranslateInstance(request.getX(), request.getY());
			transform.scale(downsample, downsample);
			
			Area areaROI = null;
			if (pathROI != null && !(pathROI instanceof RectangleROI)) {
				areaROI = PathROIToolsAwt.getArea(pathROI);
			}

			
			double simplifyAmount = downsample * 1.5; // May want to revise this...
			if (areaTissue != null) {
				areaTissue = areaTissue.createTransformedArea(transform);
				if (areaROI != null)
					areaTissue.intersect(areaROI);
				
				if (!areaTissue.isEmpty()) {
					PathShape roiTissue = PathROIToolsAwt.getShapeROI(areaTissue, -1, request.getZ(), request.getT());
					roiTissue = ShapeSimplifierAwt.simplifyShape(roiTissue, simplifyAmount);
					pathObjects.add(new PathAnnotationObject(roiTissue, PathClassFactory.getDefaultPathClass(PathClasses.STROMA)));
				}
			}
			if (areaDAB != null) {
				areaDAB = areaDAB.createTransformedArea(transform);
				if (areaROI != null)
					areaDAB.intersect(areaROI);
				
				if (!areaDAB.isEmpty()) {
					PathShape roiDAB = PathROIToolsAwt.getShapeROI(areaDAB, -1, request.getZ(), request.getT());
					roiDAB = ShapeSimplifierAwt.simplifyShape(roiDAB, simplifyAmount);
					pathObjects.add(new PathAnnotationObject(roiDAB, PathClassFactory.getDefaultPathClass(PathClasses.TUMOR)));
				}
			}
			
			
			matOD.release();
			matDAB.release();
			matBinaryDAB.release();
			matBinaryTissue.release();
			
			return pathObjects;
		}
		
		
		
		@Override
		public String getLastResultsDescription() {
			return null;
		}

		
	}
	
	
	
	
	public static Area getArea(final Mat mat) {
		if (mat.empty())
			return null;
		
		// Identify all contours
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();
		Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
		if (contours.isEmpty()) {
			hierarchy.release();
			return null;
		}
		
		Area area = new Area();
		updateArea(contours, hierarchy, area, 0, 0);
		
		hierarchy.release();
		
		return area;
	}
	
	
	
	public static void updateArea(final List<MatOfPoint> contours, final Mat hierarchy, final Area area, int row, int depth) {
		while (row >= 0) {
			int[] data = new int[4];
			hierarchy.get(0, row, data);
			
			MatOfPoint contour = contours.get(row);
			
			// Don't include isolated pixels - otherwise add or remove, as required
			if (contour.height() > 2) {
				Path2D path = getContour(contour);
				if (depth % 2 == 0)
					area.add(new Area(path));
				else
					area.subtract(new Area(path));
			}
			
			// Deal with any sub-contours
			if (data[2] >= 0)
				updateArea(contours, hierarchy, area, data[2], depth+1);
			
			// Move to next contour in this hierarchy level
			row = data[0];
		}
	}
	
	
	
	public static Path2D getContour(MatOfPoint contour) {
		// Create a path for the contour
        Path2D path = new Path2D.Float();
        boolean firstPoint = true;
        for (org.opencv.core.Point p : contour.toArray()) {
        	if (firstPoint) {
        		path.moveTo(p.x, p.y);
        		firstPoint = false;
        	} else {
        		path.lineTo(p.x, p.y);
        	}
        }
        return path;
	}
	
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		String stain2Name = imageData.getColorDeconvolutionStains() == null ? "DAB" : imageData.getColorDeconvolutionStains().getStain(2).getName();
		String stain2Prompt = stain2Name + " threshold";
		ParameterList params = new ParameterList()
				.addIntParameter("downsampleFactor", "Downsample factor", 4, "", 1, 32, "Amount to downsample image prior to detection - higher values lead to smaller images (and faster but less accurate processing)")
				.addDoubleParameter("gaussianSigmaMicrons", "Gaussian sigma", 5, GeneralTools.micrometerSymbol(), "Gaussian filter size - higher values give a smoother (less-detailed) result")
				.addDoubleParameter("thresholdTissue", "Tissue threshold", 0.1, "OD units", "Threshold to use for tissue detection (used to create stroma annotation) - if zero, no stroma annotation is created")
				.addDoubleParameter("thresholdDAB", stain2Prompt, 0.25, "OD units", "Threshold to use for cytokeratin detection (used to create tumor annotation) - if zero, no tumor annotation is created")
				.addDoubleParameter("separationDistanceMicrons", "Separation distance", 0.5, GeneralTools.micrometerSymbol(), "Approximate space to create between tumour & stroma classes when they occur side-by-side");
		
//		double thresholdTissue = 0.1;
//		double thresholdDAB = 0.1;
//		double gaussianSigmaMicrons = 5;
//		int separationRadius = 1;

		// TODO: Support parameters properly!
//		
//		if (imageData.getServer().hasPixelSizeMicrons()) {
//			String um = GeneralTools.micrometerSymbol();
//			params.addDoubleParameter("medianRadius", "Median radius", 1, um).
//				addDoubleParameter("gaussianSigma", "Gaussian sigma", 1.5, um).
//				addDoubleParameter("openingRadius", "Opening radius", 8, um).
//				addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0).
//				addDoubleParameter("minArea", "Minimum area", 25, um+"^2");
//		} else {
//			params.addDoubleParameter("medianRadius", "Median radius", 1, "px").
//					addDoubleParameter("gaussianSigma", "Gaussian sigma", 2, "px").
//					addDoubleParameter("openingRadius", "Opening radius", 20, "px").
//					addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0).
//					addDoubleParameter("minArea", "Minimum area", 100, "px^2");
//		}
//		params.addBooleanParameter("splitShape", "Split by shape", true);			
		return params;
	}

	@Override
	public String getName() {
		return "Cytokeratin annotation creation (TMA, IHC)";
	}
	
	@Override
	public String getDescription() {
		return "Create tumor/non-tumor annotations by thresholding a cytokeratin staining";
	}

	@Override
	public String getLastResultsDescription() {
		return detector == null ? "" : detector.getLastResultsDescription();
	}
	
	@Override
	protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
//		if (detector == null)
			detector = new CytokeratinDetector();
		tasks.add(DetectionPluginTools.createRunnableTask(detector, getParameterList(imageData), imageData, parentObject));
	}

	
}


