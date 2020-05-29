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

package qupath.imagej.detect.cells;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.filter.EDM;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
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
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;

/**
 * Cell detection that takes into consideration membrane staining.
 * <p>
 * This command only works (somewhat) for hematoxylin and DAB staining.
 * If membrane quantification is not required, {@link WatershedCellDetection} is more robust and versatile.
 * 
 * @author Pete Bankhead
 *
 */
public class WatershedCellMembraneDetection extends AbstractTileableDetectionPlugin<BufferedImage> {

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
	
	static String IMAGE_OPTICAL_DENSITY = "Optical density sum";
	static String IMAGE_HEMATOXYLIN = "Hematoxylin";
	
	private ParameterList params;
	
	/**
	 * Default constructor.
	 */
	public WatershedCellMembraneDetection() {
		
		Prefs.setThreads(1);
		params = new ParameterList();
		// TODO: Use a better way to determining if pixel size is available in microns
//		params.addEmptyParameter("detectionParameters", "Detection parameters", true);

		String microns = GeneralTools.micrometerSymbol();
		
		params.addTitleParameter("Setup parameters");
		
		params.addChoiceParameter("detectionImageBrightfield", "Choose detection image", IMAGE_HEMATOXYLIN, Arrays.asList(IMAGE_HEMATOXYLIN, IMAGE_OPTICAL_DENSITY),
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

		params.addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 2.5,
				"Intensity threshold - detected nuclei must have a mean intensity >= threshold");
		params.addDoubleParameter("maxBackground", "Max background intensity", 2, null,
				"If background radius > 0, detected nuclei occurring on a background > max background intensity will be discarded");
		
//		params.addBooleanParameter("mergeAll", "Merge all", true);
		params.addBooleanParameter("watershedPostProcess", "Split by shape", true,
				"Split merged detected nuclei based on shape ('roundness')");
		params.addBooleanParameter("excludeDAB", "Exclude DAB (membrane staining)", true,
				"Set to 'true' if regions of high DAB staining should not be considered nuclei; useful if DAB stains cell membranes");
		
		
		params.addTitleParameter("Cell parameters");

		params.addDoubleParameter("cellExpansionMicrons", "Cell expansion", 8, microns, 0, 25,
				"Amount by which to expand detected nuclei to approximate the full cell area");
		params.addDoubleParameter("cellExpansion", "Cell expansion", 10, "px",
				"Amount by which to expand detected nuclei to approximate the full cell area");
		
		params.addBooleanParameter("limitExpansionByNucleusSize", "Limit cell expansion by nucleus size", false, "If checked, nuclei will not be expanded by more than their (estimated) smallest diameter in any direction - may give more realistic results for smaller, or 'thinner' nuclei");
			
		params.addBooleanParameter("includeNuclei", "Include cell nucleus", true,
				"If cell expansion is used, optionally include/exclude the nuclei within the detected cells");
		
		
		params.addTitleParameter("General parameters");
		params.addBooleanParameter("smoothBoundaries", "Smooth boundaries", false,
				"Smooth the detected nucleus/cell boundaries");
		params.addBooleanParameter("makeMeasurements", "Make measurements", true,
				"Add default shape & intensity measurements during detection");
	}
	
	
	static class CellDetector implements ObjectDetector<BufferedImage> {
	
