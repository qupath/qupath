package qupath.lib.gui.align;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.transform.Affine;
import javafx.scene.transform.TransformChangedEvent;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractImageDataOverlay;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.stores.DefaultImageRegionStore;
import qupath.lib.regions.ImageRegion;

/**
 * A PathOverlay implementation capable of painting one image on top of another, 
 * including an optional affine transformation.
 * 
 * @author Pete Bankhead
 */
public class ImageServerOverlay extends AbstractImageDataOverlay {
	
	private static Logger logger = LoggerFactory.getLogger(ImageServerOverlay.class);
	
	private QuPathViewer viewer;
	private ImageServer<BufferedImage> server;
	
	private Affine affine = new Affine();
	
	private AffineTransform transform;
	private AffineTransform transformInverse;
	
	public ImageServerOverlay(final QuPathViewer viewer, final ImageServer<BufferedImage> server) {
		this(viewer, server, new Affine());
	}
	
	public ImageServerOverlay(final QuPathViewer viewer, final ImageServer<BufferedImage> server, final Affine affine) {
		super(viewer.getOverlayOptions(), viewer.getImageData());
		this.viewer = viewer;
		this.server = server;
		this.transform = new AffineTransform();
		this.transformInverse = null;//transform.createInverse();
		// Request repaint any time the transform changes
		this.affine = affine;
		this.affine.addEventHandler(TransformChangedEvent.ANY, e ->  {
			updateTransform();
			viewer.repaintEntireImage();
		});
		updateTransform();
	}
	
	
	public Affine getAffine() {
		return affine;
	}
	
	private void updateTransform() {
		transform.setTransform(
			affine.getMxx(),
			affine.getMyx(),
			affine.getMxy(),
			affine.getMyy(),
			affine.getTx(),
			affine.getTy()
			);
		try {
			transformInverse = transform.createInverse();
		} catch (NoninvertibleTransformException e) {
			logger.warn("Unable to invert transform", e);
		}
	}

	@Override
	public boolean supportsImageDataChange() {
		return false;
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {

		DefaultImageRegionStore store = viewer.getImageRegionStore();
		BufferedImage imgThumbnail = store.getThumbnail(server, imageRegion.getZ(), imageRegion.getT(), true);
			
		// Paint the image
		Graphics2D gCopy = (Graphics2D)g2d.create();
		if (transformInverse != null) {
			AffineTransform transformOld = gCopy.getTransform();
			transformOld.concatenate(transformInverse);
			gCopy.setTransform(transformOld);
		} else {
			logger.debug("Inverse affine transform is null!");
		}
		store.paintRegion(server, gCopy, gCopy.getClip(), imageRegion.getZ(), imageRegion.getT(), downsampleFactor, imgThumbnail, observer, null);
		gCopy.dispose();
				
	}
	
}