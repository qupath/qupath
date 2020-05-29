/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.viewer;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.TextAlignment;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;

/**
 * Scalebar component (or at least wrapper for the displayable component) 
 * to show an adjustable scalebar on top of an image viewer.
 * 
 * @author Pete Bankhead
 *
 */
class Scalebar implements QuPathViewerListener {
	
	private QuPathViewer viewer;
	private double preferredLength;
	// Only permit some sensible-looking, rounding scale lengths
	private double[] permittedScales = new double[]{0.1, 0.25, 0.5, 1, 2, 5, 10, 20, 50, 100, 200, 250, 400, 500, 800, 1000, 2000, 5000, 10000, 20000, 50000, 100000};
	
	protected static DecimalFormat df = new DecimalFormat("#.##");
	private double scaledLength = Double.NaN;
	private double scaledLengthPixels = Double.NaN;
	
	private Label label = new Label();
	
	private Canvas canvas = new Canvas();
	private Line scaleLine = new Line();
	
	private Color color = ColorToolsFX.TRANSLUCENT_BLACK_FX;
	
	// Used to determine if recomputing the scalebar is necessary
	private double lastDownsample = Double.NaN;
	
	public Scalebar(final QuPathViewer viewer) {
		this(viewer, 100);
	}

	public Scalebar(final QuPathViewer viewer, final double preferredLength) {
		this.viewer = viewer;
		this.preferredLength = preferredLength;
		viewer.addViewerListener(this);
		
		label.setTextAlignment(TextAlignment.CENTER);
		var fontBinding = Bindings.createStringBinding(() -> {
				var temp = PathPrefs.viewerFontSizeProperty().get();
				return temp == null ? null : "-fx-font-size: " + temp.getFontSize();
		}, PathPrefs.viewerFontSizeProperty());
		label.styleProperty().bind(fontBinding);
	}
	
	
	public void setVisible(final boolean visible) {
		this.label.setVisible(visible);
//		this.scaleLine.setVisible(visible);
	}
	
	public boolean isVisible() {
		return this.label.isVisible();
//		return this.scaleLine.isVisible();
	}
	
	
	protected double getPermittedScale(double preferredLength) {
		double minDiff = Double.POSITIVE_INFINITY;
		double scaleValue = Double.NaN;
		for (double d : permittedScales) {
			double tempDiff = Math.abs(d - preferredLength);
			if (tempDiff < minDiff) {
				scaleValue = d;
				minDiff = tempDiff;
			}
		}
		return scaleValue;
	}

	public void updateScalebar() {
		if (viewer == null) {
			scaleLine.setVisible(false);
//			setText("");
//			setText(null);
		}
		try {
			scaleLine.setVisible(true);
			// Check the downsample has changed
			double currentDownsample = viewer.getDownsampleFactor();
			if (lastDownsample == currentDownsample)
				return;
			
//			System.out.println("UPDATING SCALE BAR! " + currentDownsample);
			
			ImageServer<?> server = viewer.getServer();
			// The scalebar is shown horizontally - so request the horizontal scale, if known
			double scale = 1.0;
			String unit = "px";
			PixelCalibration cal = server.getPixelCalibration();
			if (cal.hasPixelSizeMicrons()) {
				scale = cal.getPixelWidthMicrons();
				unit = GeneralTools.micrometerSymbol();
			}
			// The size of one pixel... in some kind of units
			double pxSize = scale * currentDownsample;
			// Find the permitted scalebar size closest to the preferred length
			scaledLength = getPermittedScale(pxSize * preferredLength);
			scaledLengthPixels = scaledLength / pxSize;
			// Switch to mm if appropriate
			String labelText = df.format(scaledLength) + unit;
			if (scaledLength >= 1000 && GeneralTools.micrometerSymbol().equals(unit)) {
				labelText = df.format(scaledLength / 1000) + "mm";
			}
			
			
			
//			String label = String.format("%f %s", scaledLength, unit);
			// If it make sense, could convert microns to mm... but let's not do that for now
	//		if (server.pixelSizeMicronsKnown() && scaledLength >= 1000)
	//			label = String.format("%f %s", scaledLength, "mm");
			
			
			double width = scaledLengthPixels;
			double height = 5;
			canvas.setWidth(width+2);
			canvas.setHeight(height);
			GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.clearRect(0, 0, width+2, height);
			gc.save();
			int x = 1;
			gc.setStroke(color);
			gc.setLineWidth(2);
			gc.strokeLine(x, 0, x, height);
			gc.strokeLine(x, height/2, x+width, height/2);
			gc.strokeLine(x+width, 0, x+width, height);
			gc.restore();
			
			scaleLine.setStartX(0.0);
			scaleLine.setStartY(0.0);
			scaleLine.setEndY(0.0);
			scaleLine.setEndX(scaledLength);
			
			
			
			label.setText(labelText);
			lastDownsample = currentDownsample;
		} catch (NullPointerException e) {
			label.setText("");
		}
	}
	
	
	public void setTextColor(final Color color) {
		if (this.color == color)
			return;
		this.color = color;
		label.setTextFill(color);
		lastDownsample = Double.NaN; // Force the update to happen
		updateScalebar();
	}
	
	
	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		lastDownsample = Double.NaN; // Reset the last downsample
		updateScalebar();
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
		updateScalebar();
	}
	
	
	public Node getNode() {
		label.setGraphic(canvas);
		label.setTextAlignment(TextAlignment.CENTER);
		label.setContentDisplay(ContentDisplay.TOP);
		return label;
	}
	



	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		this.viewer = null;
	}
	
}
