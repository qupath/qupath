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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.scripting.QP;

/**
 * Component used to display and edit available {@linkplain PathClass PathClasses}.
 * 
 * @author Pete Bankhead
 */
public class PathClassPane {
	
	private final static Logger logger = LoggerFactory.getLogger(PathClassPane.class);
	
	private QuPathGUI qupath;
	
	private Pane pane;
	
	/**
	 * List displaying available PathClasses
	 */
	private ListView<PathClass> listClasses;
	
	/**
	 * Filter visible classes
	 */
	private StringProperty filterText = new SimpleStringProperty("");
	
	/**
	 * If set, request that new annotations have their classification set automatically
	 */
	private BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);
	
	PathClassPane(QuPathGUI qupath) {
		this.qupath = qupath;
		pane = createClassPane();
	}

	
	Predicate<PathClass> createPredicate(String text) {
		if (text == null || text.isBlank())
			return p -> true;
		String text2 = text.toLowerCase();
		return (PathClass p) -> {
			return p == null || p == PathClassFactory.getPathClassUnclassified() ||
					p.toString().toLowerCase().contains(text2);
		};
	}
	
	private Pane createClassPane() {
		listClasses = new ListView<>();
		
		var filteredList = qupath.getAvailablePathClasses().filtered(createPredicate(null));
		listClasses.setItems(filteredList);
		listClasses.setTooltip(new Tooltip("Annotation classes available (right-click to add or remove)"));
		
		listClasses.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateAutoSetPathClassProperty());
		
		listClasses.setCellFactory(v -> new PathClassListCell(qupath));

		listClasses.getSelectionModel().select(0);
		listClasses.setPrefSize(100, 200);
		
		listClasses.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		var copyCombo = new KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN);
		var pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCodeCombination.SHORTCUT_DOWN);
		
		// Intercept space presses because we handle them elsewhere
		listClasses.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
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
			}
		});
		
		listClasses.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
			if (e.isConsumed())
				return;
			if (e.getCode() == KeyCode.BACK_SPACE) {
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
		});
		listClasses.setOnMouseClicked(e -> {
			if (!e.isPopupTrigger() && e.getClickCount() == 2)
				promptToEditSelectedClass();
		});

		ContextMenu menuClasses = createClassesMenu();
		listClasses.setContextMenu(menuClasses);
		
		// Add the class list
		BorderPane paneClasses = new BorderPane();
		paneClasses.setCenter(listClasses);

		Action setSelectedObjectClassAction = new Action("Set class", e -> {
			var hierarchy = getHierarchy();
			if (hierarchy == null)
				return;
			PathClass pathClass = getSelectedPathClass();
			if (pathClass == PathClassFactory.getPathClassUnclassified())
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
		});
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
		filterText.addListener((v, o, n) -> filteredList.setPredicate(createPredicate(n)));
		var paneBottom = PaneTools.createRowGrid(tfFilter, paneClassButtons);
		
		PaneTools.setMaxWidth(Double.MAX_VALUE,
				btnSetClass, btnAutoClass, tfFilter);
		
		paneClasses.setBottom(paneBottom);
		return paneClasses;
	}
	
	ContextMenu createClassesMenu() {
		ContextMenu menu = new ContextMenu();
		
		Action actionAddClass = new Action("Add class", e -> promptToAddClass());
		Action actionRemoveClass = new Action("Remove class", e -> promptToRemoveSelectedClasses());
		Action actionResetClasses = new Action("Reset to default classes", e -> promptToResetClasses());
		Action actionImportClasses = new Action("Import classes from project", e -> promptToImportClasses());

		actionRemoveClass.disabledProperty().bind(Bindings.createBooleanBinding(() -> {
			PathClass item = listClasses.getSelectionModel().getSelectedItem();
			return item == null || PathClassFactory.getPathClassUnclassified() == item;
		},
		listClasses.getSelectionModel().selectedItemProperty()
		));
		
		MenuItem miRemoveClass = ActionUtils.createMenuItem(actionRemoveClass);
		MenuItem miAddClass = ActionUtils.createMenuItem(actionAddClass);
		MenuItem miResetAllClasses = ActionUtils.createMenuItem(actionResetClasses);
//		MenuItem miPopulateFromImage = ActionUtils.createMenuItem(actionPopulateFromImage);
//		MenuItem miPopulateFromImageBase = ActionUtils.createMenuItem(actionPopulateFromImageBase);
		
//		MenuItem miClearAllClasses = new MenuItem("Clear all classes");
//		miClearAllClasses.setOnAction(e -> promptToClearClasses());

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
			selectObjectsByClassification(imageData, getSelectedPathClasses().toArray(PathClass[]::new));
		});		

		MenuItem miSetHidden = new MenuItem("Hide classes in viewer");
		miSetHidden.setOnAction(e -> setSelectedClassesVisibility(false));
		MenuItem miSetVisible = new MenuItem("Show classes in viewer");
		miSetVisible.setOnAction(e -> setSelectedClassesVisibility(true));
		
