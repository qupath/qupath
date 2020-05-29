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

package qupath.lib.gui.commands;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.PathObjectListCell;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;

/**
 * Component for creating and modifying point objects.
 * 
 * @author Pete Bankhead
 *
 */
class CountingPane implements PathObjectSelectionListener, PathObjectHierarchyListener {

	private QuPathGUI qupath;
	
	private BorderPane pane = new BorderPane();
	
	private PathObjectHierarchy hierarchy;
	
	private ListView<PathObject> listCounts;
	
	private Action btnAdd = new Action("Add", e -> {
		PathObject pathObjectCounts = PathObjects.createAnnotationObject(ROIs.createPointsROI(ImagePlane.getDefaultPlane()));
		hierarchy.addPathObject(pathObjectCounts);
//		hierarchy.fireChangeEvent(pathObjectCounts.getParent());
		hierarchy.getSelectionModel().setSelectedObject(pathObjectCounts);
//		promptToSetProperties();
	});
	private Action btnEdit = new Action("Edit", e -> promptToSetProperties());
	private Action btnDelete = new Action("Delete", e -> {
		PathObject pathObjectSelected = listCounts.getSelectionModel().getSelectedItem();
		if (pathObjectSelected != null && PathObjectTools.hasPointROI(pathObjectSelected))
			GuiTools.promptToRemoveSelectedObject(pathObjectSelected, hierarchy);
	});
	
	/**
	 * Create point annotations for all available classifications
	 */
	private Action btnCreateForClasses = new Action("Create points for all classes", e -> {
		var viewer = qupath.getViewer();
		var hierarchy = viewer.getHierarchy();
		var availableClasses = qupath.getAvailablePathClasses()
				.stream()
				.filter(p -> p != null && p != PathClassFactory.getPathClassUnclassified())
				.collect(Collectors.toList());
		if (hierarchy == null || availableClasses.isEmpty())
			return;
		var plane = viewer.getImagePlane();
		var pathObjects = new ArrayList<PathObject>();
		for (PathClass pathClass : availableClasses) {
			pathObjects.add(PathObjects.createAnnotationObject(ROIs.createPointsROI(plane), pathClass));
		}
		hierarchy.addPathObjects(pathObjects);
	});
	
	
	public CountingPane(final QuPathGUI qupath, final PathObjectHierarchy hierarchy) {
		
		this.qupath = qupath;
		listCounts = new ListView<>();
		
		setHierarchy(hierarchy);
		
		listCounts.getSelectionModel().selectedItemProperty().addListener((e, oldSelection, selectedObject) -> {
			updateSelectedObjectFromList(selectedObject);
		});
		
		// Make buttons
		GridPane paneMainButtons = PaneTools.createColumnGridControls(
				ActionUtils.createButton(btnAdd),
				ActionUtils.createButton(btnEdit),
				ActionUtils.createButton(btnDelete)
				);
		
		// Add additional options
		var popup = new ContextMenu();
		popup.getItems()
			.add(ActionUtils.createMenuItem(btnCreateForClasses));
		
		var paneButtons = new BorderPane(paneMainButtons);
		Button btnMore = GuiTools.createMoreButton(popup, Side.RIGHT);
		paneButtons.setRight(btnMore);
				
		// Add double-click listener
		listCounts.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1)
				promptToSetProperties();
			// TODO: Consider reimplementing deselection
//			else {
//				int index = listCounts.locationToIndex(e.getPoint());
//				if (index < 0)
//					return;
//				if (!listCounts.getCellBounds(index, index).contains(e.getPoint())) {
//					hierarchy.getSelectionModel().resetSelection();
//					//						listCounts.clearSelection();
//					//						e.consume();
//				}
//			}
		}
				);
		ContextMenu menu = new ContextMenu();
		Menu menuSetClass = new Menu("Set class");
		menu.setOnShowing(e -> {
			menuSetClass.getItems().setAll(
					qupath.getAvailablePathClasses().stream()
					.map(p -> createPathClassMenuItem(p))
					.collect(Collectors.toList()));
		});
		MenuItem miCopy = new MenuItem("Copy to clipboard");
		miCopy.setOnAction(e -> {
			copyCoordinatesToClipboard(listCounts.getSelectionModel().getSelectedItem());
			}
		);
		miCopy.disableProperty().bind(listCounts.getSelectionModel().selectedItemProperty().isNull());
		menuSetClass.disableProperty().bind(listCounts.getSelectionModel().selectedItemProperty().isNull());
		menu.getItems().addAll(menuSetClass, miCopy);
		listCounts.setContextMenu(menu);
		
		listCounts.setCellFactory(v -> new PathObjectListCell(p -> p.toString().replace(" (Points)", "")));
		
		
		PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> listCounts.refresh());
		
		// Add to panel
		BorderPane panelList = new BorderPane();
		panelList.setCenter(listCounts);
		panelList.setBottom(paneButtons);
