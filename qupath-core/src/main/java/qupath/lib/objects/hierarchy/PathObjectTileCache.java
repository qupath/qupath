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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
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
 * through getObjectsForRegion, to avoid rebuilding it too often when the hierarchy
 * is changing a lot.
 * 
 * @author Pete Bankhead
 *
 */
class PathObjectTileCache implements PathObjectHierarchyListener {
	
	private static final Logger logger = LoggerFactory.getLogger(PathObjectTileCache.class);
	
	/**
	 * Largest positive envelope, used when all objects are requested.
	 */
	private static final Envelope MAX_ENVELOPE = new Envelope(-Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE);
	
	/**
	 * Store a spatial index according to the class of PathObject.
	 */
	private final Map<Class<? extends PathObject>, SpatialIndex> map = new HashMap<>();
	
	/**
	 * Map to cache Geometries, specifically for annotations.
	 */
	private static final Map<ROI, Geometry> geometryMap = Collections.synchronizedMap(new WeakHashMap<>());

	/**
	 * Map to cache helper classes to determine the relationship between ROIs.
	 * This is important when relationships are expensive (e.g. for complex geometries).
	 */
	private static final Map<ROI, RoiRelate> relateMap = Collections.synchronizedMap(new WeakHashMap<>());

	private final PathObjectHierarchy hierarchy;
	private boolean isActive = false;
	
	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
	
	
	public PathObjectTileCache(PathObjectHierarchy hierarchy) {
		this.hierarchy = hierarchy;
		if (hierarchy != null)
			hierarchy.addListener(this);
	}
	
	public void resetCache() {
		isActive = false;
		logger.trace("Cache reset!");
	}
	
//	int cacheCounter = 0;

	private void constructCache(Class<? extends PathObject> limitToClass) {
		w.lock();
		try {
	//		logger.info("Skipping cache reconstruction...");
			long startTime = System.currentTimeMillis();
			isActive = true;
			if (limitToClass == null)
				map.clear();
			else
				map.remove(limitToClass);
			addToCache(hierarchy.getRootObject(), true, limitToClass);
			long endTime = System.currentTimeMillis();
            logger.debug("Cache reconstructed in {} ms", endTime - startTime);
		} finally {
			w.unlock();
		}
//		cacheCounter += (endTime - startTime);
//		logger.info("Cache count: " + (cacheCounter)/1000.);
	}
	
	private void ensureCacheConstructed() {
		if (!isActive())
			constructCache(null);
	}
	
	// TRUE if the cache has been constructed
	public boolean isActive() {
		return isActive;
	}
	
	/**
	 * Add a PathObject to the cache, optionally including children.
	 * 
	 * @param pathObject
	 * @param includeChildren
	 * @param limitToClass
	 */
	private void addToCache(PathObject pathObject, boolean includeChildren, Class<? extends PathObject> limitToClass) {
		// If the cache isn't active, we can ignore this... it will be constructed when it is needed
		if (!isActive())
			return;

		if (pathObject.hasROI()) {
			Class<? extends PathObject> cls = pathObject.getClass();
			if (limitToClass == null || cls == limitToClass) {
                SpatialIndex mapObjects = map.computeIfAbsent(cls, k -> createSpatialIndex());
                Envelope envelope = getEnvelope(pathObject);
				mapObjects.insert(envelope, pathObject);
			}
		}
		
		// Add the children
		if (includeChildren && !(pathObject instanceof TemporaryObject) && pathObject.hasChildObjects()) {
			for (PathObject child : pathObject.getChildObjectsAsArray())
				addToCache(child, includeChildren, limitToClass);
		}
	}

	Geometry getGeometry(ROI roi) {
		var geometry = geometryMap.get(roi);
		if (geometry == null)
			return roi.getGeometry();
		else
			return geometry;
	}

	private Coordinate getCentroidCoordinate(ROI roi) {
		// It's faster not to rely on a synchronized map
		return new Coordinate(roi.getCentroidX(), roi.getCentroidY());
	}

	boolean covers(PathObject possibleParent, PathObject possibleChild) {
		var roi = possibleParent.getROI();
		var roiChild = possibleChild.getROI();
		if (roi == null || roi.isEmpty() || roiChild == null || roiChild.isEmpty())
			return false;
		return getRoiRelate(roi).coversWithTolerance(roiChild);
	}

	RoiRelate getRoiRelate(ROI roi) {
		return relateMap.computeIfAbsent(roi, r -> new RoiRelate(r, getGeometry(r)));
	}

	boolean containsCentroid(PathObject possibleParent, PathObject possibleChild) {
		getRoiRelate(possibleParent.getROI());
		return containsCentroid(possibleParent.getROI(), possibleChild.getROI());
	}

	private boolean containsCentroid(ROI roi, ROI roiChild) {
		if (roi == null || roi.isEmpty())
			return false;

		Coordinate centroid = getCentroidCoordinate(roiChild);
		// Use a RoiRelate if we have one, but don't create a new one if we don't
		var relate = relateMap.getOrDefault(roi, null);
		if (relate != null)
			return relate.contains(centroid);
		else
			return SimplePointInAreaLocator.locate(
					centroid, getGeometry(roi)) != Location.EXTERIOR;
	}
	
	
	private SpatialIndex createSpatialIndex() {
		return new Quadtree();
	}
	
	private Envelope getEnvelope(PathObject pathObject) {
		return getEnvelope(pathObject.getROI());
	}
	
	private Envelope getEnvelope(ROI roi) {
		return new Envelope(roi.getBoundsX(), roi.getBoundsX() + roi.getBoundsWidth(),
				roi.getBoundsY(), roi.getBoundsY() + roi.getBoundsHeight());
	}