		private List<PathObject> pathObjects = null;
		
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
			// TODO: Give a sensible error
			if (pathROI == null)
				return null;
			// Get a PathImage if we have a new ROI
//			boolean imageChanged = false;
			PathImage<ImagePlus> pathImage = null;
//			if (pathImage == null || lastServerPath == null || !lastServerPath.equals(imageData.getServerPath()) || !pathROI.equals(this.pathROI)) {
				ImageServer<BufferedImage> server = imageData.getServer();
				pathImage = IJTools.convertToImagePlus(server, RegionRequest.createInstance(server.getPath(),
						ServerTools.getDownsampleFactor(server, getPreferredPixelSizeMicrons(imageData, params)),
						pathROI));
//				System.out.println("Downsample: " + pathImage.getDownsampleFactor());
//				this.pathROI = pathROI;
//				lastServerPath = imageData.getServerPath();
//				imageChanged = true;
//			}
			// Create a detector if we don't already have one for this image
			boolean isBrightfield = imageData.isBrightfield();
			FloatProcessor fpDetection = null, fpH = null, fpDAB = null;
//			if (detector2 == null || imageChanged || stains != imageData.getColorDeconvolutionStains()) {
				ImageProcessor ip = pathImage.getImage().getProcessor();
				ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
				if (ip instanceof ColorProcessor && stains != null) {
					
					FloatProcessor[] fps = IJTools.colorDeconvolve((ColorProcessor)ip, stains);
					fpH = fps[0];
					if (stains.isH_DAB())
						fpDAB = fps[1];
					else
						fpDAB = null; // At this point, only DAB is quantified (eosin ignored for H&E)
					
					if (!params.getParameters().get("detectionImageBrightfield").isHidden()) {
						if (params.getChoiceParameterValue("detectionImageBrightfield").equals(IMAGE_OPTICAL_DENSITY))
							fpDetection = IJTools.convertToOpticalDensitySum((ColorProcessor)ip, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
						else
							fpDetection = (FloatProcessor)fpH.duplicate();
					}

					
				}
				if (fpDetection == null) {
					// TODO: Deal with fluorescence
					fpDetection = ip.convertToFloatProcessor();
					fpH = ip.convertToFloatProcessor();
					fpDAB = null;
				}
//			}
			Roi roi = null;
			if (pathROI != null)
				roi = IJTools.convertToIJRoi(pathROI, pathImage);
			WatershedCellDetector detector2 = new WatershedCellDetector(fpDetection, fpH, fpDAB, roi, pathImage);
			
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
					params.getDoubleParameterValue("maxBackground"),
					medianRadius,
					sigma,
					params.getDoubleParameterValue("threshold"),
					minArea,
					maxArea,
					true, // always use 'merge all' params.getBooleanParameterValue("mergeAll"),
					params.getBooleanParameterValue("watershedPostProcess"),
					params.getBooleanParameterValue("excludeDAB"),
					cellExpansion,
					params.getBooleanParameterValue("limitExpansionByNucleusSize"),
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
				int nPositive = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getPositive(null), false);
				int nNegative = PathObjectTools.countObjectsWithClass(pathObjects, PathClassFactory.getNegative(null), false);
				return String.format("%s (%.3f%% positive)", s, ((double)nPositive * 100.0 / (nPositive + nNegative)));			
			} else
				return s;
		}

		
	}
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		
		// Show/hide parameters depending on whether the pixel size is known
		Map<String, Parameter<?>> map = params.getParameters();
		boolean pixelSizeKnown = imageData.getServer() != null && imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		for (String name : micronParameters)
			map.get(name).setHidden(!pixelSizeKnown);
		for (String name : pixelParameters)
			map.get(name).setHidden(pixelSizeKnown);

		map.get("detectionImageBrightfield").setHidden(imageData.getColorDeconvolutionStains() == null);

		map.get("excludeDAB").setHidden(imageData.getColorDeconvolutionStains() == null || !imageData.getColorDeconvolutionStains().isH_DAB());
		
		map.get("makeMeasurements").setHidden(!imageData.isBrightfield());

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
		
		private boolean refineBoundary = true; // TODO: Consider making this variable accessible
		
		
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
		private boolean mergeAll = true;
		private boolean watershedPostProcess = true; // TODO: COMBINE WITH MERGEALL OPTION
		private boolean excludeDAB = false;
		private boolean smoothBoundaries = false;

		private boolean limitExpansionByNucleusSize = false;

		private boolean makeMeasurements = true;
		
		private Roi roi = null;
		private FloatProcessor fpDetection = null;
		private FloatProcessor fpH = null;
		private FloatProcessor fpDAB = null;
		private ImageProcessor ipToMeasure = null;
		private ImageProcessor ipBackground = null;
		private List<PolygonRoi> rois = null;
		private ByteProcessor bpLoG = null;
		
		private List<PolygonRoi> roisNuclei = new ArrayList<>();
		private List<PathObject> pathObjects = new ArrayList<>();
		
		private PathImage<ImagePlus> pathImage = null;
		
		public WatershedCellDetector(FloatProcessor fpDetection, FloatProcessor fpH, FloatProcessor fpDAB, Roi roi, PathImage<ImagePlus> pathImage) {
			this.fpDetection = fpDetection;
			this.fpH = fpH;
			this.fpDAB = fpDAB;
			this.roi = roi;
			this.pathImage = pathImage;
//			Prefs.setThreads(1);
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
//				} else {
//					// Don't return a mask - all pixels are ok
//					System.out.println("Skipping background mask!");
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
								
////				fpLoG.copyBits(fpDAB, 0, 0, Blitter.ADD); // Testing is adding the optical densities helps...
//				// Check we have some above-threshold pixels - if not, don't do more
//				int indAbove = 0;
//				int n = fpLoG.getWidth() * fpLoG.getHeight();
//				while (indAbove < n && fpLoG.getf(indAbove) < threshold)
//					indAbove++;
//				if (indAbove == n)
//					return;

				// Start off with a median filter to reduce texture, if necessary
				RankFilters rf = new RankFilters();
				if (medianRadius > 0)
					rf.rank(fpLoG, medianRadius, RankFilters.MEDIAN);

				// Subtract background first, if needed
				if (backgroundRadius > 0) {
					
//					// Could mask out definitely-background pixels if we wanted (and could be confident in background radius)... although it doesn't actually help all that much
//					ByteProcessor bpPossible = SimpleThresholding.thresholdAboveEquals(fpH, (float)threshold);
//					rf.rank(bpPossible, backgroundRadius, RankFilters.MAX);
//					if (roi != null)
//						ROILabeling.fillOutside(bpPossible, roi, 0);
//					for (int i = 0; i < width * height; i++) {
//						if (bpPossible.get(i) == 0)
//							fpLoG.setf(i, Float.NEGATIVE_INFINITY);
//					}
////					bpPossible.max(1);
////					new ImagePlus("Possible", bpPossible.duplicate()).show();

//					ROILabeling.fillOutside(fpLoG, roi, Float.NEGATIVE_INFINITY);

					ipBackground = fpLoG.duplicate();
									
					bpBackgroundMask = limitedOpeningByReconstruction(fpLoG, ipBackground, backgroundRadius, maxBackground);
					
//					ipBackground = MorphologicalReconstructionInteger.openingByReconstruction(fpLoG, backgroundRadius);
					fpLoG.copyBits(ipBackground, 0, 0, Blitter.SUBTRACT);
					ipToMeasure = fpLoG.duplicate();
//					new ImagePlus("Background", ipBackground.duplicate()).show();
				} else {
					ipToMeasure = fpDetection;
					ipBackground = null;
				}
				
				//--------NEW--------
				if (excludeDAB && fpH != null && fpDAB != null) {
					// If we are avoiding DAB, set pixels away from potential nuclei to zero
					fpDAB.setRoi(roi);
					ByteProcessor bpH = SimpleThresholding.greaterThanOrEqual(fpH, fpDAB);
					bpH.multiply(1.0/255.0);
					rf.rank(bpH, 2.5, RankFilters.MEDIAN); // TODO: Check hard-coded filter sizes for reasonableness
					rf.rank(bpH, 2.5, RankFilters.MAX);
					fpLoG.copyBits(bpH, 0, 0, Blitter.MULTIPLY);
				}
//				new ImagePlus("Log", fpLoG.duplicate()).show();
				//--------END_NEW--------
				
				// Apply (approximation of) Laplacian of Gaussian filter
				fpLoG.blurGaussian(sigma);
				fpLoG.convolve(new float[]{0, -1, 0, -1, 4, -1, 0, -1, 0}, 3, 3);
				
				// Threshold the main LoG image
				bpLoG = SimpleThresholding.thresholdAbove(fpLoG, 0f);
				// Need to set the threshold very slightly above zero for ImageJ
				fpLoG.setRoi(roi);
				
//				ROILabeling.fillOutside(fpLoG, roi, Float.NEGATIVE_INFINITY);
				
				ImageProcessor ipTemp = MorphologicalReconstruction.findRegionalMaxima(fpLoG, 0.001f, false);
				ImageProcessor ipLabels = RoiLabeling.labelImage(ipTemp, 0, false);
//				new ImagePlus("Labels before", ipLabels.duplicate()).show();
				Watershed.doWatershed(fpLoG, ipLabels, 0, false);
//				new ImagePlus("Labels after", ipLabels.duplicate()).show();
				
				
				ipLabels.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
				// TODO: Consider 4/8 connectivity for watershed nucleus ROIs
				rois = RoiLabeling.getFilledPolygonROIs(ipLabels, Wand.FOUR_CONNECTED);			
//				rois = ROILabeling.getFilledPolygonROIsExperimental(ipLabels);
				
				
//				new ImagePlus("Labels", ipLabels.duplicate()).show();
				if (Thread.currentThread().isInterrupted())
					return;
			} 
			
			if (bp == null)
				bp = new ByteProcessor(width, height);			

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
//				if (ipBackground != null && !Double.isNaN(maxBackground)) {
//					ipBackground.setRoi(r);
//					if (ipBackground.getStatistics().mean > maxBackground)
//						continue;
//				}
				// Fill the ROI to keep it
				bp.fill(r);
			}
			
