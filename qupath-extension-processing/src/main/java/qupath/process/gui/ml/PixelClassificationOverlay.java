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

package qupath.process.gui.ml;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageDataServer;
import qupath.opencv.ops.ImageOps;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
public class PixelClassificationOverlay extends AbstractOverlay  {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private ImageRenderer renderer;
    private long rendererLastTimestamp = 0L;

    private Map<RegionRequest, BufferedImage> cacheRGB = Collections.synchronizedMap(new HashMap<>());
    private Set<TileRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());
    private Set<TileRequest> currentRequests = Collections.synchronizedSet(new HashSet<>());
    
    private ExecutorService pool;
    
    private Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> fun;
    
    private boolean livePrediction = false;
    
    private Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> cachedServers = new WeakHashMap<>();
    
    private ObservableBooleanValue showOverlay;

    
    private PixelClassificationOverlay(final OverlayOptions options, final int nThreads, final Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> fun) {
        super(options);
        
        showOverlay = options.showPixelClassificationProperty();
        
        // Choose number of threads based on how intensive the processing will be
        // TODO: Permit classifier to control request
//        if (classifierServer.getClassifier() instanceof OpenCVPixelClassifierDNN)
//        	nThreads = 1;
        pool = Executors.newFixedThreadPool(
        		nThreads, ThreadTools.createThreadFactory(
        				"classifier-overlay", true, Thread.NORM_PRIORITY-2));
        
        this.fun = fun;
    }
    
    /**
     * Create an overlay to display the live application of a {@link PixelClassifier} to an image, using the default number of parallel 
     * threads for classification.
     * @param options the options controlling the overlay display
     * @param classifier the classifier
     * @return
     */
    public static PixelClassificationOverlay createPixelClassificationOverlay(final OverlayOptions options, final PixelClassifier classifier) {
    	int nThreads = Math.max(1, PathPrefs.numCommandThreadsProperty().get() / 2);
    	return createPixelClassificationOverlay(options, classifier, nThreads);
    }
    
    /**
     * Create an overlay to display the live application of a {@link PixelClassifier} to an image.
     * @param options the options controlling the overlay display
     * @param classifier the classifier
     * @param nThreads number of parallel threads to use for classification (will be clipped to 1 or greater)
     * @return
     */
    public static PixelClassificationOverlay createPixelClassificationOverlay(final OverlayOptions options, final PixelClassifier classifier, final int nThreads) {
    	return new PixelClassificationOverlay(options, Math.max(1, nThreads), new ClassifierServerFunction(classifier));
    }
    
    
//    public static PixelClassificationOverlay createFeatureDisplayOverlay(final QuPathViewer viewer,
//    		final ImageDataOp calculator,
//    		PixelCalibration resolution, ImageRenderer renderer) {
//    	return createFeatureDisplayOverlay(viewer, new FeatureCalculatorServerFunction(calculator, resolution), renderer);
//    }
    
