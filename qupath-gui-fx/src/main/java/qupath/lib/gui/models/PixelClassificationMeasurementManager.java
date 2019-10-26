package qupath.lib.gui.models;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
 
/**
 * Helper class to compute area-based measurements for regions of interest based on pixel classification.
 * 
 * @author Pete Bankhead
 */
public class PixelClassificationMeasurementManager {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationMeasurementManager.class);
	
	private static Map<ImageServer<BufferedImage>, Map<ROI, MeasurementList>> measuredROIs = new WeakHashMap<>();
	
	private ImageServer<BufferedImage> classifierServer;
	private List<String> measurementNames = null;
	
	private ROI rootROI = null; // ROI for the Root object, if required
		
	private ThreadLocal<BufferedImage> imgTileMask = new ThreadLocal<>();
	
	private boolean isMulticlass = false;
	
	private double requestedDownsample;
	private double pixelArea;
	private String pixelAreaUnits;

	public PixelClassificationMeasurementManager(ImageServer<BufferedImage> classifierServer) {
		this.classifierServer = classifierServer;
		synchronized (measuredROIs) {
			if (!measuredROIs.containsKey(classifierServer))
				measuredROIs.put(classifierServer, new HashMap<>());
		}
		
        // Calculate area of a pixel
        requestedDownsample = classifierServer.getDownsampleForResolution(0);
        PixelCalibration cal = classifierServer.getPixelCalibration();
        if (cal.hasPixelSizeMicrons()) {
	        pixelArea = (cal.getPixelWidthMicrons() * requestedDownsample) * (cal.getPixelHeightMicrons() * requestedDownsample);
	        pixelAreaUnits = GeneralTools.micrometerSymbol() + "^2";
	//        if (!pathObject.isDetection()) {
	        	double scale = requestedDownsample / 1000.0;
	            pixelArea = (cal.getPixelWidthMicrons() * scale) * (cal.getPixelHeightMicrons() * scale);
	            pixelAreaUnits = "mm^2";
	//        }
        } else {
        	pixelArea = requestedDownsample * requestedDownsample;
            pixelAreaUnits = "px^2";
        }
		
		// Handle root object if we just have a single plane
		if (classifierServer.nZSlices() == 1 || classifierServer.nTimepoints() == 1)
			rootROI = ROIs.createRectangleROI(0, 0, classifierServer.getWidth(), classifierServer.getHeight(), ImagePlane.getDefaultPlane());
		
		// Treat as multi-class probabilities if that is requested or if we just have one output channel
		var type = classifierServer.getMetadata().getChannelType();
		if (type == ChannelType.MULTICLASS_PROBABILITY || 
				(type == ChannelType.PROBABILITY && classifierServer.nChannels() == 1))
			isMulticlass = true;
		
        // Just to get measurement names
		var channels = classifierServer.getMetadata().getChannels();
		updateMeasurements(classifierServer.getMetadata().getClassificationLabels(), new long[channels.size()], pixelArea, pixelAreaUnits);
	}
	
	
	/**
	 * Get the measurement value for this object.
	 * 
	 * @param pathObject the PathObject to measure
	 * @param name the measurement name
	 * @param cachedOnly if true, return null if the measurement cannot be determined from cached tiles
	 * @return
	 */
	public Number getMeasurementValue(PathObject pathObject, String name, boolean cachedOnly) {
		var roi = pathObject.getROI();
		if (roi == null || pathObject.isRootObject())
			roi = rootROI;
		return getMeasurementValue(roi, name, cachedOnly);
	}
		
	/**
	 * Get the measurement value for this ROI.
	 * 
	 * @param roi the ROI to measure
	 * @param name the measurement name
	 * @param cachedOnly if true, return null if the measurement cannot be determined from cached tiles
	 * @return
	 */
	public Number getMeasurementValue(ROI roi, String name, boolean cachedOnly) {
		var map = measuredROIs.get(classifierServer);
		if (map == null || roi == null)
			return null;
		
		var ml = map.get(roi);
		if (ml == null) {
			ml = calculateMeasurements(roi, cachedOnly);
			if (ml == null)
				return null;
			map.put(roi, ml);
		}
		return ml.getMeasurementValue(name);
	}

	/**
	 * Get the names of all measurements that may be returned.
	 * @return
	 */
	public List<String> getMeasurementNames() {
		return measurementNames == null ? Collections.emptyList() : measurementNames;
	}
	
		    
	/**
	 * Calculate measurements for a specified ROI if possible.
	 * 
	 * @param roi
	 * @param cachedOnly abort the mission if required tiles are not cached
	 * @return
	 */
	synchronized MeasurementList calculateMeasurements(final ROI roi, final boolean cachedOnly) {
    	
        Map<Integer, PathClass> classificationLabels = classifierServer.getMetadata().getClassificationLabels();
        long[] counts = null;

        ImageServer<BufferedImage> server = classifierServer;//imageData.getServer();
        
        // Check we have a suitable output type
        ImageServerMetadata.ChannelType type = classifierServer.getMetadata().getChannelType();
        if (type == ImageServerMetadata.ChannelType.FEATURE)
  			return null;
        
        Shape shape = null;
        if (!roi.isPoint())
        	shape = RoiTools.getShape(roi);
        
        // Get the regions we need
        Collection<TileRequest> requests;
        // For the root, we want all tile requests
        if (roi == rootROI) {
	        requests = server.getTileRequestManager().getAllTileRequests();
        } else if (!roi.isEmpty()) {
	        var regionRequest = RegionRequest.createInstance(server.getPath(), requestedDownsample, roi);
	        requests = server.getTileRequestManager().getTileRequests(regionRequest);
        } else
        	requests = Collections.emptyList();
        
        if (requests.isEmpty()) {
        	logger.debug("Request empty for {}", roi);
  			return null;
        }
        

        // Try to get all cached tiles - if this fails, return quickly (can't calculate measurement)
        Map<TileRequest, BufferedImage> localCache = new HashMap<>();
        for (TileRequest request : requests) {
        	BufferedImage tile = null;
			try {
				tile = cachedOnly ? classifierServer.getCachedTile(request) : classifierServer.readBufferedImage(request.getRegionRequest());
			} catch (IOException e) {
				logger.error("Error requesting tile " + request, e);
			}
        	if (tile == null)
	  			return null;
        	localCache.put(request, tile);
        }
        
        // Calculate stained proportions
        BasicStroke stroke = null;
        byte[] mask = null;
    	BufferedImage imgMask = imgTileMask.get();
        for (Map.Entry<TileRequest, BufferedImage> entry : localCache.entrySet()) {
        	TileRequest region = entry.getKey();
        	BufferedImage tile = entry.getValue();
        	// Create a binary mask corresponding to the current tile        	
        	if (imgMask == null || imgMask.getWidth() < tile.getWidth() || imgMask.getHeight() < tile.getHeight() || imgMask.getType() != BufferedImage.TYPE_BYTE_GRAY) {
        		imgMask = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        		imgTileMask.set(imgMask);
        	}
        	
        	// Get the tile, which is needed for sub-pixel accuracy
        	if (roi.isLine() || roi.isArea()) {
	        	Graphics2D g2d = imgMask.createGraphics();
	        	g2d.setColor(Color.BLACK);
	        	g2d.fillRect(0, 0, tile.getWidth(), tile.getHeight());
	        	g2d.setColor(Color.WHITE);
	        	g2d.scale(1.0/region.getDownsample(), 1.0/region.getDownsample());
	        	g2d.translate(-region.getTileX() * region.getDownsample(), -region.getTileY() * region.getDownsample());
	        	if (roi.isLine()) {
	        		float fDownsample = (float)region.getDownsample();
	        		if (stroke == null || stroke.getLineWidth() != fDownsample)
	        			stroke = new BasicStroke((float)fDownsample);
	        		g2d.setStroke(stroke);
	        		g2d.draw(shape);
	        	} else if (roi.isArea())
	        		g2d.fill(shape);
	        	g2d.dispose();
        	} else if (roi.isPoint()) {
        		for (var p : roi.getAllPoints()) {
        			int x = (int)((p.getX() - region.getImageX()) / region.getDownsample());
        			int y = (int)((p.getY() - region.getImageY()) / region.getDownsample());
        			if (x >= 0 && y >= 0 && x < imgMask.getWidth() && y < imgMask.getHeight())
        				imgMask.getRaster().setSample(x, y, 0, 255);
        		}
        	}
        	
			int h = tile.getHeight();
			int w = tile.getWidth();
			if (mask == null || mask.length != h*w)
				mask = new byte[w * h];
        	
			int nChannels = tile.getSampleModel().getNumBands();
			
			try {
				switch (type) {
					case CLASSIFICATION:
						// Calculate histogram to get labelled image counts
						counts = BufferedImageTools.computeUnsignedIntHistogram(tile.getRaster(), counts, imgMask.getRaster());
						break;
					case PROBABILITY:
						// Take classification from the channel with the highest value
						if (nChannels > 1) {
							counts = BufferedImageTools.computeArgMaxHistogram(tile.getRaster(), counts, imgMask.getRaster());
							break;
						}
						// For one channel, fall through & treat as multiclass
					case MULTICLASS_PROBABILITY:
						// For multiclass, count
						if (counts == null)
							counts = new long[nChannels];
						double threshold = getProbabilityThreshold(tile.getRaster());
						for (int c = 0; c < nChannels; c++)
							counts[c] += BufferedImageTools.computeAboveThresholdCounts(tile.getRaster(), c, threshold, imgMask.getRaster());
					case DEFAULT:
					case FEATURE:
					default:
						// TODO: Consider handling other OutputTypes?
						return updateMeasurements(classificationLabels, counts, pixelArea, pixelAreaUnits);
				}
			} catch (Exception e) {
				logger.error("Error calculating classification areas", e);
				if (nChannels > 1 && type == ChannelType.CLASSIFICATION)
					logger.error("There are {} channels - are you sure this is really a classification image?", nChannels);
			}
        }
    	return updateMeasurements(classificationLabels, counts, pixelArea, pixelAreaUnits);
    }
	
	/**
	 * Get a suitable threshold assuming a raster contains probability values.
	 * This is determined from the TransferType. For integer types this is 127.5, 
	 * otherwise it is 0.5.
	 * @param raster
	 * @return
	 */
	public static double getProbabilityThreshold(WritableRaster raster) {
		switch (raster.getTransferType()) {
			case DataBuffer.TYPE_SHORT:
			case DataBuffer.TYPE_USHORT:
			case DataBuffer.TYPE_INT:
			case DataBuffer.TYPE_BYTE: return 127.5;
			case DataBuffer.TYPE_FLOAT:
			case DataBuffer.TYPE_DOUBLE:
			default: return 0.5;
		}
	}

	private synchronized MeasurementList updateMeasurements(Map<Integer, PathClass> classificationLabels, long[] counts, double pixelArea, String pixelAreaUnits) {
  		
    	long total = GeneralTools.sum(counts);
    	
    	Collection<PathClass> pathClasses = new LinkedHashSet<>(classificationLabels.values());
    	
    	boolean addNames = measurementNames == null;
    	List<String> tempList = null;
    	int nMeasurements = pathClasses.size()*2;
    	if (!isMulticlass)
    		nMeasurements += 2;
    	if (addNames) {
    		tempList = new ArrayList<>();
    		measurementNames = Collections.unmodifiableList(tempList);
    	} else
    		nMeasurements = measurementNames.size();
    	
    	MeasurementList measurementList = MeasurementListFactory.createMeasurementList(nMeasurements, MeasurementListType.DOUBLE);
    	
    	Set<PathClass> ignored = pathClasses.stream().filter(p -> PathClassTools.isIgnoredClass(p)).collect(Collectors.toSet());
    	
    	// Calculate totals for all non-ignored classes
    	Map<PathClass, Long> pathClassTotals = new LinkedHashMap<>();
    	long totalWithoutIgnored = 0L;
    	if (counts != null) {
	    	for (int c = 0; c < counts.length; c++) {
	    		PathClass pathClass = classificationLabels.get(c);
	    		// Skip background channels
	    		if (pathClass == null || ignored.contains(pathClass))
	    			continue;
	    		long temp = counts[c];
				totalWithoutIgnored += temp;
	    		pathClassTotals.put(pathClass, pathClassTotals.getOrDefault(pathClass, 0L) + temp);
	    	}
    	} else {
    		for (var pathClass : pathClasses)
    			if (pathClass != null && !ignored.contains(pathClass))
    				pathClassTotals.put(pathClass, 0L);
    	}
    	
    	// Add measurements for classes
    	for (var entry : pathClassTotals.entrySet()) {
    		String name = entry.getKey().toString();
			String namePercentage = "Classifier: " + name + " %";
			String nameArea = "Classifier: " + name + " area " + pixelAreaUnits;
			if (tempList != null) {
				tempList.add(namePercentage);
				tempList.add(nameArea);
			}
			if (counts != null) {
				measurementList.putMeasurement(namePercentage, (double)entry.getValue()/totalWithoutIgnored * 100.0);
				if (!Double.isNaN(pixelArea)) {
					measurementList.putMeasurement(nameArea, entry.getValue() * pixelArea);
				}
			}
    	}

    	// Add total area (useful as a check)
		String nameArea = "Classifier: Total annotated area " + pixelAreaUnits;
		String nameAreaWithoutIgnored = "Classifier: Total quantified area " + pixelAreaUnits;
		if (counts != null && !Double.isNaN(pixelArea)) {
			if (tempList != null) {
    			tempList.add(nameArea);
    			tempList.add(nameAreaWithoutIgnored);
    		}
			measurementList.putMeasurement(nameArea, totalWithoutIgnored * pixelArea);
			measurementList.putMeasurement(nameAreaWithoutIgnored, total * pixelArea);
		}

    	measurementList.close();
    	return measurementList;
    }
	
}