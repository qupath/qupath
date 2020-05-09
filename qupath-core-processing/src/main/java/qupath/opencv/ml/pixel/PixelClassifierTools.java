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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
	 * Create detections objects via a pixel classifier.
	 * 
	 * @param imageData
	 * @param classifier
	 * @param selectedObjects
	 * @param minSizePixels
	 * @param minHoleSizePixels
	 * @param doSplit
	 * @param clearExisting 
	 * @return
	 */
    public static boolean createDetectionsFromPixelClassifier(
			ImageData<BufferedImage> imageData, PixelClassifier classifier, Collection<PathObject> selectedObjects, 
			double minSizePixels, double minHoleSizePixels, boolean doSplit, boolean clearExisting) {
		return createObjectsFromPixelClassifier(
				new PixelClassificationImageServer(imageData, classifier),
				imageData.getHierarchy(),
				selectedObjects,
				(var roi) -> PathObjects.createDetectionObject(roi),
				minSizePixels, minHoleSizePixels, doSplit, clearExisting);
	}

    /**
     * Create annotation objects via a pixel classifier.
     * 
     * @param imageData
     * @param classifier
     * @param selectedObjects
     * @param minSizePixels
     * @param minHoleSizePixels
     * @param doSplit
     * @param clearExisting 
     * @return
     */
	public static boolean createAnnotationsFromPixelClassifier(
			ImageData<BufferedImage> imageData, PixelClassifier classifier, Collection<PathObject> selectedObjects, 
			double minSizePixels, double minHoleSizePixels, boolean doSplit, boolean clearExisting) {
		
		return createObjectsFromPixelClassifier(
				new PixelClassificationImageServer(imageData, classifier),
				imageData.getHierarchy(),
				selectedObjects,
				(var roi) -> {
					var annotation = PathObjects.createAnnotationObject(roi);
					annotation.setLocked(true);
					return annotation;
				},
				minSizePixels, minHoleSizePixels, doSplit, clearExisting);
	}
	
	/**
	 * Create objects from an image (usually created with a pixel classifier).
	 * 
	 * @param server the image to threshold
	 * @param hierarchy the hierarchy to which the objects should be added
	 * @param selectedObjects the selected objects, if the classification should be constrained to these
	 * @param creator function to create an object of the required type
	 * @param minSizePixels the minimum size of a connected region to retain, in pixels
	 * @param minHoleSizePixels the minimum size of a hole to retain, in pixels
	 * @param doSplit optionally split a multipolygon into distinct pieces
	 * @param clearExisting remove existing objects
	 * 
	 * @return true if the command ran successfully to completion, false otherwise.
	 */
	public static boolean createObjectsFromPixelClassifier(
			ImageServer<BufferedImage> server, PathObjectHierarchy hierarchy, Collection<PathObject> selectedObjects, 
			Function<ROI, ? extends PathObject> creator, double minSizePixels, double minHoleSizePixels, boolean doSplit, boolean clearExisting) {
		
		for (var pathObject : selectedObjects) {
			if (!createObjectsFromPixelClassifier(server, hierarchy, pathObject,
					creator, minSizePixels, minHoleSizePixels, doSplit, clearExisting))
				return false;
		}
		return true;
	}

	/**
	 * Create objects and add them to an object hierarchy based on thresholding the output of a pixel classifier.
	 * 
	 * @param server
	 * @param hierarchy
	 * @param selectedObject
	 * @param creator
	 * @param minSizePixels
	 * @param minHoleSizePixels
	 * @param doSplit
	 * @param clearExisting
	 * @return
	 */
	public static boolean createObjectsFromPixelClassifier(
			ImageServer<BufferedImage> server, PathObjectHierarchy hierarchy, PathObject selectedObject, 
			Function<ROI, ? extends PathObject> creator, double minSizePixels, double minHoleSizePixels,
			boolean doSplit, boolean clearExisting) {
		
		var clipArea = selectedObject == null || selectedObject.isRootObject() ? null : selectedObject.getROI().getGeometry();
		
		// Identify regions for selected ROI or entire image
		List<RegionRequest> regionRequests;
		if (selectedObject != null && !selectedObject.isRootObject()) {
			if (selectedObject.hasROI()) {
				var request = RegionRequest.createInstance(
						server.getPath(), server.getDownsampleForResolution(0), 
						selectedObject.getROI());			
				regionRequests = Collections.singletonList(request);
			} else
				regionRequests = Collections.emptyList();
		} else {
			regionRequests = RegionRequest.createAllRequests(server, server.getDownsampleForResolution(0));
		}
		
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
					var labels = server.getMetadata().getClassificationLabels();
					for (var entry : labels.entrySet()) {
						int c = entry.getKey();
						PathClass pathClass = entry.getValue();
						if (pathClass == null || PathClassTools.isGradedIntensityClass(pathClass) || PathClassTools.isIgnoredClass(pathClass))
							continue;
						ROI roi = SimpleThresholding.thresholdToROI(raster, c-0.5, c+0.5, 0, t);
										
						if (roi != null)  {
							Geometry geometry = roi.getGeometry();
							if (clipArea != null)
								geometry = geometry.intersection(clipArea);
							if (!geometry.isEmpty())
								list.add(new GeometryWrapper(geometry, pathClass, roi.getImagePlane()));
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
				geometry = GeometryTools.refineAreas(geometry, minSizePixels, minHoleSizePixels);
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
	
		// Add objects, optionally deleting existing objects first
		if (clearExisting || (selectedObject != null && !selectedObject.hasChildren())) {
			if (selectedObject == null) {
				hierarchy.clearAll();
				hierarchy.getRootObject().addPathObjects(pathObjects);
				hierarchy.fireHierarchyChangedEvent(PixelClassifierTools.class);
			} else {
				selectedObject.clearPathObjects();
				selectedObject.addPathObjects(pathObjects);
				hierarchy.fireHierarchyChangedEvent(PixelClassifierTools.class, selectedObject);
			}
		} else {
			hierarchy.addPathObjects(pathObjects);
		}
		if (selectedObject != null && (selectedObject.isAnnotation() || selectedObject.isTMACore()))
			selectedObject.setLocked(true);
		return true;
	}
	
	
	static class GeometryWrapper {
		
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