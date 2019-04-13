package qupath.lib.classifiers.gui;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.classifiers.opencv.gui.PixelClassifierImageSelectionPane.PersistentTileCache;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifierDNN;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.common.SimpleThreadFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractImageDataOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PathOverlay that gives the results of pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassificationOverlay extends AbstractImageDataOverlay  {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private QuPathViewer viewer;

    private PixelClassificationImageServer classifierServer;
    
    private Map<RegionRequest, BufferedImage> cacheRGB = Collections.synchronizedMap(new HashMap<>());
    private Set<TileRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());
    
    private ExecutorService pool;
    
    private boolean useAnnotationMask = false;
    private boolean livePrediction = false;
    
    private PersistentTileCache tileCache;
    
    public PixelClassificationOverlay(final QuPathViewer viewer, final PixelClassificationImageServer classifierServer) {
    	this(viewer, classifierServer, null);
    }
    
    public PixelClassificationOverlay(final QuPathViewer viewer, final PixelClassificationImageServer classifierServer, final PersistentTileCache tileCache) {
        super(viewer.getOverlayOptions(), classifierServer.getImageData());
        
        // Choose number of threads based on how intensive the processing will be
        // TODO: Permit classifier to control request
        int nThreads = Math.max(1, PathPrefs.getNumCommandThreads());
        if (classifierServer.getClassifier() instanceof OpenCVPixelClassifierDNN)
        	nThreads = 1;
        pool = Executors.newFixedThreadPool(
        		nThreads, new SimpleThreadFactory(
        				"classifier-overlay", true, Thread.NORM_PRIORITY-2));
        
        this.classifierServer = classifierServer;
        this.viewer = viewer;
    }
    
    
    
    public boolean getLivePrediction() {
    	return livePrediction;
    }
    
    public void setLivePrediction(boolean livePrediction) {
    	this.livePrediction = livePrediction;
    	if (livePrediction)
    		viewer.repaint();
    }    
    
    
    @Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
        // For now, bind the display to the display of detections
        if (!viewer.getOverlayOptions().getShowPixelClassification())
            return;

        var imageData = getImageData();
        if (imageData == null)
            return;
        var hierarchy = imageData.getHierarchy();
        
//        ImageServer<BufferedImage> server = imageData.getServer();
        var server = classifierServer;

//        viewer.getImageRegionStore().paintRegion(server, g2d, AwtTools.getBounds(imageRegion), viewer.getZPosition(), viewer.getTPosition(), downsampleFactor, null, observer, null);
//        if (5 > 2)
//        	return;
        
//        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();
        double requestedDownsample = server.getPreferredDownsampleFactor(downsampleFactor);

        if (requestedDownsample > server.getDownsampleForResolution(0))
        	g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        else
        	g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        
//        boolean requestingTiles = downsampleFactor <= requestedDownsample * 4.0;
        boolean requestingTiles = true;

        Collection<PathObject> objectsForOverlap = null;
        if (requestingTiles && hierarchy.getTMAGrid() != null) {
            objectsForOverlap = hierarchy.getObjectsForRegion(TMACoreObject.class, imageRegion, null);
        }

        // Request tiles, of the size that the classifier wants to receive

     // Get the displayed clip bounds for fast checking if ROIs need to be drawn
        RegionRequest fullRequest;
    	Shape shapeRegion = g2d.getClip();
     	if (shapeRegion == null)
     		fullRequest = RegionRequest.createInstance(server.getPath(), downsampleFactor, imageRegion);
     	else
     		fullRequest = RegionRequest.createInstance(server.getPath(), downsampleFactor, AwtTools.getImageRegion(shapeRegion, imageRegion.getZ(), imageRegion.getT()));
        
        Collection<TileRequest> tiles = classifierServer.getTiles(fullRequest);

//        requests = requests.stream().map(r -> RegionRequest.createInstance(r.getPath(), requestedDownsample, r)).collect(Collectors.toList());
        
        var annotations = hierarchy.getObjects(null, PathAnnotationObject.class);
        
        // Clear pending requests, since we'll insert new ones (perhaps in a different order)
    	this.pendingRequests.clear();

