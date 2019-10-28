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

package qupath.lib.objects.hierarchy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * A basic hierarchy in which to store PathObjects.
 * <p>
 * This essentially contains a single PathRootObject from which all other objects can be reached using the child object lists.
 * However, by adding/removing objects via this hierarchy (rather than through the child lists directly), it is possible
 * to maintain a more consistent structure (e.g. by automatically inserting objects as children of the objects whose ROI completely
 * contains the object to be added), along with a spatial cache so that objects can be extracted if their ROIs overlap with a specified region.
 * <p>
 * Note: Be cautious when deserializing - it may not result in a hierarchy in a valid state.
 * As a workaround, you can construct a new PathObjectHierarchy and call setHierarchy(deserializedHierarchy) to
 * ensure that you have a properly-constructed hierarchy with the same data within it.
 * 
 * @author Pete Bankhead
 *
 */
public final class PathObjectHierarchy implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(PathObjectHierarchy.class);
			
	// TODO: Make this a choice - currently a cell object is considered 'inside' if its nucleus is fully contained (as cell boundaries themselves are a little more questionable)
	/*
	 * TODO: Consider how to explain this...
	 * The idea is that cell nuclei are used to determine whether an object is 'inside' another object,
	 * which is important when adding annotations etc. to the object hierarchy.
	 * 
	 * @return
	 */
	static boolean useCellNucleiForInsideTest = true;
	/*
	 * TODO: Consider how to explain this...
	 * The idea is that tile centroids are used to determine whether an object is 'inside' another object,
	 * which is important when adding annotations etc. to the object hierarchy.
	 * 
	 * @return
	 */
	static boolean useTileCentroidsForInsideTest = true;

	
	
	private TMAGrid tmaGrid = null;
	private PathObject rootObject = new PathRootObject();
	
	transient private PathObjectSelectionModel selectionModel = new PathObjectSelectionModel();
	transient private List<PathObjectHierarchyListener> listeners = new ArrayList<>();

	// Cache enabling faster access of objects according to location
	transient private PathObjectTileCache tileCache = new PathObjectTileCache(this);

	/**
	 * Default constructor, creates an empty hierarchy.
	 */
	public PathObjectHierarchy() {
		super();
	}

	
	/**
	 * Check if the hierarchy is empty (i.e. no objects apart from the root object, no feature maps)
	 * 
	 * @return
	 */
	public synchronized boolean isEmpty() {
		return (tmaGrid == null || tmaGrid.nCores() == 0) && !rootObject.hasChildren();// && featureMaps.isEmpty();
	}
	
	/**
	 * Add a hierarchy change listener.
	 * @param listener
	 */
	public void addPathObjectListener(PathObjectHierarchyListener listener) {
		synchronized(listeners) {
			listeners.add(listener);
		}
	}
	
	/**
	 * Remove a hierarchy change listener.
	 * @param listener
	 */
	public void removePathObjectListener(PathObjectHierarchyListener listener) {
		synchronized(listeners) {
			listeners.remove(listener);
		}
	}
	
	/**
	 * Get the root object. All other objects in the hierarchy are descendants of the root.
	 * @return
	 */
	public PathObject getRootObject() {
		return rootObject;
	}
		
	/**
	 * Get the TMAGrid, or null if there is no TMA grid associated with this hierarchy.
	 * @return
	 */
	public synchronized TMAGrid getTMAGrid() {
		return tmaGrid;
	}
	
	/**
	 * Get the selection model, which handles the selection status of objects.
	 * @return
	 */
	public PathObjectSelectionModel getSelectionModel() {
		return selectionModel;
	}
	
