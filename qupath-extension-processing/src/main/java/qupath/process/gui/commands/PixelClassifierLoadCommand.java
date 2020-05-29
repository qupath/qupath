/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.process.gui.commands;

import java.io.IOException;
import java.util.Collection;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.process.gui.ml.PixelClassificationOverlay;
import qupath.process.gui.ml.PixelClassifierUI;

/**
 * Command to apply a pre-trained pixel classifier to an image.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierLoadCommand implements Runnable {
	
	private QuPathGUI qupath;
	
	private String title = "Load Pixel Classifier";
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public PixelClassifierLoadCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		
		var imageData = viewer.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}		
		
		var project = qupath.getProject();
		if (project == null) {
			Dialogs.showErrorMessage(title, "You need a project open to run this command!");
			return;
		}
		
		Collection<String> names;
		try {
			names = project.getPixelClassifiers().getNames();
			if (names.isEmpty()) {
				Dialogs.showErrorMessage(title, "No pixel classifiers were found in the current project!");
				return;
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e);
			return;
		}
			
		var comboClassifiers = new ComboBox<String>();
		comboClassifiers.getItems().setAll(names);
		var selectedClassifier = Bindings.createObjectBinding(() -> {
			String name = comboClassifiers.getSelectionModel().getSelectedItem();
			if (name != null) {
				try {
					return project.getPixelClassifiers().get(name);
				} catch (Exception e) {
					Dialogs.showErrorMessage("Load pixel model", e);
				}
			}
			return null;
		}, comboClassifiers.getSelectionModel().selectedItemProperty());
		
		var selectedOverlay = Bindings.createObjectBinding(() -> {
			if (selectedClassifier.get() == null)
				return null;
			return PixelClassificationOverlay.createPixelClassificationOverlay(qupath.getOverlayOptions(), selectedClassifier.get());
		}, selectedClassifier);
		
		selectedOverlay.addListener((v, o, n) -> {
			if (o != null)
				o.stop();
			if (n == null) {
				viewer.resetCustomPixelLayerOverlay();
			} else {
				n.setLivePrediction(true);
				viewer.setCustomPixelLayerOverlay(n);
			}
		});
		
		var label = new Label("Choose model");
		label.setLabelFor(comboClassifiers);
		
		var classifierName = new SimpleStringProperty(null);
		classifierName.bind(comboClassifiers.getSelectionModel().selectedItemProperty());
		var tilePane = PixelClassifierUI.createPixelClassifierButtons(qupath.imageDataProperty(), selectedClassifier, classifierName);
		
//		var tilePane = PixelClassifierUI.createPixelClassifierButtons(viewer.imageDataProperty(), selectedClassifier);
		var labelRegion = new Label("Region");
		var comboRegionFilter = PixelClassifierUI.createRegionFilterCombo(qupath.getOverlayOptions());

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, "Choose pixel classification model to apply to the current image", label, comboClassifiers);
		PaneTools.addGridRow(pane, row++, 0, "Control where the pixel classification is applied during preview",
				labelRegion, comboRegionFilter, comboRegionFilter);
		PaneTools.addGridRow(pane, row++, 0, "Apply pixel classification", tilePane, tilePane);
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, comboClassifiers, tilePane);
		
//		var labelInfo = new Label(
//				"Load & apply a pixel classifier to an image.\n\n" +
//				"Note that this command is linked to a specific viewer.\n" + 
//				"If you need to classify an image in another viewer, \n" +
//				"select the viewer and run this command again."
//				);
//		labelInfo.setAlignment(Pos.CENTER);
//		labelInfo.setWrapText(true);
//		var stage = Dialogs.builder()
//			.title(title)
//			.content(pane)
//			.owner(qupath.getStage())
//			.expandableContent(labelInfo)
//			.nonModal()
//			.build();
		
		var stage = new Stage();
		stage.setTitle(title);
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
		stage.setOnHiding(e -> {
			var current = selectedOverlay.get();
			if (current != null && viewer.getCustomPixelLayerOverlay() == current) {
				current.stop();
				viewer.resetCustomPixelLayerOverlay();
				var data = viewer.getImageData();
				if (data != null)
					PixelClassificationImageServer.setPixelLayer(data, null);
			}
		});
		
	}
	

}