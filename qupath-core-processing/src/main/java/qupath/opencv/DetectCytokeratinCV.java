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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.DetectionPluginTools;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

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
		
		private String lastResultsDescription = null;


		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) throws IOException {
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
			PixelCalibration cal = imageData.getServer().getPixelCalibration();
			double pixelSize = cal.getAveragedPixelSizeMicrons() * downsample;
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

			float[] pxHematoxylin = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_1, null, stains);
			float[] pxDAB = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_2, null, stains);

//			float[] pxHematoxylin = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 0, null);
//			float[] pxDAB = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 1, null);

			// Create OpenCV Mats
			Mat matOD = new Mat(h, w, CV_32FC1);
			Mat matDAB = new Mat(h, w, CV_32FC1);
			OpenCVTools.putPixelsFloat(matOD, pxHematoxylin);
			OpenCVTools.putPixelsFloat(matDAB, pxDAB);

			// Add the DAB to the haematoxylin values
			add(matOD, matDAB, matOD);

			// If the third channel isn't a residual channel, add it too
			if (!stains.getStain(3).isResidual()) {
				float[] pxThird = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_3, null, stains);
//				float[] pxThird = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 2, null);
				Mat matThird = new Mat(h, w, CV_32FC1);
				OpenCVTools.putPixelsFloat(matThird, pxThird);
				add(matOD, matThird, matOD);
			}
			
			// Apply Gaussian filter
			Size gaussSize = new Size();
			opencv_imgproc.GaussianBlur(matOD, matOD, gaussSize, gaussianSigma);
			opencv_imgproc.GaussianBlur(matDAB, matDAB, gaussSize, gaussianSigma);

			// Threshold
			Mat matBinaryTissue = new Mat();
			if (thresholdTissue > 0)
				compare(matOD, new Mat(1, 1, CV_32FC1, Scalar.all(thresholdTissue)), matBinaryTissue, CMP_GT);
			Mat matBinaryDAB = new Mat();
			if (thresholdDAB > 0)
				compare(matDAB, new Mat(1, 1, CV_32FC1, Scalar.all(thresholdDAB)), matBinaryDAB, CMP_GT);

			// Ensure everything in the DAB image is removed from the tissue image
			if (!matBinaryTissue.empty() && !matBinaryDAB.empty())
				subtract(matBinaryTissue, matBinaryDAB, matBinaryTissue);

			// Cleanup as required
			if (separationDiameter > 0 && !matBinaryTissue.empty() && !matBinaryDAB.empty()) {
				Mat strel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, new Size(separationDiameter, separationDiameter));
				opencv_imgproc.erode(matBinaryTissue, matBinaryTissue, strel);
				opencv_imgproc.erode(matBinaryDAB, matBinaryDAB, strel);
			}





			Area areaTissue = getArea(matBinaryTissue);
			Area areaDAB = getArea(matBinaryDAB);
			AffineTransform transform = AffineTransform.getTranslateInstance(request.getX(), request.getY());
			transform.scale(downsample, downsample);

			Area areaROI = null;
			if (pathROI != null && !(pathROI instanceof RectangleROI)) {
				areaROI = RoiTools.getArea(pathROI);
			}


			double simplifyAmount = downsample * 1.5; // May want to revise this...
			if (areaTissue != null) {
				areaTissue = areaTissue.createTransformedArea(transform);
				if (areaROI != null)
					areaTissue.intersect(areaROI);

				if (!areaTissue.isEmpty()) {
					ROI roiTissue = RoiTools.getShapeROI(areaTissue, request.getPlane());
					roiTissue = ShapeSimplifier.simplifyShape(roiTissue, simplifyAmount);
					pathObjects.add(PathObjects.createAnnotationObject(roiTissue, PathClassFactory.getPathClass(StandardPathClasses.STROMA)));
				}
			}
			if (areaDAB != null) {
				areaDAB = areaDAB.createTransformedArea(transform);
				if (areaROI != null)
					areaDAB.intersect(areaROI);

				if (!areaDAB.isEmpty()) {
					ROI roiDAB = RoiTools.getShapeROI(areaDAB, request.getPlane());
					roiDAB = ShapeSimplifier.simplifyShape(roiDAB, simplifyAmount);
					pathObjects.add(PathObjects.createAnnotationObject(roiDAB, PathClassFactory.getPathClass(StandardPathClasses.TUMOR)));
				}
			}


			matOD.release();
			matDAB.release();
			matBinaryDAB.release();
			matBinaryTissue.release();
			
			lastResultsDescription = String.format("Detected %s", pathObjects.toString());

			return pathObjects;
		}



		@Override
		public String getLastResultsDescription() {
			return lastResultsDescription;
		}


	}



	/**
	 * Get an Area object corresponding to contours in a binary image from OpenCV.
	 * @param mat
	 * @return
	 */
	private static Area getArea(final Mat mat) {
		if (mat.empty())
			return null;

		// Identify all contours
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(mat, contours, hierarchy, opencv_imgproc.RETR_TREE, opencv_imgproc.CHAIN_APPROX_SIMPLE);
		if (contours.empty()) {
			hierarchy.release();
			return null;
		}

		Area area = new Area();
		updateArea(contours, hierarchy, area, 0, 0);

		hierarchy.release();

		return area;
	}



	private static void updateArea(final MatVector contours, final Mat hierarchy, final Area area, int row, int depth) {
		IntIndexer indexer = hierarchy.createIndexer();
		while (row >= 0) {
			int[] data = new int[4];
			// TODO: Check indexing after switch to JavaCPP!!!
			indexer.get(0, row, data);
//			hierarchy.get(0, row, data);

			Mat contour = contours.get(row);

			// Don't include isolated pixels - otherwise add or remove, as required
			if (contour.rows() > 2) {
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



	private static Path2D getContour(Mat contour) {
		// Create a path for the contour
		Path2D path = new Path2D.Float();
		boolean firstPoint = true;
		Indexer indexer = contour.createIndexer();
		for (int r = 0; r < contour.rows(); r++) {
			double px = indexer.getDouble(r, 0L, 0L);
			double py = indexer.getDouble(r, 0L, 1L);
			if (firstPoint) {
				path.moveTo(px, py);
				firstPoint = false;
			} else {
				path.lineTo(px, py);
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