	private Envelope getEnvelope(ImageRegion region) {
		return new Envelope(region.getMinX(), region.getMaxX(),
				region.getMinY(), region.getMaxY());
	}
	
	
	
	/**
	 * This doesn't acquire the lock! The locking is done first.
	 * 
	 * @param pathObject
	 * @param removeChildren
	 */
	private void removeFromCache(PathObject pathObject, boolean removeChildren) {
		// If the cache isn't active, then nothing to remove
		if (!isActive())
			return;
		
		SpatialIndex mapObjects = map.get(pathObject.getClass());
		
		// We can remove objects from a Quadtree
		if (mapObjects instanceof Quadtree) {
			if (mapObjects.remove(MAX_ENVELOPE, pathObject)) {
				logger.debug("Removed {} from cache", pathObject);
			} else
				logger.debug("Unable to remove {} from cache", pathObject);
			// Remove the children
			if (removeChildren) {
				for (PathObject child : pathObject.getChildObjectsAsArray())
					removeFromCache(child, removeChildren);
			}
		} else if (mapObjects != null && !removeChildren) {
			// We can't remove objects from a STRtree, but since we're just removing one object we can rebuild only the cache for this class
			constructCache(pathObject.getClass());
		} else {
			// If we need to remove multiple objects, better to just rebuild the entire cache
			constructCache(null);
		}
	}

	
	/**
	 * Get all the PathObjects stored in this cache of a specified type and having ROIs with bounds overlapping a specified region.
	 * This does not guarantee that the ROI (which may not be rectangular) overlaps the region...
	 * but a quick test is preferred over a more expensive one.
	 * <p>
	 * Note that pathObjects will be added to the collection provided, if there is one.
	 * The same object will be added to this collection multiple times if it overlaps different tiles -
	 * again in the interests of speed, no check is made.
	 * However this can be addressed by using a Set as the collection.
	 * <p>
	 * If a collection is not provided, another Collection is created & used instead.
	 * 
	 * @param cls a PathObject class, or null if all object types should be returned
	 * @param region an image region, or null if all objects with ROIs should be return
	 * @param pathObjects an (optional) existing collection to which PathObjects should be added
	 * @param includeSubclasses true if subclasses of the specified class should be included
	 * @return
	 */
	public Collection<PathObject> getObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, Collection<PathObject> pathObjects, boolean includeSubclasses) {
		ensureCacheConstructed();
		
		var envelope = region == null ? MAX_ENVELOPE : getEnvelope(region);
		
		int z = region == null ? -1 : region.getZ();
		int t = region == null ? -1 : region.getT();
		r.lock();
		try {
			// Iterate through all the classes, getting objects of the specified class or subclasses thereof
			for (Entry<Class<? extends PathObject>, SpatialIndex> entry : map.entrySet()) {
				if (cls == null || (includeSubclasses && cls.isAssignableFrom(entry.getKey())) || Objects.equals(cls, entry.getKey())) {
					if (entry.getValue() != null) {
						List<PathObject> list = entry.getValue().query(envelope);
						if (list.isEmpty())
							continue;
						
						if (pathObjects == null)
							pathObjects = new HashSet<>();
						
						// Add all objects that have a parent, i.e. might be in the hierarchy
						for (PathObject pathObject : list) {
							var roi = pathObject.getROI();
							if (roi == null || region == null || (roi.getZ() == z && roi.getT() == t)) {
								if (pathObject.getParent() != null || pathObject.isRootObject()) {
									if (envelope.intersects(getEnvelope(pathObject)))
										pathObjects.add(pathObject);
								}
							}
						}
					}
				}
			}
			if (pathObjects == null)
				return Collections.emptySet();
			return pathObjects;
		} finally {
			r.unlock();
		}
	}
	
	public boolean hasObjectsForRegion(Class<? extends PathObject> cls, ImageRegion region, boolean includeSubclasses) {
		ensureCacheConstructed();
		
		var envelope = region == null ? MAX_ENVELOPE : getEnvelope(region);
		
		int z = region == null ? -1 : region.getZ();
		int t = region == null ? -1 : region.getT();
		r.lock();
		try {
			// Iterate through all the classes, getting objects of the specified class or subclasses thereof
			for (Entry<Class<? extends PathObject>, SpatialIndex> entry : map.entrySet()) {
				if (cls == null || Objects.equals(cls, entry.getKey()) || (includeSubclasses && cls.isAssignableFrom(entry.getKey()))) {
					if (entry.getValue() != null) {
						var list = (List<PathObject>)entry.getValue().query(envelope);
						for (var pathObject : list) {
							var roi = pathObject.getROI();
							if (roi == null)
								continue;
							if (region == null)
								return true;
							if (roi.getZ() != z || roi.getT() != t)
								continue;
							if (region.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight())) {
								return true;
							}
						}
					}
				}
			}
			return false;
		} finally {
			r.unlock();
		}
	}


	@Override
	public void hierarchyChanged(final PathObjectHierarchyEvent event) {
		w.lock();
		try {
			boolean singleChange = event.getChangedObjects().size() == 1;
			PathObject singleObject = singleChange ? event.getChangedObjects().getFirst() : null;
			if (singleChange && event.getEventType() == HierarchyEventType.ADDED) {
				removeFromCache(singleObject, false);
				addToCache(singleObject, false, singleObject.getClass());
			} else if (singleChange && event.getEventType() == HierarchyEventType.REMOVED) {
				removeFromCache(singleObject, false);
			} else if (event.getEventType() == HierarchyEventType.OTHER_STRUCTURE_CHANGE || event.getEventType() == HierarchyEventType.CHANGE_OTHER) {
				if (!event.isChanging())
					resetCache();
			}
		} finally {
			w.unlock();
		}
	}
	
}
