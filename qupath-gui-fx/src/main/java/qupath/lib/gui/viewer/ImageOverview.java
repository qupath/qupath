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

package qupath.lib.gui.viewer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;

/**
 * A small preview panel to be associated with a viewer, which shows the currently-visible
 * region &amp; can be clicked on to navigate to other regions.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageOverview implements QuPathViewerListener {

	final static private Logger logger = LoggerFactory.getLogger(ImageOverview.class);

	private QuPathViewer viewer;
	
	private Canvas canvas = new Canvas();
		
	private boolean repaintRequested = false;

	/*
	 * There are two reasons for the following variables...
	 * 	1 - The viewer's thumbnail is not guaranteed to remain constant, even if the image server does -
	 * 		this is because it can be modified by color transforms, brightness/contrast settings
	 * 	2 - Simply drawing the image directly & rescaling on-the-fly produces a very low-quality image -
	 * 		getScaledInstance() provides something smoother
	 * Therefore to assist repainting, we need to have:
	 * 	- a nicely-rescaled thumbnail to draw
	 * 	- the original thumbnail image used to produce the scaled version
	 * The latter means we can compare it with the viewer's thumbnail & we know if it needs to be updated
	 */
	private BufferedImage imgLastThumbnail; // The last thumbnail the viewer gave us
	private WritableImage imgPreview;       // The (probably downsampled) preview version

	private int preferredWidth = 150; // Preferred component/image width - used for thumbnail scaling

	private Shape shapeVisible = null; // The visible shape (transformed already)
	private AffineTransform transform;
	
	private static Color color = Color.rgb(200, 0, 0, .8);
	private static Color colorBorder = Color.rgb(64, 64, 64);

	protected void mouseViewerToLocation(double x, double y) {
		ImageServer<BufferedImage> server = viewer.getServer();
		if (server == null)
			return;
		double cx = x / getWidth() * server.getWidth();
		double cy = y / getHeight() * server.getHeight();
		viewer.setCenterPixelLocation(cx, cy);
	}

	public ImageOverview(final QuPathViewer viewer) {
		this.viewer = viewer;
		setImage(viewer.getRGBThumbnail());
		
		canvas.setOnMouseClicked(e -> {
			// TODO: Check focus situation
//			// Pass focus to viewer if required - use first click for focus, not moving yet
//			if (viewer != null && viewer.isAncestorOf(ImageOverview.this) && !viewer.hasFocus())
//				viewer.requestFocus();
//			else
			mouseViewerToLocation(e.getX(), e.getY());
		});
		
		canvas.setOnMouseDragged(e -> {
			mouseViewerToLocation(e.getX(), e.getY());
			e.consume();
		});
		
		viewer.zPositionProperty().addListener(v -> repaint());
		viewer.tPositionProperty().addListener(v -> repaint());
			
		viewer.addViewerListener(this);
	}

	private void updateTransform() {
		if (imgPreview != null && viewer != null && viewer.getServer() != null) {
			double scale = (double)preferredWidth / viewer.getServer().getWidth();
			if (scale > 0) {
				// Reuse an existing transform if we have one
				if (transform == null)
					transform = AffineTransform.getScaleInstance(scale, scale);
				else
					transform.setToScale(scale, scale);
			}
			else
				transform = null;
		}
	}

	void paintCanvas() {
		
		GraphicsContext g = canvas.getGraphicsContext2D();
		double w = getWidth();
		double h = getHeight();
		g.clearRect(0, 0, w, h);
		
		if (viewer == null || !viewer.hasServer()) {
			return;
		}
		
		// Ensure the image has been set
		setImage(viewer.getRGBThumbnail());

		g.drawImage(imgPreview, 0, 0);
		
		
		// Draw the currently-visible region, if we have a viewer and it isn't 'zoom to fit' (in which case everything is visible)
		if (!viewer.getZoomToFit() && shapeVisible != null) {
			g.setStroke(color);
			g.setLineWidth(1);
			
			// TODO: Try to avoid PathIterator, and do something more JavaFX-like
			PathIterator iterator = shapeVisible.getPathIterator(null);
			double[] coords = new double[6];
			g.beginPath();
			while (!iterator.isDone()) {
				int type = iterator.currentSegment(coords);
				if (type == PathIterator.SEG_MOVETO)
					g.moveTo(coords[0], coords[1]);
				else if (type == PathIterator.SEG_LINETO)
					g.lineTo(coords[0], coords[1]);
				else if (type == PathIterator.SEG_CLOSE) {
					g.closePath();
					g.stroke();
				}
				else
					logger.debug("Unknown PathIterator type: {}", type);
				iterator.next();
			}
			
//			g2d.draw(shapeVisible);
		}
		
		// Draw border
		g.setLineWidth(2);
		g.setStroke(colorBorder);
		g.strokeRect(0, 0, w, h);
		
		repaintRequested = false;
	}

	
	public boolean isVisible() {
		return canvas.isVisible();
	}
	
	public void setVisible(final boolean visible) {
		canvas.setVisible(visible);
	}
	
	private double getWidth() {
		return canvas.getWidth();
	}

	private double getHeight() {
		return canvas.getHeight();
	}


	private void setImage(BufferedImage img) {
		if (img == imgLastThumbnail)
			return;
		if (img == null) {
			imgLastThumbnail = null;
		} else {
			int preferredHeight = (int)(img.getHeight() * (double)(preferredWidth / (double)img.getWidth()));
			
			imgPreview = getScaledRGBInstance(img, preferredWidth, preferredHeight);

			canvas.setWidth(imgPreview.getWidth());
			canvas.setHeight(imgPreview.getHeight());

//			if (imgPreview == null || imgPreview.getWidth() != preferredWidth || imgPreview.getHeight() != preferredHeight) {
//				//				imgPreview = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(preferredWidth, preferredHeight, Transparency.OPAQUE);
//				imgPreview = new BufferedImage(preferredWidth, preferredHeight, BufferedImage.TYPE_INT_RGB);
//			}
//			Graphics2D g2d = imgPreview.createGraphics();
//			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
//			g2d.drawImage(img, 0, 0, preferredWidth, preferredHeight, this);
//			g2d.dispose();
//			//			imgPreview = img.getScaledInstance(preferredWidth, preferredHeight, BufferedImage.SCALE_SMOOTH);
			imgLastThumbnail = img;
//			System.out.println("I resized from " + img.getWidth() + " to " + imgPreview.getWidth());
		}
		updateTransform();
	}


	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImage(viewer.getRGBThumbnail());
		repaint();
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
		// Get the shape & apply a transform, if we have one
		if (shape != null) {
			if (transform == null)
				updateTransform();
			if (transform != null)
				shapeVisible = transform.createTransformedShape(shape);
			else
				shapeVisible = shape;
		} else
			shapeVisible = null;
		// Repaint
		repaint();
	}


	
	void repaint() {
		if (Platform.isFxApplicationThread()) {
			repaintRequested = true;
			paintCanvas();
			return;
		}
		if (repaintRequested)
			return;
		logger.trace("Overview repaint requested!");
		repaintRequested = true;
		Platform.runLater(() -> repaint());
	}
	

	public Node getNode() {
		return canvas;
	}
	


	/**
	 * Get a scaled (RGB or ARGB) image, achieving reasonable quality even when scaling down by a considerably amount.
	 * 
	 * Code is based on https://today.java.net/article/2007/03/30/perils-imagegetscaledinstance
	 * 
	 * @param img
	 * @param targetWidth
	 * @param targetHeight
	 * @return
	 */
	static WritableImage getScaledRGBInstance(BufferedImage img, int targetWidth, int targetHeight) {
		int type = (img.getTransparency() == Transparency.OPAQUE) ?
				BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		
		BufferedImage imgResult = (BufferedImage)img;
		int w = img.getWidth();
		int h = img.getHeight();

		while (w > targetWidth || h > targetHeight) {
			
			w = Math.max(w / 2, targetWidth);
			h = Math.max(h / 2, targetHeight);

			BufferedImage imgTemp = new BufferedImage(w, h, type);
			Graphics2D g2 = imgTemp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(imgResult, 0, 0, w, h, null);
			g2.dispose();

			imgResult = imgTemp;			
		}
		return SwingFXUtils.toFXImage(imgResult, null);
	}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		this.viewer = null;
	}

}