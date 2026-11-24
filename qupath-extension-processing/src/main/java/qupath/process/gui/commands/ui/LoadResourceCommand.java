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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.UpdateUrisCommand;
import qupath.lib.images.stores.ColorModelRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * Create commands for displaying pixel classifiers and density maps.
 * @param <S> the resource type
 *
 */
public final class LoadResourceCommand<S> implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(LoadResourceCommand.class);

	private final QuPathGUI qupath;
	private final ResourceType<S> resourceType;

	// Extra resources, not found within the project
	private final Map<String, S> externalResources = new TreeMap<>();

	private final Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> cachedServers = new WeakHashMap<>();
	private final PixelClassificationOverlay overlay;
	private final IntegerProperty nThreads = new SimpleIntegerProperty(1);
	private final ObservableList<String> items = FXCollections.observableArrayList();

	private final ObjectProperty<NamedResource<S>> selectedResource = new SimpleObjectProperty<>();

	private Stage stage;
	private Subscription subscription;

	private LoadResourceCommand(QuPathGUI qupath, ResourceType<S> resourceType, int nThreads) {
		this.qupath = qupath;
		this.resourceType = resourceType;
		this.nThreads.set(nThreads);
		this.overlay = PixelClassificationOverlay.create(qupath.getOverlayOptions(), cachedServers, new ColorModelRenderer(null));
		updateOverlayThreads();
		this.subscription = setupSubscriptions();
	}

	private Subscription setupSubscriptions() {
		return this.nThreads.subscribe(this::updateOverlayThreads)
				.and(this.selectedResource.subscribe(this::handleSelectedResourceChange))
				.and(qupath.projectProperty().subscribe(this::updateAvailableItems));
	}

	private void updateOverlayThreads() {
		int n = nThreads.get();
		if (n < 0) {
			n = PathPrefs.numCommandThreadsProperty().get();
		} else if (n == 0) {
			n = 1;
		}
		overlay.setMaxThreads(n);
	}

	private void handleSelectedResourceChange(NamedResource<S> newValue) {
		cachedServers.clear();
		updateServers(newValue, cachedServers);
		for (var viewer : qupath.getAllViewers()) {
			viewer.resetCustomPixelLayerOverlay();;
			if (newValue != null) {
				viewer.setCustomPixelLayerOverlay(overlay);
			}
		}
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

	private NamedResource<S> resourceForName(String name) {
		if (name == null)
			return null;
		try {
			var project = qupath.getProject();
			var manager = resourceType.getManager(project);
			S resource;
			boolean isExternal = false;
			if (manager != null && manager.contains(name)) {
					resource = manager.get(name);
			} else{
				resource = externalResources.get(name);
				isExternal = true;
			}
			if (resource instanceof UriResource uriResource) {
				int nChanges = UpdateUrisCommand.promptToUpdateUris(
						List.of(uriResource),
						project == null ? null : project.getURI(),
						project == null ? null : project.getPreviousURI(),
						true
				);
				var updater = UriUpdater.create(uriResource);
				int nMissing = updater.countMissing();
				if (nMissing > 0) {
					String message;
					if (nMissing > 1) {
						message = nMissing + " URIs could not be found";
					} else {
						message = "Cannot find " + uriListString(updater.getMissingItems(), ", ");
					}
					Dialogs.showErrorNotification(resourceType.getDialogTitle(), message);
					logger.error("Missing URIs:\n{}",
							uriListString(updater.getMissingItems(), "\n"));
					return null;
				} else if (nChanges > 0) {
					// If we made changes, save them
					if (!isExternal) {
						if (Dialogs.showConfirmDialog("Save changes", "Overwrite '" + name + "'?")) {
							manager.put(name, resource);
						}
					} else {
						// TODO: Support overwriting external resource
						logger.warn("I can't overwrite an external resource (yet)");
					}
				}
			}
			return new NamedResource<>(name, resource);
		} catch (Exception ex) {
			Dialogs.showErrorNotification(resourceType.getDialogTitle(), ex);
			logger.error(ex.getMessage(), ex);
			return null;
		}
	}

	@Override
	public void run() {
		if (stage == null) {
			stage = createStage();
		}
		stage.show();
	}

	private Stage createStage() {
		var comboClassifiers = new ComboBox<>(items);
		comboClassifiers.getSelectionModel().selectedItemProperty().addListener(this::handleSelectedNameChange);
		var classifierName = new SimpleStringProperty(null);
		classifierName.bind(comboClassifiers.getSelectionModel().selectedItemProperty());

		var label = new Label(resourceType.choosePrompt());
		label.setMinWidth(Label.USE_PREF_SIZE);
		label.setLabelFor(comboClassifiers);

		var btnLoadExistingClassifier = GuiTools.createMoreButton(createMoreMenu(), Side.RIGHT);

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		pane.setPrefWidth(350.0);
		GridPaneUtils.addGridRow(pane,0, 0, "Choose model to apply to the current image", label, comboClassifiers, btnLoadExistingClassifier);
		GridPaneUtils.setToExpandGridPaneWidth(comboClassifiers);


		if (resourceType.getResourceClass().equals(PixelClassifier.class)) {
			var labelRegion = new Label("Region");
			labelRegion.setMinWidth(Label.USE_PREF_SIZE);
			var comboRegionFilter = PixelClassifierUI.createRegionFilterCombo(qupath.getOverlayOptions());

			@SuppressWarnings("unchecked")
			var tilePane = PixelClassifierUI.createPixelClassifierButtons(
					qupath.imageDataProperty(),
					(ObjectExpression<PixelClassifier>)selectedResource.map(NamedResource::resource),
					classifierName);

			GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0, "Control where the pixel classification is applied during preview",
					labelRegion, comboRegionFilter, comboRegionFilter);
			GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0, "Apply pixel classification", tilePane, tilePane, tilePane);

			GridPaneUtils.setToExpandGridPaneWidth(tilePane);
		} else if (resourceType.getResourceClass().equals(DensityMapBuilder.class)) {
			@SuppressWarnings("unchecked")
			var buttonPane = DensityMapUI.createButtonPane(qupath, qupath.imageDataProperty(),
					(ObjectExpression<DensityMapBuilder>)selectedResource.map(NamedResource::resource),
					classifierName, Bindings.createObjectBinding(() -> overlay), false);
			GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0, null, buttonPane, buttonPane, buttonPane);
			GridPaneUtils.setToExpandGridPaneWidth(buttonPane);
		}


		// Handle drag and drop
		pane.setOnDragOver(this::handleDragOver);
		pane.setOnDragDropped(this::handleDragDropped);

		var stage = new Stage();
		stage.setTitle(resourceType.getDialogTitle());
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
		stage.sizeToScene();
		stage.setResizable(false);
		FXUtils.addCloseWindowShortcuts((Window)stage);

		stage.setOnShowing(this::handleStageShowing);
		stage.setOnHiding(this::handleStageHiding);

		stage.focusedProperty().addListener(this::handleStageFocusChange);
		return stage;
	}

	private ContextMenu createMoreMenu() {
		var menu = new ContextMenu();
		var miLoadClassifier = new MenuItem("Import from files");
		miLoadClassifier.setOnAction(this::handleImportFiles);
		var miOpenAsText = new MenuItem("Show as text");
		miOpenAsText.setOnAction(this::handleShowSelectedResourceAsText);
		miOpenAsText.disableProperty().bind(selectedResource.isNull());

		// Enable setting number of threads
		var miThreads = new MenuItem("Set parallel threads");
		miThreads.setOnAction(e -> promptToSetThreads());

		menu.getItems().addAll(miLoadClassifier, miOpenAsText, new SeparatorMenuItem(), miThreads);
		return menu;
	}

	private void handleSelectedNameChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
		selectedResource.set(resourceForName(newValue));
	}

	private void handleDragOver(DragEvent event) {
		if (!event.getDragboard().getFiles().isEmpty()) {
			event.acceptTransferModes(TransferMode.COPY);
			event.consume();
		}
	}

	private void handleDragDropped(DragEvent event) {
		logger.trace("File(s) dragged onto pane");
		Dragboard dragboard = event.getDragboard();
		addExternalFiles(dragboard.getFiles());
	}

	private void handleStageShowing(WindowEvent event) {
		if (subscription != null) {
			subscription.unsubscribe();
		}
		subscription = setupSubscriptions();
		updateAvailableItems();
		overlay.setLivePrediction(true);
	}

	private void handleStageHiding(WindowEvent event) {
		logger.debug("Resetting overlay");
		for (var viewer : qupath.getAllViewers()) {
			if (viewer.getCustomPixelLayerOverlay() == overlay)
				viewer.resetCustomPixelLayerOverlay();
		}
		overlay.setLivePrediction(false);
		overlay.clearCache();
		if (subscription != null) {
			subscription.unsubscribe();
		}
	}

	private void handleImportFiles(ActionEvent event) {
		List<File> files = FileChoosers.promptForMultipleFiles(resourceType.getDialogTitle(),
				FileChoosers.createExtensionFilter(resourceType.filePrompt(), ".*json"));
		if (files != null)
			addExternalFiles(files);
	}

	private void handleStageFocusChange(ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue) {
		if (Boolean.TRUE.equals(newValue)) {
			for (var viewer : qupath.getAllViewers())
				viewer.setCustomPixelLayerOverlay(overlay);
		}
		// Make sure we have all the servers we need - but don't reset existing ones
		updateServers(selectedResource.get(), cachedServers);
	}


	private void handleShowSelectedResourceAsText(ActionEvent e) {
		var resource = selectedResource.get();
		if (resource == null)
			return;
		String name = resource.name();
		try {
			// Pass the resource class, since it can be required for including the appropriate JSON properties
			var json = GsonTools.getInstance(true).toJson(resource.resource(), resourceType.getResourceClass());
			if (!name.endsWith(".json"))
				name = name + ".json";
			// Show in script editor if possible; this may include better formatting and syntax highlighting
			var scriptEditor = qupath.getScriptEditor();
			if (scriptEditor != null)
				scriptEditor.showScript(name, json);
			else
				Dialogs.showTextWindow(qupath.getStage(), name, json, Modality.NONE, false);
		} catch (Exception ex) {
			Dialogs.showErrorMessage("Show as text", "Unable to create a text representation of '" + name + "', sorry!");
			logger.error(ex.getMessage(), ex);
		}
	}


	private void promptToSetThreads() {
		var params = new ParameterList()
				.addIntParameter(
						"nThreads",
						"Number of parallel threads",
						nThreads.get(),
						null,
						"Number of threads to use for live prediction");
		if (!GuiTools.showParameterDialog("Set parallel threads", params))
			return;
		var val = params.getIntParameterValue("nThreads");
		nThreads.set(val);
	}

	private static String uriListString(Collection<UriUpdater.SingleUriItem> items, String delim) {
		return items.stream()
				.map(UriUpdater.SingleUriItem::getURI)
				.map(URI::toString)
				.collect(Collectors.joining(delim));
	}
	
	
	
	private void updateServers(NamedResource<S> resource, Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> cachedServers) {
		for (var viewer : qupath.getAllViewers()) {
			var imageData = viewer.getImageData();
			if (imageData != null) {
				var server = cachedServers.get(imageData);
				if (server == null && resource != null) {
					server = resourceType.getClassifierServer(resource.resource(), imageData);
					cachedServers.put(imageData, server);
				}
			}
		}
		qupath.getViewerManager().repaintAllViewers();
	}
	

	private void addExternalFiles(List<File> files) {
		if (files.isEmpty())
			return;
		try {
			addExternalJson(files, resourceType.getManager(qupath.getProject()), externalResources);
			updateAvailableItems();
		} catch (Exception ex) {
			Dialogs.showErrorMessage(resourceType.getDialogTitle(), ex);
			logger.error(ex.getMessage(), ex);
		}
	}
	
	private void updateAvailableItems() {
		var tempList = new ArrayList<String>();
		var manager = resourceType.getManager(qupath.getProject());
		if (manager != null) {
			try {
				tempList.addAll(manager.getNames());
			} catch (IOException e) {
				Dialogs.showErrorMessage(resourceType.getDialogTitle(), e);
			}
		}
		tempList.addAll(externalResources.keySet());
		if (!(new HashSet<>(items).containsAll(tempList) && items.size() == tempList.size()))
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
				logger.error(e.getMessage(), e);
			}
			var response = Dialogs.showYesNoCancelDialog("Copy classifier file" + plural, "Copy classifier" + plural + " to the current project?");
			if (response == ButtonType.CANCEL)
				return;
			addToManager = response == ButtonType.YES;
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
				Dialogs.showErrorNotification(String.format("Could not add %s", file.getName()), ex.getMessage());
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


	private record NamedResource<S> (String name, S resource) {}
	
	
	private interface ResourceType<T> {
		
		Class<T> getResourceClass();
		
		Manager<T> getManager(Project<?> project);
		
		default T readFromPath(Path path) throws IOException {
			try (var reader = Files.newBufferedReader(path)) {
				return GsonTools.getInstance().fromJson(reader, getResourceClass());
			}
		}
		
		String getDialogTitle();
		
		ImageServer<BufferedImage> getClassifierServer(T resource, ImageData<BufferedImage> imageData);
		
		String choosePrompt();
		
		String filePrompt();
 		
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
			if (resource == null) {
				logger.warn("Pixel classifier resource is null");
			} else if (imageData == null) {
				logger.warn("Image data is null");
			} else if (resource.supportsImage(imageData)) {
				return PixelClassifierTools.createPixelClassificationServer(imageData, resource);
			} else {
				logger.warn("Pixel classifier does not support {}", imageData.getServer().getMetadata().getName());
			}
			return null;
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
			return project == null ? null : project.getResources(DensityMaps.PROJECT_LOCATION, getResourceClass(), "json");
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