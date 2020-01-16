package qupath.lib.gui.panels;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
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
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

/**
 * Component used to display and edit available {@linkplain PathClass PathClasses}.
 * 
 * @author Pete Bankhead
 */
public class PathClassPane {
	
	private final static Logger logger = LoggerFactory.getLogger(PathClassPane.class);
	
	private QuPathGUI qupath;
	
	private Pane pane;
	
	/*
	 * List displaying available PathClasses
	 */
	private ListView<PathClass> listClasses;
	
	/*
	 * If set, request that new annotations have their classification set automatically
	 */
	private BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);
	
	PathClassPane(QuPathGUI qupath) {
		this.qupath = qupath;
		pane = createClassPane();
	}

	
	private Pane createClassPane() {
		listClasses = new ListView<>();
		listClasses.setItems(qupath.getAvailablePathClasses());
		listClasses.setTooltip(new Tooltip("Annotation classes available (right-click to add or remove)"));
		
		listClasses.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateAutoSetPathClassProperty());
		
		listClasses.setCellFactory(v -> new PathClassListCell(qupath));

		listClasses.getSelectionModel().select(0);
		listClasses.setPrefSize(100, 200);
		
		listClasses.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
			if (e.getCode() == KeyCode.BACK_SPACE) {
				promptToRemoveSelectedClass();
				e.consume();
				return;
			} else if (e.getCode() == KeyCode.ENTER) {
				promptToEditSelectedClass();
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
		
		PaneTools.setMaxWidth(Double.MAX_VALUE,
				btnSetClass, btnAutoClass);
						
		paneClasses.setBottom(paneClassButtons);
		return paneClasses;
	}
	
	ContextMenu createClassesMenu() {
		ContextMenu menu = new ContextMenu();
		
		Action actionAddClass = new Action("Add class", e -> promptToAddClass());
		Action actionRemoveClass = new Action("Remove class", e -> promptToRemoveSelectedClass());
		Action actionResetClasses = new Action("Reset to default classes", e -> promptToResetClasses());
		Action actionImportClasses = new Action("Import classes from project", e -> promptToImportClasses());

//		Action actionPopulateFromImage = new Action("Populate from image (include sub-classes)", e -> promptToPopulateFromImage(false));
//		Action actionPopulateFromImageBase = new Action("Populate from image (base classes only)", e -> promptToPopulateFromImage(true));

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
		
		MenuItem miClearAllClasses = new MenuItem("Clear all classes");
		miClearAllClasses.setOnAction(e -> promptToClearClasses());

		MenuItem miPopulateFromImage = new MenuItem("All classes (including sub-classes)");
		miPopulateFromImage.setOnAction(e -> promptToPopulateFromImage(false));
		MenuItem miPopulateFromImageBase = new MenuItem("Base classes only");
		miPopulateFromImageBase.setOnAction(e -> promptToPopulateFromImage(true));
		
		MenuItem miPopulateFromChannels = new MenuItem("Image channel names");
		miPopulateFromChannels.setOnAction(e -> promptToPopulateFromChannels());

		Menu menuPopulate = new Menu("Populate from image");
		menuPopulate.getItems().addAll(
				miPopulateFromImageBase, miPopulateFromImage,
				new SeparatorMenuItem(), miPopulateFromChannels);

		MenuItem miSelectObjects = new MenuItem("Select objects with class");
		miSelectObjects.disableProperty().bind(Bindings.createBooleanBinding(
				() -> {
					var item = listClasses.getSelectionModel().getSelectedItem();
					return item == null;
				},
				listClasses.getSelectionModel().selectedItemProperty()));
		
		miSelectObjects.setOnAction(e -> {
			var hierarchy = getHierarchy();
			if (hierarchy == null)
				return;
			PathClass pathClass = getSelectedPathClass();
//			if (pathClass == null)
//				return;
			if (pathClass == PathClassFactory.getPathClassUnclassified())
				pathClass = null;
			PathClass pathClass2 = pathClass;
			List<PathObject> pathObjectsToSelect = hierarchy.getObjects(null, null)
					.stream()
					.filter(p -> !p.isRootObject() && p.getPathClass() == pathClass2)
					.collect(Collectors.toList());
			if (pathObjectsToSelect.isEmpty())
				hierarchy.getSelectionModel().clearSelection();
			else
				hierarchy.getSelectionModel().setSelectedObjects(pathObjectsToSelect, pathObjectsToSelect.get(0));
		});		

		MenuItem miToggleClassVisible = new MenuItem("Toggle display class");
		miToggleClassVisible.setOnAction(e -> {
			PathClass pathClass = getSelectedPathClass();
			if (pathClass == null)
				return;
			OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
			overlayOptions.setPathClassHidden(pathClass, !overlayOptions.isPathClassHidden(pathClass));
			listClasses.refresh();
		});
		
		menu.setOnShowing(e -> {
			var hierarchy = getHierarchy();
			menuPopulate.setDisable(hierarchy == null);
			miPopulateFromImage.setDisable(hierarchy == null);
			miPopulateFromImageBase.setDisable(hierarchy == null);
			miPopulateFromChannels.setDisable(qupath.getImageData() == null);
		});
		
		MenuItem miImportFromProject = ActionUtils.createMenuItem(actionImportClasses);
		
		menu.getItems().addAll(
				miAddClass,
				miRemoveClass,
				miResetAllClasses,
				miClearAllClasses,
				menuPopulate,
				miImportFromProject,
				new SeparatorMenuItem(),
				miToggleClassVisible,
				new SeparatorMenuItem(),
				miSelectObjects);
		
		return menu;
	}
	
	
	void updateAutoSetPathClassProperty() {
		PathClass pathClass = null;
		if (doAutoSetPathClass.get()) {
			pathClass = getSelectedPathClass();
		}
		if (pathClass == null || !pathClass.isValid())
			PathPrefs.setAutoSetAnnotationClass(null);
		else
			PathPrefs.setAutoSetAnnotationClass(pathClass);
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
		File file = QuPathGUI.getSharedDialogHelper().promptForFile("Import classifications", null, "QuPath project", ProjectIO.getProjectExtension());
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
		if (listClasses.getItems().contains(pathClass)) {
			Dialogs.showErrorMessage("Add class", "Class '" + input + "' already exists!");
			return false;
		}
		listClasses.getItems().add(pathClass);
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
		//		if (pathClass == null)
		//			return false; // TODO: Make work on default ROI color

		boolean defaultColor = pathClass == null;

		BorderPane panel = new BorderPane();

		BorderPane panelName = new BorderPane();
		String name;
		Color color;

		if (defaultColor) {
			name = "Default object color";
			color = ColorToolsFX.getCachedColor(PathPrefs.getColorDefaultObjects());
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
				PathPrefs.setColorDefaultObjects(colorValue);
			else
				PathPrefs.setColorDefaultObjects(colorValue);
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
	boolean promptToRemoveSelectedClass() {
		PathClass pathClass = getSelectedPathClass();
		if (pathClass == null)
			return false;
		if (pathClass == PathClassFactory.getPathClassUnclassified()) {
			Dialogs.showErrorMessage("Remove class", "Cannot remove selected class");
			return false;
		}
		if (Dialogs.showConfirmDialog("Remove class", "Remove '" + pathClass.getName() + "' from class list?"))
			return listClasses.getItems().remove(pathClass);
		return false;
	}
	
	
	/**
	 * Get the currently-selected PathClass.
	 * 
	 * This intercepts the 'null' class used to represent no classification.
	 * 
	 * @return
	 */
	PathClass getSelectedPathClass() {
		PathClass pathClass = listClasses.getSelectionModel().getSelectedItem();
		if (pathClass == null || pathClass.getName() == null)
			return null;
		return pathClass;
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
			if (value != null && viewer != null && viewer.getOverlayOptions().isPathClassHidden(value)) {
				setStyle("-fx-font-family:arial; -fx-font-style:italic;");		
				setText(getText() + " (hidden)");
			} else
				setStyle("-fx-font-family:arial; -fx-font-style:normal;");
		}
	}
	
}
