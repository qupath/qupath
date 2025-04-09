/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.objects.hierarchy;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.DelaunayTools;
import qupath.lib.common.LogTools;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.regions.ImagePlane;
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
 */
public final class PathObjectHierarchy implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(PathObjectHierarchy.class);
	
	private TMAGrid tmaGrid = null;
	private PathObject rootObject = new PathRootObject();
	
	private final transient PathObjectSelectionModel selectionModel = new PathObjectSelectionModel();
	private final transient List<PathObjectHierarchyListener> listeners = new ArrayList<>();

	// Cache enabling faster access of objects according to location
	private final transient PathObjectTileCache tileCache = new PathObjectTileCache(this);

	// A map to store subdivisions, useful for finding neighbors
	private transient SubdivisionManager subdivisionManager = new SubdivisionManager();

	private transient AtomicLong eventCount = new AtomicLong();

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
		return (tmaGrid == null || tmaGrid.nCores() == 0) && !rootObject.hasChildObjects();// && featureMaps.isEmpty();
	}
	
	/**
	 * Add a hierarchy change listener.
	 * @param listener
	 * @since v0.4.0; replaced {@code addPathObjectListener(PathObjectHierarchyListener)}
	 */
	public void addListener(PathObjectHierarchyListener listener) {
		synchronized(listeners) {
			listeners.add(listener);
		}
	}

	
	/**
	 * Remove a hierarchy change listener.
	 * @param listener
	 * @since v0.4.0; replaced {@code removePathObjectListener(PathObjectHierarchyListener)}
	 */
	public void removeListener(PathObjectHierarchyListener listener) {
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
	
	/**
	 * Set the tma grid for this hierarchy.
	 * If identical to the current TMA grid, nothing will happen.
	 * Otherwise, if null then any existing TMA grid will be removed.
	 * @param tmaGrid
	 */
	public synchronized void setTMAGrid(TMAGrid tmaGrid) {
		if (this.tmaGrid == tmaGrid)
			return;
		if (this.tmaGrid != null) {
			removeObjects(new ArrayList<>(this.tmaGrid.getTMACoreList()), false);
		}
		this.tmaGrid = tmaGrid;
		if (tmaGrid != null)
			addObjects(tmaGrid.getTMACoreList());
		fireHierarchyChangedEvent(getRootObject());
	}
	

	/**
	 * Comparator to use when looking for a parent annotation in the hierarchy.
	 * The logic is:
	 * <ol>
	 *   <li>Sort by area (smallest first)</li>
	 *   <li>Sort by hierarchy level (deepest first)</li>
	 *   <li>Sort by {@link DefaultPathObjectComparator}</li>
	 * </ol>
	 * In practice, one expects an object to be placed inside the smallest containing annotation - 
	 * identical areas are likely to be rare, unless obtained by duplication.
	 */
	public static final Comparator<PathObject> HIERARCHY_COMPARATOR = 
			Comparator.comparingDouble((PathObject p) -> {
				if (!p.hasROI())
					return Double.POSITIVE_INFINITY;
				else
					return p.getROI().getArea();
			})
			.thenComparing(Comparator.comparingInt(PathObject::getLevel).reversed())
			.thenComparing(DefaultPathObjectComparator.getInstance());
	
	/**
	 * Insert an object into the hierarchy. This differs from {@link #addObject(PathObject, boolean)} in that it will seek to 
	 * place the object in an appropriate location relative to existing objects, using the logic of {@link #HIERARCHY_COMPARATOR}.
	 * @param pathObject the object to add
	 * @param fireChangeEvents if true, an event will be added after adding the object. Choose false if a single event should be added after making multiple changes.
	 * @return true if the hierarchy changed as a result of this call, false otherwise
	 */
	public synchronized boolean insertPathObject(PathObject pathObject, boolean fireChangeEvents) {
		return insertPathObject(getRootObject(), pathObject, fireChangeEvents, !fireChangeEvents);
	}
	
	/**
	 * Insert a collection of objects into the hierarchy, firing a change event on completion.
	 * This differs from {@link #addObjects(Collection)} in that it will seek to 
	 * place the object in an appropriate location relative to existing objects, using the logic of {@link #HIERARCHY_COMPARATOR}.
	 * @param pathObjects the objects to add
	 * @return true if the hierarchy changed as a result of this call, false otherwise
	 */
	public synchronized boolean insertPathObjects(Collection<? extends PathObject> pathObjects) {
		var selectedObjects =  new ArrayList<>(pathObjects);
		int nObjects = selectedObjects.size();
		selectedObjects.removeIf(PathObject::isTMACore);
		if (selectedObjects.size() < nObjects)
			logger.warn("TMA core objects cannot be inserted - use resolveHierarchy() instead");
		
		if (selectedObjects.isEmpty())
			return false;
		removeObjects(selectedObjects, true);
		selectedObjects.sort(PathObjectHierarchy.HIERARCHY_COMPARATOR.reversed());
		boolean singleObject = selectedObjects.size() == 1;
		// We don't want to reset caches for every object if we have only detections, since previously-inserted objects don't impact the potential parent
		boolean allDetections = selectedObjects.stream().allMatch(PathObject::isDetection);
		for (var pathObject : selectedObjects) {
//			hierarchy.insertPathObject(pathObject, true);
			insertPathObject(getRootObject(), pathObject, singleObject, !singleObject && !allDetections);
//			insertPathObject(pathObject, selectedObjects.size() == 1);
		}
		if (!singleObject)
			fireHierarchyChangedEvent(this);
		return true;
	}
	
	/**
	 * Attempt to resolve the parent-child relationships between all objects within the hierarchy.
	 */
	public synchronized void resolveHierarchy() {
		List<? extends PathObject> tmaCores = tmaGrid == null ? Collections.emptyList() : tmaGrid.getTMACoreList();
		var annotations = getAnnotationObjects();
		if (annotations.isEmpty() && tmaCores.isEmpty()) {
			logger.debug("resolveHierarchy() called with no annotations or TMA cores!");
			return;
		}
		var detections = getDetectionObjects();
		if (annotations.size() > 1 && detections.size() > 1000) {
			logger.warn("Resolving hierarchy that contains {} annotations and {} detections - this may be slow!",
					annotations.size(), detections.size());
		} else if (annotations.size() > 100) {
			logger.warn("Resolving hierarchy with {} annotations - this may be slow!", annotations.size());
		}
		if (!tmaCores.isEmpty()) {
			// Need to remove annotations first (they will be re-inserted later) so we can resolve detections if needed
			if (!annotations.isEmpty())
				removeObjects(annotations, true);
			var remainingDetections = detections.stream().filter(p -> p.getParent() == rootObject).toList();
			if (!remainingDetections.isEmpty())
				insertPathObjects(remainingDetections);
		}
		insertPathObjects(annotations);
	}
	
	/**
	 * Insert a path object at the appropriate place in the hierarchy, without making other changes.
	 * @param pathObjectParent the first potential parent; this can be used to help filter out 'impossible' parents to aid performance
	 * @param pathObject the object to insert
	 * @param fireChangeEvents if true, fire hierarchy change events after inserting the object
	 * @param resetCache if true, reset the tile cache after adding the object; this is only used if fireChangeEvents is false
	 * @return
	 */
	private synchronized boolean insertPathObject(PathObject pathObjectParent, PathObject pathObject, boolean fireChangeEvents, boolean resetCache) {
		
		if (pathObject.isTMACore()) {
			logger.warn("TMA core objects cannot be inserted - use resolveHierarchy() instead");
			return false;
		}

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

		var possibleParentObjects = new ArrayList<>(tempSet);
		possibleParentObjects.sort(HIERARCHY_COMPARATOR);

		for (PathObject possibleParent : possibleParentObjects) {
			if (possibleParent == pathObject || possibleParent.isDetection())
				continue;
			
			boolean addObject;
			if (possibleParent.isRootObject()) {
				// If we've reached the root, definitely add
				addObject = true;
			} else {
				// If we're adding a detection, check centroid; otherwise check covers
				if (pathObject.isDetection())
					addObject = tileCache.containsCentroid(possibleParent, pathObject);
				else
					addObject = pathObjectParent != null && possibleParent == pathObjectParent ||
								tileCache.covers(possibleParent, pathObject);
			}
			
			if (addObject) {
				// Don't add if we're already where we should be
				if (pathObject.getParent() == possibleParent)
					return false;
				
				// Reassign child objects if we need to
				Collection<PathObject> previousChildren = pathObject.isDetection() ? new ArrayList<>() : new ArrayList<>(possibleParent.getChildObjects());
				// Can't reassign TMA core objects (these must be directly below the root object)
				previousChildren.removeIf(PathObject::isTMACore);
				// Beware that we could have 'orphaned' detections
				if (possibleParent.isTMACore())
					possibleParent.getParent().getChildObjects().stream().filter(PathObject::isDetection).forEach(previousChildren::add);
				possibleParent.addChildObject(pathObject);
				if (!previousChildren.isEmpty()) {
					pathObject.addChildObjects(filterObjectsForROI(pathObject.getROI(), previousChildren));
				}
				
				// Notify listeners of changes, if required
				if (fireChangeEvents)
					fireObjectAddedEvent(this, pathObject);
				else if (resetCache)
					tileCache.resetCache();
				return true;
			}
		}
		return true;
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
		boolean hasChildren = pathObject.hasChildObjects();
		
		pathObjectParent.removeChildObject(pathObject);

		// Assign the children to the parent object, if necessary
		if (keepChildren && hasChildren) {
			// We create a new array list because getPathObjectList returns an unmodifiable collection
//			List<PathObject> list = new ArrayList<>(pathObject.getPathObjectList());
			pathObjectParent.addChildObjects(pathObject.getChildObjects());
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
            List<PathObject> list = map.computeIfAbsent(parent, k -> new ArrayList<>());
            list.add(pathObject);
		}
		
		if (map.isEmpty())
			return;
		
		// Loop through and remove objects, keeping children if necessary
		Set<PathObject> childrenToKeep = new LinkedHashSet<>();
		for (Entry<PathObject, List<PathObject>> entry : map.entrySet()) {
			PathObject parent = entry.getKey();
			List<PathObject> children = entry.getValue();
			parent.removeChildObjects(children);
			if (keepChildren) {
				for (PathObject child : children)
					childrenToKeep.addAll(child.getChildObjects());
			}
		}
		childrenToKeep.removeAll(pathObjects);
		// Add children back if required (note: this can be quite slow!)
		tileCache.resetCache();
		for (PathObject pathObject : childrenToKeep) {
			addPathObjectImpl(pathObject, false);
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
	
	
	// TODO: Be very cautious about this!!!!  Use of tileCache inside a synchronized method might lead to deadlocks?
	private synchronized boolean addPathObjectToList(PathObject pathObjectParent, PathObject pathObject, boolean fireChangeEvents) {
		pathObjectParent.addChildObject(pathObject);
		// Notify listeners of changes, if required
		if (fireChangeEvents)
			fireObjectAddedEvent(this, pathObject);
		return true;
	}
	
	
	/**
	 * Add an object to the hierarchy, firing an event.
	 * @param pathObject
	 * @return
	 */
	public boolean addObject(PathObject pathObject) {
		return addPathObjectImpl(pathObject, true);
	}
	
	/**
	 * Add an object to the hierarchy, optionally firing an event.
	 * @param pathObject
	 * @param fireUpdate 
	 * @return
	 * @since v0.4.0; replaces {@code addPathObjectWithoutUpdate(PathObject)}
	 */
	public boolean addObject(PathObject pathObject, boolean fireUpdate) {
		return addPathObjectImpl(pathObject, fireUpdate);
	}

	
	/**
	 * Add path object as descendant of the requested parent.
	 * 
	 * @param pathObjectParent
	 * @param pathObject
	 * @param fireUpdate
	 * @return
	 * @since v0.4.0 (replaces {@code addPathObjectBelowParent(PathObject, PathObject, boolean)}
	 */
	public synchronized boolean addObjectBelowParent(PathObject pathObjectParent, PathObject pathObject, boolean fireUpdate) {
		if (pathObjectParent == pathObject)
			throw new IllegalArgumentException("Cannot add a PathObject as a descendent of itself!");
		if (pathObjectParent == null)
			return addPathObjectImpl(pathObject, fireUpdate);
		else
			return addPathObjectToList(pathObjectParent, pathObject, fireUpdate);
	}
	
	
	/**
	 * Add an object to the hierarchy.
	 * @param pathObject the object to add
	 * @param fireUpdate if true, fire an update event after the object is added
	 * @return
	 */
	private synchronized boolean addPathObjectImpl(PathObject pathObject, boolean fireUpdate) {
		if (pathObject == getRootObject() || !pathObject.hasROI())
			return false;
		return addPathObjectToList(getRootObject(), pathObject, fireUpdate);
	}
	
	/**
	 * Add multiple objects to the hierarchy.
	 * @param pathObjects
	 * @return
	 * @since v0.4.0; replaces {@code addPathObjects(Collection)}
	 */
	public synchronized boolean addObjects(Collection<? extends PathObject> pathObjects) {
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
		if (changes) {
			fireHierarchyChangedEvent(getRootObject());
		}
		return changes;
	}

	
	/**
	 * Remove all objects from the hierarchy.
	 */
	public synchronized void clearAll() {
		getRootObject().clearChildObjects();
		tmaGrid = null;
		fireHierarchyChangedEvent(getRootObject());
	}
	

	/**
	 * Get objects that contain Point ROIs.
	 * @param cls
	 * @return
	 * @deprecated v0.6.0; use {@link #getAllPointObjects()} instead, and filter by object type if required.
	 */
	@Deprecated
	public synchronized Collection<PathObject> getPointObjects(Class<? extends PathObject> cls) {
		LogTools.warnOnce(logger, "getPointObjects() is deprecated, use getAllPointObjects() instead");
		Collection<PathObject> pathObjects = getObjects(null, cls);
		if (!pathObjects.isEmpty()) {
            pathObjects.removeIf(pathObject -> !PathObjectTools.hasPointROI(pathObject));
		}
		return pathObjects;
	}

	/**
	 * Get all objects in the hierarchy that have a point (or multi-point) ROI.
	 * @return
	 */
	public Collection<PathObject> getAllPointObjects() {
		return getAllObjects(false).stream().filter(PathObjectTools::hasPointROI).toList();
	}

	/**
	 * Get all annotation objects in the hierarchy that have a point (or multi-point) ROI.
	 * @return
	 */
	public Collection<PathObject> getAllPointAnnotations() {
		return getAnnotationObjects().stream()
				.filter(PathObjectTools::hasPointROI)
				.toList();
	}
	
	/**
	 * Get all cell objects in the hierarchy.
	 * @return
	 */
	public Collection<PathObject> getCellObjects() {
		return getObjects(null, PathCellObject.class);
	}
	
	/**
	 * Get all tile objects in the hierarchy.
	 * @return
	 */
	public Collection<PathObject> getTileObjects() {
		return getObjects(null, PathTileObject.class);
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
	 * @param pathObject the object to update
	 * @param isChanging if true, indicate that the object is still being changed.
	 *                   Some listeners may delay processing in expectation of an update event where isChanging is false.
	 */
	public void updateObject(PathObject pathObject, boolean isChanging) {
		if (inHierarchy(pathObject))
			removeObject(pathObject, true, false);
		addPathObjectImpl(pathObject, false);
		fireObjectsChangedEvent(this, Collections.singletonList(pathObject), isChanging);
//		fireHierarchyChangedEvent(this, pathObject);
	}
	

	/**
	 * Get a flattened list containing all PathObjects in the hierarchy (including from the root object).
	 * <p>
	 * To get a flattened list containing all {@code PathObject}s <b>without</b> the root object, one can run the following:<br>
	 * {@code getFlattenedObjectList(null).stream().filter(p -> !p.isRootObject()).toList()}
	 * <p>
	 * Or, since v0.4.0, just use {@link #getAllObjects(boolean)} instead.
	 * @param list
	 * @return
	 * @since {@link #getAllObjects(boolean)}
	 */
	public synchronized List<PathObject> getFlattenedObjectList(List<PathObject> list) {
		if (list == null)
			list = new ArrayList<>(nObjects()+ 1);
		getObjects(list, PathObject.class);
		return list;
	}
	
	/**
	 * Get all the objects in the hierarchy, optionally including the root object.
	 * @param includeRoot
	 * @return
	 * @since v0.4.0
	 */
	public synchronized Collection<PathObject> getAllObjects(boolean includeRoot) {
		var set = new LinkedHashSet<PathObject>(nObjects() + 1, 1f);
		getObjects(set, PathObject.class);
		if (includeRoot) {
			// Root already be included
			if (set.add(getRootObject())) {
				logger.warn("Root object was added!");
			}
		} else {
			set.remove(getRootObject());
		}
		return set;
	}
	
	
	/**
	 * Number of objects in the hierarchy, excluding the root.
	 * @return
	 */
	public synchronized int nObjects() {
		return PathObjectTools.countDescendants(getRootObject());
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
	 * This relies on centroids for detections (including subclasses), and a 'covers' rule for others (annotations, TMA cores).
	 * <p>
	 * <b>Note: </b> Since v0.6.0 use of this method is discouraged, and it may be deprecated and/or removed in a future
	 * release.
	 * Instead use {@link #getAllObjectsForROI(ROI)} and filter the returned collection;
	 * or, alternatively, use {@link #getAnnotationsForROI(ROI)}, {@link #getCellsForROI(ROI)},
	 * {@link #getAllDetectionsForROI(ROI)} or {@link #getTilesForROI(ROI)}.
	 * </p>
	 * @param cls class of PathObjects (e.g. PathDetectionObject), or null to accept all
	 * @param roi
	 * @return
	 */
	public Collection<PathObject> getObjectsForROI(Class<? extends PathObject> cls, ROI roi) {
		return getObjectsOfClassForROI(cls, roi);
	}


	private Collection<PathObject> getObjectsOfClassForROI(Class<? extends PathObject> cls, ROI roi) {
		if (roi.isEmpty() || !roi.isArea())
			return Collections.emptyList();

		Collection<PathObject> pathObjects = tileCache.getObjectsForRegion(cls, ImageRegion.createInstance(roi), new HashSet<>(), true);
		return filterObjectsForROI(roi, pathObjects);
	}

	/**
	 * Get all objects for a ROI.
	 * This uses the same rules as {@link #resolveHierarchy()}: annotations must be completely covered
	 * by the ROI, while detections need only have their centroid within the ROI.
	 * @param roi
	 * @return
	 * @see #getAnnotationsForROI(ROI)
	 * @see #getCellsForROI(ROI)
	 * @see #getAllDetectionsForROI(ROI)
	 * @see #getTilesForROI(ROI)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAllObjectsForROI(ROI roi) {
		return getObjectsOfClassForROI(null, roi);
	}

	/**
	 * Get all the annotations covered by the specified ROI.
	 * @param roi the ROI to use for filtering
	 * @return a collection of annotation objects that are completely covered by the specified ROI
	 * @see #getAllObjectsForROI(ROI)
	 * @implSpec This does <i>not</i> return all annotations that intersect with the ROI,
	 *           but rather only those that are <i>covered</i> by the ROI - consistent with the
	 *           behavior of {@link #resolveHierarchy()}.
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAnnotationsForROI(ROI roi) {
		return getObjectsOfClassForROI(PathAnnotationObject.class, roi);
	}

	/**
	 * Get all the tile objects with centroids falling within the specified ROI.
	 * Tile objects are a special subclass of detections.
	 * @param roi the ROI to use for filtering
	 * @return a collection of tile objects with centroids contained within the specified ROI
	 * @see #getAllObjectsForROI(ROI)
	 * @see #getAllDetectionsForROI(ROI)
	 * @implSpec This does <i>not</i> return all tiles that intersect with the ROI,
	 *           but rather only those whose centroid falls within the ROI - consistent with the
	 *           behavior of {@link #resolveHierarchy()}.
	 * @since v0.6.0
	 */
	public Collection<PathObject> getTilesForROI(ROI roi) {
		return getObjectsOfClassForROI(PathTileObject.class, roi);
	}

	/**
	 * Get all the cell objects with centroids falling within the specified ROI.
	 * Cell objects are a special subclass of detections.
	 * @param roi the ROI to use for filtering
	 * @return a collection of cell objects with centroids contained within the specified ROI
	 * @see #getAllObjectsForROI(ROI)
	 * @see #getAllDetectionsForROI(ROI)
	 * @implSpec This does <i>not</i> return all cells that intersect with the ROI,
	 *           but rather only those whose centroid falls within the ROI - consistent with the
	 *           behavior of {@link #resolveHierarchy()}.
	 * @since v0.6.0
	 */
	public Collection<PathObject> getCellsForROI(ROI roi) {
		return getObjectsOfClassForROI(PathCellObject.class, roi);
	}

	/**
	 * Get all the detection objects with centroids falling within the specified ROI -
	 * including subclasses of detections, such as cells and tiles.
	 * @param roi the ROI to use for filtering
	 * @return a collection of detection objects with centroids contained within the specified ROI
	 * @see #getAllObjectsForROI(ROI)
	 * @see #getCellsForROI(ROI)
	 * @see #getTilesForROI(ROI)
	 * @implSpec This does <i>not</i> return all cells that intersect with the ROI,
	 *           but rather only those whose centroid falls within the ROI - consistent with the
	 *           behavior of {@link #resolveHierarchy()}.
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAllDetectionsForROI(ROI roi) {
		return getObjectsOfClassForROI(PathDetectionObject.class, roi);
	}
	
	/**
	 * Filter the objects in a specified collection, returning only those contained 'inside' a ROI 
	 * as defined by the general rules for resolving the hierarchy 
	 * (i.e. centroids for detections, 'covers' rule for others).
	 * 
	 * @param roi
	 * @param pathObjects 
	 * @return
	 */
	Collection<PathObject> filterObjectsForROI(ROI roi, Collection<PathObject> pathObjects) {
		if (pathObjects.isEmpty() || !roi.isArea() || roi.isEmpty())
			return Collections.emptyList();
		
		// Note: JTS 1.17.0 does not support parallel requests, see https://github.com/locationtech/jts/issues/571
		// A change in getLocator() overcomes this - but watch out for future problems
		var relate = tileCache.getRoiRelate(roi);
		return pathObjects.parallelStream().filter(child -> {
			// Test plane first
			if (!sameZT(roi, child.getROI()))
				return false;

			if (child.isDetection())
				return relate.containsCentroid(child.getROI());
			else {
				return relate.coversWithTolerance(child.getROI());
			}
		}).collect(Collectors.toCollection(ArrayList::new));
	}
	
	
	/**
	 * Check if two ROIs fall in the same plane, i.e. have the same Z and T values.
	 * @param roi1
	 * @param roi2
	 * @return
	 */
	private static boolean sameZT(ROI roi1, ROI roi2) {
		return roi1.getZ() == roi2.getZ() && roi1.getT() == roi2.getT();
	}

	
	/**
	 * Get the objects overlapping or close to a specified region.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 * 
	 * @param cls class of object to return (subclasses are included)
	 * @param region requested region overlapping the objects ROI
	 * @param pathObjects optionally collection to which objects will be added
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @deprecated v0.6.0, use {@link #getAllObjectsForRegion(ImageRegion, Collection)} or its related methods instead.
	 * @see #getAllObjectsForRegion(ImageRegion, Collection)
	 * @see #getAnnotationsForRegion(ImageRegion, Collection)
	 * @see #getAllDetectionsForRegion(ImageRegion, Collection)
	 */
	@Deprecated
	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(cls, region, pathObjects, true);
	}

	/**
	 * Get all the objects overlapping or close to a specified region, optionally adding to an existing collection.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 *
	 * @param region requested region overlapping the objects ROI
	 * @param pathObjects optional collection to which objects will be added
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @see #getAllObjectsForROI(ROI)
	 * @see #getAllObjectsForRegion(ImageRegion)
	 * @see PathObjectTools#filterByROICovers(ROI, Collection)
	 * @see PathObjectTools#filterByROIIntersects(ROI, Collection) (ROI, Collection)
	 * @see PathObjectTools#filterByROIContainsCentroid(ROI, Collection) (ROI, Collection) (ROI, Collection)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAllObjectsForRegion(ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(null, region, pathObjects, true);
	}

	/**
	 * Get all the objects overlapping or close to a specified region.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 *
	 * @param region requested region overlapping the objects ROI
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @see #getAllObjectsForROI(ROI)
	 * @see #getAllObjectsForRegion(ImageRegion, Collection)
	 * @see PathObjectTools#filterByROICovers(ROI, Collection)
	 * @see PathObjectTools#filterByROIIntersects(ROI, Collection) (ROI, Collection)
	 * @see PathObjectTools#filterByROIContainsCentroid(ROI, Collection) (ROI, Collection) (ROI, Collection)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAllObjectsForRegion(ImageRegion region) {
		return getAllObjectsForRegion(region, null);
	}

	/**
	 * Get all the annotation objects overlapping or close to a specified region, optionally adding to an existing collection.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 *
	 * @param region requested region overlapping the objects ROI
	 * @param pathObjects optional collection to which objects will be added
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @see #getAnnotationsForRegion(ImageRegion)
	 * @see #getAllObjectsForRegion(ImageRegion, Collection)
	 * @see #getAnnotationsForROI(ROI)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAnnotationsForRegion(ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(PathAnnotationObject.class, region, pathObjects, true);
	}

	/**
	 * Get all the annotation objects overlapping or close to a specified region.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 *
	 * @param region requested region overlapping the objects ROI
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @see #getAnnotationsForRegion(ImageRegion, Collection)
	 * @see #getAllObjectsForRegion(ImageRegion)
	 * @see #getAnnotationsForROI(ROI)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAnnotationsForRegion(ImageRegion region) {
		return getAnnotationsForRegion(region, null);
	}

	/**
	 * Get all the detection objects overlapping or close to a specified region, optionally adding to an existing collection.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 *
	 * @param region requested region overlapping the objects ROI
	 * @param pathObjects optional collection to which objects will be added
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @see #getAllDetectionsForRegion(ImageRegion)
	 * @see #getAllObjectsForRegion(ImageRegion, Collection)
	 * @see #getAllDetectionsForROI(ROI) (ROI)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAllDetectionsForRegion(ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(PathDetectionObject.class, region, pathObjects, true);
	}

	/**
	 * Get all the detection objects overlapping or close to a specified region.
	 * Note that this performs a quick check; the results typically should be filtered if a more strict test for overlapping is applied.
	 *
	 * @param region requested region overlapping the objects ROI
	 * @return collection containing identified objects (same as the input collection, if provided)
	 * @see #getAllDetectionsForRegion(ImageRegion, Collection)
	 * @see #getAllObjectsForRegion(ImageRegion)
	 * @see #getAllDetectionsForROI(ROI) (ROI)
	 * @since v0.6.0
	 */
	public Collection<PathObject> getAllDetectionsForRegion(ImageRegion region) {
		return getAllDetectionsForRegion(region, null);
	}
	
	/**
	 * Returns true if the hierarchy contains objects overlapping a specific region, optionally filtering to identify only 
	 * objects of a specific class.
	 * @param cls
	 * @param region
	 * @return
	 * @since v0.6.0
	 */
	public boolean hasObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region) {
		return tileCache.hasObjectsForRegion(cls, region, true);
	}

	/**
	 * Returns true if the hierarchy contains any objects intersecting a specific region.
	 * This is similar to {@link #getAllObjectsForRegion(ImageRegion, Collection)}, 
	 * but does not return the objects themselves.
	 * @param region
	 * @return true if objects are found, false otherwise.
	 * @see #getAllObjectsForRegion(ImageRegion, Collection)
	 * @since v0.6.0
	 */
	public boolean hasObjectsForRegion(ImageRegion region) {
		return tileCache.hasObjectsForRegion(null, region, true);
	}

	/**
	 * Returns true if the hierarchy contains any annotation objects intersecting a specific region.
	 * This is similar to {@link #getAnnotationsForRegion(ImageRegion, Collection)}, 
	 * but does not return the objects themselves.
	 * @param region
	 * @return true if annotations are found, false otherwise.
	 * @see #getAnnotationsForRegion(ImageRegion, Collection)
	 * @since v0.6.0
	 */
	public boolean hasAnnotationsForRegion(ImageRegion region) {
		return tileCache.hasObjectsForRegion(PathAnnotationObject.class, region, true);
	}

	/**
	 * Returns true if the hierarchy contains any detection objects (including subclasses) intersecting a specific region.
	 * This is similar to {@link #getAllDetectionsForRegion(ImageRegion, Collection)},
	 * but does not return the objects themselves.
	 * @param region
	 * @return true if detections are found, false otherwise.
	 * @see #getAllDetectionsForRegion(ImageRegion, Collection)
	 * @since v0.6.0
	 */
	public boolean hasDetectionsForRegion(ImageRegion region) {
		return tileCache.hasObjectsForRegion(PathDetectionObject.class, region, true);
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
	public void fireObjectMeasurementsChangedEvent(Object source, Collection<? extends PathObject> pathObjects) {
		fireObjectMeasurementsChangedEvent(source, pathObjects, false);
	}
	
	/**
	 * Fire a hierarchy update indicating object measurements have changed.
	 * @param source
	 * @param pathObjects
	 * @param isChanging
	 */
	public void fireObjectMeasurementsChangedEvent(Object source, Collection<? extends PathObject> pathObjects, boolean isChanging) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectsChangedEvent(source, this, HierarchyEventType.CHANGE_MEASUREMENTS, pathObjects, isChanging);
		fireEvent(event);
	}
	
	/**
	 * Fire a hierarchy update indicating object classifications have changed.
	 * @param source
	 * @param pathObjects
	 */
	public void fireObjectClassificationsChangedEvent(Object source, Collection<? extends PathObject> pathObjects) {
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
			eventCount.incrementAndGet();
			if (!event.isChanging()) {
				if (event.isStructureChangeEvent()) {
					var changed = event.getChangedObjects();
					var classes = changed.stream().map(PathObject::getClass).distinct().toList();
					if (classes.isEmpty() || classes.contains(PathRootObject.class))
						resetNeighbors();
					else {
						for (var cls : classes) {
							resetNeighborsForClass(cls);
						}
					}
				}
			}

			for (PathObjectHierarchyListener listener : listeners) {
				listener.hierarchyChanged(event);
			}
		}
	}

	/**
	 * Get the number of events that were fired.
	 * <p>
	 * Important notes:
	 * <ul>
	 *     <li>This is a transient property (not stored in the data file)</li>
	 *    <li>The value is incremented <i>before</i>> listeners have been notified (so that a listener may query it)</li>
	 * </ul>
	 * The purpose of this method is to support lazy evaluation within functions that depend upon the hierarchy,
	 * but where adding and removing a listener would require too much overhead.
	 * Use of the event count makes it possible to cache the result of expensive computations, and return the cached
	 * values for as long as the count is unchanged.
	 *
	 * @return a timestamp
	 * @since v0.6.0
	 */
	public long getEventCount() {
		synchronized (listeners) {
			return eventCount.get();
		}
	}

	private synchronized void resetNeighborsForClass(Class<? extends PathObject> cls) {
		subdivisionManager.clear();
	}

	private synchronized void resetNeighbors() {
		subdivisionManager.clear();
	}

	/**
	 * Find all neighbors of a PathObject, having the same class as the object (e.g. detection, cell, annotation).
	 * This is based on centroids and Delaunay triangulation.
	 * It also assumes 'square' pixels, and searches for neighbors only on the same 2D plane (z and t).
	 * @param pathObject
	 * <p>
	 * This is an <i>experimental method</i> added in v0.6.0, subject to change.
	 * @return
	 * @since v0.6.0
	 */
	public synchronized List<PathObject> findAllNeighbors(PathObject pathObject) {
		var subdivision = getSubdivision(pathObject);
		return subdivision == null ? Collections.emptyList() : subdivision.getNeighbors(pathObject);
	}

	/**
	 * Find the nearest neighbor of a PathObject, having the same class as the object (e.g. detection, cell, annotation).
	 * This is based on centroids and Delaunay triangulation.
	 * It also assumes 'square' pixels, and searches for neighbors only on the same 2D plane (z and t).
	 * <p>
	 * This is an <i>experimental method</i> added in v0.6.0, subject to change.
	 * @param pathObject
	 * @return
	 * @since v0.6.0
	 */
	public synchronized PathObject findNearestNeighbor(PathObject pathObject) {
		var subdivision = getSubdivision(pathObject);
		return subdivision == null ? null : subdivision.getNearestNeighbor(pathObject);
	}

	/**
	 * Get the subdivision containing a specific PathObject.
	 * This is based on centroids and Delaunay triangulation in 2D.
	 * It supports #findAllNeighbors(PathObject) and #findNearestNeighbor(PathObject); obtaining the subdivision
	 * enables a wider range of spatial queries.
	 * <p>
	 * This is an <i>experimental method</i> added in v0.6.0, subject to change.
	 * @param pathObject
	 * @return
	 * @since v0.6.0
	 */
	public synchronized DelaunayTools.Subdivision getSubdivision(PathObject pathObject) {
		return subdivisionManager.getSubdivision(pathObject);
	}

	/**
	 * Get a subdivision containing detections.
	 * This does <i>not</i> include sub-classes such as 'cell' or 'tile'.
	 * <p>
	 * This is an <i>experimental method</i> added in v0.6.0, subject to change.
	 * @param plane
	 * @return
	 * @since v0.6.0
	 */
	public synchronized DelaunayTools.Subdivision getDetectionSubdivision(ImagePlane plane) {
		return subdivisionManager.getSubdivision(PathDetectionObject.class, plane);
	}

	/**
	 * Get a subdivision containing cell objects.
	 * <p>
	 * This is an <i>experimental method</i> added in v0.6.0, subject to change.
	 * @param plane
	 * @return
	 * @since v0.6.0
	 */
	public synchronized DelaunayTools.Subdivision getCellSubdivision(ImagePlane plane) {
		return subdivisionManager.getSubdivision(PathCellObject.class, plane);
	}

	/**
	 * Get a subdivision containing annotation objects.
	 * <p>
	 * This is an <i>experimental method</i> added in v0.6.0, subject to change.
	 * @param plane
	 * @return
	 * @since v0.6.0
	 */
	public synchronized DelaunayTools.Subdivision getAnnotationSubdivision(ImagePlane plane) {
		return subdivisionManager.getSubdivision(PathAnnotationObject.class, plane);
	}


	private DelaunayTools.Subdivision computeSubdivision(Class<? extends PathObject> cls) {
		var pathObjects = tileCache.getObjectsForRegion(cls,
				null, null, false);
		return DelaunayTools.createFromCentroids(pathObjects, true);
	}

	
	@Override
	public String toString() {
		return "Hierarchy: " + nObjects() + " objects";
	}

	private class SubdivisionManager {

		private static final DelaunayTools.Subdivision EMPTY = DelaunayTools.createFromCentroids(Collections.emptyList(), true);

		private static final Map<Class<? extends PathObject>,
				Map<ImagePlane, DelaunayTools.Subdivision>> subdivisionMap = new ConcurrentHashMap<>();

		synchronized DelaunayTools.Subdivision getSubdivision(PathObject pathObject) {
			if (pathObject == null || !pathObject.hasROI()) {
				return EMPTY;
			}
			return getSubdivision(pathObject.getClass(), pathObject.getROI().getImagePlane());
		}

		synchronized DelaunayTools.Subdivision getSubdivision(Class<? extends PathObject> cls, ImagePlane plane) {
			var map = subdivisionMap.computeIfAbsent(cls, k -> new ConcurrentHashMap<>());
			return map.computeIfAbsent(plane, k -> computeSubdivision(cls, plane));
		}

		private DelaunayTools.Subdivision computeSubdivision(Class<? extends PathObject> cls, ImagePlane plane) {
			var pathObjects = tileCache.getObjectsForRegion(cls,
					ImageRegion.createInstance(-Integer.MAX_VALUE/2, -Integer.MAX_VALUE/2,
							Integer.MAX_VALUE, Integer.MAX_VALUE, plane.getZ(), plane.getT()),
					null, false);
			return DelaunayTools.createFromCentroids(pathObjects, true);
		}

		private synchronized void clear() {
			subdivisionMap.clear();
		}

		private synchronized void clearClass(Class<? extends PathObject> cls) {
			subdivisionMap.getOrDefault(cls, Collections.emptyMap()).clear();
		}

	}
	
}
