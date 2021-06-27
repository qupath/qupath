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

package qupath.lib.objects.hierarchy.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
	private final static Logger logger = LoggerFactory.getLogger(PathObjectSelectionModel.class);
	
	private List<PathObjectSelectionListener> listeners = Collections.synchronizedList(new ArrayList<>());
	
	private Set<PathObject> selectedSet = Collections.synchronizedSet(new LinkedHashSet<>(256));
	private Set<PathObject> selectedSetUnmodifiable = Collections.unmodifiableSet(selectedSet);
	private PathObject pathObjectSelected = null;
	
	/**
	 * Specify a collection of objects to be selected, and which among them should be the primary.
	 * <p>
	 * Any previous selection is reset.
	 * 
	 * @param pathObjects
	 * @param primarySelectedObject
	 * 
	 * @implNote In v0.3.0 this changes to always set a primary selected object whenever a collection is supplied. 
	 *           If no primary object is specified, the previous primary object is retained if its selected status is 
	 *           unchanged, otherwise the first object in the collection is chosen.
	 *           The reason for this is to avoid confusion whenever scripters sometimes set selected objects, knowing 
	 *           there to only be one, and then find getSelectedObject() still returns null.
	 */
	public synchronized void setSelectedObjects(Collection<? extends PathObject> pathObjects, final PathObject primarySelectedObject) {
		if (pathObjects == null || pathObjects.isEmpty()) {
			clearSelection();
			return;
		}
		// Check if we have any changes to make
		if (pathObjectSelected == primarySelectedObject && selectedSet.size() == pathObjects.size() && selectedSet.containsAll(pathObjects))
			return;
		
		// Update selected objects
		PathObject previousSelected = pathObjectSelected;
		selectedSet.clear();
		selectedSet.addAll(pathObjects);
		
		// This shouldn't be needed... and yet it is?
		if (selectedSet.remove(null))
			logger.warn("setSelectedObjects collection contained null!");
		
		if (primarySelectedObject == null) {
			// If no primary object is provided, try to keep the old one or else take the first in the set
			if (previousSelected != null && selectedSet.contains(previousSelected))
				pathObjectSelected = previousSelected;
			else {
				for (var temp : pathObjects) {
					if (temp != null) {
						pathObjectSelected = temp;
						break;
					}
				}
			}
		} else
			pathObjectSelected = primarySelectedObject;
			
//		if (selectedObject == null) {
//			updateToLastSelectedObject();
//		} else
		firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
	}
	
	/**
	 * Get an unmodifiable set containing all the currently-selected objects.
	 * @return
	 */
	public synchronized Set<PathObject> getSelectedObjects() {
		return selectedSetUnmodifiable;
	}
	

	/**
	 * Returns true if no objects are selected.
	 * 
	 * @return
	 */
	public synchronized boolean noSelection() {
		return pathObjectSelected == null && selectedSet.isEmpty();
	}

	/**
	 * Returns true if only one object has been selected, accessible by getSelectedObject();
	 * 
	 * @return
	 */
	public synchronized boolean singleSelection() {
		return selectedSet.size() == 1 || (selectedSet.isEmpty() && pathObjectSelected != null);
	}
	
	/**
	 * Select the specified object to be the primary selected object, optionally retaining the 
	 * existing selected objects.
	 * 
	 * @param pathObject
	 * @param addToSelection add to the existing selection, rather than allowing only the specified object to be selected
	 * 
	 * @see #setSelectedObject(PathObject)
	 */
	public void setSelectedObject(PathObject pathObject, boolean addToSelection) {
		if (!addToSelection) {
			setSelectedObject(pathObject);
			return;
		}
		if (pathObject == null)
			return;
		logger.trace("Adding object {} to selection", pathObject);
		PathObject previousSelected = pathObjectSelected;
		selectedSet.add(pathObject);
		pathObjectSelected = pathObject;
		firePathObjectSelectionChangedEvent(pathObjectSelected, previousSelected);
	}
	
	
	private synchronized void updateToLastSelectedObject() {
		Iterator<? extends PathObject> iter = selectedSet.iterator();
		while (iter.hasNext())
			pathObjectSelected = iter.next();
	}
	
	/**
	 * Ensure that the specified object is removed from the selection.
	 * @param pathObject
	 */
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
	 * <p>
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
	 * <p>
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
	
	
	/**
	 * Set the specified object to be selected, deselecting all others.
	 * @param pathObject
	 */
	public void setSelectedObject(PathObject pathObject) {
		logger.trace("Setting selected object to {}", pathObject);
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
	
	/**
	 * Get the current primary selected object.
	 * @return
	 */
	public PathObject getSelectedObject() {
		return pathObjectSelected;
	}
	
	/**
	 * Query whether a specific object is selected.
	 * 
	 * @param pathObject
	 * @return
	 */
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
	
	/**
	 * Clear selection so that no objects are selected.
	 */
	public synchronized void clearSelection() {
		if (noSelection())
			return;
		selectedSet.clear();
		PathObject previous = pathObjectSelected;
		pathObjectSelected = null;
		firePathObjectSelectionChangedEvent(null, previous);
	}

	synchronized void firePathObjectSelectionChangedEvent(PathObject pathObjectSelected, PathObject previousObject) {
		var allSelected = Collections.unmodifiableSet(new HashSet<>(selectedSet));
		for (PathObjectSelectionListener listener : listeners) {
			listener.selectedPathObjectChanged(pathObjectSelected, previousObject, allSelected);
		}
	}
	
	/**
	 * Add listener for selection changes.
	 * @param listener
	 */
	public synchronized void addPathObjectSelectionListener(PathObjectSelectionListener listener) {
		logger.debug("Adding selection listener {}", listener);
		listeners.add(listener);
	}
	
	/**
	 * Remove listener for selection changes.
	 * @param listener
	 */
	public synchronized void removePathObjectSelectionListener(PathObjectSelectionListener listener) {
		logger.debug("Removing selection listener {}", listener);
		listeners.remove(listener);
	}
	
}
