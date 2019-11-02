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

package qupath.imagej.detect.cells;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.filter.EDM;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import qupath.imagej.processing.MorphologicalReconstruction;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.processing.SimpleThresholding;
import qupath.imagej.processing.Watershed;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.DoubleParameter;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;

/**
 * Default command for cell detection within QuPath, assuming either a nuclear or cytoplasmic staining.
 * <p>
 * To automatically classify cells as positive or negative along with detection, see {@link PositiveCellDetection}.
 * <p>
 * To quantify membranous staining see {@link WatershedCellMembraneDetection}.
 * 
 * @author Pete Bankhead
 *
 */
public class WatershedCellDetection extends AbstractTileableDetectionPlugin<BufferedImage> {
	
	protected boolean parametersInitialized = false;

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
	
//	private static String[] fluorescenceParameters = {
//			"detectionImageFluorescence"
//	};
	
	private static String[] fluorescenceParameters = {
			"detectionImage"
	};

	private static String[] brightfieldParameters = {
			"detectionImageBrightfield",
			"maxBackground"
	};

	transient private CellDetector detector;
	
	private final static Logger logger = LoggerFactory.getLogger(WatershedCellDetection.class);
	
	static String IMAGE_OPTICAL_DENSITY = "Optical density sum";
	static String IMAGE_HEMATOXYLIN = "Hematoxylin OD";
	
	ParameterList params;
	
	
	static class CellDetector implements ObjectDetector<BufferedImage> {
	
		private String lastServerPath = null;
		//private PathImage<ImagePlus> pathImage; // Caching these cause out of memory errors...
		private ROI pathROI;
		
		private List<PathObject> pathObjects = null;
//		private WatershedCellDetector detector2;
//		private FloatProcessor fpDetection, fpH, fpDAB;
//		private ColorDeconvolutionStains stains;
		
