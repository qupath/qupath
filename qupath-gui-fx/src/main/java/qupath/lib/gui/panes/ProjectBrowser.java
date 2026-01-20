/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.panes.ProjectTreeRow.ImageRow;
import qupath.lib.gui.panes.ProjectTreeRow.MetadataRow;
import qupath.lib.gui.panes.ProjectTreeRow.Type;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.io.UriUpdater;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Component for previewing and selecting images within a project.
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
public class ProjectBrowser implements ChangeListener<ImageData<BufferedImage>> {

	private static final Logger logger = LoggerFactory.getLogger(ProjectBrowser.class);
	private static final BooleanProperty keepDescriptionPaneOpenPref = PathPrefs.createPersistentPreference(
			"keepDescriptionOpen",
			false
	);
	private Project<BufferedImage> project;

	// Requested thumbnail max dimensions
	private int thumbnailWidth = 1000;
	private int thumbnailHeight = 600;

	private final QuPathGUI qupath;
	private final BorderPane panel;

	private ProjectImageTreeModel model = new ProjectImageTreeModel(null);
	private final TreeView<ProjectTreeRow> tree;
	private final PredicateTextField<String> tfFilter;

	 // Keep a record of servers that failed - don't want to keep putting in thumbnails requests if the server is unavailable.
	private final Set<ProjectTreeRow> serversFailed = Collections.synchronizedSet(new HashSet<>());

	private final StringProperty descriptionText = new SimpleStringProperty();

	// Predicate for filtering tree rows
	private final ObjectProperty<Predicate<ProjectImageEntry<?>>> predicateProperty = new SimpleObjectProperty<>(s -> true);

	private static final ObjectProperty<ProjectThumbnailSize> thumbnailSize = PathPrefs.createPersistentPreference("projectThumbnailSize",
			ProjectThumbnailSize.SMALL, ProjectThumbnailSize.class);
	
	// Record if the context menu is showing; this is to block a tooltip obscuring it
	private final BooleanProperty contextMenuShowing = new SimpleBooleanProperty();
	
	/**
	 * Metadata keys that will always be present
	 */
	private enum BaseMetadataKeys {
		IMAGE_NAME("Image name"), ENTRY_ID("Entry ID"), URI("URI");

		private final String displayName;

		BaseMetadataKeys(String displayName) {
			this.displayName = displayName;
		}

		String getDisplayName() {
			return displayName;
		}

		String getKey() {
			return "SORT_KEY[" + toString() + "]";
		}

	}
	private static final String UNASSIGNED_NODE = "(Unassigned)";
	private static final String UNDEFINED_VALUE = "Undefined";

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
		PathPrefs.skipProjectUriChecksProperty().addListener((v, o, n) -> tree.refresh());

		panel = new BorderPane();
		panel.getStyleClass().add("project-browser");

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

		Button editDescriptionButton = new Button(
				null,
				IconFactory.createNode(FontAwesome.Glyph.PENCIL, 13)
		);
		editDescriptionButton.setTooltip(new Tooltip("Edit description"));
		editDescriptionButton.setOnAction(e -> promptToEditSelectedImageDescription());
		editDescriptionButton.visibleProperty().bind(Bindings.createBooleanBinding(
				() -> tree.getSelectionModel().getSelectedItem() != null && tree.getSelectionModel().getSelectedItem().getValue().getType().equals(Type.IMAGE),
				tree.getSelectionModel().selectedItemProperty()
		));
		editDescriptionButton.managedProperty().bind(editDescriptionButton.visibleProperty());

		ToggleButton keepDescriptionOpenButton = new ToggleButton(
				null,
				IconFactory.createNode(FontAwesome.Glyph.THUMB_TACK, 13)
		);
		keepDescriptionOpenButton.selectedProperty().set(keepDescriptionPaneOpenPref.get());
		keepDescriptionOpenButton.selectedProperty().addListener((p, o, n) -> keepDescriptionPaneOpenPref.set(n));
		keepDescriptionOpenButton.setTooltip(new Tooltip("Keep description pane open"));
		keepDescriptionOpenButton.setPadding(new Insets(1,9,1,9));

