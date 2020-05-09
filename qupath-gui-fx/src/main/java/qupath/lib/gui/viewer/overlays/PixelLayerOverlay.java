package qupath.lib.gui.viewer.overlays;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

/**
 * An overlay depicting a custom pixel layer for an image.
 * <p>
 * Currently, the pixel layer is stored transitively in the {@link ImageData} properties.
 * This behavior may change in a future release.
 * <p>
 * This has been deprecated in favor of always using the more complex pixel classification overlay that supports live prediction.
 * 
 * @author Pete Bankhead
 */
@Deprecated
class PixelLayerOverlay extends AbstractOverlay {
	
	private DefaultImageRegionStore store;
	
	/**
	 * Constructor.
	 * @param viewer the viewer to which the overlay will be added.
	 */
	public PixelLayerOverlay(QuPathViewer viewer) {
		super(viewer.getOverlayOptions());
		this.store = viewer.getImageRegionStore();
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData,
			boolean paintCompletely) {
		
		var pixelLayer = imageData == null ? null : PixelClassificationImageServer.getPixelLayer(imageData);
		
		if (imageData == null || !getOverlayOptions().getShowPixelClassification() || !(pixelLayer instanceof ImageServer<?>))
			return;
		
		var server = (ImageServer<BufferedImage>)pixelLayer;
		
		store.paintRegion(server, g2d, g2d.getClip(), imageRegion.getZ(), imageRegion.getT(), downsampleFactor, null, null, null);
	}

}