//	/**
//	 * Check if the hierarchy is changing.  This can occur, for example, if a plugin is running
//	 * that modifies the hierarchy frequently, and so listeners may want to avoid responding to
//	 * events for performance reasons.
//	 * @return
//	 */
//	public boolean isChanging() {
//		return changing;
//	}
//	
//	public void setChanging(boolean changing) {
//		this.changing = changing;
//	}
	
	/**
	 * Set the tma grid for this hierarchy.
	 * @param tmaGrid
	 */
	public synchronized void setTMAGrid(TMAGrid tmaGrid) {
		clearTMAGrid();
		if (tmaGrid == null)
			return;
		this.tmaGrid = tmaGrid;
		updateTMAHierarchy();
	}
	
	/**
	 * Remove the TMA grid for this hierarchy.
	 */
	// TODO: Improve TMA grid modification whenever many detection objects already exist
	synchronized void clearTMAGrid() {
		if (tmaGrid == null)
			return;
		removeObjects(new ArrayList<>(tmaGrid.getTMACoreList()), true);
//		for (TMACoreObject core : tmaGrid.getTMACoreList())
//			removeObject(core, true);
//		this.tmaGrid = null;
		// Notify listeners of changes
		fireHierarchyChangedEvent(getRootObject());
	}
	
	/**
	 * Remove a single object from the hierarchy, firing a remove event.
	 * @param pathObject the object to remove
	 * @param keepChildren if true, retain all children and descendants of the object being removed; if false, remove these also
	 * @return
	 */
	public synchronized boolean removeObject(PathObject pathObject, boolean keepChildren) {
		return removeObject(pathObject, keepChildren, true);
	}
	
	/**
	 * Remove a single object from the hierarchy, without firing a remove event.
	 * @param pathObject the object to remove
	 * @param keepChildren if true, retain all children and descendants of the object being removed; if false, remove these also
	 * @return
	 */
	public synchronized boolean removeObjectWithoutUpdate(PathObject pathObject, boolean keepChildren) {
		return removeObject(pathObject, keepChildren, false);
	}
	
	/**
	 * Remove a single object from the hierarchy, optionally firing a remove event.
	 * @param pathObject the object to remove
	 * @param keepChildren if true, retain all children and descendants of the object being removed; if false, remove these also
	 * @param fireEvent if true, fire a hierarchy event. May be false if one wishes to complete several changes before updating listeners later.
	 * @return
	 */
	private synchronized boolean removeObject(PathObject pathObject, boolean keepChildren, boolean fireEvent) {
		// Check the object is within the hierarchy & has a valid parent (from which it can be removed)
		PathObject pathObjectParent = pathObject.getParent();
		if (!inHierarchy(pathObject) || pathObjectParent == null) {
			logger.warn(pathObject + " could not be removed from the hierarchy");
			return false;
		}

		// Can't keep children if there aren't any
		boolean hasChildren = pathObject.hasChildren();
		
		pathObjectParent.removePathObject(pathObject);

		// Assign the children to the parent object, if necessary
		if (keepChildren && hasChildren) {
			// We create a new array list because getPathObjectList returns an unmodifiable collection
//			List<PathObject> list = new ArrayList<>(pathObject.getPathObjectList());
			pathObjectParent.addPathObjects(pathObject.getChildObjects());
//			pathObject.clearPathObjects(); // Clear child objects, just in case
		}
		if (fireEvent) {
			if (keepChildren || !hasChildren)
				fireObjectRemovedEvent(this, pathObject, pathObjectParent);
			else
				fireHierarchyChangedEvent(this, pathObjectParent);
		}
		return true;
	}
	
	/**
	 * Remove a collection of objects, firing a single 'hierarchy changed' event afterwards to notify listeners if anything happened
	 * (i.e. if any of the objects really were found within the hierarchy) &amp; removed.
	 * 
	 * @param pathObjects the objects to remove
	 * @param keepChildren if true, retain children and descendants of the objects being removed
	 */
	public synchronized void removeObjects(Collection<? extends PathObject> pathObjects, boolean keepChildren) {
		
		if (pathObjects.isEmpty())
			return;
		
		List<PathObject> pathObjectSet = new ArrayList<>(pathObjects);
		pathObjectSet.sort((o1, o2) -> Integer.compare(o2.getLevel(), o1.getLevel()));
		
		// Determine the parents for each object
		Map<PathObject, List<PathObject>> map = new HashMap<>();
		for (PathObject pathObject : pathObjectSet) {
			PathObject parent = pathObject.getParent();
			if (parent == null)
				continue;
			List<PathObject> list = map.get(parent);
			if (list == null) {
				list = new ArrayList<>();
				map.put(parent, list);
			}
			list.add(pathObject);
		}
		
		if (map.isEmpty())
			return;
		
		// Loop through and remove objects, keeping children if necessary
		Set<PathObject> childrenToKeep = new LinkedHashSet<>();
		for (Entry<PathObject, List<PathObject>> entry : map.entrySet()) {
			PathObject parent = entry.getKey();
			List<PathObject> children = entry.getValue();
			parent.removePathObjects(children);
			if (keepChildren) {
				for (PathObject child : children)
					childrenToKeep.addAll(child.getChildObjects());
			}
		}
		childrenToKeep.removeAll(pathObjects);
		// Add children back if required (note: this can be quite slow!)
		tileCache.resetCache();
		for (PathObject pathObject : childrenToKeep) {
			addPathObject(pathObject, false);
		}
		fireHierarchyChangedEvent(this);
		
		// This previously could result in child objects being deleted even if keepChildren was 
		// true, depending upon the order in which objects were removed.
//		// Loop through and remove objects
//		for (Entry<PathObject, List<PathObject>> entry : map.entrySet()) {
//			PathObject parent = entry.getKey();
//			List<PathObject> children = entry.getValue();
//			parent.removePathObjects(children);
//			if (keepChildren) {
//				for (PathObject child : children) {
//					if (child.hasChildren()) {
//						List<PathObject> newChildList = new ArrayList<>(child.getChildObjects());
//						newChildList.removeAll(pathObjects);
//						parent.addPathObjects(newChildList);
//					}
//				}
//			}
//		}
//		fireHierarchyChangedEvent(this);
	}
	
	
	/**
	 * Determine if a PathObject is within this hierarchy.
	 * The actual test is to check the highest parent object of the PathObject is equal to the root object for this hierarchy.
	 * 
	 * @param pathObject
	 * @return
	 */
	private synchronized boolean inHierarchy(PathObject pathObject) { // made private as only called here
		if (pathObject == null)
			return false;
		while (pathObject.getParent() != null)
			pathObject = pathObject.getParent();
		return pathObject.equals(getRootObject());
	}
	
	
	private synchronized void updateTMAHierarchy() {
		if (tmaGrid == null)
			return;

		// Assign annotations to cores
		List<PathObject> pathObjectChildren = new ArrayList<>(rootObject.getChildObjects());
		rootObject.clearPathObjects();
		// Add cores to the start of the object list
		rootObject.addPathObjects(tmaGrid.getTMACoreList());
		// Add back any other objects
		for (PathObject pathObject : pathObjectChildren)
			addPathObject(pathObject, false);

		// Notify listeners of changes
		fireHierarchyChangedEvent(getRootObject());
	}
	
	
