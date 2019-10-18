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

package qupath.imagej.detect.tissue;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.processing.MorphologicalReconstruction;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.processing.SimpleThresholding;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.DetectionPluginTools;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Very basic global thresholding command to identify tissue regions.
 * 
 * Uses fixed approach to downsampling images &amp; global threshold applied to RGB images only.
 * 
 * TODO: Provide choice of channels to threshold
 * TODO: Support 16-bit data
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleTissueDetection2 extends AbstractDetectionPlugin<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(SimpleTissueDetection2.class);
	
	private ParameterList params;

	private String lastResults = null;
	
	/**
	 * Constructor.
	 */
	public SimpleTissueDetection2() {
		
		params = new ParameterList().
				addIntParameter("threshold", "Threshold", 127, null, 0, 255, "Global threshold to use - defined in the range 0-255");
		
		params.addDoubleParameter("requestedPixelSizeMicrons", "Requested pixel size", 20, GeneralTools.micrometerSymbol(), "Requested pixel size for detection resolution - higher values mean a less detailed (but faster) result.\nNote that if the resolution is set too high (leading to a huge image) it will be adjusted automatically.");
		params.addDoubleParameter("minAreaMicrons", "Minimum area", 10000, GeneralTools.micrometerSymbol()+"^2", "The minimum area for a detected region - smaller regions will be discarded");
		params.addDoubleParameter("maxHoleAreaMicrons", "Max fill area", 1000000, GeneralTools.micrometerSymbol()+"^2", "'Holes' occurring within the detected regions that are smaller than this will be filled in");
		
		params.addDoubleParameter("requestedDownsample", "Downsample", 50, null, "Amount to downsample the image - higher values indicate smaller images (and less detection).\nNote that if the downsample is set too low (leading to a huge image) it will be adjusted automatically.");
		params.addDoubleParameter("minAreaPixels", "Minimum area", 100000, "px^2", "The minimum area for a detected region - smaller regions will be discarded");
		params.addDoubleParameter("maxHoleAreaPixels", "Max fill area", 500, "px^2", "'Holes' occurring within the detected regions that are smaller than this will be filled in");


		params.addBooleanParameter("darkBackground", "Dark background", false, "Choose this option if the background is darker (e.g. in a fluorescence image) rather than brighter than the tissue");
		params.addBooleanParameter("smoothImage", "Smooth image", true, "Apply 3x3 mean filter to (downsampled) image to reduce noise before thresholding");
		params.addBooleanParameter("medianCleanup", "Cleanup with median filter", true, "Apply median filter to thresholded image to reduce small variations");
		params.addBooleanParameter("dilateBoundaries", "Expand boundaries", false, "Apply 3x3 maximum filter to binary image to increase region sizes");
		params.addBooleanParameter("smoothCoordinates", "Smooth coordinates", true, "Apply smmothing to region boundaries, to reduce 'blocky' appearance");
		params.addBooleanParameter("excludeOnBoundary", "Exclude on boundary", false, "Discard detection regions that touch the image boundary");
		
		params.addBooleanParameter("singleAnnotation", "Single annotation", true, "Create a single annotation object from all (possibly-disconnected) regions");

	}
	
	
	
	class GlobalThresholder implements ObjectDetector<BufferedImage> {
		
		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, final ParameterList params, final ROI pathROI) throws IOException {
			
			ImageServer<BufferedImage> server = imageData.getServer();
			
			double downsample;
			PixelCalibration cal = server.getPixelCalibration();
			if (cal.hasPixelSizeMicrons()) {
				downsample = params.getDoubleParameterValue("requestedPixelSizeMicrons") / cal.getAveragedPixelSizeMicrons();
			} else
				downsample = params.getDoubleParameterValue("requestedDownsample");
			
			double maxDim = pathROI == null ? Math.max(server.getWidth(), server.getHeight()) : Math.max(pathROI.getBoundsWidth(), pathROI.getBoundsHeight());
			double maxDimLimit = 4000;
			if (!(maxDim / downsample <= maxDimLimit)) {
				logger.warn("Invalid requested downsample {} - will use {} instead", downsample, maxDim / maxDimLimit);
				downsample = maxDim / maxDimLimit;
			}
			
//				Rectangle bounds = pathROI != null ? pathROI.getBounds() : new Rectangle(0, 0, server.getWidth(), server.getHeight());
//				RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, bounds);
				
				RegionRequest request;
				if (!RoiTools.isShapeROI(pathROI)) {
					request = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight());
				} else
					request = RegionRequest.createInstance(server.getPath(), downsample, pathROI);

				
				PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request); // TODO: Implement z-stack support
			
			double threshold = params.getIntParameterValue("threshold");
			double minAreaMicrons = 1, maxHoleAreaMicrons = 1, minAreaPixels = 1, maxHoleAreaPixels = 1;
			if (cal.hasPixelSizeMicrons()) {
				minAreaMicrons = params.getDoubleParameterValue("minAreaMicrons");
				maxHoleAreaMicrons = params.getDoubleParameterValue("maxHoleAreaMicrons");
			} else {
				minAreaPixels = params.getDoubleParameterValue("minAreaPixels");
				maxHoleAreaPixels = params.getDoubleParameterValue("maxHoleAreaPixels");			
			}
			boolean smoothImage = params.getBooleanParameterValue("smoothImage");
			boolean darkBackground = params.getBooleanParameterValue("darkBackground");
			boolean smoothCoordinates = params.getBooleanParameterValue("smoothCoordinates");
			boolean medianCleanup = params.getBooleanParameterValue("medianCleanup");
			boolean excludeOnBoundary = params.getBooleanParameterValue("excludeOnBoundary");
			boolean dilateBoundaries = params.getBooleanParameterValue("dilateBoundaries");
			boolean singleAnnotation = Boolean.TRUE.equals(params.getBooleanParameterValue("singleAnnotation"));
			
			// Create a ByteProcessor
			ImagePlus imp = pathImage.getImage();
			ByteProcessor bp = imp.getProcessor().convertToByteProcessor();
			
			if (smoothImage)
				bp.smooth();
	//		new ImagePlus("Binary", bp.duplicate()).show();
	
			// Apply threshold
			if (darkBackground)
				bp = SimpleThresholding.thresholdAbove(bp, (float)threshold);
			else
				bp = SimpleThresholding.thresholdBelow(bp, (float)threshold);
					
			if (Thread.currentThread().isInterrupted())
				return null;

			
			// Apply small median filter to clean up
			if (medianCleanup) {
				RankFilters rf = new RankFilters();
				rf.rank(bp, 1, RankFilters.MEDIAN);
			}
			
			// Apply maximum filter, if required
			if (dilateBoundaries)
				bp.filter(ImageProcessor.MAX);
			
	//		bp.resetMinAndMax();
	//		new ImagePlus("", bp.duplicate()).show();
			
			// If there is a ROI, clear everything outside
			Roi roiIJ = null;
			if (pathROI != null) {
				roiIJ = IJTools.convertToIJRoi(pathROI, imp.getCalibration(), downsample);
				RoiLabeling.clearOutside(bp, roiIJ);
			}
			
			// Exclude on image boundary now, if required
			// This needs to be done before getting ROIs, because filled ROIs are requested - which can result in the whole image being filled in if the boundary covers everything
			if (excludeOnBoundary) {
				ByteProcessor bpMarker = new ByteProcessor(bp.getWidth(), bp.getHeight());
				bpMarker.setValue(255);
				bpMarker.drawRect(0, 0, bp.getWidth(), bp.getHeight());
				if (roiIJ != null)
					bpMarker.draw(roiIJ);
				bpMarker.copyBits(bp, 0, 0, Blitter.AND);
				ByteProcessor bpBoundary = MorphologicalReconstruction.binaryReconstruction(bpMarker, bp, false);
				bp.copyBits(bpBoundary, 0, 0, Blitter.SUBTRACT);
			}
			
			if (Thread.currentThread().isInterrupted())
				return null;
			
			// Convert to objects
			double minArea, maxHoleArea;
			if (cal.hasPixelSizeMicrons()) {
				double areaScale = 1.0 / (cal.getAveragedPixelSizeMicrons() * cal.getAveragedPixelSizeMicrons() * downsample * downsample);
				minArea = minAreaMicrons * areaScale;
				maxHoleArea = maxHoleAreaMicrons * areaScale;
			} else {
				minArea = minAreaPixels / (downsample * downsample);
				maxHoleArea = maxHoleAreaPixels / (downsample * downsample);			
			}
			logger.debug("Min area: {}", minArea);
			logger.debug("Max hole area: {}", maxHoleArea);
			
			if (Thread.currentThread().isInterrupted())
				return null;
			
			bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			List<PathObject> pathObjects = convertToPathObjects(bp, minArea, smoothCoordinates, imp.getCalibration(), downsample, maxHoleArea, excludeOnBoundary, singleAnnotation, pathImage.getImageRegion().getPlane(), null);

			if (Thread.currentThread().isInterrupted())
				return null;
			
			if (pathObjects == null || pathObjects.isEmpty())
				lastResults =  "No regions detected!";
			else if (pathObjects.size() == 1)
				lastResults =  "1 region detected";
			else
				lastResults =  pathObjects.size() + " regions detected";
			
			return pathObjects;
		}
		
		
		@Override
		public String getLastResultsDescription() {
			return lastResults;
		}
		
		
	}
	
	
	
	
	private static List<PathObject> convertToPathObjects(ByteProcessor bp, double minArea, boolean smoothCoordinates, Calibration cal, double downsample, double maxHoleArea, boolean excludeOnBoundary, boolean singleAnnotation, ImagePlane plane, List<PathObject> pathObjects) {
		List<PolygonRoi> rois = RoiLabeling.getFilledPolygonROIs(bp, Wand.FOUR_CONNECTED);
		if (pathObjects == null)
			pathObjects = new ArrayList<>(rois.size());
		
		// We might need a copy of the original image
		boolean fillAllHoles = maxHoleArea <= 0;
		ByteProcessor bpOrig = !fillAllHoles ? (ByteProcessor)bp.duplicate() : null;
		
		bp.setValue(255);
		for (PolygonRoi r : rois) {
			// Check for boundary overlap
			if (excludeOnBoundary) {
				Rectangle bounds = r.getBounds();
				if (bounds.x <= 0 || bounds.y <= 0 ||
						bounds.x + bounds.width >= bp.getWidth()-1 || 
						bounds.y + bounds.height >= bp.getHeight()-1)
					continue;
			}
			bp.setRoi(r);
			if (bp.getStatistics().area < minArea)
				continue;
						
			bp.fill(r); // Fill holes as we go - it might matter later
//			if (smoothCoordinates) {
////				r = new PolygonRoi(r.getInterpolatedPolygon(2.5, false), Roi.POLYGON);
//				r = new PolygonRoi(r.getInterpolatedPolygon(Math.min(2.5, r.getNCoordinates()*0.1), false), Roi.POLYGON); // TODO: Check this smoothing - it can be troublesome, causing nuclei to be outside cells
//			}
			
			PolygonROI pathPolygon = IJTools.convertToPolygonROI(r, cal, downsample, plane);
//			if (pathPolygon.getArea() < minArea)
//				continue;
			// Smooth the coordinates, if we downsampled quite a lot
			if (smoothCoordinates) {
				pathPolygon = ROIs.createPolygonROI(ShapeSimplifier.smoothPoints(pathPolygon.getAllPoints()), ImagePlane.getPlaneWithChannel(pathPolygon));
				pathPolygon = ShapeSimplifier.simplifyPolygon(pathPolygon, downsample/2);
			}
			pathObjects.add(PathObjects.createAnnotationObject(pathPolygon));
		}
		
		
		if (Thread.currentThread().isInterrupted())
			return null;
		
		// TODO: Optimise this - the many 'containsObject' calls are a (potentially easy-to-fix) bottleneck
		if (!fillAllHoles) {
			// Get the holes alone
			bp.copyBits(bpOrig, 0, 0, Blitter.DIFFERENCE);
//			new ImagePlus("Binary", bp).show();
			bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			
			List<PathObject> holes = convertToPathObjects(bp, maxHoleArea, smoothCoordinates, cal, downsample, 0, false, false, plane, null);
			
			// For each object, fill in any associated holes
			List<Area> areaList = new ArrayList<>();
			for (int ind = 0; ind < pathObjects.size(); ind++) {
				if (holes.isEmpty())
					break;
				
				PathObject pathObject = pathObjects.get(ind);
				areaList.clear();
				Iterator<PathObject> iter = holes.iterator();
				while (iter.hasNext()) {
					PathObject hole = iter.next();
					if (PathObjectTools.containsObject(pathObject, hole)) {
						areaList.add(RoiTools.getArea(hole.getROI()));
						iter.remove();
					}
				}
				if (areaList.isEmpty())
					continue;
				
				// If we have some areas, combine them
				// TODO: FIX MAJOR BOTTLENECK HERE!!!
				Area hole = areaList.get(0);
				for (int i = 1; i < areaList.size(); i++) {
					hole.add(areaList.get(i));
					if (i % 100 == 0) {
						logger.debug("Added hole " + i + "/" + areaList.size());
						if (Thread.currentThread().isInterrupted())
							return null;
					}
				}
				
				// Now subtract & create a new object
				ROI pathROI = pathObject.getROI();
				if (RoiTools.isShapeROI(pathROI)) {
					Area areaMain = RoiTools.getArea(pathROI);
					areaMain.subtract(hole);
					pathROI = RoiTools.getShapeROI(areaMain, pathROI.getImagePlane());
					pathObjects.set(ind, PathObjects.createAnnotationObject(pathROI));
				}
			}
		}
		
		
		// This is a clumsy way to do it...
		if (singleAnnotation) {
			ROI roi = null;
			for (PathObject annotation : pathObjects) {
				ROI currentShape = annotation.getROI();
				if (roi == null)
					roi = currentShape;
				else
					roi = RoiTools.combineROIs(roi, currentShape, CombineOp.ADD);
			}
			pathObjects.clear();
			if (roi != null)
				pathObjects.add(PathObjects.createAnnotationObject(roi));
		}
		
		
		
		// Lock the objects
		for (PathObject pathObject : pathObjects)
			((PathAnnotationObject)pathObject).setLocked(true);
		
		return pathObjects;
	}
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		boolean micronsKnown = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.setHiddenParameters(!micronsKnown, "requestedPixelSizeMicrons", "minAreaMicrons", "maxHoleAreaMicrons");
		params.setHiddenParameters(micronsKnown, "requestedDownsample", "minAreaPixels", "maxHoleAreaPixels");
		return params;
	}

	@Override
	public String getName() {
		return "Simple tissue detection";
	}

	@Override
	public String getLastResultsDescription() {
		return lastResults;
	}


	@Override
	public String getDescription() {
		return "Detect one or more regions of interest by applying a global threshold";
	}


	@Override
	protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
		tasks.add(DetectionPluginTools.createRunnableTask(new GlobalThresholder(), getParameterList(imageData), imageData, parentObject));
	}


	@Override
	protected Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		
		PathObjectHierarchy hierarchy = getHierarchy(runner);
		if (hierarchy.getTMAGrid() == null)
			return Collections.singleton(hierarchy.getRootObject());
		
		return hierarchy.getSelectionModel().getSelectedObjects().stream().filter(p -> p.isTMACore()).collect(Collectors.toList());
//		PathObjectHierarchy hierarchy = runner.getImageData().getHierarchy();
//		PathObject pathObjectSelected = runner.getSelectedObject();
//		if (pathObjectSelected instanceof PathAnnotationObject || pathObjectSelected instanceof TMACoreObject)
//			return Collections.singleton(pathObjectSelected);
//		return Collections.singleton(hierarchy.getRootObject());
	}


	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		// TODO: Re-allow taking an object as input in order to limit bounds
		// Temporarily disabled so as to avoid asking annoying questions when run repeatedly
		List<Class<? extends PathObject>> list = new ArrayList<>();
		list.add(TMACoreObject.class);
//		list.add(PathAnnotationObject.class);
		list.add(PathRootObject.class);
		return list;
	}

}
