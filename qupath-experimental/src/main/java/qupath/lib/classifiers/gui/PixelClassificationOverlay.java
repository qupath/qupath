package qupath.lib.classifiers.gui;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.SimpleThreadFactory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.stores.ImageRegionStoreHelpers;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Graphics2D;
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

public class PixelClassificationOverlay extends AbstractOverlay {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationOverlay.class);

    private QuPathViewer viewer;

    private PixelClassifier classifier;
    private Map<RegionRequest, BufferedImage> cache = new HashMap<>();
    private Map<BufferedImage, BufferedImage> cacheRGB = new HashMap<>();
    private Set<RegionRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());

    private ExecutorService pool = Executors.newFixedThreadPool(8, new SimpleThreadFactory("classifier-overlay", true));

    PixelClassificationOverlay(final QuPathViewer viewer, final PixelClassifier classifier) {
        super();
        this.classifier = classifier;
        this.viewer = viewer;
    }

    @Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {

        // For now, bind the display to the display of detections
        if (!viewer.getOverlayOptions().getShowObjects())
            return;

        ImageData<BufferedImage> imageData = viewer.getImageData();
        if (imageData == null)
            return;
        ImageServer<BufferedImage> server = imageData.getServer();

        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();

        boolean requestingTiles = downsampleFactor <= requestedDownsample * 4.0;

        Collection<PathObject> objectsForOverlap = null;
        if (requestingTiles && imageData.getHierarchy().getTMAGrid() != null) {
            objectsForOverlap = imageData.getHierarchy().getObjectsForRegion(TMACoreObject.class, imageRegion, null);
        }

        // Request tiles, of the size that the classifier wants to receive
        int tileWidth = classifier.getMetadata().getInputWidth() - classifier.requestedPadding() * 2;
        int tileHeight = classifier.getMetadata().getInputHeight() - classifier.requestedPadding() * 2;
        if (tileWidth <= 0)
        	tileWidth = 256;
        if (tileHeight <= 0)
        	tileHeight = 256;
        List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(
			server, g2d.getClip(), requestedDownsample, imageRegion.getZ(), imageRegion.getT(), tileWidth, tileHeight, null);

//        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Loop through & paint classified tiles if we have them, or request tiles if we don't
        for (RegionRequest request : requests) {
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

    // TODO: Revise this - don't require BufferedImage input!
    void requestTile(RegionRequest request) {
        // Make the request, if it isn't already pending
        if (pendingRequests.add(request)) {
            pool.submit(() -> {
                try {
                    // We might need to rescale or add padding, so request the tile from the region store
                    int padding = classifier.requestedPadding();
                    ImageServer<BufferedImage> server = viewer.getServer();
                    double downsample = request.getDownsample();
					BufferedImage img2 = server.readBufferedImage(RegionRequest.createInstance(
						request.getPath(), request.getDownsample(), 
						(int)(request.getX()-padding*downsample), (int)(request.getY()-padding*downsample),
						(int)Math.round(request.getWidth()+padding*downsample*2),
						(int)Math.round(request.getHeight()+padding*downsample*2)));

                    BufferedImage imgResult = classifier.applyClassification(img2, padding);
                    cache.put(request, imgResult);
                    pendingRequests.remove(request);
                    viewer.repaint();
                } catch (Exception e) {
                   logger.error("Error requesting tile classification", e);
                }
            });
        }
    }


}