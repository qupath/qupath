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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.TemporaryObject;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * A tile cache that keeps a reference to a collection of PathObjects as flat lists.
 * It endeavors to keep itself synchronized with a PathObjectHierarchy,
 * responding to its change events.
 * <p>
 * In practice, the cache itself is constructed lazily whenever a request is made 
 * through getObjectsForRegion, so as to avoid rebuilding it too often when the hierarchy
 * is changing a lot.
 * 
 * @author Pete Bankhead
 *
 */
class PathObjectTileCache implements PathObjectHierarchyListener {
	
	public static int DEFAULT_TILE_SIZE = 1024;
	
	final private static Logger logger = LoggerFactory.getLogger(PathObjectTileCache.class);

	private Map<Class<? extends PathObject>, PathObjectTileMap> map = new HashMap<Class<? extends PathObject>, PathObjectTileMap>();
	
	private PathObjectHierarchy hierarchy;
	private boolean isActive = false;
	private int tileSize = DEFAULT_TILE_SIZE;
	
	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
	
	
	public PathObjectTileCache(PathObjectHierarchy hierarchy, int tileSize) {
		this.hierarchy = hierarchy;
		this.tileSize = tileSize;
		if (hierarchy != null)
			hierarchy.addPathObjectListener(this);
	}
	
	public PathObjectTileCache(PathObjectHierarchy hierarchy) {
		this(null, DEFAULT_TILE_SIZE);
	}

	public void resetCache() {
		isActive = false;
		logger.trace("Cache reset!");
	}
	
//	int cacheCounter = 0;

	private void constructCache() {
		w.lock();
		try {
	//		logger.info("Skipping cache reconstruction...");
			long startTime = System.currentTimeMillis();
			isActive = true;
			map.clear();
			addToCache(hierarchy.getRootObject(), true);
			long endTime = System.currentTimeMillis();
			logger.trace("Cache reconstructed in " + (endTime - startTime)/1000.);
		} finally {
			w.unlock();
		}
//		cacheCounter += (endTime - startTime);
//		logger.info("Cache count: " + (cacheCounter)/1000.);
	}
	
	private void ensureCacheConstructed() {
		if (!isActive())
			constructCache();
	}
	
	// TRUE if the cache has been constructed
	public boolean isActive() {
		return isActive;
	}
	
