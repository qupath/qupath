/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener.Change;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.tools.PathObjectLabels;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;


/**
 * Component for displaying annotations within the active image.
 * <p>
 * Also shows the {@link PathClass} list.
 * 
 * @author Pete Bankhead
 *
 */
public class AnnotationPane implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {

	private static final Logger logger = LoggerFactory.getLogger(AnnotationPane.class);

	private QuPathGUI qupath;
	
	// Need to preserve this to guard against garbage collection
	@SuppressWarnings("unused")
	private ObservableValue<ImageData<BufferedImage>> imageDataProperty;
	// Simply taken from imageDataProperty
	private ImageData<BufferedImage> imageData;
	
	private BooleanProperty disableUpdates = new SimpleBooleanProperty(false);
	
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
	private boolean suppressSelectionChanges = false;
	
	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 */
	public AnnotationPane(final QuPathGUI qupath) {
		this(qupath, qupath.imageDataProperty());
	}
	
	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 * @param imageDataProperty the current {@link ImageData}
	 */
	public AnnotationPane(final QuPathGUI qupath, ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
		this.qupath = qupath;
		this.imageDataProperty = imageDataProperty;
		this.disableUpdates.addListener((v, o, n) -> {
			if (!n)
				enableUpdates();
		});
		
		pathClassPane = new PathClassPane(qupath);
		setImageData(imageDataProperty.getValue());
		
		Pane paneAnnotations = createAnnotationsPane();
		
//		GridPane paneColumns = PaneTools.createColumnGrid(panelObjects, panelClasses);
		SplitPane paneColumns = new SplitPane(
				paneAnnotations,
				pathClassPane.getPane()
				);
		paneColumns.setDividerPositions(0.5);
		pane.setCenter(paneColumns);
		imageDataProperty.addListener(this);
	}
	
	/**
	 * Property that may be used to prevent updates on every hierarchy or selection change event.
	 * This can be used to improve performance by preventing the list being updated even when 
	 * it is not visible to the user.
	 * @return
	 */
	public BooleanProperty disableUpdatesProperty() {
		return disableUpdates;
	}
	
	private void enableUpdates() {
		if (hierarchy == null)
			return;
		hierarchyChanged(PathObjectHierarchyEvent.createStructureChangeEvent(this, hierarchy, hierarchy.getRootObject()));
		selectedPathObjectChanged(hierarchy.getSelectionModel().getSelectedObject(), null, hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	private Pane createAnnotationsPane() {
		listAnnotations = new ListView<>();
		hierarchyChanged(null); // Force update

		listAnnotations.setCellFactory(v -> PathObjectLabels.createListCell());

		listAnnotations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		listAnnotations.getSelectionModel().getSelectedItems().addListener(
				(Change<? extends PathObject> c) -> synchronizeHierarchySelectionToListSelection()
		);
		listAnnotations.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeHierarchySelectionToListSelection());

		listAnnotations.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
				if (pathObject == null || !pathObject.hasROI())
					return;
				qupath.getViewer().centerROI(pathObject.getROI());
			}
		});
		
		PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> listAnnotations.refresh());

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
		
		// Add support for delete/backspace
		listAnnotations.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
			if (e.isConsumed())
				return;
			if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE) {
				btnDelete.fire();
				e.consume();
			}
		});

		panelObjects.setBottom(panelButtons);
		return panelObjects;
	}
	
	
	/**
	 * Update the selected objects in the hierarchy to match those in the list, 
	 * unless selection changes should be suppressed.
	 */
	void synchronizeHierarchySelectionToListSelection() {
		if (hierarchy == null || suppressSelectionChanges)
			return;
		suppressSelectionChanges = true;
		Set<PathObject> selectedSet = new HashSet<>(listAnnotations.getSelectionModel().getSelectedItems());
		PathObject selectedObject = listAnnotations.getSelectionModel().getSelectedItem();
		if (!selectedSet.contains(selectedObject))
			selectedObject = null;
		hierarchy.getSelectionModel().setSelectedObjects(selectedSet, selectedObject);
		suppressSelectionChanges = false;
	}

	/**
	 * Get the pane for display.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}


	void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData == imageData)
			return;

		// Deal with listeners for the current ImageData
		if (this.hierarchy != null) {
			hierarchy.removeListener(this);
			hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
		}
		this.imageData = imageData;
		if (this.imageData != null) {
			hierarchy = imageData.getHierarchy();
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
			hierarchy.addListener(this);
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

		if (suppressSelectionChanges || disableUpdates.get())
			return;
		
		suppressSelectionChanges = true;
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
				suppressSelectionChanges = false;
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
				suppressSelectionChanges = false;
				return;
			}
			if (inds.length == 1)
				model.select(inds[0]);
			else if (inds.length > 1)
				model.selectIndices(inds[0], inds);
		} finally {
			suppressSelectionChanges = false;			
		}
	}





	
	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
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
		
		if (disableUpdates.get())
			return;

		// Create a sorted list of annotations
		List<PathObject> newList = new ArrayList<>(hierarchy.getObjects(new HashSet<>(), PathAnnotationObject.class));
		Collections.sort(newList, annotationListComparator);
				
		pathClassPane.getListView().refresh();
		// If the lists are the same, we just need to refresh the appearance (because e.g. classifications or measurements now differ)
		// For some reason, 'equals' alone wasn't behaving nicely (perhaps due to ordering?)... so try a more manual test instead
		if (newList.equals(listAnnotations.getItems())) {
			// Don't refresh unless there is good reason to believe the list should appear different now
			// This was introduced due to flickering as annotations were dragged
			// TODO: Reconsider when annotation list is refreshed
//			if (event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
			if (!event.isChanging())
				listAnnotations.refresh();
			return;
		}
		// If the lists are different, we need to update accordingly - but we don't want to trigger accidental selection updates
//		listAnnotations.getSelectionModel().clearSelection(); // Clearing the selection would cause annotations to disappear when interactively training a classifier!
		boolean lastChanging = suppressSelectionChanges;
		suppressSelectionChanges = true;
		listAnnotations.getItems().setAll(newList);
		suppressSelectionChanges = lastChanging;
	}
	
	
	static Comparator<PathObject> annotationListComparator = Comparator.nullsFirst(Comparator
				.comparingInt((PathObject p) -> p.hasROI() ? p.getROI().getT() : -1)
				.thenComparingInt(p -> p.hasROI() ? p.getROI().getZ() : -1)
				.thenComparing(p -> p.toString())
				.thenComparingDouble(p -> p.hasROI() ? p.getROI().getBoundsY(): -1)
				.thenComparingDouble(p -> p.hasROI() ? p.getROI().getBoundsX(): -1)
				.thenComparing(p -> p.getID().toString())
				);
	
	
}