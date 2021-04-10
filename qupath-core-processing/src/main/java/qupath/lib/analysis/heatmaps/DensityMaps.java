/*-
 * #%L
 * This file is part of QuPath.
 * %%
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


package qupath.lib.analysis.heatmaps;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.Predicate;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.algorithms.ContourTracing;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;

public class DensityMaps {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMaps.class);
	
	private static final PathClass DEFAULT_HOTSPOT_CLASS = PathClassFactory.getPathClass("Hotspot", ColorTools.packRGB(200, 120, 20));

	
	public static DensityMapBuilder builder() {
		return new DensityMapBuilder();
	}
	
	
	public static class DensityMapParameters {
		
		private double pixelSize = -1;
		private int maxSize = 2048;
		
		private double radius = 0;
		private boolean gaussianFilter = false;

		private Predicate<PathObject> primaryObjects;
		private Predicate<PathObject> denominatorObjects;
		
		private String primaryName = null;
		
		// Can't be JSON-serialized
		private transient ColorModel colorModel;
						
		private DensityMapParameters() {}
		
		private DensityMapParameters(DensityMapParameters params) {
			this.pixelSize = params.pixelSize;
			this.maxSize = params.maxSize;
			this.radius = params.radius;
			this.gaussianFilter = params.gaussianFilter;
			
			this.primaryObjects = params.primaryObjects;
			this.primaryName = params.primaryName;
			this.denominatorObjects = params.denominatorObjects;			
			
			this.colorModel = params.colorModel;
		}
		
	}
	
	
	public static class DensityMapBuilder {
		
		private DensityMapParameters params = new DensityMapParameters();
		
		private DoubleFunction<String> stringFun;
		
		private DensityMapBuilder() {}
		
		public DensityMapBuilder pixelSize(double requestedPixelSize) {
			params.pixelSize = requestedPixelSize;
			return this;
		}
		
		public DensityMapBuilder gaussianFilter(boolean gaussianFilter) {
			params.gaussianFilter = gaussianFilter;
			return this;
		}
		
		public DensityMapBuilder maxSize(int maxSize) {
			params.maxSize = maxSize;
			return this;
		}
		
		public DensityMapBuilder radius(double radius) {
			params.radius = radius;
			return this;
		}
		
		public DensityMapBuilder density(Predicate<PathObject> primaryObjects) {
			params.primaryObjects = primaryObjects;
			return this;
		}
		
		public DensityMapBuilder density(PathClass pathClass) {
			return density(pathClass, false);
		}
		
		public DensityMapBuilder density(PathClass pathClass, boolean baseClass) {
			if (baseClass) {
				params.primaryObjects = PathObjectFilter.DETECTIONS_ALL.and(p -> getBaseClass(p.getPathClass()) == pathClass);
			} else { 
				params.primaryObjects = PathObjectFilter.DETECTIONS_ALL.and(p -> p.getPathClass() == pathClass);
			}
			if (pathClass != null)
				params.primaryName = pathClass.toString();
			return this;
		}
		
		public DensityMapBuilder pointAnnotations(PathClass pathClass) {
			return pointAnnotations(pathClass, false);
		}
		
		public DensityMapBuilder pointAnnotations(PathClass pathClass, boolean baseClass) {
			var filter = PathObjectFilter.ANNOTATIONS.and(PathObjectFilter.ROI_POINT);
			if (baseClass)
				params.primaryObjects = filter.and(p -> getBaseClass(p.getPathClass()) == pathClass);
			else
				params.primaryObjects = filter.and(p -> p.getPathClass() == pathClass);
			if (pathClass != null)
				params.primaryName = pathClass.toString();
			return this;
		}
		
		public DensityMapBuilder percentage(Predicate<PathObject> primaryObjects, Predicate<PathObject> allObjects) {
			params.primaryObjects = primaryObjects;
			params.denominatorObjects = allObjects;
			stringFun = d -> GeneralTools.formatNumber(d, 1) + " %";
			return this;
		}
		
		
		public DensityMapBuilder positiveDetections() {
			params.denominatorObjects = PathObjectFilter.DETECTIONS_ALL;
			params.primaryObjects = params.denominatorObjects.and(p -> p.hasROI() && PathClassTools.isPositiveClass(p.getPathClass()));
			stringFun = d -> "Positive " + GeneralTools.formatNumber(d, 1);// + " %";
			params.primaryName = "Positive";
			return this;
		}
		
		public DensityMapBuilder positiveDetections(PathClass baseClass) {
			params.denominatorObjects = PathObjectFilter.DETECTIONS_ALL.and(p -> p.hasROI() && getBaseClass(p.getPathClass()) == baseClass);
			params.primaryObjects = params.denominatorObjects.and(p -> PathClassTools.isPositiveClass(p.getPathClass()));
			stringFun = d -> baseClass.toString() + " positive " + GeneralTools.formatNumber(d, 1);// + " %";
			if (baseClass != null)
				params.primaryName = baseClass.toString() + ": Positive";
			else
				params.primaryName = "Positive";
			return this;
		}
		
		public DensityMapBuilder colorModel(ColorModel colorModel) {
			params.colorModel = colorModel;
			return this;
		}
		
		/**
		 * Set the string function, which can be used to convert a density value into a readable string representation.
		 * This should be called after any method that sets the density map type, e.g. {@link #positiveDetections()} to override the default.
		 * @param fun
		 * @return
		 */
		public DensityMapBuilder stringFunction(DoubleFunction<String> fun) {
			this.stringFun = fun;
			return this;
		}
		
		private static PathClass getBaseClass(PathClass pathClass) {
			return pathClass == null ? null : pathClass.getBaseClass();
		}
		
		public DensityMapImageServer buildMap(ImageData<BufferedImage> imageData) {
			return createMap(imageData, params);
		}

		public DensityMapParameters buildParameters() {			
			return new DensityMapParameters(params);
		}

	}
	
	
	
	public static DensityMapImageServer createMap(ImageData<BufferedImage> imageData, DensityMapParameters params) {
		String name = params.primaryName == null ? "Density" : params.primaryName;
		return DensityMapImageServer.createDensityMapServer(
				imageData,
				params.pixelSize,
				params.radius,
				Map.of(name, params.primaryObjects),
				params.denominatorObjects,
				params.gaussianFilter,
				params.colorModel
				);
	}
	
	
	
	public static PathClass getHotspotClass(PathClass baseClass) {
		PathClass hotspotClass = DEFAULT_HOTSPOT_CLASS;
		if (PathClassTools.isValidClass(baseClass)) {
			hotspotClass = PathClassTools.mergeClasses(baseClass, hotspotClass);
		}
		return hotspotClass;
	}
	
	
	public static void traceContours(PathObjectHierarchy hierarchy, ImageServer<BufferedImage> server, int channel, Collection<? extends PathObject> parents, double threshold, boolean doSplit, PathClass hotspotClass) {
		// Get the selected objects
		boolean changes = false;
		if (hotspotClass == null)
			hotspotClass = getHotspotClass(null);
		for (var parent : parents) {
			var annotations = new ArrayList<PathObject>();
			var roiParent = parent.getROI();
			
			var request = roiParent == null ? RegionRequest.createInstance(server) :
				RegionRequest.createInstance(server.getPath(), server.getDownsampleForResolution(0), roiParent);
//						boolean doErode = params.getBooleanParameterValue("erode");
			BufferedImage img;
			try {
				img = server.readBufferedImage(request);					
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage(), e);
				continue;
			}
			if (img == null)
				continue;
			
			var geometry = ContourTracing.createTracedGeometry(img.getRaster(), threshold, Double.POSITIVE_INFINITY, channel, request);
			if (geometry == null || geometry.isEmpty()) {
				continue;
			}
			
			Geometry geomNew = null;
			if (roiParent == null)
				geomNew = geometry;
			else
				geomNew = GeometryTools.ensurePolygonal(geometry.intersection(roiParent.getGeometry()));
			if (geomNew.isEmpty())
				continue;
			
			var roi = GeometryTools.geometryToROI(geomNew, request == null ? ImagePlane.getDefaultPlane() : request.getPlane());
				
			if (doSplit) {
				for (var r : RoiTools.splitROI(roi))
					annotations.add(PathObjects.createAnnotationObject(r, hotspotClass));
			} else
				annotations.add(PathObjects.createAnnotationObject(roi, hotspotClass));
			parent.addPathObjects(annotations);
			changes = true;
		}
		
		if (changes)
			hierarchy.fireHierarchyChangedEvent(DensityMaps.class);
		else
			logger.warn("No thresholded hotspots found!");
		
	}
	
	
	private static SimpleImage toSimpleImage(Raster raster, int band) {
		int w = raster.getWidth();
		int h = raster.getHeight();
		float[] pixels = raster.getSamples(0, 0, w, h, band, (float[])null);
		return SimpleImages.createFloatImage(pixels, w, h);
	}
	
	
	private static int getCountsChannel(ImageServer<BufferedImage> server) {
		if (server.getChannel(server.nChannels()-1).getName().equals("Counts"))
			return server.nChannels()-1;
		return -1;
	}
	
	
	public static void findHotspots(PathObjectHierarchy hierarchy, ImageServer<BufferedImage> server, int channel, Collection<? extends PathObject> parents, int nHotspots, double radius, double minCount, boolean allowOverlapping, PathClass hotspotClass) throws IOException {
		SimpleImage mask = null;
		int countChannel = getCountsChannel(server);
		
		for (var parent : parents) {
			
			RegionRequest request;
			if (parent.hasROI())
				request = RegionRequest.createInstance(server.getPath(), server.getDownsampleForResolution(0), parent.getROI());
			else
				request = RegionRequest.createInstance(server);
			
			var img = server.readBufferedImage(request);
			var raster = img.getRaster();
			
			var image = toSimpleImage(raster, channel);
			
			if (minCount > 0) {
				mask = countChannel >= 0 ? toSimpleImage(raster, countChannel) : image;
				mask = SimpleProcessing.threshold(mask, minCount, 0, 1, 1);
			}
			
			var finder = new SimpleProcessing.PeakFinder(image)
					.region(request)
					.calibration(server.getPixelCalibration())
					.peakClass(hotspotClass)
					.minimumSeparation(allowOverlapping ? -1 : radius * 2)
					.withinROI(parent.hasROI())
					.radius(radius);
			
			if (mask != null)
				finder.mask(mask);
			
			
			var hotspots = finder.createObjects(parent.getROI(), nHotspots);
			parent.addPathObjects(hotspots);
		}
		
		hierarchy.fireHierarchyChangedEvent(DensityMaps.class);
	}
	
	

}
