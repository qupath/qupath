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

package qupath.imagej.detect.nuclei;

import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import qupath.imagej.color.ColorDeconvolutionIJ;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.imagej.objects.measure.ObjectMeasurements;
import qupath.imagej.processing.MorphologicalReconstruction;
import qupath.imagej.processing.ROILabeling;
import qupath.imagej.processing.Watershed;
import qupath.imagej.wrappers.PixelImageIJ;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.experimental.ShapeSimplifier;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Cell detection that takes into consideration membrane staining.
 * 
 * @author Pete Bankhead
 *
 */
public class WatershedCellMembraneDetectionWithBoundaries extends AbstractTileableDetectionPlugin<BufferedImage> {

	final private static Logger logger = LoggerFactory.getLogger(WatershedCellMembraneDetectionWithBoundaries.class);
	
	private static String[] micronParameters = {
			"requestedPixelSizeMicrons",
			"backgroundRadiusMicrons",
			"medianRadiusMicrons",
			"sigmaMicrons",
			"minAreaMicrons",
			"maxAreaMicrons",
			"cellExpansionMicrons",
	};

	private static String[] pixelParameters = {
			//		"requestedPixelSize",
			"backgroundRadius",
			"medianRadius",
			"sigma",
			"minArea",
			"maxArea",
			"cellExpansion",
	};

	transient private CellDetector detector;

	private ParameterList params;


	public WatershedCellMembraneDetectionWithBoundaries() {

		Prefs.setThreads(1);
		params = new ParameterList();
		// TODO: Use a better way to determining if pixel size is available in microns
		//		params.addEmptyParameter("detectionParameters", "Detection parameters", true);

		String microns = GeneralTools.micrometerSymbol();

		params.addEmptyParameter("paramsResolution", "Setup parameters", true);

		params.addDoubleParameter("requestedPixelSizeMicrons", "Requested pixel size", .5, microns, 
				"Choose pixel size at which detection will be performed - higher values are likely to be faster, but may be less accurate; set <= 0 to use the full image resolution");
		//		params.addDoubleParameter("requestedPixelSize", "Requested downsample factor", 1, "");

		params.addEmptyParameter("paramsNuclei", "Nucleus parameters", true);

		params.addDoubleParameter("backgroundRadiusMicrons", "Background radius", 8, microns, 
				"Radius for background estimation, should be > the largest nucleus radius, or <= 0 to turn off background subtraction");
		params.addDoubleParameter("medianRadiusMicrons", "Median filter radius", 0, microns,
				"Radius of median filter used to reduce image texture (optional)");
		params.addDoubleParameter("sigmaMicrons", "Sigma", 1.5, microns,
				"Sigma value for Gaussian filter used to reduce noise; increasing the value stops nuclei being fragmented, but may reduce the accuracy of boundaries");
		params.addDoubleParameter("minAreaMicrons", "Minimum area", 10, microns+"^2",
				"Detected nuclei with an area < minimum area will be discarded");
		params.addDoubleParameter("maxAreaMicrons", "Maximum area", 1000, microns+"^2",
				"Detected nuclei with an area > maximum area will be discarded");

		params.addDoubleParameter("backgroundRadius", "Background radius", 15, "px", 
				"Radius for background estimation, should be > the largest nucleus radius, or <= 0 to turn off background subtraction");
		params.addDoubleParameter("medianRadius", "Median filter radius", 0, "px",
				"Radius of median filter used to reduce image texture (optional)");
		params.addDoubleParameter("sigma", "Sigma", 3, "px",
				"Sigma value for Gaussian filter used to reduce noise; increasing the value stops nuclei being fragmented, but may reduce the accuracy of boundaries");
		params.addDoubleParameter("minArea", "Minimum area", 10, "px^2",
				"Detected nuclei with an area < minimum area will be discarded");
		params.addDoubleParameter("maxArea", "Maximum area", 1000, "px^2",
				"Detected nuclei with an area > maximum area will be discarded");

		params.addDoubleParameter("threshold", "Nucleus threshold", 0.1, null, 0, 2.5,
				"Nucleus intensity threshold - detected nuclei must have a mean intensity >= threshold");
		params.addDoubleParameter("membraneThreshold", "Membrane threshold", 0.1, null, 0, 2.5,
				"Membrane intensity threshold - used to help identify positive membranous staining");
		params.addDoubleParameter("maxBackground", "Max background intensity", 2, null,
				"If background radius > 0, detected nuclei occurring on a background > max background intensity will be discarded");

		//		params.addBooleanParameter("mergeAll", "Merge all", true);
		params.addBooleanParameter("watershedPostProcess", "Split by shape", true,
				"Split merged detected nuclei based on shape ('roundness')");

		params.addEmptyParameter("paramsCells", "Cell parameters", true);

		params.addDoubleParameter("cellExpansionMicrons", "Cell expansion", 8, microns, 0, 25,
				"Amount by which to expand detected nuclei to approximate the full cell area");
		params.addDoubleParameter("cellExpansion", "Cell expansion", 10, "px",
				"Amount by which to expand detected nuclei to approximate the full cell area");

		params.addBooleanParameter("includeNuclei", "Include cell nucleus", true,
				"If cell expansion is used, optionally include/exclude the nuclei within the detected cells");


		params.addEmptyParameter("paramsGeneral", "General parameters", true);
		params.addBooleanParameter("smoothBoundaries", "Smooth boundaries", false,
				"Smooth the detected nucleus/cell boundaries");
		params.addBooleanParameter("makeMeasurements", "Make measurements", true,
				"Add default shape & intensity measurements during detection");
	}
	
	
	@Override
	protected boolean parseArgument(ImageData<BufferedImage> imageData, String arg) {
		if (imageData == null || imageData.getImageType() != ImageType.BRIGHTFIELD_H_DAB) {
			logger.error("Can only process H-DAB images");
			return false;
		}
		return true;
	}
	