	/**
	 * Add a PathObject to the cache, optionally including children.
	 * 
	 * The lock is not acquired here!
	 * 
	 * @param pathObject
	 * @param includeChildren
	 */
	private void addToCache(PathObject pathObject, boolean includeChildren) {
		// If the cache isn't active, we can ignore this... it will be constructed when it is needed
		if (!isActive())
			return;
		
		if (pathObject.hasROI()) {
			Class<? extends PathObject> cls = pathObject.getClass();
			PathObjectTileMap mapObjects = map.get(cls);
			if (mapObjects == null) {
				mapObjects = new PathObjectTileMap(tileSize);
				map.put(cls, mapObjects);
			}
			mapObjects.put(pathObject);
		}
		// Add the children
		if (includeChildren && !(pathObject instanceof TemporaryObject) && pathObject.hasChildren()) {
			for (PathObject child : pathObject.getChildObjects().toArray(new PathObject[0]))
				addToCache(child, includeChildren);
		}
		
	}
	
	
	/**
	 * This doesn't acquire the lock!
	 * 
	 * @param pathObject
	 * @param removeChildren
	 */
	private void removeFromCache(PathObject pathObject, boolean removeChildren) {
		// If the cache isn't active, then nothing to remove
		if (!isActive())
			return;
		
		//JClass<? extends PathObject> cls = pathObject.getClass();
		PathObjectTileMap mapObjects = map.get(pathObject.getClass()); //J
		//JPathObjectTileMap mapObjects = map.get(cls);
		if (mapObjects != null) {
			mapObjects.remove(pathObject);
		}
		// Remove the children
		if (removeChildren) {
			for (PathObject child : pathObject.getChildObjects())
				removeFromCache(child, removeChildren);
		}
	}
	
	
//	/**
//	 * Add a PathObject to the cache.  Child objects are not added.
//	 * @param pathObject
//	 */
//	private void addToCache(PathObject pathObject) {
//		addToCache(pathObject, false);
//	}
	
	
	/**
	 * Get all the PathObjects stored in this cache of a specified type and having ROIs with bounds overlapping a specified region.
	 * This does not guarantee that the ROI (which may not be rectangular) overlaps the region...
	 * but a quick test is preferred over a more expensive one.
	 * 
	 * Note that pathObjects will be added to the collection provided, if there is one.
	 * The same object will be added to this collection multiple times if it overlaps different tiles -
	 * again in the interests of speed, no check is made.
	 * However this can be addressed by using a Set as the collection.
	 * 
	 * If a collection is not provided, a HashSet is created & used instead.
	 * Either way, the collection actually used is returned.
	 * 
	 * @param cls a PathObject class, or null if all object types should be returned
	 * @param region an image region, or null if all objects with ROIs should be return
	 * @param pathObjects an (optional) existing collection to which PathObjects should be added
	 * @param includeSubclasses true if subclasses of the specified class should be included
	 * @return
	 */
	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, Collection<PathObject> pathObjects, boolean includeSubclasses) {
		ensureCacheConstructed();
		
		r.lock();
		try {
			// Iterate through all the classes, getting objects of the specified class or subclasses thereof
			for (Entry<Class<? extends PathObject>, PathObjectTileMap> entry : map.entrySet()) {
				if (cls == null || (includeSubclasses && cls.isAssignableFrom(entry.getKey())) || cls.isInstance(entry.getKey())) {
					if (entry.getValue() != null)
						pathObjects = entry.getValue().getObjectsForRegion(region, pathObjects);
				}
			}
	//		logger.info("Objects for " + region + ": " + (pathObjects == null ? 0 : pathObjects.size()));
			if (pathObjects == null)
				return Collections.emptySet();
			return pathObjects;
		} finally {
			r.unlock();
		}
	}
	
	public boolean hasObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, boolean includeSubclasses) {
		ensureCacheConstructed();
		
		r.lock();
		try {
			// Iterate through all the classes, getting objects of the specified class or subclasses thereof
			for (Entry<Class<? extends PathObject>, PathObjectTileMap> entry : map.entrySet()) {
				if (cls == null || cls.isInstance(entry.getKey()) || (includeSubclasses && cls.isAssignableFrom(entry.getKey()))) {
					if (entry.getValue() != null) {
						if (entry.getValue().hasObjectsForRegion(region))
							return true;
					}
				}
			}
			return false;
		} finally {
			r.unlock();
		}
	}
	
//	public synchronized Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, Rectangle region, Collection<PathObject> pathObjects) {
//		ensureCacheConstructed();
//		
//		if (pathObjects == null)
//			pathObjects = new HashSet<>();
//		
//		// Iterate through all the classes, getting objects of the specified class or subclasses thereof
//		if (cls == null) {
//			for (Class<? extends PathObject> tempClass : map.keySet())
//				getObjectsForRegion(tempClass, region, pathObjects);
//			return pathObjects;
//		}
//		
//		// Extract the map for the type
//		PathObjectTileMap mapObjects = map.get(cls);
//		if (mapObjects == null)
//			return pathObjects;
//		
//		// Get the objects
//		return mapObjects.getObjectsForRegion(region, pathObjects);
//	}
	
	