		TitledPane textDescriptionContainer = GuiTools.createLeftRightTitledPane(
				"Description",
				new HBox(5, editDescriptionButton, keepDescriptionOpenButton)
		);
		TextArea textDescription = new TextArea();
		textDescription.textProperty().bind(descriptionText);
		textDescription.setWrapText(true);
		textDescription.setEditable(false);
		textDescriptionContainer.setContent(textDescription);

		MasterDetailPane mdTree = new MasterDetailPane(Side.BOTTOM, tree, textDescriptionContainer, false);
		mdTree.showDetailNodeProperty().bind(Bindings.createBooleanBinding(
				() -> keepDescriptionOpenButton.selectedProperty().get() || descriptionText.get() != null,
				keepDescriptionOpenButton.selectedProperty(), descriptionText
		));
		
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
		
		
		this.tfFilter = new PredicateTextField<String>();
		tfFilter.setPromptText("Search entry in project");
		tfFilter.setSpacing(0.0);
		var tooltip = new Tooltip("Type some text to filter the project entries by name or type.");
		Tooltip.install(tfFilter, tooltip);
		predicateProperty.bind(Bindings.createObjectBinding(() -> {
			if (tfFilter.useRegexProperty().get())
				return (ProjectImageEntry<?> e) -> e != null && tfFilter.predicateProperty().get().test(e.getImageName());
			else {
				return ProjectEntryPredicate.createIgnoreCase(tfFilter.getText());
			}
		}, tfFilter.useRegexProperty(), tfFilter.textProperty(), tfFilter.predicateProperty()));
		predicateProperty.addListener((m, o, n) -> refreshTree(null));
		
		var paneUserFilter = GridPaneUtils.createRowGrid(tfFilter);
		
		BorderPane panelTree = new BorderPane();
		panelTree.setCenter(titledTree);

		panel.setBottom(paneUserFilter);
		panel.setCenter(panelTree);

		Button btnOpen = ActionTools.createButton(qupath.getCommonActions().PROJECT_OPEN);
		Button btnCreate = ActionTools.createButton(qupath.getCommonActions().PROJECT_NEW);
		Button btnAdd = ActionTools.createButton(qupath.getCommonActions().PROJECT_ADD_IMAGES);
		GridPane paneButtons = GridPaneUtils.createColumnGridControls(btnCreate, btnOpen, btnAdd);
		paneButtons.prefWidthProperty().bind(panel.widthProperty());
		paneButtons.setPadding(new Insets(5, 5, 5, 5));
		panel.setTop(paneButtons);

