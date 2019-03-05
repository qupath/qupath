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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.regions.ImageRegion;

/**
 * A basic hierarchy in which to store PathObjects.
 * <p>
 * This basically contains a single PathRootObject from which all other objects can be reached using the child object lists.
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
	transient private Vector<PathObjectHierarchyListener> listeners = new Vector<>();

	// Cache enabling faster access of objects according to location
	transient private PathObjectTileCache tileCache = new PathObjectTileCache(this);

	
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
	
	public void addPathObjectListener(PathObjectHierarchyListener listener) {
		listeners.add(listener);
	}
	
	public void removePathObjectListener(PathObjectHierarchyListener listener) {
		listeners.remove(listener);
	}
	
	public PathObject getRootObject() {
		return rootObject;
	}
		
	public synchronized TMAGrid getTMAGrid() {
		return tmaGrid;
	}
	
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
	
	public synchronized void setTMAGrid(TMAGrid tmaGrid) {
		clearTMAGrid();
		if (tmaGrid == null)
			return;
		this.tmaGrid = tmaGrid;
		updateTMAHierarchy();
	}
	
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
	
	public synchronized boolean removeObject(PathObject pathObject, boolean keepChildren) {
		return removeObject(pathObject, keepChildren, true);
	}
	
	public synchronized boolean removeObject(PathObject pathObject, boolean keepChildren, boolean fireEvent) {
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
	 * @param pathObjects
	 * @param keepChildren
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
		
		// Loop through and remove objects
		for (Entry<PathObject, List<PathObject>> entry : map.entrySet()) {
			PathObject parent = entry.getKey();
			List<PathObject> children = entry.getValue();
			parent.removePathObjects(children);
			if (keepChildren) {
				for (PathObject child : children) {
					if (child.hasChildren()) {
						List<PathObject> newChildList = new ArrayList<>(child.getChildObjects());
						newChildList.removeAll(pathObjects);
						parent.addPathObjects(newChildList);
					}
				}
			}
		}
		fireHierarchyChangedEvent(this);
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
			addPathObject(pathObject, false, false);

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
	private synchronized boolean addPathObjectToList(PathObject pathObjectParent, PathObject pathObject, boolean avoidDuplicates, boolean fireChangeEvents) {
		
		if (pathObject != null && !pathObject.isDetection())
			logger.trace("Adding {} to hierarchy", pathObject);
		
		// Get all the annotations that might be a parent of this object
		var region = ImageRegion.createInstance(pathObject.getROI());
		Collection<PathObject> tempSet = new HashSet<>();
		tempSet.add(getRootObject());
		tileCache.getObjectsForRegion(PathAnnotationObject.class, region, tempSet, true);
		if (tmaGrid != null)
			tileCache.getObjectsForRegion(TMACoreObject.class, region, tempSet, true);

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
					addObject = tileCache.covers(possibleParent, pathObject);
			}
			if (addObject) {
				if (pathObject.getParent() == possibleParent)
					return false;
				
				var previousChildren = new HashSet<>(possibleParent.getChildObjects());
				possibleParent.addPathObject(pathObject);
				// If we have a non-detection, consider reassigning child objects
				if (!pathObject.isDetection()) {
					long startTime = System.currentTimeMillis();
					var locator = tileCache.getLocator(pathObject);
					var preparedGeometry = tileCache.getPreparedGeometry(tileCache.getGeometry(pathObject));
					var toAdd = previousChildren.parallelStream().filter(child -> {
						if (child.isDetection())
							return tileCache.containsCentroid(locator, child);
						else
							return tileCache.covers(preparedGeometry, child);
					}).collect(Collectors.toList());
					pathObject.addPathObjects(toAdd);
					
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
					long endTime = System.currentTimeMillis();
					System.err.println("Add time: " + (endTime - startTime));
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
	 * Only objects with ROIs can be added.
	 * TODO: Consider relaxing this requirement.
	 * 
	 * @param pathObject
	 * @param avoidDuplicates
	 * @return
	 */
	public synchronized boolean addPathObject(PathObject pathObject, boolean avoidDuplicates) {
		return addPathObject(pathObject, avoidDuplicates, true);
	}
	
	/**
	 * Add path object as descendant of the requested parent.
	 * 
	 * @param pathObjectParent
	 * @param pathObject
	 * @param avoidDuplicates
	 * @param fireUpdate
	 * @return
	 */
	public synchronized boolean addPathObjectBelowParent(PathObject pathObjectParent, PathObject pathObject, boolean avoidDuplicates, boolean fireUpdate) {
		if (pathObjectParent == null)
			return addPathObject(pathObject, avoidDuplicates, fireUpdate);
		else
			return addPathObjectToList(pathObjectParent, pathObject, avoidDuplicates, fireUpdate);
	}
	
	public synchronized boolean addPathObject(PathObject pathObject, boolean avoidDuplicates, boolean fireUpdate) {
		if (pathObject == getRootObject() || !pathObject.hasROI())
			return false;
		return addPathObjectToList(getRootObject(), pathObject, avoidDuplicates, fireUpdate);
	}
	
	public synchronized boolean addPathObjects(Collection<? extends PathObject> pathObjects, boolean avoidDuplicates) {
		boolean changes = false;
		int n = pathObjects.size();
		int counter = 0;
		for (PathObject pathObject : pathObjects) {
			if (n > 10000) {
				if (counter % 1000 == 0)
					logger.debug("Adding {} of {}", counter, n);
			} else if (n > 1000 && counter % 100 == 0)
				logger.debug("Adding {} of {}", counter, n);
			changes = addPathObjectToList(getRootObject(), pathObject, avoidDuplicates, false) || changes;
			counter++;
		}
		if (changes)
			fireHierarchyChangedEvent(getRootObject());
//			fireChangeEvent(getRootObject());
		return changes;
	}
	
	public synchronized void clearAll() {
		getRootObject().clearPathObjects();
		tmaGrid = null;
		fireHierarchyChangedEvent(getRootObject());
	}
	
	
	private synchronized void addPathObjectsRecursively(PathObject pathObject, Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		// Prefer to iterate through long lists and process as we go, rather than handle one object per method call
		addPathObjectsRecursively(Collections.singleton(pathObject), pathObjects, cls);
	}
	
	
	private static void addPathObjectsRecursively(Collection<PathObject> pathObjectsInput, Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		for (PathObject childObject : pathObjectsInput) {
			if (cls == null || cls.isInstance(childObject)) {
				pathObjects.add(childObject);
			}
			if (childObject.hasChildren())
				addPathObjectsRecursively(childObject.getChildObjects(), pathObjects, cls);
		}
	}

	public synchronized Collection<PathObject> getPointObjects(Class<? extends PathObject> cls) {
		Collection<PathObject> pathObjects = getObjects(null, cls);
		if (!pathObjects.isEmpty()) {
			Iterator<PathObject> iter = pathObjects.iterator();
			while (iter.hasNext()) {
				if (!iter.next().isPoint()) {
					iter.remove();
				}
			}
		}
		return pathObjects;
	}
	
	//J Possible function to include??? in Java 8
	//public synchronized List<PathObject> getObjectsByClass(Class<? extends PathObject> cls) {
	//	switch (cls.toString()) {
	//		case "Point": 		return getObjects(null,cls).stream().filter(x->x.isPoint()).collect(Collectors.toList()); 
	//		case "Annotation": 	return getObjects(null,cls).stream().filter(x->x.isAnnotation()).collect(Collectors.toList());
	//		default: 			return new ArrayList<>();
	//	}
	//}
	
	public Collection<PathObject> getCellObjects() {
		return getObjects(null, PathDetectionObject.class);
	}
	
	public Collection<PathObject> getDetectionObjects() {
		return getObjects(null, PathDetectionObject.class);
	}
	
	public Collection<PathObject> getAnnotationObjects() {
		return getObjects(null, PathAnnotationObject.class);
	}

	public Collection<PathObject> getObjects(Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		if (pathObjects == null)
			pathObjects = new ArrayList<>();
		
		// If we want annotations, it can be much faster to get them from the tile cache than to sift through a potentially large number of detections
		if (PathAnnotationObject.class == cls && tileCache != null && tileCache.isActive()) {
			pathObjects.addAll(tileCache.getObjectsForRegion(cls, null, null, true));
			return pathObjects;
		}
		
		addPathObjectsRecursively(getRootObject(), pathObjects, cls);
		return pathObjects;
	}
	
	/**
	 * Get all descendant objects with a specified type
	 * 
	 * @param pathObject
	 * @param pathObjects
	 * @param cls
	 * @return
	 */
	public synchronized List<PathObject> getDescendantObjects(PathObject pathObject, List<PathObject> pathObjects, Class<? extends PathObject> cls) {
		if (pathObjects == null)
			pathObjects = new ArrayList<>();
		if (pathObject == null || !pathObject.hasChildren())
			return pathObjects;
		addPathObjectsRecursively(pathObject.getChildObjects(), pathObjects, cls);
		return pathObjects;
	}
	
	/**
	 * Update an object that is already in the hierarchy (e.g. because its ROI has changed).
	 * 
	 * @param pathObject
	 */
	public void updateObject(PathObject pathObject) {
		if (inHierarchy(pathObject))
			removeObject(pathObject, true, false);
		addPathObject(pathObject, true, false);
		fireObjectsChangedEvent(this, Collections.singletonList(pathObject), false);
	}
	
	
	/**
	 * Get a flattened list containing all PathObjects in the hierarchy (apart from the root object).
	 * 
	 * @param list - optional list into which the objects should be added (may be null)
	 * @return
	 */
	public synchronized List<PathObject> getFlattenedObjectList(List<PathObject> list) {
		if (list == null)
			list = new ArrayList<>(nObjects());
		getObjects(list, PathObject.class);
		return list;
	}
	
	
	public synchronized int nObjects() {
		int count = PathObjectTools.countDescendants(getRootObject());
		return count;
	}
	
	public synchronized void setHierarchy(PathObjectHierarchy hierarchy) {
		if (this == hierarchy)
			return;
		rootObject = hierarchy.getRootObject();
		tmaGrid = hierarchy.tmaGrid;
		fireHierarchyChangedEvent(rootObject);
	}
	
	
	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(cls, region, pathObjects, true);
	}
	
	public boolean hasObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region) {
		return tileCache.hasObjectsForRegion(cls, region, true);
	}
	
	
	synchronized void fireObjectRemovedEvent(Object source, PathObject pathObject, PathObject previousParent) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectRemovedEvent(source, this, previousParent, pathObject);
		fireEvent(event);
	}

	synchronized void fireObjectAddedEvent(Object source, PathObject pathObject) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectAddedEvent(source, this, pathObject.getParent(), pathObject);
		fireEvent(event);
	}
	
	
	public synchronized void fireObjectMeasurementsChangedEvent(Object source, Collection<PathObject> pathObjects) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_MEASUREMENTS, pathObjects, false);
		fireEvent(event);
	}
	
	public synchronized void fireObjectClassificationsChangedEvent(Object source, Collection<PathObject> pathObjects) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_CLASSIFICATION, pathObjects, false);
		fireEvent(event);
	}
	

	public synchronized void fireObjectsChangedEvent(Object source, Collection<? extends PathObject> pathObjects) {
		fireObjectsChangedEvent(source, pathObjects, false);
	}

	public synchronized void fireObjectsChangedEvent(Object source, Collection<? extends PathObject> pathObjects, boolean isChanging) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_OTHER, pathObjects, isChanging);
		fireEvent(event);
	}
	
	public synchronized void fireHierarchyChangedEvent(Object source, PathObject pathObject) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createStructureChangeEvent(source, this, pathObject);
		fireEvent(event);
	}

	public synchronized void fireHierarchyChangedEvent(Object source) {
		fireHierarchyChangedEvent(source, getRootObject());
	}
	
	
	void fireEvent(PathObjectHierarchyEvent event) {
		if (listeners != null) {
			for (PathObjectHierarchyListener listener : listeners.toArray(new PathObjectHierarchyListener[0]))
				listener.hierarchyChanged(event);
		}
	}
	
	
	@Override
	public String toString() {
		return "Hierarchy: " + nObjects() + " objects";
	}
	
}