	static class CellDetector implements ObjectDetector<BufferedImage> {

		private List<PathObject> pathObjects = null;

		private boolean nucleiClassified = false;


		public static double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
			if (imageData.getServer().hasPixelSizeMicrons())
				return Math.max(params.getDoubleParameterValue("requestedPixelSizeMicrons"), imageData.getServer().getAveragedPixelSizeMicrons());
			return Double.NaN;
		}


		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			// TODO: Give a sensible error
			if (pathROI == null)
				return null;
			// Get a PathImage if we have a new ROI
			//			boolean imageChanged = false;
			PathImage<ImagePlus> pathImage = null;

			ImageServer<BufferedImage> server = imageData.getServer();
			pathImage = PathImagePlus.createPathImage(server, pathROI, ServerTools.getDownsampleFactor(server, getPreferredPixelSizeMicrons(imageData, params), true));

			// Create a detector if we don't already have one for this image
			boolean isBrightfield = imageData.isBrightfield();
			FloatProcessor fpDetection = null, fpH = null, fpDAB = null;
			ImageProcessor ip = pathImage.getImage().getProcessor();
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			if (ip instanceof ColorProcessor && stains != null) {

				FloatProcessor[] fps = ColorDeconvolutionIJ.colorDeconvolve((ColorProcessor)ip, stains.getStain(1), stains.getStain(2), stains.getStain(3));
				fpH = fps[0];
				fpDAB = fps[1];
				fpDetection = (FloatProcessor)fpH.duplicate();

			} else throw new IllegalArgumentException("Only H-DAB images currently supported!");
			//			}
			Roi roi = null;
			if (pathROI != null)
				roi = ROIConverterIJ.convertToIJRoi(pathROI, pathImage);
			WatershedCellDetector detector2 = new WatershedCellDetector(fpDetection, fpH, fpDAB, roi, pathImage);

			// Create or reset the PathObjects list
			if (pathObjects == null)
				pathObjects = new ArrayList<>();
			else
				pathObjects.clear();


			// Convert parameters where needed
			double sigma, medianRadius, backgroundRadius, minArea, maxArea, cellExpansion;
			if (pathImage.hasPixelSizeMicrons()) {
				double pixelSize = 0.5 * (pathImage.getPixelHeightMicrons() + pathImage.getPixelWidthMicrons());
				backgroundRadius = params.getDoubleParameterValue("backgroundRadiusMicrons") / pixelSize;
				medianRadius = params.getDoubleParameterValue("medianRadiusMicrons") / pixelSize;
				sigma = params.getDoubleParameterValue("sigmaMicrons") / pixelSize;
				minArea = params.getDoubleParameterValue("minAreaMicrons") / (pixelSize * pixelSize);
				maxArea = params.getDoubleParameterValue("maxAreaMicrons") / (pixelSize * pixelSize);
				cellExpansion = params.getDoubleParameterValue("cellExpansionMicrons") / (pixelSize);
			} else {
				backgroundRadius = params.getDoubleParameterValue("backgroundRadius");
				medianRadius = params.getDoubleParameterValue("medianRadius");
				sigma = params.getDoubleParameterValue("sigma");
				minArea = params.getDoubleParameterValue("minArea");
				maxArea = params.getDoubleParameterValue("maxArea");
				cellExpansion = params.getDoubleParameterValue("cellExpansion");
			}

