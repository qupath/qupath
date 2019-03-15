package qupath.lib.classifiers.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;
 
public class PixelClassificationMeasurementManager {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationMeasurementManager.class);
	
	private PixelClassificationImageServer classifierServer;
	
    private Set<String> measurementsAdded = new HashSet<>();
    private Map<PathObject, ROI> measuredObjects = new WeakHashMap<>();
	
	private ThreadLocal<BufferedImage> imgTileMask = new ThreadLocal<>();

	public PixelClassificationMeasurementManager(PixelClassificationImageServer classifierServer) {
		this.classifierServer = classifierServer;
	}
	
	
	boolean resetMeasurements(PathObject pathObject) {
		return updateMeasurements(pathObject, classifierServer.getChannels(), null, 0L, Double.NaN, null);
	}
	
		    
    /**
     * Add percentage measurements if possible, or discard if not possible.
     * 
     * @param pathObject
     * @return
     */
    boolean addPercentageMeasurements(final PathObject pathObject, final boolean cachedOnly) {
    	
       	// Check if we've already measured this
    	if (measuredObjects.getOrDefault(pathObject, null) == pathObject.getROI())
    		return false;

        if (!classifierServer.hasPixelSizeMicrons())
        	return false;
    	
        List<ImageChannel> channels = classifierServer.getChannels();
        long[] counts = null;
        long total = 0L;
                

    	if (pathObject == null || !pathObject.getROI().isArea()) {
  			return resetMeasurements(pathObject);
    	}

        ImageServer<BufferedImage> server = classifierServer;//imageData.getServer();
        
        // Calculate area of a pixel
        double requestedDownsample = classifierServer.getDownsampleForResolution(0);
        double pixelArea = (server.getPixelWidthMicrons() * requestedDownsample) * (server.getPixelHeightMicrons() * requestedDownsample);
        String pixelAreaUnits = GeneralTools.micrometerSymbol() + "^2";
        if (!pathObject.isDetection()) {
        	double scale = requestedDownsample / 1000.0;
            pixelArea = (server.getPixelWidthMicrons() * scale) * (server.getPixelHeightMicrons() * scale);
            pixelAreaUnits = "mm^2";
        }

        
        // Check we have a suitable output type
        OutputType type = classifierServer.getOutputType();
        if (type == OutputType.Features)
  			return resetMeasurements(pathObject);
        
        
    	ROI roi = pathObject.getROI();

        Shape shape = PathROIToolsAwt.getShape(roi);
        
        // Get the regions we need
        var regionRequest = RegionRequest.createInstance(server.getPath(), requestedDownsample, roi);
        Collection<TileRequest> requests = server.getTiles(regionRequest);
        
        if (requests.isEmpty()) {
        	logger.debug("Request empty for {}", pathObject);
  			return resetMeasurements(pathObject);
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
	  			return resetMeasurements(pathObject);
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
			case Classification:
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
			case Probability:
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
			case Features:
				return false;
			default:
				return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
        	}
        }
    	return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
    }

    
    synchronized void resetMeasurements(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects) {
    	boolean changes = false;
    	for (PathObject pathObject : pathObjects) {
    		if (updateMeasurements(pathObject, classifierServer.getChannels(), null, 0L, Double.NaN, null))
    			changes = true;
    	}
    	if (hierarchy != null && changes) {
        	hierarchy.fireObjectMeasurementsChangedEvent(this, pathObjects);    		
    	}
    }

    
    
    private synchronized boolean updateMeasurements(PathObject pathObject, List<ImageChannel> channels, long[] counts, long total, double pixelArea, String pixelAreaUnits) {
    	// Remove any existing measurements
    	int nBefore = pathObject.getMeasurementList().size();
  		pathObject.getMeasurementList().removeMeasurements(measurementsAdded.toArray(new String[0]));
  		boolean changes = nBefore != pathObject.getMeasurementList().size();
    	
  		
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
    		if (counts != null) {
    			pathObject.getMeasurementList().putMeasurement(namePercentage, (double)counts[c]/totalWithoutIgnored * 100.0);
    			measurementsAdded.add(namePercentage);
    			if (!Double.isNaN(pixelArea)) {
    				pathObject.getMeasurementList().putMeasurement(nameArea, counts[c] * pixelArea);
        			measurementsAdded.add(nameArea);
    			}
    			changes = true;
    		}
    	}

    	// Add total area (useful as a check)
		String nameArea = "Classifier: Total annotated area " + pixelAreaUnits;
		String nameAreaWithoutIgnored = "Classifier: Total quantified area " + pixelAreaUnits;
		if (counts != null && !Double.isNaN(pixelArea)) {
			pathObject.getMeasurementList().putMeasurement(nameAreaWithoutIgnored, totalWithoutIgnored * pixelArea);
			pathObject.getMeasurementList().putMeasurement(nameArea, total * pixelArea);
			measurementsAdded.add(nameAreaWithoutIgnored);
			measurementsAdded.add(nameArea);
			changes = true;
		}

    	if (changes)
    		pathObject.getMeasurementList().close();
    	if (counts == null) {
        	measuredObjects.remove(pathObject);
    		return changes;
    	}
    	measuredObjects.put(pathObject, pathObject.getROI());
    	return changes;
    }
	
	
}