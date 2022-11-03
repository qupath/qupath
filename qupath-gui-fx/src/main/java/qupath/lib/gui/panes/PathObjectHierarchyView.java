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
import java.util.*;
import java.util.stream.Collectors;

import javafx.scene.control.*;
import org.controlsfx.control.BreadCrumbBar;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.DetectionTreeDisplayModes;
import qupath.lib.gui.tools.PathObjectLabels;
import qupath.lib.images.ImageData;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;


/**
 * Component for showing a tree-based view of the object hierarchy.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectHierarchyView implements ChangeListener<ImageData<BufferedImage>>, PathObjectSelectionListener, PathObjectHierarchyListener {
	
	/**
	 * Request that we only synchronize to the primary selection; otherwise synchronizing to 
	 * multiple selections from long lists can be a performance bottleneck
	 */
	private static boolean synchronizePrimarySelectionOnly = true;
	
	// Need to preserve this to guard against garbage collection
	@SuppressWarnings("unused")
	private ObservableValue<ImageData<BufferedImage>> imageDataProperty;
	
	private PathObjectHierarchy hierarchy;
	
	private BooleanProperty disableUpdates = new SimpleBooleanProperty(false);
	
	private TreeView<PathObject> treeView;
	private BorderPane treeViewPane = new BorderPane();
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public PathObjectHierarchyView(final QuPathGUI qupath) {
		this(qupath, qupath.imageDataProperty());
	}
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 * @param imageDataProperty the {@link ImageData} to display
	 */
	public PathObjectHierarchyView(final QuPathGUI qupath, ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
		
		this.imageDataProperty = imageDataProperty;
		this.disableUpdates.addListener((v, o, n) -> {
			if (!n)
				enableUpdates();
		});
		
		// Handle display changes
		treeView = new TreeView<>(createNode(new PathRootObject()));
		treeView.setCellFactory(t -> PathObjectLabels.createTreeCell());
		
		PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> treeView.refresh());
		PathPrefs.colorTMAProperty().addListener((v, o, n) -> treeView.refresh());
		PathPrefs.colorTMAMissingProperty().addListener((v, o, n) -> treeView.refresh());
		PathPrefs.colorTileProperty().addListener((v, o, n) -> treeView.refresh());
		
		treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		treeView.getSelectionModel().getSelectedItems().addListener(
				(ListChangeListener.Change<? extends TreeItem<PathObject>> c) -> synchronizeSelectionModelToTree(c)
		);
		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeSelectionModelToTree(null));
		// When nodes are expanded, we need to ensure selections are handled
		treeView.expandedItemCountProperty().addListener((v, o, n) -> synchronizeTreeToSelectionModel());
		
		setImageData(imageDataProperty.getValue());
		imageDataProperty.addListener(this);
		
		// Add popup to control detection display
		ContextMenu popup = new ContextMenu();
		ToggleGroup toggleGroup = new ToggleGroup();
		RadioMenuItem miWithIcons = new RadioMenuItem("With icons");
		miWithIcons.setToggleGroup(toggleGroup);
		miWithIcons.selectedProperty().addListener((v, o, n) -> {
			if (n)
				PathPrefs.detectionTreeDisplayModeProperty().set(DetectionTreeDisplayModes.WITH_ICONS);
		});


		RadioMenuItem miWithoutIcons = new RadioMenuItem("Without icons");
		miWithoutIcons.setToggleGroup(toggleGroup);
		miWithoutIcons.selectedProperty().addListener((v, o, n) -> {
			if (n)
				PathPrefs.detectionTreeDisplayModeProperty().set(DetectionTreeDisplayModes.WITHOUT_ICONS);
		});

		RadioMenuItem miHide = new RadioMenuItem("Hide detections");
		miHide.setToggleGroup(toggleGroup);
		miHide.selectedProperty().addListener((v, o, n) -> {
			if (n)
				PathPrefs.detectionTreeDisplayModeProperty().set(DetectionTreeDisplayModes.NONE);
		});
		// Ensure we have the right toggle selected
		miWithIcons.setSelected(PathPrefs.detectionTreeDisplayModeProperty().get() == DetectionTreeDisplayModes.WITH_ICONS);
		miWithoutIcons.setSelected(PathPrefs.detectionTreeDisplayModeProperty().get() == DetectionTreeDisplayModes.WITHOUT_ICONS);
		miHide.setSelected(PathPrefs.detectionTreeDisplayModeProperty().get() == DetectionTreeDisplayModes.NONE);

		// Add to menu
		Menu menuDetectionDisplay = new Menu("Detection display");
		menuDetectionDisplay.getItems().setAll(
			miWithIcons, miWithoutIcons, miHide
		);
		popup.getItems().setAll(
				menuDetectionDisplay
		);
		treeView.setContextMenu(popup);

		treeView.setShowRoot(false);
		
		treeViewPane.setCenter(treeView);
		BreadCrumbBar<PathObject> breadCrumbBar = new BreadCrumbBar<>();
		breadCrumbBar.setAutoNavigationEnabled(false);
		breadCrumbBar.setStyle("-fx-font-size: 0.8em;");
		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> breadCrumbBar.setSelectedCrumb(n));
		breadCrumbBar.setOnCrumbAction(e -> {
			if (e.getSelectedCrumb() != null && hierarchy != null)
				hierarchy.getSelectionModel().setSelectedObject(e.getSelectedCrumb().getValue());
		});
