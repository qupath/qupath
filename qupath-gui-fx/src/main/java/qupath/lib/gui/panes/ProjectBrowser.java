/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.panes;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.textfield.TextFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.panes.ProjectTreeRow.ImageRow;
import qupath.lib.gui.panes.ProjectTreeRow.MetadataRow;
import qupath.lib.gui.panes.ProjectTreeRow.Type;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Component for previewing and selecting images within a project.
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
public class ProjectBrowser implements ChangeListener<ImageData<BufferedImage>> {

	private static final Logger logger = LoggerFactory.getLogger(ProjectBrowser.class);

	private Project<BufferedImage> project;

	// Requested thumbnail max dimensions
	private int thumbnailWidth = 1000;
	private int thumbnailHeight = 600;

	private QuPathGUI qupath;
	private BorderPane panel;

	private ProjectImageTreeModel model = new ProjectImageTreeModel(null);
	private TreeView<ProjectTreeRow> tree;

	 // Keep a record of servers that failed- don't want to keep putting in thumbnails requests if the server is unavailable.
	private Set<ProjectTreeRow> serversFailed = Collections.synchronizedSet(new HashSet<>());
	
	private StringProperty descriptionText = new SimpleStringProperty();
	
	private static TextField tfFilter;

	private static ObjectProperty<ProjectThumbnailSize> thumbnailSize = PathPrefs.createPersistentPreference("projectThumbnailSize",
			ProjectThumbnailSize.SMALL, ProjectThumbnailSize.class);
	
	// Record if the context menu is showing; this is to block a tooltip obscuring it
	private BooleanProperty contextMenuShowing = new SimpleBooleanProperty();
	
	/**
	 * Metadata keys that will always be present
	 */
	private static final String URI = "URI";
	private static final String UNASSIGNED_NODE = "(Unassigned)";
	private static final String UNDEFINED_VALUE = "Undefined";
	private static String[] baseMetadataKeys = new String[] {URI};
	
