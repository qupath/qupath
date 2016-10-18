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

import org.controlsfx.control.PopOver;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.tma.entries.TMAEntry;


/**
 * A TableCell for containing the image of a TMAEntry.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageTableCell extends TreeTableCell<TMAEntry, Image> {
		
		final private Canvas canvas = new Canvas();
		
		private PopOver popOver = null;
		private ContextMenu menu = new ContextMenu();
		private MenuItem miCopy = new MenuItem("Copy");
		
		public ImageTableCell() {
			super();
			canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			canvas.setWidth(100);
			canvas.setHeight(100);
			canvas.heightProperty().bind(canvas.widthProperty());
			addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
				if (popOver != null && e.getClickCount() == 2)
					popOver.show(this);
			});
			menu.getItems().add(miCopy);
		}
		
		@Override
		protected void updateItem(Image item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setGraphic(null);
				popOver = null;
				return;
			}
			
			double w = getTableColumn().getWidth()-10;
			canvas.setWidth(w);
			setGraphic(canvas);
			
			// Create PopOver to show larger image
			ImageView imageView = new ImageView(item);
			imageView.setPreserveRatio(true);
			Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
			// TODO: Consider setting max width/height elsewhere, so user can adjust?
			imageView.setFitWidth(Math.min(primaryScreenBounds.getWidth()*0.5, item.getWidth()));
			imageView.setFitHeight(Math.min(primaryScreenBounds.getHeight()*0.75, item.getHeight()));
			// Enable copying to clipboard
			miCopy.setOnAction(e -> {
				ClipboardContent content = new ClipboardContent();
				content.putImage(item);
				Clipboard.getSystemClipboard().setContent(content);
			});
			imageView.setOnContextMenuRequested(e -> menu.show(this, e.getScreenX(), e.getScreenY()));
			
			popOver = new PopOver(imageView);
			String name = this.getTreeTableRow().getItem() == null ? null : this.getTreeTableRow().getItem().getName();
			if (name != null)
				popOver.setTitle(name);
			
			this.setContentDisplay(ContentDisplay.CENTER);
			this.setAlignment(Pos.CENTER);
			
			GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.clearRect(0, 0, w, w);
			PaintingToolsFX.paintImage(canvas, item);
//			else if (!waitingImages.contains(item)) {
//				waitingImages.add(item);
//				pool.execute(new ImageWorker(item));
//			}
		}
		
	}