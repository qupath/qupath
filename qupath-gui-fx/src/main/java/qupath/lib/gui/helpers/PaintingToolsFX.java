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

package qupath.lib.gui.helpers;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Static methods to help with common painting tasks with JavaFX.
 * 
 * @author Pete Bankhead
 *
 */
public class PaintingToolsFX {
	
	/**
	 * Paint an image centered within a canvas, scaled to be as large as possible while maintaining its aspect ratio.
	 * 
	 * Background is transparent.
	 * 
	 * @param canvas
	 * @param image
	 */
	public static void paintImage(final Canvas canvas, final Image image) {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		gc.setFill(Color.TRANSPARENT);
		if (image == null) {
			gc.clearRect(0, 0, w, h);
			return;
		}
		double scale = Math.min(
				w/image.getWidth(),
				h/image.getHeight());
		double sw = image.getWidth()*scale;
		double sh = image.getHeight()*scale;
		double sx = (w - sw)/2;
		double sy = (h - sh)/2;
		gc.clearRect(0, 0, w, h);
		gc.drawImage(image, sx, sy, sw, sh);
	}

}
