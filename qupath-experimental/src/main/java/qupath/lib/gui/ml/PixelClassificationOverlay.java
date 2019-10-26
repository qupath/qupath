package qupath.lib.gui.ml;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractImageDataOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ml.pixel.FeatureImageServer;
import qupath.opencv.ml.pixel.features.FeatureCalculator;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
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
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;

/**
 * {@link PathOverlay} that gives the results of pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassificationOverlay extends AbstractImageDataOverlay  {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private QuPathViewer viewer;
    
    private ImageRenderer renderer;
    private long rendererLastTimestamp = 0L;

    private Map<RegionRequest, BufferedImage> cacheRGB = Collections.synchronizedMap(new HashMap<>());
    private Set<TileRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());
    
    private ExecutorService pool;
    
    private Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> fun;
    
    private boolean useAnnotationMask = false;
    private boolean livePrediction = false;
    
    private ObservableBooleanValue showOverlay;

    
    private PixelClassificationOverlay(final QuPathViewer viewer, final int nThreads, final Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> fun) {
        super(viewer.getOverlayOptions(), viewer.getImageData());
        
        showOverlay = viewer.getOverlayOptions().showPixelClassificationProperty();
        
        // Choose number of threads based on how intensive the processing will be
        // TODO: Permit classifier to control request
//        if (classifierServer.getClassifier() instanceof OpenCVPixelClassifierDNN)
//        	nThreads = 1;
        pool = Executors.newFixedThreadPool(
        		nThreads, ThreadTools.createThreadFactory(
        				"classifier-overlay", true, Thread.NORM_PRIORITY-2));
        
        this.fun = fun;
        this.viewer = viewer;
    }
    
    
    public static PixelClassificationOverlay createPixelClassificationOverlay(final QuPathViewer viewer, final PixelClassifier classifier) {
        int nThreads = Math.max(1, PathPrefs.getNumCommandThreads());
    	return new PixelClassificationOverlay(viewer, nThreads, new ClassifierServerFunction(classifier));
    }
    
    
    public static PixelClassificationOverlay createFeatureDisplayOverlay(final QuPathViewer viewer,
    		final FeatureCalculator<BufferedImage> calculator,
    		PixelCalibration resolution, ImageRenderer renderer) {
    	return createFeatureDisplayOverlay(viewer, new FeatureCalculatorServerFunction(calculator, resolution), renderer);
    }
    
    public static PixelClassificationOverlay createFeatureDisplayOverlay(final QuPathViewer viewer,
    		final ImageServer<BufferedImage> featureServer, ImageRenderer renderer) {
    	return createFeatureDisplayOverlay(viewer, new FeatureCalculatorServerFunction(featureServer), renderer);
    }
    
    private static PixelClassificationOverlay createFeatureDisplayOverlay(final QuPathViewer viewer,
    		final FeatureCalculatorServerFunction fun, ImageRenderer renderer) {
    	var overlay = new PixelClassificationOverlay(viewer, 1, fun);
    	overlay.setRenderer(renderer);
//    	overlay.showOverlay = viewer.getOverlayOptions().showPixelClassificationProperty().not();
    	return overlay;
    }
    
    
    static class FeatureCalculatorServerFunction implements Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> {
    	
    	private ImageServer<BufferedImage> server;
    	private FeatureCalculator<BufferedImage> calculator;
    	private PixelCalibration resolution;
    	
    	private FeatureCalculatorServerFunction(FeatureCalculator<BufferedImage> calculator, PixelCalibration resolution) {
    		this.calculator = calculator;
    		this.resolution = resolution;
    	}
    	
    	private FeatureCalculatorServerFunction(ImageServer<BufferedImage> server) {
    		this.server = server;
    	}

		@Override
		public ImageServer<BufferedImage> apply(ImageData<BufferedImage> imageData) {
			if (imageData == null) {
				server = null;
				return null;
			}
			if (server != null && (server instanceof FeatureImageServer && ((FeatureImageServer)server).getImageData() != imageData))
				server = null;
			if (server == null && calculator != null && calculator.supportsImage(imageData)) {
	    		try {
					server = new FeatureImageServer(imageData, calculator, resolution);
				} catch (IOException e) {
					logger.error("Error creating FeatureImageServer", e);
				}
	    	}
	    	return server;
		}
    	
    }
    
    
    static class ClassifierServerFunction implements Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> {
    	
    	private PixelClassificationImageServer server;
    	private PixelClassifier classifier;
    	
    	private ClassifierServerFunction(PixelClassifier classifier) {
    		this.classifier = classifier;
    	}

		@Override
		public ImageServer<BufferedImage> apply(ImageData<BufferedImage> imageData) {
			if (imageData == null) {
				server = null;
				return null;
			}
			if (server != null && server.getImageData() != imageData)
				server = null;
			if (server == null && classifier.supportsImage(imageData)) {
	    		server = new PixelClassificationImageServer(imageData, classifier);
	    		PixelClassificationImageServer.setPixelLayer(imageData, server);
	    	}
	    	return server;
		}
    	
    }

    private synchronized void setRenderer(ImageRenderer renderer) {
    	if (this.renderer == renderer)
    		return;
    	this.renderer = renderer;
    	cacheRGB.clear();
    }

    private ImageRenderer getRenderer() {
    	return renderer;
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
        if (!showOverlay.get())
            return;

        var imageData = getImageData();
        if (imageData == null)
            return;
        var hierarchy = imageData.getHierarchy();
        
//        ImageServer<BufferedImage> server = imageData.getServer();
        var server = getPixelClassificationServer();
        if (server == null)
        	return;

//        viewer.getImageRegionStore().paintRegion(server, g2d, AwtTools.getBounds(imageRegion), viewer.getZPosition(), viewer.getTPosition(), downsampleFactor, null, observer, null);
//        if (5 > 2)
//        	return;
        
        if (renderer != null && rendererLastTimestamp != renderer.getLastChangeTimestamp()) {
        	cacheRGB.clear();
        	rendererLastTimestamp = renderer.getLastChangeTimestamp();
        }
        
//        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();
		double requestedDownsample = ServerTools.getPreferredDownsampleFactor(server, downsampleFactor);

		var gCopy = (Graphics2D)g2d.create();
		
        if (requestedDownsample > server.getDownsampleForResolution(0))
        	gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        else
        	gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        var comp = getAlphaComposite();
    	var previousComposite = gCopy.getComposite();
        if (comp != null) {
        	if (previousComposite instanceof AlphaComposite)
        		gCopy.setComposite(comp.derive(((AlphaComposite) previousComposite).getAlpha() * comp.getAlpha()));
        	else
        		gCopy.setComposite(comp);
        }
        
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
        
        Collection<TileRequest> tiles = server.getTileRequestManager().getTileRequests(fullRequest);

//        requests = requests.stream().map(r -> RegionRequest.createInstance(r.getPath(), requestedDownsample, r)).collect(Collectors.toList());
        
        var annotations = hierarchy.getAnnotationObjects();
        
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
            	gCopy.drawImage(imgRGB, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
//                g2d.setColor(Color.RED);
//                g2d.drawRect(request.getX(), request.getY(), request.getWidth(), request.getHeight());
//                System.err.println(request.getHeight() == imgRGB.getHeight());
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
            	requestTile(tile, server);
        }
        gCopy.dispose();
    }
    
    /**
     * Get a cached RGB image if we have one.  If we don't, optionally supply an original image 
     * that will be painted to create a new RGB image (which will then be cached).
     * 
     * @param request
     * @param img
     * @return
     */
    synchronized BufferedImage getCachedRGBImage(RegionRequest request, BufferedImage img) {
    	var imgRGB = cacheRGB.get(request);
        // If we don't have an RGB version, create one
        if (imgRGB == null && img != null) {
            if (img.getType() == BufferedImage.TYPE_INT_ARGB ||
            		img.getType() == BufferedImage.TYPE_INT_RGB ||
            		img.getType() == BufferedImage.TYPE_BYTE_INDEXED ||
            		img.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                imgRGB = img;
            } else if (renderer == null) {
                imgRGB = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = imgRGB.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
            } else {
            	try {
            		imgRGB = renderer.applyTransforms(img, null);
            	} catch (Exception e) {
            		logger.error("Exception rendering image", e);
            	}
            }
            cacheRGB.put(request, imgRGB);
        }
        return imgRGB;
    }
    
    
    
    public void stop() {
    	List<Runnable> pending = this.pool.shutdownNow();
    	cacheRGB.clear();
    	logger.debug("Stopped classification overlay, dropped {} requests", pending.size());
    	if (getImageData() != null)
    		setImageData(null);
    }
    
    
    public synchronized ImageServer<BufferedImage> getPixelClassificationServer() {
    	return fun.apply(getImageData());
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
    

    void requestTile(TileRequest tile, ImageServer<BufferedImage> classifierServer) {
        // Make the request, if it isn't already pending
        if (!pool.isShutdown() && pendingRequests.add(tile)) {
            pool.submit(() -> {
            	if (pool.isShutdown())
            		return;
            	// Check we still need to make the request
            	if (!pendingRequests.contains(tile)) {
            		return;
            	}
                try {
                	classifierServer.readBufferedImage(tile.getRegionRequest());
                    viewer.repaint();
                    var channelType = classifierServer.getMetadata().getChannelType();
                    if (channelType == ChannelType.CLASSIFICATION || channelType == ChannelType.PROBABILITY || channelType == ChannelType.MULTICLASS_PROBABILITY) {
		                var imageData = getImageData();
		                var hierarchy = imageData == null ? null : imageData.getHierarchy();
		                if (hierarchy != null) {
		                	var changed = new ArrayList<PathObject>();
		                	changed.add(hierarchy.getRootObject());
		                	changed.addAll(hierarchy.getAnnotationObjects());
		                	Platform.runLater(() -> {
		                		hierarchy.fireObjectMeasurementsChangedEvent(this, changed);
		                	});
		                }
                    }
                } catch (Exception e) {
                   logger.error("Error requesting tile classification: {}", e.getLocalizedMessage());
                   logger.debug("", e);
                } finally {
                    pendingRequests.remove(tile);
                }
            });
        }
    }

	@Override
	public boolean supportsImageDataChange() {
		return true;
	}
	

}