/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.gui.viewer.recording.ViewTrackerAnalysisCommand.DataMapsLocationString;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.regions.ImageRegion;

final class ViewTrackerSlideOverview {
	// TODO: Make sure we reset the shapeVisible (I think?) when we change T or Z manually from the slider (because it should always correspond to a specific frame)
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerSlideOverview.class);
	
	private QuPathViewer viewer;
	private BufferedImage img;
	private BufferedImageOverlay overlay;
	private WritableImage scaledImg;
	private WritableImage scaledMap;
	
	private Canvas canvas;
	
	private final int preferredWidth = 250; // Preferred component/image width - used for thumbnail scaling
	private int preferredHeight = -1;
	private final Color color = Color.rgb(200, 0, 0, .8);
	
	private Shape shapeVisible = null; // The visible shape (transformed already)
	private AffineTransform transform = new AffineTransform();
	
	private DoubleProperty mouseXLocation = new SimpleDoubleProperty();
	private DoubleProperty mouseYLocation = new SimpleDoubleProperty();
	
	private DataMapsLocationString locationString;
	
	ViewTrackerSlideOverview(QuPathViewer viewer) {
		this.viewer = viewer;
		this.canvas = new Canvas();
		
		img = viewer.getRGBThumbnail();
		if (img == null)
			return;

		preferredHeight = (int)(img.getHeight() * (preferredWidth / (double)img.getWidth()));
		var imgPreview = GuiTools.getScaledRGBInstance(img, preferredWidth, preferredHeight);
		canvas.setWidth(imgPreview.getWidth());
		canvas.setHeight(imgPreview.getHeight());
		paintCanvas();
		
		Tooltip tooltip = new Tooltip();
		tooltip.setShowDelay(Duration.ZERO);
		tooltip.setHideDelay(Duration.ZERO);
		tooltip.textProperty().bind(Bindings.createStringBinding(() -> {
			var canvasDownsample = viewer.getServerWidth()/canvas.getWidth();
			var x = mouseXLocation.get()*canvasDownsample;
			var y = mouseYLocation.get()*canvasDownsample;
			if (x < 0 || y < 0)
				return "";
			
			String legend = "X: " + ViewTracker.df.format(x) + System.lineSeparator() + "Y: " + ViewTracker.df.format(y);
			if (overlay != null && locationString != null)
				legend += System.lineSeparator() + locationString.getLocationString(viewer.getImageData(), x, y, viewer.getZPosition(), viewer.getTPosition());
			return legend;
		}, mouseXLocation, mouseYLocation));
		
		
		Tooltip.install(canvas, tooltip);
		
		canvas.setOnMouseMoved(e -> {
			if (e.getX() < 0 || e.getY() < 0)
				return;
			mouseXLocation.set(e.getX());
			mouseYLocation.set(e.getY());
			tooltip.setX(e.getScreenX() + 10);
			tooltip.setY(e.getScreenY());
		});
		
		canvas.setOnMouseExited(e -> {
			mouseXLocation.set(-1.0);
			mouseYLocation.set(-1.0);
		});

		canvas.setOnContextMenuRequested(e -> {
			final MenuItem tifExportItem = new MenuItem("Export data as TIF");
		    final MenuItem copyItem = new MenuItem("Copy image to clipboard");
		    final ContextMenu contextMenu = new ContextMenu(tifExportItem, copyItem);
		    
		    tifExportItem.setOnAction(event -> {
		    	var path = Dialogs.promptToSaveFile("Save data map", null, "data map", "TIF", ".tif");
		    	if (path == null)
		    		return;
		    	
		    	try {
		    		var imgToExport = overlay.getRegionMap().get(ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition()));
					ImageWriterTools.writeImage(imgToExport, path.toString());
				} catch (IOException ex) {
					logger.error("Could not export data", ex.getLocalizedMessage());
				}
		    });
		    
		    copyItem.setOnAction(event -> {
		    	if (overlay == null)
		    		return;

		    	ClipboardContent content = new ClipboardContent();
		    	var imgToExport = overlay.getRegionMap().get(ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition()));
				content.putImage(SwingFXUtils.toFXImage(imgToExport, null));
				Clipboard.getSystemClipboard().setContent(content);
		    });
		    
		    tifExportItem.setDisable(overlay == null);
		    copyItem.setDisable(overlay == null);
		    
			contextMenu.show(canvas.getParent().getScene().getWindow(), e.getScreenX(), e.getScreenY());
			contextMenu.setAutoHide(true);
		});
	}
	
	Canvas getCanvas() {
		return canvas;
	}
	
	void paintCanvas() {
		paintCanvas(true, true);
	}
	
	/**
	 * Paint the viewer's thumbnail on the canvas, followed by the overlay (if there is one), 
	 * then the viewed region (if there is one) and finally the thumbnail border.
	 * @param resetMainImage whether to recalculate the scaled thumbnail image from server (should be true if Z or T changed)
	 * @param resetScaledMap whether to recalculate the scaled data map (should be true if Z or T or the normalising range changed)
	 */
	void paintCanvas(boolean resetMainImage, boolean resetScaledMap) {
		GraphicsContext g = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		g.clearRect(0, 0, w, h);
		
		if (viewer == null || !viewer.hasServer())
			return;
		
		
		// Draw image
		if (resetMainImage) {
			img = viewer.getRGBThumbnail();
			scaledImg = GuiTools.getScaledRGBInstance(img, preferredWidth, preferredHeight);
		}
		drawImage(g, scaledImg);
		
		// Draw overlay on top of image
		if (overlay != null && resetScaledMap) {
			var tempImg = overlay.getRegionMap().get(ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition()));
			scaledMap = GuiTools.getScaledRGBInstance(tempImg, preferredWidth, preferredHeight);
		}
		drawImage(g, scaledMap);
		
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
		}
		
		// Draw border
		g.setLineWidth(2);
		g.setStroke(Color.rgb(64, 64, 64));
		g.strokeRect(0, 0, w, h);
	}
	
	private static void drawImage(GraphicsContext g, WritableImage imgPreview) {
		if (imgPreview == null)
			return;
		g.drawImage(imgPreview, 0, 0);
	}

	void setOverlay(BufferedImageOverlay overlay) {
		this.overlay = overlay;
		if (overlay == null)
			scaledMap = null;
		paintCanvas();
	}
	
	void setVisibleShape(ViewRecordingFrame frame) {
		double scale = (double)preferredWidth / viewer.getServer().getWidth();
		double theta = frame.getRotation();
		Shape shape = frame.getShape();
		
		transform.setToIdentity();
		transform.setToScale(scale, scale);
		
		// TODO: Remove 'instanceof', which is needed because addFrame() stores a rotated rectangle instead of the non-rotated one!
		if (shape instanceof Rectangle2D && theta != 0) {
			var center = frame.getFrameCentre();
			transform.rotate(-theta, center.getX(), center.getY());
		}

		shapeVisible = transform.createTransformedShape(shape);
	}

	void setLocationStringFunction(DataMapsLocationString locationString) {
		this.locationString = locationString;
	}
}
