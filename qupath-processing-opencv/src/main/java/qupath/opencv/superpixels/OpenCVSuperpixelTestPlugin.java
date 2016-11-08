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

package qupath.opencv.superpixels;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.processing.OpenCVTools;

/**
 * A simple superpixel-generating command based upon applying ImageJ's watershed transform to the
 * absolute values of a Difference-of-Gaussians filtered image.
 * 
 * This provides tile objects that generally correspond to regions containing reasonably similar 
 * intensities or textures, which might then be classified.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVSuperpixelTestPlugin extends AbstractTileableDetectionPlugin<BufferedImage> {

	@Override
	public String getName() {
		return "DoG superpixel creator";
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected double getPreferredPixelSizeMicrons(final ImageData<BufferedImage> imageData, final ParameterList params) {
		double pixelSize = params.getDoubleParameterValue("downsampleFactor");
		if (imageData != null && imageData.getServer().hasPixelSizeMicrons())
			pixelSize *= imageData.getServer().getAveragedPixelSizeMicrons();
		return pixelSize;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(final ImageData<BufferedImage> imageData, final ParameterList params) {
		return new DoGSuperpixelDetector();
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 0;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList().
				addDoubleParameter("downsampleFactor", "Downsample factor", 8, null, "Downsample factor, used to determine the resolution of the image being processed").
				addDoubleParameter("sigmaPixels", "Gaussian sigma", 10, "px", "Sigma value used for smoothing; higher values result in larger regions being created").
				addDoubleParameter("sigmaMicrons", "Gaussian sigma", 10, GeneralTools.micrometerSymbol(), "Sigma value used for smoothing; higher values result in larger regions being created").
				addDoubleParameter("minThreshold", "Minimum intensity threshold", 10, null, "Regions with average values below this threshold will be discarded; this helps remove background or artefacts").
				addDoubleParameter("maxThreshold", "Maximum intensity threshold", 230, null, "Regions with average values above this threshold will be discarded; this helps remove background or artefacts").
				addDoubleParameter("noiseThreshold", "Noise threshold", 1, null, "Local threshold used to determine the number of regions created")
				;
		
		boolean hasMicrons = imageData != null && imageData.getServer().hasPixelSizeMicrons();
		params.getParameters().get("sigmaPixels").setHidden(hasMicrons);
		params.getParameters().get("sigmaMicrons").setHidden(!hasMicrons);
		
		return params;
	}
	
	
	
	static class DoGSuperpixelDetector implements ObjectDetector<BufferedImage> {
		
		private String lastResultSummary = null;

		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, final ParameterList params, final ROI pathROI) {
			
			// TODO: Give a sensible error
			if (pathROI == null) {
				lastResultSummary = "No ROI selected!";
				return null;
			}
			// Get a PathImage if we have a new ROI
			ImageServer<BufferedImage> server = imageData.getServer();
			RegionRequest request = RegionRequest.createInstance(server.getPath(), params.getDoubleParameterValue("downsampleFactor"), pathROI);
			BufferedImage img = server.readBufferedImage(request);
			
			Mat mat = OpenCVTools.imageToMat(img);
			
			Mat matLAB = new Mat();
			Imgproc.cvtColor(mat, matLAB, Imgproc.COLOR_RGB2Lab);
			
			
			
			Mat matGray = new Mat();
			Imgproc.cvtColor(mat, matGray, Imgproc.COLOR_RGB2GRAY);
			
			double sigma = getSigma(server, params);
			Mat mat2 = new Mat();
			Size size = new Size((int)(sigma * 1.6 * 6), (int)(sigma * 1.6 * 6));
			Imgproc.GaussianBlur(matGray, mat2, size, sigma*1.6);
			Imgproc.GaussianBlur(matGray, matGray, size, sigma);
			
//			Core.subtract(mat, mat2, mat);
			
			Core.absdiff(matGray, mat2, matGray);

			// Normalizing to std dev would ensure some data-dependence... which has fors an againsts
//			double stdDev = fp.getStatistics().stdDev;
//			fp.multiply(1.0/stdDev);
			
			
			Core.absdiff(matGray, new Scalar(255), matGray);
			
			Imgproc.dilate(matGray, mat2, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));
			
			Mat matBinary = new Mat();
			Core.compare(matGray, mat2, matBinary, Core.CMP_EQ);
			
			Mat matLabels = new Mat();
			Imgproc.connectedComponents(matBinary, matLabels);
			
			Imgproc.cvtColor(matGray, mat, Imgproc.COLOR_GRAY2RGB);
			
			Imgcodecs.imwrite("/Users/pete/Desktop/OpenCV.tif", matGray);

			Imgproc.watershed(mat, matLabels);
			
			List<MatOfPoint> contours = new ArrayList<>();
			Imgproc.findContours(matBinary, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
			
			List<PathObject> pathObjects = new ArrayList<>();
			
			double downsample = request.getDownsample();
			double x = request.getX();
			double y = request.getY();
			List<Point2> points = new ArrayList<>();
			double minArea = 1;
			for (MatOfPoint contour : contours){
				
				// Discard single pixels / lines
				if (contour.size().height <= 2)
					continue;
				
				// Simplify the contour slightly
				MatOfPoint2f contour2f = new MatOfPoint2f();
				contour2f.fromArray(contour.toArray());
				MatOfPoint2f contourApprox = new MatOfPoint2f();
				Imgproc.approxPolyDP(contour2f, contourApprox, 0.5, true);
				contour2f = contourApprox;
				
				// Create a polygon ROI
		        points.clear();
		        for (org.opencv.core.Point p : contour2f.toArray())
		        	points.add(new Point2(p.x * downsample + x, p.y * downsample + y));
		        	        
		        // Add new polygon if it is contained within the ROI & measurable
		        PolygonROI pathPolygon = new PolygonROI(points);
		        if (!(pathPolygon.getArea() >= minArea)) {
		        	// Don't do a simpler < because we also want to discard the region if the area couldn't be measured (although this is unlikely)
		        	continue;
		        }
		        
	//	        logger.info("Area comparison: " + Imgproc.contourArea(contour) + ",\t" + (pathPolygon.getArea() / downsample / downsample));
	//	        Mat matSmall = new Mat();
		        if (pathROI instanceof RectangleROI || PathObjectTools.containsROI(pathROI, pathPolygon)) {
		        	MeasurementList measurementList = MeasurementListFactory.createMeasurementList(20, MeasurementList.TYPE.FLOAT);
		        	PathObject pathObject = new PathDetectionObject(pathPolygon, null, measurementList);
		        	
		        	measurementList.addMeasurement("Area", pathPolygon.getArea());
		        	measurementList.addMeasurement("Perimeter", pathPolygon.getPerimeter());
		        	measurementList.addMeasurement("Circularity", pathPolygon.getCircularity());
		        	measurementList.addMeasurement("Solidity", pathPolygon.getSolidity());
		        	
		        	// I am making an assumption regarding square pixels here...
		        	RotatedRect rrect = Imgproc.minAreaRect(contour2f);
		        	measurementList.addMeasurement("Min axis", Math.min(rrect.size.width, rrect.size.height) * downsample);
		        	measurementList.addMeasurement("Max axis", Math.max(rrect.size.width, rrect.size.height) * downsample);
		        		        	
		        	// Store the object
		        	pathObjects.add(pathObject);
		        }
			}
			
			
			lastResultSummary = pathObjects.size() + " tiles created";
			
			return pathObjects;
		}
		
		
		static double getSigma(final ImageServer<?> server, final ParameterList params) {
			double pixelSizeMicrons = .5 * (server.getPixelWidthMicrons() + server.getPixelHeightMicrons()) * params.getDoubleParameterValue("downsampleFactor");
			if (Double.isNaN(pixelSizeMicrons)) {
				return params.getDoubleParameterValue("sigmaPixels") * params.getDoubleParameterValue("downsampleFactor");				
			} else
				return params.getDoubleParameterValue("sigmaMicrons") / pixelSizeMicrons;
		}
		

		@Override
		public String getLastResultsDescription() {
			return lastResultSummary;
		}
		
	}
	
	
	
	@Override
	public String getDescription() {
		return "Partition image into tiled regions of irregular shapes, using intensity & boundary information";
	}
	
	
	@Override
	protected synchronized Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		Collection<? extends PathObject> parents = super.getParentObjects(runner);
		return parents;
		
		// Exploring the use of hidden objects...
//		PathObject pathObjectHidden = new PathTileObject();
//		for (PathObject parent : parents) {
//			pathObjectHidden.addPathObject(new PathTileObject(parent.getROI()));
//		}
//		imageData.getHierarchy().getRootObject().addPathObject(pathObjectHidden);
//		return pathObjectHidden.getPathObjectList();
	}
	

}