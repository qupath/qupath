/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.panels;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;


/**
 * Component for displaying annotations within the active image.
 * <p>
 * Also shows the PathClass list.
 * 
 * @author Pete Bankhead
 *
 */
public class PathAnnotationPanel implements PathObjectSelectionListener, ImageDataChangeListener<BufferedImage>, PathObjectHierarchyListener {

	private final static Logger logger = LoggerFactory.getLogger(PathAnnotationPanel.class);

	private QuPathGUI qupath;
	private ImageData<BufferedImage> imageData;
	private PathObjectHierarchy hierarchy;
	
	private BorderPane pane = new BorderPane();

	/*
	 * Request that we only synchronize to the primary selection; otherwise synchronizing to 
	 * multiple selections from long lists can be a performance bottleneck
	 */
	private static boolean synchronizePrimarySelectionOnly = true;
	
	/*
	 * List displaying annotations in the current hierarchy
	 */
	private ListView<PathObject> listAnnotations;
		
	/*
	 * Selection being changed by outside forces, i.e. don't fire an event
	 */
	private boolean changingSelection = false;
	
	/*
	 * List displaying available PathClasses
	 */
	private ListView<PathClass> listClasses;
	
	/*
	 * If set, request that new annotations have their classification set automatically
	 */
	private BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);
	
	public PathAnnotationPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		Pane paneClasses = createClassPane();
		setImageData(qupath.getImageData());
		
		Pane paneAnnotations = createAnnotationsPane();
		
