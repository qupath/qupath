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

package qupath.lib.objects.hierarchy.events;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Vector;

import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

/**
 * Model for handling selection of single and multiple PathObjects.
 * 
 * @author Pete Bankhead
 * 
 * @see qupath.lib.objects.hierarchy.PathObjectHierarchy
 * 
 */
public class PathObjectSelectionModel {
	
	private Vector<PathObjectSelectionListener> listeners = new Vector<>();
	
	private Set<PathObject> selectedSet = Collections.synchronizedSet(new LinkedHashSet<>(256));
	private Set<PathObject> selectedSetUnmodifiable = Collections.unmodifiableSet(selectedSet);
	private PathObject pathObjectSelected = null;
	
	public void setSelectedObjects(Collection<? extends PathObject> pathObjects, final PathObject selectedObject) {
		if (pathObjects == null || pathObjects.isEmpty()) {
			clearSelection();
			return;
		}
		// Check if we have any changes to make
		if (selectedSet.size() == pathObjects.size() && selectedSet.containsAll(pathObjects))
			return;
		
		// Update selected objects
		PathObject previousSelected = pathObjectSelected;
		selectedSet.clear();
		selectedSet.addAll(pathObjects);
		selectedSet.remove(null); // This shouldn't be needed... and yet it is?
//		if (selectedObject == null) {
//			updateToLastSelectedObject();
//		} else
			pathObjectSelected = selectedObject;
		firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
	}
	
	public Set<PathObject> getSelectedObjects() {
		return selectedSetUnmodifiable;
	}
	

	/**
	 * Returns true if no objects are selected.
	 * 
	 * @return
	 */
	public boolean noSelection() {
		return pathObjectSelected == null && selectedSet.isEmpty();
	}

	/**
	 * Returns true if only one object has been selected, accessible by getSelectedObject();
	 * 
	 * @return
	 */
	public boolean singleSelection() {
		return selectedSet.size() == 1 || (selectedSet.isEmpty() && pathObjectSelected != null);
	}
	
	
	public void setSelectedObject(PathObject pathObject, boolean addToSelection) {
		if (!addToSelection) {
			setSelectedObject(pathObject);
			return;
		}
		if (pathObject == null)
			return;
		PathObject previousSelected = pathObjectSelected;
		selectedSet.add(pathObject);
		pathObjectSelected = pathObject;
		firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
	}
	
	
	private void updateToLastSelectedObject() {
		Iterator<? extends PathObject> iter = selectedSet.iterator();
		while (iter.hasNext())
			pathObjectSelected = iter.next();
	}
	

	public void deselectObject(PathObject pathObject) {
		PathObject previousSelected = pathObjectSelected;
		boolean changes = selectedSet.remove(pathObject);
		if (pathObjectSelected == previousSelected) {
			pathObjectSelected = null;
			updateToLastSelectedObject();
			changes = true;
		}
		if (changes)
			firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
	}
	
	
	/**
	 * Ensure the specified objects are deselected.
	 * 
	 * The selection state of other objects will not be modified.
	 * 
	 * @param pathObjects
	 */
	public void deselectObjects(Collection<? extends PathObject> pathObjects) {
		if (selectedSet.removeAll(pathObjects)) {
			PathObject previousSelected = pathObjectSelected;
			if (pathObjects.contains(pathObjectSelected))
				updateToLastSelectedObject();
			firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
		}
	}
	
	
	/**
	 * Ensure the specified objects are selected.
	 * 
	 * The selection state of other objects will not be modified.
	 * 
	 * @param pathObjects
	 */
	public void selectObjects(Collection<? extends PathObject> pathObjects) {
		if (selectedSet.addAll(pathObjects)) {
			PathObject previousSelected = pathObjectSelected;
			if (previousSelected == null)
				updateToLastSelectedObject();
			firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
		}
	}
	
	
	
	public void setSelectedObject(PathObject pathObject) {
		// Here we fire even when the object is the same... this is because sometimes the object is selected but not
		// in the hierarchy - and some listeners respond differently depending upon which is the case
//		if (this.pathObjectSelected == pathObject)
//			return;
		PathObject previousObject = pathObjectSelected;
		pathObjectSelected = pathObject;
		
		selectedSet.clear();
		if (pathObjectSelected != null)
			selectedSet.add(pathObjectSelected);
		firePathObjectSelectionChangedEvent(pathObjectSelected, previousObject);
	}
	
	public PathObject getSelectedObject() {
		return pathObjectSelected;
	}
	
	public boolean isSelected(PathObject pathObject) {
		return pathObjectSelected == pathObject || selectedSet.contains(pathObject);
	}
	
	/**
	 * Get the ROI of the selected object, if available, or null
	 * @return
	 */
	public ROI getSelectedROI() {
		if (pathObjectSelected != null)
			return pathObjectSelected.getROI();
		return null;
	}
	
	public void clearSelection() {
		if (noSelection())
			return;
		selectedSet.clear();
		PathObject previous = pathObjectSelected;
		pathObjectSelected = null;
		firePathObjectSelectionChangedEvent(null, previous);
	}

	protected void firePathObjectSelectionChangedEvent(PathObject pathObjectSelected, PathObject previousObject) {
////		System.err.println("Setting to " + selectedSet);
//		if (pathObjectSelected == null)
//			System.err.println("Setting to null...");
		for (PathObjectSelectionListener listener : listeners) {
			listener.selectedPathObjectChanged(pathObjectSelected, previousObject);
		}
	}
	
	public void addPathObjectSelectionListener(PathObjectSelectionListener listener) {
		listeners.add(listener);
	}
	
	public void removePathObjectSelectionListener(PathObjectSelectionListener listener) {
		listeners.remove(listener);
	}
	
}