//		panelList.setBorder(BorderFactory.createTitledBorder("Counts"));		
		
		pane.setCenter(panelList);
	}
	
	
	MenuItem createPathClassMenuItem(PathClass pathClass) {
		var mi = new MenuItem(pathClass.toString());
		var rect = new Rectangle(8, 8, ColorToolsFX.getCachedColor(pathClass.getColor()));
		mi.setGraphic(rect);
		mi.setOnAction(e -> {
			var pathObject = listCounts.getSelectionModel().getSelectedItem();
			if (pathClass == PathClassFactory.getPathClassUnclassified())
				pathObject.setPathClass(null);
			else
				pathObject.setPathClass(pathClass);
			if (hierarchy != null)
				hierarchy.fireObjectClassificationsChangedEvent(mi, Collections.singleton(pathObject));
		});
		return mi;
	}
	
	
	public Pane getPane() {
		return pane;
	}
	
	public ListView<PathObject> getListView() {
		return listCounts;
	}
	
	
	public List<PathObject> getPathObjects() {
		return new ArrayList<>(listCounts.getItems());
	}
	
	
	
	public void setHierarchy(PathObjectHierarchy hierarchy) {
		if (this.hierarchy == hierarchy)
			return;
		if (this.hierarchy != null) {
			this.hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
			this.hierarchy.removePathObjectListener(this);
		}
		this.hierarchy = hierarchy;
		PathObject objectSelected = null;
		
		if (this.hierarchy != null) {
			PathObjectSelectionModel model = this.hierarchy.getSelectionModel();
			model.addPathObjectSelectionListener(this);
			objectSelected = model.getSelectedObject();
			this.hierarchy.addPathObjectListener(this);
		}
		// Update selected object in list, if suitable
		if (objectSelected != null && PathObjectTools.hasPointROI(objectSelected))
			listCounts.getSelectionModel().select(objectSelected);
		else
			listCounts.getSelectionModel().clearSelection();
		
		// Force update
		hierarchyChanged(null);
	}
	
	
	public static void copyCoordinatesToClipboard(PathObject pathObject) {
//		PathObject pathObject = viewer.getPathObjectHierarchy().getSelectionModel().getSelectedPathObject();
		if (pathObject == null || !pathObject.hasROI() || !(pathObject.getROI() instanceof PointsROI)) {
			Dialogs.showErrorMessage("Copy points to clipboard", "No points selected!");
			return;
		}
		StringBuilder sb = new StringBuilder();
		String name = pathObject.getDisplayedName();
		PointsROI points = (PointsROI)pathObject.getROI();
		for (Point2 p : points.getAllPoints())
			sb.append(name).append("\t").append(p.getX()).append("\t").append(p.getY()).append("\n");

		StringSelection stringSelection = new StringSelection(sb.toString());
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(stringSelection, null);
	}
	
	
	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
		// Check if we have points
		boolean hasPoints = pathObjectSelected != null && PathObjectTools.hasPointROI(pathObjectSelected);
		btnEdit.setDisabled(!hasPoints);
		btnDelete.setDisabled(!hasPoints);
		if (pathObjectSelected == listCounts.getSelectionModel().getSelectedItem())
			return;
		if (pathObjectSelected != null && PathObjectTools.hasPointROI(pathObjectSelected))
			listCounts.getSelectionModel().select(pathObjectSelected);
		else
			listCounts.getSelectionModel().clearSelection();
	}


	private void promptToSetProperties() {
		PathObject pathObjectSelected = listCounts.getSelectionModel().getSelectedItem();
		if (pathObjectSelected != null && PathObjectTools.hasPointROI(pathObjectSelected)) {
			GuiTools.promptToSetActiveAnnotationProperties(hierarchy);
		}
	}

	private void updateSelectedObjectFromList(final PathObject selectedObject) {
		if (selectedObject == null || hierarchy == null || hierarchy.getSelectionModel().getSelectedObject() == selectedObject)
			return;
		// Set the currently-selected object
		hierarchy.getSelectionModel().setSelectedObject(selectedObject);
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (hierarchy == null) {
			listCounts.getItems().clear();
			return;
		} else if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}
		
		Collection<PathObject> newList = hierarchy.getPointObjects(PathAnnotationObject.class);
		
		// We want to avoid shuffling the list if possible we adding points
		var items = listCounts.getItems();
		if (items.size() == newList.size() && (
				newList.equals(items) || newList.containsAll(items))) {
//			if (event != null && event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
			listCounts.refresh();
		} else {
			// Update the items if we need to
			listCounts.getItems().setAll(newList);
		}
		
		// We want to retain selection status
		selectedPathObjectChanged(hierarchy.getSelectionModel().getSelectedObject(), null, hierarchy.getSelectionModel().getSelectedObjects());
	}	
	
	
}