//		GridPane paneColumns = PaneTools.createColumnGrid(panelObjects, panelClasses);
		SplitPane paneColumns = new SplitPane(paneAnnotations, paneClasses);
		paneColumns.setDividerPositions(0.5);
		pane.setCenter(paneColumns);
		qupath.addImageDataChangeListener(this);
	}
	
	
	private Pane createAnnotationsPane() {
		listAnnotations = new ListView<>();
		hierarchyChanged(null); // Force update

		listAnnotations.setCellFactory(v -> new PathAnnotationListCell());

		listAnnotations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		listAnnotations.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<PathObject>() {
			@Override
			public void onChanged(Change<? extends PathObject> c) {
				synchronizeListSelectionToHierarchy();
			}
		});
		
		listAnnotations.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeListSelectionToHierarchy());

		listAnnotations.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
				if (pathObject == null || !pathObject.hasROI())
					return;
				ROI roi = pathObject.getROI();
				QuPathViewer viewer = qupath.getViewer();
				if (viewer != null) {
					if (roi.getZ() >= 0)
						viewer.setZPosition(roi.getZ());
					if (roi.getT() >= 0)
						viewer.setTPosition(roi.getT());
					viewer.setCenterPixelLocation(roi.getCentroidX(), roi.getCentroidY());
				}
			}
		});

		ContextMenu menuPoints = new ContextMenu();
		MenuItem miSetProperties = new MenuItem("Set properties");
		miSetProperties.setOnAction(e -> promptToSetActiveAnnotationProperties(hierarchy));
		MenuItem miSplitPoints = new MenuItem("Split points");
		miSplitPoints.setOnAction(e -> {
			PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
			if (pathObject == null || !PathObjectTools.hasPointROI(pathObject) || hierarchy == null)
				return;
			PointsROI points = (PointsROI)pathObject.getROI();
			if (points.getNumPoints() <= 1)
				return;
			List<PathObject> newObjects = new ArrayList<>();
			int c = points.getC();
			int z = points.getZ();
			int t = points.getT();
			PathClass pathClass = pathObject.getPathClass();
			for (Point2 p : points.getAllPoints()) {
				PathObject temp = PathObjects.createAnnotationObject(ROIs.createPointsROI(p.getX(), p.getY(), ImagePlane.getPlaneWithChannel(c, z, t)), pathClass);
				newObjects.add(temp);
			}
			hierarchy.addPathObjects(newObjects);
			hierarchy.removeObject(pathObject, true);
			// Reset the selection if necessary
			if (hierarchy.getSelectionModel().getSelectedObject() == pathObject)
				hierarchy.getSelectionModel().setSelectedObject(null);

		});
		MenuItem miMergePoints = new MenuItem("Merge points for class");
		miMergePoints.setOnAction(e -> {
			PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
			if (pathObject == null || !PathObjectTools.hasPointROI(pathObject) || hierarchy == null)
				return;
			PathClass pathClass = pathObject.getPathClass();
			if (pathClass == null) {
				logger.error("No PathClass set - merging can only be applied to points of the same class!");
				return;
			}
			List<PathObject> objectsToMerge = new ArrayList<>();
			PointsROI points = (PointsROI)pathObject.getROI();
			int c = points.getC();
			int z = points.getZ();
			int t = points.getT();
			for (PathObject temp : hierarchy.getPointObjects(PathAnnotationObject.class)) {
				if (pathClass.equals(temp.getPathClass()) && c == temp.getROI().getC() && t == temp.getROI().getT() && z == temp.getROI().getZ())
					objectsToMerge.add(temp);
			}
			if (objectsToMerge.size() <= 1) {
				logger.warn("No objects found with the same classification (for same c, z, t) to merge!");
				return;
			}
			// Create new points object
			List<Point2> pointsList = new ArrayList<>();
			for (PathObject temp : objectsToMerge) {
				pointsList.addAll(((PointsROI)temp.getROI()).getAllPoints());
			}
			PathObject pathObjectNew = PathObjects.createAnnotationObject(ROIs.createPointsROI(pointsList, ImagePlane.getPlaneWithChannel(c, z, t)), pathClass);
			hierarchy.removeObjects(objectsToMerge, true);
			hierarchy.addPathObject(pathObjectNew);
		});

		menuPoints.getItems().addAll(
				miSetProperties,
				new SeparatorMenuItem(),
				miSplitPoints,
				miMergePoints
				);
		listAnnotations.setContextMenu(menuPoints);

		// Add the main annotation list
		BorderPane panelObjects = new BorderPane();
		panelObjects.setCenter(listAnnotations);
		//		panelObjects.setBorder(BorderFactory.createTitledBorder("Annotation list"));
		GridPane panelButtons = PaneTools.createColumnGrid(2);

		Action removeROIAction = createRemoveROIAction();
		Action clearROIsAction = createClearROIsAction();

		Button button = ActionUtils.createButton(removeROIAction);
		panelButtons.add(button, 0, 0);
		button.prefWidthProperty().bind(panelButtons.widthProperty().divide(2));
		button = ActionUtils.createButton(clearROIsAction);
		panelButtons.add(button, 1, 0);
		button.prefWidthProperty().bind(panelButtons.widthProperty().divide(2));
		panelObjects.setBottom(panelButtons);
		return panelObjects;
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
				refreshList(listClasses);
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
		Button btnMore = new Button("\u22EE");
		btnMore.setTooltip(new Tooltip("More options"));
		btnMore.setOnAction(e -> {
			menuClasses.show(btnMore, Side.RIGHT, 0, 0);
		});
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
		
		actionRemoveClass.disabledProperty().bind(Bindings.createBooleanBinding(() -> {
			PathClass item = listClasses.getSelectionModel().getSelectedItem();
			return item == null || PathClassFactory.getPathClassUnclassified() == item;
		},
		listClasses.getSelectionModel().selectedItemProperty()
		));
		
		MenuItem miRemoveClass = ActionUtils.createMenuItem(actionRemoveClass);
		MenuItem miAddClass = ActionUtils.createMenuItem(actionAddClass);
		MenuItem miResetAllClasses = ActionUtils.createMenuItem(actionResetClasses);
		
		MenuItem miSelectObjects = new MenuItem("Select objects with class");
		miSelectObjects.disableProperty().bind(Bindings.createBooleanBinding(
				() -> {
					var item = listClasses.getSelectionModel().getSelectedItem();
					return item == null;
				},
				listClasses.getSelectionModel().selectedItemProperty()));
		
		miSelectObjects.setOnAction(e -> {
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
		
		MenuItem miImportFromProject = ActionUtils.createMenuItem(actionImportClasses);
		
		menu.getItems().addAll(
				miAddClass,
				miRemoveClass,
				miResetAllClasses,
				new SeparatorMenuItem(),
				miSelectObjects,
				new SeparatorMenuItem(),
				miToggleClassVisible,
				new SeparatorMenuItem(),
				miImportFromProject);
		
		return menu;
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
			refreshList(listClasses);
			var project = qupath.getProject();
			// Make sure we have updated the classes in the project
			if (project != null) {
				project.setPathClasses(listClasses.getItems());
			}
			if (hierarchy != null)
				hierarchy.fireHierarchyChangedEvent(listClasses);
			return true;
		}
		return false;
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
	
	
	void synchronizeListSelectionToHierarchy() {
		if (hierarchy == null || changingSelection)
			return;
		changingSelection = true;
		Set<PathObject> selectedSet = new HashSet<>(listAnnotations.getSelectionModel().getSelectedItems());
		PathObject selectedObject = listAnnotations.getSelectionModel().getSelectedItem();
		if (!selectedSet.contains(selectedObject))
			selectedObject = null;
		hierarchy.getSelectionModel().setSelectedObjects(selectedSet, selectedObject);
		changingSelection = false;
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
	
	
	
	/**
	 * Create an action to remove the selected ROI, and optionally its descendants.
	 * 
	 * @return
	 */
	Action createRemoveROIAction() {
		Action action = new Action("Delete", e -> {
			if (hierarchy == null)
				return;
			// TODO: Consider reusing selected object deletion code within viewer
			// Remove all the selected ROIs
			List<PathObject> pathObjectsToRemove = new ArrayList<>(listAnnotations.getSelectionModel().getSelectedItems());
			if (pathObjectsToRemove == null || pathObjectsToRemove.isEmpty())
				return;
			int nObjects = pathObjectsToRemove.size();
			if (!Dialogs.showYesNoDialog("Delete annotations",
					String.format("Delete %d %s?", nObjects, nObjects == 1 ? "annotation" : "annotations")))
				return;
			// Check for descendant objects
			List<PathObject> descendantList = new ArrayList<>();
			for (PathObject parent : pathObjectsToRemove)
				PathObjectTools.getFlattenedObjectList(parent, descendantList, false);
			descendantList.removeAll(pathObjectsToRemove);
			int nDescendants = descendantList.size();
			boolean keepChildren = true;
			if (nDescendants > 0) {
				DialogButton result = Dialogs.showYesNoCancelDialog("Delete annotations",
						String.format("Keep %d descendant %s?", nDescendants, nDescendants == 1 ? "object" : "objects"));
				if (result == DialogButton.CANCEL)
					return;
				else if (result == DialogButton.YES)
					keepChildren = true;
				else
					keepChildren = false;
			}
			hierarchy.getSelectionModel().clearSelection();
			hierarchy.removeObjects(pathObjectsToRemove, keepChildren);
		});
		action.setLongText("Delete the currently-selected annotations");
		return action;
	}


	/**
	 * Create an action to clear all annotation ROIs.
	 * 
	 * @return
	 */
	Action createClearROIsAction() {
		Action action = new Action("Delete all", e -> {
			if (hierarchy == null)
				return;
			List<PathObject> pathObjectsToRemove = new ArrayList<>(listAnnotations.getItems());
			if (pathObjectsToRemove.isEmpty())
				return;
			int nObjects = pathObjectsToRemove.size();
			if (!Dialogs.showYesNoDialog("Delete all annotations", String.format("Delete %d %s", nObjects, nObjects == 1 ? "annotation" : "annotations")))
				return;
			
			// Prompt to keep descendant objects, if required
			List<PathObject> children = new ArrayList<>();
			for (PathObject temp : pathObjectsToRemove) {
				if (temp.hasChildren()) {
					children.addAll(temp.getChildObjects());
				}
			}
			children.removeAll(pathObjectsToRemove);
			boolean keepChildren = true;
			if (!children.isEmpty()) {
				DialogButton response = Dialogs.showYesNoCancelDialog("Delete all annotations", "Keep descendant objects?");
				if (response == DialogButton.CANCEL)
					return;
				keepChildren = response == DialogButton.YES;
			}
			hierarchy.getSelectionModel().clearSelection();
			hierarchy.removeObjects(pathObjectsToRemove, keepChildren);
		});
		action.setLongText("Clear all annotations in the list");
		return action;
	}



	/**
	 * Binds the width properties to the GridPane width.
	 * 
	 * @param controls
	 * @return
	 */
	public static GridPane createColumnGridControls(final Control... controls) {
		GridPane pane = new GridPane();
		int n = controls.length;
		for (int i = 0; i < n; i++) {
			ColumnConstraints col = new ColumnConstraints();
			col.setPercentWidth(100.0/n);			
			pane.getColumnConstraints().add(col);
			Control control = controls[i];
			pane.add(control, i, 0);
			control.prefWidthProperty().bind(pane.widthProperty().divide(n));
		}
		RowConstraints row = new RowConstraints();
		row.setPercentHeight(100);
		pane.getRowConstraints().add(row);
		return pane;
	}


	public Pane getPane() {
		return pane;
	}



	static <T> void refreshList(final ListView<T> listView) {
		if (Platform.isFxApplicationThread()) {
			listView.refresh();
		} else
			Platform.runLater(() -> refreshList(listView));
	}



	void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData == imageData)
			return;

		// Deal with listeners for the current ImageData
		if (this.hierarchy != null) {
			hierarchy.removePathObjectListener(this);
			hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
		}
		this.imageData = imageData;
		if (this.imageData != null) {
			hierarchy = imageData.getHierarchy();
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
			hierarchy.addPathObjectListener(this);
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			listAnnotations.getItems().setAll(hierarchy.getAnnotationObjects());
			hierarchy.getSelectionModel().setSelectedObject(selected);
		} else {
			listAnnotations.getItems().clear();
		}
	}


	public ImageData<BufferedImage> getImageData() {
		return imageData;
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




	public static void promptToSetActiveAnnotationProperties(final PathObjectHierarchy hierarchy) {
		PathObject currentObject = hierarchy.getSelectionModel().getSelectedObject();
		if (currentObject == null || !currentObject.isAnnotation())
			return;
		ROI roi = currentObject.getROI();
		if (roi == null)
			return;
		if (promptToSetAnnotationProperties((PathAnnotationObject)currentObject)) {
			hierarchy.fireObjectsChangedEvent(null, Collections.singleton(currentObject));
			// Ensure the object is still selected
			hierarchy.getSelectionModel().setSelectedObject(currentObject);
		}
	}



	static boolean promptToSetAnnotationProperties(final PathAnnotationObject annotation) {


		GridPane panel = new GridPane();
		panel.setVgap(5);
		panel.setHgap(5);
		TextField textField = new TextField();
		if (annotation.getName() != null)
			textField.setText(annotation.getName());
		textField.setPrefColumnCount(20);
		// Post focus request to run later, after dialog displayed
		Platform.runLater(() -> textField.requestFocus());
		
		panel.add(new Label("Name "), 0, 0);
		panel.add(textField, 1, 0);

		boolean promptForColor = true;
		ColorPicker panelColor = null;
		if (promptForColor) {
			panelColor = new ColorPicker(ColorToolsFX.getDisplayedColor(annotation));
			panel.add(new Label("Color "), 0, 1);
			panel.add(panelColor, 1, 1);
			panelColor.prefWidthProperty().bind(textField.widthProperty());
		}
		
		Label labDescription = new Label("Description");
		TextArea textAreaDescription = new TextArea(annotation.getDescription());
		textAreaDescription.setPrefRowCount(3);
		textAreaDescription.setPrefColumnCount(25);
		labDescription.setLabelFor(textAreaDescription);
		panel.add(labDescription, 0, 2);
		panel.add(textAreaDescription, 1, 2);
		
		CheckBox cbLocked = new CheckBox("");
		cbLocked.setSelected(annotation.isLocked());
		Label labelLocked = new Label("Locked");
		panel.add(labelLocked, 0, 3);
		labelLocked.setLabelFor(cbLocked);
		panel.add(cbLocked, 1, 3);

		if (!Dialogs.showConfirmDialog("Set annotation properties", panel))
			return false;

		String name = textField.getText().trim();
		if (name.length() > 0)
			annotation.setName(name);
		else
			annotation.setName(null);
		if (promptForColor)
			annotation.setColorRGB(ColorToolsFX.getARGB(panelColor.getValue()));

		// Set the description only if we have to
		String description = textAreaDescription.getText();
		if (description == null || description.isEmpty())
			annotation.setDescription(null);
		else
			annotation.setDescription(description);
		
		annotation.setLocked(cbLocked.isSelected());
		
		return true;
	}

	

	@Override
	public void selectedPathObjectChanged(final PathObject pathObjectSelected, final PathObject previousObject, Collection<PathObject> allSelected) {
		if (!Platform.isFxApplicationThread()) {
			// Do not synchronize to changes on other threads (since these may interfere with scripts)
//			Platform.runLater(() -> selectedPathObjectChanged(pathObjectSelected, previousObject, allSelected));
			return;
		}

		if (changingSelection)
			return;
		
		changingSelection = true;
		if (synchronizePrimarySelectionOnly) {
			try {
				var listSelectionModel = listAnnotations.getSelectionModel();
				listSelectionModel.clearSelection();
				if (pathObjectSelected != null && pathObjectSelected.isAnnotation()) {
					listSelectionModel.select(pathObjectSelected);
					listAnnotations.scrollTo(pathObjectSelected);
				}
				return;
			} finally {
				changingSelection = false;
			}
		}
		
		try {
			
			var hierarchySelected = new TreeSet<>(DefaultPathObjectComparator.getInstance());
			hierarchySelected.addAll(allSelected);
			
			// Determine the objects to select
			MultipleSelectionModel<PathObject> model = listAnnotations.getSelectionModel();
			List<PathObject> selected = new ArrayList<>();
			for (PathObject pathObject : hierarchySelected) {
				if (pathObject == null)
					logger.warn("Selected object is null!");
				else if (pathObject.isAnnotation())
					selected.add(pathObject);
			}
			if (selected.isEmpty()) {
				if (!model.isEmpty())
					model.clearSelection();
				return;
			}
			// Check if we're making changes
			List<PathObject> currentlySelected = model.getSelectedItems();
			if (selected.size() == currentlySelected.size() && (hierarchySelected.containsAll(currentlySelected))) {
				listAnnotations.refresh();
				return;
			}
			
//			System.err.println("Starting...");
//			System.err.println(hierarchy.getAnnotationObjects().size());
//			System.err.println(hierarchySelected.size());
//			System.err.println(listAnnotations.getItems().size());
			if (hierarchySelected.containsAll(listAnnotations.getItems())) {
				model.selectAll();
				return;
			}
			
	//		System.err.println("Setting " + currentlySelected + " to " + selected);
			int[] inds = new int[selected.size()];
			int i = 0;
			model.clearSelection();
			boolean firstInd = true;
			for (PathObject temp : selected) {
				int idx = listAnnotations.getItems().indexOf(temp);
				if (idx >= 0 && firstInd) {
					Arrays.fill(inds, idx);
					firstInd = false;
				}
				inds[i] = idx;
				i++;
			}
			
			if (inds.length == 1 && pathObjectSelected instanceof PathAnnotationObject)
				listAnnotations.scrollTo(pathObjectSelected);
			
			if (firstInd) {
				changingSelection = false;
				return;
			}
			if (inds.length == 1)
				model.select(inds[0]);
			else if (inds.length > 1)
				model.selectIndices(inds[0], inds);
		} finally {
			changingSelection = false;			
		}
	}





	// TODO: Fix this so that it can't get the class names out of sync with the PathClassFactory
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
//			textField.setText(name);
//			panelName.setLeft(new Label("Class name"));
//			panelName.setCenter(textField);
		}

		panel.setTop(panelName);
		ColorPicker panelColor = new ColorPicker(color);

		panel.setCenter(panelColor);

		if (!Dialogs.showConfirmDialog("Edit class", panel))
			return false;

