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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.scene.control.*;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
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
	
	private QuPathGUI qupath;
	
	private ObservableList<PathClass> availablePathClasses;
	
	private Pane pane;
	private ListView<PathClass> listClasses;
	
	private StringProperty filterText = new SimpleStringProperty("");
	
	/**
	 * If set, request that new annotations have their classification set automatically
	 */
	private BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);
	
	PathClassPane(QuPathGUI qupath) {
		this.qupath = qupath;
		this.availablePathClasses = qupath.getAvailablePathClasses();
		pane = createClassPane();
	}

	
	private Predicate<PathClass> createPathClassListFilter(String filterText) {
		if (filterText == null || filterText.isBlank())
			return p -> true;
		String text2 = filterText.toLowerCase();
		return (PathClass p) -> {
			return p == null || p == PathClass.NULL_CLASS ||
					p.toString().toLowerCase().contains(text2);
		};
	}
	
	private Pane createClassPane() {
		listClasses = new ListView<>();
		
		var filteredList = availablePathClasses.filtered(createPathClassListFilter(null));
		listClasses.setItems(filteredList);
		listClasses.setTooltip(new Tooltip("Annotation classes available (right-click to add or remove)"));
		
		listClasses.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateAutoSetPathClassProperty());
		
		listClasses.setCellFactory(v -> new PathClassListCell(qupath));

		listClasses.getSelectionModel().select(0);
		listClasses.setPrefSize(100, 200);
		
		listClasses.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
				
		// Intercept space presses because we handle them elsewhere
		listClasses.addEventFilter(KeyEvent.KEY_PRESSED, this::filterKeyPresses);
		listClasses.setOnMouseClicked(e -> {
			if (!e.isPopupTrigger() && e.getClickCount() == 2)
				promptToEditSelectedClass();
		});

		ContextMenu menuClasses = createClassesMenu();
		listClasses.setContextMenu(menuClasses);
		
		// Add the class list
		BorderPane paneClasses = new BorderPane();
		paneClasses.setCenter(listClasses);

		Action setSelectedObjectClassAction = new Action("Set class", e -> promptToSetClass());
		setSelectedObjectClassAction.setLongText("Set the class of the currently-selected annotation(s)");

		Action autoClassifyAnnotationsAction = new Action("Auto set");
		autoClassifyAnnotationsAction.setLongText("Automatically set all new annotations to the selected class");
		autoClassifyAnnotationsAction.selectedProperty().bindBidirectional(doAutoSetPathClass);
		
		doAutoSetPathClass.addListener((e, f, g) -> updateAutoSetPathClassProperty());

		Button btnSetClass = ActionUtils.createButton(setSelectedObjectClassAction);
		ToggleButton btnAutoClass = ActionUtils.createToggleButton(autoClassifyAnnotationsAction);
		
		// Create a button to show context menu (makes it more obvious to the user that it exists)
		Button btnMore = GuiTools.createMoreButton(menuClasses, Side.RIGHT);
		GridPane paneClassButtons = new GridPane();
		paneClassButtons.add(btnSetClass, 0, 0);
		paneClassButtons.add(btnAutoClass, 1, 0);
		paneClassButtons.add(btnMore, 2, 0);
		GridPane.setHgrow(btnSetClass, Priority.ALWAYS);
		GridPane.setHgrow(btnAutoClass, Priority.ALWAYS);
		
		var tfFilter = new TextField();
		tfFilter.setTooltip(new Tooltip("Type to filter classifications in list"));
		tfFilter.setPromptText("Filter classifications in list");
		filterText.bind(tfFilter.textProperty());
		filterText.addListener((v, o, n) -> filteredList.setPredicate(createPathClassListFilter(n)));
		var paneBottom = GridPaneUtils.createRowGrid(tfFilter, paneClassButtons);
		
		GridPaneUtils.setMaxWidth(Double.MAX_VALUE,
				btnSetClass, btnAutoClass, tfFilter);
		
		paneClasses.setBottom(paneBottom);
		return paneClasses;
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
			if (pathObject.isTMACore())
				continue;
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
					.stream().map(p -> p.toString()).collect(Collectors.joining(System.lineSeparator()));
			if (!s.isBlank()) {
				Clipboard.getSystemClipboard().setContent(
						Map.of(DataFormat.PLAIN_TEXT, s));
			}
			e.consume();
			return;
		} else if (pasteCombo.match(e)) {
			logger.debug("Paste not implemented for classification list!");
			e.consume();
			return;
		}
	}
	
	
	private Action createAddClassAction() {
		return new Action("Add class", e -> promptToAddClass());
	}

	private Action createRemoveSelectedClassesAction() {
		return new Action("Remove class", e -> promptToRemoveSelectedClasses());
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
		
		Action actionAddClass = createAddClassAction();
		Action actionRemoveClass = createRemoveSelectedClassesAction();
		Action actionResetClasses = createResetToDefaultClassesAction();
		Action actionImportClasses = createImportClassesFromProjectAction();

		actionRemoveClass.disabledProperty().bind(createRemoveClassDisabledBinding());
		
		MenuItem miRemoveClass = ActionUtils.createMenuItem(actionRemoveClass);
		MenuItem miAddClass = ActionUtils.createMenuItem(actionAddClass);
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

		MenuItem miSelectObjects = new MenuItem("Select objects by classification");
		miSelectObjects.disableProperty().bind(Bindings.createBooleanBinding(
				() -> {
					var item = listClasses.getSelectionModel().getSelectedItem();
					return item == null;
				},
				listClasses.getSelectionModel().selectedItemProperty()));
		
		miSelectObjects.setOnAction(e -> {
			var imageData = qupath.getImageData();
			if (imageData == null)
				return;
			Commands.selectObjectsByClassification(imageData, getSelectedPathClasses().toArray(PathClass[]::new));
		});		

		MenuItem miSetHidden = new MenuItem("Hide classes in viewer");
		miSetHidden.setOnAction(e -> setSelectedClassesVisibility(false));
		MenuItem miSetVisible = new MenuItem("Show classes in viewer");
		miSetVisible.setOnAction(e -> setSelectedClassesVisibility(true));
		
		menu.setOnShowing(e -> {
			var hierarchy = getHierarchy();
			menuPopulate.setDisable(hierarchy == null);
			miPopulateFromImage.setDisable(hierarchy == null);
			miPopulateFromImageBase.setDisable(hierarchy == null);
			miPopulateFromChannels.setDisable(qupath.getImageData() == null);
			var selected = getSelectedPathClasses();
			boolean hasClasses = !selected.isEmpty();
			miSetVisible.setDisable(!hasClasses);
			miSetHidden.setDisable(!hasClasses);
		});
		
		MenuItem miImportFromProject = ActionUtils.createMenuItem(actionImportClasses);
		
		menu.getItems().addAll(
				MenuTools.createMenu("Add/Remove...", 
						miAddClass,
						miRemoveClass
						),
				menuPopulate,
				miPopulateFromChannels,
				miResetAllClasses,
				miImportFromProject,
				new SeparatorMenuItem(),
				MenuTools.createMenu("Show/Hide...", 
						miSetVisible,
						miSetHidden
						),
				miSelectObjects);
		
		return menu;
	}
	
	
	
	/**
	 * Update pane to reflect the current status.
	 */
	public void refresh() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> refresh());
			return;
		}
		listClasses.refresh();
	}
	
	private OverlayOptions getOverlayOptions() {
		return qupath.getViewer().getOverlayOptions();
	}
	
	private void toggleSelectedClassesVisibility() {
		OverlayOptions overlayOptions = getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
			overlayOptions.setPathClassHidden(pathClass, !overlayOptions.isPathClassHidden(pathClass));
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
			newClasses.add(0, PathClass.NULL_CLASS);
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
		
		Set<PathClass> representedClasses = hierarchy.getFlattenedObjectList(null).stream()
				.filter(p -> !p.isRootObject())
				.map(p -> p.getPathClass())
				.filter(p -> p != null && p != PathClass.NULL_CLASS)
				.map(p -> baseClassesOnly ? p.getBaseClass() : p)
				.collect(Collectors.toSet());
		
		List<PathClass> newClasses = new ArrayList<>(representedClasses);
		Collections.sort(newClasses);
		
		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No classifications found in current image!");
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
			newClasses.add(0, PathClass.NULL_CLASS);
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
				Dialogs.showInfoNotification("Import PathClasses", file.getName() + " contains same classifications - no changes to make");
				return false;
			}
			availablePathClasses.setAll(pathClasses);
			return true;
		} catch (Exception ex) {
			Dialogs.showErrorMessage("Error reading project", ex);
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
		} else if (input.toLowerCase().equals("null")) {
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
			//					listModelPathClasses.fireListDataChangedEvent();
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
			return true;
		}
		return false;
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
				.filter(p -> p != null && p != PathClass.NULL_CLASS)
				.toList();
		if (pathClasses.isEmpty())
			return false;
		String message;
		if (pathClasses.size() == 1)
			message = "Remove '" + pathClasses.get(0).toString() + "' from class list?";
		else
			message = "Remove " + pathClasses.size() + " classes from list?";
		if (Dialogs.showConfirmDialog("Remove classes", message))
			return availablePathClasses.removeAll(pathClasses);
		return false;
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
				.map(p -> p.getName() == null ? null : p)
				.toList();
	}
	
	
	/**
	 * Extract annotations from a hierarchy with a specific classification.
	 * @param hierarchy
	 * @param pathClass
	 * @return
	 */
	private static List<PathObject> getAnnotationsForClass(PathObjectHierarchy hierarchy, PathClass pathClass) {
		if (hierarchy == null)
			return Collections.emptyList();
		List<PathObject> annotations = new ArrayList<>();
		for (PathObject pathObject : hierarchy.getAnnotationObjects()) {
			if (pathClass.equals(pathObject.getPathClass()))
				annotations.add(pathObject);
		}
		return annotations;
	}
	
	
	/**
	 * A {@link ListCell} for displaying {@linkplain PathClass PathClasses}, including annotation counts 
	 * for the classes if available.
	 */
	private static class PathClassListCell extends ListCell<PathClass> {
		
		private QuPathGUI qupath;
		
		PathClassListCell(QuPathGUI qupath) {
			this.qupath = qupath;
		}

		@Override
		protected void updateItem(PathClass value, boolean empty) {
			super.updateItem(value, empty);
			QuPathViewer viewer = qupath == null ? null : qupath.getViewer();
			PathObjectHierarchy hierarchy = viewer == null ? null : viewer.getHierarchy();
			int size = 10;
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
			} else if (value.getBaseClass() == value && value.getName() == null) {
				setText("None");
				setGraphic(new Rectangle(size, size, ColorToolsFX.getCachedColor(0, 0, 0, 0)));
			} else {
				int n = 0; 
				if (hierarchy != null) {
					try {
						n = getAnnotationsForClass(hierarchy, value).size();
					} catch (Exception e) {
						logger.debug("Exception while counting objects for class", e);
					}
				}
				if (n == 0)
					setText(value.toString());
				else
					setText(value.toString() + " (" + n + ")");
				setGraphic(new Rectangle(size, size, ColorToolsFX.getPathClassColor(value)));
			}
			if (!empty && viewer != null && viewer.getOverlayOptions().isPathClassHidden(value)) {
				setStyle("-fx-font-family:arial; -fx-font-style:italic;");		
				setText(getText() + " (hidden)");
			} else
				setStyle("-fx-font-family:arial; -fx-font-style:normal;");
		}
	}
	
}