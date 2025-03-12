package qupath.lib.gui.viewer.overlays;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.images.servers.ZProjectedImageServer;
import qupath.lib.regions.ImageRegion;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

public class ZProjectOverlay extends AbstractImageOverlay {

    private final ObjectProperty<ImageRenderer> renderer = new SimpleObjectProperty<>();

    private final ObjectProperty<ZProjectedImageServer.Projection> projection =
            new SimpleObjectProperty<>(ZProjectedImageServer.Projection.MAX);

    private DefaultImageRegionStore store;

    // If set, the overlay will attempt to paint the entire region in one go - rather than limited to cached tiles
    private long paintCompletelyTimeout = 250;

    private final Map<ImageServer<BufferedImage>, ImageServer<BufferedImage>> map = new WeakHashMap<>();

    protected ZProjectOverlay(OverlayOptions options) {
        super(options);
        projection.addListener((v, o, n) -> map.clear());
    }

    private ZProjectOverlay(QuPathViewer viewer) {
        this(viewer.getOverlayOptions());
        this.store = viewer.getImageRegionStore();
        this.renderer.set(viewer.getImageDisplay());
    }

    public static ZProjectOverlay create(QuPathViewer viewer) {
        return new ZProjectOverlay(viewer);
    }

    public ZProjectedImageServer.Projection getProjection() {
        return projection.get();
    }

    public ObjectProperty<ZProjectedImageServer.Projection> projectionProperty() {
        return projection;
    }

    public void setProjection(ZProjectedImageServer.Projection projection) {
        this.projection.set(projection);
    }

    /**
     * Get the {@link ImageRenderer} property used with this overlay.
     * @return
     */
    public ObjectProperty<ImageRenderer> rendererProperty() {
        return renderer;
    }

    /**
     * Set the {@link ImageRenderer} property used with this overlay.
     * @return
     */
    public void setRenderer(ImageRenderer renderer) {
        this.renderer.set(renderer);
    }

    /**
     * Get the {@link ImageRenderer} used with this overlay, which may be null.
     * @return
     */
    public ImageRenderer getRenderer() {
        return renderer.get();
    }

    private DefaultImageRegionStore getStore() {
        if (store == null) {
            store = ImageRegionStoreFactory.createImageRegionStore();
        }
        return store;
    }

    private ImageServer<BufferedImage> getProjection(ImageServer<BufferedImage> server) {
        return map.computeIfAbsent(server, this::createProjection);
    }

    private ImageServer<BufferedImage> createProjection(ImageServer<BufferedImage> server) {
        return new TransformedServerBuilder(server)
                .zProject(projection.get())
                .build();
    }


    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {
        super.paintOverlay(g2d, imageRegion, downsampleFactor, imageData, paintCompletely);
        var proj = projection.get();
        if (getOpacity() == 0 || imageData == null || imageData.getServer().nZSlices() == 1 || proj == null ||
               !getOverlayOptions().showPixelClassificationProperty().get())
            return;

        var zProjServer = getProjection(imageData.getServer());
        int z = 0;
        if (zProjServer.nZSlices() > 1) {
            z = imageRegion.getZ();
        }

        var store = getStore();
        if (paintCompletelyTimeout > 0) {
            store.paintRegionCompletely(
                zProjServer, g2d, g2d.getClip(), z, imageRegion.getT(), downsampleFactor,
                null, renderer.get(), paintCompletelyTimeout);
        } else {
            store.paintRegion(
                zProjServer, g2d, g2d.getClip(), z, imageRegion.getT(), downsampleFactor,
                null, null, renderer.get());
        }
    }

}