//			new ImagePlus("BP early", bp.duplicate()).show();
			
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
//				ROILabeling.clearBoundary(bp, roi, 0);
			
			// Locate nucleus ROIs
			bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			
			
//			new ImagePlus("BP later", bp.duplicate()).show();

			//----------------------------
			// MINOR BOUNDARY REFINEMENT
			// The idea is that Gaussian smoothing tends to cause the boundaries of 'thin' nuclei to be overestimated;
			// this uses a smaller filter to correct instances where the boundary has moved by just one pixel
			if (refineBoundary && sigma > 1.5) {
				
//				new ImagePlus("Before", bp.duplicate()).show();
				
				FloatProcessor fpLoG = (FloatProcessor)fpDetection.duplicate();
				fpLoG.blurGaussian(1);
				fpLoG.convolve(new float[]{0, -1, 0, -1, 4, -1, 0, -1, 0}, 3, 3);
				ByteProcessor bp2 = SimpleThresholding.thresholdAbove(fpLoG, 0f);
				bp2.copyBits(bp, 0, 0, Blitter.MIN); // Remove everything not detected in bp
				bp.filter(ByteProcessor.MIN);
				bp.copyBits(bp2, 0, 0, Blitter.MAX);

//				new ImagePlus("After", bp.duplicate()).show();

				regenerateROIs = true;
			}
			
			roisNuclei = RoiLabeling.getFilledPolygonROIs(bp, Wand.FOUR_CONNECTED);

			if (Thread.currentThread().isInterrupted())
				return;
			
			// Remove nuclei with areas outside the permitted range - updating the binary image as we go
			if (minArea > 0 || maxArea > 0) {
				bp.setValue(0);
				Iterator<PolygonRoi> iter = roisNuclei.iterator();
				while (iter.hasNext()) {
					Roi roiTemp = iter.next();
					fpDetection.setRoi(roiTemp);
					double area = ImageStatistics.getStatistics(fpDetection, Measurements.AREA, null).pixelCount;
					if ((minArea > 0 && area < minArea) || (maxArea > 0 && area > maxArea)) {
						iter.remove();
						bp.fill(roiTemp);
					}
				}
				fpDetection.resetRoi();
			}
			
			
			// Label nuclei
			ShortProcessor ipLabels = new ShortProcessor(width, height);
			RoiLabeling.labelROIs(ipLabels, roisNuclei);
			
			
			
			
			//----------------------------
			Map<Float, PolygonRoi> roisCellsList = null;
			ImageProcessor ipLabelsCells = null;
			int nCells = 0;
			
			// Membrane detection (new 9/8/2015)
			if (excludeDAB && fpDAB != null) {
				
				FloatProcessor fpMembranes = (FloatProcessor)fpDAB.duplicate();
				fpMembranes.blurGaussian(2); // TODO: Enable a different sigma parameter?
				
				float membraneThreshold = .2f; // TODO: Enable a different membrane threshold?
				for (int i = 0; i < width * height; i++) {
					fpMembranes.setf(i, membraneThreshold - fpMembranes.getf(i));
				}
				fpMembranes.max(0.);
				
				// Fill nuclei with max values
				fpMembranes.setValue(0.1);
				for (Roi r : roisNuclei) {
					fpMembranes.fill(r);
				}
				
				// Locate regional maxima that will be used for detection
				ByteProcessor bpMarkers = new MaximumFinder().findMaxima(fpMembranes, 0.09, ImageProcessor.NO_THRESHOLD, MaximumFinder.IN_TOLERANCE, false, false);
				
				// Remove any impossibly-large areas
				RoiLabeling.removeByAreas(bpMarkers, 1, maxArea, true);
				
				// Determine a mask of the furthest anything can expand
				ByteProcessor bpMaxExpansion = (ByteProcessor)bpMarkers.duplicate();
				new RankFilters().rank(bpMaxExpansion, cellExpansion, RankFilters.MAX);
				
				// Update the mask to remove completely unstained pixels
				for (int i = 0; i < width * height; i++) {
					if (fpH.getf(i) + fpDAB.getf(i) < 0.025f)
						bpMaxExpansion.set(i, 0);
				}
				
				// Expand all potential cells as much as possible
				float minThreshold = (float)(fpMembranes.getStatistics().min - 0.05);
				for (int i = 0; i < width * height; i++) {
					if (bpMaxExpansion.get(i) == 0)
						fpMembranes.setf(i, minThreshold);
				}
				fpMembranes.resetMinAndMax();
				int lastLabel = roisNuclei.size();
				ipLabelsCells = ipLabels.duplicate();
				for (int i = 0; i < width * height; i++) {
					if (bpMarkers.get(i) != 0 && ipLabelsCells.get(i) == 0) {
						ipLabelsCells.set(i, Short.MAX_VALUE);
					}
				}
				FloodFiller ff = new FloodFiller(ipLabelsCells);
				for (int i = 0; i < width * height; i++) {
					if (ipLabelsCells.get(i) == Short.MAX_VALUE) {
						lastLabel++;
						ipLabelsCells.setValue(lastLabel);
						ff.fill(i%width, i/width);
					}
				}
				
//				fpMembranes.resetMinAndMax();
//				new ImagePlus("BEFORE", fpMembranes.duplicate()).show();
//				ipLabels = ROILabeling.labelImage(bpMarkers, false);
				Watershed.doWatershed(fpMembranes, ipLabelsCells, minThreshold+.025, false);
				ipLabelsCells.setThreshold(0.5, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
				roisCellsList = RoiLabeling.getFilledPolygonROIsFromLabels(ipLabelsCells, Wand.FOUR_CONNECTED);
				nCells = lastLabel;
			}
			
			
			
			//----------------------------
			
			
			
			
			
			// Measure nuclei
			List<RunningStatistics> statsHematoxylin = null;
			List<RunningStatistics> statsDAB = null;
			if (makeMeasurements) {
				statsHematoxylin = StatisticsHelper.createRunningStatisticsList(roisNuclei.size());
				SimpleImage imgLabels = new PixelImageIJ(ipLabels);
				StatisticsHelper.computeRunningStatistics(new PixelImageIJ(fpH), imgLabels, statsHematoxylin);
				if (fpDAB != null) {
					statsDAB = StatisticsHelper.createRunningStatisticsList(roisNuclei.size());
					StatisticsHelper.computeRunningStatistics(new PixelImageIJ(fpDAB), imgLabels, statsDAB);
				}
			}
			
			if (Thread.currentThread().isInterrupted())
				return;

			
			// Create nucleus objects
			// TODO: Set the measurement capacity to improve efficiency
			List<PathObject> nucleiObjects = new ArrayList<>();
			Calibration cal = pathImage.getImage().getCalibration();
			ImagePlane plane = pathImage.getImageRegion().getPlane();
			for (int i = 0; i < roisNuclei.size(); i++) {
				PolygonRoi r = roisNuclei.get(i);
				
				if (smoothBoundaries)
					r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2.5, r.getNCoordinates()*0.1), true), Roi.POLYGON);
				
				PolygonROI pathROI = IJTools.convertToPolygonROI(r, cal, pathImage.getDownsampleFactor(), plane);
				
				if (smoothBoundaries) {
//					int nBefore = pathROI.nVertices();
					pathROI = ShapeSimplifier.simplifyPolygon(pathROI, pathImage.getDownsampleFactor()/4.0);
//					int nAfter = pathROI.nVertices();
//					System.out.println("Vertices removed: " + (nBefore - nAfter));
				}
				
				// Create a new shared measurement list
				MeasurementList measurementList = MeasurementListFactory.createMeasurementList(makeMeasurements ? 30 : 0, MeasurementList.MeasurementListType.FLOAT);
				
				if (makeMeasurements) {
					ObjectMeasurements.addShapeStatistics(measurementList, r, fpH, cal, "Nucleus: ");
	
	//				PathObject pathObject = new PathDetectionObject(pathROI, PathPrefs.getNucleusClass());
	//				PathObjectIJ.computeShapeStatistics(pathObject, pathImage, fpH, pathImage.getImage().getCalibration(), "Nucleus: ");
					
					RunningStatistics stats = statsHematoxylin.get(i);
					measurementList.addMeasurement("Nucleus: Hematoxylin OD mean", stats.getMean());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD sum", stats.getSum());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD std dev", stats.getStdDev());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD max", stats.getMax());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD min", stats.getMin());
					measurementList.addMeasurement("Nucleus: Hematoxylin OD range", stats.getRange());
					if (statsDAB != null) {
						stats = statsDAB.get(i);
						measurementList.addMeasurement("Nucleus: DAB OD mean", stats.getMean());
						measurementList.addMeasurement("Nucleus: DAB OD sum", stats.getSum());
						measurementList.addMeasurement("Nucleus: DAB OD std dev", stats.getStdDev());
						measurementList.addMeasurement("Nucleus: DAB OD max", stats.getMax());
						measurementList.addMeasurement("Nucleus: DAB OD min", stats.getMin());
						measurementList.addMeasurement("Nucleus: DAB OD range", stats.getRange());
					}
				}
				
				// TODO: It would be more efficient to measure the hematoxylin intensities along with the shapes