//		breadCrumbBar.selectedCrumbProperty().bind(treeView.getSelectionModel().selectedItemProperty());
		treeViewPane.setBottom(breadCrumbBar);
		
		// Update when display is changed
		PathPrefs.detectionTreeDisplayModeProperty().addListener((v, o, n) -> hierarchyChanged(null));
		
		// Center on double-click
		treeView.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				var item = treeView.getSelectionModel().getSelectedItem();
				var pathObject = item == null ? null : item.getValue();
				if (pathObject == null || !pathObject.hasROI())
					return;
				qupath.getViewer().centerROI(pathObject.getROI());
			}
		});
		
	}
	
	private void enableUpdates() {
		if (hierarchy == null)
			return;
		hierarchyChanged(PathObjectHierarchyEvent.createStructureChangeEvent(this, hierarchy, hierarchy.getRootObject()));
//		selectedPathObjectChanged(hierarchy.getSelectionModel().getSelectedObject(), null, hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	private boolean synchronizingModelToTree = false;
	private boolean synchronizingTreeToModel = false;
	
	/**
	 * Ensure that the hierarchy selection model matches the selection within the TreeView.
	 * @param change
	 */
	private void synchronizeSelectionModelToTree(final ListChangeListener.Change<? extends TreeItem<PathObject>> change) {
		if (synchronizingTreeToModel)
			return;
		
		PathObjectSelectionModel model = getHierarchySelectionModel();
		if (model == null) {
			return;
		}
		
		boolean wasSynchronizingToTree = synchronizingModelToTree;
		try {
			synchronizingModelToTree = true;
			
			// Check - was anything removed?
			boolean removed = false;
			if (change != null) {
				while (change.next())
					removed = removed | change.wasRemoved();
			}
			
			MultipleSelectionModel<TreeItem<PathObject>> treeModel = treeView.getSelectionModel();
			List<TreeItem<PathObject>> selectedItems = treeModel.getSelectedItems();
			
			// If we just have no selected items, and something was removed, then clear the selection
			if (selectedItems.isEmpty() && removed) {
				model.clearSelection();
				return;				
			}
			
			// If we just have one selected item, and also items were removed from the selection, then only select the one item we have
//			if (selectedItems.size() == 1 && removed) {
			if (selectedItems.size() == 1) {
				model.setSelectedObject(selectedItems.get(0).getValue(), false);
				return;
			}
			
			// If we have multiple selected items, we need to ensure that everything in the tree matches with everything in the selection model
			Set<PathObject> toSelect = treeView.getSelectionModel().getSelectedItems().stream().map(t -> t.getValue()).collect(Collectors.toSet());
			TreeItem<PathObject> mainSelection = treeView.getSelectionModel().getSelectedItem();
			PathObject primary = mainSelection == null ? null : mainSelection.getValue();
			model.setSelectedObjects(toSelect, primary);
		} finally {
			synchronizingModelToTree = wasSynchronizingToTree;
		}
	}
	
	
	private PathObjectSelectionModel getHierarchySelectionModel() {
		return hierarchy == null ? null : hierarchy.getSelectionModel();
	}

	/**
	 * Ensure that the selection in the TreeView matches the hierarchy selection model.
	 */
	private void synchronizeTreeToSelectionModel() {
		var model = getHierarchySelectionModel();
		if (model == null)
			synchronizeTreeToSelectionModel(null, Collections.emptySet());
		else
			synchronizeTreeToSelectionModel(model.getSelectedObject(), model.getSelectedObjects());
	}
	
	/**
	 * Ensure that the selection in the TreeView matches the selection being passed here.
	 * @param primarySelected
	 * @param allSelected
	 */
	private void synchronizeTreeToSelectionModel(PathObject primarySelected, Collection<PathObject> allSelected) {
		if (synchronizingModelToTree)
			return;
		
		if (synchronizePrimarySelectionOnly) {
			boolean currentlySynchronizing = synchronizingTreeToModel;
			try {
				synchronizingTreeToModel = true;
				MultipleSelectionModel<TreeItem<PathObject>> treeModel = treeView.getSelectionModel();
				if (primarySelected == null)
					treeModel.clearSelection();
				else
					selectSingleObject(primarySelected);
				return;
			} finally {
				synchronizingTreeToModel = currentlySynchronizing;
			}
		}
		
		
		boolean ownsChanges = !synchronizingTreeToModel;
		try {
			synchronizingTreeToModel = true;
						
			MultipleSelectionModel<TreeItem<PathObject>> treeModel = treeView.getSelectionModel();
			if (primarySelected == null && allSelected.isEmpty()) {
				treeModel.clearSelection();
				return;
			}
			
			if (allSelected.size() == 1) {
				selectSingleObject(primarySelected);
				return;
			}
			
			// Need a Set for reasonable performance
			if (!(allSelected instanceof Set))
				allSelected = new HashSet<>(allSelected);
			
			// Loop through all possible selections, and select them if they should be selected (and not if they shouldn't)
			int n = treeView.getExpandedItemCount();
			int mainObjectInd = -1;
			
			for (int i = 0; i < n; i++) {
				TreeItem<PathObject> item = treeView.getTreeItem(i);
				if (item == null) {
					treeModel.clearSelection(i);
					continue;
				}
				PathObject temp = item.getValue();
				if (temp == primarySelected)
					mainObjectInd = i;
				if (allSelected.contains(temp)) {
					// Only select if necessary, or if this is the main selected object
					if (!treeModel.isSelected(i))
						treeModel.select(i);
				}
				else
					treeModel.clearSelection(i);
			}
			// Ensure that the main object is focused & its node expanded
			if (mainObjectInd >= 0) {
				treeModel.select(mainObjectInd);
				treeView.scrollTo(mainObjectInd);
			}
			
				
		} finally {
			if (ownsChanges)
				synchronizingTreeToModel = false;
		}
	}
	
	
	/**
	 * Select just one object within the tree - everything else will be cleared.
	 * 
	 * @param pathObjectSelected
	 */
	private void selectSingleObject(final PathObject pathObjectSelected) {
		if (pathObjectSelected == null)
			return;
		// Search for a path to select the object... opening the tree accordingly
		List<PathObject> ancestors = PathObjectTools.getAncestorList(pathObjectSelected);
		if (ancestors.isEmpty() || treeView.getRoot() == null)
			return;
		List<TreeItem<PathObject>> treeItems = new ArrayList<>();
		treeItems.add(treeView.getRoot());
		TreeItem<PathObject> deepestItem = null;
		while (!ancestors.isEmpty()) {
			PathObject pathObject = ancestors.remove(0);
			TreeItem<PathObject> found = null;
			for (TreeItem<PathObject> treeItem : treeItems) {
				if (treeItem.getValue() == pathObject) {
					found = treeItem;
					break;
				}
			}
			// We can't get any deeper
			if (found == null)
				return;
			deepestItem = found;
			treeItems = found.getChildren();
		}
		if (deepestItem != null && deepestItem.getValue() == pathObjectSelected) {
			TreeItem<PathObject> parent = deepestItem.getParent();
			while (parent != null) {
				parent.setExpanded(true);
				parent = parent.getParent();
			}
//			deepestItem.setExpanded(true);
			int row = treeView.getRow(deepestItem);
			treeView.getSelectionModel().clearAndSelect(row);
			treeView.scrollTo(row);
		}
	}
	
	
	/**
	 * Get the pane for display.
	 * @return
	 */
	public Pane getPane() {
		return treeViewPane;
	}
	

	
	
	/**
	 * Property that may be used to prevent updates on every hierarchy or selection change event.
	 * This can be used to improve performance by preventing the table being updated even when 
	 * it is not visible to the user.
	 * @return
	 */
	public BooleanProperty disableUpdatesProperty() {
		return disableUpdates;
	}
	
	
	void setImageData(ImageData<BufferedImage> imageData) {
		if (hierarchy != null) {
			hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
			hierarchy.removeListener(this);
		}
		
		this.hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy != null) {
			hierarchy.addListener(this);
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
			treeView.setRoot(createNode(hierarchy.getRootObject()));
		} else
			treeView.setRoot(createNode(new PathRootObject()));
//			model.setPathObject(hierarchy.getSelectionModel().getSelectedPathObject());
//		} else
//			model.setPathObject(null);
	}
	
	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
		if (Platform.isFxApplicationThread()) {
			if (disableUpdates.get())
				return;
			synchronizeTreeToSelectionModel(pathObjectSelected, allSelected);
		}
		// Do not synchronize to changes in other threads, as these may interfere with scripts
