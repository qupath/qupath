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

package qupath.lib.gui.viewer.overlays;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
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
import java.awt.image.DataBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;

/**
 * {@link PathOverlay} that gives the results of pixel classification.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassificationOverlay extends AbstractImageOverlay  {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private ObjectProperty<ImageRenderer> renderer = new SimpleObjectProperty<>();
    private long rendererLastTimestamp = 0L;

    private Map<RegionRequest, BufferedImage> cacheRGB = Collections.synchronizedMap(new HashMap<>());
    private Set<TileRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());
    private Set<TileRequest> currentRequests = Collections.synchronizedSet(new HashSet<>());
    
    private int maxThreads = ThreadTools.getParallelism();
    private ThreadPoolExecutor pool;
    
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
        
        var threadFactory = ThreadTools.createThreadFactory(
				"classifier-overlay", true, Thread.NORM_PRIORITY-2);
        
        if (nThreads > 0)
        	maxThreads = nThreads;
        pool = (ThreadPoolExecutor)Executors.newFixedThreadPool(
        		maxThreads, threadFactory);
        
        this.renderer.addListener((v, o, n) -> cacheRGB.clear());
        
        this.fun = fun;
    }
    
    /**
     * Create an overlay to display the live application of a {@link PixelClassifier} to an image, using the default number of parallel 
     * threads for classification.
     * @param options the options controlling the overlay display
     * @param classifier the classifier
     * @return
     */
    public static PixelClassificationOverlay create(final OverlayOptions options, final PixelClassifier classifier) {
    	int nThreads = Math.max(1, PathPrefs.numCommandThreadsProperty().get());
    	return create(options, classifier, nThreads);
    }
    
    /**
     * Create an overlay to display the live application of a {@link PixelClassifier} to an image.
     * @param options the options controlling the overlay display
     * @param classifier the classifier
     * @param nThreads number of parallel threads to use for classification (will be clipped to 1 or greater)
     * @return
     */
    public static PixelClassificationOverlay create(final OverlayOptions options, final PixelClassifier classifier, final int nThreads) {
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
     * @param fun function to create an {@link ImageServer} from the {@link ImageData}.
     *            Note that the server generated by the function call will be cached. 
     *            If it is necessary to control the caching, this needs to be done externally 
     *            and {@link #create(OverlayOptions, Map, ImageRenderer)} should be used instead.
     * @param renderer rendered used to create an RGB image
     * @return the {@link PixelClassificationOverlay}
     */
    public static PixelClassificationOverlay create(final OverlayOptions options,
    		final Function<ImageData<BufferedImage>, ImageServer<BufferedImage>> fun, ImageRenderer renderer) {
    	var overlay = new PixelClassificationOverlay(options, 1, fun);
    	overlay.setRenderer(renderer);
    	return overlay;
    }
    
    /**
     * Create an overlay to display a live image that can be created from an existing {@link ImageData}.
     * This differs from {@link #create(OverlayOptions, Function, ImageRenderer)} in that 
     * a cached map of classifier servers is used directly.
     * 
     * @param options options to control the overlay display
     * @param map map to obtain an {@link ImageServer} from an {@link ImageData}
     * @param renderer rendered used to create an RGB image
     * @return the {@link PixelClassificationOverlay}
     */
    public static PixelClassificationOverlay create(final OverlayOptions options,
    		final Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> map, ImageRenderer renderer) {
    	var overlay = new PixelClassificationOverlay(options, 1, data -> null);
    	overlay.cachedServers = map;
    	overlay.setRenderer(renderer);
    	return overlay;
    }
    
    /**
     * Create an overlay to display a live image that can be created from an existing {@link ImageData}.
     * 
     * @param options options to control the overlay display
     * @param fun function to create an {@link ImageServer} from the {@link ImageData}
     * @param renderer rendered used to create an RGB image
     * @return the {@link PixelClassificationOverlay}
     * @deprecated Use {@link #create(OverlayOptions, Function, ImageRenderer)} instead.
     */
    @Deprecated
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
    	
    	private PixelClassificationImageServer server; // Cached server
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
	    	}
	    	return server;
		}
    	
    }
    
    /**
     * Get the {@link ImageRenderer} property used with this overlay.
     * @return
     */
    public ObjectProperty<ImageRenderer> rendererProperty() {
    	return renderer;
    }
    
    /**
     * Get the {@link ImageRenderer} used with this overlay, which may be null.
     * @return
     */
    public ImageRenderer getRenderer() {
    	return renderer.get();
    }
    
    /**
     * Set the maximum number of threads that may be used during live prediction.
     * @param nThreads
     */
    public void setMaxThreads(int nThreads) {
    	maxThreads = Math.max(1, nThreads);
    	if (maxThreads < pool.getCorePoolSize())
    		pool.setCorePoolSize(maxThreads);
    	pool.setMaximumPoolSize(maxThreads);
    	pool.setCorePoolSize(maxThreads);
		logger.debug("Number of parallel threads set to {}", nThreads);
    }
    
    /**
     * Get the maximum number of threads that may be used during live prediction.
     * @return 
     */
    public int getMaxThreads() {
    	return maxThreads;
    }

    /**
     * Set the {@link ImageRenderer} to be used with this overlay.
     * @param renderer
     */
    public void setRenderer(ImageRenderer renderer) {
    	this.renderer.set(renderer);
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
    
    /**
     * Get the {@link ImageServer} that would be used by this overlay for the specified {@link ImageData}.
     * Note that the servers are cached internally.
     * @param imageData
     * @return
     */
    public ImageServer<BufferedImage> getPixelClassificationServer(ImageData<BufferedImage> imageData) {
    	// TODO: Support not caching servers (e.g. if the caching is performed externally) - probably by accepting a map in a create method (so the map becomes the cache)
//    	return imageData == null ? null : createPixelClassificationServer(imageData);
    	return imageData == null ? null : cachedServers.computeIfAbsent(imageData, data -> createPixelClassificationServer(data));
    }
    
    @Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {
        // For now, bind the display to the display of detections
        if (!showOverlay.get())
            return;

        if (imageData == null)
            return;
                
//        ImageServer<BufferedImage> server = imageData.getServer();
        var server = getPixelClassificationServer(imageData);
        if (server == null)
        	return;
        
        // Show classified tiles. Without this, opacity can make it hard to see which regions have been processed.
        // Note that if the alpha value is too large, tile boundaries can appear at some viewing magnifications (previous default was 32)
        var colorComplete = imageData.getImageType() == ImageData.ImageType.FLUORESCENCE ? 
        		ColorToolsAwt.getCachedColor(255, 255, 255, 1) :
        		ColorToolsAwt.getCachedColor(0, 0, 0, 1);
        
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
        
    	var renderer = this.renderer.get();
        if (renderer != null && rendererLastTimestamp != renderer.getLastChangeTimestamp()) {
        	clearCache();
        	rendererLastTimestamp = renderer.getLastChangeTimestamp();
        }
        
//        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();
		double requestedDownsample = ServerTools.getPreferredDownsampleFactor(server, downsampleFactor);

		var gCopy = (Graphics2D)g2d.create();
				
        if (requestedDownsample > server.getDownsampleForResolution(0))
        	gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        else
        	// Only use specified interpolation when upsampling
    		setInterpolation(gCopy);
//        	gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        var comp = getAlphaComposite();
    	var previousComposite = gCopy.getComposite();
        if (comp != null) {
        	if (previousComposite instanceof AlphaComposite)
        		gCopy.setComposite(comp.derive(((AlphaComposite) previousComposite).getAlpha() * comp.getAlpha()));
        	else
        		gCopy.setComposite(comp);
        }
        
        
        Collection<TileRequest> tiles = server.getTileRequestManager().getTileRequests(fullRequest);
        
        if (fullRequest != null) {
        	double x = (Math.max(0, fullRequest.getMinX()) + Math.min(server.getWidth(), fullRequest.getMaxX())) / 2.0;
        	double y = (Math.max(0, fullRequest.getMinY()) + Math.min(server.getHeight(), fullRequest.getMaxY())) / 2.0;
        	var p = new Point2(x, y);
        	tiles = new ArrayList<>(tiles);
        	((List<TileRequest>)tiles).sort(
        			Comparator.comparingDouble((TileRequest t) -> p.distanceSq(t.getImageX() + t.getImageWidth() / 2.0, t.getImageY() + t.getImageHeight() / 2.0))
//        			.reversed()
        			);
        }

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
            	gCopy.setColor(colorComplete);
            	gCopy.fillRect(request.getX(), request.getY(), request.getWidth(), request.getHeight());
            	// Get the cached RGB painted version (since painting can be a fairly expensive operation)
            	gCopy.drawImage(imgRGB, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
//                g2d.setColor(Color.RED);
//                g2d.drawRect(request.getX(), request.getY(), request.getWidth(), request.getHeight());
//                System.err.println(request.getHeight() == imgRGB.getHeight());
                continue;
            }
            
            // Request a tile
            if (livePrediction) {
            	requestTile(tile, imageData, server);
            }
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
    	var renderer = this.renderer.get();
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
      * Clear any cached tiles.
      */
     public void clearCache() {
    	 cacheRGB.clear();
     }

    
    /**
     * Stop the overlap, halting any pending tile requests.
     */
    public void stop() {
    	List<Runnable> pending = this.pool.shutdownNow();
    	clearCache();
    	logger.debug("Stopped classification overlay, dropped {} requests", pending.size());
    }
    
    /**
     * Get an {@link ImageServer}, where pixels are determined by applying a {@link PixelClassifier} to another (wrapped) image.
     * @param imageData 
     * @return
     */
    synchronized ImageServer<BufferedImage> createPixelClassificationServer(ImageData<BufferedImage> imageData) {
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
                	classifierServer.readRegion(tile.getRegionRequest());
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
    
    
    @Override
    public String getLocationString(ImageData<BufferedImage> imageData, double x, double y, int z, int t) {
    	if (getLocationStringFunction() == null) {
	    	var classifierServer = imageData == null ? null : getPixelClassificationServer(imageData);
	    	if (classifierServer == null)
	    		return null;
	    	return getDefaultLocationString(classifierServer, x, y, z, t);
    	} else
    		return super.getLocationString(imageData, x, y, z, t);
    }
    	
    
    /**
     * Default method for getting a location string from an {@link ImageServer} using cached tiles.
     * If tiles are not cached, no string is returned.
     * <p>
     * May be used by classes implementing {@link PathOverlay#getLocationString(ImageData, double, double, int, int)}
     * 
     * @param server
     * @param x
     * @param y
     * @param z
     * @param t
     * @return location String based upon pixel values and cached tiles, or null if no String is available
     */
    public static String getDefaultLocationString(ImageServer<BufferedImage> server, double x, double y, int z, int t) {
    	
    	int level = 0;
    	var tile = server.getTileRequestManager().getTileRequest(level, (int)Math.round(x), (int)Math.round(y), z, t);
    	if (tile == null)
    		return null;
    	var img = server.getCachedTile(tile);
    	if (img == null)
    		return null;

    	int xx = (int)Math.floor((x - tile.getImageX()) / tile.getDownsample());
    	int yy = (int)Math.floor((y - tile.getImageY()) / tile.getDownsample());
    	if (xx < 0 || yy < 0 || xx >= img.getWidth() || yy >= img.getHeight())
    		return null;
    	
//    	String coords = GeneralTools.formatNumber(x, 1) + "," + GeneralTools.formatNumber(y, 1);
    	var channelType = server.getMetadata().getChannelType();
    	
    	double scale = 1.0;
    	double probabilityScale = 1.0;
    	if (img.getRaster().getDataBuffer().getDataType() == DataBuffer.TYPE_BYTE)
    		probabilityScale = 1.0/255.0;
    	String defaultName = "";
    	
    	switch (channelType) {
		case CLASSIFICATION:
        	var classificationLabels = server.getMetadata().getClassificationLabels();
        	int sample = img.getRaster().getSample(xx, yy, 0); 
        	var pathClass = classificationLabels.get(sample);
        	String name = pathClass == null ? null : pathClass.toString();
        	if (name == null)
        		return null;
        	return String.format("Classification: %s", name);
		case MULTICLASS_PROBABILITY:
		case PROBABILITY:
			defaultName = "Prediction: ";
			scale = probabilityScale;
			break;
		case DENSITY:
			defaultName = "Density: ";
		case FEATURE:
		case DEFAULT:
		default:
    	}
    	var channels = server.getMetadata().getChannels();
    	StringBuilder sb = new StringBuilder(defaultName);
    	StringBuilder sbWithNames = new StringBuilder(defaultName);
		for (int c = 0; c < channels.size(); c++) {
			double sampleDouble = img.getRaster().getSampleDouble(xx, yy, c) * scale;
			String num = GeneralTools.formatNumber(sampleDouble, 2);
			if (c != 0) {	
				sb.append(", ");
				sbWithNames.append(", ");
			}
			sb.append(num);
			sbWithNames.append(channels.get(c).getName()).append(": ").append(num);
		}
		// Include names only if the string is short enough
		if (sbWithNames.length() < 100)
			return sbWithNames.toString();
		else
			return sb.toString();
    }
    

}