//				PathObject pathObject = new PathDetectionObject(pathROI, PathClassFactory.getNucleusClass(), measurementList);
				PathObject pathObject = PathObjects.createDetectionObject(pathROI, null, measurementList);
				nucleiObjects.add(pathObject);

			}
			
			if (Thread.currentThread().isInterrupted())
				return;

//			List<Roi> roisCellsList = null;
			
			// Optionally expand the nuclei to become cells
			if (cellExpansion > 0) {
				
				Map<Float, PolygonRoi> roisCells;
				if (roisCellsList == null) {
				
					FloatProcessor fpEDM = new EDM().makeFloatEDM(bp, (byte)255, false);
					fpEDM.multiply(-1);
					
					// ---------------------- EXPERIMENTAL - CONSTRAIN CELL EXPANSION BY NUCLEUS SIZE
					double cellExpansionThreshold = -cellExpansion;
					if (limitExpansionByNucleusSize) {
						MaximumFinder mf = new MaximumFinder();
						ByteProcessor bpVoronoi = mf.findMaxima(fpEDM, .5, cellExpansionThreshold, MaximumFinder.SEGMENTED, false, false);
						FloatProcessor fpEDM2 = new EDM().makeFloatEDM(bp, (byte)0, false);
						
						FloatProcessor fpMarkers = new FloatProcessor(fpEDM2.getWidth(), fpEDM2.getHeight());
						Arrays.fill((float[])(fpMarkers.getPixels()), Float.NEGATIVE_INFINITY);
						for (Roi r : roisNuclei) {
							fpEDM2.setRoi(r);
							double max = fpEDM2.getStatistics().max;
							fpMarkers.setValue(-2. * max);
							fpMarkers.fill(r);
						}
						
						for (int i = 0; i < bpVoronoi.getWidth()*bpVoronoi.getHeight(); i++) {
							if (bpVoronoi.getf(i) == 0)
								fpEDM.setf(i, Float.NEGATIVE_INFINITY);
						}
						if (!MorphologicalReconstruction.morphologicalReconstruction(fpMarkers, fpEDM))
							logger.error("Problem during morphological reconstruction!");
						ByteProcessor bpMax = mf.findMaxima(fpMarkers, 0.0001, MaximumFinder.IN_TOLERANCE, false);
						fpEDM.copyBits(bpMax, 0, 0, Blitter.COPY);
						cellExpansionThreshold = 128; // Cell expansion has already been applied
					}
					
					// ---------------------- END EXPERIMENTAL
					
					// Create cell ROIs
					ipLabelsCells = ipLabels.duplicate();
					Watershed.doWatershed(fpEDM, ipLabelsCells, cellExpansionThreshold, false);
					roisCells = RoiLabeling.getFilledPolygonROIsFromLabels(ipLabelsCells, roisNuclei.size());
					nCells = roisCells.size();
				} else {
					roisCells = roisCellsList;
				}
				
				
				
				
				// Compute cell DAB stats
				List<RunningStatistics> statsDABCell = null;
				if (fpDAB != null && makeMeasurements) {
					statsDABCell = StatisticsHelper.createRunningStatisticsList(nCells);
					StatisticsHelper.computeRunningStatistics(new PixelImageIJ(fpDAB), new PixelImageIJ(ipLabelsCells), statsDABCell);
				}
							
				// Create labelled image for cytoplasm, i.e. remove all nucleus pixels
				// TODO: Make a buffer zone between nucleus and cytoplasm?!
				for (int i = 0; i < ipLabels.getWidth() * ipLabels.getHeight(); i++) {
					if (ipLabels.getf(i) != 0)
						ipLabelsCells.setf(i, 0f);
				}
				
				// Compute cytoplasm stats
				List<RunningStatistics> statsDABCytoplasm = null;
				if (includeNuclei && fpDAB != null && makeMeasurements) {
					statsDABCytoplasm = StatisticsHelper.createRunningStatisticsList(nCells);
					StatisticsHelper.computeRunningStatistics(new PixelImageIJ(fpDAB), new PixelImageIJ(ipLabelsCells), statsDABCytoplasm);
				}
				
				// Create membrane stats
				List<RunningStatistics> statsDABMembrane = null;
				if (includeNuclei && excludeDAB && fpDAB != null && makeMeasurements) {
					statsDABMembrane = StatisticsHelper.createRunningStatisticsList(nCells);
					ImageProcessor ipLabelsMembrane = new ShortProcessor(width, height);
					// TODO: WARNING!  This method of creating measurements doesn't permit membranes to overlap.
					// This means that sometimes one cell could 'steal' a bit of the membrane of another cell.
					// However it's unlikely this actually make any substantial difference...
					for (Entry<Float, PolygonRoi> entry : roisCells.entrySet()) {
						Float label = entry.getKey();
						PolygonRoi roiTemp = entry.getValue();
						ipLabelsMembrane.setValue(label.doubleValue());
						ipLabelsMembrane.draw(roiTemp);
					}
					StatisticsHelper.computeRunningStatistics(new PixelImageIJ(fpDAB), new PixelImageIJ(ipLabelsMembrane), statsDABMembrane);
//					ipLabelsMembrane.resetMinAndMax();
//					new ImagePlus("LABELS", ipLabelsMembrane.duplicate()).show();
				}
				
				
				// Create cell objects
//				PathClass cellClass = PathClassFactory.getPathClass("Cell", ColorTools.makeRGB(255, 200, 0));
				PathClass cellClass = null;
				for (Entry<Float, PolygonRoi> entry : roisCells.entrySet()) {
					PolygonRoi r = entry.getValue();
					if (r == null)
						continue;
					if (smoothBoundaries)
						r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2.5, r.getNCoordinates()*0.1), false), Roi.POLYGON); // TODO: Check this smoothing - it can be troublesome, causing nuclei to be outside cells
