package qupath.lib.gui.viewer.overlays;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

public class PixelLayerOverlay extends AbstractOverlay {
	
	private QuPathViewer viewer;
	
	public PixelLayerOverlay(QuPathViewer viewer) {
		this.viewer = viewer;
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer,
			boolean paintCompletely) {
		
		var imageData = viewer.getImageData();
		var options = viewer.getOverlayOptions();
		var pixelLayer = imageData == null ? null : PixelClassificationImageServer.getPixelLayer(imageData);
		
		if (imageData == null || !options.getShowPixelClassification() || !(pixelLayer instanceof ImageServer<?>))
			return;
		
		var server = (ImageServer<BufferedImage>)pixelLayer;
		
		DefaultImageRegionStore store = viewer.getImageRegionStore();
//		BufferedImage imgThumbnail = store.getThumbnail(server, imageRegion.getZ(), imageRegion.getT(), true);
			
		store.paintRegion(server, g2d, g2d.getClip(), imageRegion.getZ(), imageRegion.getT(), downsampleFactor, null, observer, null);
	}

}
