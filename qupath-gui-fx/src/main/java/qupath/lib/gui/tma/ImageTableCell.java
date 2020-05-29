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


package qupath.lib.gui.tma;

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
import javafx.stage.Screen;
import qupath.lib.gui.tma.TMAEntries.TMAEntry;
import qupath.lib.gui.tools.GuiTools;


/**
 * A TableCell for containing the image of a TMAEntry.
 * 
 * @author Pete Bankhead
 *
 */
class ImageTableCell extends TreeTableCell<TMAEntry, TMAEntry> {
		
		final private Canvas canvas = new Canvas();
		
		private TMAImageCache cache;
		private boolean isOverlay;
		private PopOver popOver = null;
		private ContextMenu menu = new ContextMenu();
		private MenuItem miCopy = new MenuItem("Copy");
		
		private Image img; // Keep a reference to the last image, so the cache doesn't throw it away
		
		public ImageTableCell(final TMAImageCache cache, final boolean isOverlay) {
			super();
			this.cache = cache;
			this.isOverlay = isOverlay;
			canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			canvas.setWidth(100);
			canvas.setHeight(100);
			canvas.heightProperty().bind(canvas.widthProperty());
			menu.getItems().add(miCopy);
		}
		
		@Override
		protected void updateItem(TMAEntry item, boolean empty) {
			super.updateItem(item, empty);
			popOver = null;
			
			if (item == null || empty) {
				setGraphic(null);
				img = null;
				return;
			}

			double w = getTableColumn().getWidth()-10;
			img = isOverlay ? cache.getOverlay(item, w) : cache.getImage(item, w);
			if (img == null) {
				setGraphic(null);
				img = null;
				return;
			}
			
			canvas.setWidth(w);
			setGraphic(canvas);
			
			this.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					if (popOver == null)
						createPopOver(item, isOverlay);
					if (popOver != null)
						popOver.show(this);
				}
			});
			
			this.setContentDisplay(ContentDisplay.CENTER);
			this.setAlignment(Pos.CENTER);
			
			GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.clearRect(0, 0, w, w);
			img = isOverlay ? cache.getOverlay(item, w) : cache.getImage(item, w);
			GuiTools.paintImage(canvas, img);
		}
		
		
		private void createPopOver(final TMAEntry entry, final boolean isOverlay) {
			// Request full resolution image
			Image image = isOverlay ? cache.getOverlay(entry, -1) : cache.getImage(entry, -1);
			if (image == null)
				return;
			// Create PopOver to show larger image
			ImageView imageView = new ImageView(image);
			imageView.setPreserveRatio(true);
			Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
			// TODO: Consider setting max width/height elsewhere, so user can adjust?
			imageView.setFitWidth(Math.min(primaryScreenBounds.getWidth()*0.5, image.getWidth()));
			imageView.setFitHeight(Math.min(primaryScreenBounds.getHeight()*0.75, image.getHeight()));
			// Enable copying to clipboard
			miCopy.setOnAction(e -> {
				ClipboardContent content = new ClipboardContent();
				content.putImage(image);
				Clipboard.getSystemClipboard().setContent(content);
			});
			imageView.setOnContextMenuRequested(e -> menu.show(this, e.getScreenX(), e.getScreenY()));

			popOver = new PopOver(imageView);
			String name = this.getTreeTableRow().getItem() == null ? null : this.getTreeTableRow().getItem().getName();
			if (name != null)
				popOver.setTitle(name);
		}
		
		
	}