package qupath.lib.classifiers.gui;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.classifiers.pixel.PixelClassifierOutputChannel;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.SimpleThreadFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.stores.ImageRegionStoreHelpers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.ImageObserver;
import java.awt.image.SampleModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;

public class PixelClassificationOverlay extends AbstractOverlay implements PathObjectHierarchyListener, QuPathViewerListener {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private QuPathViewer viewer;

    private PixelClassifier classifier;
    private PixelClassificationImageServer classifierServer;
    
    private Map<RegionRequest, BufferedImage> cache = new HashMap<>();
    private Map<BufferedImage, BufferedImage> cacheRGB = new HashMap<>();
    private Set<RegionRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());
    
    private Set<String> measurementsAdded = new HashSet<>();

    private ExecutorService pool = Executors.newFixedThreadPool(8, new SimpleThreadFactory("classifier-overlay", true));

    private Map<PathObject, ROI> measuredObjects = new WeakHashMap<>();
    
    private ImageData<BufferedImage> imageData;
    
    public PixelClassificationOverlay(final QuPathViewer viewer, final PixelClassifier classifier) {
        super();
        this.cache = ImageServerProvider.getCache(BufferedImage.class);
        
        this.classifier = classifier;
        this.viewer = viewer;
        this.viewer.addViewerListener(this);
        imageDataChanged(viewer, null, viewer.getImageData());
    }
    
    
    private synchronized void updateAnnotationMeasurements() {
    	if (imageData == null) {
    		return;
    	}
    	
    	PathObjectHierarchy hierarchy = imageData.getHierarchy();
    	List<PathObject> changed = new ArrayList<>();
    	for (PathObject annotation : hierarchy.getObjects(null, PathAnnotationObject.class)) {
    		if (addPercentageMeasurements(annotation))
    			changed.add(annotation);
    	}
		hierarchy.fireObjectMeasurementsChangedEvent(this, changed);
    }
    
    
    synchronized boolean addPercentageMeasurements(final PathObject pathObject) {
    	
       	// Check if we've already measured this
    	if (measuredObjects.getOrDefault(pathObject, null) == pathObject.getROI())
    		return false;

    	
        List<PixelClassifierOutputChannel> channels = classifier.getMetadata().getChannels();
        long[] counts = null;
        long total = 0L;
                
 
    	if (imageData == null || !pathObject.getROI().isArea()) {
  			return updateMeasurements(pathObject, channels, null, 0L, Double.NaN, null);
    	}

        ImageServer<BufferedImage> server = classifierServer;//imageData.getServer();
        
        // Calculate area of a pixel
        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();
        double pixelArea = (server.getPixelWidthMicrons() * requestedDownsample) * (server.getPixelHeightMicrons() * requestedDownsample);
        String pixelAreaUnits = GeneralTools.micrometerSymbol() + "^2";
        if (!pathObject.isDetection()) {
        	double scale = requestedDownsample / 1000.0;
            pixelArea = (server.getPixelWidthMicrons() * scale) * (server.getPixelHeightMicrons() * scale);
            pixelAreaUnits = "mm^2";
        }

        
        // Check we have a suitable output type
        OutputType type = classifier.getMetadata().getOutputType();
        if (type == OutputType.Features)
        	return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
        
        
    	ROI roi = pathObject.getROI();

    	int tileWidth = classifier.getMetadata().getInputWidth();// - classifier.requestedPadding() * 2;
        int tileHeight = classifier.getMetadata().getInputHeight();// - classifier.requestedPadding() * 2;
        if (tileWidth <= 0)
        	tileWidth = 256;
        if (tileHeight <= 0)
        	tileHeight = 256;
        
        Shape shape = PathROIToolsAwt.getShape(roi);
        
        // Get the regions we need
        List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(
			server, shape, requestedDownsample, roi.getZ(), roi.getT(), tileWidth, tileHeight, null);
        
        if (requests.isEmpty()) {
        	logger.debug("Request empty for {}", pathObject);
        	return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
        }
        
        requests = requests.stream().map(r -> RegionRequest.createInstance(r.getPath(), requestedDownsample, r)).collect(Collectors.toList());


        // Try to get all cached tiles - if this fails, return quickly (can't calculate measurement)
        Map<RegionRequest, BufferedImage> localCache = new HashMap<>();
        for (RegionRequest request : requests) {
        	BufferedImage tile = cache.getOrDefault(request, null);
        	if (tile == null)
        		return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
        	localCache.put(request, tile);
        }
        
        // Calculate stained proportions
        counts = new long[channels.size()];
        total = 0L;
        BufferedImage imgMask = null;
        for (Map.Entry<RegionRequest, BufferedImage> entry : localCache.entrySet()) {
        	RegionRequest region = entry.getKey();
        	BufferedImage tile = entry.getValue();
        	// Create a binary mask corresponding to the current tile        	
        	if (imgMask == null || imgMask.getWidth() != tile.getWidth() || imgMask.getHeight() != tile.getHeight()) {
        		imgMask = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        	}
        	Graphics2D g2d = imgMask.createGraphics();
        	g2d.setColor(Color.BLACK);
        	g2d.fillRect(0, 0, tile.getWidth(), tile.getHeight());
        	g2d.setColor(Color.WHITE);
        	g2d.scale(1.0/region.getDownsample(), 1.0/region.getDownsample());
        	g2d.translate(-region.getX(), -region.getY());
        	g2d.fill(shape);
        	g2d.dispose();
        	
//        	if ("Mine".equals(pathObject.getName())) {
//            	new ImagePlus(region.toString(), tile).duplicate().show();
//            	new ImagePlus(region.toString()+"-mask", imgMask).duplicate().show();        		
//        	}
        	
        	switch (type) {
			case Classification:
			case Probability:
				DataBuffer buffer = tile.getRaster().getDataBuffer();
				SampleModel sampleModel = tile.getSampleModel();
				DataBuffer bufferMask = imgMask.getRaster().getDataBuffer();
				SampleModel sampleModelMask = imgMask.getSampleModel();
				int b = 0;
				try {
					for (int y = 0; y < tile.getHeight(); y++) {
						for (int x = 0; x < tile.getWidth(); x++) {
							if (sampleModelMask.getSample(x, y, b, bufferMask) == 0)
								continue;
							int ind = sampleModel.getSample(x, y, b, buffer);
							// TODO: This could be out of range!
							counts[ind]++;
							total++;
						}					
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			case Features:
				return false;
			case Logit:
				break;
			default:
				return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
        	}
        }
    	return updateMeasurements(pathObject, channels, counts, total, pixelArea, pixelAreaUnits);
    }

    
    private synchronized void resetMeasurements(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects) {
    	boolean changes = false;
    	for (PathObject pathObject : pathObjects) {
    		if (updateMeasurements(pathObject, classifier.getMetadata().getChannels(), null, 0L, Double.NaN, null))
    			changes = true;
    	}
    	if (hierarchy != null && changes) {
        	hierarchy.fireObjectMeasurementsChangedEvent(this, pathObjects);    		
    	}
    }

    
    
    private synchronized boolean updateMeasurements(PathObject pathObject, List<PixelClassifierOutputChannel> channels, long[] counts, long total, double pixelArea, String pixelAreaUnits) {
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
    		pathObject.getMeasurementList().closeList();
    	if (counts == null) {
        	measuredObjects.remove(pathObject);
    		return changes;
    	}
    	measuredObjects.put(pathObject, pathObject.getROI());
    	return changes;
    }
    
    

    @Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
    	cacheRGB.clear();
        // For now, bind the display to the display of detections
        if (!viewer.getOverlayOptions().getShowPixelClassification())
            return;

        if (imageData == null)
            return;
//        ImageServer<BufferedImage> server = imageData.getServer();
        var server = classifierServer;

        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();

//        boolean requestingTiles = downsampleFactor <= requestedDownsample * 4.0;
        boolean requestingTiles = true;

        Collection<PathObject> objectsForOverlap = null;
        if (requestingTiles && imageData.getHierarchy().getTMAGrid() != null) {
            objectsForOverlap = imageData.getHierarchy().getObjectsForRegion(TMACoreObject.class, imageRegion, null);
        }

        // Request tiles, of the size that the classifier wants to receive
        int tileWidth = classifier.getMetadata().getInputWidth();// - classifier.requestedPadding() * 2;
        int tileHeight = classifier.getMetadata().getInputHeight();// - classifier.requestedPadding() * 2;
        if (tileWidth <= 0)
        	tileWidth = 256;
        if (tileHeight <= 0)
        	tileHeight = 256;
        List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(
			classifierServer, g2d.getClip(), requestedDownsample, imageRegion.getZ(), imageRegion.getT(), -1, -1, null);

//        requests = requests.stream().map(r -> RegionRequest.createInstance(r.getPath(), requestedDownsample, r)).collect(Collectors.toList());
        
        var annotations = imageData.getHierarchy().getObjects(null, PathAnnotationObject.class);
        
        
//        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Loop through & paint classified tiles if we have them, or request tiles if we don't
        for (RegionRequest request : requests) {
        	
        	if (annotations == null) {
        		boolean doPaint = false;
        		for (var annotation : annotations) {
        			var roi = annotation.getROI();
        			if (roi.getZ() == request.getZ() &&
        					roi.getT() == request.getT() &&
        					request.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight())) {
        				doPaint = true;
        				break;
        			}
        		}
        		if (!doPaint)
        			continue;
        	}
        	
        	// Get the cached raw classified image
            BufferedImage img = cache.get(request);
            if (img != null) {
            	// Get the cached RGB painted version (since painting can be a fairly expensive operation)
                BufferedImage imgRGB = cacheRGB.get(img);
                // If we don't have an RGB version, create one
                if (imgRGB == null) {
                    if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
                        imgRGB = img;
                    } else {
                        imgRGB = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = imgRGB.createGraphics();
                        g.drawImage(img, 0, 0, null);
                        g.dispose();
                    }
//                    if (imgRGB.getRGB(0, 0) == ColorTools.makeRGB(0, 0, 0)) {
//                    	System.err.println("WRONG! " + img.getColorModel());
//                    } else {
//                    	System.err.println("RIGHT! " + img.getColorModel());
//                    }
                    cacheRGB.put(img, imgRGB);
                }
                g2d.drawImage(imgRGB, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
                continue;
            }

            // Don't want parallel requests, or requests when we've zoomed out (although maybe the latter is ok...?)
            if (!requestingTiles) {
                continue;
            }

            if (objectsForOverlap != null) {
                if (!objectsForOverlap.stream().anyMatch(pathObject -> {
                    ROI roi = pathObject.getROI();
                    return request.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
                    }
                ))
                   continue;
            }

            // Request a tile
            requestTile(request);
        }
    }
    
    
    public void stop() {
    	if (imageData != null) {
    		resetMeasurements(imageData.getHierarchy(), imageData.getHierarchy().getObjects(null, PathAnnotationObject.class));
    	}
    	imageDataChanged(viewer, imageData, null);
    	this.pendingRequests.clear();
    	List<Runnable> pending = this.pool.shutdownNow();
    	logger.debug("Stopped classification overlay, dropped {} requests", pending.size());
    }
    
    
    public PixelClassificationImageServer getPixelClassificationServer() {
    	return classifierServer;
    }
    

    // TODO: Revise this - don't require BufferedImage input!
    void requestTile(RegionRequest request) {
        // Make the request, if it isn't already pending
        if (!cache.containsKey(request) && pendingRequests.add(request)) {
            pool.submit(() -> {
            	if (pool.isShutdown())
            		return;
            	// Check we still need to make the request
            	if (cache.containsKey(request) || !pendingRequests.contains(request)) {
//            		System.err.println("Ditched request!");
            		return;
            	}
                try {
//                    // We might need to rescale or add padding, so request the tile from the region store
//                    int padding = classifier.requestedPadding();
//                    ImageServer<BufferedImage> server = viewer.getServer();
//                    double downsample = request.getDownsample();
//					BufferedImage img2 = server.readBufferedImage(RegionRequest.createInstance(
//						request.getPath(), request.getDownsample(), 
//						(int)(request.getX()-padding*downsample), (int)(request.getY()-padding*downsample),
//						(int)Math.round(request.getWidth()+padding*downsample*2),
//						(int)Math.round(request.getHeight()+padding*downsample*2)));
//
//                    BufferedImage imgResult = classifier.applyClassification(img2, padding);
                    
                	BufferedImage imgResult = classifierServer.readBufferedImage(request);
//                    BufferedImage imgResult = classifier.applyClassification(viewer.getServer(), request);
                    cache.put(request, imgResult);
                    viewer.repaint();
                    Platform.runLater(() -> updateAnnotationMeasurements());
                    pendingRequests.remove(request);
                } catch (Exception e) {
                   logger.error("Error requesting tile classification", e);
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
			if (classifierServer != null)
				try {
					classifierServer.close();
				} catch (Exception e) {
					logger.warn("Exception when closing classification server", e);
				}
		}
		this.imageData = imageDataNew;
		if (imageDataNew != null) {
			imageDataNew.getHierarchy().addPathObjectListener(this);
			
			String cacheDirectory = null;
			var project = QuPathGUI.getInstance().getProject();
			if (project != null) {
				Path tempDir;
				try {
					var baseDir = Paths.get(project.getBaseDirectory().getAbsolutePath(), "pixel_classification", imageDataNew.getServer().getShortServerName());
					Files.createDirectories(baseDir);
					tempDir = Files.createTempDirectory(
							baseDir, "classification");
					cacheDirectory = tempDir.toString();
					logger.info("Created cache directory: {}", cacheDirectory);
					// TODO: WRITE CLASSIFIER INTO DIRECTORY!
				} catch (IOException e) {
					logger.error("Unable to create temp directory", e);
					cacheDirectory = null;
				}
			}
			classifierServer = new PixelClassificationImageServer(cacheDirectory, cache, imageDataNew.getServer(), classifier);
		}
	}


	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}


	@Override
	public void viewerClosed(QuPathViewer viewer) {}


}