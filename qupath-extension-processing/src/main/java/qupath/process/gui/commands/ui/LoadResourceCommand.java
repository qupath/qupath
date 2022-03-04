/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.images.stores.ColorModelRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.io.UriResource;
import qupath.lib.io.UriUpdater;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ResourceManager.Manager;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.process.gui.commands.density.DensityMapUI;
import qupath.process.gui.commands.ml.PixelClassifierUI;

/**
 * Create commands for displaying pixel classifiers and density maps.
 * 
 * @author Pete Bankhead
 * @param <S> 
 *
 */
public final class LoadResourceCommand<S> implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(LoadResourceCommand.class);

	private QuPathGUI qupath;
	private ResourceType<S> resourceType;
	
	private Map<String, S> extras = new TreeMap<>();
	
	private int nThreads = 1;
	
	/**
	 * Constructor.
	 * @param qupath
	 * @param resourceType 
	 */
	private LoadResourceCommand(QuPathGUI qupath, ResourceType<S> resourceType, int nThreads) {
		this.qupath = qupath;
		this.resourceType = resourceType;
		this.nThreads = nThreads;
	}
	
	/**
	 * Create a {@link Runnable} to interactively load a pixel classifier and display it in all viewers.
	 * @param qupath
	 * @return
	 */
	public static LoadResourceCommand<PixelClassifier> createLoadPixelClassifierCommand(QuPathGUI qupath) {
		return new LoadResourceCommand<>(qupath, new PixelClassifierType(), PathPrefs.numCommandThreadsProperty().get());
	}
	
	/**
	 * Create a {@link Runnable} to interactively load a density map and display it in all viewers.
	 * @param qupath
	 * @return
	 */
	public static LoadResourceCommand<DensityMapBuilder> createLoadDensityMapCommand(QuPathGUI qupath) {
		return new LoadResourceCommand<>(qupath, new DensityMapType(), 1);
	}
	

	@Override
	public void run() {
		
		String title = resourceType.getDialogTitle();

		var cachedServers = new WeakHashMap<ImageData<BufferedImage>, ImageServer<BufferedImage>>();
		var overlay = PixelClassificationOverlay.create(qupath.getOverlayOptions(), cachedServers, new ColorModelRenderer(null));
		overlay.setMaxThreads(nThreads);
		for (var viewer : qupath.getViewers())
			viewer.setCustomPixelLayerOverlay(overlay);
		
		var comboClassifiers = new ComboBox<String>();
		try {
			updateAvailableItems(comboClassifiers.getItems());
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		var selectedResource = Bindings.createObjectBinding(() -> {
			String name = comboClassifiers.getSelectionModel().getSelectedItem();
			cachedServers.clear();
			if (name != null) {
				try {
					var project = qupath.getProject();
					var manager = resourceType.getManager(project);
					S resource;
					if (manager != null && manager.contains(name))
						resource = manager.get(name);
					else
						resource = extras.get(name);
					if (resource instanceof UriResource) {
						UriUpdater.fixUris((UriResource)resource, project);
					}
					return resource;
				} catch (Exception ex) {
					// TODO: Investigate why this is triggered twice
					Dialogs.showErrorNotification(resourceType.getDialogTitle(), ex);
				}
			}
			return null;
		}, comboClassifiers.getSelectionModel().selectedItemProperty());
		
		var label = new Label(resourceType.choosePrompt());
		label.setLabelFor(comboClassifiers);
		
		selectedResource.addListener((v, o, n) -> {
			cachedServers.clear();
			updateServers(n, cachedServers);
		});
		
		// Add file chooser
		var menu = new ContextMenu();
		var miLoadClassifier = new MenuItem("Import from files");
		miLoadClassifier.setOnAction(e -> {
			List<File> files = Dialogs.promptForMultipleFiles(title, null, resourceType.filePrompt(), "json");
			if (files == null || files.isEmpty())
				return;
			try {
				addExternalJson(files, resourceType.getManager(qupath.getProject()), extras);
				updateAvailableItems(comboClassifiers.getItems());
			} catch (IOException ex) {
				Dialogs.showErrorMessage(title, ex);
			}
		});
		var miOpenAsText = new MenuItem("Show as text");
		miOpenAsText.setOnAction(e -> {
			var name = comboClassifiers.getSelectionModel().getSelectedItem();
			var resource = selectedResource.get();
			if (resource == null)
				return;
			try {
				// Pass the resource class, since it can be required for including the appropriate JSON properties
				var json = GsonTools.getInstance(true).toJson(resource, resourceType.getResourceClass());
				if (!name.endsWith(".json"))
					name = name + ".json";
				// Show in script editor if possible; this may include better formatting and syntax highlighting
				var scriptEditor = qupath == null ? null : qupath.getScriptEditor();
				if (scriptEditor != null)
					scriptEditor.showScript(name, json);
				else
					Dialogs.showTextWindow(qupath.getStage(), name, json, Modality.NONE, false);
			} catch (Exception ex) {
				Dialogs.showErrorMessage("Show model as text", "Unable to create a text representation of '" + name + "', sorry!");
			}
		});
		miOpenAsText.disableProperty().bind(selectedResource.isNull());
		
		// Enable setting number of threads
		var miThreads = new MenuItem("Set parallel threads");
		miThreads.setOnAction(e -> {
			var params = new ParameterList()
					.addIntParameter(
							"nThreads",
							"Number of parallel threads",
							nThreads,
							null,
							"Number of threads to use for live prediction");
			if (!Dialogs.showParameterDialog("Set parallel threads", params))
				return;
//			var result = Dialogs.showInputDialog("Set parallel threads", "Number of threads to use for live prediction", (double)nThreads);
			var val = params.getIntParameterValue("nThreads");
			if (val == nThreads)
				return;
			if (val < 0)
				nThreads = PathPrefs.numCommandThreadsProperty().get();
			else
				nThreads = Math.max(1, val);
			if (overlay != null)
				overlay.setMaxThreads(nThreads);
		});
		
		menu.getItems().addAll(miLoadClassifier, miOpenAsText, new SeparatorMenuItem(), miThreads);
		var btnLoadExistingClassifier = GuiTools.createMoreButton(menu, Side.RIGHT);
		
		var classifierName = new SimpleStringProperty(null);
		classifierName.bind(comboClassifiers.getSelectionModel().selectedItemProperty());
		

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		pane.setPrefWidth(350.0);
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, "Choose model to apply to the current image", label, comboClassifiers, btnLoadExistingClassifier);
		PaneTools.setToExpandGridPaneWidth(comboClassifiers);

		
		if (resourceType.getResourceClass().equals(PixelClassifier.class)) {
			var labelRegion = new Label("Region");
			var comboRegionFilter = PixelClassifierUI.createRegionFilterCombo(qupath.getOverlayOptions());

			@SuppressWarnings("unchecked")
			var tilePane = PixelClassifierUI.createPixelClassifierButtons(qupath.imageDataProperty(), (ObjectExpression<PixelClassifier>)selectedResource, classifierName);
			
			PaneTools.addGridRow(pane, row++, 0, "Control where the pixel classification is applied during preview",
					labelRegion, comboRegionFilter, comboRegionFilter);
			PaneTools.addGridRow(pane, row++, 0, "Apply pixel classification", tilePane, tilePane, tilePane);

			PaneTools.setToExpandGridPaneWidth(tilePane);
		} else if (resourceType.getResourceClass().equals(DensityMapBuilder.class)) {
			@SuppressWarnings("unchecked")
			var buttonPane = DensityMapUI.createButtonPane(qupath, qupath.imageDataProperty(), (ObjectExpression<DensityMapBuilder>)selectedResource, classifierName, Bindings.createObjectBinding(() -> overlay), false);
			PaneTools.addGridRow(pane, row++, 0, null, buttonPane, buttonPane, buttonPane);
			PaneTools.setToExpandGridPaneWidth(buttonPane);
		}
						

		// Handle drag and drop
		pane.setOnDragOver(e -> {
			e.acceptTransferModes(TransferMode.COPY);
			e.consume();
		});
		
		pane.setOnDragDropped(e -> {
			logger.trace("File(s) dragged onto pane");
			Dragboard dragboard = e.getDragboard();
			if (dragboard.hasFiles()) {
				try {
					addExternalJson(dragboard.getFiles(), resourceType.getManager(qupath.getProject()), extras);
					updateAvailableItems(comboClassifiers.getItems());
				} catch (Exception ex) {
					Dialogs.showErrorMessage(title, ex);
				}				
			}
		});
		
		var stage = new Stage();
		stage.setTitle(title);
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
		stage.setOnHiding(e -> {
			if (overlay != null)
				overlay.stop();
			logger.debug("Resetting overlay");
			for (var viewer : qupath.getViewers()) {
				if (viewer.getCustomPixelLayerOverlay() == overlay)
					viewer.resetCustomPixelLayerOverlay();
			}
		});
		
		stage.focusedProperty().addListener((v, o, n) -> {
			if (n && overlay != null) {
				for (var viewer : qupath.getViewers())
					viewer.setCustomPixelLayerOverlay(overlay);
			}
			// Make sure we have all the servers we need - but don't reset existing ones
			updateServers(selectedResource.get(), cachedServers);
		});
		
		overlay.setLivePrediction(true);
		
	}
	
	
	
	private void updateServers(S resource, Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> cachedServers) {
		for (var viewer : qupath.getViewers()) {
			var imageData = viewer.getImageData();
			if (imageData != null) {
				var server = cachedServers.get(imageData);
				if (server == null && resource != null) {
					server = resourceType.getClassifierServer(resource, imageData);
					cachedServers.put(imageData, server);
				}
			}
		}
		qupath.repaintViewers();
	}
	
	
	
	private void updateAvailableItems(ObservableList<String> items) throws IOException {
		var tempList = new ArrayList<String>();
		var manager = resourceType.getManager(qupath.getProject());
		if (manager != null)
			tempList.addAll(manager.getNames());
		tempList.addAll(extras.keySet());
		if (!(items.containsAll(tempList) && items.size() == tempList.size()))
			items.setAll(tempList);
	}
	
	
	
	
	private void addExternalJson(List<File> files, Manager<S> manager, Map<String, S> externalResources) {
		String plural = files.size() > 1 ? "s" : "";
		Set<String> currentNames = new HashSet<>();
		boolean addToManager = false;
		
		if (manager != null) {
			try {
				currentNames.addAll(manager.getNames());
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage(), e);
			}
			var response = Dialogs.showYesNoCancelDialog("Copy classifier file" + plural, "Copy classifier" + plural + " to the current project?");
			if (response == DialogButton.CANCEL)
				return;
			addToManager = response == DialogButton.YES;
		}
		
		List<File> fails = new ArrayList<>();
		for (var file: files) {
			try {
				if (!GeneralTools.getExtension(file).get().equals(".json"))
					throw new IOException(String.format("We need JSON files (.json), not %s", GeneralTools.getExtension(file).orElse("missing any file extension")));
				
				// TODO: Check if classifier is valid before adding it 
				var classifier = resourceType.readFromPath(file.toPath());

				// Get a unique name (adding number if needed)
				String name = GeneralTools.getNameWithoutExtension(file);
				name = GeneralTools.generateDistinctName(name, currentNames);
				
				if (addToManager)
					manager.put(name, classifier);
				else
					externalResources.put(name, classifier);
				currentNames.add(name);
				logger.debug("Added {}", name);
			} catch (IOException ex) {
				Dialogs.showErrorNotification(String.format("Could not add %s", file.getName()), ex.getLocalizedMessage());
				fails.add(file);
			}
		}
		
		if (!fails.isEmpty()) {
			String failedClassifiers = fails.stream().map(e -> "- " + e.getName()).collect(Collectors.joining(System.lineSeparator()));
			String pluralize = fails.size() == 1 ? "" : "s";
			Dialogs.showErrorMessage("Error adding classifier" + pluralize, String.format("Could not add the following:%s%s", 
					System.lineSeparator(), 
					failedClassifiers)
			);
		}
		
		int nSuccess = files.size() - fails.size();
		String plural2 = nSuccess > 1 ? "s" : "";
		if (nSuccess > 0)
			Dialogs.showInfoNotification("Classifier" + plural2 + " added successfully", String.format("%d classifier" + plural2 + " added", nSuccess));
	}
	
	
	
	private static interface ResourceType<T> {
		
		public Class<T> getResourceClass();
		
		public Manager<T> getManager(Project<?> project);
		
		public default T readFromPath(Path path) throws IOException {
			try (var reader = Files.newBufferedReader(path)) {
				return GsonTools.getInstance().fromJson(reader, getResourceClass());
			}
		}
		
		public String getDialogTitle();
		
		public ImageServer<BufferedImage> getClassifierServer(T resource, ImageData<BufferedImage> imageData);
		
		public String choosePrompt();
		
		public String filePrompt();
 		
	}
	
	private static class PixelClassifierType implements ResourceType<PixelClassifier> {
		
		@Override
		public Class<PixelClassifier> getResourceClass() {
			return PixelClassifier.class;
		}

		@Override
		public Manager<PixelClassifier> getManager(Project<?> project) {
			return project == null ? null : project.getPixelClassifiers();
		}

		@Override
		public String getDialogTitle() {
			return "Load pixel classifier";
		}

		@Override
		public ImageServer<BufferedImage> getClassifierServer(PixelClassifier resource,
				ImageData<BufferedImage> imageData) {
			return PixelClassifierTools.createPixelClassificationServer(imageData, resource);
		}

		@Override
		public String choosePrompt() {
			return "Choose model";
		}
		
		@Override
		public String filePrompt() {
			return "Pixel classifier (.json)";
		}
		
		
	}
	
	private static class DensityMapType implements ResourceType<DensityMapBuilder> {

		@Override
		public Class<DensityMapBuilder> getResourceClass() {
			return DensityMapBuilder.class;
		}

		@Override
		public Manager<DensityMapBuilder> getManager(Project<?> project) {
			return project.getResources(DensityMaps.PROJECT_LOCATION, getResourceClass(), "json");
		}
		
		@Override
		public String getDialogTitle() {
			return "Load density map";
		}

		@Override
		public ImageServer<BufferedImage> getClassifierServer(DensityMapBuilder resource,
				ImageData<BufferedImage> imageData) {
			return resource.buildServer(imageData);
		}

		@Override
		public String choosePrompt() {
			return "Choose map";
		}
		
		@Override
		public String filePrompt() {
			return "Density map (.json)";
		}

		
	}
	
	
}