		private boolean nucleiClassified = false;
	
			
		public static double getPreferredPixelSizeMicrons(ImageData<BufferedImage> imageData, ParameterList params) {
			PixelCalibration cal = imageData.getServer().getPixelCalibration();
			if (cal.hasPixelSizeMicrons()) {
				double requestedPixelSize = params.getDoubleParameterValue("requestedPixelSizeMicrons");
				double averagedPixelSize = cal.getAveragedPixelSizeMicrons();
				if (requestedPixelSize < 0)
					requestedPixelSize = averagedPixelSize * (-requestedPixelSize);
				requestedPixelSize = Math.max(requestedPixelSize, averagedPixelSize);
				return requestedPixelSize;
			}
			return Double.NaN;
		}
		
	
		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) throws IOException {
			if (pathROI == null)
				throw new IOException("Cell detection requires a ROI!");
			// Get a PathImage if we have a new ROI
//			boolean imageChanged = false;
			PathImage<ImagePlus> pathImage = null;
			if (lastServerPath == null || !lastServerPath.equals(imageData.getServerPath()) || pathImage == null || !pathROI.equals(this.pathROI)) {
				ImageServer<BufferedImage> server = imageData.getServer();
				lastServerPath = imageData.getServerPath();
				double downsample = ServerTools.getDownsampleFactor(server, getPreferredPixelSizeMicrons(imageData, params));
				pathImage = IJTools.convertToImagePlus(server, RegionRequest.createInstance(server.getPath(), downsample, pathROI));
//				pathImage = IJTools.createPathImage(server, pathROI, ServerTools.getDownsampleFactor(server, getPreferredPixelSizeMicrons(imageData, params), false));
				logger.trace("Cell detection with downsample: {}", pathImage.getDownsampleFactor());
				this.pathROI = pathROI;
//				imageChanged = true;
			}
			// Create a detector if we don't already have one for this image
			boolean isBrightfield = imageData.isBrightfield();
			//			if (detector2 == null || imageChanged || stains != imageData.getColorDeconvolutionStains()) {
			//			if (imageChanged || stains != imageData.getColorDeconvolutionStains()) {
			ImageProcessor ip = pathImage.getImage().getProcessor();
			FloatProcessor fpDetection = null;
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			Map<String, FloatProcessor> channels = new LinkedHashMap<>();
			Map<String, FloatProcessor> channelsCell = new LinkedHashMap<>();
			Roi roi = null;
			if (pathROI != null)
				roi = IJTools.convertToIJRoi(pathROI, pathImage);
			if (ip instanceof ColorProcessor && stains != null && isBrightfield) {
				FloatProcessor[] fps = IJTools.colorDeconvolve((ColorProcessor)ip, stains);
				for (int i = 0; i < 3; i++) {
					StainVector stain = stains.getStain(i+1);
					if (!stain.isResidual()) {
						channels.put(stain.getName() + " OD", fps[i]);
						channelsCell.put(stain.getName() + " OD", fps[i]);
					}
				}
//				channels.put("Hematoxylin OD",  fps[0]);
//				if (stains.isH_DAB()) {
//					channels.put("DAB OD", fps[1]);
//					channelsCell.put("DAB OD", fps[1]);
//				}
//				else if (stains.isH_E()) {
//					channels.put("Eosin OD", fps[1]);
//					channelsCell.put("Eosin OD", fps[1]);
//				}
				

				if (!params.getParameters().get("detectionImageBrightfield").isHidden()) {
					if (params.getChoiceParameterValue("detectionImageBrightfield").equals(IMAGE_OPTICAL_DENSITY))
						fpDetection = IJTools.convertToOpticalDensitySum((ColorProcessor)ip, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
					else
						fpDetection = (FloatProcessor)fps[0].duplicate();
				}
				
				// Temporary test of the usefulness of RGB measurements...
//				channels.put("Red", ((ColorProcessor)ip).toFloat(0, null));
//				channels.put("Green", ((ColorProcessor)ip).toFloat(1, null));
//				channels.put("Blue", ((ColorProcessor)ip).toFloat(2, null));
				
			} //else {
			if (fpDetection == null) {
				List<ImageChannel> imageChannels = imageData.getServer().getMetadata().getChannels();
				if (ip instanceof ColorProcessor) {
					for (int c = 0; c < 3; c++) {
						String name = imageChannels.get(c).getName();
						channels.put(name, ((ColorProcessor)ip).toFloat(c, null));
					}
				} else {
					ImagePlus imp = pathImage.getImage();
					for (int c = 1; c <= imp.getNChannels(); c++) {
						String name = imageChannels.get(c-1).getName();
						if (channels.containsKey(name))
							logger.warn("Channel with duplicate name '{}' - will be skipped", name);
						else
							channels.put(name, imp.getStack().getProcessor(imp.getStackIndex(c, 0, 0)).convertToFloatProcessor());
//						channels.put("Channel " + c, imp.getStack().getProcessor(imp.getStackIndex(c, 0, 0)).convertToFloatProcessor());
					}
				}
				// For fluorescence, measure everything
				channelsCell.putAll(channels);
				
				// Try to get detection channel for fluorescence
				String detectionChannelName;
				if (!isBrightfield) {
					detectionChannelName = (String)params.getChoiceParameterValue("detectionImage");
					fpDetection = channels.get(detectionChannelName);
//					detectionChannel = params.getIntParameterValue("detectionImageFluorescence");
				}
				else throw new IllegalArgumentException("No valid detection channel is selected!");
//				if (detectionChannelName == null) {
//					detectionChannelName = imageChannels.get(detectionChannel-1).getName();
//					logger.warn("Unable to find specified Channel {} - will default to Channel 1", detectionChannel);
//				}
//				fpDetection = channels.get(detectionChannelName);
			}
			WatershedCellDetector detector2 = new WatershedCellDetector(fpDetection, channels, channelsCell, roi, pathImage);
			
			// Create or reset the PathObjects list
			if (pathObjects == null)
				pathObjects = new ArrayList<>();
			else
				pathObjects.clear();
	
			
			// Convert parameters where needed
			double sigma, medianRadius, backgroundRadius, minArea, maxArea, cellExpansion;
			if (pathImage.getPixelCalibration().hasPixelSizeMicrons()) {
				double pixelSize = pathImage.getPixelCalibration().getAveragedPixelSizeMicrons();
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
					isBrightfield ? params.getDoubleParameterValue("maxBackground") : Double.NEGATIVE_INFINITY,
					medianRadius,
					sigma,
					params.getDoubleParameterValue("threshold"),
					minArea,
					maxArea,
					true, // always use 'merge all' params.getBooleanParameterValue("mergeAll"),
					params.getBooleanParameterValue("watershedPostProcess"),
					params.getBooleanParameterValue("excludeDAB"),
					cellExpansion,
//					params.getBooleanParameterValue("limitExpansionByNucleusSize"),
					params.getBooleanParameterValue("smoothBoundaries"),
					params.getBooleanParameterValue("includeNuclei"),
					params.getBooleanParameterValue("makeMeasurements"),
					pathROI.getZ(),
					pathROI.getT());// && isBrightfield);
			
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
				int nPositive = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getNegative(null), false);
				int nNegative = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPositive(null), false);
				return String.format("%s (%.3f%% positive)", s, ((double)nPositive * 100.0 / (nPositive + nNegative)));			
			} else
				return s;
		}

		
	}
	
	
	private ParameterList buildParameterList(final ImageData<BufferedImage> imageData) { 
			
		ParameterList params = new ParameterList();
		// TODO: Use a better way to determining if pixel size is available in microns
//		params.addEmptyParameter("detectionParameters", "Detection parameters", true);

		String microns = IJ.micronSymbol + "m";
		
		params.addTitleParameter("Setup parameters");

		String defaultChannel = null;
		List<String> channelNames = new ArrayList<>();
		String[] nucleusGuesses = new String[] {"dapi", "hoechst", "nucleus", "nuclei", "nuclear", "hematoxylin", "haematoxylin"};
		for (ImageChannel channel : imageData.getServer().getMetadata().getChannels()) {
			String name = channel.getName();
			channelNames.add(name);
			if (defaultChannel == null) {
				String lower = name.toLowerCase();
				for (String guess : nucleusGuesses) {
					if (lower.contains(guess))
						defaultChannel = name;
				}
			}
		}
		if (defaultChannel == null)
			defaultChannel = channelNames.get(0);
		if (channelNames.size() != new HashSet<>(channelNames).size())
			logger.warn("Image contains duplicate channel names! This may be confusing for detection and analysis.");
		params.addChoiceParameter("detectionImage", "Detection channel", defaultChannel, channelNames, "Choose the channel that should be used for nucleus detection (e.g. DAPI)");

		params.addChoiceParameter("detectionImageBrightfield", "Detection image", IMAGE_HEMATOXYLIN, Arrays.asList(IMAGE_HEMATOXYLIN, IMAGE_OPTICAL_DENSITY),
				"Transformed image to which to apply the detection");

		params.addDoubleParameter("requestedPixelSizeMicrons", "Requested pixel size", .5, microns, 
				"Choose pixel size at which detection will be performed - higher values are likely to be faster, but may be less accurate; set <= 0 to use the full image resolution");
//		params.addDoubleParameter("requestedPixelSize", "Requested downsample factor", 1, "");

		
		params.addTitleParameter("Nucleus parameters");
		
		params.addDoubleParameter("backgroundRadiusMicrons", "Background radius", 8, microns, 
				"Radius for background estimation, should be > the largest nucleus radius, or <= 0 to turn off background subtraction");
		params.addDoubleParameter("medianRadiusMicrons", "Median filter radius", 0, microns,
				"Radius of median filter used to reduce image texture (optional)");
		params.addDoubleParameter("sigmaMicrons", "Sigma", 1.5, microns,
				"Sigma value for Gaussian filter used to reduce noise; increasing the value stops nuclei being fragmented, but may reduce the accuracy of boundaries");
		params.addDoubleParameter("minAreaMicrons", "Minimum area", 10, microns+"^2",
				"Detected nuclei with an area < minimum area will be discarded");
		params.addDoubleParameter("maxAreaMicrons", "Maximum area", 400, microns+"^2",
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

		params.addTitleParameter("Intensity parameters");
		params.addDoubleParameter("threshold", "Threshold", 0.1, null,
				"Intensity threshold - detected nuclei must have a mean intensity >= threshold");
//		params.addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 2.5,
//				"Intensity threshold - detected nuclei must have a mean intensity >= threshold");
		params.addDoubleParameter("maxBackground", "Max background intensity", 2, null,
				"If background radius > 0, detected nuclei occurring on a background > max background intensity will be discarded");
		
//		params.addBooleanParameter("mergeAll", "Merge all", true);
		params.addBooleanParameter("watershedPostProcess", "Split by shape", true,
				"Split merged detected nuclei based on shape ('roundness')");
		params.addBooleanParameter("excludeDAB", "Exclude DAB (membrane staining)", false,
				"Set to 'true' if regions of high DAB staining should not be considered nuclei; useful if DAB stains cell membranes");
		
		
		params.addTitleParameter("Cell parameters");

		params.addDoubleParameter("cellExpansionMicrons", "Cell expansion", 5, microns, 0, 25,
				"Amount by which to expand detected nuclei to approximate the full cell area");
		params.addDoubleParameter("cellExpansion", "Cell expansion", 5, "px",
				"Amount by which to expand detected nuclei to approximate the full cell area");
		
//		params.addBooleanParameter("limitExpansionByNucleusSize", "Limit cell expansion by nucleus size", false, "If checked, nuclei will not be expanded by more than their (estimated) smallest diameter in any direction - may give more realistic results for smaller, or 'thinner' nuclei");
			
		params.addBooleanParameter("includeNuclei", "Include cell nucleus", true,
				"If cell expansion is used, optionally include/exclude the nuclei within the detected cells");
		
		
		params.addTitleParameter("General parameters");
		params.addBooleanParameter("smoothBoundaries", "Smooth boundaries", true,
				"Smooth the detected nucleus/cell boundaries");
		params.addBooleanParameter("makeMeasurements", "Make measurements", true,
				"Add default shape & intensity measurements during detection");
		
		return params;
	}
	
	@Override
	protected boolean parseArgument(ImageData<BufferedImage> imageData, String arg) {
		if (arg != null) {
			// We don't want running with old scripts to silently produce the wrong result, so instead we check
			Map<String, String> map = GeneralTools.parseArgStringValues(arg);
			if (map.containsKey("detectionImageFluorescence"))
				throw new IllegalArgumentException("'detectionImageFluorescence' is not supported in this version of QuPath - use 'detectionImage' instead");
		}
		return super.parseArgument(imageData, arg);
	}

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		
		if (!parametersInitialized) {
			params = buildParameterList(imageData);
		}
		
		// Show/hide parameters depending on whether the pixel size is known
		Map<String, Parameter<?>> map = params.getParameters();
		boolean pixelSizeKnown = imageData.getServer() != null && imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		for (String name : micronParameters)
			map.get(name).setHidden(!pixelSizeKnown);
		for (String name : pixelParameters)
			map.get(name).setHidden(pixelSizeKnown);
		
		params.setHiddenParameters(!pixelSizeKnown, micronParameters);
		params.setHiddenParameters(pixelSizeKnown, pixelParameters);

		boolean isBrightfield = imageData.isBrightfield();
		params.setHiddenParameters(!isBrightfield, brightfieldParameters);
		params.setHiddenParameters(isBrightfield, fluorescenceParameters);
		
		if (!isBrightfield) {
			if (imageData.getServer().getPixelType().getBitsPerPixel() > 8)
				((DoubleParameter)params.getParameters().get("threshold")).setValue(100.0);
			else
				((DoubleParameter)params.getParameters().get("threshold")).setValue(25.0);
		}

//		map.get("detectionImageBrightfield").setHidden(imageData.getColorDeconvolutionStains() == null);

		map.get("excludeDAB").setHidden(imageData.getColorDeconvolutionStains() == null || !imageData.getColorDeconvolutionStains().isH_DAB());
		
//		map.get("makeMeasurements").setHidden(!imageData.isBrightfield());

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
		
		
		private boolean refineBoundary = true; // TODO: Consider making this variable accessible
		
		
		private double backgroundRadius = 15;
		private double maxBackground = 0.3;
		
		private int z = 0, t = 0;
		
		private boolean lastRunCompleted = false;
		
		private boolean includeNuclei = true;
		private double cellExpansion = 0;
		
		private double minArea = 0;
		private double maxArea = 0;
		
		private double medianRadius = 2;
		private double sigma = 2.5;
		private double threshold = 0.3;
		private boolean mergeAll = true;
		private boolean watershedPostProcess = true; // TODO: COMBINE WITH MERGEALL OPTION
		private boolean excludeDAB = false;
		private boolean smoothBoundaries = false;

//		private boolean limitExpansionByNucleusSize = false;

		private boolean makeMeasurements = true;
		
		private Roi roi = null;
		private FloatProcessor fpDetection = null;
		private Map<String, FloatProcessor> channels = new LinkedHashMap<>(); // Map of channels to measure for nuclei only, and their names
		private Map<String, FloatProcessor> channelsCell = new LinkedHashMap<>(); // Map of channels to measure for cell/cytoplasm, and their names
		private ImageProcessor ipToMeasure = null;
		private List<PolygonRoi> rois = null;
		private ByteProcessor bpLoG = null;
		
		private List<PolygonRoi> roisNuclei = new ArrayList<>();
		private List<PathObject> pathObjects = new ArrayList<>();
		
		private PathImage<ImagePlus> pathImage = null;
		
		public WatershedCellDetector(FloatProcessor fpDetection, Map<String, FloatProcessor> channels, Map<String, FloatProcessor> channelsCell, Roi roi, PathImage<ImagePlus> pathImage) {
			this.fpDetection = fpDetection;
			if (channels != null)
				this.channels.putAll(channels);
			if (channelsCell != null)
				this.channelsCell.putAll(channelsCell);
			this.roi = roi;
			this.pathImage = pathImage;
			Prefs.setThreads(1);
		}
		
		
		
		public static ByteProcessor limitedOpeningByReconstruction(final ImageProcessor ip, final ImageProcessor ipBackground, final double radius, final double maxBackground) {
			// Apply (initial) morphological opening
			final RankFilters rf = new RankFilters();
			ipBackground.setRoi(ip.getRoi());
			rf.rank(ipBackground, radius, RankFilters.MIN);
			
			// Mask out any above-threshold background pixels & their surroundings
			ByteProcessor bpMask = null;
			if (!Double.isNaN(maxBackground) && maxBackground > 0) {
				int w = ip.getWidth();
				int h = ip.getHeight();
				for (int i = 0; i < w * h; i++) {
					if (ipBackground.getf(i) > maxBackground) {
						if (bpMask == null)
							bpMask = new ByteProcessor(w, h);
						bpMask.setf(i, 1f);
					}
				}
				// Apply mask if required
				if (bpMask != null) {
					rf.rank(bpMask, radius*2, RankFilters.MAX);
					for (int i = 0; i < w * h; i++) {
						if (bpMask.getf(i) != 0f) {
							ipBackground.setf(i, Float.NEGATIVE_INFINITY);
						}
					}
				}
			}
			
			// Apply the morphological reconstruction
			MorphologicalReconstruction.morphologicalReconstruction(ipBackground, ip);
			return bpMask;
		}
		
		
		
		private void doDetection(boolean regenerateROIs) {
			int width = fpDetection.getWidth();
			int height = fpDetection.getHeight();
//			Prefs.setThreads(1);
			lastRunCompleted = false;
			pathObjects.clear();
			ByteProcessor bp = null;
			ByteProcessor bpBackgroundMask = null;
			fpDetection.setRoi(roi);
			if (regenerateROIs) {
				rois = null;
				bpLoG = null;
				
				// Use Laplacian of Gaussian filtering followed by watershed transform to determine possible nucleus segments
				// Result will be a dramatic over-segmentation...
				FloatProcessor fpLoG = (FloatProcessor)fpDetection.duplicate();

				// Start off with a median filter to reduce texture, if necessary
				RankFilters rf = new RankFilters();
				if (medianRadius > 0)
					rf.rank(fpLoG, medianRadius, RankFilters.MEDIAN);

				//--------NEW--------
				if (excludeDAB && channels.containsKey("Hematoxylin OD") && channels.containsKey("DAB OD")) {
					// If we are avoiding DAB, set pixels away from potential nuclei to zero
					FloatProcessor fpDAB = channels.get("DAB OD");
					fpDAB.setRoi(roi);
					ByteProcessor bpH = SimpleThresholding.greaterThanOrEqual(channels.get("Hematoxylin OD"), fpDAB);
					bpH.multiply(1.0/255.0);
					rf.rank(bpH, 2.5, RankFilters.MEDIAN);
					rf.rank(bpH, 2.5, RankFilters.MAX);
					fpLoG.copyBits(bpH, 0, 0, Blitter.MULTIPLY);
				}
				//--------END_NEW--------
				
				// Subtract background first, if needed
				if (backgroundRadius > 0) {
					ImageProcessor ipBackground = fpLoG.duplicate();
					bpBackgroundMask = limitedOpeningByReconstruction(fpLoG, ipBackground, backgroundRadius, maxBackground);
					fpLoG.copyBits(ipBackground, 0, 0, Blitter.SUBTRACT);
					ipToMeasure = fpLoG.duplicate();
				} else {
					ipToMeasure = fpDetection;
				}
				
				// Apply (approximation of) Laplacian of Gaussian filter
				fpLoG.blurGaussian(sigma);
				fpLoG.convolve(new float[]{0, -1, 0, -1, 4, -1, 0, -1, 0}, 3, 3);
				
				// Threshold the main LoG image
				bpLoG = SimpleThresholding.thresholdAbove(fpLoG, 0f);
				// Need to set the threshold very slightly above zero for ImageJ
				// TODO: DECIDE ON USING MY WATERSHED OR IMAGEJ'S....
				fpLoG.setRoi(roi);
				
				ImageProcessor ipTemp = MorphologicalReconstruction.findRegionalMaxima(fpLoG, 0.001f, false);
				ImageProcessor ipLabels = RoiLabeling.labelImage(ipTemp, 0, false);
				Watershed.doWatershed(fpLoG, ipLabels, 0, false);
				
				ipLabels.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
				// TODO: Consider 4/8 connectivity for watershed nucleus ROIs
				rois = RoiLabeling.getFilledPolygonROIs(ipLabels, Wand.FOUR_CONNECTED);			
				
				if (Thread.currentThread().isInterrupted())
					return;
			} 
			
			if (bp == null)
				bp = new ByteProcessor(width, height);	
			
//			// TODO: Consider application of an automated threshold
//			if (threshold < 0) {
//				ipToMeasure.resetRoi();
//				ImageStatistics stats = ipToMeasure.getStatistics();
//				threshold = stats.mean;// + stats.stdDev;
//				logger.info("Mean threshold set: " + threshold);
//			}

			bp.setValue(255);
			for (Roi r : rois) {
				// Perform mean intensity check - skip if below threshold
				ipToMeasure.setRoi(r);
				double mean = ipToMeasure.getStatistics().mean;
				if (mean <= threshold) {
					continue;
				}
				// Perform background intensity check, if required
				if (bpBackgroundMask != null) {
					bpBackgroundMask.setRoi(r);
					if (bpBackgroundMask.getStatistics().mean > 0)
						continue;				
				}
				// Fill the ROI to keep it
				bp.fill(r);
			}
			
			if (Thread.currentThread().isInterrupted())
				return;
			
			// Create a new, updated binary image with the potential nucleus regions & (optionally) merge these
			bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			if (mergeAll) {
				bp.filter(ImageProcessor.MAX);
				bp.copyBits(bpLoG, 0, 0, Blitter.AND);	
				if (watershedPostProcess) {
					// TODO: ARRANGE A MORE EFFICIENT FILL HOLES
					List<PolygonRoi> rois2 = RoiLabeling.getFilledPolygonROIs(bp, Wand.FOUR_CONNECTED);
					bp.setValue(255);
					for (Roi r : rois2)
						bp.fill(r);
					new EDM().toWatershed(bp);
				}
			}
			// TODO: Look at the better boundary clearing implemented in Fast_nucleus_counts
			if (roi != null)
				RoiLabeling.clearOutside(bp, roi);
			
			// Locate nucleus ROIs
			bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			
			
			if (IJ.debugMode) {
				IJTools.quickShowImage("Binary", bp.duplicate());
			}

			//----------------------------
			// MINOR BOUNDARY REFINEMENT
			// The idea is that Gaussian smoothing tends to cause the boundaries of 'thin' nuclei to be overestimated;
			// this uses a smaller filter to correct instances where the boundary has moved by just one pixel
			if (refineBoundary && sigma > 1.5) {
				FloatProcessor fpBoundaryCleanup = (FloatProcessor)fpDetection.duplicate();
				fpBoundaryCleanup.blurGaussian(1);
				fpBoundaryCleanup.convolve(new float[]{0, -1, 0, -1, 4, -1, 0, -1, 0}, 3, 3);
				ByteProcessor bp2 = SimpleThresholding.thresholdAbove(fpBoundaryCleanup, 0f);
				bp2.copyBits(bp, 0, 0, Blitter.MIN); // Remove everything not detected in bp
				bp.filter(ByteProcessor.MIN);
				bp.copyBits(bp2, 0, 0, Blitter.MAX);
				regenerateROIs = true;
			}
			
			//----------------------------
			
			roisNuclei = RoiLabeling.getFilledPolygonROIs(bp, Wand.FOUR_CONNECTED);

			if (Thread.currentThread().isInterrupted())
				return;
			
			// Remove nuclei with areas outside the permitted range - updating the binary image as we go
			if (minArea > 0 || maxArea > 0) {
				bp.setValue(0);
				Iterator<PolygonRoi> iter = roisNuclei.iterator();
				while (iter.hasNext()) {
					Roi roiTemp = iter.next();
					ipToMeasure.setRoi(roiTemp);
					ImageStatistics stats = ImageStatistics.getStatistics(ipToMeasure, Measurements.AREA | Measurements.MEAN, null);
					double area = stats.pixelCount;
					if ((stats.mean < threshold) || (minArea > 0 && area < minArea) || (maxArea > 0 && area > maxArea)) {
						iter.remove();
						bp.fill(roiTemp);
					}
				}
				ipToMeasure.resetRoi();
			}
			
			
			// Label nuclei
			ShortProcessor ipLabels = new ShortProcessor(width, height);
			RoiLabeling.labelROIs(ipLabels, roisNuclei);
			
			// Measure nuclei for all required channels
			Map<String, List<RunningStatistics>> statsMap = new LinkedHashMap<>();
			if (makeMeasurements) {
				SimpleImage imgLabels = new PixelImageIJ(ipLabels);
				for (String key : channels.keySet()) {
					List<RunningStatistics> statsList = StatisticsHelper.createRunningStatisticsList(roisNuclei.size());
					StatisticsHelper.computeRunningStatistics(new PixelImageIJ(channels.get(key)), imgLabels, statsList);
					statsMap.put(key, statsList);
				}
			}
			
			if (Thread.currentThread().isInterrupted())
				return;

			double downsample = pathImage.getDownsampleFactor();
			double downsampleSqrt = Math.sqrt(downsample);
			
			// Create nucleus objects
			// TODO: Set the measurement capacity to improve efficiency
			List<PathObject> nucleiObjects = new ArrayList<>();
			Calibration cal = pathImage.getImage().getCalibration();
			ImagePlane plane = ImagePlane.getPlane(z, t);
			for (int i = 0; i < roisNuclei.size(); i++) {
				PolygonRoi rOrig = roisNuclei.get(i);
				
				PolygonRoi r = rOrig;
				if (smoothBoundaries) {
					r = new PolygonRoi(rOrig.getInterpolatedPolygon(1, false), Roi.POLYGON);
					r = smoothPolygonRoi(r);
					r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2, r.getNCoordinates()*0.1), false), Roi.POLYGON);
				}
				
				PolygonROI pathROI = IJTools.convertToPolygonROI(r, cal, downsample, plane);
				
				if (smoothBoundaries) {
					pathROI = ShapeSimplifier.simplifyPolygon(pathROI, downsampleSqrt/2);
				}
				
				// Create a new shared measurement list
				MeasurementList measurementList = MeasurementListFactory.createMeasurementList(makeMeasurements ? 30 : 0, MeasurementList.MeasurementListType.FLOAT);
				
				if (makeMeasurements) {
					ObjectMeasurements.addShapeStatistics(measurementList, r, fpDetection, cal, "Nucleus: ");
	
					for (String key : channels.keySet()) {
						List<RunningStatistics> statsList = statsMap.get(key);
						RunningStatistics stats = statsList.get(i);
						measurementList.addMeasurement("Nucleus: " + key + " mean", stats.getMean());
						measurementList.addMeasurement("Nucleus: " + key + " sum", stats.getSum());
						measurementList.addMeasurement("Nucleus: " + key + " std dev", stats.getStdDev());
						measurementList.addMeasurement("Nucleus: " + key + " max", stats.getMax());
						measurementList.addMeasurement("Nucleus: " + key + " min", stats.getMin());
						measurementList.addMeasurement("Nucleus: " + key + " range", stats.getRange());
					}
				}
				
				// TODO: It would be more efficient to measure the hematoxylin intensities along with the shapes
				PathObject pathObject = PathObjects.createDetectionObject(pathROI, null, measurementList);
				nucleiObjects.add(pathObject);

			}
			
			if (Thread.currentThread().isInterrupted())
				return;

			List<Roi> roisCellsList = null;
			
			// Optionally expand the nuclei to become cells
			if (cellExpansion > 0) {
				FloatProcessor fpEDM = new EDM().makeFloatEDM(bp, (byte)255, false);
				fpEDM.multiply(-1);
				
				double cellExpansionThreshold = -cellExpansion;
				
				// Create cell ROIs
				ImageProcessor ipLabelsCells = ipLabels.duplicate();
				Watershed.doWatershed(fpEDM, ipLabelsCells, cellExpansionThreshold, false);
				PolygonRoi[] roisCells = RoiLabeling.labelsToFilledROIs(ipLabelsCells, roisNuclei.size());
				
				// Compute cell DAB stats
				Map<String, List<RunningStatistics>> statsMapCell = new LinkedHashMap<>();
				if (makeMeasurements) {
					for (String key : channelsCell.keySet()) {
						List<RunningStatistics> statsList = StatisticsHelper.createRunningStatisticsList(roisNuclei.size());
						StatisticsHelper.computeRunningStatistics(new PixelImageIJ(channelsCell.get(key)), new PixelImageIJ(ipLabelsCells), statsList);
						statsMapCell.put(key, statsList);
					}
				}
							
				// Create labelled image for cytoplasm, i.e. remove all nucleus pixels
				// TODO: Make a buffer zone between nucleus and cytoplasm!
				for (int i = 0; i < ipLabels.getWidth() * ipLabels.getHeight(); i++) {
					if (ipLabels.getf(i) != 0)
						ipLabelsCells.setf(i, 0f);
				}
				
				// Compute cytoplasm stats
				Map<String, List<RunningStatistics>> statsMapCytoplasm = new LinkedHashMap<>();
				if (makeMeasurements) {
					for (String key : channelsCell.keySet()) {
						List<RunningStatistics> statsList = StatisticsHelper.createRunningStatisticsList(roisNuclei.size());
						StatisticsHelper.computeRunningStatistics(new PixelImageIJ(channelsCell.get(key)), new PixelImageIJ(ipLabelsCells), statsList);
						statsMapCytoplasm.put(key, statsList);
					}
				}
				
				
				// Create cell objects
				roisCellsList = new ArrayList<>(roisCells.length); // In case we need texture measurements, store all cell ROIs
				for (int i = 0; i < roisCells.length; i++) {
					PolygonRoi r = roisCells[i];
					if (r == null)
						continue;
					
					if (smoothBoundaries) {
						r = new PolygonRoi(r.getInterpolatedPolygon(1, false), Roi.POLYGON);
						r = smoothPolygonRoi(r);
						r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2, r.getNCoordinates()*0.1), false), Roi.POLYGON);
					}
