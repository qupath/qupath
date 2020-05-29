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

package qupath.opencv;


import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RotatedRect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.Size2f;
import org.bytedeco.javacpp.indexer.Indexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorDeconvMatrix3x3;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;
import qupath.opencv.tools.ProcessingCV;

/**
 * Alternative (incomplete) attempt at nucleus segmentation.
 * <p>
 * It's reasonably fast... but not particularly good.
 * 
 * @author Pete Bankhead
 *
 */
public class WatershedNucleiCV extends AbstractTileableDetectionPlugin<BufferedImage> {

	private static Logger logger = LoggerFactory.getLogger(WatershedNucleiCV.class);

	transient private WatershedNuclei detector;


	class WatershedNuclei implements ObjectDetector<BufferedImage> {

		// TODO: REQUEST DOWNSAMPLE IN PLUGINS
		private List< PathObject> pathObjects = new ArrayList<>();


		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) throws IOException {
			// Reset any detected objects
			pathObjects.clear();

			boolean splitShape = params.getBooleanParameterValue("splitShape");
			//			double downsample = params.getIntParameterValue("downsampleFactor");

			PixelCalibration cal = imageData.getServer().getPixelCalibration();
			double downsample = cal.hasPixelSizeMicrons() ? 
					getPreferredPixelSizeMicrons(imageData, params) / cal.getAveragedPixelSizeMicrons() :
						1;
					downsample = Math.max(downsample, 1);

					double threshold = params.getDoubleParameterValue("threshold");
					// Extract size-dependent parameters
					int medianRadius, openingRadius;
					double gaussianSigma, minArea;
					ImageServer<BufferedImage> server = imageData.getServer();
					if (cal.hasPixelSizeMicrons()) {
						double pixelSize = 0.5 * downsample * (cal.getPixelHeightMicrons() + cal.getPixelWidthMicrons());
						medianRadius = (int)(params.getDoubleParameterValue("medianRadius") / pixelSize + .5);
						gaussianSigma = params.getDoubleParameterValue("gaussianSigma") / pixelSize;
						openingRadius = (int)(params.getDoubleParameterValue("openingRadius") / pixelSize + .5);
						minArea = params.getDoubleParameterValue("minArea") / (pixelSize * pixelSize);
						logger.trace(String.format("Sizes: %d, %.2f, %d, %.2f", medianRadius, gaussianSigma, openingRadius, minArea));
					} else {
						medianRadius = (int)(params.getDoubleParameterValue("medianRadius") + .5);
						gaussianSigma = params.getDoubleParameterValue("gaussianSigma");
						openingRadius = (int)(params.getDoubleParameterValue("openingRadius") + .5);
						minArea = params.getDoubleParameterValue("minArea");			
					}

					// TODO: Avoid hard-coding downsample
					Rectangle bounds = AwtTools.getBounds(pathROI);
					double x = bounds.getX();
					double y = bounds.getY();

					//		logger.info("BOUNDS: " + bounds);

					// Read the buffered image
					BufferedImage img = server.readBufferedImage(RegionRequest.createInstance(server.getPath(), downsample, pathROI));

					// Extract the color deconvolved channels
					// TODO: Support alternative stain vectors
					ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
					boolean isH_DAB = stains.isH_DAB();
					float[][] pxDeconvolved = colorDeconvolve(img, stains.getStain(1).getArray(), stains.getStain(2).getArray(), null, 2);
					float[] pxHematoxylin = pxDeconvolved[0];
					float[] pxDAB = isH_DAB ? pxDeconvolved[1] : null;

					// Convert to OpenCV Mat
					int width = img.getWidth();
					int height = img.getHeight();
					Mat mat = new Mat(height, width, CV_32FC1);

					// It seems OpenCV doesn't use the array directly, so no need to copy...
					OpenCVTools.putPixelsFloat(mat, pxHematoxylin);

					Mat matBackground = new Mat();

					opencv_imgproc.medianBlur(mat, mat, 1);
					opencv_imgproc.GaussianBlur(mat, mat, new Size(5, 5), 0.75);
					opencv_imgproc.morphologyEx(mat, matBackground, opencv_imgproc.MORPH_CLOSE, OpenCVTools.getCircularStructuringElement(1));
					ProcessingCV.morphologicalReconstruction(mat, matBackground);

					// Apply opening by reconstruction & subtraction to reduce background
					opencv_imgproc.morphologyEx(mat, matBackground, opencv_imgproc.MORPH_OPEN, OpenCVTools.getCircularStructuringElement(openingRadius));
					ProcessingCV.morphologicalReconstruction(matBackground, mat);
					subtract(mat, matBackground, mat);

					// Apply Gaussian filter
					int gaussianWidth = (int)(Math.ceil(gaussianSigma * 3) * 2 + 1);
					opencv_imgproc.GaussianBlur(mat, mat, new Size(gaussianWidth, gaussianWidth), gaussianSigma);

					// Apply Laplacian filter
					Mat matLoG = matBackground;
					opencv_imgproc.Laplacian(mat, matLoG, mat.depth(), 1, -1, 0, BORDER_DEFAULT);

					// Threshold
					Mat matBinaryLoG = new Mat();
					compare(matLoG, new Mat(1, 1, CV_32FC1, Scalar.ZERO), matBinaryLoG, CMP_GT);

					// Watershed transform
					Mat matBinary = matBinaryLoG.clone();
					OpenCVTools.watershedIntensitySplit(matBinary, matLoG, 0, 1);

					// Identify all contours
					MatVector contours = new MatVector();
					opencv_imgproc.findContours(matBinary, contours, new Mat(), opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

					// Create a labelled image for each contour
					Mat matLabels = new Mat(matBinary.size(), CV_32F, Scalar.ZERO);
					List<RunningStatistics> statsList = new ArrayList<>();
					int label = 0;
					Point offset = new Point(0, 0);
					for (int c = 0; c < contours.size(); c++) {
						label++;
						opencv_imgproc.drawContours(matLabels, contours, c, Scalar.all(label), -1, opencv_imgproc.LINE_8, null, Integer.MAX_VALUE, offset);
						statsList.add(new RunningStatistics());
					}
					// Compute mean for each contour, keep those that are sufficiently intense
					float[] labels = new float[(int)matLabels.total()];
					OpenCVTools.extractPixels(matLabels, labels);
					computeRunningStatistics(pxHematoxylin, labels, statsList);
					int ind = 0;
					Scalar color = Scalar.WHITE;
					matBinary.put(Scalar.ZERO);
					for (RunningStatistics stats : statsList) {
						if (stats.getMean() > threshold) {
							opencv_imgproc.drawContours(matBinary, contours, ind, color, -1, opencv_imgproc.LINE_8, null, Integer.MAX_VALUE, offset);				
						}
						ind++;
					}

					// Dilate binary image & extract remaining contours
					opencv_imgproc.dilate(matBinary, matBinary, opencv_imgproc.getStructuringElement(opencv_imgproc.CV_SHAPE_RECT, new Size(3, 3)));
					min(matBinary, matBinaryLoG, matBinary);

					OpenCVTools.fillSmallHoles(matBinary, minArea*4);

					// Split using distance transform, if necessary
					if (splitShape)
						watershedDistanceTransformSplit(matBinary, openingRadius/4);

					// Create path objects from contours		
					contours = new MatVector();
					Mat hierarchy = new Mat();
					opencv_imgproc.findContours(matBinary, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);
					ArrayList<Point2> points = new ArrayList<>();

					// Create label image
					matLabels.put(Scalar.ZERO);

					// Update the labels to correspond with the contours, and compute statistics
					label = 0;
					List<RunningStatistics> statsHematoxylinList = new ArrayList<>((int)contours.size());
					List<RunningStatistics> statsDABList = new ArrayList<>((int)contours.size());
					for (int c = 0; c < contours.size(); c++){
						Mat contour = contours.get(c);

						// Discard single pixels / lines
						if (contour.rows() <= 2)
							continue;

						// Simplify the contour slightly
						Mat contourApprox = new Mat();
						opencv_imgproc.approxPolyDP(contour, contourApprox, 0.5, true);
						contour = contourApprox;
						contours.put(c, contour);

						// Create a polygon ROI
						points.clear();
						Indexer indexerContour = contour.createIndexer();
						for (int r = 0; r < contour.rows(); r++) {
							double px = indexerContour.getDouble(r, 0L, 0L);
							double py = indexerContour.getDouble(r, 0L, 1L);
							points.add(new Point2(px * downsample + x, py * downsample + y));
						}

						// Add new polygon if it is contained within the ROI & measurable
						PolygonROI pathPolygon = ROIs.createPolygonROI(points, ImagePlane.getPlaneWithChannel(pathROI));
						if (!(pathPolygon.getArea() >= minArea)) {
							// Don't do a simpler < because we also want to discard the region if the area couldn't be measured (although this is unlikely)
							continue;
						}

						//	        logger.info("Area comparison: " + opencv_imgproc.contourArea(contour) + ",\t" + (pathPolygon.getArea() / downsample / downsample));
						//	        Mat matSmall = new Mat();
						if (pathROI instanceof RectangleROI || PathObjectTools.containsROI(pathROI, pathPolygon)) {
							MeasurementList measurementList = MeasurementListFactory.createMeasurementList(20, MeasurementList.MeasurementListType.FLOAT);
							PathObject pathObject = PathObjects.createDetectionObject(pathPolygon, null, measurementList);

							measurementList.addMeasurement("Area", pathPolygon.getArea());
							measurementList.addMeasurement("Perimeter", pathPolygon.getLength());
							measurementList.addMeasurement("Circularity", RoiTools.getCircularity(pathPolygon));
							measurementList.addMeasurement("Solidity", pathPolygon.getSolidity());

							// I am making an assumption regarding square pixels here...
							RotatedRect rrect = opencv_imgproc.minAreaRect(contour);
							Size2f size = rrect.size();
							measurementList.addMeasurement("Min axis", Math.min(size.width(), size.height()) * downsample);
							measurementList.addMeasurement("Max axis", Math.max(size.width(), size.height()) * downsample);

							// Store the object
							pathObjects.add(pathObject);

							// Create a statistics object & paint a label in preparation for intensity stat computations later
							label++;
							statsHematoxylinList.add(new RunningStatistics());
							if (pxDAB != null)
								statsDABList.add(new RunningStatistics());
							opencv_imgproc.drawContours(matLabels, contours, c, Scalar.all(label), -1, opencv_imgproc.LINE_8, null, Integer.MAX_VALUE, offset);
						}
					}

					// Compute intensity statistics
					OpenCVTools.extractPixels(matLabels, labels);
					computeRunningStatistics(pxHematoxylin, labels, statsHematoxylinList);
					if (pxDAB != null)
						computeRunningStatistics(pxDAB, labels, statsDABList);
					ind = 0;
					for (PathObject pathObject : pathObjects) {
						MeasurementList measurementList = pathObject.getMeasurementList();
						RunningStatistics statsHaem = statsHematoxylinList.get(ind);
						//    	pathObject.addMeasurement("Area (px)", statsHaem.nPixels() * downsample * downsample);
						measurementList.addMeasurement("Hematoxylin mean", statsHaem.getMean());
						measurementList.addMeasurement("Hematoxylin std dev", statsHaem.getStdDev());
						measurementList.addMeasurement("Hematoxylin min", statsHaem.getMin());
						measurementList.addMeasurement("Hematoxylin max", statsHaem.getMax());
						measurementList.addMeasurement("Hematoxylin range", statsHaem.getRange());

						if (pxDAB != null) {
							RunningStatistics statsDAB = statsDABList.get(ind);
							measurementList.addMeasurement("DAB mean", statsDAB.getMean());
							measurementList.addMeasurement("DAB std dev", statsDAB.getStdDev());
							measurementList.addMeasurement("DAB min", statsDAB.getMin());
							measurementList.addMeasurement("DAB max", statsDAB.getMax());
							measurementList.addMeasurement("DAB range", statsDAB.getRange());
						}

						measurementList.close();
						ind++;
					}
					logger.info("Found " + pathObjects.size() + " contours");

					return pathObjects;
		}




		@Override
		public String getLastResultsDescription() {
			return String.format("Detected %d nuclei", pathObjects.size());
		}


	}


