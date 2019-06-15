package qupath.lib.gui.models;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
 
public class PixelClassificationMeasurementManager {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationMeasurementManager.class);
	
	private static Map<ImageServer<BufferedImage>, Map<ROI, MeasurementList>> measuredROIs = new WeakHashMap<>();
	
	private ImageServer<BufferedImage> classifierServer;
	private List<String> measurementNames = null;
	
	private ROI rootROI = null; // ROI for the Root object, if required
		
	private ThreadLocal<BufferedImage> imgTileMask = new ThreadLocal<>();
	
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
		
        // Just to get measurement names
		updateMeasurements(classifierServer.getChannels(), new long[classifierServer.getChannels().size()], 0, pixelArea, pixelAreaUnits);
	}
	
	
	public Number getMeasurementValue(PathObject pathObject, String name) {
		var roi = pathObject.getROI();
		if (roi == null || pathObject.isRootObject())
			roi = rootROI;
		
		var map = measuredROIs.get(classifierServer);
		if (map == null || roi == null)
			return null;
		
		var ml = map.get(roi);
		if (ml == null) {
			ml = calculateMeasurements(roi, true);
			if (ml == null)
				return null;
			map.put(roi, ml);
		}
		return ml.getMeasurementValue(name);
	}

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
    	
//        if (!classifierServer.hasPixelSizeMicrons())
//        	return null;
    	
        List<ImageChannel> channels = classifierServer.getChannels();
        long[] counts = null;
        long total = 0L;
        

        ImageServer<BufferedImage> server = classifierServer;//imageData.getServer();
        
        // Check we have a suitable output type
        ImageServerMetadata.ChannelType type = classifierServer.getOutputType();
        if (type == ImageServerMetadata.ChannelType.FEATURE)
  			return null;
        
        
        Shape shape = RoiTools.getShape(roi);
        
        // Get the regions we need
        var regionRequest = RegionRequest.createInstance(server.getPath(), requestedDownsample, roi);
        Collection<TileRequest> requests = server.getTileRequestManager().getTileRequests(regionRequest);
        
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
        counts = new long[channels.size()];
        total = 0L;
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
        	Graphics2D g2d = imgMask.createGraphics();
        	g2d.setColor(Color.BLACK);
        	g2d.fillRect(0, 0, tile.getWidth(), tile.getHeight());
        	g2d.setColor(Color.WHITE);
        	g2d.scale(1.0/region.getDownsample(), 1.0/region.getDownsample());
        	g2d.translate(-region.getTileX() * region.getDownsample(), -region.getTileY() * region.getDownsample());
        	g2d.fill(shape);
        	g2d.dispose();
        	
			int h = tile.getHeight();
			int w = tile.getWidth();
			if (mask == null || mask.length != h*w)
				mask = new byte[w * h];
        	
        	switch (type) {
			case CLASSIFICATION:
				var raster = tile.getRaster();
				var rasterMask = imgMask.getRaster();
				int b = 0;
				try {
					rasterMask.getDataElements(0, 0, w, h, mask);
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							if (mask[y*w+x] == (byte)0)
								continue;
							int ind = raster.getSample(x, y, b);
							// TODO: This could be out of range!  But shouldn't be...
							counts[ind]++;
							total++;
						}					
					}
				} catch (Exception e) {
					logger.error("Error calculating classification areas", e);
				}
				break;
			case PROBABILITY:
				// Take classification from the channel with the highest value
				raster = tile.getRaster();
				rasterMask = imgMask.getRaster();
				int nChannels = Math.min(channels.size(), raster.getNumBands()); // Expecting these to be the same...
				try {
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							if (rasterMask.getSample(x, y, 0) == 0)
								continue;
							double maxValue = raster.getSampleDouble(x, y, 0);
							int ind = 0;
							for (int i = 1; i < nChannels; i++) {
								double val = raster.getSampleDouble(x, y, i);
								if (val > maxValue) {
									maxValue = val;
									ind = i;
								}
							}
							counts[ind]++;
							total++;
						}					
					}
				} catch (Exception e) {
					logger.error("Error calculating classification areas", e);
				}
				break;
			default:
				// TODO: Consider handling other OutputTypes?
				return updateMeasurements(channels, counts, total, pixelArea, pixelAreaUnits);
        	}
        }
    	return updateMeasurements(channels, counts, total, pixelArea, pixelAreaUnits);
    }

    
    
    private synchronized MeasurementList updateMeasurements(List<ImageChannel> channels, long[] counts, long total, double pixelArea, String pixelAreaUnits) {
  		
    	boolean addNames = measurementNames == null;
    	List<String> tempList = null;
    	int nMeasurements = channels.size()*2+2;
    	if (addNames) {
    		tempList = new ArrayList<>();
    		measurementNames = Collections.unmodifiableList(tempList);
    	} else
    		nMeasurements = measurementNames.size();
    	
    	MeasurementList measurementList = MeasurementListFactory.createMeasurementList(nMeasurements, MeasurementListType.DOUBLE);
    	
    	long totalWithoutIgnored = 0L;
    	if (counts != null) {
    		for (int c = 0; c < channels.size(); c++) {
    			if (channels.get(c).isTransparent())
        			continue;
    			totalWithoutIgnored += counts[c];
    		}
    	}
    	
    	for (int c = 0; c < channels.size(); c++) {
    		// Skip background channels
    		if (channels.get(c).isTransparent())
    			continue;
    		
    		String namePercentage = "Classifier: " + channels.get(c).getName() + " %";
    		String nameArea = "Classifier: " + channels.get(c).getName() + " area " + pixelAreaUnits;
    		if (tempList != null) {
    			tempList.add(namePercentage);
    			tempList.add(nameArea);
    		}
    		if (counts != null) {
    			measurementList.putMeasurement(namePercentage, (double)counts[c]/totalWithoutIgnored * 100.0);
    			if (!Double.isNaN(pixelArea)) {
    				measurementList.putMeasurement(nameArea, counts[c] * pixelArea);
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