//        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Loop through & paint classified tiles if we have them, or request tiles if we don't
        for (TileRequest tile : tiles) {
        	
        	var request = tile.getRegionRequest();
        	
        	if (useAnnotationMask) {
        		boolean doPaint = false;
        		for (var annotation : annotations) {
        			var roi = annotation.getROI();
        			if (!roi.isArea())
        				continue;
        			if (roi.getZ() == request.getZ() &&
        					roi.getT() == request.getT() &&
        					request.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight())) {
//        				var shape = roi.getShape();
//        				// Intersects doesn't seem to be working nicely with Ellipse?
////        				if (shape instanceof Ellipse2D)
//        					shape = shape.getBounds();
//        				if (shape.intersects(request.getX(), request.getY(), request.getWidth(), request.getHeight())) {
            				doPaint = true;
//        				}
        				break;
        			}
        		}
        		if (!doPaint)
        			continue;
        	}
        	
        	// Try to get an RGB image, supplying a cached probably tile (if we have one)
            BufferedImage imgRGB = getCachedRGBImage(request, server.getCachedTile(tile));
            if (imgRGB != null) {
            	// Get the cached RGB painted version (since painting can be a fairly expensive operation)
                g2d.drawImage(imgRGB, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
                continue;
            }
            
            // We might not necessarily want tiles (e.g. if we are zoomed out for a particularly slow classifier)
            if (!requestingTiles) {
                continue;
            }

            if (objectsForOverlap != null) {
                if (!objectsForOverlap.stream().anyMatch(pathObject -> {
                    ROI roi = pathObject.getROI();
                    if (!roi.isArea())
                    	return false;
                    return request.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
                    }
                ))
                   continue;
            }

            // Request a tile
            if (livePrediction)
            	requestTile(tile);
        }
    }
    
    /**
     * Get a cached RGB image if we have one.  If we don't, optionally supply an original image 
     * that will be painted to create a new RGB image (which will then be cached).
     * 
     * @param request
     * @param img
     * @return
     */
    BufferedImage getCachedRGBImage(RegionRequest request, BufferedImage img) {
    	var imgRGB = cacheRGB.get(request);
        // If we don't have an RGB version, create one
        if (imgRGB == null && img != null) {
            if (img.getType() == BufferedImage.TYPE_INT_ARGB ||
            		img.getType() == BufferedImage.TYPE_INT_RGB ||
            		img.getType() == BufferedImage.TYPE_BYTE_INDEXED ||
            		img.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                imgRGB = img;
            } else {
                imgRGB = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = imgRGB.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
            }
            cacheRGB.put(request, imgRGB);
        }
        return imgRGB;
    }
    
    
    
    public void stop(boolean resetMeasurements) {
//    	if (imageData != null && resetMeasurements) {
//    		manager.resetMeasurements(imageData.getHierarchy(), imageData.getHierarchy().getObjects(null, PathAnnotationObject.class));
//    	}
//    	viewer.getImageRegionStore().clearCacheForServer(classifierServer);
    	List<Runnable> pending = this.pool.shutdownNow();
    	cacheRGB.clear();
    	logger.debug("Stopped classification overlay, dropped {} requests", pending.size());
    	if (getImageData() != null)
    		setImageData(null);
    }
    
    
    public PixelClassificationImageServer getPixelClassificationServer() {
    	return classifierServer;
    }
    
        
    public void setUseAnnotationMask(final boolean useMask) {
    	if (this.useAnnotationMask == useMask)
    		return;
    	this.useAnnotationMask = useMask;
    	// Cancel pending requests if we need less than previously
    	if (useMask)
    		this.pendingRequests.clear();
    	if (viewer != null)
    		viewer.repaint();
    }
    
    public boolean useAnnotationMask() {
    	return useAnnotationMask;
    }
    

    void requestTile(TileRequest tile) {
        // Make the request, if it isn't already pending
        if (pendingRequests.add(tile)) {
            pool.submit(() -> {
            	if (pool.isShutdown())
            		return;
            	// Check we still need to make the request
            	if (!pendingRequests.contains(tile)) {
            		return;
            	}
                try {
                	BufferedImage imgResult = null;
                	if (tileCache != null)
                		imgResult = tileCache.readFromCache(tile.getRegionRequest());
                	if (imgResult == null)
                		imgResult = classifierServer.readBufferedImage(tile.getRegionRequest());
                	else {
                		logger.info("Read cached tile: {}", tile);
                	}
                    getCachedRGBImage(tile.getRegionRequest(), imgResult);
                    viewer.repaint();
                    
                    var imageData = getImageData();
                    var hierarchy = imageData == null ? null : imageData.getHierarchy();
                    if (hierarchy != null) {
	                    var annotations = hierarchy.getAnnotationObjects();
	                    if (!annotations.isEmpty())
	                    	hierarchy.fireObjectMeasurementsChangedEvent(this, annotations);
                    }
                    
                } catch (Exception e) {
                   logger.error("Error requesting tile classification", e);
                } finally {
                    pendingRequests.remove(tile);
                }
            });
        }
    }

    @Override
	public void setImageData(final ImageData<BufferedImage> imageData) {
		super.setImageData(imageData);
		if (getImageData() == null && classifierServer != null) {
			try {
				classifierServer.close();
				classifierServer = null;
			} catch (Exception e) {
				logger.warn("Exception when closing classification server", e);
			}
		}
	}

	@Override
	public boolean supportsImageDataChange() {
		return false;
	}
	

}