	/**
	 * To load thumbnails in the background
	 */
	private static ExecutorService executor;

	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public ProjectBrowser(final QuPathGUI qupath) {
		this.project = qupath.getProject();
		this.qupath = qupath;
		this.tree = new TreeView<>();

		qupath.imageDataProperty().addListener(this);
		
		// Get thumbnails in separate thread
		executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));
		
		PathPrefs.maskImageNamesProperty().addListener((v, o, n) -> refreshTree(null));

		panel = new BorderPane();

		tree.setCellFactory(n -> new ProjectTreeRowCell());
		
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
			if (n != null && n.getValue().getType() == ProjectTreeRow.Type.IMAGE)
				descriptionText.set(ProjectTreeRow.getEntry(n.getValue()).getDescription());
			else
				descriptionText.set(null);
		});

		TitledPane titledTree = new TitledPane("Image list", mdTree);
		titledTree.setCollapsible(false);
		titledTree.setMaxHeight(Double.MAX_VALUE);
		
		
		tfFilter = new TextField();
		tfFilter.setPromptText("Search entry in project");
		tfFilter.setTooltip(new Tooltip("Type some text to filter the project entries by name or type."));
		tfFilter.textProperty().addListener((m, o, n) -> refreshTree(null));
		
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

	}

	ContextMenu getPopup() {
		
		Action actionOpenImage = new Action("Open image", e -> qupath.openImageEntry(getSelectedEntry()));
		Action actionRemoveImage = new Action("Remove image(s)", e -> {
			Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
			Collection<ProjectImageEntry<BufferedImage>> entries = ProjectTreeRow.getEntries(imageRows);
			
			if (entries.isEmpty())
				return;
			
			// Don't allow us to remove any entries that are currently open (in any viewer)
			for (var viewer : qupath.getViewers()) {
				var imageData = viewer.getImageData();
				var entry = imageData == null ? null : getProject().getEntry(imageData);
				if (entry != null && entries.contains(entry)) {
					Dialogs.showErrorMessage("Remove project entries", "Please close all images you want to remove!");
					return;
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
			refreshTree(null);
			syncProject(project);
			if (tree != null) {
				boolean isExpanded = tree.getRoot() != null && tree.getRoot().isExpanded();
				tree.setRoot(model.getRoot());
				tree.getRoot().setExpanded(isExpanded);
			}
		});
		
		Action actionDuplicateImages = new Action("Duplicate image(s)", e -> {
			Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
			if (imageRows.isEmpty()) {
				logger.debug("Nothing to duplicate - no entries selected");
				return;
			}
			
			boolean singleImage = false;
			String name = "";
			String title = "Duplicate images";
			String namePrompt = "Append to image name";
			String nameHelp = "Specify text to append to the image name to distinguish duplicated images";
			if (imageRows.size() == 1) {
				title = "Duplicate image";
				namePrompt = "Duplicate image name";
				nameHelp = "Specify name for the duplicated image";
				singleImage = true;
				name = imageRows.iterator().next().getDisplayableString();
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
			
			for (var imageRow : imageRows) {
				try {
					var newEntry = project.addDuplicate(ProjectTreeRow.getEntry(imageRow), copyData);
					if (newEntry != null && !name.isBlank()) {
						if (singleImage)
							newEntry.setImageName(name);
						else
							newEntry.setImageName(newEntry.getImageName() + name);
					}
				} catch (Exception ex) {
					Dialogs.showErrorNotification("Duplicating image", "Error duplicating " + ProjectTreeRow.getEntry(imageRow).getImageName());
					logger.error(ex.getLocalizedMessage(), ex);
				}
			}
			try {
				project.syncChanges();
			} catch (Exception ex) {
				logger.error("Error synchronizing project changes: " + ex.getLocalizedMessage(), ex);
			}
			refreshProject();
			if (imageRows.size() == 1)
				logger.debug("Duplicated 1 image entry");
			else
				logger.debug("Duplicated {} image entries");
		});
		
		Action actionSetImageName = new Action("Rename image", e -> {
			TreeItem<ProjectTreeRow> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue().getType() == ProjectTreeRow.Type.IMAGE) {
				if (setProjectEntryImageName(ProjectTreeRow.getEntry(path.getValue())) && project != null)
					syncProject(project);
			}
		});
		// Add a metadata value
		Action actionAddMetadataValue = new Action("Add metadata", e -> {
			Project<BufferedImage> project = getProject();
			Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
			if (project != null && !imageRows.isEmpty()) {
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
				
				ProjectImageEntry<BufferedImage> entry = imageRows.size() == 1 ? ProjectTreeRow.getEntry(imageRows.iterator().next()) : null;
				int nMetadataValues = entry == null ? 0 : entry.getMetadataKeys().size();
				
				GridPane pane = new GridPane();
				pane.setVgap(5);
				pane.setHgap(5);
				pane.add(labKey, 0, 0);
				pane.add(tfMetadataKey, 1, 0);
				pane.add(labValue, 0, 1);
				pane.add(tfMetadataValue, 1, 1);
				String name = imageRows.size() + " images";
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
						for (var temp : imageRows)
							ProjectTreeRow.getEntry(temp).putMetadataValue(key, value);
						syncProject(project);
						tree.refresh();
					}
				}
							
			} else {
				Dialogs.showErrorMessage("Edit image description", "No entry is selected!");
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
		
		// Mask the name of the images and shuffle the entry
		Action actionMaskImageNames = ActionTools.createSelectableAction(PathPrefs.maskImageNamesProperty(), "Mask image names");
		
		// Refresh thumbnail according to current display settings
		Action actionRefreshThumbnail = new Action("Refresh thumbnail", e -> {
			TreeItem<ProjectTreeRow> path = tree.getSelectionModel().getSelectedItem();
			if (path == null)
				return;
			if (path.getValue().getType() == ProjectTreeRow.Type.IMAGE) {
				ProjectImageEntry<BufferedImage> entry =ProjectTreeRow.getEntry(path.getValue());
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
				
		// Open the project directory using Explorer/Finder etc.
		Action actionOpenProjectDirectory = createBrowsePathAction("Project...", () -> getProjectPath());
		Action actionOpenProjectEntryDirectory = createBrowsePathAction("Project entry...", () -> getProjectEntryPath());
		Action actionOpenImageServerDirectory = createBrowsePathAction("Image...", () -> getImageServerPath());
		

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
		MenuItem miMaskImages = ActionUtils.createCheckMenuItem(actionMaskImageNames);
		
		
		// Set visibility as menu being displayed
		menu.setOnShowing(e -> {
			TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
			ProjectImageEntry<BufferedImage> selectedEntry = selected == null ? null : ProjectTreeRow.getEntry(selected.getValue());
			var entries = getSelectedImageRowsRecursive();
			boolean isImageEntry = selectedEntry != null;
			
			int nSelectedEntries = ProjectTreeRow.getEntries(entries).size();
			if (nSelectedEntries == 1) {
				actionDuplicateImages.setText("Duplicate image");
				actionRemoveImage.setText("Remove image");
			} else {
				actionDuplicateImages.setText("Duplicate " + nSelectedEntries + " images");
				actionRemoveImage.setText("Remove " + nSelectedEntries + " images");				
			}
			
//			miOpenProjectDirectory.setVisible(project != null && project.getBaseDirectory().exists());
			miOpenImage.setVisible(isImageEntry);
			miDuplicateImage.setVisible(isImageEntry);
			miSetImageName.setVisible(isImageEntry);
			miAddMetadata.setVisible(!entries.isEmpty());
			miEditDescription.setVisible(isImageEntry);
			miRefreshThumbnail.setVisible(isImageEntry && isCurrentImage(selectedEntry));
			miRemoveImage.setVisible(selected != null && project != null && !project.getImageList().isEmpty());
			
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
				for (String key : baseMetadataKeys) {
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
				miMaskImages,
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
		
		contextMenuShowing.bind(menu.showingProperty());
		
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
		if (item.getType() == Type.IMAGE)
			return ProjectTreeRow.getEntry(item).getEntryPath();
		return null;
	}
	
	Path getImageServerPath() {
		var selected = tree.getSelectionModel().getSelectedItem();
		if (selected == null)
			return null;
		var item = selected.getValue();
		if (item.getType() == Type.IMAGE) {
			try {
				var uris = ProjectTreeRow.getEntry(item).getURIs();
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
	
	static boolean showDescriptionEditor(ProjectImageEntry<?> entry) {
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
	 * @return true if the project is now set (even if unchanged), false if the project change was thwarted or cancelled.
	 */
	public boolean setProject(final Project<BufferedImage> project) {
		if (this.project == project)
			return true;		
		
		this.project = project;
		model = new ProjectImageTreeModel(project);
		tree.setRoot(model.getRoot());
		tree.getRoot().setExpanded(true);
		Platform.runLater(() -> tree.getParent().layout());
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
		refreshTree(null);
	}

	private void ensureServerInWorkspace(final ImageData<BufferedImage> imageData) {
		if (imageData == null || project == null)
			return;
		
		if (project.getEntry(imageData) != null)
			return;

		var entry = ProjectCommands.addSingleImageToProject(project, imageData.getServer(), null);
		if (entry != null) {
			boolean expanded = tree.getRoot() != null && tree.getRoot().isExpanded();
			tree.setRoot(model.getRoot());
			setSelectedEntry(tree, tree.getRoot(), new ImageRow(project.getEntry(imageData)));
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
			qupath.refreshProject();
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
		} else if (!entry.equals(getSelectedEntry()))
			setSelectedEntry(tree, tree.getRoot(), getSelectedImageRow());
		if (tree != null) {
			tree.refresh();
		}
	}

	private static <T> boolean setSelectedEntry(TreeView<T> treeView, TreeItem<T> item, final T object) {
		if (item.getValue() == object) {
			treeView.getSelectionModel().select(item);
			return true;
		}
		for (TreeItem<T> child : item.getChildren()) {
			if (setSelectedEntry(treeView, child, object))
				return true;
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
	private BufferedImage resizeForThumbnail(BufferedImage imgThumbnail) {
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

	private ImageData<BufferedImage> getCurrentImageData() {
		return qupath.getViewer().getImageData();
	}

//	File getBaseDirectory() {
//		return Projects.getBaseDirectory(project);
//	}
//
//	File getProjectFile() {
//		File dirBase = getBaseDirectory();
//		if (dirBase == null || !dirBase.isDirectory())
//			return null;
//		return new File(dirBase, "project" + ProjectIO.getProjectExtension());
//	}

	private boolean isCurrentImage(final ProjectImageEntry<BufferedImage> entry) {
		ImageData<BufferedImage> imageData = getCurrentImageData();
		if (imageData == null || entry == null || project == null)
			return false;
		return project.getEntry(imageData) == entry;
	}
	
	/**
	 * Get all the {@link ProjectTreeRow.ImageRow}s included in the current selection. 
	 * This means that selecting a {@link ProjectTreeRow.MetadataRow} will return all the {@link ProjectTreeRow.ImageRow}s that belong to it.
	 * @return a collection of ImageRows
	 * @see #getSelectedImageRow()
	 */
	private Collection<ImageRow> getSelectedImageRowsRecursive() {
		List<TreeItem<ProjectTreeRow>> selected = tree.getSelectionModel().getSelectedItems();
		if (selected == null)
			return Collections.emptyList();
		return selected.stream().map(p -> {
			if (p.getValue().getType() == ProjectTreeRow.Type.IMAGE)
				return Collections.singletonList((ImageRow)p.getValue());
			return getImageRowsRecursive(p, null);
		}).flatMap(Collection::stream).collect(Collectors.toSet());
	}
	
	private ProjectImageEntry<BufferedImage> getSelectedEntry() {
		TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue().getType() == ProjectTreeRow.Type.IMAGE)
			return ((ImageRow)selected.getValue()).getEntry();
		return null;
	}
	
	/**
	 * Get the selected {@link ProjectTreeRow.ImageRow} and return it. 
	 * If nothing is selected or the selected {@link ProjectTreeRow} is not an image entry, return {@code null}.
	 * @return selected ImageRow
	 */
	private ImageRow getSelectedImageRow() {
		TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue().getType() == ProjectTreeRow.Type.IMAGE)
			return (ImageRow)selected.getValue();
		return null;
	}

	/**
	 * Get all {@code ImageRow} objects under the specified {@code item}.
	 * <p>
	 * E.g. If supplied with a {@link ProjectTreeRow.MetadataRow}, a collection of 
	 * all {@code ImageRow}s under it will be returned. If supplied with 
	 * a {@link ProjectTreeRow.RootRow}, a collection of all {@code ImageRow}s 
	 * under it will be returned (ignoring the {@link ProjectTreeRow.MetadataRow}s
	 * @param item the start node
	 * @param entries collection where to store the ImageRows found
	 * @return a collection of ImageRows
	 */
	private static Collection<ImageRow> getImageRowsRecursive(final TreeItem<ProjectTreeRow> item, Collection<ImageRow> entries) {
		if (entries == null)
			entries = new HashSet<>();
		if (item.getValue().getType() == ProjectTreeRow.Type.IMAGE)
			entries.add((ImageRow)item.getValue());
		for (TreeItem<ProjectTreeRow> child : item.getChildren()) {
			entries = getImageRowsRecursive(child, entries);
		}
		return entries;
	}
	
	/**
	 * Get all the distinct entry metadata values possible for a given key.
	 * @param metadataKey
	 * @return set of distinct metadata values
	 */
	private Set<String> getAllMetadataValues(String metadataKey) {
		return project.getImageList().stream()
				.map(entry -> {
					try {
						return getDefaultValue(entry, metadataKey);
					} catch (IOException ex) {
						// Could only happen because of call to getURIs()
						logger.warn("Could not get the URI(s) of " + entry.getImageName(), ex.getLocalizedMessage());
					}
					return UNDEFINED_VALUE;
				})
				.collect(Collectors.toSet());
	}
	
	/**
	 * Gets the value of the entry for the specified key.
	 * E.g. if key == URI, the value returned will be the entry's URI.
	 * This method should be used to get sorting values that
	 * are not specifically part of an entry's metadata.
	 * @param <T>
	 * @param entry 
	 * @param key
	 * @return value
	 * @throws IOException 
	 */
	private static <T> String getDefaultValue(ProjectImageEntry<T> entry, String key) throws IOException {
		if (key.equals(URI)) {
			var URIs = entry.getURIs();
			var it = URIs.iterator();
			
			if (URIs.size() == 0)
				return UNDEFINED_VALUE;
			
			if (URIs.size() == 1) {
				URI uri = it.next();
				String fullURI = uri.getPath();
				if (uri.getAuthority() != null)
					return "[remote] " + uri.getAuthority() + fullURI;
				return fullURI.substring(fullURI.lastIndexOf("/")+1, fullURI.length());
			}
			return "Multiple URIs";
		}
		var value = entry.getMetadataValue(key);
		return value == null ? UNASSIGNED_NODE : value;
	}
	
	/**
	 * This method rebuilds the tree, optionally selecting an {@link ImageRow} afterwards.
	 * @param imageToSelect image to select after refreshing
	 */
	private void refreshTree(ImageRow imageToSelect) {
		Platform.runLater(() -> {
			tree.setRoot(null);
			tree.setRoot(new ProjectTreeRowItem(new ProjectTreeRow.RootRow(project)));
			tree.getRoot().setExpanded(true);
			
			try {
				var listOfChildren = tree.getRoot().getChildren();
				for (int i = 0; i < listOfChildren.size(); i++) {
					if (imageToSelect == null) {
						if (listOfChildren.get(i).getChildren().size() > 0) {
							listOfChildren.get(i).setExpanded(true);
							tree.refresh();
							break;
						}							
					} else {
						for (var child: listOfChildren) {
							if (child.getValue().getType() == Type.METADATA) {
								for (var imageChild: child.getChildren()) {
									if (imageChild.getValue().equals(imageToSelect)) {
										child.setExpanded(true);
										tree.getSelectionModel().select(imageChild);
										break;
									}
								}
							} else if (child.getValue().equals(imageToSelect))
								tree.getSelectionModel().select(child);
						}
					}
				}
			} catch (Exception ex) {
				logger.error("Error getting children objects in the ProjectBrowser", ex.getLocalizedMessage());
			}
		});
	}

	private Action createSortByKeyAction(final String name, final String key) {
		return new Action(name, e -> {
			if (model == null)
				return;
			model.setMetadataKey(key);
			ImageRow selectedImageRow = getSelectedImageRow();
			refreshTree(selectedImageRow);
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
			return false;
		}
		if (entry == null) {
			logger.error("Cannot set image name - entry is null");
			return false;
		}
		
		String name = Dialogs.showInputDialog("Set Image Name", "Enter the new image name", entry.getImageName());
		if (name == null)
			return false;
		
		if (name.trim().isEmpty() || name.equals(entry.getImageName())) {
			logger.warn("Cannot set image name to {} - will ignore", name);
			return false;
		}
		
		// Try to set the name
		boolean changed = setProjectEntryImageName(entry, name);
		if (changed) {
			for (var viewer : qupath.getViewers()) {
				var imageData = viewer.getImageData();
				if (imageData == null)
					continue;
				var currentEntry = project.getEntry(imageData);
				if (Objects.equals(entry, currentEntry)) {
					var server = imageData.getServer();
					if (!name.equals(server.getMetadata().getName())) {
						// We update via the ImageData so that a property update is fired
						var metadata2 = new ImageServerMetadata.Builder(server.getMetadata())
								.name(name)
								.build();
						imageData.updateServerMetadata(metadata2);
						// Bit of a cheat to force measurement table updates
						imageData.getHierarchy().fireHierarchyChangedEvent(this);
					}
				}
			}
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
	private static synchronized <T> boolean setProjectEntryImageName(final ProjectImageEntry<T> entry, final String name) {
		
		if (entry.getImageName().equals(name)) {
			logger.warn("Project image name already set to {} - will be left unchanged", name);
			return false;
		}

		if (name == null) {
			logger.warn("Project entry name cannot be null!");
			return false;
		}

		entry.setImageName(name);
		
		return true;
	}
	
	private List<ImageRow> getAllImageRows() {
		if (!PathPrefs.maskImageNamesProperty().get())
			return project.getImageList().stream().map(entry -> new ImageRow(entry)).collect(Collectors.toList());
		
		// If 'mask names' is ticked, shuffle the image list for less biased analyses
		var imageList = project.getImageList();
		var indices = IntStream.range(0, imageList.size()).boxed().collect(Collectors.toList());
		Collections.shuffle(indices);
		return indices.stream().map(index -> new ImageRow(imageList.get(index))).collect(Collectors.toList());
	}

	private class ProjectImageTreeModel {
		
		private ProjectTreeRowItem root;
		private String metadataKey;
		
		private ProjectImageTreeModel(final Project<?> project) {
			this.root = new ProjectTreeRowItem(new ProjectTreeRow.RootRow(project));
		}
		
		private String getMetadataKey() {
			return metadataKey;
		}
		
		/**
		 * Set the metadata key based on which the entries will be sorted.
		 * @param metadataKey
		 */
		private void setMetadataKey(String metadataKey) {
			this.metadataKey = metadataKey;
		}
		
		private ProjectTreeRowItem getRoot() {
			return root;
		}
	}

	private class ProjectTreeRowCell extends TreeCell<ProjectTreeRow> {
		
		private Tooltip tooltip = new Tooltip();
		private StackPane label = new StackPane();
		private ImageView viewTooltip = new ImageView();
		private Canvas viewCanvas = new Canvas();
		private ProjectTreeRow objectCell = null;
		private BooleanProperty showTooltip = new SimpleBooleanProperty();
		
		private DoubleBinding viewWidth = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getWidth(),
				thumbnailSize);

		private DoubleBinding viewHeight = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getHeight(),
				thumbnailSize);
		
		private ProjectTreeRowCell() {
			viewTooltip.setFitHeight(250);
			viewTooltip.setFitWidth(250);
			viewTooltip.setPreserveRatio(true);
			viewCanvas.widthProperty().bind(viewWidth);
			viewCanvas.heightProperty().bind(viewHeight);
			viewCanvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			label.getChildren().add(viewCanvas);
			label.prefWidthProperty().bind(viewCanvas.widthProperty());
			label.prefHeightProperty().bind(viewCanvas.heightProperty());
			// Avoid having the tooltip obscure any popup menu
			tooltipProperty().bind(Bindings.createObjectBinding(() -> {
				return showTooltip.get() && !contextMenuShowing.get() ? tooltip : null;
			}, contextMenuShowing, showTooltip));
		}
		
		@Override
		public void updateItem(ProjectTreeRow item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || item == null) {
                setText(null);
                setGraphic(null);
                showTooltip.set(false);
                return;
            }

			if (item.getType() == ProjectTreeRow.Type.ROOT) {
				var children = getTreeItem().getChildren();
				setText(item.getDisplayableString() + (children.size() > 0 ? " (" + children.size() + ")" : ""));
				setGraphic(null);
				setStyle("-fx-font-weight: normal; -fx-font-family: arial");
				return;
			} else if (item.getType() == ProjectTreeRow.Type.METADATA) {
				var children = getTreeItem().getChildren();
				setText(item.getDisplayableString() + (children.size() > 0 ? " (" + children.size() + ")" : ""));
				setGraphic(null);
				setStyle("-fx-font-weight: normal; -fx-font-family: arial");
				return;
			}
			
			// IMAGE
			ProjectImageEntry<BufferedImage> entry = item.getType() == ProjectTreeRow.Type.IMAGE ? ProjectTreeRow.getEntry(item) : null;
			if (isCurrentImage(entry))
				setStyle("-fx-font-weight: bold; -fx-font-family: arial");
			else if (entry == null || entry.hasImageData())
				setStyle("-fx-font-weight: normal; -fx-font-family: arial");
			else
				setStyle("-fx-font-style: italic; -fx-font-family: arial");
			
			if (entry == null) {
				setText(item.toString() + " (" + getTreeItem().getChildren().size() + ")");
				tooltip.setText(item.toString());
                showTooltip.set(true);
				setGraphic(null);
			} else {
				setGraphic(null);
				// Set whatever tooltip we have
				tooltip.setGraphic(null);
                showTooltip.set(true);

				setText(entry.getImageName());
				tooltip.setText(entry.getSummary());

				try {
					// Fetch the thumbnail or generate it if not present
					BufferedImage img = entry.getThumbnail();
					if (img != null) {
						// If the cell contains the same object, no need to repaint the graphic
						if (objectCell == item && getGraphic() != null)
							return;
						
						Image image = SwingFXUtils.toFXImage(img, null);
						viewTooltip.setImage(image);
						tooltip.setGraphic(viewTooltip);
						GuiTools.paintImage(viewCanvas, image);
						objectCell = item;
						if (getGraphic() == null)
							setGraphic(label);
					} else if (!serversFailed.contains(item)) {
						executor.submit(() -> {
							final ProjectTreeRow objectTemp = getItem();
							final ProjectImageEntry<BufferedImage> entryTemp = ProjectTreeRow.getEntry(objectTemp);
							try {
								if (entryTemp != null && objectCell != objectTemp && entryTemp.getThumbnail() == null) {
									try (ImageServer<BufferedImage> server = entryTemp.getServerBuilder().build()) {
										entryTemp.setThumbnail(ProjectCommands.getThumbnailRGB(server));
										objectCell = objectTemp;
										tree.refresh();
									} catch (Exception ex) {
										logger.warn("Error opening ImageServer (thumbnail generation): " + ex.getLocalizedMessage(), ex);
										Platform.runLater(() -> setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER)));
										serversFailed.add(item);
									}
								}
							} catch (IOException ex) {
								logger.warn("Error getting thumbnail: " + ex.getLocalizedMessage());
								Platform.runLater(() -> setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER)));
								serversFailed.add(item);
							}
						});
					} else
						setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER));
				} catch (Exception e) {
					setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER));
					logger.warn("Unable to read thumbnail for {} ({})" + entry.getImageName(), e.getLocalizedMessage());
					serversFailed.add(item);
				}
			}
		}
	}
		
	/**
	 * TreeItem to help with the display of project objects.
	 */
	private class ProjectTreeRowItem extends TreeItem<ProjectTreeRow> {
		
		private boolean computed = false;
		
		private ProjectTreeRowItem(ProjectTreeRow obj) {
			super(obj);
		}

		@Override
		public boolean isLeaf() {
			if (computed)
				return super.getChildren().size() == 0;
		
			switch(getValue().getType()) {
				case ROOT:
					return project != null && project.getImageList().size() > 0 && !project.getImageList().stream()
										.filter(entry -> entry.getImageName().toLowerCase().contains(tfFilter.getText().toLowerCase()))
										.findAny()
										.isPresent();
				case METADATA:
					return false;
				case IMAGE:
					return true;
				default:
					throw new IllegalArgumentException("Could not understand the type of the object: " + getValue().getType());
			}
			
		}
		
		@Override
		public ObservableList<TreeItem<ProjectTreeRow>> getChildren() {
			if (!isLeaf() && !computed) {
				ObservableList<TreeItem<ProjectTreeRow>> children = FXCollections.observableArrayList();
				var filter = tfFilter.getText().toLowerCase();
				var metadataKey = model.getMetadataKey();
				switch (getValue().getType()) {
				case ROOT:
					if (project == null)
						break;
					
					if (metadataKey == null) {
						for (var row: getAllImageRows()) {
							if (filter != null && !filter.isEmpty() && !row.getDisplayableString().toLowerCase().contains(filter))
								continue;
							children.add(new ProjectTreeRowItem(row));
						}
					} else {
						var values = new ArrayList<>(getAllMetadataValues(metadataKey));
						GeneralTools.smartStringSort(values);
						children.addAll(values.stream()
								.map(value -> new ProjectTreeRowItem(new MetadataRow(value)))
								.collect(Collectors.toList()));
					}
					break;
				case METADATA:
					if (metadataKey == null || metadataKey.isEmpty())		// This should never happen
						break;
					
					for (var row: getAllImageRows()) {
						if (filter != null && !filter.isEmpty() && !row.getDisplayableString().toLowerCase().contains(filter))
							continue;
						try {
							var value = getDefaultValue(ProjectTreeRow.getEntry(row), metadataKey);
							if (value != null && value.equals(((MetadataRow)getValue()).getDisplayableString()))
								children.add(new ProjectTreeRowItem(row));
						} catch (IOException ex) {
							logger.warn("Could not get URIs from: " + row.getDisplayableString(), ex.getLocalizedMessage());
						}
					}
				case IMAGE:
					break;
				default:
					throw new IllegalArgumentException("Could not understand the type of the object: " + getValue().getType());
				}
				computed = true;
				super.getChildren().setAll(children);
			}
			return super.getChildren();
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
}