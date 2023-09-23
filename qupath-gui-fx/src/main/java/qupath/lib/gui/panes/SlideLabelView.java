/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;

/**
 * A simple viewer for a slide label, tied to the current viewer.
 * 
 * @author Pete Bankhead
 *
 */
public class SlideLabelView implements ChangeListener<ImageData<BufferedImage>> {
	
	private static Logger logger = LoggerFactory.getLogger(SlideLabelView.class);
	
	private QuPathGUI qupath;

	private SimpleImageViewer simpleImageViewer;
	private Stage stage;

	private BooleanProperty showing = PathPrefs.createPersistentPreference("showSlideLabel", false);

	private StringProperty placeholderText = new SimpleStringProperty();
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public SlideLabelView(final QuPathGUI qupath) {
		this.qupath = qupath;
		qupath.imageDataProperty().addListener(this);
	}
	
	private void createDialog() {
		simpleImageViewer = new SimpleImageViewer();

		simpleImageViewer.placeholderTextProperty().bind(placeholderText);

		stage = simpleImageViewer.getStage();
		stage.initOwner(qupath.getStage());

		if (simpleImageViewer.getImage() == null) {
			stage.setWidth(240);
			stage.setHeight(240);
		}

		showing.addListener((v, o, n) -> {
			if (n) {
				if (!stage.isShowing()) {
					GuiTools.showWithScreenSizeConstraints(stage, 0.8);
				}
			} else {
				if (stage.isShowing())
					stage.hide();
			}
		});
		
		stage.showingProperty().addListener((v, o, n) -> {
			if (n)
				updateLabel(qupath.getImageData());
			showing.set(n);
		});
		
		
		if (showing.get()) {
			Platform.runLater(() -> {
				updateLabel(qupath.getImageData());
				GuiTools.showWithScreenSizeConstraints(stage, 0.8);
			});
		}
	}
	
	/**
	 * Property indicating whether the label is showing on screen or not.
	 * @return
	 */
	public BooleanProperty showingProperty() {
		if (stage == null)
			createDialog();
		return showing;
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		updateLabel(imageDataNew);
	}
	
	private void updateLabel(final ImageData<BufferedImage> imageData) {
		if (stage == null || !stage.isShowing())
			return;
		
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			placeholderText.set("No image open in the current viewer");
			simpleImageViewer.resetImage();
		} else {
			String labelName = searchForLabelName(server);
			if (labelName == null) {
				placeholderText.set("No label found");
				simpleImageViewer.resetImage();
			} else
				simpleImageViewer.updateImage(labelName, server.getAssociatedImage(labelName));
		}
	}

	/**
	 * Try to get the name of an associated label image.
	 * @param server
	 * @return
	 */
	private static String searchForLabelName(ImageServer<?> server) {
		// Try to get a label from the associated images
		// We start by looking for associated names equal to or containing label, and eventually broaden out to allow other associated images
		// as better than displaying nothing at all (since they might actually be label images).
		// Adapted because of https://github.com/qupath/qupath/issues/643
		var associatedNames = server.getAssociatedImageList();
		if (associatedNames.isEmpty())
			return null;
		String labelName = associatedNames.stream().filter(n -> n.toLowerCase().trim().equals("label")).findFirst().orElse(null);
		if (labelName == null)
			labelName = associatedNames.stream().filter(n -> n.toLowerCase().trim().contains("(label)")).findFirst().orElse(null);
		if (labelName == null)
			labelName = associatedNames.stream().filter(n -> n.toLowerCase().trim().contains("label")).findFirst().orElse(null);
		if (labelName == null)
			labelName = associatedNames.stream().filter(n -> n.toLowerCase().trim().contains("macro")).findFirst().orElse(null);
		if (labelName == null)
			labelName = associatedNames.get(0);
		return labelName;
	}

}