	private static void watershedDistanceTransformSplit(Mat matBinary, int maxFilterRadius) {
		Mat matWatershedSeedsBinary;
		
		// Create a background mask
		Mat matBackground = new Mat();
		compare(matBinary, new Mat(1, 1, CV_32FC1, Scalar.WHITE), matBackground, CMP_NE);

		// Separate by shape using the watershed transform
		Mat matDistanceTransform = new Mat();
		opencv_imgproc.distanceTransform(matBinary, matDistanceTransform, opencv_imgproc.CV_DIST_L2, opencv_imgproc.CV_DIST_MASK_PRECISE);
		// Find local maxima
		matWatershedSeedsBinary = new Mat();
		opencv_imgproc.dilate(matDistanceTransform, matWatershedSeedsBinary, OpenCVTools.getCircularStructuringElement(maxFilterRadius));
		compare(matDistanceTransform, matWatershedSeedsBinary, matWatershedSeedsBinary, CMP_EQ);
		matWatershedSeedsBinary.setTo(new Mat(1, 1, matWatershedSeedsBinary.type(), Scalar.ZERO), matBackground);
		// Dilate slightly to merge nearby maxima
		opencv_imgproc.dilate(matWatershedSeedsBinary, matWatershedSeedsBinary, OpenCVTools.getCircularStructuringElement(2));

		// Create labels for watershed
		Mat matLabels = new Mat(matDistanceTransform.size(), CV_32F, Scalar.ZERO);
		OpenCVTools.labelImage(matWatershedSeedsBinary, matLabels, opencv_imgproc.RETR_CCOMP);

		// Remove everything outside the thresholded region
		matLabels.setTo(new Mat(1, 1, matLabels.type(), Scalar.ZERO), matBackground);

		// Do watershed
		// 8-connectivity is essential for the watershed lines to be preserved - otherwise OpenCV's findContours could not be used
		ProcessingCV.doWatershed(matDistanceTransform, matLabels, 0.1, true);

		// Update the binary image to remove the watershed lines
		multiply(matBinary, matLabels, matBinary, 1, matBinary.type());
	}

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList();
		params.addDoubleParameter("preferredMicrons", "Preferred pixel size", 0.5, GeneralTools.micrometerSymbol(),
				"Preferred image resolution for detection (higher values mean lower resolution)");
		//				addIntParameter("downsampleFactor", "Downsample factor", 2, "", 1, 4);

