package qupath.lib.classifiers

import qupath.lib.common.SimpleThreadFactory
import qupath.lib.gui.viewer.QuPathViewer
import qupath.lib.gui.viewer.overlays.AbstractOverlay
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServer
import qupath.lib.images.stores.DefaultImageRegionStore
import qupath.lib.images.stores.ImageRegionStoreHelpers
import qupath.lib.objects.PathObject
import qupath.lib.objects.TMACoreObject
import qupath.lib.regions.ImageRegion
import qupath.lib.regions.RegionRequest

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

public class PixelClassificationOverlay extends AbstractOverlay {

    private QuPathViewer viewer;

    private PixelClassifier classifier;
    private Map<RegionRequest, BufferedImage> cache = new HashMap<>();
    private Map<BufferedImage, BufferedImage> cacheRGB = new HashMap<>();
    private Set<RegionRequest> pendingRequests = Collections.synchronizedSet(new HashSet<>());

    private ExecutorService pool = Executors.newFixedThreadPool(8, new SimpleThreadFactory("classifier-overlay", true))

    PixelClassificationOverlay(final QuPathViewer viewer, final PixelClassifier classifier) {
        super();
        this.classifier = classifier;
        this.viewer = viewer;
    }

    @Override
    void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {

        // For now, bind the display to the display of detections
        if (!viewer.getOverlayOptions().getShowObjects())
            return;

        ImageData<BufferedImage> imageData = viewer.getImageData();
        if (imageData == null)
            return;
        ImageServer<BufferedImage> server = imageData.getServer();

        double requestedDownsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();

        boolean requestingTiles = downsampleFactor <= requestedDownsample * 4.0

        Collection<PathObject> objectsForOverlap = null;
        if (requestingTiles && imageData.getHierarchy().getTMAGrid() != null) {
            objectsForOverlap = imageData.getHierarchy().getObjectsForRegion(TMACoreObject.class, imageRegion, null)
        }

        // Check which tile requests are required currently
        // Note: we use the downsample for the *classifier*
        List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(server, g2d.getClip(), requestedDownsample, imageRegion.getZ(), imageRegion.getT(), null);

        // Loop through & paint classified tiles if we have them, or request tiles if we don't
        DefaultImageRegionStore store = viewer.getImageRegionStore();
        for (RegionRequest request : requests) {

            BufferedImage img = cache.get(request);
            if (img != null) {

                BufferedImage imgRGB = cacheRGB.get(img);
                if (imgRGB == null) {
                    if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
                        imgRGB = img;
                    } else {
                        imgRGB = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB)
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
                if (!objectsForOverlap.stream().anyMatch({PathObject pathObject ->
                    def roi = pathObject.getROI()
                    return request.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight())}
                ))
                    continue;
            }

            // If there is a tile available, request classification
            BufferedImage imgTile = store.getCachedTile(server, request);
            if (imgTile != null) {
                requestTile(request, imgTile);
            }
        }
    }

    private boolean processingRequest() {
        return !pendingRequests.isEmpty();
    }

    void requestTile(RegionRequest request, BufferedImage img) {
        // Make the request, if it isn't already pending
        if (pendingRequests.add(request)) {
            pool.submit { ->
                try {
                    // We might need to rescale or add padding, so request the tile from the region store
                    int padding = classifier.requestedPadding()
                    def server = viewer.getServer()
                    // TODO: HANDLE NON-RGB IMAGES
                    double downsample = request.getDownsample()
                    int width = ((request.getWidth()) / downsample + padding*2) as int
                    int height = ((request.getHeight()) / downsample + padding*2) as int
                    def imgTile2 = new BufferedImage(width, height, img.getType())
                    def g2d2 = imgTile2.createGraphics()
                    g2d2.setClip(0, 0, imgTile2.getWidth(), imgTile2.getHeight())
                    g2d2.scale(1.0 / downsample, 1.0 / downsample)
                    g2d2.translate(-request.getX() + padding * downsample, -request.getY() + padding * downsample)
                    g2d2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    viewer.getImageRegionStore().paintRegionCompletely(server, g2d2, g2d2.getClip(), request.getZ(), request.getT(), request.getDownsample(), null, null, 10L)
                    g2d2.dispose()
                    img = imgTile2

                    BufferedImage imgResult = classifier.applyClassification(img, padding);
                    cache.put(request, imgResult);
                    pendingRequests.remove(request);
                    viewer.repaint();
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }
        }
    }


}