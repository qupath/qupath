/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
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
import java.util.stream.LongStream;

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

	/**
	 * Constructor.
	 * @param classifierServer the server for which measurements will be made.
	 */
	public PixelClassificationMeasurementManager(ImageServer<BufferedImage> classifierServer) {
		this.classifierServer = classifierServer;
		synchronized (measuredROIs) {
			if (!measuredROIs.containsKey(classifierServer))
				measuredROIs.put(classifierServer, new HashMap<>());
		}
		
        // Calculate area of a pixel
        requestedDownsample = classifierServer.getDownsampleForResolution(0);
        PixelCalibration cal = classifierServer.getPixelCalibration();
        if (cal.unitsMatch2D()) {
	        pixelArea = (cal.getPixelWidth().doubleValue() * requestedDownsample) * (cal.getPixelHeight().doubleValue() * requestedDownsample);
	        pixelAreaUnits = cal.getPixelWidthUnit() + "^2";
        } else {
        	pixelArea = requestedDownsample * requestedDownsample;
            pixelAreaUnits = "px^2";
        }
        
//        if (cal.hasPixelSizeMicrons()) {
//	        pixelArea = (cal.getPixelWidthMicrons() * requestedDownsample) * (cal.getPixelHeightMicrons() * requestedDownsample);
//	        pixelAreaUnits = GeneralTools.micrometerSymbol() + "^2";
//	        double scale = requestedDownsample / 1000.0;
//	        pixelArea = (cal.getPixelWidthMicrons() * scale) * (cal.getPixelHeightMicrons() * scale);
//	        pixelAreaUnits = "mm^2";
//        } else {
//        	pixelArea = requestedDownsample * requestedDownsample;
//            pixelAreaUnits = "px^2";
//        }
		
		// Handle root object if we just have a single plane
		if (classifierServer.nZSlices() == 1 || classifierServer.nTimepoints() == 1)
			rootROI = ROIs.createRectangleROI(0, 0, classifierServer.getWidth(), classifierServer.getHeight(), ImagePlane.getDefaultPlane());
		
		// Treat as multi-class probabilities if that is requested or if we just have one output channel
		var type = classifierServer.getMetadata().getChannelType();
		if (type == ChannelType.MULTICLASS_PROBABILITY || 
				(type == ChannelType.PROBABILITY && classifierServer.nChannels() == 1))
			isMulticlass = true;
		
        // Just to get measurement names
		updateMeasurements(classifierServer.getMetadata().getClassificationLabels(), null, pixelArea, pixelAreaUnits);
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
		return ml.get(name);
	}

	/**
	 * Get the names of all measurements that may be returned.
	 * @return
	 */
	public List<String> getMeasurementNames() {
		return measurementNames == null ? Collections.emptyList() : measurementNames;
	}
	
	
	/**
	 * Check if a ROI shape completely contains tile. This should err on the side of caution and 
	 * only return true if it is very confident that all pixels of the tile are inside the shape.
	 * @param shape
	 * @param tile
	 * @param padding pad the tile to make the estimate more conservative (and deal with non-zero stroke thickness for shape masks)
	 * @return
	 */
	private static boolean completelyContainsTile(Shape shape, TileRequest tile, double padding) {
		return shape.contains(
				tile.getImageX()-padding,
				tile.getImageY()-padding,
				tile.getImageWidth()+padding*2,
    			tile.getImageHeight()+padding*2     
				);
	}
	
	/**
	 * Check if a ROI shape could intersect a tile. This should err on the side of caution and 
	 * only eliminate cases where there is definitely no intersection.
	 * @param shape
	 * @param tile
	 * @param padding pad the tile to make the estimate more conservative (and deal with non-zero stroke thickness for shape masks)
	 * @return
	 */
	private static boolean mayIntersectTile(Shape shape, TileRequest tile, double padding) {
		return shape.intersects(
				tile.getImageX()-padding,
				tile.getImageY()-padding,
				tile.getImageWidth()+padding*2,
    			tile.getImageHeight()+padding*2     
				);
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
	        // Skip tiles that don't intersect with the ROI shape
	        if (shape != null) {
	        	var shapeTemp = shape;
	        	requests = requests.stream().filter(r -> mayIntersectTile(shapeTemp, r, r.getDownsample())).collect(Collectors.toList());
	        }
        } else
        	requests = Collections.emptyList();
        
        if (requests.isEmpty()) {
        	logger.debug("Request empty for {}", roi);
  			return null;
        }
        

        // Try to get all cached tiles - if this fails, we need to return quickly if cachedOnly==true
        // TODO: Avoid pre-caching all tiles; this will fail on large regions + low memory, when not all tiles can be stored in the cache
        Map<TileRequest, BufferedImage> localCache = new HashMap<>();
        List<TileRequest> tilesToRequest = new ArrayList<>();
        for (TileRequest request : requests) {
        	BufferedImage tile = classifierServer.getCachedTile(request);
			// If we only accept cached tiles, and we don't have one, return immediately
			if (cachedOnly && tile == null) {
				return null;
			}
			// Preserve tiles in a local cache, to avoid risk that they could
			// be cleared from the main tile cache before we use them
			tilesToRequest.add(request);
			if (tile != null)
				localCache.put(request, tile);
			// TODO: Consider enabling requests to be made here
        }
        
        // Calculate stained proportions
        BasicStroke stroke = null;
        byte[] mask = null;
    	BufferedImage imgMask = imgTileMask.get();
    	
    	Rectangle bounds = new Rectangle();
    	Point2D p1 = new Point2D.Double();
    	Point2D p2 = new Point2D.Double();
    	
    	long startTime = System.currentTimeMillis();
    	
        for (var region : tilesToRequest) {
        	// Remove from the local cache (so eligible for garbage collection sooner)
        	BufferedImage tile = localCache.remove(region);
        	
        	// If null, we need to request the prediction now
        	if (!cachedOnly && tile == null) {
	        	try {
	        		// Try the cache again... just in case
					tile = classifierServer.getCachedTile(region);
					if (tile == null)
						tile = classifierServer.readRegion(region.getRegionRequest());
				} catch (IOException e) {
					logger.error("Error requesting tile " + region, e);
				}
        	}
        	// We failed to get a required tile - return
        	if (tile == null)
        		return null;
        	
        	// Create a binary mask that is at least as big as the current tile 
        	if (imgMask == null || imgMask.getWidth() < tile.getWidth() || imgMask.getHeight() < tile.getHeight() || imgMask.getType() != BufferedImage.TYPE_BYTE_GRAY) {
        		imgMask = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        		imgTileMask.set(imgMask);
        	}

        	
    		// Check if the entire image is within the mask
    		boolean fullMask = false;

        	if (shape != null && completelyContainsTile(shape, region, region.getDownsample())) {
        		// Quickly test if the entire image is masked
        		// If so, we can save time by avoiding creating and testing the mask
        		fullMask = true;
        		bounds.setRect(0, 0, tile.getWidth(), tile.getHeight());
        	} else {
        	
	        	// Initialize the bounds
	    		bounds.setBounds(0, 0, -1, -1);
	    		        	
	        	// Get the tile, which is needed for sub-pixel accuracy
	        	if (roi.isLine() || roi.isArea()) {
	        		
	        		Graphics2D g2d = imgMask.createGraphics();
	        		g2d.setColor(Color.BLACK);
	        		g2d.fillRect(0, 0, tile.getWidth(), tile.getHeight());
	        		g2d.setColor(Color.WHITE);
	        		g2d.scale(1.0/region.getDownsample(), 1.0/region.getDownsample());
	        		g2d.translate(-region.getTileX() * region.getDownsample(), -region.getTileY() * region.getDownsample());
	        		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	        		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
	        		if (roi.isLine()) {
	        			float fDownsample = (float)region.getDownsample();
	        			if (stroke == null || stroke.getLineWidth() != fDownsample)
	        				stroke = new BasicStroke((float)fDownsample);
	        			g2d.setStroke(stroke);
	        			g2d.draw(shape);
	        		} else if (roi.isArea())
	        			g2d.fill(shape);

	        		// Use the Graphics2D transform to set a bounding box that contains the ROI
	        		// This can dramatically reduce the number of samples that need to be checked sometimes
	        		var transform = g2d.getTransform();
	        		p1.setLocation(roi.getBoundsX(), roi.getBoundsY());
	        		transform.transform(p1, p1);
	        		p2.setLocation(roi.getBoundsX() + roi.getBoundsWidth(), roi.getBoundsY() + roi.getBoundsHeight());
	        		transform.transform(p2, p2);
	        		bounds.x = (int)Math.max(0, p1.getX() - 1);
	        		bounds.y = (int)Math.max(0, p1.getY() - 1);
	        		bounds.width = (int)Math.min(tile.getWidth(), Math.ceil(p2.getX() + 1)) - bounds.x;
	        		bounds.height = (int)Math.min(tile.getHeight(), Math.ceil(p2.getY() + 1)) - bounds.y;

	        		g2d.dispose();
	        		
	        	} else if (roi.isPoint()) {
	        		// Check if we are adding any points, so we can skip if not
	        		boolean anyPoints = false;
	        		for (var p : roi.getAllPoints()) {
	        			int x = (int)((p.getX() - region.getImageX()) / region.getDownsample());
	        			int y = (int)((p.getY() - region.getImageY()) / region.getDownsample());
	        			// Check if point within range
	        			if (x >= 0 && y >= 0 && x < tile.getWidth() && y < tile.getHeight()) {
	        				// Clear the raster
	        				if (!anyPoints) {
	        					Graphics2D g2d = imgMask.createGraphics();
	        	        		g2d.setColor(Color.BLACK);
	        	        		g2d.fillRect(0, 0, tile.getWidth(), tile.getHeight());
	        	        		g2d.dispose();
		                		anyPoints = true;
	        				}
	        				imgMask.getRaster().setSample(x, y, 0, 255);
	                		bounds.add(x, y);
	                		bounds.add(x+1, y+1);
	        			}
	        		}
	        		if (!anyPoints) {
//	        			logger.trace("Skipping - just no point");
	        			continue;
	        		}
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
						counts = BufferedImageTools.computeUnsignedIntHistogram(tile.getRaster(), counts, fullMask ? null : imgMask.getRaster(), bounds);
						break;
					case PROBABILITY:
						// Take classification from the channel with the highest value
						if (nChannels > 1) {
							counts = BufferedImageTools.computeArgMaxHistogram(tile.getRaster(), counts, fullMask ? null : imgMask.getRaster(), bounds);
							break;
						}
						// For one channel, fall through & treat as multiclass
					case MULTICLASS_PROBABILITY:
						// For multiclass, count
						if (counts == null)
							counts = new long[nChannels];
						double threshold = getProbabilityThreshold(tile.getRaster());
						for (int c = 0; c < nChannels; c++)
							counts[c] += BufferedImageTools.computeAboveThresholdCounts(tile.getRaster(), c, threshold, fullMask ? null : imgMask.getRaster(), bounds);
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
        
    	long endTime = System.currentTimeMillis();
    	if (logger.isDebugEnabled()) {
    		long totalCounts = LongStream.of(counts).sum();
    		logger.debug("Counted {} pixels in {} ms (area {} {})", totalCounts, endTime - startTime, GeneralTools.formatNumber(totalCounts*pixelArea, 2), pixelAreaUnits);
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
  		
    	long total = counts == null ? 0L : GeneralTools.sum(counts);
    	
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
    	
    	Set<PathClass> ignored = pathClasses.stream().filter(p -> p == null || PathClassTools.isIgnoredClass(p)).collect(Collectors.toSet());
    	
    	// Calculate totals for all non-ignored classes
    	Map<PathClass, Long> pathClassTotals = new LinkedHashMap<>();
    	long totalWithoutIgnored = 0L;
    	if (counts != null) {
	    	for (var entry : classificationLabels.entrySet()) {
	    		PathClass pathClass = entry.getValue();
	    		// Skip background channels
	    		if (pathClass == null || ignored.contains(pathClass))
	    			continue;
	    		int c = entry.getKey();
	    		long temp = counts == null || c >= counts.length ? 0L : counts[c];
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
    		var pathClass = entry.getKey();
    		String name = pathClass.toString();
			String namePercentage = name + " %";
			String nameArea = name + " area " + pixelAreaUnits;
			if (tempList != null) {
				if (pathClassTotals.size() > 1)
					tempList.add(namePercentage);
				tempList.add(nameArea);
			}
			if (counts != null) {
				long count = entry.getValue();
				if (pathClassTotals.size() > 1)
					measurementList.put(namePercentage, (double)count/totalWithoutIgnored * 100.0);
				if (!Double.isNaN(pixelArea)) {
					measurementList.put(nameArea, count * pixelArea);
				}
			}
    	}

    	// Add total area (useful as a check)
		String nameArea = "Total annotated area " + pixelAreaUnits;
		String nameAreaWithoutIgnored = "Total quantified area " + pixelAreaUnits;
		if (counts != null && !Double.isNaN(pixelArea)) {
			if (tempList != null) {
    			tempList.add(nameArea);
    			tempList.add(nameAreaWithoutIgnored);
    		}
			measurementList.put(nameArea, totalWithoutIgnored * pixelArea);
			measurementList.put(nameAreaWithoutIgnored, total * pixelArea);
		}

    	measurementList.close();
    	return measurementList;
    }
	
}