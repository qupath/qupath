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

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.textfield.TextFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

/**
 * Component for previewing and selecting images within a project.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectBrowser implements ChangeListener<ImageData<BufferedImage>> {

	final static String THUMBNAIL_EXT = "jpg";

	final private static Logger logger = LoggerFactory.getLogger(ProjectBrowser.class);

	private Project<BufferedImage> project;

	// Requested thumbnail max dimensions
	private int thumbnailWidth = 1000;
	private int thumbnailHeight = 600;

	private QuPathGUI qupath;
	private BorderPane panel;

	private ProjectImageTreeModel model = new ProjectImageTreeModel(null);
	private TreeView<Object> tree = new TreeView<>();

	// Keep a record of servers we've requested - don't want to keep putting in requests if the server is unavailable
	private Set<String> serversRequested = new HashSet<>();
	
	private StringProperty descriptionText = new SimpleStringProperty();
	
	private static TextField tfFilter;

	private static ObjectProperty<ProjectThumbnailSize> thumbnailSize = PathPrefs.createPersistentPreference("projectThumbnailSize",
			ProjectThumbnailSize.SMALL, ProjectThumbnailSize.class);
	
	private static String[] additionalSortKeys;
	
	private static final String URI = "URI";
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public ProjectBrowser(final QuPathGUI qupath) {
		this.project = qupath.getProject();
		this.qupath = qupath;

		qupath.imageDataProperty().addListener(this);
		
		PathPrefs.maskImageNamesProperty().addListener((v, o, n) -> {
			tree.refresh();
		});

		panel = new BorderPane();

		tree.setCellFactory(new Callback<TreeView<Object>, TreeCell<Object>>() {
			@Override public TreeCell<Object> call(TreeView<Object> treeView) {
				return new ImageEntryCell();
			}
		});
		
		thumbnailSize.addListener((v, o, n) -> tree.refresh());

		tree.setRoot(null);

		tree.setContextMenu(getPopup());

		tree.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				qupath.openImageEntry(getSelectedEntry());
				e.consume();
			}
		});

		tree.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				qupath.openImageEntry(getSelectedEntry());
				e.consume();
			}
		});
		
//		TextArea textDescription = new TextArea();
		TextArea textDescription = new TextArea();
		textDescription.textProperty().bind(descriptionText);
		textDescription.setWrapText(true);
		MasterDetailPane mdTree = new MasterDetailPane(Side.BOTTOM, tree, textDescription, false);
		mdTree.showDetailNodeProperty().bind(descriptionText.isNotNull());
		
		tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			Object selected = n == null ? null : n.getValue();
			if (selected instanceof ProjectImageEntry)
				descriptionText.set(((ProjectImageEntry<?>)selected).getDescription());
			else
				descriptionText.set(null);
		});

		TitledPane titledTree = new TitledPane("Image list", mdTree);
		titledTree.setCollapsible(false);
		titledTree.setMaxHeight(Double.MAX_VALUE);
		
		
		tfFilter = new TextField();
		tfFilter.setPromptText("Search entry in project");
		tfFilter.setTooltip(new Tooltip("Type some text to filter the project entries by name or type."));
		
		tfFilter.textProperty().addListener((m, o, n) -> {
			model.rebuildModel();
			tree.setRoot(model.getRootFX());
			tree.getRoot().setExpanded(true);
			
			// If user has entered keyword(s) for search and some entries match, 
			// expand the first TreeItem that contains relevant matches.
			try {
				var listOfChildren = tree.getRoot().getChildren();
				if (tfFilter.getText().length() > 0) {
						for (int i = 0; i < listOfChildren.size(); i++) {
							if (listOfChildren.get(i).getChildren().size() > 0) {
								listOfChildren.get(i).setExpanded(true);
								break;
							}
					}
				}
			} catch (Exception e) {}
		});
		
		
		var paneUserFilter = PaneTools.createRowGrid(tfFilter);
		
		BorderPane panelTree = new BorderPane();
		panelTree.setCenter(titledTree);

		panel.setBottom(paneUserFilter);
		panel.setCenter(panelTree);

		Button btnOpen = ActionTools.createButton(qupath.lookupActionByText("Open project"), false);
		Button btnCreate = ActionTools.createButton(qupath.lookupActionByText("Create project"), false);
		Button btnAdd = ActionTools.createButton(qupath.lookupActionByText("Add images"), false);
		GridPane paneButtons = PaneTools.createColumnGridControls(btnCreate, btnOpen, btnAdd);
		paneButtons.prefWidthProperty().bind(panel.widthProperty());
		paneButtons.setPadding(new Insets(5, 5, 5, 5));
		panel.setTop(paneButtons);
		
		qupath.getPreferencePane().addChoicePropertyPreference(
				thumbnailSize, FXCollections.observableArrayList(ProjectThumbnailSize.values()), ProjectThumbnailSize.class,
				"Project thumbnails size", "Appearance", "Choose thumbnail size for the project pane");
		
		additionalSortKeys = new String[] {URI};

	}
	
	private static String getUserFilter() {
		return tfFilter.getText().toLowerCase();
	}



	ContextMenu getPopup() {
		
		Action actionOpenImage = new Action("Open image", e -> qupath.openImageEntry(getSelectedEntry()));
		Action actionRemoveImage = new Action("Delete image(s)", e -> {
			Collection<ProjectImageEntry<BufferedImage>> entries = getAllSelectedEntries();
			
			if (entries.isEmpty())
				return;
			
			// Don't allow us to remove any entries that are currently open (in any viewer)
			var project = getProject();
			if (project != null) {
				for (var viewer : qupath.getViewers()) {
					var imageData = viewer.getImageData();
					var entry = imageData == null ? null : getProject().getEntry(imageData);
					if (entry != null && entries.contains(entry)) {
						Dialogs.showErrorMessage("Remove project entries", "Please close all images you want to remove!");
						return;
					}
				}
			}
			
			if (entries.size() == 1) {
				if (!Dialogs.showConfirmDialog("Remove project entry", "Remove " + entries.iterator().next().getImageName() + " from project?"))
					return;
			} else if (!Dialogs.showYesNoDialog("Remove project entries", String.format("Remove %d entries?", entries.size())))
				return;
			
			var result = Dialogs.showYesNoCancelDialog("Remove project entries",
					"Delete all associated data?");
			if (result == DialogButton.CANCEL)
				return;
			
			project.removeAllImages(entries, result == DialogButton.YES);
			model.rebuildModel();
			syncProject(project);
			if (tree != null) {
				boolean isExpanded = tree.getRoot() != null && tree.getRoot().isExpanded();
				tree.setRoot(model.getRootFX());
				tree.getRoot().setExpanded(isExpanded);
//				tree.refresh();
			}
		});
		
		Action actionSetImageName = new Action("Rename image", e -> {
			TreeItem<?> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue() instanceof ProjectImageEntry) {
				if (setProjectEntryImageName((ProjectImageEntry<BufferedImage>)path.getValue()) && project != null)
					syncProject(project);
			}
		});
		
		// Refresh thumbnail according to current display settings
		Action actionRefreshThumbnail = new Action("Refresh thumbnail", e -> {
			TreeItem<?> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue() instanceof ProjectImageEntry) {
				ProjectImageEntry<BufferedImage> entry = (ProjectImageEntry<BufferedImage>)path.getValue();
				if (!isCurrentImage(entry)) {
					logger.warn("Cannot refresh entry for image that is not open!");
					return;
				}
				BufferedImage imgThumbnail = qupath.getViewer().getRGBThumbnail();
				imgThumbnail = resizeForThumbnail(imgThumbnail);
				try {
					entry.setThumbnail(imgThumbnail);
				} catch (IOException e1) {
					logger.error("Error writing thumbnail", e1);
				}
				tree.refresh();
			}
		});
		
		// Edit the description for the image
		Action actionEditDescription = new Action("Edit description", e -> {
			Project<?> project = getProject();
			ProjectImageEntry<?> entry = getSelectedEntry();
			if (project != null && entry != null) {
				if (showDescriptionEditor(entry)) {
					descriptionText.set(entry.getDescription());
					syncProject(project);						
				}
			} else {
				Dialogs.showErrorMessage("Edit image description", "No entry is selected!");
			}
		});
		
		// Add a metadata value
		Action actionAddMetadataValue = new Action("Add metadata", e -> {
			Project<BufferedImage> project = getProject();
			Collection<ProjectImageEntry<BufferedImage>> entries = getAllSelectedEntries();
			if (project != null && !entries.isEmpty()) {
				
				TextField tfMetadataKey = new TextField();
				var suggestions = project.getImageList().stream()
						.map(p -> p.getMetadataKeys())
						.flatMap(Collection::stream)
						.distinct()
						.sorted()
						.collect(Collectors.toList());
				TextFields.bindAutoCompletion(tfMetadataKey, suggestions);
				
				TextField tfMetadataValue = new TextField();
				Label labKey = new Label("New key");
				Label labValue = new Label("New value");
				labKey.setLabelFor(tfMetadataKey);
				labValue.setLabelFor(tfMetadataValue);
				tfMetadataKey.setTooltip(new Tooltip("Enter the name for the metadata entry"));
				tfMetadataValue.setTooltip(new Tooltip("Enter the value for the metadata entry"));
				
				ProjectImageEntry<BufferedImage> entry = entries.size() == 1 ? entries.iterator().next() : null;
				int nMetadataValues = entry == null ? 0 : entry.getMetadataKeys().size();
				
				GridPane pane = new GridPane();
				pane.setVgap(5);
				pane.setHgap(5);
				pane.add(labKey, 0, 0);
				pane.add(tfMetadataKey, 1, 0);
				pane.add(labValue, 0, 1);
				pane.add(tfMetadataValue, 1, 1);
				String name = entries.size() + " images";
				if (entry != null) {
					name = entry.getImageName();
					if (nMetadataValues > 0) {
						
						Label labelCurrent = new Label("Current metadata");
						TextArea textAreaCurrent = new TextArea();
						textAreaCurrent.setEditable(false);
	
						String keyString = entry.getMetadataSummaryString();
						if (keyString.isEmpty())
							textAreaCurrent.setText("No metadata entries yet");
						else
							textAreaCurrent.setText(keyString);
						textAreaCurrent.setPrefRowCount(3);
						labelCurrent.setLabelFor(textAreaCurrent);
	
						pane.add(labelCurrent, 0, 2);
						pane.add(textAreaCurrent, 1, 2);	
					}
				}
				
				Dialog<ButtonType> dialog = new Dialog<>();
				dialog.setTitle("Metadata");
				dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
				dialog.getDialogPane().setHeaderText("Set metadata for " + name);
				dialog.getDialogPane().setContent(pane);
				Optional<ButtonType> result = dialog.showAndWait();
				if (result.isPresent() && result.get() == ButtonType.OK) {
					String key = tfMetadataKey.getText().trim();
					String value = tfMetadataValue.getText();
					if (key.isEmpty()) {
						logger.warn("Attempted to set metadata value for {}, but key was empty!", name);
					} else {
						// Set metadata for all entries
						for (var temp : entries)
							temp.putMetadataValue(key, value);
						syncProject(project);
						tree.refresh();
					}
				}
							
			} else {
				Dialogs.showErrorMessage("Edit image description", "No entry is selected!");
			}
		});
		
		Action actionDuplicateImages = new Action("Duplicate image(s)", e -> {
			Collection<ProjectImageEntry<BufferedImage>> entries = getAllSelectedEntries();
			if (entries.isEmpty()) {
				logger.debug("Cannot duplicate project entries - no entries selected");
				return;
			}
			
			boolean singleImage = false;
			String name = "";
			String title = "Duplicate images";
			String namePrompt = "Append to image name";
			String nameHelp = "Specify text to append to the image name to distinguish duplicated images";
			if (entries.size() == 1) {
				title = "Duplicate image";
				namePrompt = "Duplicate image name";
				nameHelp = "Specify name for the duplicated image";
				singleImage = true;
				name = entries.iterator().next().getImageName();
				name = GeneralTools.generateDistinctName(
						name,
						project.getImageList().stream().map(p -> p.getImageName()).collect(Collectors.toSet()));
			}
			var params = new ParameterList()
					.addStringParameter("name", namePrompt, name, nameHelp)
					.addBooleanParameter("copyData", "Also duplicate data files", true, "Duplicate any associated data files along with the image");
			
			if (!Dialogs.showParameterDialog(title, params))
				return;

			boolean copyData = params.getBooleanParameterValue("copyData");
			name = params.getStringParameterValue("name");

			// Ensure we have a single space and then the text to append, with extra whitespace removed
			if (!singleImage && !name.isBlank())
				name = " " + name.strip();
			
			for (var entry : entries) {
				try {
					var newEntry = project.addDuplicate(entry, copyData);
					if (newEntry != null && !name.isBlank()) {
						if (singleImage)
							newEntry.setImageName(name);
						else
							newEntry.setImageName(newEntry.getImageName() + name);
					}
				} catch (Exception ex) {
					Dialogs.showErrorNotification("Duplicating image", "Error duplicating " + entry.getImageName());
					logger.error(ex.getLocalizedMessage(), ex);
				}
			}
			try {
				project.syncChanges();
			} catch (Exception ex) {
				logger.error("Error synchronizing project changes: " + ex.getLocalizedMessage(), ex);
			}
			refreshProject();
			if (entries.size() == 1)
				logger.debug("Duplicated 1 image entry");
			else
				logger.debug("Duplicated {} image entries");
		});
		
		// Open the project directory using Explorer/Finder etc.
		Action actionOpenProjectDirectory = createBrowsePathAction("Project...", () -> getProjectPath());
		Action actionOpenProjectEntryDirectory = createBrowsePathAction("Project entry...", () -> getProjectEntryPath());
		Action actionOpenImageServerDirectory = createBrowsePathAction("Image server...", () -> getImageServerPath());
		

		Menu menuSort = new Menu("Sort by...");
		ContextMenu menu = new ContextMenu();
		
		var hasProjectBinding = qupath.projectProperty().isNotNull();
		var menuOpenDirectories = MenuTools.createMenu("Open directory...", 
				actionOpenProjectDirectory,
				actionOpenProjectEntryDirectory,
				actionOpenImageServerDirectory);
		menuOpenDirectories.visibleProperty().bind(hasProjectBinding);
//		MenuItem miOpenProjectDirectory = ActionUtils.createMenuItem(actionOpenProjectDirectory);
		
		
		MenuItem miOpenImage = ActionUtils.createMenuItem(actionOpenImage);
		MenuItem miRemoveImage = ActionUtils.createMenuItem(actionRemoveImage);
		MenuItem miDuplicateImage = ActionUtils.createMenuItem(actionDuplicateImages);
		MenuItem miSetImageName = ActionUtils.createMenuItem(actionSetImageName);
		MenuItem miRefreshThumbnail = ActionUtils.createMenuItem(actionRefreshThumbnail);
		MenuItem miEditDescription = ActionUtils.createMenuItem(actionEditDescription);
		MenuItem miAddMetadata = ActionUtils.createMenuItem(actionAddMetadataValue);
		
		
		// Set visibility as menu being displayed
		menu.setOnShowing(e -> {
			TreeItem<Object> selected = tree.getSelectionModel().getSelectedItem();
			var entries = getAllSelectedEntries();
			boolean hasImageEntry = selected != null && selected.getValue() instanceof ProjectImageEntry;
//			miOpenProjectDirectory.setVisible(project != null && project.getBaseDirectory().exists());
			miOpenImage.setVisible(hasImageEntry);
			miSetImageName.setVisible(hasImageEntry);
			miAddMetadata.setVisible(!entries.isEmpty());
			miEditDescription.setVisible(hasImageEntry);
			miRefreshThumbnail.setVisible(hasImageEntry && isCurrentImage((ProjectImageEntry<BufferedImage>)selected.getValue()));
			miRemoveImage.setVisible(project != null && !project.getImageList().isEmpty());
			
			if (project == null) {
				menuSort.setVisible(false);
				return;
			}
			Map<String, MenuItem> newItems = new TreeMap<>();
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				// Add all entry metadata keys
				for (String key : entry.getMetadataKeys()) {
					if (!newItems.containsKey(key))
						newItems.put(key, ActionUtils.createMenuItem(createSortByKeyAction(key, key)));
				}
				// Add all additional keys
				for (String key : additionalSortKeys) {
					if (!newItems.containsKey(key))
						newItems.put(key, ActionUtils.createMenuItem(createSortByKeyAction(key, key)));
				}
			}
			menuSort.getItems().setAll(newItems.values());
			
			menuSort.getItems().add(0, ActionUtils.createMenuItem(createSortByKeyAction("None", null)));
			menuSort.getItems().add(1, new SeparatorMenuItem());
			
			menuSort.setVisible(true);
			
			if (menu.getItems().isEmpty())
				e.consume();
		});
		
		SeparatorMenuItem separator = new SeparatorMenuItem();
		separator.visibleProperty().bind(menuSort.visibleProperty());
		menu.getItems().addAll(
				miOpenImage,
				miRemoveImage,
				miDuplicateImage,
				new SeparatorMenuItem(),
				miSetImageName,
				miAddMetadata,
				miEditDescription,
				miRefreshThumbnail,
				separator,
				menuSort
				);
		
		separator = new SeparatorMenuItem();
		separator.visibleProperty().bind(menuOpenDirectories.visibleProperty());
		if (Desktop.isDesktopSupported()) {
			menu.getItems().addAll(
					separator,
					menuOpenDirectories);
		}

		return menu;

	}
	
	Path getProjectPath() {
		return project == null ? null : project.getPath();
	}

	Path getProjectEntryPath() {
		var selected = tree.getSelectionModel().getSelectedItem();
		if (selected == null)
			return null;
		var item = selected.getValue();
		if (item instanceof ProjectImageEntry<?>)
			return ((ProjectImageEntry<?>)item).getEntryPath();
		return null;
	}
	
	Path getImageServerPath() {
		var selected = tree.getSelectionModel().getSelectedItem();
		if (selected == null)
			return null;
		var item = selected.getValue();
		if (item instanceof ProjectImageEntry<?>) {
			try {
				var uris = ((ProjectImageEntry<?>)item).getServerURIs();
				if (!uris.isEmpty())
					return GeneralTools.toPath(uris.iterator().next());
			} catch (IOException e) {
				logger.debug("Error converting server path to file path", e);
			}
		}
		return null;
	}

	
	Action createBrowsePathAction(String text, Supplier<Path> func) {
		var action = new Action(text, e -> {
			var path = func.get();
			if (path == null)
				return;
			// Get directory if we will need one
			GuiTools.browseDirectory(path.toFile());
		});
		action.disabledProperty().bind(Bindings.createBooleanBinding(() -> func.get() == null, tree.getSelectionModel().selectedItemProperty()));
		return action;
	}
	
	
	
	/**
	 * Try to save a project, showing an error message if this fails.
	 * 
	 * @param project
	 * @return
	 */
	public static boolean syncProject(Project<?> project) {
		try {
			logger.info("Saving project {}...", project);
			project.syncChanges();
			return true;
		} catch (IOException e) {
			Dialogs.showErrorMessage("Save project", e);
			return false;
		}
	}
	
	
	boolean showDescriptionEditor(ProjectImageEntry<?> entry) {
		TextArea editor = new TextArea();
		editor.setWrapText(true);
		editor.setText(entry.getDescription());
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setTitle("Image description");
		dialog.getDialogPane().setHeaderText(entry.getImageName());
		dialog.getDialogPane().setContent(editor);
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK && editor.getText() != null) {	
			var text = editor.getText();
			entry.setDescription(text.isEmpty() ? null : text);
			return true;
		}
		return false;
	}


	private Project<BufferedImage> getProject() {
		return project;
	}

	/**
	 * Get the {@link Pane} component for addition to a scene.
	 * @return
	 */
	public Pane getPane() {
		return panel;
	}


	/**
	 * Set the project.
	 * @param project
	 * @return true if the project is now set (even if unchanged), false if the project change was thwarted or canceled.
	 */
	public boolean setProject(final Project<BufferedImage> project) {
		if (this.project == project)
			return true;		
		
		this.project = project;

		model = new ProjectImageTreeModel(project);
		tree.setRoot(model.getRootFX());
		tree.getRoot().setExpanded(true);
		return true;
	}
	
	
	/**
	 * Refresh the current project, updating the displayed entries.
	 * Note that this must be called on the JavaFX Application thread.
	 * If it is not, the request will be passed to the application thread 
	 * (and therefore not processed immediately).
	 */
	public void refreshProject() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> refreshProject());
			return;
		}
		model = new ProjectImageTreeModel(project);
		tree.setRoot(model.getRootFX());
		tree.getRoot().setExpanded(true);		
	}


	void ensureServerInWorkspace(final ImageData<BufferedImage> imageData) {
		if (imageData == null || project == null)
			return;
		
		if (project.getEntry(imageData) != null)
			return;

		var entry = ProjectCommands.addSingleImageToProject(project, imageData.getServer(), null);
		if (entry != null) {
			boolean expanded = tree.getRoot() != null && tree.getRoot().isExpanded();
			tree.setRoot(model.getRootFX());
			setSelectedEntry(tree, tree.getRoot(), project.getEntry(imageData));
			syncProject(project);
			if (expanded)
				tree.getRoot().setExpanded(true);
			// Copy the ImageData to the current entry
			if (!entry.hasImageData()) {
				try {
					logger.info("Copying ImageData to {}", entry);
					entry.saveImageData(imageData);
				} catch (IOException e) {
					logger.error("Unable to save ImageData: " + e.getLocalizedMessage(), e);
				}
			}
		}
	}



	@Override
	public void changed(final ObservableValue<? extends ImageData<BufferedImage>> source, final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
		if (imageDataNew == null || project == null)
			return;
		ProjectImageEntry<BufferedImage> entry = project.getEntry(imageDataNew);
		if (entry == null) {
			// Previously we gave a choice... now we force the image to be included in the project to avoid complications
//			if (DisplayHelpers.showYesNoDialog("Add to project", "Add " + imageDataNew.getServer().getShortServerName() + " to project?"))
				ensureServerInWorkspace(imageDataNew);
		}
		else if (!entry.equals(getSelectedEntry()))
			setSelectedEntry(tree, tree.getRoot(), entry);
		//			list.setSelectedValue(entry, true);

		if (tree != null) {
			tree.refresh();
		}
	}



	static <T> boolean setSelectedEntry(TreeView<T> treeView, TreeItem<T> item, final T object) {
		if (item.getValue() == object) {
			treeView.getSelectionModel().select(item);
			return true;
		} else {
			for (TreeItem<T> child : item.getChildren()) {
				if (setSelectedEntry(treeView, child, object))
					return true;
			}
		}
		return false;
	}
	
	
	/**
	 * Resize an image so that its dimensions fit inside thumbnailWidth x thumbnailHeight.
	 * 
	 * Note: this assumes the image can be drawn to a Graphics object.
	 * 
	 * @param imgThumbnail
	 * @return
	 */
	BufferedImage resizeForThumbnail(BufferedImage imgThumbnail) {
		double scale = Math.min((double)thumbnailWidth / imgThumbnail.getWidth(), (double)thumbnailHeight / imgThumbnail.getHeight());
		if (scale > 1)
			return imgThumbnail;
		BufferedImage imgThumbnail2 = new BufferedImage((int)(imgThumbnail.getWidth() * scale), (int)(imgThumbnail.getHeight() * scale), imgThumbnail.getType());
		Graphics2D g2d = imgThumbnail2.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(imgThumbnail, 0, 0, imgThumbnail2.getWidth(), imgThumbnail2.getHeight(), null);
		g2d.dispose();
		return imgThumbnail2;
	}


	//	@Override
	//	public void valueChanged(ListSelectionEvent e) {
	//		updateThumbnailForSelected();
	////		openSelectedImage();
	////		ProjectImageEntry entry = list.getSelectedValue();
	////		qupath.openWholeSlideServer(entry.getServerPath(), true, true);
	//	}



	//	boolean openImage(final String path) {
	//		
	//	}


	ImageData<BufferedImage> getCurrentImageData() {
		return qupath.getViewer().getImageData();
	}


	File getBaseDirectory() {
		return Projects.getBaseDirectory(project);
	}


	File getProjectFile() {
		File dirBase = getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory())
			return null;
		return new File(dirBase, "project" + ProjectIO.getProjectExtension());
	}


	boolean isCurrentImage(final ProjectImageEntry<BufferedImage> entry) {
		ImageData<BufferedImage> imageData = getCurrentImageData();
		if (imageData == null || entry == null || project == null)
			return false;
		return project.getEntry(imageData) == entry;
	}


	Collection<ProjectImageEntry<BufferedImage>> getAllSelectedEntries() {
		List<TreeItem<Object>> selected = tree.getSelectionModel().getSelectedItems();
		if (selected == null)
			return Collections.emptyList();
		return selected.stream().map(p -> {
			if (p.getValue() instanceof ProjectImageEntry)
				return Collections.singletonList((ProjectImageEntry<BufferedImage>)p.getValue());
			else
				return getImageEntries(p, null);
		}).flatMap(Collection::stream).collect(Collectors.toSet());
	}
	

	ProjectImageEntry<BufferedImage> getSelectedEntry() {
		TreeItem<Object> selected = tree.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue() instanceof ProjectImageEntry)
			return (ProjectImageEntry<BufferedImage>)selected.getValue();
		return null;
	}


	static Collection<ProjectImageEntry<BufferedImage>> getImageEntries(final TreeItem<?> item, Collection<ProjectImageEntry<BufferedImage>> entries) {
		if (entries == null)
			entries = new HashSet<>();
		if (item.getValue() instanceof ProjectImageEntry)
			entries.add((ProjectImageEntry<BufferedImage>)item.getValue());
		for (TreeItem<?> child : item.getChildren()) {
			entries = getImageEntries(child, entries);
		}
		return entries;
	}
	
	/**
	 * Gets the value of the entry for the specified key.
	 * E.g. if key == URI, the value returned will be the entry's URI.
	 * This method should be used to get sorting values that
	 * are not specifically part of an entry's metadata.
	 * @param <T>
	 * @param key
	 * @return value
	 * @throws IOException 
	 */
	private static <T> String getDefaultValue(ProjectImageEntry<T> entry, String key) throws IOException {
		if (key.equals(URI)) {
			var URIs = entry.getServerURIs();
			var it = URIs.iterator();
			if (URIs.size() == 1) {
				URI uri = it.next();
				String fullURI = uri.getPath();
				if (uri.getAuthority() != null)
					return "[remote] " + uri.getAuthority() + fullURI;
				return fullURI.substring(fullURI.lastIndexOf("/")+1, fullURI.length());
			}
			else
				return "Multiple URIs";
		}
		return "Undefined";
	}



	Action createSortByKeyAction(final String name, final String key) {
		return new Action(name, e -> {
			if (model == null)
				return;
			model.setMetadataKeys(key);
			ProjectImageEntry<BufferedImage> entrySelected = getSelectedEntry();
			tree.setRoot(model.getRootFX());
			tree.getRoot().setExpanded(true);
			if (entrySelected != null)
				setSelectedEntry(tree, tree.getRoot(), entrySelected);
		});
	}

	
	/**
	 * Prompt the user to set a new name for a ProjectImageEntry.
	 * 
	 * @param entry
	 * @return true if the entry was changed, false otherwise.
	 */
	private boolean setProjectEntryImageName(final ProjectImageEntry<BufferedImage> entry) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			logger.error("Cannot set image name - project is null");
		}
		if (entry == null) {
			logger.error("Cannot set image name - entry is null");
		}
		
		String name = Dialogs.showInputDialog("Set Image Name", "Enter the new image name", entry.getImageName());
		if (name == null)
			return false;
		if (name.trim().isEmpty() || name.equals(entry.getImageName())) {
			logger.warn("Cannot set image name to {} - will ignore", name);
		}
		
		// Try to set the name
		boolean changed = setProjectEntryImageName(entry, name);
		if (changed) {
			tree.refresh();
			qupath.refreshTitle();
		}
		return changed;
	}
	
	
	/**
	 * The the name for a specified ProjectImageEntry.
	 * 
	 * This works hard to do its job... including renaming any data files accordingly.
	 * 
	 * @param entry
	 * @param name
	 * @return
	 */
	private synchronized static <T> boolean setProjectEntryImageName(final ProjectImageEntry<T> entry, final String name) {
		
		if (entry.getImageName().equals(name)) {
			logger.warn("Project image name already set to {} - will be left unchanged", name);
			return false;
		}

		if (name.equals(null)) {
			logger.warn("Project entry name cannot be null!");
			return false;
		}

		entry.setImageName(name);
		
		return true;
	}
	


	// I fully admit this is a horrible design - cobbled together from an earlier Swing version
	// TODO: Greatly improve the project tree display...
	static class ProjectImageTreeModel {

		private Project<?> project;
		private Map<String, List<ProjectImageEntry<?>>> map = new HashMap<>();
		private List<String> mapKeyList = new ArrayList<>();

		private List<String> sortMetadataKeys = new ArrayList<>();
		private List<String> sortDefaultKeys = new ArrayList<>();
		private String PROJECT_KEY;
		private String DEFAULT_ROOT = "No project";
		private String UNASSIGNED_NODE = "(Unassigned)";
		

		ProjectImageTreeModel(final Project<?> project) {
			this(project, null);
		}


		ProjectImageTreeModel(final Project<?> project, final String metadataKey) {
			this.project = project;
			if (project == null)
				PROJECT_KEY = "Unnamed project";
			else
				PROJECT_KEY = project.getName();
			if (metadataKey != null)
				setMetadataKeys(metadataKey);
			//			rebuildModel();
			sortDefaultKeys.add(URI);
		}

		public void setMetadataKeys(final String... metadataSortKeys) {
			sortMetadataKeys.clear();
			if (metadataSortKeys != null) {
				for (String key : metadataSortKeys) {
					if (key != null)
						sortMetadataKeys.add(key);
				}
			}
			rebuildModel();
		}

		public void resetMetadataKeys() {
			setMetadataKeys();
		}

		public void rebuildModel() {
			map.clear();
			mapKeyList.clear();
			if (project == null)
				return;

			// Populate the map
			String emptyKey = sortMetadataKeys.isEmpty() ? PROJECT_KEY : UNASSIGNED_NODE;
			var imageList = new ArrayList<>(project.getImageList());
			String userFilter = getUserFilter();
						
			for (ProjectImageEntry<?> entry : imageList) {
				boolean metadataMatchesFilter = false;
				String localKey = emptyKey;
				for (String sortingKey : sortMetadataKeys) {
					try {
						String temp;
						if (sortDefaultKeys.contains(sortingKey))
							temp = getDefaultValue(entry, sortingKey);
						else
							temp = entry.getMetadataValue(sortingKey);

						if (temp != null) {
							if (temp.toLowerCase().contains(userFilter))
								metadataMatchesFilter = true;
							localKey = temp;
							break;
						}
					} catch (IOException e) {
						logger.error(e.toString());
					}
				}

				
				List<ProjectImageEntry<?>> list = map.get(localKey);
				if (list == null) {
					list = new ArrayList<>();
					map.put(localKey, list);
				}
				// Filter out images that do not match the user's keywords or the entry's metadata
				if (entry.getImageName().toLowerCase().contains(userFilter) || metadataMatchesFilter)
					list.add(entry);
			}

			/*
			// Sort all the lists
			for (List<ProjectImageEntry<?>> list : map.values()) {
				list.sort(new Comparator<ProjectImageEntry<?>>() {
					@Override
					public int compare(ProjectImageEntry<?> o1, ProjectImageEntry<?> o2) {
						return o1.toString().compareTo(o2.toString());
					}
				});
			}
			*/

			// Populate the key list
			mapKeyList.addAll(map.keySet());
			Collections.sort(mapKeyList);
			// Ensure unassigned is at the end
			if (mapKeyList.remove(UNASSIGNED_NODE))
				mapKeyList.add(UNASSIGNED_NODE);
		}
		
		public boolean matchesUserFilter(String entryMetadata) {
			String userFilter = getUserFilter();
			if (userFilter == null || userFilter.equals("")) 
				return true;
			if (entryMetadata.toLowerCase().contains(userFilter))
				return true;
			return false;
		}
		
		
		/**
		 * This method should be used instead of calling Project.getImageList() 
		 * when we need the list of {@code ImageProjectEntry}s within a project  
		 * after having filtered out the entries with the key words provided by 
		 * the user.
		 * @return {@code List<ProjectImageEntry>}
		 */
		public List<ProjectImageEntry<?>> getImageList(){
			List<ProjectImageEntry<?>> out = 
				    map.values().stream()
				        .flatMap(List::stream)
				        .collect(Collectors.toList());
			return out;
		}


		public TreeItem<Object> getRootFX() {
			rebuildModel();
			
			// If we are masking the image names, we should also shuffle the entries
			boolean maskNames = PathPrefs.maskImageNamesProperty().get();
			
			TreeItem<Object> root = new TreeItem<>(getRoot());
			List<TreeItem<Object>> items = root.getChildren();
			if (project != null) {
				Random rand = new Random(project.hashCode());
				if (sortMetadataKeys.isEmpty()) {
					var imageList = getImageList();
					if (maskNames)
						Collections.shuffle(imageList, rand);
					for (ProjectImageEntry<?> entry : imageList)
						items.add(new TreeItem<>(entry));
				} else {
					for (String key : mapKeyList) {
						TreeItem<Object> item = new TreeItem<>(key);
						var imageList = map.get(key);
						if (maskNames)
							Collections.shuffle(imageList, rand);
						for (ProjectImageEntry<?> entry : imageList)
							item.getChildren().add(new TreeItem<>(entry));
						items.add(item);
					}
				}
			}
			return root;
		}



		public Object getRoot() {
			return project == null ? DEFAULT_ROOT : PROJECT_KEY;
		}

		public Object getChild(Object parent, int index) {
			//			if (parent == project && project != null)
			//				return project.getImageList().get(index);

			if (project == null)
				return null;
			if (parent == PROJECT_KEY && !sortMetadataKeys.isEmpty())
				return mapKeyList.get(index);
			List<ProjectImageEntry<?>> list = map.get(parent);
			return list.get(index);
		}

		public int getChildCount(Object parent) {
			if (project == null)
				return 0;
			if (parent == PROJECT_KEY && !sortMetadataKeys.isEmpty())
				return map.size();
			List<ProjectImageEntry<?>> list = map.get(parent);
			return list == null ? 0 : list.size();
		}

		public boolean isLeaf(Object node) {
			return node instanceof ProjectImageEntry;
		}

		public int getIndexOfChild(Object parent, Object child) {
			if (project == null)
				return -1;
			if (parent == PROJECT_KEY && !sortMetadataKeys.isEmpty())
				return mapKeyList.indexOf(child);
			List<ProjectImageEntry<?>> list = map.get(parent);
			return list == null ? -1 : list.indexOf(child);
		}

	}




	static enum ProjectThumbnailSize {
		SMALL, MEDIUM, LARGE;
		
		private double defaultHeight = 40;
		private double defaultWidth = 50;
		
		@Override
		public String toString() {
			switch(this) {
			case LARGE:
				return "Large";
			case MEDIUM:
				return "Medium";
			case SMALL:
				return "Small";
			default:
				return super.toString();
			}
		}
		
		public double getWidth() {
			switch(this) {
			case LARGE:
				return defaultWidth * 3.0;
			case MEDIUM:
				return defaultWidth * 2.0;
			case SMALL:
			default:
				return defaultWidth;
			}
		}
		
		public double getHeight() {
			switch(this) {
			case LARGE:
				return defaultHeight * 3.0;
			case MEDIUM:
				return defaultHeight * 2.0;
			case SMALL:
			default:
				return defaultHeight;
			}
		}
	}
	

	class ImageEntryCell extends TreeCell<Object> {

		final SimpleDateFormat dateFormat = new SimpleDateFormat();
		
		private Tooltip tooltip = new Tooltip();
		private StackPane label = new StackPane();
		private ImageView viewTooltip = new ImageView();
		private Canvas viewCanvas = new Canvas();
		
		private DoubleBinding viewWidth = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getWidth(),
				thumbnailSize);

		private DoubleBinding viewHeight = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getHeight(),
				thumbnailSize);

		public ImageEntryCell() {
			viewTooltip.setFitHeight(250);
			viewTooltip.setFitWidth(250);
			viewTooltip.setPreserveRatio(true);
			viewCanvas.widthProperty().bind(viewWidth);
			viewCanvas.heightProperty().bind(viewHeight);
			viewCanvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			label.getChildren().add(viewCanvas);
			label.prefWidthProperty().bind(viewCanvas.widthProperty());
			label.prefHeightProperty().bind(viewCanvas.heightProperty());
			
//			setOnDragDetected( event ->  {
//				if (isEmpty())
//					return;
//				Object item = getItem();
//				String path = null;
//				if (item instanceof ProjectImageEntry<?>)
//					path = ((ProjectImageEntry)item).getServerPath();
//				if (path == null)
//					return;
//				Dragboard db = startDragAndDrop(TransferMode.COPY);
//				ClipboardContent cc = new ClipboardContent();
//				cc.putString(path);
//				db.setContent(cc);
//				db.setDragView(snapshot(null, null));
//            });
		}

		@Override
		protected void updateItem(Object item, boolean empty) {
			super.updateItem(item, empty);

			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				setTooltip(null);
				return;
			}

			ProjectImageEntry<BufferedImage> entry = item instanceof ProjectImageEntry ? (ProjectImageEntry<BufferedImage>)item : null;
			if (isCurrentImage(entry))
				setStyle("-fx-font-weight: bold; -fx-font-family: arial");
			else if (entry == null || entry.hasImageData())
				setStyle("-fx-font-weight: normal; -fx-font-family: arial");
			else
				setStyle("-fx-font-style: italic; -fx-font-family: arial");
			
			if (entry == null) {
				setText(item.toString() + " (" + getTreeItem().getChildren().size() + ")");
				tooltip.setText(item.toString());
				setTooltip(tooltip);
				setGraphic(null);
			} else {
				// Set whatever tooltip we have
				tooltip.setGraphic(null);
				setTooltip(tooltip);

				setText(entry.getImageName());
				//	        	 String s = entry.toString();
				//	        	 File file = getImageDataPath(entry);
				//	        	 if (file != null && file.exists()) {
				//	        		 double sizeMB = file.length() / 1024.0 / 1024.0;
				//	        		 s = String.format("%s (%.2f MB)", s, sizeMB);
				//	        	 }

				tooltip.setText(entry.getSummary());
				//	        	 Tooltip tooltip = new Tooltip(sb.toString());

				BufferedImage img = null;
				try {
					img = (BufferedImage)entry.getThumbnail();
				} catch (Exception e) {
					logger.warn("Unable to read thumbnail for {} ({})" + entry.getImageName(), e.getLocalizedMessage());
				}
				
				if (img != null) {
					Image image = SwingFXUtils.toFXImage(img, null);
					viewTooltip.setImage(image);
					tooltip.setGraphic(viewTooltip);
					GuiTools.paintImage(viewCanvas, image);
					if (getGraphic() == null)
						setGraphic(label);
				} else {
					setGraphic(null);
				}
			}
			
		}
	}
}