//J	synchronized boolean updateParent(final PathObject pathObject) {
		// Could check if parent needs to be changed... however consider that update parent doesn't check if it should be made a child of another object inside its current parent
//		// Check if the parent is still ok
//		if (containsObject(pathObject.getParent(), pathObject)) {
//			return false;
//		}
		
		// If we have a TMA core, the parent can't change
//J		if (pathObject.isTMACore())
//J			return false;
		
		// Otherwise, if parent isn't ok, remove and then re-add this object
//J		if (pathObject.getParent() != null) {
//J			pathObject.getParent().removePathObject(pathObject);
//J		}
//J		addPathObject(pathObject, false, false);
//J		fireHierarchyChangedEvent(this);
//		addPathObjectToList(getRootObject(), pathObject, false);
//J		return true;
//J	}
	
	// TODO: Be very cautious about this!!!!  Use of tileCache inside a synchronized method might lead to deadlocks?
	private synchronized boolean addPathObjectToList(PathObject pathObjectParent, PathObject pathObject, boolean fireChangeEvents) {
		
		if (pathObject != null && !pathObject.isDetection())
			logger.trace("Adding {} to hierarchy", pathObject);
		
		// Get all the annotations that might be a parent of this object
		var region = ImageRegion.createInstance(pathObject.getROI());
		Collection<PathObject> tempSet = new HashSet<>();
		tempSet.add(getRootObject());
		tileCache.getObjectsForRegion(PathAnnotationObject.class, region, tempSet, true);
		if (tmaGrid != null)
			tileCache.getObjectsForRegion(TMACoreObject.class, region, tempSet, true);
		
		if (pathObjectParent != null) {
			tempSet.removeIf(p -> p != pathObjectParent && !PathObjectTools.isAncestor(p, pathObjectParent));
		}

		var possibleObjects = new ArrayList<PathObject>(tempSet);
		Collections.sort(possibleObjects, (p1, p2) -> -Integer.compare(p1.getLevel(), p2.getLevel()));

		for (PathObject possibleParent : possibleObjects) {
			if (possibleParent == pathObject || possibleParent.isDetection())
				continue;
			boolean addObject = possibleParent.isRootObject();
			if (!addObject) {
				if (pathObject.isDetection())
					addObject = tileCache.containsCentroid(possibleParent, pathObject);
				else
					addObject = tileCache.covers(possibleParent, pathObject) ||
									pathObjectParent != null && possibleParent == pathObjectParent;
			}
			if (addObject) {
				if (pathObject.getParent() == possibleParent)
					return false;
				
				Collection<PathObject> previousChildren = pathObject.isDetection() ? Collections.emptyList() : new ArrayList<>(possibleParent.getChildObjects());
				possibleParent.addPathObject(pathObject);
				// If we have a non-detection, consider reassigning child objects
				if (!previousChildren.isEmpty()) {
//					long startTime = System.currentTimeMillis();
					pathObject.addPathObjects(filterObjectsForROI(pathObject.getROI(), previousChildren));
					
//					var toAdd = previousChildren.parallelStream().filter(child -> {
//						if (child.isDetection())
//							return tileCache.containsCentroid(pathObject, child);
//						else
//							return tileCache.covers(pathObject, child);
//					}).collect(Collectors.toList());
//					pathObject.addPathObjects(toAdd);
					
//					for (var child : previousChildren) {
//						boolean reassignChild = false;
//						if (child.isDetection())
//							reassignChild = tileCache.containsCentroid(pathObject, child);
//						else if (!pathObject.isDetection())
//							reassignChild = tileCache.covers(pathObject, child);
//						if (reassignChild) {
//							pathObject.addPathObject(child);
//						}
//					}
//					long endTime = System.currentTimeMillis();
//					System.err.println("Add time: " + (endTime - startTime));
				}
				
				// Notify listeners of changes, if required
				if (fireChangeEvents)
					fireObjectAddedEvent(this, pathObject);
				return true;
			}
		}
		return true;
	}
	
	
	/**
	 * Add an object to the hierarchy, firing an event.
	 * @param pathObject
	 * @return
	 */
	public boolean addPathObject(PathObject pathObject) {
		return addPathObject(pathObject, true);
	}
	
	/**
	 * Add an object to the hierarchy, without firing an event.
	 * @param pathObject
	 * @return
	 */
	public boolean addPathObjectWithoutUpdate(PathObject pathObject) {
		return addPathObject(pathObject, false);
	}
	
	/**
	 * Add path object as descendant of the requested parent.
	 * 
	 * @param pathObjectParent
	 * @param pathObject
	 * @param fireUpdate
	 * @return
	 */
	public synchronized boolean addPathObjectBelowParent(PathObject pathObjectParent, PathObject pathObject, boolean fireUpdate) {
		if (pathObjectParent == null)
			return addPathObject(pathObject, fireUpdate);
		else
			return addPathObjectToList(pathObjectParent, pathObject, fireUpdate);
	}
	
	
	/**
	 * Add an object to the hierarchy.
	 * @param pathObject the object to add
	 * @param fireUpdate if true, fire an update event after the object is added
	 * @return
	 */
	private synchronized boolean addPathObject(PathObject pathObject, boolean fireUpdate) {
		if (pathObject == getRootObject() || !pathObject.hasROI())
			return false;
		return addPathObjectToList(getRootObject(), pathObject, fireUpdate);
	}
	
	/**
	 * Add multiple objects to the hierarchy.
	 * @param pathObjects
	 * @return
	 */
	public synchronized boolean addPathObjects(Collection<? extends PathObject> pathObjects) {
		boolean changes = false;
		int n = pathObjects.size();
		int counter = 0;
		for (PathObject pathObject : pathObjects) {
			if (n > 10000) {
				if (counter % 1000 == 0)
					logger.debug("Adding {} of {}", counter, n);
			} else if (n > 1000 && counter % 100 == 0)
				logger.debug("Adding {} of {}", counter, n);
			changes = addPathObjectToList(getRootObject(), pathObject, false) || changes;
			counter++;
		}
		if (changes)
			fireHierarchyChangedEvent(getRootObject());
//			fireChangeEvent(getRootObject());
		return changes;
	}
	
	/**
	 * Remove all objects from the hierarchy.
	 */
	public synchronized void clearAll() {
		getRootObject().clearPathObjects();
		tmaGrid = null;
		fireHierarchyChangedEvent(getRootObject());
	}
	

	/**
	 * Get objects that contain Point ROIs.
	 * @param cls
	 * @return
	 */
	public synchronized Collection<PathObject> getPointObjects(Class<? extends PathObject> cls) {
		Collection<PathObject> pathObjects = getObjects(null, cls);
		if (!pathObjects.isEmpty()) {
			Iterator<PathObject> iter = pathObjects.iterator();
			while (iter.hasNext()) {
				if (!PathObjectTools.hasPointROI(iter.next())) {
					iter.remove();
				}
			}
		}
		return pathObjects;
	}
	
	/**
	 * Get all cell objects in the hierarchy.
	 * @return
	 */
	public Collection<PathObject> getCellObjects() {
		return getObjects(null, PathCellObject.class);
	}
	
	/**
	 * Get all detection objects in the hierarchy (including sub-classes of detections).
	 * @return
	 */
	public Collection<PathObject> getDetectionObjects() {
		return getObjects(null, PathDetectionObject.class);
	}
	
	/**
	 * Get all annotation objects in the hierarchy.
	 * @return
	 */
	public Collection<PathObject> getAnnotationObjects() {
		return getObjects(null, PathAnnotationObject.class);
	}

	/**
	 * Get all objects in the hierarchy, optionally filtering to return only objects that are instances of a specific class.
	 * Note that this method returns the root object, unless it has been filtered out.
	 * @param pathObjects
	 * @param cls
	 * @return
	 */
	public Collection<PathObject> getObjects(Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		if (pathObjects == null)
			pathObjects = new ArrayList<>();
		
		// If we want annotations, it can be much faster to get them from the tile cache than to sift through a potentially large number of detections
		if (PathAnnotationObject.class == cls && tileCache != null && tileCache.isActive()) {
			pathObjects.addAll(tileCache.getObjectsForRegion(cls, null, null, true));
			return pathObjects;
		}
		if (cls == null || cls.isAssignableFrom(PathRootObject.class))
			pathObjects.add(getRootObject());
		
		return PathObjectTools.getDescendantObjects(getRootObject(), pathObjects, cls);
	}
	
	/**
	 * Update an object that is already in the hierarchy (e.g. because its ROI has changed).
	 * 
	 * @param pathObject
	 */
	public void updateObject(PathObject pathObject) {
		if (inHierarchy(pathObject))
			removeObject(pathObject, true, false);
		addPathObject(pathObject, false);
		fireObjectsChangedEvent(this, Collections.singletonList(pathObject), false);
//		fireHierarchyChangedEvent(this, pathObject);
	}
	
	
	/**
	 * Get a flattened list containing all PathObjects in the hierarchy (apart from the root object).
	 * 
	 * @param list optional list into which the objects should be added (may be null)
	 * @return
	 */
	public synchronized List<PathObject> getFlattenedObjectList(List<PathObject> list) {
		if (list == null)
			list = new ArrayList<>(nObjects());
		getObjects(list, PathObject.class);
		return list;
	}
	
	/**
	 * Number of objects in the hierarchy, excluding the root.
	 * @return
	 */
	public synchronized int nObjects() {
		int count = PathObjectTools.countDescendants(getRootObject());
		return count;
	}
	
	/**
	 * Set the contents of this hierarchy to be the same as that of another hierarchy.
	 * In practice, this means copying the root and TMA grid of the second hierarchy.
	 * @param hierarchy
	 */
	public synchronized void setHierarchy(PathObjectHierarchy hierarchy) {
		if (this == hierarchy)
			return;
		rootObject = hierarchy.getRootObject();
		tmaGrid = hierarchy.tmaGrid;
		fireHierarchyChangedEvent(rootObject);
	}
	
	/**
	 * Get the objects within a specified ROI, as defined by the general rules for resolving the hierarchy. 
	 * This relies on centroids for detections, and a 'covers' rule for others.
	 * 
	 * @param cls class of PathObjects (e.g. PathDetectionObject), or null to accept all
	 * @param roi
	 * @return
	 */
	public Collection<PathObject> getObjectsForROI(Class<? extends PathObject> cls, ROI roi) {
		if (roi.isEmpty() || !roi.isArea())
			return Collections.emptyList();
		
		Collection<PathObject> pathObjects = tileCache.getObjectsForRegion(cls, ImageRegion.createInstance(roi), new HashSet<>(), true);
		return filterObjectsForROI(roi, pathObjects);
	}
	
	/**
	 * Filter the objects in a specified collection, returning only those contained 'inside' a ROI 
	 * as defined by the general rules for resolving the hierarchy 
	 * (i.e. centroids for detections, 'covers' rule for others).
	 * 
	 * @param roi
	 * @return
	 */
	Collection<PathObject> filterObjectsForROI(ROI roi, Collection<PathObject> pathObjects) {
		if (pathObjects.isEmpty() || !roi.isArea() || roi.isEmpty())
			return Collections.emptyList();
		
		var locator = tileCache.getLocator(roi, false);
		var preparedGeometry = tileCache.getPreparedGeometry(tileCache.getGeometry(roi));
		return pathObjects.parallelStream().filter(child -> {
			// Test plane first
			if (!samePlane(roi, child.getROI(), false))
				return false;
			
			if (child.isDetection())
				return tileCache.containsCentroid(locator, child);
			else {
				return tileCache.covers(preparedGeometry, child);
			}
		}).collect(Collectors.toList());
	}
	
	
	/**
	 * Check if two ROIs fall in the same plane, optionally testing the channel as well.
	 * @param roi1
	 * @param roi2
	 * @param checkChannel
	 * @return
	 */
	static boolean samePlane(ROI roi1, ROI roi2, boolean checkChannel) {
		if (checkChannel)
			return roi1.getImagePlane().equals(roi2.getImagePlane());
		else
			return roi1.getZ() == roi2.getZ() && roi1.getT() == roi2.getT();
	}
	
	
	/**
	 * Get the objects within a specified region.
	 * @param cls
	 * @param region
	 * @param pathObjects
	 * @return
	 */
	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(cls, region, pathObjects, true);
	}
	
	/**
	 * Returns true if the hierarchy contains objects overlapping a specific region, optionally filtering to identify only 
	 * objects of a specific class.
	 * @param cls
	 * @param region
	 * @return
	 */
	public boolean hasObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region) {
		return tileCache.hasObjectsForRegion(cls, region, true);
	}
	
	
	void fireObjectRemovedEvent(Object source, PathObject pathObject, PathObject previousParent) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectRemovedEvent(source, this, previousParent, pathObject);
		fireEvent(event);
	}

	void fireObjectAddedEvent(Object source, PathObject pathObject) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectAddedEvent(source, this, pathObject.getParent(), pathObject);
		fireEvent(event);
	}
	
	/**
	 * Fire a hierarchy update indicating object measurements have changed.
	 * @param source
	 * @param pathObjects
	 */
	public void fireObjectMeasurementsChangedEvent(Object source, Collection<PathObject> pathObjects) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_MEASUREMENTS, pathObjects, false);
		fireEvent(event);
	}
	
	/**
	 * Fire a hierarchy update indicating object classifications have changed.
	 * @param source
	 * @param pathObjects
	 */
	public void fireObjectClassificationsChangedEvent(Object source, Collection<PathObject> pathObjects) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_CLASSIFICATION, pathObjects, false);
		fireEvent(event);
	}
	
	/**
	 * Fire a hierarchy update indicating objects have changed.
	 * @param source
	 * @param pathObjects
	 */
	public void fireObjectsChangedEvent(Object source, Collection<? extends PathObject> pathObjects) {
		fireObjectsChangedEvent(source, pathObjects, false);
	}

	/**
	 * Fire a hierarchy update indicating objects have changed, and may still be changing.
	 * @param source
	 * @param pathObjects
	 * @param isChanging is true, listeners may choose not to respond until an event is fired with isChanging false
	 */
	public void fireObjectsChangedEvent(Object source, Collection<? extends PathObject> pathObjects, boolean isChanging) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_OTHER, pathObjects, isChanging);
		fireEvent(event);
	}
	
	/**
	 * Fire a hierarchy update indicating the hierarchy structure has changed, impacting descendants of a specified object.
	 * @param source
	 * @param pathObject
	 */
	public void fireHierarchyChangedEvent(Object source, PathObject pathObject) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createStructureChangeEvent(source, this, pathObject);
		fireEvent(event);
	}

	/**
	 * Fire a hierarchy update indicating the hierarchy structure has changed.
	 * This is often a good choice of event if multiple changes may have occurred, of if one is unsure what exactly 
	 * has changed.
	 * @param source
	 */
	public void fireHierarchyChangedEvent(Object source) {
		fireHierarchyChangedEvent(source, getRootObject());
	}
	
	
	synchronized void fireEvent(PathObjectHierarchyEvent event) {
		synchronized(listeners) {
			for (PathObjectHierarchyListener listener : listeners)
				listener.hierarchyChanged(event);
		}
	}
	
	
	@Override
	public String toString() {
		return "Hierarchy: " + nObjects() + " objects";
	}
	
}