		if (imageData.getServer().getPixelCalibration().hasPixelSizeMicrons()) {
			String um = GeneralTools.micrometerSymbol();
			params.addDoubleParameter("medianRadius", "Median radius", 1, um, "Median filter radius").
			addDoubleParameter("gaussianSigma", "Gaussian sigma", 1.5, um, "Gaussian filter sigma").
			addDoubleParameter("openingRadius", "Opening radius", 8, um, "Morphological opening filter radius").
			addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0, "Intensity threshold").
			addDoubleParameter("minArea", "Minimum area", 25, um+"^2", "Minimum area threshold");
		} else {
			params.setHiddenParameters(true, "preferredMicrons");
			params.addDoubleParameter("medianRadius", "Median radius", 1, "px", "Median filter radius").
			addDoubleParameter("gaussianSigma", "Gaussian sigma", 2, "px", "Gaussian filter sigma").
			addDoubleParameter("openingRadius", "Opening radius", 20, "px", "Morphological opening filter radius").
			addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0, "Intensity threshold").
			addDoubleParameter("minArea", "Minimum area", 100, "px^2", "Minimum area threshold");
		}
		params.addBooleanParameter("splitShape", "Split by shape", true);			
		return params;
	}

	@Override
	public String getName() {
		return "OpenCV nucleus experiment";
	}

	private RunningStatistics computeRunningStatistics(float[] pxIntensities, byte[] pxMask, int width, Rect bounds) {
		RunningStatistics stats = new RunningStatistics();
		for (int i = 0; i < pxMask.length; i++) {
			if (pxMask[i] == 0)
				continue;
			// Compute the image index
			int x = i % bounds.width() + bounds.x();
			int y = i % bounds.width() + bounds.y();
			// Add the value
			stats.addValue(pxIntensities[y * width + x]);
		}
		return stats;
	}



	@Override
	public String getLastResultsDescription() {
		return detector == null ? "" : detector.getLastResultsDescription();
	}

	@Override
	public String getDescription() {
		return "Alternative nucleus detection";
	}



	// TODO: If this ever becomes important, switch to using the QuPath core implementation instead of this one
	@Deprecated
	private static float[][] colorDeconvolve(BufferedImage img, double[] stain1, double[] stain2, double[] stain3, int nStains) {
		// TODO: Precompute the default matrix inversion
		if (stain3 == null)
			stain3 = StainVector.cross3(stain1, stain2);
		double[][] stainMat = new double[][]{stain1, stain2, stain3};
		ColorDeconvMatrix3x3 mat3x3 = new ColorDeconvMatrix3x3(stainMat);
		double[][] matInv = mat3x3.inverse();
		double[] stain1Inv = matInv[0];
		double[] stain2Inv = matInv[1];
		double[] stain3Inv = matInv[2];

		// Extract the buffered image pixels
		int[] buf = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
		// Preallocate the output
		float[][] output = new float[nStains][buf.length];

		// Apply color deconvolution
		double[] od_lut = ColorDeconvolutionHelper.makeODLUT(255, 256);
		for (int i = 0; i < buf.length; i++) {
			int c = buf[i];
			// Extract RGB values & convert to optical densities using a lookup table
			double r = od_lut[(c & 0xff0000) >> 16];
			double g = od_lut[(c & 0xff00) >> 8];
			double b = od_lut[c & 0xff];
			// Apply deconvolution & store the results
			for (int s = 0; s < nStains; s++) {
				output[s][i] = (float)(r * stain1Inv[s] + g * stain2Inv[s] + b * stain3Inv[s]);
			}
		}
		return output;
	}

	@Override
	protected double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
		PixelCalibration cal = imageData.getServer().getPixelCalibration();
		if (cal.hasPixelSizeMicrons())
			return Math.max(params.getDoubleParameterValue("preferredMicrons"), cal.getAveragedPixelSizeMicrons());
		return 0.5;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		return new WatershedNuclei();
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 50;
	}



	private static void computeRunningStatistics(float[] pxIntensities, float[] pxLabels, List<RunningStatistics> statsList) {
		float lastLabel = Float.NaN;
		int nLabels = statsList.size();
		RunningStatistics stats = null;
		for (int i = 0; i < pxIntensities.length; i++) {
			float label = pxLabels[i];
			if (label == 0 || label > nLabels)
				continue;
			// Get a new statistics object if necessary
			if (label != lastLabel) {
				stats = statsList.get((int)label-1);
				lastLabel = label;
			}
			// Add the value
			stats.addValue(pxIntensities[i]);
		}
	}

}