//		MenuItem miToggleClassVisible = new MenuItem("Toggle display class");
//		miToggleClassVisible.setOnAction(e -> {
//			OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
//			for (var pathClass : getSelectedPathClasses()) {
//				if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
//					continue;
//				overlayOptions.setPathClassHidden(pathClass, !overlayOptions.isPathClassHidden(pathClass));
//			}
//			listClasses.refresh();
//		});
		
		menu.setOnShowing(e -> {
			var hierarchy = getHierarchy();
			menuPopulate.setDisable(hierarchy == null);
			miPopulateFromImage.setDisable(hierarchy == null);
			miPopulateFromImageBase.setDisable(hierarchy == null);
			miPopulateFromChannels.setDisable(qupath.getImageData() == null);
			var selected = getSelectedPathClasses();
			boolean hasClasses = !selected.isEmpty();
//			boolean hasClasses = selected.size() > 1 || 
//					(selected.size() == 1 && selected.get(0) != null && selected.get(0) != PathClassFactory.getPathClassUnclassified());
			miSetVisible.setDisable(!hasClasses);
			miSetHidden.setDisable(!hasClasses);
//			miRemoveClass.setDisable(!hasClasses);
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
//						miToggleClassVisible
						),
//				new SeparatorMenuItem(),
				miSelectObjects);
		
		return menu;
	}
	
	
	/**
	 * Select objects by classification, logging the step (if performed) in the history workflow.
	 * @param imageData the {@link ImageData} containing objects to be selected
	 * @param pathClasses classifications that will result in an object being selected
	 * @return true if a selection command was run, false otherwise (e.g. if no pathClasses were specified)
	 */
	public static boolean selectObjectsByClassification(ImageData<?> imageData, PathClass... pathClasses) {
		var hierarchy = imageData.getHierarchy();
		if (pathClasses.length == 0) {
			logger.warn("Cannot select objects by classification - no classifications selected!");
			return false;
		}
		QP.selectObjectsByPathClass(hierarchy, pathClasses);
		var s = Arrays.stream(pathClasses)
				.map(p -> p == null || p == PathClassFactory.getPathClassUnclassified() ? "null" : "\"" + p.toString() + "\"").collect(Collectors.joining(", "));
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Select objects by classification",
				"selectObjectsByClassification(" + s + ");"));
		return true;
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
	
	
	void toggleSelectedClassesVisibility() {
		OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
			overlayOptions.setPathClassHidden(pathClass, !overlayOptions.isPathClassHidden(pathClass));
		}
		listClasses.refresh();
	}
	
	
	void setSelectedClassesVisibility(boolean visible) {
		OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
//			if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
//				continue;
			overlayOptions.setPathClassHidden(pathClass, !visible);
		}
		listClasses.refresh();
	}
	
	void updateAutoSetPathClassProperty() {
		PathClass pathClass = null;
		if (doAutoSetPathClass.get()) {
			pathClass = getSelectedPathClass();
		}
		if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
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
	boolean promptToPopulateFromChannels() {
		var imageData = qupath.getImageData();
		if (imageData == null)
			return false;
		
		var server = imageData.getServer();
		List<PathClass> newClasses = new ArrayList<>();
		for (var channel : server.getMetadata().getChannels()) {
			newClasses.add(PathClassFactory.getPathClass(channel.getName(), channel.getColor()));
		}
		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No channels found, somehow!");
			return false;
		}
		
		List<PathClass> currentClasses = new ArrayList<>(qupath.getAvailablePathClasses());
		currentClasses.remove(null);
		if (currentClasses.equals(newClasses)) {
			Dialogs.showInfoNotification("Set available classes", "Class lists are the same - no changes to make!");
			return false;
		}
		// Always need to be able to ignore...
		newClasses.add(PathClassFactory.getPathClass(StandardPathClasses.IGNORE));
		
		var btn = DialogButton.YES;
		if (qupath.getAvailablePathClasses().size() > 1)
			btn = Dialogs.showYesNoCancelDialog("Set available classes", "Keep existing available classes?");
		if (btn == DialogButton.YES) {
			newClasses.removeAll(qupath.getAvailablePathClasses());
			return qupath.getAvailablePathClasses().addAll(newClasses);
		} else if (btn == DialogButton.NO) {
			newClasses.add(0, PathClassFactory.getPathClassUnclassified());
			return qupath.getAvailablePathClasses().setAll(newClasses);
		} else
			return false;
	}
	
	/**
	 * Prompt to remove all available classifications ('null' remains)
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToClearClasses() {
		var available = qupath.getAvailablePathClasses();
		if (available.isEmpty() || (available.size() == 1 && available.get(0) == PathClassFactory.getPathClassUnclassified()))
			return false;
		if (Dialogs.showConfirmDialog("Remove classifications", "Remove all available classes?")) {
			available.setAll(PathClassFactory.getPathClassUnclassified());
			return true;
		} else
			return false;
	}
	
	
	/**
	 * Prompt to populate available class list from the current image.
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToPopulateFromImage(boolean baseClassesOnly) {
		var hierarchy = getHierarchy();
		if (hierarchy == null)
			return false;
		
		Set<PathClass> representedClasses = hierarchy.getFlattenedObjectList(null).stream()
				.filter(p -> !p.isRootObject())
				.map(p -> p.getPathClass())
				.filter(p -> p != null && p != PathClassFactory.getPathClassUnclassified())
				.map(p -> baseClassesOnly ? p.getBaseClass() : p)
				.collect(Collectors.toSet());
		
		List<PathClass> newClasses = new ArrayList<>(representedClasses);
		Collections.sort(newClasses);
		
		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No classifications found in current image!");
			return false;
		}
		
		newClasses.add(PathClassFactory.getPathClass(StandardPathClasses.IGNORE));
		
		List<PathClass> currentClasses = new ArrayList<>(qupath.getAvailablePathClasses());
		currentClasses.remove(null);
		if (currentClasses.equals(newClasses)) {
			Dialogs.showInfoNotification("Set available classes", "Class lists are the same - no changes to make!");
			return false;
		}
		
		var btn = DialogButton.YES;
		if (qupath.getAvailablePathClasses().size() > 1)
			btn = Dialogs.showYesNoCancelDialog("Set available classes", "Keep existing available classes?");
		if (btn == DialogButton.YES) {
			newClasses.removeAll(qupath.getAvailablePathClasses());
			return qupath.getAvailablePathClasses().addAll(newClasses);
		} else if (btn == DialogButton.NO) {
			newClasses.add(0, PathClassFactory.getPathClassUnclassified());
			return qupath.getAvailablePathClasses().setAll(newClasses);
		} else
			return false;
	}
	
	/**
	 * Prompt to import available class list from another project.
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToImportClasses() {
		File file = Dialogs.promptForFile("Import classifications", null, "QuPath project", ProjectIO.getProjectExtension());
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
			ObservableList<PathClass> availableClasses = qupath.getAvailablePathClasses();
			if (pathClasses.size() == availableClasses.size() && availableClasses.containsAll(pathClasses)) {
				Dialogs.showInfoNotification("Import PathClasses", file.getName() + " contains same classifications - no changes to make");
				return false;
			}
			availableClasses.setAll(pathClasses);
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
	boolean promptToResetClasses() {
		if (Dialogs.showConfirmDialog("Reset classes", "Reset all available classes?")) {
			return qupath.resetAvailablePathClasses();
		}
		return false;
	}
	
	
	/**
	 * Prompt to add a new classification.
	 * @return true if a new classification was added, false otherwise
	 */
	boolean promptToAddClass() {
		String input = Dialogs.showInputDialog("Add class", "Class name", "");
		if (input == null || input.trim().isEmpty())
			return false;
		PathClass pathClass = PathClassFactory.getPathClass(input);
		var list = qupath.getAvailablePathClasses();
		if (list.contains(pathClass)) {
			Dialogs.showErrorMessage("Add class", "Class '" + input + "' already exists!");
			return false;
		} else if (input.toLowerCase().equals("null")) {
			Dialogs.showErrorMessage("Add class", "Cannot add a 'null' class, try another name!");
			return false;
		}
		list.add(pathClass);
		return true;
	}
	
	/**
	 * Prompt to edit the selected classification.
	 * @return true if changes were made, false otherwise
	 */
	boolean promptToEditSelectedClass() {
		PathClass pathClassSelected = getSelectedPathClass();
		if (promptToEditClass(pathClassSelected)) {
			//					listModelPathClasses.fireListDataChangedEvent();
			GuiTools.refreshList(listClasses);
			var project = qupath.getProject();
			// Make sure we have updated the classes in the project
			if (project != null) {
				project.setPathClasses(listClasses.getItems());
			}
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
	 * Prompt to edit the name/color of a class.
	 * @param pathClass
	 * @return
	 */
	public static boolean promptToEditClass(final PathClass pathClass) {
		if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
			return false;

		boolean defaultColor = pathClass == null;

		BorderPane panel = new BorderPane();

		BorderPane panelName = new BorderPane();
		String name;
		Color color;

		if (defaultColor) {
			name = "Default object color";
			color = ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get());
			//			textField.setEditable(false);
			//			textField.setEnabled(false);
			Label label = new Label(name);
			label.setPadding(new Insets(5, 0, 10, 0));
			panelName.setCenter(label);
		} else {
			name = pathClass.getName();
			if (name == null)
				name = "";
			color = ColorToolsFX.getPathClassColor(pathClass);		
			Label label = new Label(name);
			label.setPadding(new Insets(5, 0, 10, 0));
			panelName.setCenter(label);
			//				textField.setText(name);
			//				panelName.setLeft(new Label("Class name"));
			//				panelName.setCenter(textField);
		}

		panel.setTop(panelName);
		ColorPicker panelColor = new ColorPicker(color);

		panel.setCenter(panelColor);

		if (!Dialogs.showConfirmDialog("Edit class", panel))
			return false;

		//			String newName = textField.getText().trim();
		Color newColor = panelColor.getValue();
		//			if ((name.length() == 0 || name.equals(newName)) && newColor.equals(color))
		//				return false;

		Integer colorValue = newColor.isOpaque() ? ColorToolsFX.getRGB(newColor) : ColorToolsFX.getARGB(newColor);
		if (defaultColor) {
			if (newColor.isOpaque())
				PathPrefs.colorDefaultObjectsProperty().set(colorValue);
			else
				PathPrefs.colorDefaultObjectsProperty().set(colorValue);
		}
		else {
			//				if (!name.equals(pathClass.getName()) && PathClassFactory.pathClassExists(newName)) {
			//					logger.warn("Modified name already exists - cannot rename");
			//					return false;
			//				}
			//				pathClass.setName(newName);
			pathClass.setColor(colorValue);
		}
		return true;
	}



	/**
	 * Prompt to remove the currently selected class, if there is one.
	 * 
	 * @return true if changes were made to the class list, false otherwise
	 */
	boolean promptToRemoveSelectedClasses() {
		List<PathClass> pathClasses = getSelectedPathClasses()
				.stream()
				.filter(p -> p != null && p != PathClassFactory.getPathClassUnclassified())
				.collect(Collectors.toList());
		if (pathClasses.isEmpty())
			return false;
		String message;
		if (pathClasses.size() == 1)
			message = "Remove '" + pathClasses.get(0).toString() + "' from class list?";
		else
			message = "Remove " + pathClasses.size() + " classes from list?";
		if (Dialogs.showConfirmDialog("Remove classes", message))
			return qupath.getAvailablePathClasses().removeAll(pathClasses);
		return false;
	}
	
	
	/**
	 * Get the currently-selected PathClass.
	 * @return
	 */
	PathClass getSelectedPathClass() {
		return listClasses.getSelectionModel().getSelectedItem();
	}
	
	/**
	 * Get the currently-selected PathClasses.
	 * @return
	 */
	List<PathClass> getSelectedPathClasses() {
		return listClasses.getSelectionModel().getSelectedItems()
				.stream()
				.map(p -> p.getName() == null ? null : p)
				.collect(Collectors.toList());
	}
	
	
	/**
	 * Extract annotations from a hierarchy with a specific classification.
	 * @param hierarchy
	 * @param pathClass
	 * @return
	 */
	static List<PathObject> getAnnotationsForClass(PathObjectHierarchy hierarchy, PathClass pathClass) {
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
	static class PathClassListCell extends ListCell<PathClass> {
		
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
						// Try to count objects for class
						// May be possibility of concurrent modification exception?
						//						n = nLabelledObjectsForClass(hierarchy, value);
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