//	@Override
//	public void pathObjectChanged(PathObjectHierarchy pathObjectHierarchy, PathObject pathObject) {
//		// Remove, then re-add the object - ignoring children (which shouldn't be changed, as no structural change is associated with this event)
//		removeFromCache(pathObject, false);
//		addToCache(pathObject, false);
//	}


	@Override
	public void hierarchyChanged(final PathObjectHierarchyEvent event) {
//		logger.info("Type: " + event.getEventType());
		w.lock();
		try {
			if (event.getEventType() == HierarchyEventType.ADDED)
				addToCache(event.getChangedObjects().get(0), false);
			else if (event.getEventType() == HierarchyEventType.REMOVED)
				removeFromCache(event.getChangedObjects().get(0), false);
			else if (event.getEventType() == HierarchyEventType.OTHER_STRUCTURE_CHANGE || event.getEventType() == HierarchyEventType.CHANGE_OTHER) {
				if (event.getChangedObjects().size() == 1 && !event.getChangedObjects().get(0).isRootObject()) {
					removeFromCache(event.getChangedObjects().get(0), false);
					addToCache(event.getChangedObjects().get(0), false);					
				} else
					resetCache();
			}
		} finally {
			w.unlock();
		}
//		else if (event.getEventType() == HierarchyEventType.OBJECT_CHANGE)
//			resetCache(); // TODO: Check if full change is necessary for object change events			
	}
	
}


class PathObjectTileMap {

	final private int tileSize;
	final private Map<String, Set<PathObject>> map = new HashMap<>();
	
	
//	public String toString() {
//		int sum = 0;
//		for (String key : map.keySet()) {
////			logger.info(key + ": " + map.get(key).size());
//			sum += map.get(key).size();
//		}
//		return String.format("Map size: " + sum);
//	}
	
	public PathObjectTileMap(int tileSize) {
		this.tileSize = tileSize;
	}
	
	private String getKey(int tx, int ty, int z, int t) {
		return tx + "-" + ty + "-" + z + "-" + t;
	}
	
	/**
	 * Add a pathObject to the map.
	 * If it does not have a ROI, it will be ignored.
	 * Otherwise it is added to the map for as many tiles as its ROI's bounding box intersects.
	 * 
	 * @param pathObject
	 */
	public void put(PathObject pathObject) {
		if (!pathObject.hasROI())
			return;
		
//		if (pathObject.isPoint()) {
//			PathPointsROI points = (PathPointsROI)pathObject.getROI();
//		}
		
		// Compute the tiles & add as required
		ROI pathROI = pathObject.getROI();
		int tx1 = (int)(pathROI.getBoundsX() / tileSize);
		int ty1 = (int)(pathROI.getBoundsY() / tileSize);
		int tx2 = (int)((pathROI.getBoundsX() + pathROI.getBoundsWidth()) / tileSize);
		int ty2 = (int)((pathROI.getBoundsY() + pathROI.getBoundsHeight()) / tileSize);
		int z = pathROI.getZ();
		int t = pathROI.getT();
		for (int y = ty1; y <= ty2; y++) {
			for (int x = tx1; x <= tx2; x++) {
				putInMap(getKey(x, y, z, t), pathObject);
			}				
		}
	}
	
	public void remove(PathObject pathObject) {
		// In theory, we ought to be able just to query the map according to the object's ROI...
		// however, if the ROI has been edited this won't work - so, for now, we query every collection
		// TODO: Consider optimizing this if ROI editing is disabled
		for (Collection<PathObject> list : map.values())
			list.remove(pathObject);
		
//		// Compute the tiles & add as required
//		PathROI pathROI = pathObject.getROI();
//		if (pathROI == null)
//			return;
//		Rectangle bounds = pathROI.getBounds();
//		int tx1 = bounds.x / tileSize;
//		int ty1 = bounds.y / tileSize;
//		int tx2 = (bounds.x + bounds.width) / tileSize;
//		int ty2 = (bounds.y + bounds.height) / tileSize;
//		int z = pathROI.getZ();
//		int t = pathROI.getT();
//
//		for (int y = ty1; y <= ty2; y++) {
//			for (int x = tx1; x <= tx2; x++) {
//				Collection<PathObject> list = map.get(getKey(x, y, z, t));
//				if (list != null) {
//					list.remove(pathObject);
//				}
//			}				
//		}
	}
	
