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


package qupath.lib.gui.tma.cells;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.tma.entries.TMAEntry;
import qupath.lib.gui.tma.entries.TMAImageCache;

/**
 * A ListCell for containing the image of a TMAEntry.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageListCell extends ListCell<TMAEntry> {

	final private TMAImageCache imageCache;
	
	final private Canvas canvas = new Canvas();
	final private ObservableValue<Boolean> showOverlay;

	public ImageListCell(final ObservableValue<Boolean> showOverlay, final TMAImageCache imageCache) {
		super();
		this.showOverlay = showOverlay;
		this.imageCache = imageCache;
		canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
		canvas.setWidth(250);
		canvas.setHeight(250);
		canvas.heightProperty().bind(canvas.widthProperty());
	}

	@Override
	protected void updateItem(TMAEntry entry, boolean empty) {
		super.updateItem(entry, empty);
		if (entry == null || empty) {
			setGraphic(null);
			setTooltip(null);
			return;
		}

		//			double w = getTableColumn().getWidth()-10;
		double w = getListView().getWidth() - 40;
		if (w <= 0) {
			setGraphic(null);
			return;
		}

		Image img;
		if (showOverlay.getValue()) {
			if (!entry.hasOverlay()) {
				setGraphic(null);
				return;
			}
			img = imageCache.getOverlay(entry, w);
		}
		else {
			if (!entry.hasImage()) {
				setGraphic(null);
				return;
			}
			img = imageCache.getImage(entry, w);
		}

		canvas.setWidth(w);
		setGraphic(canvas);

		this.setContentDisplay(ContentDisplay.CENTER);
		this.setAlignment(Pos.CENTER);
		
		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, w, w);
		if (img == null)
			return;
		if (img != null) {
			PaintingToolsFX.paintImage(canvas, img);
		}
	}

}