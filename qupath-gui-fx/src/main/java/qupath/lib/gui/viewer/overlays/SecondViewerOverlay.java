/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.viewer.overlays;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;

/**
 * 
 * Experimental second viewer overlay, i.e. a method to display the contents of one
 * viewer superimposed on top of another.
 * 
 * This is currently of limited, and the code requires cleaning up.
 * 
 * Some notable issues include that it depends upon syncing/desyncing viewers to improve alignment,
 * doesn't take into consideration 'swipe' movements that cause viewers to continue panning after
 * the initial drag gesture has completed, and it only really works when two viewers are open.
 * 
 * TODO: Complete (or discard) the SecondViewerOverlay class
 * 
 * @author Pete Bankhead
 *
 */
public class SecondViewerOverlay extends AbstractOverlay implements QuPathViewerListener {
	
	final private static Logger logger = LoggerFactory.getLogger(SecondViewerOverlay.class);
	
	private QuPathViewer viewerSource;
	private QuPathViewer viewerDest;
	
	private ViewerInternal viewerInternal;
	
	public SecondViewerOverlay(final QuPathViewer viewerSource, final QuPathViewer viewerDest) {
		super();
		logger.warn("Second viewer overlay is still experimental! Created for {}", viewerSource);
		this.viewerSource = viewerSource;
		this.viewerDest = viewerDest;
		this.viewerSource.addViewerListener(this);
		this.viewerDest.addViewerListener(this);
		viewerInternal = new ViewerInternal(viewerSource);
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
		if (viewerInternal == null)
			return;
		
		
		
//		viewerInternal.paint
//		g2d = (Graphics2D)viewerDest.getGraphics();
		g2d = (Graphics2D)g2d.create();
		g2d.setTransform(new AffineTransform());
		viewerInternal.paintViewer(g2d, viewerDest.getWidth(), viewerDest.getHeight());
		g2d.dispose();
		
//		viewerInternal.paintComponents(viewerDest.getGraphics());
//		viewerInternal.paint(g2d);
	}

	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		if (viewer == viewerSource)
			viewerInternal.setImageData(imageDataNew);
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
//		if (viewer == viewerSource) {
			viewerInternal.setCenterPixelLocation(viewerSource.getCenterPixelX(), viewerSource.getCenterPixelY());
			viewerInternal.setDownsampleFactor(viewerDest.getDownsampleFactor());
			viewerInternal.setRotation(viewerSource.getRotation());
			viewerDest.repaint(); // Maybe don't need to repaint...?
//		}
	}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		this.viewerInternal = null;
		this.viewerSource.removeViewerListener(this);
		this.viewerDest.removeViewerListener(this);
		this.viewerSource = null;
		this.viewerDest = null;
	}
	
	
	class ViewerInternal extends QuPathViewer {
		
		ViewerInternal(final QuPathViewer viewer) {
			super(viewer.getImageData(), viewer.getImageRegionStore(), new OverlayOptions(), viewer.getImageDisplay());
			OverlayOptions overlayOptions = getOverlayOptions();
			overlayOptions.setShowObjects(false);
			overlayOptions.setShowAnnotations(false);
			overlayOptions.setShowTMAGrid(false);
			setCenterPixelLocation(viewer.getCenterPixelX(), viewer.getCenterPixelY());
			setDownsampleFactor(viewer.getDownsampleFactor());
			setRotation(viewer.getRotation());
		}
		
		@Override
		public void paintViewer(Graphics g, int w, int h) {
			super.paintViewer(g, w, h);
		}
		
		@Override
		public int getWidth() {
			return viewerDest == null ? 0 : viewerDest.getWidth();
		}

		@Override
		public int getHeight() {
			return viewerDest == null ? 0 : viewerDest.getHeight();			
		}
		
		@Override
		protected BufferedImage createBufferedImage(final int w, final int h) {
			return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		}

	}
	

}
