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

package qupath.lib.gui.viewer.tools;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Abstract implementation of a PathTool.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractPathTool implements PathTool, QuPathViewerListener {

	QuPathViewer viewer;
	ModeWrapper modes;
	
	transient LevelComparator comparator;
	
	
	AbstractPathTool(ModeWrapper modes) {
		this.modes = modes;
	}
	
	void ensureCursorType(Cursor cursor) {
//		System.err.println(cursor);
		// We don't want to change a waiting cursor unnecessarily
		Cursor currentCursor = viewer.getCursor();
		if (currentCursor == null || currentCursor == Cursor.WAIT)
			return;
		viewer.setCursor(cursor);
	}
	
	
	protected QuPathViewer getViewer() {
		return viewer;
	}

	
//	/**
//	 * Try to select an object with a ROI overlapping a specified coordinate.
//	 * 
//	 * @param x
//	 * @param y
//	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
//	 * @return true if any object was selected
//	 */
//	boolean tryToSelect(double x, double y, int searchCount) {
//		PathObjectHierarchy hierarchy = viewer.getPathObjectHierarchy();
//		if (hierarchy == null)
//			return false;
//		Set<PathObject> pathObjects = new HashSet<>(8);
//		hierarchy.getObjectsForRegion(PathObject.class, ImageRegion.createInstance((int)x, (int)y, 1, 1, viewer.getZPosition(), viewer.getTPosition()), pathObjects);
//		PathObjectHelpers.removePoints(pathObjects); // Ensure we don't have any PointROIs
//		
//		// Ensure the ROI contains the click
//		Iterator<PathObject> iter = pathObjects.iterator();
//		while (iter.hasNext()) {
//			if (!iter.next().getROI().contains(x, y))
//				iter.remove();
//		}
//		
//		if (pathObjects.isEmpty()) {
//			hierarchy.getSelectionModel().setSelectedPathObject(null);
//			return false;
//		}
//		if (pathObjects.size() == 1) {
//			hierarchy.getSelectionModel().setSelectedPathObject(pathObjects.iterator().next());
//			return true;
//		}
//		List<PathObject> pathObjectList = new ArrayList<>(pathObjects);
//		if (comparator == null)
//			comparator = new LevelComparator();
//		Collections.sort(pathObjectList, comparator);
//		int ind = pathObjects.size() - searchCount % pathObjects.size() - 1;
//		hierarchy.getSelectionModel().setSelectedPathObject(pathObjectList.get(ind));
//		return true;
//	}
	
	
	/**
	 * Try to select an object with a ROI overlapping a specified coordinate.
	 * If there is no object found, the current selected object will be reset (to null).
	 * 
	 * @param x
	 * @param y
	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
	 * @return true if any object was selected
	 */
	boolean tryToSelect(double x, double y, int searchCount, boolean addToSelection) {
		return tryToSelect(x, y, searchCount, addToSelection, false);
	}
	
	boolean tryToSelect(double x, double y, int searchCount, boolean addToSelection, boolean toggleSelection) {
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return false;
		PathObject pathObject = getSelectableObject(x, y, searchCount);
		if (toggleSelection && hierarchy.getSelectionModel().getSelectedObject() == pathObject)
			hierarchy.getSelectionModel().deselectObject(pathObject);
		else
			viewer.setSelectedObject(pathObject, addToSelection);
		// Reset selection if we have nothing
		if (pathObject == null && addToSelection)
			viewer.setSelectedObject(null);
		return pathObject != null;
	}
	
	
	/**
	 * Determine which object would be selected by a click in this location - but do not actually apply the selection.
	 * 
	 * @param x
	 * @param y
	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
	 * @return true if any object was selected
	 */
	PathObject getSelectableObject(double x, double y, int searchCount) {
		List<PathObject> pathObjectList = getSelectableObjectList(x, y);
		if (pathObjectList == null || pathObjectList.isEmpty())
			return null;
		int ind = pathObjectList.size() - searchCount % pathObjectList.size() - 1;
		return pathObjectList.get(ind);
	}
	
	
	/**
	 * Get a list of all selectable objects overlapping the specified x, y coordinates, ordered by depth in the hierarchy
	 * @param x
	 * @param y
	 * @return
	 */
	List<PathObject> getSelectableObjectList(double x, double y) {
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		Collection<PathObject> pathObjects = PathObjectTools.getObjectsForLocation(hierarchy, x, y, viewer.getZPosition(), viewer.getTPosition());
		if (pathObjects.isEmpty())
			return Collections.emptyList();
		List<PathObject> pathObjectList = new ArrayList<>(pathObjects);
		if (pathObjectList.size() == 1)
			return pathObjectList;
		if (comparator == null)
			comparator = new LevelComparator();
		Collections.sort(pathObjectList, comparator);
		return pathObjectList;
	}
	
	
	
	public void mouseClicked(MouseEvent e) {}

	public void mouseDragged(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}
	
	public void mouseMoved(MouseEvent e) {}

	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
			return;
		}
		
		Object source = e.getSource();
		if (source instanceof Node) {
			Node node = (Node)source;
			if (node.isFocusTraversable() && !node.isFocused()) {
				node.requestFocus();
				e.consume();
			}
		}
//		// Ensure we can focus this component
//		Component component = e.getComponent();
//		if (!component.isFocusable()) {
//			component.setFocusable(true);
//		}
//		component.requestFocus();
	}

	public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
			return;
		}
	}

	/**
	 * Compare with the goal of making objects earlier in the hierarchy (i.e. closer to the root)
	 * closer to the start of any sorted collection.
	 */
	static class LevelComparator implements Comparator<PathObject> {

		@Override
		public int compare(PathObject o1, PathObject o2) {
			return Integer.compare(o1.getLevel(), o2.getLevel());
		}
		
	}
	
	
	@Override
	public void registerTool(QuPathViewer viewer) {
		// Disassociate from any previous viewer
		if (this.viewer != null)
			deregisterTool(this.viewer);
		// Associate with new viewer
		this.viewer = viewer;
		if (viewer != null) {
//			if (viewer.getServer() == null)
//				System.err.println("Registering null!");
//			else
//				System.err.println("Registering " + viewer.getServer().getShortServerName());
			Node canvas = viewer.getView();
			
			canvas.setOnMouseDragged(e -> mouseDragged(e));
			canvas.setOnMouseDragReleased(e -> mouseDragged(e));
			
			canvas.setOnMouseMoved(e -> mouseMoved(e));

			canvas.setOnMouseClicked(e -> mouseClicked(e));
			canvas.setOnMousePressed(e -> mousePressed(e));
			canvas.setOnMouseReleased(e -> mouseReleased(e));
			
			canvas.setOnMouseEntered(e -> mouseEntered(e));
			canvas.setOnMouseExited(e -> mouseExited(e));

			viewer.addViewerListener(this);
		}
	}

	@Override
	public void deregisterTool(QuPathViewer viewer) {
		if (this.viewer == viewer) {
			this.viewer = null;
			
			Node canvas = viewer.getView();
			canvas.setOnMouseDragged(null);
			canvas.setOnMouseDragReleased(null);
			
			canvas.setOnMouseMoved(null);

			canvas.setOnMouseClicked(null);
			canvas.setOnMousePressed(null);
			canvas.setOnMouseReleased(null);
			
			canvas.setOnMouseEntered(null);
			canvas.setOnMouseExited(null);
			
			viewer.removeViewerListener(this);
		}
	}
	
	
	
	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {}


	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}


	@Override
	public void viewerClosed(QuPathViewer viewer) {}
	
	
}