//    /**
//     * Create an overlay to display a live image displaying the features for a {@link PixelClassifier}.
//     * @param viewer the viewer to which the overlay should be added 
//     * @param featureServer an {@link ImageServer} representing the features
//     * @param renderer a rendered used to convert the features to RGB
//     * @return
//     */
//    public static PixelClassificationOverlay createFeatureDisplayOverlay(final QuPathViewer viewer,
//    		final ImageServer<BufferedImage> featureServer, ImageRenderer renderer) {
//    	return createFeatureDisplayOverlay(viewer, new FeatureCalculatorServerFunction(featureServer), renderer);
//    }
    
    /**
     * Create an overlay to display a live image that can be created from an existing {@link ImageData}.
     * 
     * @param options options to control the overlay display
     * @param fun function to create an {@link ImageServer} from the {@link ImageData}
     * @param renderer rendered used to create an RGB image
     * @return the {@link PixelClassificationOverlay}
     */
    public static PixelClassificationOverlay createFeatureDisplayOverlay(final OverlayOptions options,
    		final Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> fun, ImageRenderer renderer) {
    	var overlay = new PixelClassificationOverlay(options, 1, fun);
    	overlay.setRenderer(renderer);
    	return overlay;
    }
    
    
    static class FeatureCalculatorServerFunction implements Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> {
    	
    	private ImageServer<BufferedImage> server;
    	private ImageDataOp calculator;
    	private PixelCalibration resolution;
    	
    	private FeatureCalculatorServerFunction(ImageDataOp calculator, PixelCalibration resolution) {
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
			if (server != null && (server instanceof ImageDataServer && ((ImageDataServer<?>)server).getImageData() != imageData))
				server = null;
			if (server == null && calculator != null && calculator.supportsImage(imageData)) {
				server = ImageOps.buildServer(imageData, calculator, resolution);
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
    
    /**
     * Query whether live prediction is turned on.
     * @return
     */
    public boolean getLivePrediction() {
    	return livePrediction;
    }
    
    /**
     * Turn on or off live prediction.
     * This requests tile classifications as the overlay is being viewed.
     * @param livePrediction
     */
    public void setLivePrediction(boolean livePrediction) {
    	this.livePrediction = livePrediction;
//    	if (livePrediction)
//    		viewer.repaint();
    }    
    
    
    @Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {
        // For now, bind the display to the display of detections
        if (!showOverlay.get())
            return;

        if (imageData == null)
            return;
        
//        ImageServer<BufferedImage> server = imageData.getServer();
        var server = cachedServers.computeIfAbsent(imageData, data -> getPixelClassificationServer(data));
        if (server == null)
        	return;

        
        // Get the displayed clip bounds for fast checking if ROIs need to be drawn
        RegionRequest fullRequest;
    	Shape shapeRegion = g2d.getClip();
     	if (shapeRegion == null)
     		fullRequest = RegionRequest.createInstance(server.getPath(), downsampleFactor, imageRegion);
     	else
     		fullRequest = RegionRequest.createInstance(server.getPath(), downsampleFactor, AwtTools.getImageRegion(shapeRegion, imageRegion.getZ(), imageRegion.getT()));

        // If we have a filter, we might not need to do anything
    	var filter = getOverlayOptions().getPixelClassificationRegionFilter();
    	// Avoid this check; it causes confusion when zoomed in
//    	if (!filter.test(imageData, fullRequest))
//    		return;
        
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
        
        
        Collection<TileRequest> tiles = server.getTileRequestManager().getTileRequests(fullRequest);

        // Clear pending requests, since we'll insert new ones (perhaps in a different order)
    	this.pendingRequests.clear();

//        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Loop through & paint classified tiles if we have them, or request tiles if we don't
        for (TileRequest tile : tiles) {
        	
        	var request = tile.getRegionRequest();
        	
        	if (filter != null && !filter.test(imageData, request))
        		continue;
        	
        	// Try to get an RGB image, supplying a server that can be queried for a corresponding non-RGB cached tile if needed
            BufferedImage imgRGB = getCachedTileRGB(tile, server);
            if (imgRGB != null) {
            	// Get the cached RGB painted version (since painting can be a fairly expensive operation)
            	gCopy.drawImage(imgRGB, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
//                g2d.setColor(Color.RED);
//                g2d.drawRect(request.getX(), request.getY(), request.getWidth(), request.getHeight());
//                System.err.println(request.getHeight() == imgRGB.getHeight());
                continue;
            }
            
            // Request a tile
            if (livePrediction)
            	requestTile(tile, imageData, server);
        }
        gCopy.dispose();
    }
    
    /**
     * Get a cached RGB image if we have one.  If we don't, optionally supply a server that may be 
     * queried for the corresponding (possibly-non-RGB) cached tile.
     * 
     * @param request
     * @param server
     * @return
     */
     BufferedImage getCachedTileRGB(TileRequest request, ImageServer<BufferedImage> server) {
    	var imgRGB = cacheRGB.get(request.getRegionRequest());
    	if (imgRGB != null || server == null)
    		return imgRGB;
        // If we have a tile that isn't RGB, then create the RGB version we need
    	var img = server.getCachedTile(request);
        if (img != null) {
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
            cacheRGB.put(request.getRegionRequest(), imgRGB);
        }
        return imgRGB;
    }
    
    
    /**
     * Stop the overlap, halting any pending tile requests.
     */
    public void stop() {
    	List<Runnable> pending = this.pool.shutdownNow();
    	cacheRGB.clear();
    	logger.debug("Stopped classification overlay, dropped {} requests", pending.size());
    }
    
    /**
     * Get an {@link ImageServer}, where pixels are determined by applying a {@link PixelClassifier} to another (wrapped) image.
     * @param imageData 
     * @return
     */
    synchronized ImageServer<BufferedImage> getPixelClassificationServer(ImageData<BufferedImage> imageData) {
    	return fun.apply(imageData);
    }
    

    void requestTile(TileRequest tile, ImageData<BufferedImage> imageData, ImageServer<BufferedImage> classifierServer) {
        // Make the request, if it isn't already pending
        if (!pool.isShutdown() && pendingRequests.add(tile)) {
            pool.submit(() -> {
            	if (pool.isShutdown())
            		return;
            	// Check we still need to make the request
            	if (!pendingRequests.contains(tile) || !currentRequests.add(tile)) {
            		return;
            	}
//            	System.err.println(tile.hashCode() + " - " + ImageRegion.createInstance(tile.getImageX(), tile.getImageY(), tile.getImageWidth(), tile.getImageHeight(), tile.getZ(), tile.getT()));
            	var changed = new ArrayList<PathObject>();
                var hierarchy = imageData == null ? null : imageData.getHierarchy();
                try {
                	classifierServer.readBufferedImage(tile.getRegionRequest());
//                    viewer.repaint();
                	QuPathGUI.getInstance().repaintViewers();
                    var channelType = classifierServer.getMetadata().getChannelType();
                    if (channelType == ChannelType.CLASSIFICATION || channelType == ChannelType.PROBABILITY || channelType == ChannelType.MULTICLASS_PROBABILITY) {
		                if (hierarchy != null) {
//		                	if (pendingRequests.size() <= 1)
//		                		changed.add(hierarchy.getRootObject());
	                		changed.add(hierarchy.getRootObject());
		                	hierarchy.getObjectsForRegion(PathAnnotationObject.class, tile.getRegionRequest(), changed);
//		                	changed.addAll(hierarchy.getAnnotationObjects());
		                }
                    }
                } catch (Exception e) {
                   logger.error("Error requesting tile classification: ", e.getLocalizedMessage(), e);
                } finally {
                    currentRequests.remove(tile);
                    pendingRequests.remove(tile);
                    if (hierarchy != null && !changed.isEmpty()) {
	                	Platform.runLater(() -> {
	                		// TODO: We don't want to fire a load of 'heavy' events, so we state that isChanging = true (beware this may need revised!)
	                		hierarchy.fireObjectMeasurementsChangedEvent(this, changed, true); //!pendingRequests.isEmpty());
	                	});
                    }
                }
            });
        }
    }
	

}