/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.shape.Rectangle;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

/**
 * Component used to display and edit available {@linkplain PathClass PathClasses}.
 * 
 * @author Pete Bankhead
 */
class PathClassPane {
	
	private static final Logger logger = LoggerFactory.getLogger(PathClassPane.class);
	
	private static final KeyCombination copyCombo = new KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN);
	private static final KeyCombination pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCodeCombination.SHORTCUT_DOWN);
	
	private final QuPathGUI qupath;
	
	private final ObservableList<PathClass> availablePathClasses;
	
	private final Pane pane;
	private ListView<PathClass> listClasses;

	/**
	 * If set, request that new annotations have their classification set automatically
	 */
	private final BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);
	
	PathClassPane(QuPathGUI qupath) {
		this.qupath = qupath;
		this.availablePathClasses = qupath.getAvailablePathClasses();
		var mainPane = createClassPane();
		var titled = GuiTools.createLeftRightTitledPane("Class list", createTitleNode());
		titled.setContent(mainPane);
		mainPane.setPadding(Insets.EMPTY);

		titled.setContentDisplay(ContentDisplay.RIGHT);
		pane = new BorderPane(titled);

		// Refresh when visibilities change
		var options = qupath.getOverlayOptions();
		InvalidationListener refresher = o -> listClasses.refresh();
		options.selectedClassVisibilityModeProperty().addListener(refresher);
		options.useExactSelectedClassesProperty().addListener(refresher);
		options.selectedClassesProperty().addListener(refresher);
	}



	private Pane createTitleNode() {
		int iconSize = 8;

		Action addNewAction = new Action(e -> promptToAddClass());
		addNewAction.setGraphic(IconFactory.createNode(FontAwesome.Glyph.PLUS, iconSize));
		addNewAction.setLongText("Add a new class to the list");

		Action removeSelected = new Action(e -> promptToRemoveSelectedClasses());
		removeSelected.setGraphic(IconFactory.createNode(FontAwesome.Glyph.MINUS, iconSize));
		removeSelected.disabledProperty().bind(Bindings.createBooleanBinding(() ->
				listClasses.getSelectionModel().getSelectedItems().stream().noneMatch(p -> p != PathClass.NULL_CLASS),
				listClasses.getSelectionModel().getSelectedItems()));
		removeSelected.setLongText("Remove the selected classes from the list (this does not change or remove objects)");

		Button btnAdd = ActionUtils.createButton(addNewAction);
		Button btnRemove = ActionUtils.createButton(removeSelected);

		var btnMore = GuiTools.createMoreButton(createClassesMenu(), Side.RIGHT);
		btnMore.setText(null);
		btnMore.setGraphic(IconFactory.createNode(FontAwesome.Glyph.CARET_RIGHT, 12));

		var spacer = new Pane();
		spacer.setPrefWidth(4.0);

		return new HBox(btnAdd, btnRemove, btnMore);
	}

	
	private Pane createClassPane() {
		listClasses = new ListView<>();

		var filteredList = availablePathClasses.filtered(p -> true);
		listClasses.setItems(filteredList);

		listClasses.getSelectionModel().selectedItemProperty()
				.addListener((v, o, n) -> updateAutoSetPathClassProperty());
		
		listClasses.setCellFactory(v -> new PathClassListCell(qupath));

		listClasses.getSelectionModel().select(0);
		listClasses.setPrefSize(100, 200);
		
		listClasses.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
				
		// Intercept space presses because we handle them elsewhere
		listClasses.addEventFilter(KeyEvent.KEY_PRESSED, this::filterKeyPresses);
		listClasses.setOnMouseClicked(e -> {
			if (!e.isPopupTrigger() && e.getClickCount() == 2) {
				if (promptToEditSelectedClass()) {
					// This fires a change event to notify any listeners
					availablePathClasses.setAll(availablePathClasses.stream().toList());
				}
			}
		});

		ContextMenu popup = createSelectedMenu();
		listClasses.setContextMenu(popup);
		
		// Add the class list
		BorderPane paneClasses = new BorderPane();
		paneClasses.setCenter(listClasses);

		Action setSelectedObjectClassAction = new Action("Set selected", e -> promptToSetClass());
		setSelectedObjectClassAction.setLongText("Set the class of the currently-selected objects");

		Action autoClassifyAnnotationsAction = new Action("Auto set");
		autoClassifyAnnotationsAction.setLongText("Automatically set all new annotations to the selected class");
		autoClassifyAnnotationsAction.selectedProperty().bindBidirectional(doAutoSetPathClass);
		
		doAutoSetPathClass.addListener((e, f, g) -> updateAutoSetPathClassProperty());

		Button btnSetClass = ActionUtils.createButton(setSelectedObjectClassAction);
		ToggleButton btnAutoClass = ActionUtils.createToggleButton(autoClassifyAnnotationsAction);

		// Context menu button to work with selected objects & classifications
		Button btnMoreObjects = GuiTools.createMoreButton(createSelectedMenu(), Side.RIGHT);

		GridPane paneClassButtons = new GridPane();
		var col1 = new ColumnConstraints();
		var col2 = new ColumnConstraints();
		col1.setPercentWidth(50.0);
		col2.setPercentWidth(50.0);
		paneClassButtons.getColumnConstraints().setAll(col1, col2);

		var paneMore = new VBox(btnMoreObjects);

		paneClassButtons.add(btnSetClass, 0, 1);
		paneClassButtons.add(btnAutoClass, 1, 1);

		var filter = new PredicateTextField<PathClass>();
		filter.setPromptText("Filter classes");
		filter.setIgnoreCase(true);
		filter.setUseRegex(true);
		Tooltip.install(filter, new Tooltip("Type to filter classes in list"));

		filteredList.predicateProperty().bind(filter.predicateProperty());

		var paneBottom = new BorderPane(paneClassButtons);
		paneBottom.setTop(filter);
		paneBottom.setRight(paneMore);

		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, btnSetClass, btnAutoClass);
		GridPaneUtils.setMaxWidth(Double.MAX_VALUE, btnSetClass, btnAutoClass, filter);

		var comboSelectToShow = new ChoiceBox<OverlayOptions.ClassVisibilityMode>();
		comboSelectToShow.setConverter(new ClassVisibilityConverter());
		comboSelectToShow.getItems().setAll(OverlayOptions.ClassVisibilityMode.values());
		comboSelectToShow.setMaxWidth(Double.MAX_VALUE);

		var btnMoreVisible = GuiTools.createMoreButton(createVisbilityMenu(), Side.RIGHT);
		var paneTop = new BorderPane(comboSelectToShow);
		paneTop.setRight(btnMoreVisible);
		comboSelectToShow.valueProperty()
				.bindBidirectional(getOverlayOptions().selectedClassVisibilityModeProperty());
		comboSelectToShow.getSelectionModel().selectFirst();

		paneClasses.setTop(paneTop);
		paneClasses.setBottom(paneBottom);
		return paneClasses;
	}

	private static class ClassVisibilityConverter extends StringConverter<OverlayOptions.ClassVisibilityMode> {

		@Override
		public String toString(OverlayOptions.ClassVisibilityMode object) {
			return switch (object) {
				case HIDE_SELECTED -> "Show by default";
				case SHOW_SELECTED -> "Hide by default";
			};
		}

		@Override
		public OverlayOptions.ClassVisibilityMode fromString(String string) {
			return switch (string) {
				case "Show by default" -> OverlayOptions.ClassVisibilityMode.HIDE_SELECTED;
				case "Hide by default" -> OverlayOptions.ClassVisibilityMode.SHOW_SELECTED;
				default -> null;
			};
		}
	}
	
	
	private void promptToSetClass() {
		var hierarchy = getHierarchy();
		if (hierarchy == null)
			return;
		PathClass pathClass = getSelectedPathClass();
		if (pathClass == PathClass.NULL_CLASS)
			pathClass = null;
		var pathObjects = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		List<PathObject> changed = new ArrayList<>();
		for (PathObject pathObject : pathObjects) {
			// Previously we didn't allow TMA core objects to be classified this way,
			// but since v0.6.0 we do
			if (pathObject.getPathClass() == pathClass)
				continue;				
			pathObject.setPathClass(pathClass);
			changed.add(pathObject);
		}
		if (!changed.isEmpty()) {
			hierarchy.fireObjectClassificationsChangedEvent(this, changed);
			GuiTools.refreshList(listClasses);
		}
	}
	
	
	private void filterKeyPresses(KeyEvent e) {
		if (e.isConsumed())
			return;
		if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.T) {
			toggleSelectedClassesVisibility();
			e.consume();
			return;
		} else if (e.getCode() == KeyCode.S) {
			setSelectedClassesVisibility(true);
			e.consume();
			return;
		} else if (e.getCode() == KeyCode.H) {
			setSelectedClassesVisibility(false);
			e.consume();
			return;
		} else if (e.getCode() == KeyCode.BACK_SPACE) {
			promptToRemoveSelectedClasses();
			e.consume();
			return;
		} else if (e.getCode() == KeyCode.ENTER) {
			promptToEditSelectedClass();
			e.consume();
			return;
		} else if (copyCombo.match(e)) {
			// Copy the list if needed
			String s = listClasses.getSelectionModel().getSelectedItems()
					.stream().map(PathClass::toString)
					.collect(Collectors.joining(System.lineSeparator()));
			if (!s.isBlank()) {
				Clipboard.getSystemClipboard().setContent(
						Map.of(DataFormat.PLAIN_TEXT, s));
			}
			e.consume();
			return;
		} else if (pasteCombo.match(e)) {
			logger.debug("Paste not implemented for class list!");
			e.consume();
			return;
		}
	}
	

	private Action createResetToDefaultClassesAction() {
		return new Action("Reset to default classes", e -> promptToResetClasses());
	}
	
	private Action createImportClassesFromProjectAction() {
		return new Action("Import classes from project", e -> promptToImportClasses());
	}
	
	private BooleanBinding createRemoveClassDisabledBinding() {
		return Bindings.createBooleanBinding(() -> {
					PathClass item = listClasses.getSelectionModel().getSelectedItem();
					return item == null || PathClass.NULL_CLASS == item;
				},
				listClasses.getSelectionModel().selectedItemProperty()
		);
	}

	
	private ContextMenu createClassesMenu() {
		ContextMenu menu = new ContextMenu();
		
		Action actionResetClasses = createResetToDefaultClassesAction();
		Action actionImportClasses = createImportClassesFromProjectAction();

		MenuItem miResetAllClasses = ActionUtils.createMenuItem(actionResetClasses);

		MenuItem miPopulateFromImage = new MenuItem("All classes (including sub-classes)");
		miPopulateFromImage.setOnAction(e -> promptToPopulateFromImage(false));
		MenuItem miPopulateFromImageBase = new MenuItem("Base classes only");
		miPopulateFromImageBase.setOnAction(e -> promptToPopulateFromImage(true));
		
		MenuItem miPopulateFromChannels = new MenuItem("Populate from image channels");
		miPopulateFromChannels.setOnAction(e -> promptToPopulateFromChannels());

		Menu menuPopulate = new Menu("Populate from existing objects");
		menuPopulate.getItems().addAll(
				miPopulateFromImage, miPopulateFromImageBase);
		
		menu.setOnShowing(e -> {
			var hierarchy = getHierarchy();
			menuPopulate.setDisable(hierarchy == null);
			miPopulateFromImage.setDisable(hierarchy == null);
			miPopulateFromImageBase.setDisable(hierarchy == null);
			miPopulateFromChannels.setDisable(qupath.getImageData() == null);
		});
		
		MenuItem miImportFromProject = ActionUtils.createMenuItem(actionImportClasses);
		
		menu.getItems().addAll(
				menuPopulate,
				miPopulateFromChannels,
				miResetAllClasses,
				miImportFromProject
		);
		
		return menu;
	}

	private ContextMenu createVisbilityMenu() {
		ContextMenu menu = new ContextMenu();

		CheckMenuItem miSelectExact = new CheckMenuItem("Show/hide exact class matches only");
		miSelectExact.selectedProperty().bindBidirectional(getOverlayOptions().useExactSelectedClassesProperty());

		MenuItem miResetClassVisibility = new MenuItem("Reset selected classes");
		miResetClassVisibility.setOnAction(e -> resetSelectedClassVisibility());

		MenuItem miRestoreClassVisibilityDefaults = new MenuItem("Restore class visibility to default settings");
		miRestoreClassVisibilityDefaults.setOnAction(e -> restoreClassVisibilityDefaults());

		menu.getItems().addAll(
				miSelectExact,
				new SeparatorMenuItem(),
				miResetClassVisibility,
				miRestoreClassVisibilityDefaults
		);
		return menu;
	}


	private void resetSelectedClassVisibility() {
		var options = getOverlayOptions();
		options.selectedClassesProperty().clear();
	}

	private void restoreClassVisibilityDefaults() {
		var options = getOverlayOptions();
		options.setSelectedClassVisibilityMode(OverlayOptions.ClassVisibilityMode.HIDE_SELECTED);
		options.setUseExactSelectedClasses(false);
		options.selectedClassesProperty().clear();
	}

	private ContextMenu createSelectedMenu() {
		ContextMenu menu = new ContextMenu();

		MenuItem miSelectObjects = new MenuItem("Select objects by classification");
		miSelectObjects.disableProperty().bind(Bindings.createBooleanBinding(
				() -> {
					var item = listClasses.getSelectionModel().getSelectedItem();
					return item == null && getHierarchy() != null;
				},
				listClasses.getSelectionModel().selectedItemProperty(),
				qupath.imageDataProperty()));

		miSelectObjects.setOnAction(e -> {
			var imageData = qupath.getImageData();
			if (imageData == null)
				return;
			Commands.selectObjectsByClassification(imageData, getSelectedPathClasses().toArray(PathClass[]::new));
		});


		menu.getItems().addAll(
				miSelectObjects);

		return menu;
	}

	
	
	/**
	 * Update pane to reflect the current status.
	 */
	public void refresh() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::refresh);
			return;
		}
		listClasses.refresh();
	}
	
	private OverlayOptions getOverlayOptions() {
		return qupath.getOverlayOptions();
	}
	
	private void toggleSelectedClassesVisibility() {
		OverlayOptions overlayOptions = getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
			overlayOptions.setPathClassHidden(pathClass, !overlayOptions.selectedClassesProperty().contains(pathClass));
		}
		listClasses.refresh();
	}
	
	
	private void setSelectedClassesVisibility(boolean visible) {
		OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
			overlayOptions.setPathClassHidden(pathClass, !visible);
		}
		listClasses.refresh();
	}
	
	private void updateAutoSetPathClassProperty() {
		PathClass pathClass = null;
		if (doAutoSetPathClass.get()) {
			pathClass = getSelectedPathClass();
		}
		if (pathClass == null || pathClass == PathClass.NULL_CLASS)
			PathPrefs.autoSetAnnotationClassProperty().set(null);
		else
			PathPrefs.autoSetAnnotationClassProperty().set(pathClass);
	}
	
	private PathObjectHierarchy getHierarchy() {
		var imageData = qupath.getImageData();
		return imageData == null ? null : imageData.getHierarchy();
	}
	
	/**
	 * Prompt to populate available class list from the channels of the current {@link ImageServer}.
	 * @return true if the class list was changed, false otherwise.
	 */
	private boolean promptToPopulateFromChannels() {
		var imageData = qupath.getImageData();
		if (imageData == null)
			return false;
		
		var server = imageData.getServer();
		List<PathClass> newClasses = new ArrayList<>();
		for (var channel : server.getMetadata().getChannels()) {
			newClasses.add(PathClass.fromString(channel.getName(), channel.getColor()));
		}
		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No channels found, somehow!");
			return false;
		}
		
		List<PathClass> currentClasses = new ArrayList<>(availablePathClasses);
		currentClasses.remove(null);
		if (currentClasses.equals(newClasses)) {
			Dialogs.showInfoNotification("Set available classes", "Class lists are the same - no changes to make!");
			return false;
		}
		// Always need to be able to ignore...
		newClasses.add(PathClass.StandardPathClasses.IGNORE);
		
		var btn = ButtonType.YES;
		if (availablePathClasses.size() > 1)
			btn = Dialogs.showYesNoCancelDialog("Set available classes", "Keep existing available classes?");
		if (btn == ButtonType.YES) {
			newClasses.removeAll(availablePathClasses);
			return availablePathClasses.addAll(newClasses);
		} else if (btn == ButtonType.NO) {
			newClasses.addFirst(PathClass.NULL_CLASS);
			return availablePathClasses.setAll(newClasses);
		} else
			return false;
	}
	
	
	/**
	 * Prompt to populate available class list from the current image.
	 * @param baseClassesOnly 
	 * @return true if the class list was changed, false otherwise.
	 */
	private boolean promptToPopulateFromImage(boolean baseClassesOnly) {
		var hierarchy = getHierarchy();
		if (hierarchy == null)
			return false;

		List<PathClass> newClasses = hierarchy.getFlattenedObjectList(null)
				.stream()
				.filter(p -> !p.isRootObject())
				.map(PathObject::getPathClass)
				.filter(p -> p != null && p != PathClass.NULL_CLASS)
				.map(p -> baseClassesOnly ? p.getBaseClass() : p)
				.distinct()
				.sorted()
				.collect(Collectors.toCollection(ArrayList::new));

		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No classes found in current image!");
			return false;
		}
		
		newClasses.add(PathClass.StandardPathClasses.IGNORE);
		
		List<PathClass> currentClasses = new ArrayList<>(availablePathClasses);
		currentClasses.remove(null);
		if (currentClasses.equals(newClasses)) {
			Dialogs.showInfoNotification("Set available classes", "Class lists are the same - no changes to make!");
			return false;
		}
		
		var btn = ButtonType.YES;
		if (availablePathClasses.size() > 1)
			btn = Dialogs.showYesNoCancelDialog("Set available classes", "Keep existing available classes?");
		if (btn == ButtonType.YES) {
			newClasses.removeAll(availablePathClasses);
			return availablePathClasses.addAll(newClasses);
		} else if (btn == ButtonType.NO) {
			newClasses.addFirst(PathClass.NULL_CLASS);
			return availablePathClasses.setAll(newClasses);
		} else
			return false;
	}
	
	/**
	 * Prompt to import available class list from another project.
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToImportClasses() {
		File file = FileChoosers.promptForFile("Import classifications",
				FileChoosers.createExtensionFilter("QuPath project", ProjectIO.getProjectExtension()));
		if (file == null)
			return false;
		if (!file.getAbsolutePath().toLowerCase().endsWith(ProjectIO.getProjectExtension())) {
			Dialogs.showErrorMessage("Import PathClasses", file.getName() + " is not a project file!");
			return false;
		}
		try {
			Project<?> project = ProjectIO.loadProject(file, BufferedImage.class);
			List<PathClass> pathClasses = project.getPathClasses();
			if (pathClasses.isEmpty()) {
				Dialogs.showErrorMessage("Import PathClasses", "No classes found in " + file.getName());
				return false;
			}
			if (pathClasses.size() == availablePathClasses.size() && availablePathClasses.containsAll(pathClasses)) {
				Dialogs.showInfoNotification("Import PathClasses", file.getName() + " contains same classes - no changes to make");
				return false;
			}
			availablePathClasses.setAll(pathClasses);
			return true;
		} catch (Exception ex) {
			Dialogs.showErrorMessage("Error reading project", ex);
			logger.error(ex.getMessage(), ex);
			return false;
		}
	}
	
	
	/**
	 * Prompt to reset classifications to the default list.
	 * @return true if the class list was changed, false otherwise
	 */
	private boolean promptToResetClasses() {
		if (Dialogs.showConfirmDialog("Reset classes", "Reset all available classes?")) {
			return qupath.resetAvailablePathClasses();
		}
		return false;
	}
	
	
	/**
	 * Prompt to add a new classification.
	 * @return true if a new classification was added, false otherwise
	 */
	private boolean promptToAddClass() {
		String input = Dialogs.showInputDialog("Add class", "Class name", "");
		if (input == null || input.trim().isEmpty())
			return false;
		PathClass pathClass = PathClass.fromString(input);
		if (availablePathClasses.contains(pathClass)) {
			Dialogs.showErrorMessage("Add class", "Class '" + input + "' already exists!");
			return false;
		} else if (input.equalsIgnoreCase("null")) {
			Dialogs.showErrorMessage("Add class", "Cannot add a 'null' class, try another name!");
			return false;
		}
		availablePathClasses.add(pathClass);
		listClasses.getSelectionModel().clearAndSelect(listClasses.getItems().size()-1);
		return true;
	}
	
	/**
	 * Prompt to edit the selected classification.
	 * @return true if changes were made, false otherwise
	 */
	private boolean promptToEditSelectedClass() {
		PathClass pathClassSelected = getSelectedPathClass();
		if (Commands.promptToEditClass(pathClassSelected)) {
			handleClassUpdated();
			return true;
		}
		return false;
	}

	/**
	 * Call this method when a class changes (e.g. the colors).
	 */
	private void handleClassUpdated() {
		GuiTools.refreshList(listClasses);
		var project = qupath.getProject();
		// Make sure we have updated the classes in the project
		if (project != null) {
			project.setPathClasses(listClasses.getItems());
		}
		qupath.getViewerManager().repaintAllViewers();
		// TODO: Considering the only thing that can change is the color, firing an event should be unnecessary?
		// In any case, it doesn't make sense to do the current image only... should do all or none
		var hierarchy = getHierarchy();
		if (hierarchy != null)
			hierarchy.fireHierarchyChangedEvent(listClasses);
	}
	
	/**
	 * Get the pane that may be used to display the classifications.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}
	
	/**
	 * Get the ListView displaying the classes.
	 * @return
	 */
	ListView<PathClass> getListView() {
		return listClasses;
	}
	
	/**
	 * Prompt to remove the currently selected class, if there is one.
	 * 
	 * @return true if changes were made to the class list, false otherwise
	 */
	private boolean promptToRemoveSelectedClasses() {
		List<PathClass> pathClasses = getSelectedPathClasses()
				.stream()
				.filter(PathClassPane::isNotNull)
				.toList();
		if (pathClasses.isEmpty())
			return false;
		String message;
		if (pathClasses.size() == 1)
			message = "Remove '" + pathClasses.getFirst().toString() + "' from class list?";
		else
			message = "Remove " + pathClasses.size() + " classes from list?";
		if (Dialogs.showConfirmDialog("Remove classes", message))
			return availablePathClasses.removeAll(pathClasses);
		return false;
	}

	private static boolean isNotNull(PathClass pathClass) {
		return !isNull(pathClass);
	}

	private static boolean isNull(PathClass pathClass) {
		return pathClass == null || pathClass == PathClass.NULL_CLASS;
	}
	
	
	/**
	 * Get the currently-selected PathClass.
	 * @return
	 */
	private PathClass getSelectedPathClass() {
		return listClasses.getSelectionModel().getSelectedItem();
	}
	
	/**
	 * Get the currently-selected PathClasses.
	 * @return
	 */
	private List<PathClass> getSelectedPathClasses() {
		return listClasses.getSelectionModel().getSelectedItems()
				.stream()
				.map(p -> p.getName() == null ? null : p) // Return null for NULL_CLASS
				.toList();
	}
	
	
	/**
	 * A {@link ListCell} to display a {@link PathClass}.
	 */
	private static class PathClassListCell extends ListCell<PathClass> {
		
		private final QuPathGUI qupath;
		private final OverlayOptions overlayOptions;

		private final BorderPane pane = new BorderPane();
		private final Label label = new Label();

		private final ColorPicker colorPicker = new ColorPicker();
		private final int colorPickerSize = 10;
		private final int eyeSize = 14;

		private final Node iconShowing = new StackPane(IconFactory.createNode(FontAwesome.Glyph.EYE, eyeSize));
		private final Node iconHidden = new StackPane(IconFactory.createNode(FontAwesome.Glyph.EYE_SLASH, eyeSize));
		private final Node iconUnavailable = new StackPane(GlyphFontRegistry.font("FontAwesome")
				.create('\uf2a8')
				.size(eyeSize));

		private static final String STYLE_HIDDEN = "-fx-font-family:arial; -fx-font-style:italic;";
		private static final String STYLE_SHOWING = "-fx-font-family:arial; -fx-font-style:normal;";

		PathClassListCell(QuPathGUI qupath) {
			this.qupath = qupath;
			this.overlayOptions = qupath == null ? null : qupath.getOverlayOptions();
			label.setMaxWidth(Double.MAX_VALUE);
			label.setMinWidth(20);

			colorPicker.getStyleClass().addAll( "minimal-color-picker", "always-opaque", "button");
			colorPicker.setStyle("-fx-color-rect-width: " + colorPickerSize + "; -fx-color-rect-height: " + colorPickerSize + ";");
			colorPicker.setOnHiding(e -> this.handleColorChange(colorPicker.getValue()));

			label.setGraphic(colorPicker);

			// Tooltip for the main label (but not the visibility part)
			Tooltip tooltip = new Tooltip("Available classes (right-click to add or remove).\n" +
					"Names ending with an Asterisk* are 'ignored' under certain circumstances - see the docs for more info.");
			label.setTooltip(tooltip);
			label.setTextOverrun(OverrunStyle.ELLIPSIS);

			var sp = new StackPane(label);
			label.setMaxWidth(Double.MAX_VALUE);
			sp.setPrefWidth(1.0);
			sp.setMinHeight(0.0);
			StackPane.setAlignment(label, Pos.CENTER_LEFT);
			pane.setCenter(sp);

			configureHiddenIcon();
			configureShowingIcon();
			configureUnavailableIcon();
		}

		private void handleColorChange(Color color) {
			var pathClass = getItem();
			if (pathClass != null && pathClass != PathClass.NULL_CLASS) {
				var rgb = ColorToolsFX.getRGB(color);
				if (!Objects.equals(rgb, pathClass.getColor())) {
					pathClass.setColor(rgb);
					var qupath = QuPathGUI.getInstance();
					if (qupath != null) {
						// TODO: Consider whether project class list needs to be updated
						for (var viewer : qupath.getAllViewers()) {
							// Technically we only need to repaint the viewers, but we need to ensure that
							// any cached tiles are updated as well - so it's easiest to fire a hierarchy changed event
							var hierarchy = viewer.getHierarchy();
							if (hierarchy != null) {
								hierarchy.fireHierarchyChangedEvent(pathClass);
							}
						}
					}
				}
			}
		}

		private void configureHiddenIcon() {
			iconHidden.opacityProperty().bind(opacityBinding);
			if (overlayOptions != null) {
				Tooltip.install(iconHidden, new Tooltip("Class hidden - click to toggle visibility"));
				iconHidden.setOnMouseClicked(this::handleToggleVisibility);
			}
		}

		private void configureShowingIcon() {
			iconShowing.opacityProperty().bind(opacityBinding);
			if (overlayOptions != null) {
				Tooltip.install(iconShowing, new Tooltip("Class showing - click to toggle visibility"));
				iconShowing.setOnMouseClicked(this::handleToggleVisibility);
			}
		}

		private DoubleBinding opacityBinding = Bindings.createDoubleBinding(this::calculateOpacity,
				hoverProperty(), selectedProperty(), itemProperty());

		private boolean isSelected(PathClass pathClass) {
			return overlayOptions.selectedClassesProperty().contains(pathClass);
		}

		private double calculateOpacity() {
			var item = getItem();
			if (item == null)
				return 0.0;
			if (isHover() || isSelected())
				return 0.8;
			var mode = overlayOptions.getSelectedClassVisibilityMode();
			switch (mode) {
				case HIDE_SELECTED:
					if (isHidden(item))
						return isSelected(item) ? 0.8 : 0.6;
					else
						return 0.1;
				case SHOW_SELECTED:
					if (isHidden(item))
						return 0.4;
					else
						return isSelected(item) ? 0.8 : 0.6;
			}
			return 1.0;
		}

		private void configureUnavailableIcon() {
			iconUnavailable.opacityProperty().bind(opacityBinding);
			if (overlayOptions != null) {
				Tooltip.install(iconUnavailable,
						new Tooltip("Derived class is hidden because a related class is already hidden"));
				iconUnavailable.setOnMouseClicked(this::handleToggleVisibility);
			}
		}

		private void handleToggleVisibility(MouseEvent e) {
			var pathClass = getItem();
			var options = overlayOptions;
			if (pathClass != null && options != null) {
				options.setPathClassHidden(pathClass, !options.selectedClassesProperty().contains(pathClass));
				getListView().refresh();
			}
			e.consume();
		}


		private PathObjectHierarchy getHierarchy() {
			var imageData = qupath == null ? null : qupath.getImageData();
			return imageData == null ? null : imageData.getHierarchy();
		}

		private boolean isHidden(PathClass pathClass) {
			return overlayOptions != null && overlayOptions.isPathClassHidden(pathClass);
		}

		private long getAnnotationCount(PathClass pathClass) {
			var hierarchy = getHierarchy();
			if (hierarchy != null) {
				try {
					return hierarchy.getAnnotationObjects()
							.stream()
							.filter(p -> pathClass.equals(p.getPathClass()))
							.count();
				} catch (Exception e) {
					logger.debug("Exception while counting objects for class", e);
				}
			}
			return -1;
		}

		private String getText(PathClass pathClass) {
			if (pathClass.getBaseClass() == pathClass && pathClass.getName() == null) {
				return "None";
			} else {
				long n = getAnnotationCount(pathClass);
				if (n > 0)
					return pathClass + " (" + n + ")";
				else
					return pathClass.toString();
			}
		}

		private Color getColor(PathClass pathClass) {
			var rgb = pathClass.getColor();
			if (rgb == null || PathClassTools.isNullClass(pathClass)) {
				return Color.TRANSPARENT;
			} else {
				return ColorToolsFX.getPathClassColor(pathClass);
			}
		}

		@Override
		protected void updateItem(PathClass value, boolean empty) {
			super.updateItem(value, empty);
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			colorPicker.setValue(getColor(value));
			colorPicker.setDisable(value == PathClass.NULL_CLASS);

			String text = getText(value);
			var mode = overlayOptions.getSelectedClassVisibilityMode();
			if (isHidden(value)) {
				label.setStyle(STYLE_HIDDEN);
				if (value == PathClass.NULL_CLASS) {
					pane.setRight(iconHidden);
				} else if (mode == OverlayOptions.ClassVisibilityMode.HIDE_SELECTED &&
						!isSelected(value)) {
					pane.setRight(iconUnavailable);
				} else {
					pane.setRight(iconHidden);
				}
			} else {
				label.setStyle(STYLE_SHOWING);
				pane.setRight(iconShowing);
			}
			label.setText(text);
			setGraphic(pane);
		}

	}
	
}