//					if (smoothBoundaries)
//						r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2, r.getNCoordinates()*0.1), false), Roi.POLYGON); // TODO: Check this smoothing - it can be troublesome, causing nuclei to be outside cells
////						r = smoothPolygonRoi(r);

					PolygonROI pathROI = IJTools.convertToPolygonROI(r, cal, downsample, plane);
					if (smoothBoundaries)
						pathROI = ShapeSimplifier.simplifyPolygon(pathROI, downsampleSqrt/2.0);

					
					MeasurementList measurementList = null;
					PathObject nucleus = null;
					if (includeNuclei) {
						// Use the nucleus' measurement list
						nucleus = nucleiObjects.get(i);
						measurementList = nucleus.getMeasurementList();					
					} else {
						// Create a new measurement list
						measurementList = MeasurementListFactory.createMeasurementList(makeMeasurements ? 12 : 0, MeasurementList.MeasurementListType.GENERAL);
					}
									
					// Add cell shape measurements
					if (makeMeasurements) {
						ObjectMeasurements.addShapeStatistics(measurementList, r, fpDetection, pathImage.getImage().getCalibration(), "Cell: ");
	//					ObjectMeasurements.computeShapeStatistics(pathObject, pathImage, fpH, pathImage.getImage().getCalibration());
	
						// Add cell measurements
						for (String key : channelsCell.keySet()) {
							if (statsMapCell.containsKey(key)) {
								RunningStatistics stats = statsMapCell.get(key).get(i);
								measurementList.addMeasurement("Cell: " + key + " mean", stats.getMean());
								measurementList.addMeasurement("Cell: " + key + " std dev", stats.getStdDev());
								measurementList.addMeasurement("Cell: " + key + " max", stats.getMax());
								measurementList.addMeasurement("Cell: " + key + " min", stats.getMin());
		//						pathObject.addMeasurement("Cytoplasm: " + key + " range", stats.getRange());
							}
						}
							
							// Add cytoplasm measurements
						for (String key : channelsCell.keySet()) {
							if (statsMapCytoplasm.containsKey(key)) {
								RunningStatistics stats = statsMapCytoplasm.get(key).get(i);
								measurementList.addMeasurement("Cytoplasm: " + key + " mean", stats.getMean());
								measurementList.addMeasurement("Cytoplasm: " + key + " std dev", stats.getStdDev());
								measurementList.addMeasurement("Cytoplasm: " + key + " max", stats.getMax());
								measurementList.addMeasurement("Cytoplasm: " + key + " min", stats.getMin());
		//						pathObject.addMeasurement("Cytoplasm: " + key + " range", stats.getRange());
							}
						}
						
						// Add nucleus area ratio, if available
						if (nucleus != null && nucleus.getROI().isArea()) {
							double nucleusArea = nucleus.getROI().getArea();
							double cellArea = pathROI.getArea();
							measurementList.addMeasurement("Nucleus/Cell area ratio", Math.min(nucleusArea / cellArea, 1.0));
	//						measurementList.addMeasurement("Nucleus/Cell expansion", cellArea - nucleusArea);
						}
					}

					
					// Create & store the cell object
					PathObject pathObject = PathObjects.createCellObject(pathROI, nucleus == null ? null : nucleus.getROI(), null, measurementList);
					pathObjects.add(pathObject);
					
					roisCellsList.add(r);
				}
			} else {
				pathObjects.addAll(nucleiObjects);
			}
			
			// Close the measurement lists
			for (PathObject pathObject : pathObjects)
				pathObject.getMeasurementList().close();
			
			// Sometimes smoothing can cause nuclei of cell boundaries to be removed - in this case, 
			// filter out the invalid ROIs now
			int sizeBefore = pathObjects.size();
			pathObjects.removeIf(p -> PathObjectTools.getROI(p, false).isEmpty() ||
					PathObjectTools.getROI(p, true).isEmpty());
			int sizeAfter = pathObjects.size();
			if (sizeBefore != sizeAfter) {
				logger.debug("Filtered out {} invalid cells (empty ROIs)", sizeBefore - sizeAfter);
			}
			
			lastRunCompleted = true;
		}
		
		
		
		
		private static PolygonRoi smoothPolygonRoi(PolygonRoi r) {
			FloatPolygon poly = r.getFloatPolygon();
			FloatPolygon poly2 = new FloatPolygon();
			int nPoints = poly.npoints;
			for (int i = 0; i < nPoints; i += 2) {
				int iMinus = (i + nPoints - 1) % nPoints;
				int iPlus = (i + 1) % nPoints;
				poly2.addPoint((poly.xpoints[iMinus] + poly.xpoints[iPlus] + poly.xpoints[i])/3, 
						(poly.ypoints[iMinus] + poly.ypoints[iPlus] + poly.ypoints[i])/3);
			}
//			return new PolygonRoi(poly2, r.getType());
			return new PolygonRoi(poly2, Roi.POLYGON);
		}
		
		
		
		public List<PathObject> getPathObjects() {
			return pathObjects;
		}
		
		
