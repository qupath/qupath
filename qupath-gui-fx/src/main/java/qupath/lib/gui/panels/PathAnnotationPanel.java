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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
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
	private BooleanProperty hasImageData = new SimpleBooleanProperty(false);
	
	private BorderPane pane = new BorderPane();

	/*
	 * Request that we only synchronize to the primary selection; otherwise synchronizing to 
	 * multiple selections from long lists can be a performance bottleneck
	 */
	private static boolean synchronizePrimarySelectionOnly = true;
	
	private PathClassPane pathClassPane;
	
	/*
	 * List displaying annotations in the current hierarchy
	 */
	private ListView<PathObject> listAnnotations;
		
	/*
	 * Selection being changed by outside forces, i.e. don't fire an event
	 */
	private boolean changingSelection = false;
	
	
	
	public PathAnnotationPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		pathClassPane = new PathClassPane(qupath);
		setImageData(qupath.getImageData());
		
		Pane paneAnnotations = createAnnotationsPane();
		
//		GridPane paneColumns = PaneTools.createColumnGrid(panelObjects, panelClasses);
		SplitPane paneColumns = new SplitPane(
				paneAnnotations,
				pathClassPane.getPane()
				);
		paneColumns.setDividerPositions(0.5);
		pane.setCenter(paneColumns);
		qupath.addImageDataChangeListener(this);
	}
	
	
	private Pane createAnnotationsPane() {
		listAnnotations = new ListView<>();
		hierarchyChanged(null); // Force update

		listAnnotations.setCellFactory(v -> new PathObjectListCell());

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

		ContextMenu menuAnnotations = GuiTools.populateAnnotationsMenu(qupath, new ContextMenu());
		listAnnotations.setContextMenu(menuAnnotations);

		// Add the main annotation list
		BorderPane panelObjects = new BorderPane();
		panelObjects.setCenter(listAnnotations);

		// Add buttons
		Button btnSelectAll = new Button("Select all");
		btnSelectAll.setOnAction(e -> listAnnotations.getSelectionModel().selectAll());
		btnSelectAll.setTooltip(new Tooltip("Select all annotations"));

		Button btnDelete = new Button("Delete");
		btnDelete.setOnAction(e -> GuiTools.promptToClearAllSelectedObjects(imageData));
		btnDelete.setTooltip(new Tooltip("Delete all selected objects"));

		// Create a button to show context menu (makes it more obvious to the user that it exists)
		Button btnMore = GuiTools.createMoreButton(menuAnnotations, Side.RIGHT);
		GridPane panelButtons = new GridPane();
		panelButtons.add(btnSelectAll, 0, 0);
		panelButtons.add(btnDelete, 1, 0);
		panelButtons.add(btnMore, 2, 0);
		GridPane.setHgrow(btnSelectAll, Priority.ALWAYS);
		GridPane.setHgrow(btnDelete, Priority.ALWAYS);

		PaneTools.setMaxWidth(Double.MAX_VALUE,
				btnSelectAll, btnDelete);
		
		BooleanBinding disableButtons = hasImageData.not();
		btnSelectAll.disableProperty().bind(disableButtons);
		btnDelete.disableProperty().bind(disableButtons);
		btnMore.disableProperty().bind(disableButtons);

		panelObjects.setBottom(panelButtons);
		return panelObjects;
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
	
	
	
//	void promptToDeleteAnnotations() {
//		Action action = new Action("Delete", e -> {
//			if (hierarchy == null)
//				return;
//			// TODO: Consider reusing selected object deletion code within viewer
//			// Remove all the selected ROIs
//			List<PathObject> pathObjectsToRemove = new ArrayList<>(listAnnotations.getSelectionModel().getSelectedItems());
//			if (pathObjectsToRemove == null || pathObjectsToRemove.isEmpty())
//				return;
//			int nObjects = pathObjectsToRemove.size();
//			if (!Dialogs.showYesNoDialog("Delete annotations",
//					String.format("Delete %d %s?", nObjects, nObjects == 1 ? "annotation" : "annotations")))
//				return;
//			// Check for descendant objects
//			List<PathObject> descendantList = new ArrayList<>();
//			for (PathObject parent : pathObjectsToRemove)
//				PathObjectTools.getFlattenedObjectList(parent, descendantList, false);
//			descendantList.removeAll(pathObjectsToRemove);
//			int nDescendants = descendantList.size();
//			boolean keepChildren = true;
//			if (nDescendants > 0) {
//				DialogButton result = Dialogs.showYesNoCancelDialog("Delete annotations",
//						String.format("Keep %d descendant %s?", nDescendants, nDescendants == 1 ? "object" : "objects"));
//				if (result == DialogButton.CANCEL)
//					return;
//				else if (result == DialogButton.YES)
//					keepChildren = true;
//				else
//					keepChildren = false;
//			}
//			hierarchy.getSelectionModel().clearSelection();
//			hierarchy.removeObjects(pathObjectsToRemove, keepChildren);
//		});
//		action.setLongText("Delete the currently-selected annotations");
//		return action;
//	}


	public Pane getPane() {
		return pane;
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
		hasImageData.set(this.imageData != null);
		pathClassPane.refresh();
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
		pathClassPane.getListView().refresh();
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
//		listAnnotations.getSelectionModel().clearSelection(); // Clearing the selection would cause annotations to disappear when interactively training a classifier!
		listAnnotations.getItems().setAll(newList);
	}
	
}