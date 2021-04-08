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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

/**
 * A simple viewer for a slide label, tied to the current viewer.
 * 
 * @author Pete Bankhead
 *
 */
public class SlideLabelView implements ChangeListener<ImageData<BufferedImage>> {
	
	private static Logger logger = LoggerFactory.getLogger(SlideLabelView.class);
	
	private QuPathGUI qupath;
	private Stage dialog;
	private BooleanProperty showing = PathPrefs.createPersistentPreference("showSlideLabel", false);
	private BorderPane pane = new BorderPane();
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public SlideLabelView(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.imageDataProperty().addListener(this);
	}
	
	private void createDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Label");
		dialog.setScene(new Scene(pane, 400, 400));
		
		showing.addListener((v, o, n) -> {
			if (n) {
				if (!dialog.isShowing())
					dialog.show();
			} else {
				if (dialog.isShowing())
					dialog.hide();
			}
		});
		
		
		dialog.showingProperty().addListener((v, o, n) -> {
			if (n)
				updateLabel(qupath.getImageData());
			showing.set(n);
		});
		
		
		if (showing.get()) {
			Platform.runLater(() -> dialog.show());
		}
	}
	
	/**
	 * Property indicating whether the label is showing on screen or not.
	 * @return
	 */
	public BooleanProperty showingProperty() {
		if (dialog == null)
			createDialog();
		return showing;
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		updateLabel(imageDataNew);
	}
	
	private void updateLabel(final ImageData<BufferedImage> imageData) {
		if (dialog == null || !dialog.isShowing())
			return;
		
		// Try to get a label image
		Image imgLabel = null;
		String message = "No label available";
		if (imageData != null) {
			ImageServer<BufferedImage> server = imageData.getServer();
			if (server != null) {
				var associatedNames = server.getAssociatedImageList();
				if (!associatedNames.isEmpty()) {
					try {
						// Try to get a label from the associated images
						// We start by looking for associated names equal to or containing label, and eventually broaden out to allow other associated images 
						// as better than displaying nothing at all (since they might actually be label images).
						// Adapted because of https://github.com/qupath/qupath/issues/643
						String labelName = server.getAssociatedImageList().stream().filter(n -> n.toLowerCase().trim().equals("label")).findFirst().orElse(null);
						if (labelName == null)
							labelName = server.getAssociatedImageList().stream().filter(n -> n.toLowerCase().trim().contains("(label)")).findFirst().orElse(null);
						if (labelName == null)
							labelName = server.getAssociatedImageList().stream().filter(n -> n.toLowerCase().trim().contains("label")).findFirst().orElse(null);
						if (labelName == null)
							labelName = server.getAssociatedImageList().stream().filter(n -> n.toLowerCase().trim().contains("macro")).findFirst().orElse(null);
						if (labelName == null)
							labelName = associatedNames.get(0);
						imgLabel = SwingFXUtils.toFXImage(server.getAssociatedImage(labelName), null);
					} catch (Exception e) {
						logger.error("Unable to read label from {}", server.getPath());
					}
				}
			}
		} else
			message = "No image open in the current viewer";

		// Update the component
		if (imgLabel == null)
			pane.setCenter(new Label(message));
		else {
			
			ContextMenu popup = new ContextMenu();
			MenuItem copyItem = new MenuItem("Copy");
			var content = new ClipboardContent();
			content.putImage(imgLabel);
			copyItem.setOnAction(e -> {
				Clipboard.getSystemClipboard().setContent(content);
			});
			popup.getItems().add(copyItem);
			
			ImageView view = new ImageView(imgLabel);
			view.setPreserveRatio(true);
			view.fitWidthProperty().bind(pane.widthProperty());
			view.fitHeightProperty().bind(pane.heightProperty());
			pane.setCenter(view);
			
			view.setOnMousePressed(e -> {
				if (e.isPopupTrigger())
					popup.show(view, e.getScreenX(), e.getScreenY());
			});
			view.setOnMouseReleased(e -> {
				if (e.isPopupTrigger())
					popup.show(view, e.getScreenX(), e.getScreenY());
			});
			
		}
	}

}
