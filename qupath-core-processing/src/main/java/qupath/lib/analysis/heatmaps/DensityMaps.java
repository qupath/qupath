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
import java.awt.image.Raster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.images.ContourTracing;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;

/**
 * Class for constructing and using density maps.
 * 
 * @author Pete Bankhead
 */
public class DensityMaps {
	
	private final static Logger logger = LoggerFactory.getLogger(DensityMaps.class);
	
	private static final PathClass DEFAULT_HOTSPOT_CLASS = PathClassFactory.getPathClass("Hotspot", ColorTools.packRGB(200, 120, 20));

	/**
	 * Density map normalization methods.
	 */
	public static enum DensityMapNormalization {
		
		/**
		 * No normalization; maps provide raw object counts in a defined radius.
		 * This is equivalent to applying a circular sum filter to object counts per pixel.
		 */
		NONE,
		
//		/**
//		 * Area normalization; maps provide averaged object counts in a defined radius.
//		 * This is equivalent to applying a circular mean filter to object counts per pixel.
//		 * 
//		 * TODO: Consider normalization denominator; currently this uses downsampled pixel units.
//		 */
//		AREA,
		
		/**
		 * Gaussian-weighted area normalization; maps provide weighted averaged object counts in a defined radius.
		 * This is equivalent to applying a Gaussian filter to object counts per pixel.
		 * 
		 * TODO: Consider normalization denominator; currently this uses downsampled pixel units.
		 */
		GAUSSIAN,
		
		/**
		 * Object normalization; maps provide 
		 */
		OBJECTS;
		
		@Override
		public String toString() {
			switch(this) {
			case NONE:
				return "None (raw counts)";
			case OBJECTS:
				return "Object counts";
//			case AREA:
//				return "Area";
			case GAUSSIAN:
				return "Gaussian-weighted";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
		
	}
	
	
	/**
	 * Create a new {@link DensityMapBuilder} to create a customized density map.
	 * @return the builder
	 */
	public static DensityMapBuilder builder(Predicate<PathObject> allObjects) {
		return new DensityMapBuilder(allObjects);
	}
	
	
	/**
	 * Helper class for storing parameters to build a {@link DensityMapImageServer}.
	 */
	public static class DensityMapParameters {
		
		private double pixelSize = -1;
		
		private double radius = 0;
		private DensityMapNormalization normalization = DensityMapNormalization.NONE;

		private Predicate<PathObject> allObjects;
		private Map<String, Predicate<PathObject>> additionalFilters = new LinkedHashMap<>();
								
		private DensityMapParameters() {}
		
		private DensityMapParameters(DensityMapParameters params) {
			this.pixelSize = params.pixelSize;
			this.radius = params.radius;
			this.normalization = params.normalization;
			
			this.allObjects = params.allObjects;
			this.additionalFilters = new LinkedHashMap<>(params.additionalFilters);
		}
		
	}
	
	/**
	 * Builder for a {@link DensityMapImageServer} or {@link DensityMapParameters}.
	 */
	public static class DensityMapBuilder {
		
		private DensityMapParameters params = new DensityMapParameters();
		
		private DensityMapBuilder(Predicate<PathObject> allObjects) {
			Objects.nonNull(allObjects);
			params.allObjects = allObjects;
		}
		
		/**
		 * Requested pixel size to determine the resolution of the density map, in calibrated units.
		 * <p>
		 * The default is -1, which means a resolution will be determined automatically based upon 
		 * the radius value and the image size.
		 * <p>
		 * It is recommended to keep the default in most cases, to avoid the risk of creating a map 
		 * that is too large and causes performance or memory problems.
		 * 
		 * @param requestedPixelSize
		 * @return this builder
		 * @implNote The value is given in calibrated units at the time the density map is constructed. 
		 *           Any changes to the calibration information of the image server will not impact this.
		 * @see #radius(double)
		 */
		public DensityMapBuilder pixelSize(double requestedPixelSize) {
			params.pixelSize = requestedPixelSize;
			return this;
		}
		
		public DensityMapBuilder normalization(DensityMapNormalization normalization) {
			params.normalization = normalization;
			return this;
		}
		
//		/**
//		 * Request that a Gaussian filter is used, rather than the default mean (disk) filter.
//		 * <p>
//		 * A Gaussian filter gives a smoother result by using a weighted average, but the interpretation 
//		 * of the result is more difficult. 
//		 * Using a disk filter, the density map values are essentially the sum of the number of objects with centroids 
//		 * falling within a predefined radius.
//		 * With a Gaussian filter, these have been weighted.
//		 * <p>
//		 * @param gaussianFilter true if a Gaussian filter should be used, false otherwise (default is false)
//		 * @return this builder
//		 * @implNote The coefficients of the Gaussian filter are renormalized to have a maximum of 1, 
//		 *           with a sigma value equal to the specified filter radius.
//		 * @see #radius(double)
//		 */
//		public DensityMapBuilder gaussianFilter(boolean gaussianFilter) {
//			params.gaussianFilter = gaussianFilter;
//			return this;
//		}
		
		public DensityMapBuilder addDensities(String name, Predicate<PathObject> filter) {
			params.additionalFilters.put(name, filter);
			return this;
		}
		
		
		/**
		 * The radius of the filter used to calculate densities.
		 * @param radius
		 * @return this builder
		 */
		public DensityMapBuilder radius(double radius) {
			params.radius = radius;
			return this;
		}
		
		
		/**
		 * Build a {@link DensityMapImageServer} using the current parameters and the specified {@link ImageData}.
		 * @param imageData
		 * @return the density map
		 */
		public DensityMapImageServer buildMap(ImageData<BufferedImage> imageData) {
			return createMap(imageData, params);
		}

		/**
		 * Build a {@link DensityMapParameters} objects that may be passed to {@link DensityMaps#createMap(ImageData, DensityMapParameters)}.
		 * @return the parameters
		 */
		public DensityMapParameters buildParameters() {			
			return new DensityMapParameters(params);
		}

	}
	
	
	public static DensityMapImageServer createMap(ImageData<BufferedImage> imageData, String jsonParams) {
		var params = GsonTools.getInstance().fromJson(jsonParams, DensityMapParameters.class);
		return createMap(imageData, params);
	}
	
	
	public static DensityMapImageServer createMap(ImageData<BufferedImage> imageData, DensityMapParameters params) {
		return DensityMapImageServer.createDensityMap(
				imageData,
				params.pixelSize,
				params.radius,
				params.additionalFilters,
				params.allObjects,
				params.normalization,
				null
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
