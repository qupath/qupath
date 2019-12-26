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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
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

		ContextMenu menuAnnotations = QuPathGUI.populateAnnotationsMenu(qupath, new ContextMenu());
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
		Button btnMore = new Button("\u22EE");
		btnMore.setTooltip(new Tooltip("More options"));
		btnMore.setOnAction(e -> {
			menuAnnotations.show(btnMore, Side.RIGHT, 0, 0);
		});
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
	
	
	/**
	 * TODO: Make split points accessible through the GUI
	 * @param hierarchy
	 */
	static void splitPoints(PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
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
	}
	
	/**
	 * TODO: Make merge points accessible through the GUI
	 * @param hierarchy
	 */
	static void mergePointsForClass(PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		if (pathObject == null || !PathObjectTools.hasPointROI(pathObject) || !pathObject.isAnnotation())
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
	}

	

	public static void promptToSetActiveAnnotationProperties(final PathObjectHierarchy hierarchy) {
		PathObject currentObject = hierarchy.getSelectionModel().getSelectedObject();
		if (currentObject == null || !currentObject.isAnnotation())
			return;
		ROI roi = currentObject.getROI();
		if (roi == null)
			return;
		
		Collection<PathAnnotationObject> otherAnnotations = hierarchy.getSelectionModel().getSelectedObjects().stream()
				.filter(p -> p.isAnnotation() && p != currentObject)
				.map(p -> (PathAnnotationObject)p)
				.collect(Collectors.toList());
		
		if (promptToSetAnnotationProperties((PathAnnotationObject)currentObject, otherAnnotations)) {
			hierarchy.fireObjectsChangedEvent(null, Collections.singleton(currentObject));
			// Ensure the object is still selected
			hierarchy.getSelectionModel().setSelectedObject(currentObject);
		}
	}



	static boolean promptToSetAnnotationProperties(final PathAnnotationObject annotation, Collection<PathAnnotationObject> otherAnnotations) {
		
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
		
		
		CheckBox cbAll = new CheckBox("");
		boolean hasOthers = otherAnnotations != null && !otherAnnotations.isEmpty();
		cbAll.setSelected(hasOthers);
		Label labelApplyToAll = new Label("Apply to all");
		cbAll.setTooltip(new Tooltip("Apply properties to all " + (otherAnnotations.size() + 1) + " selected annotations"));
		if (hasOthers) {
			panel.add(labelApplyToAll, 0, 4);
			labelApplyToAll.setLabelFor(cbAll);
			panel.add(cbAll, 1, 4);
		}
		

		if (!Dialogs.showConfirmDialog("Set annotation properties", panel))
			return false;
		
		List<PathAnnotationObject> toChange = new ArrayList<>();
		toChange.add(annotation);
		if (cbAll.isSelected())
			toChange.addAll(otherAnnotations);
		
		String name = textField.getText().trim();
		
		for (var temp : toChange) {
			if (name.length() > 0)
				temp.setName(name);
			else
				temp.setName(null);
			if (promptForColor)
				temp.setColorRGB(ColorToolsFX.getARGB(panelColor.getValue()));
	
			// Set the description only if we have to
			String description = textAreaDescription.getText();
			if (description == null || description.isEmpty())
				temp.setDescription(null);
			else
				temp.setDescription(description);
			
			temp.setLocked(cbLocked.isSelected());
		}
		
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
	
}