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
import java.util.*;
import java.util.stream.Collectors;

import javafx.scene.control.*;
import org.controlsfx.control.BreadCrumbBar;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.prefs.PathPrefs;
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
public class PathObjectHierarchyView implements ImageDataChangeListener<BufferedImage>, PathObjectSelectionListener, PathObjectHierarchyListener {
	
	/**
	 * Request that we only synchronize to the primary selection; otherwise synchronizing to 
	 * multiple selections from long lists can be a performance bottleneck
	 */
	private static boolean synchronizePrimarySelectionOnly = true;

	
	/**
	 * Control how detections are displayed in this tree view.
	 * 
	 * Showing all detections can be a bad idea, since there may be serious performance issues 
	 * (especially when selecting/deselecting objects on an expanded tree).
	 */
	public static enum TreeDetectionDisplay {
		NONE, WITHOUT_ICONS, WITH_ICONS;
			@Override
			public String toString() {
				switch(this) {
				case NONE:
					return "None";
				case WITHOUT_ICONS:
					return "Without icons";
				case WITH_ICONS:
					return "With icons";
				default:
					return "Unknown";
				}
			}
	}
	
	private static ObjectProperty<TreeDetectionDisplay> detectionDisplay = PathPrefs.createPersistentPreference(
			"hierarchyTreeDetectionDisplay", TreeDetectionDisplay.WITH_ICONS, TreeDetectionDisplay.class);
	
	static {
		QuPathGUI.getInstance().getPreferencePanel().addPropertyPreference(detectionDisplay, TreeDetectionDisplay.class, "Hierarchy detection display", "General",
				"Choose how to display detections in the hierarchy tree view - choose 'None' for the best performance");
	}
	
	private ImageData<?> imageData;
	
	private TreeView<PathObject> treeView;
	private BorderPane treeViewPane = new BorderPane();
	
	public PathObjectHierarchyView(final QuPathGUI qupath) {
		
		// Handle display changes
		treeView = new TreeView<>(createNode(new PathRootObject()));
		treeView.setCellFactory(new Callback<TreeView<PathObject>, TreeCell<PathObject>>() {
			@Override public TreeCell<PathObject> call(TreeView<PathObject> treeView) {
		         return new PathObjectCell();
		     }
		});
		
		treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		treeView.getSelectionModel().getSelectedItems().addListener(
				(ListChangeListener.Change<? extends TreeItem<PathObject>> c) -> synchronizeSelectionModelToTree(c)
		);
		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeSelectionModelToTree(null));
		// When nodes are expanded, we need to ensure selections are handled
		treeView.expandedItemCountProperty().addListener((v, o, n) -> synchronizeTreeToSelectionModel());
		
		setImageData(qupath.getImageData());
		qupath.addImageDataChangeListener(this);
		
		// Add popup to control detection display
		ContextMenu popup = new ContextMenu();
		ToggleGroup toggleGroup = new ToggleGroup();
		RadioMenuItem miWithIcons = new RadioMenuItem("With icons");
		miWithIcons.setToggleGroup(toggleGroup);
		miWithIcons.selectedProperty().addListener((v, o, n) -> {
			if (n)
				detectionDisplay.set(TreeDetectionDisplay.WITH_ICONS);
		});


		RadioMenuItem miWithoutIcons = new RadioMenuItem("Without icons");
		miWithoutIcons.setToggleGroup(toggleGroup);
		miWithoutIcons.selectedProperty().addListener((v, o, n) -> {
			if (n)
				detectionDisplay.set(TreeDetectionDisplay.WITHOUT_ICONS);
		});

