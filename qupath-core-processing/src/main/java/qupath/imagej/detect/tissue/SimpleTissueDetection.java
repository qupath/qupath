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
import java.util.Map;

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
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Very basic global thresholding command to identify tissue regions.
 * <p>
 * Uses fixed approach to downsampling images &amp; global threshold applied to RGB images only.
 * 
 * TODO: Provide choice of channels to threshold
 * TODO: Support 16-bit data
 * TODO: Recompute pixel size per ROI, thereby enabling refinement of detected regions at a higher resolution
 * 
 * @author Pete Bankhead
 *
 */
class SimpleTissueDetection extends AbstractDetectionPlugin<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(SimpleTissueDetection.class);
	
	private ParameterList params;
	
	transient private GlobalThresholder thresholder;
	
	
	public SimpleTissueDetection() {
		params = new ParameterList().
				addIntParameter("threshold", "Threshold", 127, null, 0, 255, "Intensity thereshold (8-bit)");
		
		params.addDoubleParameter("minAreaMicrons", "Minimum area", 10000, GeneralTools.micrometerSymbol()+"^2", "Minimum area of a region (smaller regions will be discarded)");
		params.addDoubleParameter("maxHoleAreaMicrons", "Max fill area", 1000000, GeneralTools.micrometerSymbol()+"^2", "Maximum hole area to be filled (smaller holes will be removed)");
		
		params.addDoubleParameter("minAreaPixels", "Minimum area", 100000, "px^2", "Minimum area of a region (smaller regions will be discarded)");
		params.addDoubleParameter("maxHoleAreaPixels", "Max fill area", 500, "px^2", "Maximum hole area to be filled (smaller holes will be removed)");
		
		params.addBooleanParameter("darkBackground", "Dark background", false);
		params.addBooleanParameter("medianCleanup", "Cleanup with median filter", true);
		params.addBooleanParameter("smoothCoordinates", "Smooth coordinates", true);
		params.addBooleanParameter("excludeOnBoundary", "Exclude on boundary", false);
	}
	
	
	
	static class GlobalThresholder implements ObjectDetector<BufferedImage> {
		
		transient private String lastResults = null;
		
		transient private String lastServerPath = null;
		transient private PathImage<ImagePlus> pathImage = null;
		transient private ROI lastPathROI = null;
		
//		transient private PathImage<ImagePlus> pathImage = null;
//		transient private PathROI lastPathROI = null;
		
	
		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, final ParameterList params, final ROI pathROI) throws IOException {
			
			ImageServer<BufferedImage> server = imageData.getServer();
			
//			double downsample = getDownsampleFactor(imageData.getServer());
			double downsample = 1;
			double maxDim = Math.max(server.getWidth(), server.getHeight());
			if (maxDim > 1500)
				downsample = maxDim / 1500;
			
			if (pathImage == null || pathROI != lastPathROI || lastServerPath == null || !lastServerPath.equals(imageData.getServer().getPath())) {
//				Rectangle bounds = pathROI != null ? pathROI.getBounds() : new Rectangle(0, 0, server.getWidth(), server.getHeight());
//				RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, bounds);
				
				RegionRequest request;
				if (!RoiTools.isShapeROI(pathROI)) {
					request = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight());
				} else
					request = RegionRequest.createInstance(server.getPath(), downsample, pathROI);

				
				pathImage = IJTools.convertToImagePlus(server, request); // TODO: Implement z-stack support
				lastPathROI = pathROI;
			}
			
			double threshold = params.getIntParameterValue("threshold");
			double minAreaMicrons = 1, maxHoleAreaMicrons = 1, minAreaPixels = 1, maxHoleAreaPixels = 1;
			if (server.getPixelCalibration().hasPixelSizeMicrons()) {
				minAreaMicrons = params.getDoubleParameterValue("minAreaMicrons");
				maxHoleAreaMicrons = params.getDoubleParameterValue("maxHoleAreaMicrons");
			} else {
				minAreaPixels = params.getDoubleParameterValue("minAreaPixels");
				maxHoleAreaPixels = params.getDoubleParameterValue("maxHoleAreaPixels");			
			}
			boolean darkBackground = params.getBooleanParameterValue("darkBackground");
			boolean smoothCoordinates = params.getBooleanParameterValue("smoothCoordinates");
			boolean medianCleanup = params.getBooleanParameterValue("medianCleanup");
			boolean excludeOnBoundary = params.getBooleanParameterValue("excludeOnBoundary");
			
			// Create a ByteProcessor
			ImagePlus imp = pathImage.getImage();
			ByteProcessor bp = imp.getProcessor().convertToByteProcessor();
			
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
			
	//		bp.resetMinAndMax();
	//		new ImagePlus("", bp.duplicate()).show();
			
			// If there is a ROI, clear everything outside
			if (pathROI != null) {
				Roi roi = IJTools.convertToIJRoi(pathROI, imp.getCalibration(), downsample);
				RoiLabeling.clearOutside(bp, roi);
			}
			
			if (Thread.currentThread().isInterrupted())
				return null;
			
			
			// Convert to objects
			double minArea, maxHoleArea;
			PixelCalibration cal = server.getPixelCalibration();
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
			List<PathObject> pathObjects = convertToPathObjects(bp, minArea, smoothCoordinates, imp.getCalibration(), downsample, maxHoleArea, excludeOnBoundary, pathImage.getImageRegion().getPlane(), null);

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
	
	
	
	
	private static List<PathObject> convertToPathObjects(ByteProcessor bp, double minArea, boolean smoothCoordinates, Calibration cal, double downsample, double maxHoleArea, boolean excludeOnBoundary, ImagePlane plane, List<PathObject> pathObjects) {
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
			if (smoothCoordinates)
				r = new PolygonRoi(r.getInterpolatedPolygon(2.5, false), Roi.POLYGON);

			PolygonROI pathPolygon = IJTools.convertToPolygonROI(r, cal, downsample, plane);
//			if (pathPolygon.getArea() < minArea)
//				continue;
			// Smooth the coordinates, if we downsampled quite a lot
			if (smoothCoordinates) {
				pathPolygon = ROIs.createPolygonROI(ShapeSimplifier.smoothPoints(pathPolygon.getAllPoints()), ImagePlane.getPlane(pathPolygon));
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
			
			List<PathObject> holes = convertToPathObjects(bp, maxHoleArea, smoothCoordinates, cal, downsample, 0, false, plane, null);
			
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
		
		
//		if (!fillAllHoles) {
//			// Get the holes alone
//			bp.copyBits(bpOrig, 0, 0, Blitter.DIFFERENCE);
////			new ImagePlus("Binary", bp).show();
//			bp.setThreshold(127, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
//			List<PathObject> holes = convertToPathObjects(bp, maxHoleArea, smoothCoordinates, cal, downsample, 0, false, null);
//			for (PathObject hole : holes) {
//				PathObject mainObject = null;
//				int ind = 0;
//				for (PathObject pathObject : pathObjects) {
//					if (PathObjectHierarchy.containsObject(pathObject, hole)) {
//						mainObject = pathObject;
//						break;
//					}
//					ind++;
//				}
//				if (mainObject != null) {
//					PathShape pathShape = PathROIHelpers.combineROIs((PathShape)mainObject.getROI(), (PathShape)hole.getROI(), CombineOp.SUBTRACT);
//					pathObjects.set(ind, new PathAnnotationObject(pathShape));
//				}
//			}
//		}
		
		
		return pathObjects;
	}
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		Map<String, Parameter<?>> map = params.getParameters();
		boolean micronsKnown = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		map.get("minAreaMicrons").setHidden(!micronsKnown);
		map.get("maxHoleAreaMicrons").setHidden(!micronsKnown);
		map.get("minAreaPixels").setHidden(micronsKnown);
		map.get("minAreaPixels").setHidden(micronsKnown);
		return params;
	}

	@Override
	public String getName() {
		return "Simple tissue detection";
	}

	@Override
	public String getLastResultsDescription() {
		if (thresholder == null)
			return "";
		return thresholder.getLastResultsDescription();
	}


	@Override
	public String getDescription() {
		return "Detect one or more regions of interest by applying a global threshold";
	}


	@Override
	protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
		if (thresholder == null)
			thresholder = new GlobalThresholder();
		tasks.add(DetectionPluginTools.createRunnableTask(thresholder, getParameterList(imageData), imageData, parentObject));
	}


	@Override
	protected Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		PathObjectHierarchy hierarchy = runner.getImageData().getHierarchy();
		PathObject pathObjectSelected = hierarchy.getSelectionModel().getSelectedObject();
		if (pathObjectSelected instanceof PathAnnotationObject || pathObjectSelected instanceof TMACoreObject)
			return Collections.singleton(pathObjectSelected);
		return Collections.singleton(hierarchy.getRootObject());
	}


	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		// TODO: Re-allow taking an object as input in order to limit bounds
		// Temporarily disabled so as to avoid asking annoying questions when run repeatedly
		List<Class<? extends PathObject>> list = new ArrayList<>();
//		list.add(TMACoreObject.class);
//		list.add(PathAnnotationObject.class);
		list.add(PathRootObject.class);
		return list;
	}

}