//		public void runDetection(double backgroundRadius, double maxBackground, double medianRadius, double sigma, double threshold, double minArea, double maxArea, boolean mergeAll, boolean watershedPostProcess, boolean excludeDAB, double cellExpansion, boolean limitExpansionByNucleusSize, boolean smoothBoundaries, boolean includeNuclei, boolean makeMeasurements) {
		public void runDetection(double backgroundRadius, double maxBackground, double medianRadius, double sigma, double threshold, double minArea, double maxArea, boolean mergeAll, boolean watershedPostProcess, boolean excludeDAB, double cellExpansion, boolean smoothBoundaries, boolean includeNuclei, boolean makeMeasurements, int z, int t) {
			
			boolean updateNucleusROIs = rois == null || bpLoG == null;
			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.medianRadius != medianRadius;
			this.medianRadius = medianRadius;
			
			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.t != t || this.z != z;
			this.z = z;
			this.t = t;
			
			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.backgroundRadius != backgroundRadius;
			this.backgroundRadius = backgroundRadius;

			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.sigma != sigma;
			this.sigma = sigma;
			
			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.excludeDAB != excludeDAB;
			this.excludeDAB = excludeDAB;

			boolean updateAnything = updateNucleusROIs || !lastRunCompleted;

			updateAnything = updateAnything ? updateAnything : this.minArea != minArea;
			this.minArea = minArea;

			updateAnything = updateAnything ? updateAnything : this.maxArea != maxArea;
			this.maxArea = maxArea;

			updateAnything = updateAnything ? updateAnything : this.maxBackground != maxBackground;
			this.maxBackground = maxBackground;

			updateAnything = updateAnything ? updateAnything : this.threshold != threshold;
			this.threshold = threshold;

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
			
//			updateAnything = updateAnything ? updateAnything : this.limitExpansionByNucleusSize != limitExpansionByNucleusSize;
//			this.limitExpansionByNucleusSize = limitExpansionByNucleusSize;
			
//			if (!updateAnything)
//				return;
			
			doDetection(updateNucleusROIs);
			
		}
		
		
	}
	
	
	@Override
	public String getDescription() {
		return "Default cell detection algorithm for brightfield images with nuclear or cytoplasmic staining";
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
//		double pxSize = getPreferredPixelSizeMicrons(imageData, params);
		double pxSize = imageData.getServer().getPixelCalibration().getAveragedPixelSizeMicrons();
		if (!Double.isFinite(pxSize))
			return params.getDoubleParameterValue("cellExpansion") > 0 ? 25 : 10;
		double nucleusRadiusMicrons = 10.0;
		double expansionMicrons = nucleusRadiusMicrons;
		double cellExpansion = params.getDoubleParameterValue("cellExpansionMicrons");
		if (cellExpansion > 0)
			expansionMicrons += params.getDoubleParameterValue("cellExpansionMicrons");
		int overlap = (int)(expansionMicrons / pxSize * 2.0);
//		System.out.println("Tile overlap: " + overlap + " pixels");
		return overlap;
	}
		
}