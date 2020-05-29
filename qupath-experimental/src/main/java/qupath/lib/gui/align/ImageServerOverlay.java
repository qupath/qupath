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

package qupath.lib.gui.align;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.transform.Affine;
import javafx.scene.transform.TransformChangedEvent;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

/**
 * A {@link PathOverlay} implementation capable of painting one image on top of another, 
 * including an optional affine transformation.
 * 
 * @author Pete Bankhead
 */
public class ImageServerOverlay extends AbstractOverlay {
	
	private static Logger logger = LoggerFactory.getLogger(ImageServerOverlay.class);
	
	private DefaultImageRegionStore store;
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
		super(viewer.getOverlayOptions());
		this.store = viewer.getImageRegionStore();
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
	
	/**
	 * Get the current renderer.
	 * @return
	 */
	public ImageRenderer getRenderer() {
		return renderer;
	}
	
	/**
	 * Set the rendered, which controls conversion of the image to RGB.
	 * @param renderer
	 */
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
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {

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
		if (PathPrefs.viewerInterpolateBilinearProperty().get())
			gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		else
			gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		store.paintRegion(server, gCopy, gCopy.getClip(), imageRegion.getZ(), imageRegion.getT(), downsampleFactor, imgThumbnail, null, renderer);
		gCopy.dispose();
				
	}
	
}