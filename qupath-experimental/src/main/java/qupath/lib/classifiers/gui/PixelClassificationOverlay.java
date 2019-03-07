package qupath.lib.classifiers.gui;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.classifiers.pixel.OpenCVPixelClassifierDNN;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.SimpleThreadFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
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

import javafx.application.Platform;

/**
 * PathOverlay that gives the results of pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassificationOverlay extends AbstractOverlay implements PathObjectHierarchyListener, QuPathViewerListener {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private QuPathViewer viewer;

    private PixelClassificationImageServer classifierServer;
    
    private Map<RegionRequest, BufferedImage> cacheRGB = Collections.synchronizedMap(new HashMap<>());
    private Set<TileRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());
    
    private ExecutorService pool;
    
    private ImageData<BufferedImage> imageData;
    
    private PixelClassificationMeasurementManager manager;
    
    private boolean useAnnotationMask = false;
    private boolean livePrediction = false;
    
    public PixelClassificationOverlay(final QuPathViewer viewer, final PixelClassificationImageServer classifierServer) {
        super();
        this.manager = new PixelClassificationMeasurementManager(classifierServer);
        
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
        this.viewer.addViewerListener(this);
        
        this.imageData = viewer.getImageData();
        imageData.getHierarchy().addPathObjectListener(this);
//        imageDataChanged(viewer, null, viewer.getImageData());
    }
    
    
    
    public boolean getLivePrediction() {
    	return livePrediction;
    }
    
    public void setLivePrediction(boolean livePrediction) {
    	this.livePrediction = livePrediction;
    	if (livePrediction)
    		viewer.repaint();
    }
    
    
    private synchronized void updateAnnotationMeasurements() {
    	if (imageData == null) {
    		return;
    	}
    	
    	PathObjectHierarchy hierarchy = imageData.getHierarchy();
    	List<PathObject> changed = new ArrayList<>();
    	
//    	hierarchy.getObjects(null, PathAnnotationObject.class).parallelStream().forEach(annotation -> {
//    		if (addPercentageMeasurements(annotation))
//    			changed.add(annotation);
//    	});
    	
    	if (!pool.isShutdown()) {
	    	pool.submit(() -> {
	        	for (PathObject annotation : hierarchy.getObjects(null, PathAnnotationObject.class)) {
	        		if (manager.addPercentageMeasurements(annotation, true)) {
	        			changed.add(annotation);
	        			if (Thread.interrupted())
	        				return;
	        		}
	        	}
	        	if (!changed.isEmpty())
	        		Platform.runLater(() -> hierarchy.fireObjectMeasurementsChangedEvent(this, changed));    		
	    	});
    	}
    }
    
    public String getResultsString(double x, double y) {
    	if (viewer == null || classifierServer == null)
    		return null;
    	return getResultsString(classifierServer, x, y, viewer.getZPosition(), viewer.getTPosition());
    }

    
    
    public static String getResultsString(PixelClassificationImageServer classifierServer, double x, double y, int z, int t) {
    	if (classifierServer == null)
    		return null;
    	
    	int level = 0;
    	var tile = classifierServer.getTile(level, (int)Math.round(x), (int)Math.round(y), z, t);
    	if (tile == null)
    		return null;
    	var img = classifierServer.getCachedTile(tile);
    	if (img == null)
    		return null;

    	int xx = (int)Math.floor((x - tile.getImageX()) / tile.getDownsample());
    	int yy = (int)Math.floor((y - tile.getImageY()) / tile.getDownsample());
    	if (xx < 0 || yy < 0 || xx >= img.getWidth() || yy >= img.getHeight())
    		return null;
    	
//    	String coords = GeneralTools.formatNumber(x, 1) + "," + GeneralTools.formatNumber(y, 1);
    	
    	var channels = classifierServer.getChannels();
    	if (classifierServer.getOutputType() == OutputType.Classification) {
        	int sample = img.getRaster().getSample(xx, yy, 0); 		
        	return String.format("Classification: %s", channels.get(sample).getName());
//        	return String.format("Classification (%s):\n%s", coords, channels.get(sample).getName());
    	} else {
    		var array = new String[channels.size()];
    		for (int c = 0; c < channels.size(); c++) {
    			float sample = img.getRaster().getSampleFloat(xx, yy, c);
    			if (img.getRaster().getDataBuffer().getDataType() == DataBuffer.TYPE_BYTE)
    				sample /= 255f;
    			array[c] = channels.get(c).getName() + ": " + GeneralTools.formatNumber(sample, 2);
    		}
        	return String.format("Prediction: %s", String.join(", ", array));
    	}
    }
    
    
    
    @Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
        // For now, bind the display to the display of detections
        if (!viewer.getOverlayOptions().getShowPixelClassification())
            return;

        if (imageData == null)
            return;
        
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
        if (requestingTiles && imageData.getHierarchy().getTMAGrid() != null) {
            objectsForOverlap = imageData.getHierarchy().getObjectsForRegion(TMACoreObject.class, imageRegion, null);
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
        
        var annotations = imageData.getHierarchy().getObjects(null, PathAnnotationObject.class);
        
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
        if (livePrediction)
        	updateAnnotationMeasurements();
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
    
    
    
    public void stop() {
    	if (imageData != null) {
    		manager.resetMeasurements(imageData.getHierarchy(), imageData.getHierarchy().getObjects(null, PathAnnotationObject.class));
    	}
//    	viewer.getImageRegionStore().clearCacheForServer(classifierServer);
    	imageDataChanged(viewer, imageData, null);
    	List<Runnable> pending = this.pool.shutdownNow();
    	viewer.removeViewerListener(this);
    	cacheRGB.clear();
    	logger.debug("Stopped classification overlay, dropped {} requests", pending.size());
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
                	BufferedImage imgResult = classifierServer.readBufferedImage(tile.getRegionRequest());
                    getCachedRGBImage(tile.getRegionRequest(), imgResult);
                    viewer.repaint();
                    Platform.runLater(() -> updateAnnotationMeasurements());
                } catch (Exception e) {
                   logger.error("Error requesting tile classification", e);
                } finally {
                    pendingRequests.remove(tile);
                }
            });
        }
    }


	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (event.isAddedOrRemovedEvent() || event.isStructureChangeEvent())
			updateAnnotationMeasurements();
	}


	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		if (this.imageData != null) {
			this.imageData.getHierarchy().removePathObjectListener(this);
			if (classifierServer != null) {
				try {
					classifierServer.close();
				} catch (Exception e) {
					logger.warn("Exception when closing classification server", e);
				}
			}
		}
		viewer.getCustomOverlayLayers().remove(this);
	}


	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}


	@Override
	public void viewerClosed(QuPathViewer viewer) {}
	

}