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

package qupath.opencv.ml.pixel;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.processing.SimpleThresholding;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.Reclassifier;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper methods for working with pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierTools {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierTools.class);

    
    /**
     * Options when creating objects from a pixel classifier.
     * <p>
     * This exists to avoid requiring numerous boolean arguments.
     */
    public enum CreateObjectOptions {
    	/**
    	 * Delete existing child objects
    	 */
    	DELETE_EXISTING,
    	/**
    	 * Split connected components
    	 */
    	SPLIT,
    	/**
    	 * Generate objects for ignored classes (default is not to)
    	 */
    	INCLUDE_IGNORED,
    	/**
    	 * Set the new objects to be selected
    	 */
    	SELECT_NEW;
    	
    	@Override
    	public String toString() {
    		switch(this) {
			case DELETE_EXISTING:
				return "Delete existing";
			case INCLUDE_IGNORED:
				return "Include ignored";
			case SPLIT:
				return "Split";
			case SELECT_NEW:
				return "Select new";
			default:
				throw new IllegalArgumentException("Unknown option " + this);
    		}
    	}
    	
    }
    
    
    
	/**
	 * Create detection objects based upon an {@link ImageServer} that provides classification or probability output, 
	 * applied to selected objects. If no objects are selected, objects are created across the entire image.
	 * 
	 * 
	 * @param hierarchy
	 * @param classifierServer
     * @param minArea the minimum area of connected regions to retain
     * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
     * @return true if changes were made to the hierarchy, false otherwise
	 */
    public static boolean createDetectionsFromPixelClassifier(
			PathObjectHierarchy hierarchy, ImageServer<BufferedImage> classifierServer, 
			double minArea, double minHoleArea, CreateObjectOptions... options) {
		var selected = hierarchy.getSelectionModel().getSelectedObjects();
		if (selected.isEmpty())
			selected = Collections.singleton(hierarchy.getRootObject());
		return createObjectsFromPredictions(
				classifierServer,
				hierarchy,
				selected,
				(var roi) -> PathObjects.createDetectionObject(roi),
				minArea, minHoleArea, options);
	}
    
    /**
	 * Create detection objects based upon the output of a pixel classifier, applied to selected objects.
	 * If no objects are selected, objects are created across the entire image.
	 * 
     * @param imageData the original image data, which will be the input to the pixel classifier
     * @param classifier the pixel classifier
     * @param minArea the minimum area of connected regions to retain
     * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
     * @return true if changes were made to the hierarchy, false otherwise
     */
    public static boolean createDetectionsFromPixelClassifier(
			ImageData<BufferedImage> imageData, PixelClassifier classifier, double minArea, double minHoleArea, CreateObjectOptions... options) {
		return createDetectionsFromPixelClassifier(
				imageData.getHierarchy(),
				new PixelClassificationImageServer(imageData, classifier),
				minArea, minHoleArea, options);
	}
	
	/**
	 * Create objects from an image (usually created with a pixel classifier) where values represent classifications or predictions.
	 * 
	 * @param server the image to threshold
	 * @param hierarchy the hierarchy to which the objects should be added
	 * @param selectedObjects the selected objects, if the classification should be constrained to these
	 * @param creator function to create an object of the required type
	 * @param minArea the minimum size of a connected region to retain, in calibrated units
	 * @param minHoleArea the minimum size of a hole to retain, in calibrated units
     * @param options additional options to control how objects are created
	 * 
	 * @return true if the command ran successfully to completion, false otherwise.
	 * @see GeometryTools#refineAreas(Geometry, double, double)
	 */
	public static boolean createObjectsFromPredictions(
			ImageServer<BufferedImage> server, PathObjectHierarchy hierarchy, Collection<PathObject> selectedObjects, 
			Function<ROI, ? extends PathObject> creator, double minArea, double minHoleArea, CreateObjectOptions... options) {
		
		if (selectedObjects.isEmpty())
			return false;
		
		var optionSet = new HashSet<>(Arrays.asList(options));
		boolean doSplit = optionSet.contains(CreateObjectOptions.SPLIT);
		boolean includeIgnored = optionSet.contains(CreateObjectOptions.INCLUDE_IGNORED);
		boolean clearExisting = optionSet.contains(CreateObjectOptions.DELETE_EXISTING);
		
		Set<PathObject> toSelect = optionSet.contains(CreateObjectOptions.SELECT_NEW) ? new HashSet<>() : null;
		
		Map<PathObject, Collection<PathObject>> map = new LinkedHashMap<>();
		boolean firstWarning = true;
		List<PathObject> parentObjects = new ArrayList<>(selectedObjects); // In case the collection might be changed elsewhere...
		
		// Sort in descending order of area; this is because some potential child objects might have been removed already by the time they are required
		parentObjects = parentObjects.stream().filter(p -> p.isRootObject() || (p.hasROI() && p.getROI().isArea()))
				.sorted(Comparator.comparing(PathObject::getROI, Comparator.nullsFirst(Comparator.comparingDouble(ROI::getArea).reversed())))
				.collect(Collectors.toList());
		
		List<PathObject> completed = new ArrayList<>();
		List<PathObject> toDeselect = new ArrayList<>();
		for (var pathObject : parentObjects) {
			// If we are clearing existing objects, we don't need to worry about creating objects inside others that will later be deleted
			if (clearExisting) {
				boolean willRemove = false;
				for (var possibleAncestor : completed) {
					if (PathObjectTools.isAncestor(pathObject, possibleAncestor)) {
						willRemove = true;
						break;
					}
				}
				if (willRemove) {
					toDeselect.add(pathObject);
					logger.warn("Skipping {} during object creation (is descendant of an object that is already being processed)", pathObject);
					continue;
				}
			}
			
			var children = createObjectsFromPixelClassifier(server, pathObject.getROI(),
					creator, minArea, minHoleArea, doSplit, includeIgnored);
			// Sanity check - don't allow non-detection objects to be added to detections
			if (pathObject.isDetection() && children.stream().anyMatch(p -> !p.isDetection())) {
				if (firstWarning) {
					logger.warn("Cannot add non-detection objects to detections! Objects will be skipped...");
					firstWarning = false;
				}
			} else {
				if (toSelect != null)
					toSelect.addAll(children);
				map.put(pathObject, children);
			}
			completed.add(pathObject);
			if (Thread.currentThread().isInterrupted())
				return false;
		}
		for (var entry : map.entrySet()) {
			var parent = entry.getKey();
			var children = entry.getValue();
			if (clearExisting && parent.hasChildren())
				parent.clearPathObjects();
			parent.addPathObjects(children);
			if (!parent.isRootObject())
				parent.setLocked(true);
		}
		if (map.size() == 1)
			hierarchy.fireHierarchyChangedEvent(null, map.keySet().iterator().next());
		else if (map.size() > 1)
			hierarchy.fireHierarchyChangedEvent(null);
		
		if (toSelect != null) {
			toSelect = toSelect.stream().filter(p -> PathObjectTools.hierarchyContainsObject(hierarchy, p)).collect(Collectors.toSet());
			hierarchy.getSelectionModel().setSelectedObjects(toSelect, null);
		} else if (!toDeselect.isEmpty()) {
			hierarchy.getSelectionModel().deselectObjects(toDeselect);
		}
		
		return true;
	}
	

	/**
	 * Create annotation objects based upon the output of a pixel classifier, applied to selected objects.
	 * If no objects are selected, objects are created across the entire image.
	 * 
     * @param imageData the original image data, which will be the input to the pixel classifier
     * @param classifier the pixel classifier
     * @param minArea the minimum area of connected regions to retain
     * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
     * @return true if changes were made to the hierarchy, false otherwise
     */
	public static boolean createAnnotationsFromPixelClassifier(
			ImageData<BufferedImage> imageData, PixelClassifier classifier, double minArea, double minHoleArea, CreateObjectOptions... options) {
		return createAnnotationsFromPixelClassifier(
				imageData.getHierarchy(),
				new PixelClassificationImageServer(imageData, classifier),
				minArea, minHoleArea, options);
	}
	
	
	/**
	 * Create annotation objects based upon an {@link ImageServer} that provides classification or probability output, 
	 * applied to selected objects. If no objects are selected, objects are created across the entire image.
	 * 
	 * 
	 * @param hierarchy
	 * @param classifierServer
     * @param minArea the minimum area of connected regions to retain
     * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
     * @return true if changes were made to the hierarchy, false otherwise
	 */
	public static boolean createAnnotationsFromPixelClassifier(
			PathObjectHierarchy hierarchy, ImageServer<BufferedImage> classifierServer, double minArea, double minHoleArea, CreateObjectOptions... options) {
		var selected = hierarchy.getSelectionModel().getSelectedObjects();
		if (selected.isEmpty())
			selected = Collections.singleton(hierarchy.getRootObject());
		return createObjectsFromPredictions(
				classifierServer,
				hierarchy,
				selected,
				(var roi) -> {
					var annotation = PathObjects.createAnnotationObject(roi);
					annotation.setLocked(true);
					return annotation;
				},
				minArea,
				minHoleArea,
				options);
	}
	

	/**
	 * Create objects based upon an {@link ImageServer} that provides classification or probability output.
	 * 
	 * @param server image server providing pixels from which objects should be created
	 * @param roi region of interest in which objects should be created (optional; if null, the entire image is used)
	 * @param creator function to create an object from a ROI (e.g. annotation or detection)
	 * @param minArea minimum area for an object fragment to retain, in calibrated units based on the pixel calibration
	 * @param minHoleArea minimum area for a hole to fill, in calibrated units based on the pixel calibration
     * @param doSplit if true, split connected regions into separate objects
	 * @param includeIgnored if true, create object for (non-null) classes that are normally ignored; see {@link PathClassTools#isIgnoredClass(PathClass)}
	 * @return the objects created within the ROI
	 */
	public static Collection<PathObject> createObjectsFromPixelClassifier(
			ImageServer<BufferedImage> server, ROI roi, 
			Function<ROI, ? extends PathObject> creator, double minArea, double minHoleArea, boolean doSplit, boolean includeIgnored) {
		
		// We need classification labels to do anything
		var labels = server.getMetadata().getClassificationLabels();
		if (labels == null || labels.isEmpty())
			throw new IllegalArgumentException("Cannot create objects for server - no classification labels are available!");
		
		if (roi != null && !roi.isArea()) {
			logger.warn("Cannot create objects for non-area ROIs");
			return Collections.emptyList();
		}
		Geometry clipArea = roi == null ? null : roi.getGeometry();
		
		// Identify regions for selected ROI or entire image
		// This is a list because it might need to handle multiple z-slices or timepoints
		List<RegionRequest> regionRequests;
		if (roi != null) {
			var request = RegionRequest.createInstance(
					server.getPath(), server.getDownsampleForResolution(0), 
					roi);
			regionRequests = Collections.singletonList(request);
		} else {
			regionRequests = RegionRequest.createAllRequests(server, server.getDownsampleForResolution(0));
		}
		
		double pixelArea = server.getPixelCalibration().getPixelWidth().doubleValue() * server.getPixelCalibration().getPixelHeight().doubleValue();
		double minAreaPixels = minArea / pixelArea;
		double minHoleAreaPixels = minHoleArea / pixelArea;
		
		// Create output array
		var pathObjects = new ArrayList<PathObject>();

		// Loop through region requests (usually 1, unless we have a z-stack or time series)
		for (RegionRequest regionRequest : regionRequests) {
			Collection<TileRequest> tiles = server.getTileRequestManager().getTileRequests(regionRequest);
			
			Map<PathClass, List<GeometryWrapper>> pathObjectMap = tiles.parallelStream().map(t -> {
				var list = new ArrayList<GeometryWrapper>();
				try {
					var img = server.readBufferedImage(t.getRegionRequest());
					// Get raster containing classifications and integer values, by taking the argmax
					var raster = img.getRaster();
					if (server.getMetadata().getChannelType() != ImageServerMetadata.ChannelType.CLASSIFICATION) {
						var nChannels = server.nChannels();
						int h = raster.getHeight();
						int w = raster.getWidth();
						byte[] output = new byte[w * h];
						for (int y = 0; y < h; y++) {
							for (int x = 0; x < w; x++) {
								int maxInd = 0;
								float maxVal = raster.getSampleFloat(x, y, 0);
								for (int c = 1; c < nChannels; c++) {
									float val = raster.getSampleFloat(x, y, c);						
									if (val > maxVal) {
										maxInd = c;
										maxVal = val;
									}
									output[y*w+x] = (byte)maxInd;
								}
							}
						}
						raster = WritableRaster.createPackedRaster(
								new DataBufferByte(output, w*h), w, h, 8, null);
					}
					for (var entry : labels.entrySet()) {
						int c = entry.getKey();
						PathClass pathClass = entry.getValue();
						if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified() || (!includeIgnored && PathClassTools.isIgnoredClass(pathClass)))
							continue;
//						if (pathClass == null || PathClassTools.isGradedIntensityClass(pathClass) || PathClassTools.isIgnoredClass(pathClass))
//							continue;
						ROI roiDetected = SimpleThresholding.thresholdToROI(raster, c-0.5, c+0.5, 0, t);
										
						if (roiDetected != null)  {
							Geometry geometry = roiDetected.getGeometry();
							if (clipArea != null)
								geometry = geometry.intersection(clipArea);
							if (!geometry.isEmpty() && geometry.getArea() > 0) {
								// Exclude lines/points that can sometimes arise
								list.add(new GeometryWrapper(geometry, pathClass, roiDetected.getImagePlane()));
							}
						}
					}
				} catch (Exception e) {
					logger.error("Error requesting classified tile", e);
				}
				return list;
			}).flatMap(p -> p.stream()).collect(Collectors.groupingBy(p -> p.pathClass, Collectors.toList()));
		
			// Merge objects with the same classification
			for (var entry : pathObjectMap.entrySet()) {
				var pathClass = entry.getKey();
				var list = entry.getValue();
				
				// Merge to a single Geometry
				var collection = list.stream().map(g -> g.geometry).collect(Collectors.toList());
//				long start = System.currentTimeMillis();
//				Geometry geometry = collection.get(0).getFactory().createGeometryCollection(collection.toArray(Geometry[]::new)).buffer(0);
//				long middle = System.currentTimeMillis();
				Geometry geometry = GeometryTools.union(collection);
//				long end = System.currentTimeMillis();
//				System.err.println("Buffer: " + (middle - start) + ", area = " + geometry.getArea());
//				System.err.println("Union: " + (end - middle) + ", area = " + geometry2.getArea());
				
				// Apply size filters
				geometry = GeometryTools.refineAreas(geometry, minAreaPixels, minHoleAreaPixels);
				if (geometry == null)
					continue;
				
				if (doSplit) {
					for (int i = 0; i < geometry.getNumGeometries(); i++) {
						var geom = geometry.getGeometryN(i);
						var r = GeometryTools.geometryToROI(geom, regionRequest.getPlane());
						var annotation = creator.apply(r);
						annotation.setPathClass(pathClass);
						pathObjects.add(annotation);
					}
				} else {
					var r = GeometryTools.geometryToROI(geometry, regionRequest.getPlane());
					var annotation = creator.apply(r);
					annotation.setPathClass(pathClass);
					pathObjects.add(annotation);				
				}
			}
		}
		return pathObjects;
	}
	
	
	private static class GeometryWrapper {
		
		final Geometry geometry;
		final PathClass pathClass;
		final ImagePlane plane;
		
		GeometryWrapper(Geometry geometry, PathClass pathClass, ImagePlane plane) {
			this.geometry = geometry;
			this.pathClass = pathClass;
			this.plane = plane;
		}
		
	}
	
	/**
	 * Create an {@link ImageServer} that displays the results of applying a {@link PixelClassifier} to an image.
	 * @param imageData the image to which the classifier should apply
	 * @param classifier the pixel classifier
	 * @return the classification {@link ImageServer}
	 */
	public static ImageServer<BufferedImage> createPixelClassificationServer(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		return new PixelClassificationImageServer(imageData, classifier);
	}
	
	
	/**
	 * Create a {@link PixelClassificationMeasurementManager} that can be used to generate measurements from applying a pixel classifier to an image.
	 * @param imageData the image to which the classifier should be applied
	 * @param classifier the pixel classifier
	 * @return the {@link PixelClassificationMeasurementManager}
	 */
	public static PixelClassificationMeasurementManager createMeasurementManager(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		return createMeasurementManager(createPixelClassificationServer(imageData, classifier));
	}
	
	/**
	 * Create a {@link PixelClassificationMeasurementManager} that can be used to generate measurements from an {@link ImageServer} where pixels provide 
	 * classification or probability information.
	 * @param classifierServer the classification image server
	 * @return the {@link PixelClassificationMeasurementManager}
	 */
	public static PixelClassificationMeasurementManager createMeasurementManager(ImageServer<BufferedImage> classifierServer) {
		return new PixelClassificationMeasurementManager(classifierServer);
	}
	
	
	/**
	 * Add measurements to selected objects based upon the output of a {@link PixelClassifier}.
	 * 
	 * @param imageData the image data, which will be input to the classifier and which contains the selected objects to measure. 
	 *                  If no objects are selected, measurements will be applied to the entire image.
	 * @param classifier the pixel classifier
	 * @param measurementID identifier that is prepended to measurement names, to make these identifiable later (optional; may be null)
	 * @return true if measurements were added, false otherwise
	 */
	public static boolean addMeasurementsToSelectedObjects(ImageData<BufferedImage> imageData, PixelClassifier classifier, String measurementID) {
		var manager = createMeasurementManager(imageData, classifier);
		var hierarchy = imageData.getHierarchy();
		var objectsToMeasure = hierarchy.getSelectionModel().getSelectedObjects();
		if (objectsToMeasure.isEmpty())
			objectsToMeasure = Collections.singleton(hierarchy.getRootObject());
		addMeasurements(objectsToMeasure, manager, measurementID);
		hierarchy.fireObjectMeasurementsChangedEvent(manager, objectsToMeasure);
		return true;
	}
	
	/**
	 * Add measurements to specified objects from a {@link PixelClassificationMeasurementManager}.
	 * 
	 * @param objectsToMeasure the objects to measure.
	 * @param manager the manager used to generate measurements
	 * @param measurementID identifier that is prepended to measurement names, to make these identifiable later (optional; may be null)
	 * @return true if measurements were added, false otherwise
	 */
	public static boolean addMeasurements(Collection<? extends PathObject> objectsToMeasure, PixelClassificationMeasurementManager manager, String measurementID) {
		
		if (measurementID == null || measurementID.isBlank())
			measurementID = "";
		else {
			measurementID = measurementID.strip();
			if (measurementID.endsWith(":"))
				measurementID += " ";
			else
				measurementID += ": ";			
		}
		
		
		int n = objectsToMeasure.size();
		int i = 0;
		
		for (var pathObject : objectsToMeasure) {
			i++;
			if (n < 100 || n % 100 == 0)
				logger.debug("Measured {}/{}", i, n);
			try (var ml = pathObject.getMeasurementList()) {
				for (String name : manager.getMeasurementNames()) {
					Number value = manager.getMeasurementValue(pathObject, name, false);
					double val = value == null ? Double.NaN : value.doubleValue();
					ml.putMeasurement(measurementID + name, val);
				}
			}
			// We really want to lock objects so we don't end up with wrong measurements
			pathObject.setLocked(true);
		}
		return true;
	}
	


	/**
	 * Apply classification from a server to a collection of objects.
	 * 
	 * @param classifierServer an {@link ImageServer} with output type 
	 * @param pathObjects
	 * @param preferNucleusROI
	 */
	public static void classifyObjectsByCentroid(ImageServer<BufferedImage> classifierServer, Collection<PathObject> pathObjects, boolean preferNucleusROI) {
		var labels = classifierServer.getMetadata().getClassificationLabels();
		var reclassifiers = pathObjects.parallelStream().map(p -> {
				try {
					var roi = PathObjectTools.getROI(p, preferNucleusROI);
					int x = (int)roi.getCentroidX();
					int y = (int)roi.getCentroidY();
					int ind = getClassification(classifierServer, x, y, roi.getZ(), roi.getT());
					return new Reclassifier(p, labels.get(ind), false);
				} catch (Exception e) {
					return new Reclassifier(p, null, false);
				}
			}).collect(Collectors.toList());
		reclassifiers.parallelStream().forEach(r -> r.apply());
	}
	
	
	
	/**
	 * Request the classification for a specific pixel.
	 * 
	 * @param server
	 * @param x
	 * @param y
	 * @param z
	 * @param t
	 * @return
	 * @throws IOException
	 */
	public static int getClassification(ImageServer<BufferedImage> server, int x, int y, int z, int t) throws IOException {
		
		var type = server.getMetadata().getChannelType();
		if (type != ImageServerMetadata.ChannelType.CLASSIFICATION && type != ImageServerMetadata.ChannelType.PROBABILITY)
			return -1;
		
		var tile = server.getTileRequestManager().getTileRequest(0, x, y, z, t);
		if (tile == null)
			return -1;
		
		int xx = (int)Math.floor(x / tile.getDownsample() - tile.getTileX());
		int yy = (int)Math.floor(y / tile.getDownsample() - tile.getTileY());
		var img = server.readBufferedImage(tile.getRegionRequest());
		
		if (xx >= img.getWidth())
			xx = img.getWidth() - 1;
		if (xx < 0)
			xx = 0;

		if (yy >= img.getHeight())
			yy = img.getHeight() - 1;
		if (yy < 0)
			yy = 0;

		int nBands = img.getRaster().getNumBands();
		if (nBands == 1 && type == ImageServerMetadata.ChannelType.CLASSIFICATION) {
			try {
				return img.getRaster().getSample(xx, yy, 0);
			} catch (Exception e) {
				logger.error("Error requesting classification", e);
				return -1;
			}
		} else if (type == ImageServerMetadata.ChannelType.PROBABILITY) {
			int maxInd = -1;
			double maxVal = Double.NEGATIVE_INFINITY;
			var raster = img.getRaster();
			for (int b = 0; b < nBands; b++) {
				double temp = raster.getSampleDouble(xx, yy, b);
				if (temp > maxVal) {
					maxInd = b;
					maxVal = temp;
				}
			}
			return maxInd;
		}
		return -1;
	}
	
	
	
	/**
	 * Classify cells according to the prediction of the pixel corresponding to the cell centroid using a {@link PixelClassifier}.
	 * @param imageData the {@link ImageData} containing the cells
	 * @param classifier the classifier
	 * @param preferNucleusROI whether to use the nucleus ROI (if available) rather than the cell ROI
	 */
	public static void classifyCellsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier, boolean preferNucleusROI) {
		classifyObjectsByCentroid(imageData, classifier, imageData.getHierarchy().getCellObjects(), preferNucleusROI);
	}

	/**
	 * Classify detections according to the prediction of the pixel corresponding to the detection centroid using a {@link PixelClassifier}.
	 * If the detections are cells, the nucleus ROI is used where possible.
	 * @param imageData the {@link ImageData} containing the cells
	 * @param classifier the classifier
	 */
	public static void classifyDetectionsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		classifyObjectsByCentroid(imageData, classifier, imageData.getHierarchy().getDetectionObjects(), true);
	}
	
	/**
	 * Classify objects according to the prediction of the pixel corresponding to the object's ROI centroid using a {@link PixelClassifier}.
	 * @param imageData the {@link ImageData} containing the cells
	 * @param classifier the classifier
	 * @param pathObjects the objects to classify
	 * @param preferNucleusROI use the nucleus ROI in the case of cells; ignored for all other object types
	 */
	public static void classifyObjectsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier, Collection<PathObject> pathObjects, boolean preferNucleusROI) {
		classifyObjectsByCentroid(new PixelClassificationImageServer(imageData, classifier), pathObjects, preferNucleusROI);
		imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
	}
	
	
	
	
//	public static void classifyObjectsByAreaOverlap(PixelClassificationImageServer server, Collection<PathObject> pathObjects, double overlapProportion, boolean preferNucleusROI) {
//		var reclassifiers = pathObjects.parallelStream().map(p -> {
//				try {
//					var roi = PathObjectTools.getROI(p, preferNucleusROI);
//					PixelClassificationMeasurementManager.
//					int x = (int)roi.getCentroidX();
//					int y = (int)roi.getCentroidY();
//					int ind = server.getClassification(x, y, roi.getZ(), roi.getT());
//					return new Reclassifier(p, PathClassFactory.getPathClass(server.getChannel(ind).getName()), false);
//				} catch (Exception e) {
//					return new Reclassifier(p, null, false);
//				}
//			}).collect(Collectors.toList());
//		reclassifiers.parallelStream().forEach(r -> r.apply());
//		server.getImageData().getHierarchy().fireObjectClassificationsChangedEvent(server, pathObjects);
//	}
	
	

}