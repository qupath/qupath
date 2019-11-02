package qupath.lib.gui.align;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.transform.Affine;
import javafx.scene.transform.TransformChangedEvent;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractImageDataOverlay;
import qupath.lib.images.servers.ImageServer;
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
	
	private ImageRenderer renderer;
	
	private Affine affine = new Affine();
	
	private AffineTransform transform;
	private AffineTransform transformInverse;
	
	/**
	 * Constructor.
	 * @param viewer viewer to which the overlay should be added
	 * @param server ImageServer that should be displayed on the overlay
	 */
	public ImageServerOverlay(final QuPathViewer viewer, final ImageServer<BufferedImage> server) {
		this(viewer, server, new Affine());
	}
	
	/**
	 * Constructor.
	 * @param viewer viewer to which the overlay should be added
	 * @param server ImageServer that should be displayed on the overlay
	 * @param affine Affine transform to apply to the overlaid server
	 */
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
	
	public ImageRenderer getRenderer() {
		return renderer;
	}
	
	public void setRenderer(ImageRenderer renderer) {
		this.renderer = renderer;
	}
	
	/**
	 * Get the affine transform applied to the overlay image.
	 * Making changes here will trigger repaints in the viewer.
	 * @return
	 */
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
		BufferedImage imgThumbnail = null;//store.getThumbnail(server, imageRegion.getZ(), imageRegion.getT(), true);
			
		// Paint the image
		Graphics2D gCopy = (Graphics2D)g2d.create();
		if (transformInverse != null) {
			AffineTransform transformOld = gCopy.getTransform();
			transformOld.concatenate(transformInverse);
			gCopy.setTransform(transformOld);
		} else {
			logger.debug("Inverse affine transform is null!");
		}
		var composite = getAlphaComposite();
		if (composite != null)
			gCopy.setComposite(composite);
		if (PathPrefs.getViewerInterpolationBilinear())
			gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		else
			gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		store.paintRegion(server, gCopy, gCopy.getClip(), imageRegion.getZ(), imageRegion.getT(), downsampleFactor, imgThumbnail, observer, renderer);
		gCopy.dispose();
				
	}
	
}