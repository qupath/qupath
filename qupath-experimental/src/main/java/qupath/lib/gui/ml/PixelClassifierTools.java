package qupath.lib.gui.ml;

import ij.plugin.filter.ThresholdToSelection;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.tools.IJTools;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
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
import java.awt.image.Raster;
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
 * User interface for interacting with pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierTools {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierTools.class);

    
    /**
     * Generate a QuPath ROI by thresholding an image channel image.
     * 
     * @param img the input image (any type)
     * @param minThreshold minimum threshold; pixels &gt;= minThreshold will be included
     * @param maxThreshold maximum threshold; pixels &lt;= maxThreshold will be included
     * @param band the image band to threshold (channel)
     * @param request a {@link RegionRequest} corresponding to this image, used to calibrate the coordinates.  If null, 
     * 			we assume no downsampling and an origin at (0,0).
     * @return
     * 
     * @see #thresholdToROI(ImageProcessor, TileRequest)
     */
    public static ROI thresholdToROI(BufferedImage img, double minThreshold, double maxThreshold, int band, RegionRequest request) {
    	int w = img.getWidth();
    	int h = img.getHeight();
    	float[] pixels = new float[w * h];
    	img.getRaster().getSamples(0, 0, w, h, band, pixels);
    	var fp = new FloatProcessor(w, h, pixels);
    	
    	fp.setThreshold(minThreshold, maxThreshold, ImageProcessor.NO_LUT_UPDATE);
    	return thresholdToROI(fp, request);
    }
    
    /**
     * Generate a QuPath ROI by thresholding an image channel image, deriving coordinates from a TileRequest.
     * <p>
     * This can give a more accurate result than depending on a RegionRequest because it is possible to avoid some loss of precision.
     * 
     * @param raster
     * @param minThreshold
     * @param maxThreshold
     * @param band
     * @param request
     * @return
     * 
     * @see #thresholdToROI(ImageProcessor, RegionRequest)
     */
    public static ROI thresholdToROI(Raster raster, double minThreshold, double maxThreshold, int band, TileRequest request) {
    	int w = raster.getWidth();
    	int h = raster.getHeight();
    	float[] pixels = new float[w * h];
    	raster.getSamples(0, 0, w, h, band, pixels);
    	var fp = new FloatProcessor(w, h, pixels);
    	
    	fp.setThreshold(minThreshold, maxThreshold, ImageProcessor.NO_LUT_UPDATE);
    	return thresholdToROI(fp, request);
    }
    
    
    
    /**
     * Generate a QuPath ROI from an ImageProcessor.
     * <p>
     * It is assumed that the ImageProcessor has had its min and max threshold values set.
     * 
     * @param ip
     * @param request
     * @return
     */
    static ROI thresholdToROI(ImageProcessor ip, RegionRequest request) {
    	// Need to check we have any above-threshold pixels at all
    	int n = ip.getWidth() * ip.getHeight();
    	boolean noPixels = true;
    	double min = ip.getMinThreshold();
    	double max = ip.getMaxThreshold();
    	for (int i = 0; i < n; i++) {
    		double val = ip.getf(i);
    		if (val >= min && val <= max) {
    			noPixels = false;
    			break;
    		}
    	}
    	if (noPixels)
    		return null;
    	    	
    	// Generate a shape, using the RegionRequest if we can
    	var roiIJ = new ThresholdToSelection().convert(ip);
    	if (request == null)
    		return IJTools.convertToROI(roiIJ, 0, 0, 1, ImagePlane.getDefaultPlane());
    	return IJTools.convertToROI(
    			roiIJ,
    			-request.getX()/request.getDownsample(),
    			-request.getY()/request.getDownsample(),
    			request.getDownsample(), request.getPlane());
    }
    
    static ROI thresholdToROI(ImageProcessor ip, TileRequest request) {
    	// Need to check we have any above-threshold pixels at all
    	int n = ip.getWidth() * ip.getHeight();
    	boolean noPixels = true;
    	double min = ip.getMinThreshold();
    	double max = ip.getMaxThreshold();
    	for (int i = 0; i < n; i++) {
    		double val = ip.getf(i);
    		if (val >= min && val <= max) {
    			noPixels = false;
    			break;
    		}
    	}
    	if (noPixels)
    		return null;
    	    	
    	// Generate a shape, using the TileRequest if we can
    	var roiIJ = new ThresholdToSelection().convert(ip);
    	if (request == null)
    		return IJTools.convertToROI(roiIJ, 0, 0, 1, ImagePlane.getDefaultPlane());
    	return IJTools.convertToROI(
    			roiIJ,
    			-request.getTileX(),
    			-request.getTileY(),
    			request.getDownsample(), request.getPlane());
    }
    
    
    /**
	 * Get a raster, padded by the specified amount, to the left, right, above and below.
	 * <p>
	 * Note that the padding is defined in terms of the <i>destination</i> pixels.
	 * <p>
	 * In other words, a specified padding of 5 should actually result in 20 pixels being added in each dimension 
	 * if the {@code request.getDownsample() == 4}.
	 * <p>
	 * Currently, zero-padding is used.
	 * 
	 * @param server
	 * @param request
	 * @param padding
	 * @return
	 * @throws IOException 
	 */
	public static BufferedImage getPaddedRequest(ImageServer<BufferedImage> server, RegionRequest request, int padding) throws IOException {
		// If we don't have any padding, just return directly
		if (padding == 0)
			return server.readBufferedImage(request);
		// If padding < 0, throw an exception
		if (padding < 0)
			new IllegalArgumentException("Padding must be >= 0, but here it is " + padding);
		// Get the expected bounds
		double downsample = request.getDownsample();
		int x = (int)Math.round(request.getX() - padding * downsample);
		int y = (int)Math.round(request.getY() - padding * downsample);
		int x2 = (int)Math.round((request.getX() + request.getWidth()) + padding * downsample);
		int y2 = (int)Math.round((request.getY() + request.getHeight()) + padding * downsample);
		// If we're out of range, we'll need to work a bit harder
		int padLeft = 0, padRight = 0, padUp = 0, padDown = 0;
		boolean outOfRange = false;
		if (x < 0) {
			padLeft = (int)Math.round(-x/downsample);
			x = 0;
			outOfRange = true;
		}
		if (y < 0) {
			padUp = (int)Math.round(-y/downsample);
			y = 0;
			outOfRange = true;
		}
		if (x2 > server.getWidth()) {
			padRight  = (int)Math.round((x2 - server.getWidth())/downsample);
			x2 = server.getWidth();
			outOfRange = true;
		}
		if (y2 > server.getHeight()) {
			padDown  = (int)Math.round((y2 - server.getHeight())/downsample);
			y2 = server.getHeight();
			outOfRange = true;
		}
		// If everything is within range, this should be relatively straightforward
		RegionRequest request2 = RegionRequest.createInstance(request.getPath(), downsample, x, y, x2-x, y2-y, request.getZ(), request.getT());
		BufferedImage img = server.readBufferedImage(request2);
		if (outOfRange) {
			WritableRaster raster = img.getRaster();
			WritableRaster rasterPadded = raster.createCompatibleWritableRaster(
					raster.getWidth() + padLeft + padRight,
					raster.getHeight() + padUp + padDown);
			rasterPadded.setRect(padLeft, padUp, raster);
			// Add padding above
			if (padUp > 0) {
				WritableRaster row = raster.createWritableChild(0, 0, raster.getWidth(), 1, 0, 0, null);
				for (int r = 0; r < padUp; r++)
					rasterPadded.setRect(padLeft, r, row);
			}
			// Add padding below
			if (padDown > 0) {
				WritableRaster row = raster.createWritableChild(0, raster.getHeight()-1, raster.getWidth(), 1, 0, 0, null);
				for (int r = padUp + raster.getHeight(); r < rasterPadded.getHeight(); r++)
					rasterPadded.setRect(padLeft, r, row);
			}
			// Add padding to the left
			if (padLeft > 0) {
				WritableRaster col = rasterPadded.createWritableChild(padLeft, 0, 1, rasterPadded.getHeight(), 0, 0, null);
				for (int c = 0; c < padLeft; c++)
					rasterPadded.setRect(c, 0, col);
			}
			// Add padding to the right
			if (padRight > 0) {
				WritableRaster col = rasterPadded.createWritableChild(rasterPadded.getWidth()-padRight-1, 0, 1, rasterPadded.getHeight(), 0, 0, null);
				for (int c = padLeft + raster.getWidth(); c < rasterPadded.getWidth(); c++)
					rasterPadded.setRect(c, 0, col);
			}
			// TODO: The padding seems to work - but something to be cautious with...
			img = new BufferedImage(img.getColorModel(), rasterPadded, img.isAlphaPremultiplied(), null);
		}
		return img;
	}


	    /**
	     * Create detections objects via a pixel classifier.
	     * 
	     * @param imageData
	     * @param classifier
	     * @param selectedObjects
	     * @param minSizePixels
	     * @param minHoleSizePixels
	     * @param doSplit
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
					var nChannels = server.nChannels();
					// Get raster containing classifications and integer values, by taking the argmax
					var raster = img.getRaster();
					if (server.getMetadata().getChannelType() != ImageServerMetadata.ChannelType.CLASSIFICATION) {
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
						ROI roi = thresholdToROI(raster, c-0.5, c+0.5, 0, t);
										
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
				Geometry geometry = GeometryTools.union(list.stream().map(g -> g.geometry).collect(Collectors.toList()));
				
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
	 * @param server the classification server to use
	 * @param pathObjects the objects to classify
	 * @param preferNucleusROI if true, use the nucleus ROI (if available) for cell objects
	 */
	public static void classifyObjectsByCentroid(PixelClassificationImageServer server, Collection<PathObject> pathObjects, boolean preferNucleusROI) {
		var reclassifiers = pathObjects.parallelStream().map(p -> {
				try {
					var roi = PathObjectTools.getROI(p, preferNucleusROI);
					int x = (int)roi.getCentroidX();
					int y = (int)roi.getCentroidY();
					int ind = server.getClassification(x, y, roi.getZ(), roi.getT());
					return new Reclassifier(p, PathClassFactory.getPathClass(server.getChannel(ind).getName()), false);
				} catch (Exception e) {
					return new Reclassifier(p, null, false);
				}
			}).collect(Collectors.toList());
		reclassifiers.parallelStream().forEach(r -> r.apply());
		server.getImageData().getHierarchy().fireObjectClassificationsChangedEvent(server, pathObjects);
	}
	
	
	public static void classifyCellsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier, boolean preferNucleusROI) {
		classifyObjectsByCentroid(imageData, classifier, imageData.getHierarchy().getCellObjects(), preferNucleusROI);
	}

	public static void classifyDetectionsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier, boolean preferNucleusROI) {
		classifyObjectsByCentroid(imageData, classifier, imageData.getHierarchy().getDetectionObjects(), preferNucleusROI);
	}
	
	public static void classifyObjectsByCentroid(ImageData<BufferedImage> imageData, PixelClassifier classifier, Collection<PathObject> pathObjects, boolean preferNucleusROI) {
		classifyObjectsByCentroid(new PixelClassificationImageServer(imageData, classifier), pathObjects, preferNucleusROI);
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