//		String newName = textField.getText().trim();
		Color newColor = panelColor.getValue();
//		if ((name.length() == 0 || name.equals(newName)) && newColor.equals(color))
//			return false;

		Integer colorValue = newColor.isOpaque() ? ColorToolsFX.getRGB(newColor) : ColorToolsFX.getARGB(newColor);
		if (defaultColor) {
			if (newColor.isOpaque())
				PathPrefs.setColorDefaultObjects(colorValue);
			else
				PathPrefs.setColorDefaultObjects(colorValue);
		}
		else {
//			if (!name.equals(pathClass.getName()) && PathClassFactory.pathClassExists(newName)) {
//				logger.warn("Modified name already exists - cannot rename");
//				return false;
//			}
//			pathClass.setName(newName);
			pathClass.setColor(colorValue);
		}
		return true;
	}



	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}



	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}
		
		if (hierarchy == null) {
			listAnnotations.getItems().clear();
			return;
		}

		Collection<PathObject> newList = hierarchy.getObjects(new HashSet<>(), PathAnnotationObject.class);
		listClasses.refresh();
		// If the lists are the same, we just need to refresh the appearance (because e.g. classifications or measurements now differ)
		// For some reason, 'equals' alone wasn't behaving nicely (perhaps due to ordering?)... so try a more manual test instead
