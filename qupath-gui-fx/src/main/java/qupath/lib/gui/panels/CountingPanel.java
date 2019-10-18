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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.lib.geom.Point2;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
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
public class CountingPanel implements PathObjectSelectionListener, PathObjectHierarchyListener {

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
			DisplayHelpers.promptToRemoveSelectedObject(pathObjectSelected, hierarchy);
	});
	
	
	public CountingPanel(final PathObjectHierarchy hierarchy) {
		
		listCounts = new ListView<>();
		
		setHierarchy(hierarchy);
		
		listCounts.getSelectionModel().selectedItemProperty().addListener((e, oldSelection, selectedObject) -> {
			updateSelectedObjectFromList(selectedObject);
		});
		
		// Make buttons
		GridPane panelButtons = PathAnnotationPanel.createColumnGridControls(
				ActionUtils.createButton(btnAdd),
				ActionUtils.createButton(btnEdit),
				ActionUtils.createButton(btnDelete)
				);
				
				
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
		MenuItem menuItem = new MenuItem("Copy to clipboad");
		menuItem.setOnAction(e -> {
			copyCoordinatesToClipboard(listCounts.getSelectionModel().getSelectedItem());
			}
		);
		menu.getItems().add(menuItem);
		listCounts.setContextMenu(menu);
		
		// Add to panel
		BorderPane panelList = new BorderPane();
		panelList.setCenter(listCounts);
		panelList.setBottom(panelButtons);
//		panelList.setBorder(BorderFactory.createTitledBorder("Counts"));		
		
		pane.setCenter(panelList);
	}
	
	
	public Pane getPane() {
		return pane;
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
			DisplayHelpers.showErrorMessage("Copy points to clipboard", "No points selected!");
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
			PathAnnotationPanel.promptToSetActiveAnnotationProperties(hierarchy);
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
		
		if (newList.equals(listCounts.getItems())) {
//			if (event != null && event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
				listCounts.refresh();
			return;
		}
		
		listCounts.getItems().setAll(newList);
	}	
	
	
}