//						r = smoothPolygonRoi(r);

					PolygonROI pathROI = IJTools.convertToPolygonROI(r, pathImage.getImage().getCalibration(), pathImage.getDownsampleFactor(), plane);
					if (smoothBoundaries)
						pathROI = ShapeSimplifier.simplifyPolygon(pathROI, pathImage.getDownsampleFactor()/4.0);

					
					MeasurementList measurementList = null;
					PathObject nucleus = null;
					int label = entry.getKey().intValue();
					if (label < nucleiObjects.size())
						nucleus = nucleiObjects.get(label-1);

					// If we don't have a nucleus, check the cell area isn't too large
					if (nucleus == null && pathROI.getArea() > maxArea * 2)
						continue;

					// Prepare measurement list with/without nucleus
					if (includeNuclei && nucleus != null) {
						// Use the nucleus' measurement list
						measurementList = nucleus.getMeasurementList();					
					} else {
						// Create a new measurement list
						measurementList = MeasurementListFactory.createMeasurementList(makeMeasurements ? 12 : 0, MeasurementList.MeasurementListType.GENERAL);
						nucleus = null;
					}
					
					
					// Add cell shape measurements
					if (makeMeasurements) {
						ObjectMeasurements.addShapeStatistics(measurementList, r, fpDetection, pathImage.getImage().getCalibration(), "Cell: ");
	//					ObjectMeasurements.computeShapeStatistics(pathObject, pathImage, fpH, pathImage.getImage().getCalibration());
	
						// Add cell measurements
						if (statsDABCell != null) {
							RunningStatistics stats = statsDABCell.get(label-1);
							measurementList.addMeasurement("Cell: DAB OD mean", stats.getMean());
							measurementList.addMeasurement("Cell: DAB OD std dev", stats.getStdDev());
							measurementList.addMeasurement("Cell: DAB OD max", stats.getMax());
							measurementList.addMeasurement("Cell: DAB OD min", stats.getMin());
	//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
						}
						
						// Add cytoplasm measurements
						if (statsDABCytoplasm != null) {
							RunningStatistics stats = statsDABCytoplasm.get(label-1);
							measurementList.addMeasurement("Cytoplasm: DAB OD mean", stats.getMean());
							measurementList.addMeasurement("Cytoplasm: DAB OD std dev", stats.getStdDev());
							measurementList.addMeasurement("Cytoplasm: DAB OD max", stats.getMax());
							measurementList.addMeasurement("Cytoplasm: DAB OD min", stats.getMin());
	//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
						}
						
						// Add membrane measurements
						if (statsDABMembrane != null) {
							RunningStatistics stats = statsDABMembrane.get(label-1);
							measurementList.addMeasurement("Membrane: DAB OD mean", stats.getMean());
							measurementList.addMeasurement("Membrane: DAB OD std dev", stats.getStdDev());
							measurementList.addMeasurement("Membrane: DAB OD max", stats.getMax());
							measurementList.addMeasurement("Membrane: DAB OD min", stats.getMin());
	//						pathObject.addMeasurement("Cytoplasm: DAB OD range", stats.getRange());
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
					PathObject pathObject = PathObjects.createCellObject(pathROI, nucleus == null ? null : nucleus.getROI(), cellClass, measurementList);
					pathObjects.add(pathObject);
				}
			} else {
				pathObjects.addAll(nucleiObjects);
			}
			
			// Close the measurement lists
			for (PathObject pathObject : pathObjects)
				pathObject.getMeasurementList().close();
			
			lastRunCompleted = true;
		}
		
		
		public List<PathObject> getPathObjects() {
			return pathObjects;
		}
		
		
		public void runDetection(double backgroundRadius, double maxBackground, double medianRadius, double sigma, double threshold, double minArea, double maxArea, boolean mergeAll, boolean watershedPostProcess, boolean excludeDAB, double cellExpansion, boolean limitExpansionByNucleusSize, boolean smoothBoundaries, boolean includeNuclei, boolean makeMeasurements) {
			
			boolean updateNucleusROIs = rois == null || bpLoG == null;
			updateNucleusROIs = updateNucleusROIs ? updateNucleusROIs : this.medianRadius != medianRadius;
			this.medianRadius = medianRadius;
			
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
			
			updateAnything = updateAnything ? updateAnything : this.limitExpansionByNucleusSize != limitExpansionByNucleusSize;
			this.limitExpansionByNucleusSize = limitExpansionByNucleusSize;
			
//			if (!updateAnything)
//				return;
			
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
		double pxSize = imageData.getServer().getPixelCalibration().getAveragedPixelSizeMicrons();
		if (Double.isNaN(pxSize))
			return params.getDoubleParameterValue("cellExpansion") > 0 ? 25 : 10;
		double cellExpansion = params.getDoubleParameterValue("cellExpansionMicrons") / pxSize;
		int overlap = cellExpansion > 0 ? (int)(cellExpansion * 2) : 10;
//		System.out.println("Tile overlap: " + overlap + " pixels");
		return overlap;
	}
	
	
}