//		if (newList.equals(listAnnotations.getItems())) {
		if (newList.size() == listAnnotations.getItems().size() && newList.containsAll(listAnnotations.getItems())) {
			// Don't refresh unless there is good reason to believe the list should appear different now
			// This was introduced due to flickering as annotations were dragged
			// TODO: Reconsider when annotation list is refreshed
			
//			listAnnotations.setStyle(".list-cell:empty {-fx-background-color: white;}");
			
//			if (event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
			if (!event.isChanging())
				listAnnotations.refresh();
			return;
		}
		// If the lists are different, we need to update accordingly
		listAnnotations.getSelectionModel().clearSelection();
		listAnnotations.getItems().setAll(newList);
	}
	
	
	/**
	 * Remove all the classifications for a particular class.
	 * 
	 * @param pathClass
	 */
	static void resetAnnotationClassifications(final PathObjectHierarchy hierarchy, final PathClass pathClass) {
		List<PathObject> changedList = new ArrayList<>();
		for (PathObject pathObject : hierarchy.getAnnotationObjects()) {
			if (pathClass.equals(pathObject.getPathClass())) {
				pathObject.setPathClass(null);
				changedList.add(pathObject);
			}
		}
		if (!changedList.isEmpty())
			hierarchy.fireObjectClassificationsChangedEvent(null, changedList);
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
	 * A {@link ListCell} for displaying {@linkplain PathObject PathObjects}, including ROI icons.
	 */
	static class PathAnnotationListCell extends ListCell<PathObject> {

		private Tooltip tooltip;

		@Override
		protected void updateItem(PathObject value, boolean empty) {
			super.updateItem(value, empty);
			updateTooltip(value);
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			setText(value.toString());

			int w = 16;
			int h = 16;

			if (value.hasROI())
				setGraphic(PathIconFactory.createPathObjectIcon(value, w, h));
			else
				setGraphic(null);
		}

		void updateTooltip(final PathObject pathObject) {
			if (tooltip == null) {
				if (pathObject == null || !pathObject.isAnnotation())
					return;
				tooltip = new Tooltip();
				setTooltip(tooltip);
			} else if (pathObject == null || !pathObject.isAnnotation()) {
				setTooltip(null);
				return;
			}
			PathAnnotationObject annotation = (PathAnnotationObject)pathObject;
			String description = annotation.getDescription();
			if (description == null) {
				setTooltip(null);
			} else {
				tooltip.setText(description);
				setTooltip(tooltip);
			}
		}

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