	private void putInMap(String key, PathObject pathObject) {
		Set<PathObject> set = map.get(key);
		if (set == null) {
			// TODO: It would be preferable to use a list if avoidance of duplicates is taken care of elsewhere
//			list = new ArrayList<>();
			set = new HashSet<>();
			map.put(key, set);
		}
		set.add(pathObject);
//		} else if (!list.contains(pathObject))
//			list.add(pathObject);
	}
	
	
//J	public Collection<PathObject> getListForTile(int tx, int ty, int z, int t) {
//J		String key = getKey(tx, ty, z, t);
//J		return map.get(key);
//J	}
	
	/**
	 * Get all the PathObjects stored in this map with ROIs with bounds overlapping a specified region.
	 * This does not guarantee that the ROI (which may not be rectangular) overlaps the region...
	 * but a quick test is preferred over a more expensive one.
	 * 
	 * Note that pathObjects will be added to the collection provided, if there is one.
	 * The same object will be added to this collection multiple times if it overlaps different tiles -
	 * again in the interests of speed, no check is made.
	 * However this can be fixed by using a Set as the collection.
	 * 
	 * If a collection is not provided, a HashSet is created & used instead.
	 * Either way, the collection actually used is returned.
	 * 
	 * @param region
	 * @param pathObjects
	 * @return
	 */
	public Collection<PathObject> getObjectsForRegion(ImageRegion region, Collection<PathObject> pathObjects) {
		// By default, use a set to avoid duplicates
		if (pathObjects == null)
			pathObjects = new HashSet<>();
		
		// If no region is provided, get everything
		if (region == null) {
			for (Collection<PathObject> list : map.values()) {
				if (list == null)
					continue;
				pathObjects.addAll(list);
			}
			return pathObjects;
		}

		// Loop through the required tiles
		int tx1 = region.getX() / tileSize;
		int ty1 = region.getY() / tileSize;
		int tx2 = (region.getX() + region.getWidth()) / tileSize;
		int ty2 = (region.getY() + region.getHeight()) / tileSize;
		int z = region.getZ();
		int t = region.getT();
		for (int y = ty1; y <= ty2; y++) {
			for (int x = tx1; x <= tx2; x++) {
				Set<PathObject> set = map.get(getKey(x, y, z, t));
				if (set == null)
					continue;
				// Add all the objects that really do intersect, testing as necessary
				if (x > tx1 && x < tx2 && y > ty1 && y < ty2)
					pathObjects.addAll(set);
				else {
					for (PathObject pathObject : set) {
						ROI pathROI = pathObject.getROI();
						if (pathObject.isPoint() || region.intersects(pathROI.getBoundsX(), pathROI.getBoundsY(), Math.max(pathROI.getBoundsWidth(), 1), Math.max(pathROI.getBoundsHeight(), 1)))
							pathObjects.add(pathObject);
					}
				}
			}				
		}
		return pathObjects;
	}
	
	
	
	public boolean hasObjectsForRegion(ImageRegion region) {
		if (map.isEmpty())
			return false;
		
		// If no region is provided, get everything
		if (region == null) {
			for (Collection<PathObject> list : map.values()) {
				if (list == null || list.isEmpty())
					continue;
				return true;
			}
			return false; //J
		}

		// Loop through the required tiles
		int tx1 = region.getX() / tileSize;
		int ty1 = region.getY() / tileSize;
		int tx2 = (region.getX() + region.getWidth()) / tileSize;
		int ty2 = (region.getY() + region.getHeight()) / tileSize;
		int z = region.getZ();
		int t = region.getT();
		for (int y = ty1; y <= ty2; y++) {
			for (int x = tx1; x <= tx2; x++) {
				Collection<PathObject> list = map.get(getKey(x, y, z, t));
				if (list == null || list.isEmpty())
					continue;
				// Add all the objects that really do intersect, testing as necessary
				if (x > tx1 && x < tx2 && y > ty1 && y < ty2)
					return true;
				else {
					for (PathObject pathObject : list) {
						ROI pathROI = pathObject.getROI();
						if (pathObject.isPoint() || region.intersects(pathROI.getBoundsX(), pathROI.getBoundsY(), pathROI.getBoundsWidth(), pathROI.getBoundsHeight()))
							return true;
					}
				}
			}				
		}
		return false;
	}
	
}