		qupath.getPreferencePane().getPropertySheet().getItems().add(
				new PropertyItemBuilder<>(thumbnailSize, ProjectThumbnailSize.class)
						.propertyType(PropertyItemBuilder.PropertyType.CHOICE)
						.name("Project thumbnail size")
						.category("Appearance")
						.choices(Arrays.asList(ProjectThumbnailSize.values()))
						.description("Choose thumbnail size for the project pane")
						.build()
		);
	}

	ContextMenu getPopup() {
		
		Action actionOpenImage = new Action("Open image", e -> qupath.openImageEntry(getSelectedEntry()));
		Action actionRemoveImage = new Action("Remove image(s)", e -> promptToRemoveSelectedImages());
		
		Action actionDuplicateImages = new Action("Duplicate image(s)", e -> promptToDuplicateSelectedImages());
		
		Action actionSetImageName = new Action("Rename image", e -> promptToRenameSelectedImage());
		// Add a metadata value
		Action actionAddMetadataValue = new Action("Add metadata", e -> promptToAddMetadataToSelectedImages());
		
		// Edit the description for the image
		Action actionEditDescription = new Action("Edit description", e -> promptToEditSelectedImageDescription());
		
		// Mask the name of the images and shuffle the entry
		Action actionMaskImageNames = ActionTools.createSelectableAction(PathPrefs.maskImageNamesProperty(), "Mask image names");
		
		// Refresh thumbnail according to current display settings
		Action actionRefreshThumbnail = new Action("Refresh thumbnail", e -> promptToRefreshSelectedThumbnails());
				
		// Open the project directory using Explorer/Finder etc.
		Action actionOpenProjectDirectory = createBrowsePathAction("Project...", () -> getProjectPath());
		Action actionOpenProjectEntryDirectory = createBrowsePathAction("Project entry...", () -> getProjectEntryPath());
		Action actionOpenImageServerDirectory = createBrowsePathAction("Image...", () -> getImageServerPath());
		

		ContextMenu menu = new ContextMenu();
		
		var hasProjectBinding = qupath.projectProperty().isNotNull();
		var menuOpenDirectories = MenuTools.createMenu("Open directory...",
				actionOpenProjectDirectory,
				actionOpenProjectEntryDirectory,
				actionOpenImageServerDirectory);
		var separatorOpenDirectories = new SeparatorMenuItem();
		separatorOpenDirectories.visibleProperty().bind(menuOpenDirectories.visibleProperty());
        var separatorTop = new SeparatorMenuItem();

		MenuItem miOpenImage = ActionUtils.createMenuItem(actionOpenImage);
		MenuItem miRemoveImage = ActionUtils.createMenuItem(actionRemoveImage);
		MenuItem miDuplicateImage = ActionUtils.createMenuItem(actionDuplicateImages);
		MenuItem miSetImageName = ActionUtils.createMenuItem(actionSetImageName);
		MenuItem miRefreshThumbnail = ActionUtils.createMenuItem(actionRefreshThumbnail);
		MenuItem miEditDescription = ActionUtils.createMenuItem(actionEditDescription);
		MenuItem miAddMetadata = ActionUtils.createMenuItem(actionAddMetadataValue);
		MenuItem miMaskImages = ActionUtils.createCheckMenuItem(actionMaskImageNames);

		// Create menu for sorting by metadata
		Menu menuSort = new Menu("Sort by...");

		// Set visibility as menu being displayed
		menu.setOnShowing(e -> {
			TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
			ProjectImageEntry<BufferedImage> selectedEntry = selected == null ? null : ProjectTreeRow.getEntry(selected.getValue());
			var entries = getSelectedImageRowsRecursive();
			boolean isImageEntry = selectedEntry != null;

			populateSortByMenu(menuSort);
			
			int nSelectedEntries = ProjectTreeRow.getEntries(entries).size();
			if (nSelectedEntries == 1) {
				actionDuplicateImages.setText("Duplicate image");
				actionRemoveImage.setText("Remove image");
			} else {
				actionDuplicateImages.setText("Duplicate " + nSelectedEntries + " images");
				actionRemoveImage.setText("Remove " + nSelectedEntries + " images");				
			}

			miOpenImage.setVisible(isImageEntry);
			miDuplicateImage.setVisible(isImageEntry);
			miSetImageName.setVisible(isImageEntry);
			miAddMetadata.setVisible(!entries.isEmpty());
            separatorTop.setVisible(isImageEntry);
			miEditDescription.setVisible(isImageEntry);
			miRefreshThumbnail.setVisible(isImageEntry && isCurrentImage(selectedEntry));
			miRemoveImage.setVisible(selected != null && project != null && !project.getImageList().isEmpty());

			if (project == null) {
				menuSort.setVisible(false);
				return;
			}

			menuSort.setVisible(true);

			// Handle opening directories - requires Desktop
			menuOpenDirectories.setVisible(Desktop.isDesktopSupported() && hasProjectBinding.get());

			if (menu.getItems().isEmpty())
				e.consume();
		});
		
		SeparatorMenuItem separator = new SeparatorMenuItem();
		separator.visibleProperty().bind(menuSort.visibleProperty());

		menu.getItems().addAll(
				miOpenImage,
				miRemoveImage,
				miDuplicateImage,
                separatorTop,
				miSetImageName,
				miAddMetadata,
				miEditDescription,
				miMaskImages,
				createThumbnailSizeMenu(),
				miRefreshThumbnail,
				separator,
				menuSort,
				separatorOpenDirectories,
				menuOpenDirectories
				);

		contextMenuShowing.bind(menu.showingProperty());
		
		return menu;
	}

	private Menu createThumbnailSizeMenu() {
		Menu menu = new Menu("Thumbnail size");
		ToggleGroup group = new ToggleGroup();
		for (ProjectThumbnailSize size : ProjectThumbnailSize.values()) {
			RadioMenuItem item = new RadioMenuItem(size.toString());
			item.setOnAction(e -> thumbnailSize.set(size));
			item.setUserData(size);
			menu.getItems().add(item);
			group.getToggles().add(item);
		}
		thumbnailSize.addListener((v, o, n) -> syncToggleGroupByUserData(group, n));
		syncToggleGroupByUserData(group, thumbnailSize.get());
		return menu;
	}

	private void syncToggleGroupByUserData(ToggleGroup group, Object userData) {
		for (var toggle : group.getToggles()) {
			if (Objects.equals(toggle.getUserData(), userData)) {
				group.selectToggle(toggle);
				return;
			}
		}
	}


	private void promptToEditSelectedImageDescription() {
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
	}


	private void promptToRemoveSelectedImages() {
		Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
		Collection<ProjectImageEntry<BufferedImage>> entries = ProjectTreeRow.getEntries(imageRows);

		if (entries.isEmpty())
			return;

		// Don't allow us to remove any entries that are currently open (in any viewer)
		for (var viewer : qupath.getAllViewers()) {
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
		if (result == ButtonType.CANCEL)
			return;

		project.removeAllImages(entries, result == ButtonType.YES);
		refreshTree(null);
		syncProject(project);
		if (tree != null) {
			boolean isExpanded = tree.getRoot() != null && tree.getRoot().isExpanded();
			tree.setRoot(model.getRoot());
			tree.getRoot().setExpanded(isExpanded);
		}
	}


	private void promptToRefreshSelectedThumbnails() {
		TreeItem<ProjectTreeRow> path = tree.getSelectionModel().getSelectedItem();
		if (path == null)
			return;
		if (path.getValue().getType() == ProjectTreeRow.Type.IMAGE) {
			ProjectImageEntry<BufferedImage> entry = ProjectTreeRow.getEntry(path.getValue());
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
	}


	private void promptToDuplicateSelectedImages() {
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

		if (!GuiTools.showParameterDialog(title, params))
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
            logger.error("Error synchronizing project changes: {}", ex.getMessage(), ex);
		}
		refreshProject();
		if (imageRows.size() == 1)
			logger.debug("Duplicated 1 image entry");
		else
			logger.debug("Duplicated {} image entries", imageRows.size());
	}


	private void promptToRenameSelectedImage() {
		TreeItem<ProjectTreeRow> path = tree.getSelectionModel().getSelectedItem();
		if (path == null)
			return;
		if (path.getValue().getType() == ProjectTreeRow.Type.IMAGE) {
			if (setProjectEntryImageName(ProjectTreeRow.getEntry(path.getValue())) && project != null)
				syncProject(project);
		}
	}


	private void promptToAddMetadataToSelectedImages() {
		Project<BufferedImage> project = getProject();
		Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
		if (project != null && !imageRows.isEmpty()) {
			TextField tfMetadataKey = new TextField();
			var suggestions = project.getImageList().stream()
					.map(p -> p.getMetadata().keySet())
					.flatMap(Collection::stream)
					.distinct()
					.sorted()
					.toList();
			TextFields.bindAutoCompletion(tfMetadataKey, suggestions);

			TextField tfMetadataValue = new TextField();
			Label labKey = new Label("New key");
			Label labValue = new Label("New value");
			labKey.setLabelFor(tfMetadataKey);
			labValue.setLabelFor(tfMetadataValue);
			tfMetadataKey.setTooltip(new Tooltip("Enter the name for the metadata entry"));
			tfMetadataValue.setTooltip(new Tooltip("Enter the value for the metadata entry"));

			ProjectImageEntry<BufferedImage> entry = imageRows.size() == 1 ? ProjectTreeRow.getEntry(imageRows.iterator().next()) : null;
			int nMetadataValues = entry == null ? 0 : entry.getMetadata().size();

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

			ImageRow selectedImageRow = getSelectedImageRow();
			refreshTree(selectedImageRow);

		} else {
			Dialogs.showErrorMessage("Edit image description", "No entry is selected!");
		}
	}


	/**
	 * Populate the 'Sort by...' menu, recreating values if necessary
	 * @param menuSort
	 * @return
	 */
	private Menu populateSortByMenu(Menu menuSort) {
		Map<String, MenuItem> newItems = new TreeMap<>();
		if (project != null) {
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				// Add all entry metadata keys
				for (String key : entry.getMetadata().keySet()) {
					if (!newItems.containsKey(key)) {
						var item = ActionUtils.createMenuItem(createSortByKeyAction(key, key));
						item.setMnemonicParsing(false);
						newItems.put(key, item);
					}
				}
			}
		}
		menuSort.getItems().setAll(newItems.values());

		// Add all additional keys
		for (var key : BaseMetadataKeys.values()) {
			if (!newItems.containsKey(key.getKey()))
				menuSort.getItems().add(ActionUtils.createMenuItem(createSortByKeyAction(key.getDisplayName(), key.getKey())));
		}

		menuSort.getItems().add(0, ActionUtils.createMenuItem(createSortByKeyAction("None", null)));
		menuSort.getItems().add(1, new SeparatorMenuItem());

		return menuSort;
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
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	static boolean showDescriptionEditor(ProjectImageEntry<?> entry) {
		TextArea editor = new TextArea();
		editor.setWrapText(true);
		editor.setPromptText(String.format("Enter description for %s", entry.getImageName()));
		editor.setText(entry.getDescription());
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setTitle("Image description");
		dialog.getDialogPane().setHeaderText(entry.getImageName());
		dialog.getDialogPane().setContent(editor);
		Platform.runLater(editor::requestFocus);
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
		ProjectTreeRowCell.resetUriStatus();
		tfFilter.textProperty().set("");
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
			Platform.runLater(this::refreshProject);
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
                    logger.error("Unable to save ImageData: {}", e.getMessage(), e);
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
		if (key.equals(BaseMetadataKeys.URI.getKey())) {
			var URIs = entry.getURIs();
			var it = URIs.iterator();
			
			if (URIs.isEmpty())
				return UNDEFINED_VALUE;
			
			if (URIs.size() == 1) {
				URI uri = it.next();
				String fullURI = uri.getPath();
				if (uri.getAuthority() != null)
					return "[remote] " + uri.getAuthority() + fullURI;
				return fullURI.substring(fullURI.lastIndexOf("/")+1);
			}
			return "Multiple URIs";
		} else if (key.equals(BaseMetadataKeys.IMAGE_NAME.getKey())) {
			return entry.getImageName();
		}  else if (key.equals(BaseMetadataKeys.ENTRY_ID.getKey())) {
			return entry.getID();
		}
		var value = entry.getMetadata().get(key);
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
				// When filtering (imageToSelect is null), expand all matching group nodes
				if (imageToSelect == null) {
					for (var child : listOfChildren) {
						if (!child.getChildren().isEmpty()) {
							child.setExpanded(true);
						}
					}
				} else {
					// If a specific image is requested, expand only its parent group and select it
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
			} catch (Exception ex) {
				logger.error("Error getting children objects in the ProjectBrowser", ex);
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
			for (var viewer : qupath.getAllViewers()) {
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
	 * The name for a specified ProjectImageEntry.
	 * This works hard to do its job... including renaming any data files accordingly.
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
			return project.getImageList().stream().map(entry -> new ImageRow(entry)).toList();
		
		// If 'mask names' is ticked, shuffle the image list for less biased analyses
		var imageList = project.getImageList();
		var indices = IntStream.range(0, imageList.size()).boxed().collect(Collectors.toCollection(ArrayList::new));
		Collections.shuffle(indices);
		return indices.stream().map(index -> new ImageRow(imageList.get(index))).toList();
	}

	private class ProjectImageTreeModel {

		private static final String SORT_KEY = "_SORT_KEY";
		private final ProjectTreeRowItem root;
		private String metadataKey;
		
		private ProjectImageTreeModel(final Project<?> project) {
			this.root = new ProjectTreeRowItem(new ProjectTreeRow.RootRow(project));

			if (project != null) {
				this.metadataKey = project.getMetadata().get(SORT_KEY);
			}
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

			if (metadataKey == null) {
				project.getMetadata().remove(SORT_KEY);
			} else {
				project.getMetadata().put(SORT_KEY, metadataKey);
			}
		}
		
		private ProjectTreeRowItem getRoot() {
			return root;
		}
	}

	private class ProjectTreeRowCell extends TreeCell<ProjectTreeRow> {
		
		private final Tooltip tooltip = new Tooltip();

		private Node missingGraphic;

		private final StackPane viewPane = new StackPane();
		private final Canvas viewCanvas = new Canvas();
		private final ImageView viewTooltip = new ImageView();

		private ProjectTreeRow objectCell = null;
		private final BooleanProperty showTooltip = new SimpleBooleanProperty();

		private final BooleanProperty urisMissing = new SimpleBooleanProperty(false);

		/**
		 * Cache whether or not URIs refer to missing files.
		 * We want to be able to inform the user when files are missing, but we don't want to call Files.exists()
		 * too often, so we retain the result.
		 * This means that, if the file was deleted or moved later, the user will need to refresh the project to see
		 * the change.
		 */
		private static final Map<URI, UriUpdater.UriStatus> uriStatus = new ConcurrentHashMap<>();

		/**
		 * Reset the cache of URI statuses (called when a new project is opened).
		 */
		static void resetUriStatus() {
			uriStatus.clear();
		}

		private final DoubleBinding viewWidth = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getWidth(),
				thumbnailSize);

		private final DoubleBinding viewHeight = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getHeight(),
				thumbnailSize);
		
		private ProjectTreeRowCell() {
			viewTooltip.setFitHeight(250);
			viewTooltip.setFitWidth(250);
			viewTooltip.setPreserveRatio(true);
			viewCanvas.getStyleClass().add("project-thumbnail");
			viewCanvas.widthProperty().bind(viewWidth);
			viewCanvas.heightProperty().bind(viewHeight);
			viewPane.getChildren().add(viewCanvas);
			viewPane.prefWidthProperty().bind(viewCanvas.widthProperty());
			viewPane.prefHeightProperty().bind(viewCanvas.heightProperty());
			viewCanvas.opacityProperty().bind(
					Bindings.createDoubleBinding(() -> urisMissing.get() ? 0.2 : 1.0, urisMissing));

			missingGraphic = IconFactory.createNode(
					15, 15, PathIcons.WARNING);
			missingGraphic.getStyleClass().add("missing-uri");
			Tooltip.install(missingGraphic, new Tooltip("File not found"));

			viewPane.getChildren().add(missingGraphic);
			missingGraphic.visibleProperty().bind(urisMissing);

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

			getStyleClass().setAll("tree-cell");
			urisMissing.set(false);

			if (item.getType() == ProjectTreeRow.Type.ROOT) {
				var children = getTreeItem().getChildren();
				setText(item.getDisplayableString() + (!children.isEmpty() ? " (" + children.size() + ")" : ""));
				setGraphic(null);
				return;
			} else if (item.getType() == ProjectTreeRow.Type.METADATA) {
				var children = getTreeItem().getChildren();
				// TODO: Try not to display count when grouping by ID
				setText(item.getDisplayableString() + (!children.isEmpty() ? " (" + children.size() + ")" : ""));
				setGraphic(null);
				return;
			}
			
			// IMAGE
			ProjectImageEntry<BufferedImage> entry = item.getType() == ProjectTreeRow.Type.IMAGE ? ProjectTreeRow.getEntry(item) : null;
			if (isCurrentImage(entry))
				getStyleClass().add("current-image");
			if (entry != null && !entry.hasImageData())
				getStyleClass().add("no-saved-data");

			// Check for URIs
			if (entry != null && !PathPrefs.skipProjectUriChecksProperty().get()) {
				try {
					for (var uri : entry.getURIs()) {
						if (uriStatus.computeIfAbsent(uri, ProjectTreeRowCell::checkUri) == UriUpdater.UriStatus.MISSING) {
							urisMissing.set(true);
							break;
						}
					}
				} catch (IOException e) {
					logger.error("Exception checking URIs: {}", e.getMessage(), e);
				}
			}

			if (entry == null) {
				setText(item + " (" + getTreeItem().getChildren().size() + ")");
				tooltip.setText(item.toString());
                showTooltip.set(true);
				setGraphic(null);
			} else {
				setGraphic(viewPane);
				// Set whatever tooltip we have
				tooltip.setGraphic(null);
				showTooltip.set(true);

				setText(entry.getImageName());
				if (urisMissing.get())
					tooltip.setText("Warning: At least one file is missing!\n\n" + entry.getSummary());
				else
					tooltip.setText(entry.getSummary());

				if (thumbnailSize.get() == ProjectThumbnailSize.HIDDEN) {
					viewTooltip.setImage(null);
					viewCanvas.getGraphicsContext2D().clearRect(0, 0, viewCanvas.getWidth(), viewCanvas.getHeight());
				} else {
					try {
						// Fetch the thumbnail or generate it if not present
						BufferedImage img = entry.getThumbnail();
						if (img != null) {
							Image image = SwingFXUtils.toFXImage(img, null);
							viewTooltip.setImage(image);
							tooltip.setGraphic(viewTooltip);
							GuiTools.paintImage(viewCanvas, image);
							objectCell = item;
							if (getGraphic() == null)
								setGraphic(viewPane);
						} else if (!serversFailed.contains(item)) {
							tooltip.setGraphic(viewTooltip);
							viewCanvas.getGraphicsContext2D().clearRect(0, 0, viewCanvas.getWidth(), viewCanvas.getHeight());
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
											logger.warn("Error opening ImageServer (thumbnail generation): {}", ex.getLocalizedMessage(), ex);
											Platform.runLater(() -> setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER)));
											serversFailed.add(item);
										}
									}
								} catch (IOException ex) {
									logger.warn("Error getting thumbnail: {}", ex.getLocalizedMessage());
									Platform.runLater(() -> setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER)));
									serversFailed.add(item);
								}
							});
						} else
							setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER));
					} catch (Exception e) {
						setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER));
						logger.warn("Unable to read thumbnail for {} ({})", entry.getImageName(), e.getMessage());
						serversFailed.add(item);
					}
				}
			}
		}


		private static UriUpdater.UriStatus checkUri(URI uri) {
			var path = GeneralTools.toPath(uri);
			// In case the check is slow, we make it possible for the user to turn it off.
			// See also https://github.com/qupath/qupath/pull/1298 for performance considerations.
			// TODO: Can we check if this is a network drive, to skip the test?
			if (path == null)
				return UriUpdater.UriStatus.UNKNOWN;
			else if (Files.notExists(path))
				return UriUpdater.UriStatus.MISSING;
			else
				return UriUpdater.UriStatus.EXISTS;
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
				return super.getChildren().isEmpty();

            return switch (getValue().getType()) {
                case ROOT -> project != null && !project.getImageList().isEmpty() && project.getImageList().stream()
                        .noneMatch(entry -> predicateProperty.get().test(entry));
                case METADATA -> false;
                case IMAGE -> true;
                default ->
                        throw new IllegalArgumentException("Could not understand the type of the object: " + getValue().getType());
            };
			
		}
		
		@Override
		public ObservableList<TreeItem<ProjectTreeRow>> getChildren() {
			if (!isLeaf() && !computed) {
				ObservableList<TreeItem<ProjectTreeRow>> children = FXCollections.observableArrayList();
				var filter = predicateProperty.get();
				var metadataKey = model.getMetadataKey();
				switch (getValue().getType()) {
				case ROOT:
					if (project == null)
						break;
					
					if (metadataKey == null) {
						for (var row: getAllImageRows()) {
							if (!filter.test(row.getEntry()))
								continue;
							children.add(new ProjectTreeRowItem(row));
						}
					} else {
						var values = new ArrayList<>(getAllMetadataValues(metadataKey));
						GeneralTools.smartStringSort(values);
						var potentialChildren = values.stream()
								.map(value -> new ProjectTreeRowItem(new MetadataRow(value)))
								.toList();
						// When sorting by name, we don't want to show grouped by name - since it looks weird,
						// with the name effectively being repeated twice
						if (metadataKey.equals(BaseMetadataKeys.IMAGE_NAME.getKey()))
							potentialChildren = potentialChildren.stream().flatMap(c -> {
								if (c.isLeaf())
									return Stream.empty();
								else
									return c.getChildren().stream();
							}).map(t -> (ProjectTreeRowItem)t).toList();
						// When sorting by entry ID, we want to expand everything - since there should only be one
						// entry per ID
						if (metadataKey.equals(BaseMetadataKeys.ENTRY_ID.getKey()))
							potentialChildren.forEach(c -> c.setExpanded(true));

						children.addAll(potentialChildren);
					}
					break;
				case METADATA:
					if (metadataKey == null || metadataKey.isEmpty())		// This should never happen
						break;
					
					for (var row: getAllImageRows()) {
						if (!filter.test(row.getEntry()))
							continue;
						try {
							var value = getDefaultValue(ProjectTreeRow.getEntry(row), metadataKey);
							if (value != null && value.equals(((MetadataRow)getValue()).getDisplayableString()))
								children.add(new ProjectTreeRowItem(row));
						} catch (IOException ex) {
							logger.warn("Could not get {} from {}", metadataKey, row.getDisplayableString(), ex);
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

	enum ProjectThumbnailSize {
		HIDDEN, SMALL, MEDIUM, LARGE;

		private static final int hiddenSize = 20;

		private final double defaultHeight = 40;
		private final double defaultWidth = 50;
		
		@Override
		public String toString() {
            return switch (this) {
                case HIDDEN -> "Hidden";
                case LARGE -> "Large";
                case MEDIUM -> "Medium";
                case SMALL -> "Small";
                default -> super.toString();
            };
		}
		
		public double getWidth() {
            return switch (this) {
                case LARGE -> defaultWidth * 3.0;
                case MEDIUM -> defaultWidth * 2.0;
                case HIDDEN -> hiddenSize;
                default -> defaultWidth;
            };
		}
		
		public double getHeight() {
            return switch (this) {
                case LARGE -> defaultHeight * 3.0;
                case MEDIUM -> defaultHeight * 2.0;
                case HIDDEN -> hiddenSize;
                default -> defaultHeight;
            };
		}
	}
}