//		else
//			Platform.runLater(() -> synchronizeTreeToSelectionModel(pathObjectSelected, allSelected));
	}
	
	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}
	
	
	static TreeItem<PathObject> createNode(final PathObject pathObject) {
		return new PathObjectTreeItem(pathObject);
	}
	
	static class PathObjectTreeItem extends TreeItem<PathObject> {

		private boolean childrenSet = false;
		private Boolean isLeaf = null;

		public PathObjectTreeItem(PathObject value) {
			super(value);
		}

		@Override
		public ObservableList<TreeItem<PathObject>> getChildren() {
			ObservableList<TreeItem<PathObject>> children = super.getChildren();
			var value = getValue();
			if (!childrenSet && children.isEmpty()) {
				childrenSet = true;
				var childArray = value.getChildObjectsAsArray();
				if (childArray.length > 0) {
					// We want annotations first, then TMA cores, then everything else
					// We should sort the annotations, but not the rest (because TMA cores are already ordered, and detections may be numerous)
					List<PathObject> sortable = new ArrayList<>();
					List<PathObject> tmaCores = new ArrayList<>();
					boolean includeDetections = PathPrefs.detectionTreeDisplayModeProperty().get() != DetectionTreeDisplayModes.NONE;
					List<PathObject> others = new ArrayList<>();
					for (var child : childArray) {
						assert child != value;
						if (child.isTMACore())
							tmaCores.add(child);
						else if (child.isAnnotation() || child.hasChildObjects())
							sortable.add(child);
						else if (includeDetections)
							others.add(child);
					}
					Collections.sort(sortable, DefaultPathObjectComparator.getInstance());
					
					// Create nodes in a predictable order
					List<TreeItem<PathObject>> newChildren = new ArrayList<>();
					for (var child : sortable)
						newChildren.add(createNode(child));
					for (var child : tmaCores)
						newChildren.add(createNode(child));
					for (var child : others)
						newChildren.add(createNode(child));
						
					children.setAll(newChildren);
				} else if (!children.isEmpty())
					children.clear();
			}
			return children;
		}

		@Override
		public boolean isLeaf() {
			if (isLeaf == null) {
				var pathObject = getValue();
				if (!pathObject.hasChildObjects())
					isLeaf = true;
				else if (PathPrefs.detectionTreeDisplayModeProperty().get() != DetectionTreeDisplayModes.NONE) {
					isLeaf = false;
				} else {
					isLeaf = Arrays.stream(pathObject.getChildObjectsAsArray()).allMatch(p -> p.isDetection());
				}
//				isLeaf = !getValue().hasChildren() || getChildren().isEmpty();
			}
			return isLeaf;
		}

	};



	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (hierarchy == null)
			return;
		if (event != null && event.isChanging())
			return;
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}
		if (disableUpdates.get())
			return;
		synchronizingTreeToModel = true;
		treeView.setRoot(createNode(hierarchy.getRootObject()));
		synchronizeTreeToSelectionModel();
		synchronizingTreeToModel = false;
	}
	
	
}