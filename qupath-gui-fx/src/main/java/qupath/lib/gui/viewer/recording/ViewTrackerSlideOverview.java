/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.recording;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.regions.ImageRegion;

final class ViewTrackerSlideOverview {
	// TODO: Make sure we reset the shapeVisible (I think?) when we change T or Z manually from the slider (because it should always correspond to a specific frame)
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerSlideOverview.class);
	
	private QuPathViewer viewer;
	private BufferedImage img;
	private BufferedImageOverlay overlay;
	
	private Canvas canvas;
	
	private final int preferredWidth = 250; // Preferred component/image width - used for thumbnail scaling
	private final Color color = Color.rgb(200, 0, 0, .8);
	
	private Shape shapeVisible = null; // The visible shape (transformed already)
	private AffineTransform transform = null;
	
	ViewTrackerSlideOverview(QuPathViewer viewer, Canvas canvas) {
		this.viewer = viewer;
		this.canvas = canvas;
		
		img = viewer.getRGBThumbnail();
		if (img == null)
			return;

		int preferredHeight = (int)(img.getHeight() * (preferredWidth / (double)img.getWidth()));
		var imgPreview = GuiTools.getScaledRGBInstance(img, preferredWidth, preferredHeight);
		canvas.setWidth(imgPreview.getWidth());
		canvas.setHeight(imgPreview.getHeight());
		paintCanvas();
	}
	
	void paintCanvas() {
		GraphicsContext g = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		g.clearRect(0, 0, w, h);
		
		if (viewer == null || !viewer.hasServer())
			return;
		
		// Set img
		img = viewer.getRGBThumbnail();
		
		// Draw image
		drawImage(g, img);
		
		// Draw overlay on top of image
		if (overlay != null)
			drawImage(g, overlay.getRegionMap().get(ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition())));

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
				} else
					logger.debug("Unknown PathIterator type: {}", type);
				iterator.next();
			}
			
//			g2d.draw(shapeVisible);
		}
		
		// Draw border
//		g.setLineWidth(2);
//		g.setStroke(colorBorder);
//		g.strokeRect(0, 0, w, h);
		
	}
	
	private void drawImage(GraphicsContext g, BufferedImage imgToDraw) {
		int preferredHeight = (int)(img.getHeight() * (preferredWidth / (double)img.getWidth()));
		var imgPreview = GuiTools.getScaledRGBInstance(imgToDraw, preferredWidth, preferredHeight);
		g.drawImage(imgPreview, 0, 0);
	}

	void setOverlay(BufferedImageOverlay overlay) {
		this.overlay = overlay;
		paintCanvas();
	}
	
	void setVisibleShape(ViewRecordingFrame frame) {
		double scale = (double)preferredWidth / viewer.getServer().getWidth();
		double theta = frame.getRotation();
		Shape shape = frame.getShape();
		
		if (transform == null)
			transform = new AffineTransform();
		else
			transform.setToIdentity();
		
		transform.setToScale(scale, scale);
		
		if (theta != 0) {
			var center = frame.getFrameCentre();
			transform.rotate(-theta, center.getX(), center.getY());
		}

		shapeVisible = transform.createTransformedShape(shape);
	}
}
