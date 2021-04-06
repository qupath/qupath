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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.io.GsonTools;
import qupath.lib.projects.Project;
import qupath.process.gui.ml.PixelClassificationOverlay;
import qupath.process.gui.ml.PixelClassifierUI;

/**
 * Command to apply a pre-trained pixel classifier to an image.
 * 
 * @author Pete Bankhead
 *
 */
public final class PixelClassifierLoadCommand implements Runnable {
	
	private final Logger logger = LoggerFactory.getLogger(PixelClassifierLoadCommand.class);
	private final String title = "Load Pixel Classifier";

	private QuPathGUI qupath;
	private Project<BufferedImage> project;
	
	/**
	 * Will hold external pixel classifiers (i.e. not from the project directory)
	 */
	private Map<String, PixelClassifier> externalPixelClassifiers;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public PixelClassifierLoadCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		project = qupath.getProject();
		var viewer = qupath.getViewer();
		
		var imageData = viewer.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}		
		
		if (project == null) {
			Dialogs.showErrorMessage(title, "You need a project open to run this command!");
			return;
		}
		
		Collection<String> names;
		try {
			names = project.getPixelClassifiers().getNames();
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e);
			return;
		}
		
		externalPixelClassifiers = new HashMap<>();
		ComboBox<String> comboClassifiers = new ComboBox<String>();
		comboClassifiers.getItems().setAll(names);
		var selectedClassifier = Bindings.createObjectBinding(() -> {
			String name = comboClassifiers.getSelectionModel().getSelectedItem();
			if (name != null) {
				try {
					if (project.getPixelClassifiers().contains(name))
						return project.getPixelClassifiers().get(name);
					return externalPixelClassifiers.get(name);
				} catch (Exception ex) {
					Dialogs.showErrorMessage("Load pixel model", ex);
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
				logger.info("Resetting pixel classifier overlay");
				viewer.resetCustomPixelLayerOverlay();
			} else {
				logger.info("Setting pixel classifier overlay for: {}", comboClassifiers.getSelectionModel().getSelectedItem());
				n.setLivePrediction(true);
				viewer.setCustomPixelLayerOverlay(n);
			}
		});
		
		var label = new Label("Choose model");
		label.setLabelFor(comboClassifiers);
		
		// Add file chooser
		var menu = new ContextMenu();
		var loadClassifierMI = new MenuItem("Add existing classifiers");
		loadClassifierMI.setOnAction(e -> {
			List<File> files = Dialogs.promptForMultipleFiles(title, null, "QuPath classifier file", "json");
			if (files == null || files.isEmpty())
				return;

			addClassifierFiles(files);
			try {
				List<String> updatedNames = new ArrayList<>();
				updatedNames.addAll(project.getPixelClassifiers().getNames());
				updatedNames.addAll(externalPixelClassifiers.keySet());
				comboClassifiers.getItems().setAll(updatedNames);
			} catch (IOException ex) {
				Dialogs.showErrorMessage(title, ex);
			}
		});
		
		menu.getItems().add(loadClassifierMI);
		var btnLoadExistingClassifier = GuiTools.createMoreButton(menu, Side.RIGHT);
		
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
		pane.setPrefWidth(350.0);
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, "Choose pixel classification model to apply to the current image", label, comboClassifiers, btnLoadExistingClassifier);
		PaneTools.addGridRow(pane, row++, 0, "Control where the pixel classification is applied during preview",
				labelRegion, comboRegionFilter, comboRegionFilter);
		PaneTools.addGridRow(pane, row++, 0, "Apply pixel classification", tilePane, tilePane, tilePane);
		
		PaneTools.setToExpandGridPaneWidth(comboClassifiers, tilePane);

		// Handle drag and drop
		pane.setOnDragOver(e -> {
			e.acceptTransferModes(TransferMode.COPY);
			e.consume();
		});
		
		pane.setOnDragDropped(e -> {
			logger.trace("File(s) dragged onto pane");
			Dragboard dragboard = e.getDragboard();
			if (dragboard.hasFiles()) {
				addClassifierFiles(dragboard.getFiles());
				try {
					List<String> updatedNames = new ArrayList<>();
					updatedNames.addAll(project.getPixelClassifiers().getNames());
					updatedNames.addAll(externalPixelClassifiers.keySet());
					comboClassifiers.getItems().setAll(updatedNames);
				} catch (IOException ex) {
					Dialogs.showErrorMessage(title, ex);
				}				
			}
		});
		
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
				logger.info("Resetting pixel classifier overlay");
				viewer.resetCustomPixelLayerOverlay();
				var data = viewer.getImageData();
				if (data != null)
					PixelClassificationImageServer.setPixelLayer(data, null);
			}
		});
		
	}
	
	private void addClassifierFiles(List<File> files) {
		String plural = files.size() > 1 ? "s" : "";
		var response = Dialogs.showYesNoCancelDialog("Copy classifier file" + plural, "Copy classifier" + plural + " to the current project?");
		if (response == DialogButton.CANCEL)
			return;
		
		List<File> fails = new ArrayList<>();
		for (var file: files) {
			try {
				if (!GeneralTools.getExtension(file).get().equals(".json"))
					throw new IOException(String.format("Classifier files should be JSON files (.json), not %s", GeneralTools.getExtension(file).get()));
				var json = Files.newBufferedReader(file.toPath());
				// TODO: Check if classifier is valid before adding it 
				var classifier = GsonTools.getInstance().fromJson(json, PixelClassifier.class);

				// Fix duplicate name
				int index = 1;
				String name = GeneralTools.getNameWithoutExtension(file);
				while (project.getPixelClassifiers().getNames().contains(name) || externalPixelClassifiers.containsKey(name))
					name = GeneralTools.getNameWithoutExtension(file) + " (" + index++ + ")";
				
				if (response == DialogButton.YES)
					project.getPixelClassifiers().put(name, classifier);
				else
					externalPixelClassifiers.put(name, classifier);
				logger.debug("Added {} to classifier combo.", name);
			} catch (IOException ex) {
				Dialogs.showErrorNotification(String.format("Could not add %s", file.getName()), ex.getLocalizedMessage());
				fails.add(file);
			}
		}
		
		if (!fails.isEmpty()) {
			String failedClassifiers = fails.stream().map(e -> "- " + e.getName()).collect(Collectors.joining(System.lineSeparator()));
			String pluralize = fails.size() == 1 ? "" : "s";
			Dialogs.showErrorMessage("Error adding classifier" + pluralize, String.format("Could not add the following classifier%s:%s%s", 
					pluralize,
					System.lineSeparator(), 
					failedClassifiers)
			);
		}
		
		int nSuccess = files.size() - fails.size();
		String plural2 = nSuccess > 1 ? "s" : "";
		if (nSuccess > 0)
			Dialogs.showInfoNotification("Classifier" + plural2 + " added successfully", String.format("%d classifier" + plural2 + " added", nSuccess));
	}
}