			detector2.runDetection(
					backgroundRadius,
					params.getDoubleParameterValue("maxBackground"),
					medianRadius,
					sigma,
					params.getDoubleParameterValue("threshold"),
					params.getDoubleParameterValue("membraneThreshold"),
					minArea,
					maxArea,
					true, // always use 'merge all' params.getBooleanParameterValue("mergeAll"),
					params.getBooleanParameterValue("watershedPostProcess"),
					cellExpansion,
					params.getBooleanParameterValue("smoothBoundaries"),
					params.getBooleanParameterValue("includeNuclei"),
					params.getBooleanParameterValue("makeMeasurements") && isBrightfield);

			pathObjects.addAll(detector2.getPathObjects());

			return pathObjects;
		}



		@Override
		public String getLastResultsDescription() {
			if (pathObjects == null)
				return null;
			int nDetections = pathObjects.size();
			if (nDetections == 1)
				return "1 nucleus detected";
			String s = String.format("%d nuclei detected", nDetections);
			if (nucleiClassified) {
				int nPositive = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPathClass(PathClassFactory.getPositiveClassName()), false);
				int nNegative = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPathClass(PathClassFactory.getNegativeClassName()), false);
				return String.format("%s (%.3f%% positive)", s, ((double)nPositive * 100.0 / (nPositive + nNegative)));			
			} else
				return s;
		}


	}



	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {

		// Show/hide parameters depending on whether the pixel size is known
		Map<String, Parameter<?>> map = params.getParameters();
		boolean pixelSizeKnown = imageData.getServer() != null && imageData.getServer().hasPixelSizeMicrons();
		for (String name : micronParameters)
			map.get(name).setHidden(!pixelSizeKnown);
		for (String name : pixelParameters)
			map.get(name).setHidden(pixelSizeKnown);

		map.get("makeMeasurements").setHidden(!imageData.isBrightfield());
		
		// These aren't implemented here!
		params.setHiddenParameters(true, "maxBackground", "backgroundRadiusMicrons", "backgroundRadius");

		return params;
	}

	@Override
	public String getName() {
		return "Watershed cell detection";
	}


	@Override
	public String getLastResultsDescription() {
		return detector == null ? "" : detector.getLastResultsDescription();
	}







	static class WatershedCellDetector {

		final private static Logger logger = LoggerFactory.getLogger(WatershedCellDetector.class);

		private double backgroundRadius = 15;
		private double maxBackground = 0.3;

		private boolean lastRunCompleted = false;

		private boolean includeNuclei = true;
		private double cellExpansion = 0;

		private double minArea = 0;
		private double maxArea = 0;

		private double medianRadius = 2;
		private double sigma = 2.5;
		private double threshold = 0.3;
		private double membraneThreshold = 0.2;
		private boolean mergeAll = true;
		private boolean watershedPostProcess = true; // TODO: COMBINE WITH MERGEALL OPTION
		private boolean smoothBoundaries = false;

		private boolean makeMeasurements = true;

		private Roi roi = null;
		private FloatProcessor fpDetection = null;
		private FloatProcessor fpH = null;
		private FloatProcessor fpDAB = null;
		private List<PolygonRoi> rois = null;
		private ByteProcessor bpLoG = null;

		private List<PathObject> pathObjects = new ArrayList<>();

		private PathImage<ImagePlus> pathImage = null;

		public WatershedCellDetector(FloatProcessor fpDetection, FloatProcessor fpH, FloatProcessor fpDAB, Roi roi, PathImage<ImagePlus> pathImage) {
			this.fpDetection = fpDetection;
			this.fpH = fpH;
			this.fpDAB = fpDAB;
			this.roi = roi;
			this.pathImage = pathImage;
		}



		private void doDetection(boolean regenerateROIs) {
			int width = fpDetection.getWidth();
			int height = fpDetection.getHeight();
			RankFilters rf = new RankFilters();
			lastRunCompleted = false;
			pathObjects.clear();
			fpDetection.setRoi(roi);
			
			
			// Start off with a median filter to reduce texture, if necessary
			if (medianRadius > 0) {
				rf.rank(fpH, medianRadius, RankFilters.MEDIAN);
				rf.rank(fpDAB, medianRadius, RankFilters.MEDIAN);
			}
			
			// Find potential cell boundaries for complete staining
			FloatProcessor fpDAB2 = (FloatProcessor)fpDAB.duplicate();
			if (sigma > 0)
				fpDAB2.blurGaussian(sigma);
			fpDAB2.multiply(-1);
			ByteProcessor bpCompleteCells = new MaximumFinder().findMaxima(fpDAB2, membraneThreshold, MaximumFinder.SEGMENTED, false);
			
			// Find potential nuclei
			// Use watershed transform to segment all regions
			FloatProcessor fpNuclei = (FloatProcessor)fpH.duplicate();
			// Subtract background first, if needed
			if (backgroundRadius > 0) {
				ImageProcessor ipBackground = fpNuclei.duplicate();
				WatershedCellDetection.WatershedCellDetector.limitedOpeningByReconstruction(fpNuclei, ipBackground, backgroundRadius, maxBackground);
				fpNuclei.copyBits(ipBackground, 0, 0, Blitter.SUBTRACT);
			}
			if (sigma > 0)
				fpNuclei.blurGaussian(sigma);
			
			// Determine potential nuclei by simple checking hematoxylin > DAB, which will only be used for cells that otherwise lack a nucleus
			ByteProcessor bpPossibleNucleus = new ByteProcessor(width, height);
			for (int i = 0; i < width*height; i++) {
				if (bpCompleteCells.get(i) == 0)
					continue;
				float h = fpNuclei.getf(i);
				float dab = -fpDAB2.getf(i);
//				if (h > dab && (h >= threshold || dab >= membraneThreshold))
				if (h >= threshold && (h >= dab || dab < membraneThreshold*2))
					bpPossibleNucleus.set(i, 255);
			}
//			fpDAB2.multiply(-1);
//			IJTools.quickShowImage("H", fpNuclei.duplicate());
//			IJTools.quickShowImage("DAB", fpDAB2.duplicate());
//			IJTools.quickShowImage("Possible", bpPossibleNucleus.duplicate());
//			IJTools.quickShowImage("Greater", SimpleThresholding.greaterThan(fpNuclei, fpDAB2));
			new EDM().toWatershed(bpPossibleNucleus);
			ROILabeling.removeByAreas(bpPossibleNucleus, minArea, maxArea, false);
			
			// Determine nuclei in general
			fpNuclei.convolve(new float[]{0, -1, 0, -1, 4, -1, 0, -1, 0}, 3, 3);
			fpNuclei.setThreshold(0.0001, ImageProcessor.NO_THRESHOLD, ImageProcessor.NO_LUT_UPDATE);
			List<PolygonRoi> roisTemp = ROILabeling.getFilledPolygonROIs(fpNuclei, Wand.FOUR_CONNECTED);		
			fpNuclei.setValue(0);
			ByteProcessor bpNuclei = new ByteProcessor(width, height);
			bpNuclei.setValue(255);
			for (Roi r : roisTemp) {
				fpH.setRoi(r);
				fpDAB.setRoi(r);
				bpCompleteCells.setRoi(r);
				double h = fpH.getStatistics().mean;
				// Final criterion is to avoid touching an existing membrane boundary
				if (h >= threshold && h > fpDAB.getStatistics().mean && bpCompleteCells.getStatistics().min == 255)
					bpNuclei.fill(r);
			}
			fpH.resetRoi();
			fpDAB.resetRoi();
			rf.rank(bpNuclei, 1, RankFilters.MAX);
			rf.rank(bpNuclei, 1, RankFilters.MIN);
			// Loop again, both to fill in holes and potentially use convex hull
			bpNuclei.setThreshold(128, ImageProcessor.NO_THRESHOLD, ImageProcessor.NO_LUT_UPDATE);
//			roisTemp = ROILabeling.getFilledPolygonROIs(bpNuclei, Wand.FOUR_CONNECTED);		
//			for (PolygonRoi r : roisTemp) {
//				PolygonRoi rConvex = new PolygonRoi(r.getConvexHull(), PolygonRoi.POLYGON);
//				fpH.setRoi(r);
//				double min = fpH.getStatistics().min;
//				fpH.setRoi(rConvex);
//				double minConvex = fpH.getStatistics().min;
//				bpCompleteCells.setRoi(rConvex);
//				double minConvexComplete = bpCompleteCells.getStatistics().min;
//				if (minConvexComplete == 255 && minConvex >= Math.min(min, threshold))
//					bpNuclei.fill(rConvex);
//				else
//					bpNuclei.fill(r);
//			}
			ROILabeling.fillHoles(bpNuclei);
			if (watershedPostProcess)
				new EDM().toWatershed(bpNuclei);
			
			// Add all the non-overlapping possible nuclei
			ByteProcessor bpMarker = (ByteProcessor)bpNuclei.duplicate();
			bpMarker.copyBits(bpNuclei, 0, 0, Blitter.MIN);
			bpMarker = MorphologicalReconstruction.binaryReconstruction(bpMarker, bpPossibleNucleus, false);
			for (int i = 0; i < width*height; i++) {
				if (bpPossibleNucleus.get(i) == 255 && bpMarker.get(i) == 0 && bpNuclei.get(i) == 0)
					bpNuclei.set(i, 255);
			}			
			
//			IJTools.quickShowImage("H", fpH.duplicate());
//			IJTools.quickShowImage("DAB", fpDAB.duplicate());
//			IJTools.quickShowImage("Complete", bpCompleteCells.duplicate());
//			IJTools.quickShowImage("Possible", bpPossibleNucleus.duplicate());
//			IJTools.quickShowImage("Action", bpNuclei.duplicate());

			
			
			// Loop through potential complete cells and count the number of nuclei they contain
			ShortProcessor ipCompleteLabels = ROILabeling.labelImage(bpCompleteCells, false);
			Polygon nucleusMaxima = new MaximumFinder().getMaxima(bpNuclei, 128, false);
			int[] nucleusCounts = new int[(int)ipCompleteLabels.getStatistics().max];
			for (int i = 0; i < nucleusMaxima.npoints; i++) {
				int label = ipCompleteLabels.get(nucleusMaxima.xpoints[i], nucleusMaxima.ypoints[i]);
				if (label > 0)
					nucleusCounts[label-1]++;
			}
			
//			// For regions without a nucleus, see if we can add one
//			for (int i = 0; i < width*height; i++) {
//				int label = ipCompleteLabels.get(i);
//				if (label > 0 && nucleusCounts[label-1] == 0 && bpPossibleNucleus.get(i) != 0)
//					bpNuclei.set(i, 255);
//			}
			
			// Expand nuclei to approximate full cell area
			FloatProcessor fpEDM = new EDM().makeFloatEDM(bpNuclei, (byte)255, false);
			fpEDM.multiply(-1);
			for (int i = 0; i < width*height; i++) {
				if (bpCompleteCells.get(i) == 0)
					fpEDM.setf(i, (float)(-cellExpansion-1));
			}
			ImageProcessor ipLabelsNucleiExpanded = ROILabeling.labelImage(bpNuclei, false);
			Watershed.doWatershed(fpEDM, ipLabelsNucleiExpanded, -cellExpansion, false);
			
			// Loop through potential 'complete' cells & update labelled image accordingly
			// We keep complete regions containing 0 or 1 nucleus, and otherwise use the expanded nucleus regions
			// Also keep a reference to cells considered to have complete staining, without further subdivision required
			ByteProcessor bpCells = new ByteProcessor(width, height);
			int completeMembraneFlag = 128;
			for (int i = 0; i < width*height; i++) {
				int label = ipCompleteLabels.get(i);
				if (label > 0 && nucleusCounts[label-1] <= 1) {
					bpCells.set(i, completeMembraneFlag);
				} else if (ipLabelsNucleiExpanded.get(i) > 0)
					bpCells.set(i, 255);
			}
			
			// Exclude outside the main ROI
			if (roi != null)
				ROILabeling.fillOutside(bpCells, roi, 0);
			
			// Remove very large or small areas
			ROILabeling.removeByAreas(bpCompleteCells, minArea, maxArea*4, false);

			// Create labelled images
			ShortProcessor ipLabels = ROILabeling.labelImage(bpCells, false);
			
			// Create cells
			ipLabels.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			Map<Float, PolygonRoi> roisCellsMap = ROILabeling.getFilledPolygonROIsFromLabels(ipLabels, Wand.FOUR_CONNECTED);
			
			// Create nuclei & cytoplasm regions
			ImageProcessor ipLabelsNuclei = ipLabels.duplicate();
			ImageProcessor ipLabelsCytoplasm = ipLabels.duplicate();
			Set<Float> completeMembraneSet = new HashSet<>();
			for (int i = 0; i < width*height; i++) {
				float label = ipLabels.getf(i);
				if (label > 0 && bpCells.get(i) == completeMembraneFlag)
					completeMembraneSet.add(Float.valueOf(label));
				
				if (bpNuclei.get(i) == 0)
					ipLabelsNuclei.set(i, 0);
				else
					ipLabelsCytoplasm.set(i, 0);
			}
			ipLabelsNuclei.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			Map<Float, PolygonRoi> roisNucleiMap = ROILabeling.getFilledPolygonROIsFromLabels(ipLabelsNuclei, Wand.FOUR_CONNECTED);
			
			// Create labelled regions for other compartments
			ImageProcessor ipLabelsMembrane = new ShortProcessor(width, height);
			ipLabelsMembrane.setLineWidth(2);
			for (Float key : roisCellsMap.keySet()) {
				Roi rCell = roisCellsMap.get(key);
				ipLabelsMembrane.setValue(key);
				ipLabelsMembrane.draw(rCell);
			}

			// Get measurements
			Map<Float, RunningStatistics> statsNucleusHematoxylin = StatisticsHelper.computeRunningStatisticsMap(new PixelImageIJ(fpH), new PixelImageIJ(ipLabelsNuclei));
			Map<Float, RunningStatistics> statsNucleusDAB = StatisticsHelper.computeRunningStatisticsMap(new PixelImageIJ(fpDAB), new PixelImageIJ(ipLabelsNuclei));
			Map<Float, RunningStatistics> statsCellDAB = StatisticsHelper.computeRunningStatisticsMap(new PixelImageIJ(fpH), new PixelImageIJ(ipLabels));
			Map<Float, RunningStatistics> statsCytoplasmDAB = StatisticsHelper.computeRunningStatisticsMap(new PixelImageIJ(fpDAB), new PixelImageIJ(ipLabelsCytoplasm));
			Map<Float, RunningStatistics> statsMembraneDAB = StatisticsHelper.computeRunningStatisticsMap(new PixelImageIJ(fpDAB), new PixelImageIJ(ipLabelsMembrane));
			
			Map<Float, RunningStatistics> statsMembraneComplete = new HashMap<>();
			for (int i = 0; i < width*height; i++) {
				float label = ipLabelsMembrane.getf(i);
				if (label == 0)
					continue;
				Float floatLabel = Float.valueOf(label);
				float membraneDAB = fpDAB.getf(i);
				RunningStatistics statsCell = statsCytoplasmDAB.get(floatLabel);
				if (statsCell == null) {
					statsCell = statsCellDAB.get(floatLabel);
					if (statsCell == null)
						continue;
				}
				float cellDAB = (float)statsCell.getMean();
				RunningStatistics stats = statsMembraneComplete.get(floatLabel);
				if (stats == null) {
					stats = new RunningStatistics();
					statsMembraneComplete.put(floatLabel, stats);
				}
				if (membraneDAB >= Math.max(membraneThreshold, cellDAB))
					stats.addValue(1);
				else
					stats.addValue(0);
			}
			
			// Create cells
			Calibration cal = pathImage.getImage().getCalibration();
			for (Float key : roisCellsMap.keySet()) {
				PolygonRoi rCell = roisCellsMap.get(key);
				PolygonRoi rNucleus = roisNucleiMap.get(key);
				
				PolygonROI pathROICell = null;
				PolygonROI pathROINucleus = null;
				if (smoothBoundaries) {
					rCell = new PolygonRoi(rCell.getInterpolatedPolygon(Math.min(2.5, rCell.getNCoordinates()*0.1), true), Roi.POLYGON);
					pathROICell = ROIConverterIJ.convertToPolygonROI(rCell, cal, pathImage.getDownsampleFactor());
					pathROICell = ShapeSimplifier.simplifyPolygon(pathROICell, pathImage.getDownsampleFactor()/4.0);
					
					if (rNucleus != null) {
						rNucleus = new PolygonRoi(rNucleus.getInterpolatedPolygon(Math.min(2.5, rCell.getNCoordinates()*0.1), true), Roi.POLYGON);
						pathROINucleus = ROIConverterIJ.convertToPolygonROI(rNucleus, cal, pathImage.getDownsampleFactor());
						pathROINucleus = ShapeSimplifier.simplifyPolygon(pathROINucleus, pathImage.getDownsampleFactor()/4.0);
					}
				} else {
					pathROICell = ROIConverterIJ.convertToPolygonROI(rCell, cal, pathImage.getDownsampleFactor());
					if (rNucleus != null)
						pathROINucleus = ROIConverterIJ.convertToPolygonROI(rNucleus, cal, pathImage.getDownsampleFactor());
				}
				
				// Create a new shared measurement list
				MeasurementList measurementList = MeasurementListFactory.createMeasurementList(makeMeasurements ? 30 : 0, MeasurementList.TYPE.FLOAT);

				if (rNucleus != null) {
					ObjectMeasurements.addShapeStatistics(measurementList, rNucleus, fpH, cal, "Nucleus: ");
					RunningStatistics stats = statsNucleusHematoxylin.get(key);
					measurementList.addMeasurement("Nucleus: Hematoxylin OD mean", stats.getMean());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD sum", stats.getSum());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD std dev", stats.getStdDev());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD max", stats.getMax());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD min", stats.getMin());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD range", stats.getRange());

					stats = statsNucleusDAB.get(key);
					measurementList.addMeasurement("Nucleus: DAB OD mean", stats.getMean());
					measurementList.addMeasurement("Nucleus: DAB OD sum", stats.getSum());
					measurementList.addMeasurement("Nucleus: DAB OD std dev", stats.getStdDev());
					measurementList.addMeasurement("Nucleus: DAB OD max", stats.getMax());
					measurementList.addMeasurement("Nucleus: DAB OD min", stats.getMin());
					measurementList.addMeasurement("Nucleus: DAB OD range", stats.getRange());
				}
				
				ObjectMeasurements.addShapeStatistics(measurementList, rCell, fpDetection, pathImage.getImage().getCalibration(), "Cell: ");
				//					ObjectMeasurements.computeShapeStatistics(pathObject, pathImage, fpH, pathImage.getImage().getCalibration());

				// Add cell measurements
				RunningStatistics stats = statsCellDAB.get(key);
				if (stats != null) {
					measurementList.addMeasurement("Cell: DAB OD mean", stats.getMean());
					measurementList.addMeasurement("Cell: DAB OD std dev", stats.getStdDev());
					measurementList.addMeasurement("Cell: DAB OD max", stats.getMax());
					measurementList.addMeasurement("Cell: DAB OD min", stats.getMin());
					//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
				}

				// Add cytoplasm measurements
				stats = statsCytoplasmDAB.get(key);
				if (stats != null) {
					measurementList.addMeasurement("Cytoplasm: DAB OD mean", stats.getMean());
					measurementList.addMeasurement("Cytoplasm: DAB OD std dev", stats.getStdDev());
					measurementList.addMeasurement("Cytoplasm: DAB OD max", stats.getMax());
					measurementList.addMeasurement("Cytoplasm: DAB OD min", stats.getMin());
					//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
				}

				// Add membrane measurements
				stats = statsMembraneDAB.get(key);
				if (stats != null) {
					measurementList.addMeasurement("Membrane: DAB OD mean", stats.getMean());
					measurementList.addMeasurement("Membrane: DAB OD std dev", stats.getStdDev());
					measurementList.addMeasurement("Membrane: DAB OD max", stats.getMax());
					measurementList.addMeasurement("Membrane: DAB OD min", stats.getMin());
					//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
				}
				
				stats = statsMembraneComplete.get(key);
				if (stats != null) {
					measurementList.addMeasurement("Membrane: Above mean %", stats.getMean() * 100);
				}
				
				// Store flag to indicate complete staining
				if (completeMembraneSet.contains(key))
					measurementList.addMeasurement("Membrane staining complete", 1);
				else
					measurementList.addMeasurement("Membrane staining complete", 0);
				
//				// Add membrane proportion measurements
//				if (statsDABMembraneCompletion != null) {
//					RunningStatistics stats = statsDABMembraneCompletion.get(label-1);
//					measurementList.addMeasurement("Membrane: Complete %", stats.getMean() * 100);
//					//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
//				}

				// Add nucleus area ratio, if available
				if (pathROINucleus != null && pathROINucleus instanceof PathArea) {
					double nucleusArea = ((PathArea)pathROINucleus).getArea();
					double cellArea = pathROICell.getArea();
					measurementList.addMeasurement("Nucleus/Cell area ratio", Math.min(nucleusArea / cellArea, 1.0));
				}
				
				// Create detection object
				pathObjects.add(new PathCellObject(pathROICell, pathROINucleus, null, measurementList));
				
				
			}
			
			// Remove weird cells & close measurement lists
			Iterator<PathObject> iter = pathObjects.iterator();