		RadioMenuItem miHide = new RadioMenuItem("Hide detections");
		miHide.setToggleGroup(toggleGroup);
		miHide.selectedProperty().addListener((v, o, n) -> {
			if (n)
				detectionDisplay.set(TreeDetectionDisplay.NONE);
		});
		// Ensure we have the right toggle selected
		miWithIcons.setSelected(detectionDisplay.get() == TreeDetectionDisplay.WITH_ICONS);
		miWithoutIcons.setSelected(detectionDisplay.get() == TreeDetectionDisplay.WITHOUT_ICONS);
		miHide.setSelected(detectionDisplay.get() == TreeDetectionDisplay.NONE);

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
			if (e.getSelectedCrumb() != null && imageData != null)
				imageData.getHierarchy().getSelectionModel().setSelectedObject(e.getSelectedCrumb().getValue());
		});
//		breadCrumbBar.selectedCrumbProperty().bind(treeView.getSelectionModel().selectedItemProperty());
		treeViewPane.setBottom(breadCrumbBar);
		
		// Update when display is changed
		detectionDisplay.addListener((v, o, n) -> hierarchyChanged(null));
		
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
		return imageData == null ? null : imageData.getHierarchy().getSelectionModel();
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
		List<PathObject> ancestors = PathObjectTools.getAncenstorList(pathObjectSelected);
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
	
	
	
//	public TreeView<PathObject> getTreeView() {
//		return treeView;
//	}
	
	
	public Pane getPane() {
		return treeViewPane;
	}
	
	
	static class PathObjectCell extends TreeCell<PathObject> {

		public PathObjectCell() {    }

		@Override
		protected void updateItem(PathObject item, boolean empty) {
			//	    	 updateSelected(isObjectSelected(item));
			super.updateItem(item, empty);
			setGraphic(null);
			if (item == null || empty) {
				setText(null);
				setGraphic(null);
			} else {
				setText(item.toString());
				if (item.hasROI() && (!item.isDetection() || detectionDisplay.get() == TreeDetectionDisplay.WITH_ICONS)) {
					// It consumes too many resources to create enough icons to represent every detection this way...
					// consider reintroducing in the future with a more efficient implementation, e.g. reusing images & canvases
					Color color = ColorToolsFX.getDisplayedColor(item);
					setGraphic(PathIconFactory.createPathObjectIcon(item, 16, 16));
				} else
					setGraphic(null);
			}
		}

	}
	
	
	
	
	void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData != null && this.imageData.getHierarchy() != null) {
			PathObjectHierarchy hierarchy = this.imageData.getHierarchy();
			hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
			hierarchy.removePathObjectListener(this);
		}
		
		this.imageData = imageData;
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy != null) {
			hierarchy.addPathObjectListener(this);
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
		if (Platform.isFxApplicationThread())
			synchronizeTreeToSelectionModel(pathObjectSelected, allSelected);
		// Do not synchronize to changes in other threads, as these may interfere with scripts
//		else
//			Platform.runLater(() -> synchronizeTreeToSelectionModel(pathObjectSelected, allSelected));
	}
	
	public ImageData<?> getImageData() {
		return imageData;
	}
	
	
	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
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
					Arrays.sort(childArray, DefaultPathObjectComparator.getInstance());
					List<TreeItem<PathObject>> newChildren = new ArrayList<>();
					boolean includeDetections = detectionDisplay.get() != TreeDetectionDisplay.NONE;
					for (PathObject child : childArray) {
						if (includeDetections || child.hasChildren() || !child.isDetection())
							newChildren.add(createNode(child));
					}
					children.setAll(newChildren);
				} else if (!children.isEmpty())
					children.clear();
			}
			return children;
		}

		@Override
		public boolean isLeaf() {
			if (isLeaf == null)
				isLeaf = !getValue().hasChildren() || getChildren().isEmpty();
			return isLeaf;
		}

	};



	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (imageData == null)
			return;
		if (event != null && event.isChanging())
			return;
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}
		synchronizingTreeToModel = true;
		treeView.setRoot(createNode(imageData.getHierarchy().getRootObject()));
		synchronizeTreeToSelectionModel();
		synchronizingTreeToModel = false;
	}
	
	
}