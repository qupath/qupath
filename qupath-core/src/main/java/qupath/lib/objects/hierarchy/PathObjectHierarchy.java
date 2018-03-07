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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
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
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;

/**
 * A basic hierarchy in which to store PathObjects.
 * <p>
 * This basically contains a single PathRootObject from which all other objects can be reached using the child object lists.
 * However, by adding/removing objects via this hierarchy (rather than through the child lists directly), it is possible
 * to maintain a more consistent structure (e.g. by automatically inserting objects as children of the objects whose ROI completely
 * contains the object to be added), along with a spatial cache so that objects can be extracted if their ROIs overlap with a specified region.
 * 
 * TODO: Convert to more sustainable serialization
 * 
 * Note: Be cautious when deserializing - it may not result in a hierarchy in a valid state.
 * As a workaround, you can construct a new PathObjectHierarchy and call setHierarchy(deserializedHierarchy) to
 * ensure that you have a properly-constructed hierarchy with the same data within it.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectHierarchy implements Serializable {
	
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
	transient private PathObjectTileCache tileCache = new PathObjectTileCache(this, 512);

	
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
		keepChildren = keepChildren && pathObject.hasChildren();
		
		pathObjectParent.removePathObject(pathObject);

		// Assign the children to the parent object, if necessary
		if (keepChildren) {
			// We create a new array list because getPathObjectList returns an unmodifiable collection
//			List<PathObject> list = new ArrayList<>(pathObject.getPathObjectList());
			pathObjectParent.addPathObjects(pathObject.getChildObjects());
//			pathObject.clearPathObjects(); // Clear child objects, just in case
		}
		if (fireEvent) {
			if (keepChildren)
				fireObjectRemovedEvent(this, pathObject, pathObjectParent);
			else
				fireHierarchyChangedEvent(this, pathObjectParent);
		}
		
		return true;
	}
	
	/**
	 * Remove a collection of objects, firing a single 'hierarchy changed' event afterwards to notify listeners if anything happened
	 * (i.e. if any of the objects really were found within the hierarchy) & removed.
	 * 
	 * @param pathObjects
	 * @param keepChildren
	 */
	public synchronized void removeObjects(Collection<PathObject> pathObjects, boolean keepChildren) {
		
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
			logger.debug("Adding {} to hierarchy", pathObject);
		
//		// We can't add to a non-ROI
//		if (!pathObjectParent.hasROI() && pathObjectParent != getRootObject())
//			return false;
		
		if (!pathObjectParent.hasChildren()) {
			// The parent doesn't have any other children - so we can just add the object directly
			pathObjectParent.addPathObject(pathObject);
			// Notify listeners of changes
			if (fireChangeEvents)
				fireObjectAddedEvent(this, pathObject);
			logger.debug("Adding directly: {} has no child objects", pathObjectParent);
			return true;
		}
		Collection<PathObject> pathObjects = pathObjectParent.getChildObjects();
		if (avoidDuplicates && pathObjects.contains(pathObject)) {
			logger.warn("Warning: List already contains {}, will not be added again", pathObject);
			return false;
		}
		
		
		ROI pathROI = pathObject.getROI();
		ROI pathROIInner = useCellNucleiForInsideTest && (pathObject instanceof PathCellObject) ? ((PathCellObject)pathObject).getNucleusROI() : pathROI; //J
		if (useTileCentroidsForInsideTest && pathObject instanceof PathDetectionObject && !(pathROIInner instanceof PointsROI)) {
			double cx = pathROIInner.getCentroidX();
			double cy = pathROIInner.getCentroidY();
			boolean usePoint = true;
			if (pathROIInner instanceof PathArea) {
				PathArea tempArea = (PathArea)pathROIInner;
				// If the centroid is outside the tile, try the center of the bounding box instead
				if (!tempArea.contains(cx, cy)) {
					Point2 p = PathObjectTools.getContainedPoint(tempArea);
					usePoint = p != null;
					if (usePoint) {
						cx = p.getX();
						cy = p.getY();
					}
				}
			}
			if (usePoint)
				pathROIInner = new PointsROI(cx, cy, pathROIInner.getC(), pathROIInner.getZ(), pathROIInner.getT());
//			pathROIInner = new PointsROI(pathROIInner.getCentroidX(), pathROIInner.getCentroidY(), pathROIInner.getC(), pathROIInner.getZ(), pathROIInner.getT());
		}
		
		PathObject possibleParent = pathObjectParent;
		List<PathObject> possibleChildren = new ArrayList<>();
		ImageRegion region = ImageRegion.createInstance(pathROI);
		for (PathObject temp : tileCache.getObjectsForRegion(PathObject.class, region, null, true)) {
			
//			if (useTileCentroidsForInsideTest && temp.isDetection())
//				continue;
			
//			if (!temp.hasROI() || !temp.getParent().hasROI())
//				continue;
			
			ROI tempROI = temp.getROI();
			ROI tempROIInner = tempROI;;
			// Use the nucleus ROI of a cell if available & requested
			if (temp instanceof PathCellObject && useCellNucleiForInsideTest) {
				ROI nucleusROI = ((PathCellObject)temp).getNucleusROI();
				if (nucleusROI != null)
					tempROIInner = nucleusROI;
			}
//			= useCellNucleiForInsideTest && (temp instanceof PathCellObject) ? ((PathCellObject)temp).getNucleusROI() : tempROI; //J
			
//			if (useTileCentroidsForInsideTest && temp instanceof PathTileObject)
			if (useTileCentroidsForInsideTest && temp instanceof PathDetectionObject) {
				double cx = tempROIInner.getCentroidX();
				double cy = tempROIInner.getCentroidY();
				boolean usePoint = true;
				if (tempROIInner instanceof PathArea) {
					PathArea tempArea = (PathArea)tempROIInner;
					// If the centroid is outside the tile, try the center of the bounding box instead
					if (!tempArea.contains(cx, cy)) {
						Point2 p = PathObjectTools.getContainedPoint(tempArea);
						usePoint = p != null;
						if (usePoint) {
							cx = p.getX();
							cy = p.getY();
						}
					}
				}
				if (usePoint)
					tempROIInner = new PointsROI(cx, cy, tempROI.getC(), tempROI.getZ(), tempROI.getT());
//				tempROIInner = new RectangleROI(tempROIInner.getCentroidX()-.5, tempROIInner.getCentroidY()-.5,  1,  1, tempROI.getC(), tempROI.getZ(), tempROI.getT());
			}

			if (!(temp instanceof TMACoreObject) && pathROI != tempROIInner && PathObjectTools.containsROI(pathROI, tempROIInner)){
				possibleChildren.add(temp);
			} else if (tempROI != pathROIInner && PathObjectTools.containsROI(tempROI, pathROIInner)) {
				if (possibleParent == null)
					possibleParent = temp;
				else if (temp.getLevel() > possibleParent.getLevel()) // We want the highest level to be the parent, i.e. deepest in hierarchy
					possibleParent = temp;
			}
		}
		// Add the ROI, and reassign any children from the parent
		Iterator<PathObject> iterChild = possibleChildren.iterator();
		while (iterChild.hasNext()) {
			if (iterChild.next().getParent() != possibleParent)
				iterChild.remove();
		}
		pathObject.addPathObjects(possibleChildren);
		possibleParent.addPathObject(pathObject);
		

				
//		// If an object completely contains the ROI of the object we want to add, then add it as child of the containing object
//		for (PathObject pathObjectChild : pathObjectList) {
//			if (containsROI(pathObjectChild.getROI(), pathROI)) {
//				// Check the next level of the hierarchy, trying to add there
//				return addPathObjectToList(pathObjectChild, pathObject, avoidDuplicates, fireChangeEvents);
//			}
//		}
//		
//		// We will be adding the object here - but first check to see if we need to set some new parents, i.e.
//		// find out if there are any other objects at this level that should be added as children
//		List<PathObject> childrenToReassign = null;
//		for (PathObject child : pathObjectList) {
//			if (!(child instanceof TMACoreObject ) && containsROI(pathROI, child.getROI())) {
//				if (childrenToReassign == null)
//					childrenToReassign = new ArrayList<>();
//				childrenToReassign.add(child);
//			}
//		}
//		if (childrenToReassign != null)
//			pathObject.addPathObjects(childrenToReassign);
//		
////		Iterator<PathObject> iter = pathObjectList.iterator();
////		while (iter.hasNext()) {
//////			logger.info(pathObjectParent);
////			PathObject pathObjectChild = iter.next();
////			if (containsROI(pathObject.getROI(), pathObjectChild.getROI())) {
////				iter.remove();
//////				pathObjectChild.setParent(null);
////				pathObject.addPathObject(pathObjectChild);
////			}
////		}
//		
//		// Add as a child
//		pathObjectParent.addPathObject(pathObject);
		
		// Notify listeners of changes, if required
		if (fireChangeEvents)
			fireObjectAddedEvent(this, pathObject);
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
	
	public synchronized boolean addPathObjects(Collection<PathObject> pathObjects, boolean avoidDuplicates) {
		boolean changes = false;
		int n = pathObjects.size();
		int counter = 0;
		for (PathObject pathObject : pathObjects) {
			if (n > 10000) {
				if (counter % 1000 == 0)
					logger.info("Adding {} of {}", counter, n);
			} else if (n > 1000 && counter % 100 == 0)
				logger.info("Adding {} of {}", counter, n);
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
	
	
	private synchronized void addPathObjectsRecursively(PathObject pathObject, List<PathObject> pathObjects, Class<? extends PathObject> cls) {
		// Prefer to iterate through long lists and process as we go, rather than handle one object per method call
		addPathObjectsRecursively(Collections.singleton(pathObject), pathObjects, cls);
	}
	
	
	private static void addPathObjectsRecursively(Collection<PathObject> pathObjectsInput, List<PathObject> pathObjects, Class<? extends PathObject> cls) {
		for (PathObject childObject : pathObjectsInput) {
			if (cls == null || cls.isInstance(childObject)) {
				pathObjects.add(childObject);
			}
			if (childObject.hasChildren())
				addPathObjectsRecursively(childObject.getChildObjects(), pathObjects, cls);
		}
	}

	public synchronized List<PathObject> getPointObjects(Class<? extends PathObject> cls) {
		List<PathObject> pathObjects = getObjects(null, cls);
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

	public List<PathObject> getObjects(List<PathObject> pathObjects, Class<? extends PathObject> cls) {
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
	 * @param type
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
	
	
//	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, Rectangle region, Collection<PathObject>pathObjects) {
//		return tileCache.getObjectsForRegion(cls, region, pathObjects, true);
//	}
	
	
	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, Collection<PathObject> pathObjects) {
		return tileCache.getObjectsForRegion(cls, region, pathObjects, true);
	}
	
	public boolean hasObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region) {
		return tileCache.hasObjectsForRegion(cls, region, true);
	}
	
	
	protected synchronized void fireObjectRemovedEvent(Object source, PathObject pathObject, PathObject previousParent) {
		PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectRemovedEvent(source, this, previousParent, pathObject);
		fireEvent(event);
	}

	protected synchronized void fireObjectAddedEvent(Object source, PathObject pathObject) {
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
//	
//	public synchronized void fireObjectChangedEvent(Object source, PathObject pathObject) {
//		PathObjectHierarchyEvent event = new PathObjectHierarchyEvent(source, this, HierarchyEventType.OBJECT_CHANGE, pathObject, true);
//		for (PathObjectHierarchyListener listener : listeners)
//			listener.hierarchyChanged(event);
//	}
	
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
	
	
//	@Deprecated
//	List<TiledFeatureMap> getTiledFeatureMaps() {
////		return new ArrayList<>(featureMaps);
//		return featureMaps;
//	}
//	
//	@Deprecated
//	void addTiledFeatureMap(TiledFeatureMap map) {
//		if (featureMaps.isEmpty()) {
//			featureMaps.add(map);
//			return;
//		}
//		featureMaps.add(map);
//		featureMaps.sort(new Comparator<>() {
//
//			@Override
//			public int compare(TiledFeatureMap map1, TiledFeatureMap map2) {
//				return Double.compare(map1.getTileWidth(), map2.getTileWidth());
//			}
//			
//		});
//	}
//	
//	@Deprecated
//	boolean removeTiledFeatureMap(TiledFeatureMap map) {
//		return featureMaps.remove(map);
//	}
	
}