//			double maxCellDim = Math.sqrt(maxArea / Math.PI) * 8;
			while (iter.hasNext()) {
				PathCellObject temp = (PathCellObject)iter.next();
				temp.getMeasurementList().closeList();
				// Require at least 50% solidity, and a reasonable area
				if (temp.getROI() instanceof PolygonROI) {
					PolygonROI poly = (PolygonROI)temp.getROI();
					if (poly.getArea() > maxArea * 8 || poly.getSolidity() < 0.5) //poly.getArea() / (poly.getBoundsWidth() * poly.getBoundsHeight()) < 0.25)
						iter.remove();
//					else if (!temp.hasNucleus() && (poly.getArea() > maxArea * 4 || temp.getMeasurementList().getMeasurementValue("Cell: DAB OD mean") < membraneThreshold/4))
//						iter.remove();
				}
			}			

			lastRunCompleted = true;
		}


		public List<PathObject> getPathObjects() {
			return pathObjects;
		}


		public void runDetection(double backgroundRadius, double maxBackground, double medianRadius, double sigma, double threshold, double membraneThreshold, double minArea, double maxArea, boolean mergeAll, boolean watershedPostProcess, double cellExpansion, boolean smoothBoundaries, boolean includeNuclei, boolean makeMeasurements) {

			boolean updateNucleusROIs = rois == null || bpLoG == null;
			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.medianRadius != medianRadius;
			this.medianRadius = medianRadius;

			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.backgroundRadius != backgroundRadius;
			this.backgroundRadius = backgroundRadius;

			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.sigma != sigma;
			this.sigma = sigma;

			boolean updateAnything = updateNucleusROIs || !lastRunCompleted;

			updateAnything = updateAnything ? updateAnything : this.minArea != minArea;
			this.minArea = minArea;

			updateAnything = updateAnything ? updateAnything : this.maxArea != maxArea;
			this.maxArea = maxArea;

			updateAnything = updateAnything ? updateAnything : this.maxBackground != maxBackground;
			this.maxBackground = maxBackground;

			updateAnything = updateAnything ? updateAnything : this.threshold != threshold;
			this.threshold = threshold;
			
			updateAnything = updateAnything ? updateAnything : this.membraneThreshold != membraneThreshold;
			this.membraneThreshold = membraneThreshold;

			updateAnything = updateAnything ? updateAnything : this.mergeAll != mergeAll;
			this.mergeAll = mergeAll;

			updateAnything = updateAnything ? updateAnything : this.watershedPostProcess != watershedPostProcess;
			this.watershedPostProcess = watershedPostProcess;

			updateAnything = updateAnything ? updateAnything : this.cellExpansion != cellExpansion;
			this.cellExpansion = cellExpansion;

			updateAnything = updateAnything ? updateAnything : this.smoothBoundaries != smoothBoundaries;
			this.smoothBoundaries = smoothBoundaries;

			updateAnything = updateAnything ? updateAnything : this.includeNuclei != includeNuclei;
			this.includeNuclei = includeNuclei;

			updateAnything = updateAnything ? updateAnything : this.makeMeasurements != makeMeasurements;
			this.makeMeasurements = makeMeasurements;

			try {
				doDetection(updateNucleusROIs);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


	}



	@Override
	public String getDescription() {
		return "Default cell detection algorithm for brightfield images with membrane staining";
	}



	@Override
	protected double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
		return CellDetector.getPreferredPixelSizeMicrons(imageData, params);
	}


	@Override
	protected ObjectDetector<BufferedImage> createDetector(ImageData<BufferedImage> imageData, ParameterList params) {
		return new CellDetector();
	}


	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		double pxSize = imageData.getServer().getAveragedPixelSizeMicrons();
		if (Double.isNaN(pxSize))
			return params.getDoubleParameterValue("cellExpansion") > 0 ? 50 : 25;
		double cellExpansion = params.getDoubleParameterValue("cellExpansionMicrons") / pxSize;
		int overlap = cellExpansion > 0 ? (int)(cellExpansion * 2 + 5) : 25;
		return overlap;
	}

}