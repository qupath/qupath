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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
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
	
	/**
	 * Largest positive envelope, used when all objects are requested.
	 */
	private final static Envelope MAX_ENVELOPE = new Envelope(-Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE);
	
	/**
	 * Keep a map of envelopes per ROI; ROIs should be immutable.
	 */
	private Map<ROI, Envelope> envelopeMap = new WeakHashMap<>();
	
	/**
	 * Keep a map of envelopes per object, because potentially an object might have its ROI replaced behind our back...
	 */
	private Map<PathObject, Envelope> lastEnvelopeMap = new WeakHashMap<>();
	
	/**
	 * Store a spatial index according to the class of PathObject.
	 */
	private Map<Class<? extends PathObject>, SpatialIndex> map = new HashMap<>();
	
	/**
	 * Map to cache Geometries, specifically for annotations.
	 */
	final private static Map<ROI, Geometry> geometryMap = Collections.synchronizedMap(new WeakHashMap<>());
	final private static Map<ROI, PointOnGeometryLocator> locatorMap = Collections.synchronizedMap(new WeakHashMap<>());
//	final private static Map<ROI, Coordinate> centroidMap = Collections.synchronizedMap(new WeakHashMap<>());

	private PathObjectHierarchy hierarchy;
	private boolean isActive = false;
	
	private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
	
	
	public PathObjectTileCache(PathObjectHierarchy hierarchy) {
		this.hierarchy = hierarchy;
		if (hierarchy != null)
			hierarchy.addPathObjectListener(this);
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
			logger.debug("Cache reconstructed in " + (endTime - startTime)/1000.);
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
	 */
	private void addToCache(PathObject pathObject, boolean includeChildren, Class<? extends PathObject> limitToClass) {
		// If the cache isn't active, we can ignore this... it will be constructed when it is needed
		if (!isActive())
			return;

		if (pathObject.hasROI()) {
			Class<? extends PathObject> cls = pathObject.getClass();
			if (limitToClass == null || cls == limitToClass) {
				SpatialIndex mapObjects = map.get(cls);
				if (mapObjects == null) {
					mapObjects = createSpatialIndex();
					map.put(cls, mapObjects);
				}
				Envelope envelope = getEnvelope(pathObject);
				mapObjects.insert(envelope, pathObject);
			}
		}
		
		// Add the children
		if (includeChildren && !(pathObject instanceof TemporaryObject) && pathObject.hasChildren()) {
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
	
	Geometry getGeometry(PathObject pathObject) {
		ROI roi = pathObject.getROI();
		Geometry geometry = geometryMap.get(roi);
		if (geometry == null) {
			geometry = roi.getGeometry();
			if (pathObject.isAnnotation() || pathObject.isTMACore()) {
				geometryMap.put(roi, geometry);
			}
//			long startTime = System.currentTimeMillis();
			if (!geometry.isValid()) {
				int nVertices = geometry.getNumPoints();
				if (geometry.getNumPoints() < 100)
					logger.warn("{} is not a valid geometry! Actual geometry {}", pathObject, geometry);
				else
					logger.warn("{} is not a valid geometry! Actual geometry {} ({} vertices)", pathObject, geometry.getGeometryType(), nVertices);
			}
//			long endTime = System.currentTimeMillis();
//			System.err.println("Testing " + (endTime - startTime) + " ms for " + geometry);
		}
		return geometry;
	}
	
	private Coordinate getCentroidCoordinate(PathObject pathObject) {
		ROI roi = PathObjectTools.getROI(pathObject, true);
		// It's faster not to rely on a synchronized map
		return new Coordinate(roi.getCentroidX(), roi.getCentroidY());
//		Coordinate coordinate = centroidMap.get(roi);
//		if (coordinate == null) {
//			coordinate = new Coordinate(roi.getCentroidX(), roi.getCentroidY());
////			coordinate = getGeometry(pathObject).getCentroid().getCoordinate();
//			centroidMap.put(roi, coordinate);
//		}
//		return coordinate;
	}
	
	PointOnGeometryLocator getLocator(ROI roi, boolean addToCache) {
		var locator = locatorMap.get(roi);
		if (locator == null) {
			var geometry = getGeometry(roi);
			if (geometry instanceof Polygonal || geometry instanceof LinearRing)
				locator = new IndexedPointInAreaLocator(geometry);
			else
				locator = new SimplePointInAreaLocator(geometry);
			locatorMap.put(roi, locator);
		}
		return locator;
	}
	
//	public boolean covers(PathObject possibleParent, PathObject possibleChild) {
//		return getGeometry(possibleParent).covers(getGeometry(possibleChild));
//	}
	
	private Map<Geometry, PreparedGeometry> preparedGeometryMap = new WeakHashMap<>();
	
	PreparedGeometry getPreparedGeometry(Geometry geometry) {
		var prepared = preparedGeometryMap.get(geometry);
		if (prepared != null)
			return prepared;
		
		synchronized (preparedGeometryMap) {
			prepared = preparedGeometryMap.get(geometry);
			if (prepared != null)
				return prepared;
			prepared = PreparedGeometryFactory.prepare(geometry);
			preparedGeometryMap.put(geometry, prepared);
			return prepared;
		}
	}
	
	boolean covers(PathObject possibleParent, PathObject possibleChild) {
		var parent = getPreparedGeometry(getGeometry(possibleParent));
		var child = getGeometry(possibleChild);
		return parent.covers(child);
	}
	
	boolean covers(PreparedGeometry parent, PathObject possibleChild) {
		var child = getGeometry(possibleChild);
		return parent.covers(child);
	}
	
	boolean containsCentroid(PathObject possibleParent, PathObject possibleChild) {
		Coordinate centroid = getCentroidCoordinate(possibleChild);
		if (centroid == null)
			return false;
		if (possibleParent.isDetection())
			return SimplePointInAreaLocator.locate(
					centroid, getGeometry(possibleParent)) != Location.EXTERIOR;
		return getLocator(possibleParent.getROI(), true).locate(centroid) != Location.EXTERIOR;
	}
	
	boolean containsCentroid(PointOnGeometryLocator locator, PathObject possibleChild) {
		Coordinate centroid = getCentroidCoordinate(possibleChild);
		if (centroid == null)
			return false;
		return locator.locate(centroid) != Location.EXTERIOR;
	}
	
	boolean containsCentroid(Geometry possibleParent, PathObject possibleChild) {
		Coordinate centroid = getCentroidCoordinate(possibleChild);
		if (centroid == null)
			return false;
		return SimplePointInAreaLocator.locate(centroid, possibleParent) != Location.EXTERIOR;
	}
	
	
	private SpatialIndex createSpatialIndex() {
		return new Quadtree();
//		return new STRtree();
	}
	
	private Envelope getEnvelope(PathObject pathObject) {
		var envelope = getEnvelope(pathObject.getROI());
		lastEnvelopeMap.put(pathObject, envelope);
		return envelope;
	}
	
	private Envelope getEnvelope(ROI roi) {
		var envelope = envelopeMap.get(roi);
		if (envelope == null) {
			envelope = new Envelope(roi.getBoundsX(), roi.getBoundsX() + roi.getBoundsWidth(),
					roi.getBoundsY(), roi.getBoundsY() + roi.getBoundsHeight());
			envelopeMap.put(roi, envelope);
		}
		return envelope;
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
			Envelope envelope = lastEnvelopeMap.get(pathObject);
			envelope = MAX_ENVELOPE;
//				System.err.println("Before: " + mapObjects.query(MAX_ENVELOPE).size());
			if (envelope != null) {
				if (mapObjects.remove(envelope, pathObject)) {
					logger.debug("Removed {} from cache", pathObject);
				} else
					logger.debug("Unable to remove {} from cache", pathObject);
			} else {
				logger.debug("No envelope found for {}", pathObject);					
			}
//				System.err.println("After: " + mapObjects.query(MAX_ENVELOPE).size());
			// Remove the children
			if (removeChildren) {
				for (PathObject child : pathObject.getChildObjectsAsArray())
					removeFromCache(child, removeChildren);
			}
		} else if (mapObjects instanceof SpatialIndex && !removeChildren) {
			// We can't remove objects from a STRtree, but since we're just removing one object we can rebuild only the cache for this class
			constructCache(pathObject.getClass());
		} else {
			// If we need to remove multiple objects, better to just rebuild the entire cache
			constructCache(null);
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
				if (cls == null || (includeSubclasses && cls.isAssignableFrom(entry.getKey())) || cls.isInstance(entry.getKey())) {
					if (entry.getValue() != null) {
						var list = entry.getValue().query(envelope);
						if (pathObjects == null)
							pathObjects = new HashSet<PathObject>();
						
						// Add all objects that have a parent, i.e. might be in the hierarchy
						for (PathObject pathObject : (List<PathObject>)list) {
							var roi = pathObject.getROI();
							if (roi == null || region == null || (roi.getZ() == z && roi.getT() == t)) {
								if (pathObject.getParent() != null || pathObject.isRootObject()) {
									if (envelope.intersects(getEnvelope(pathObject)))
										pathObjects.add(pathObject);
								}
							}
						}
					}
//						pathObjects = entry.getValue().getObjectsForRegion(region, pathObjects);
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
		
		var envelope = region == null ? MAX_ENVELOPE : getEnvelope(region);
		
		int z = region == null ? -1 : region.getZ();
		int t = region == null ? -1 : region.getT();
		r.lock();
		try {
			// Iterate through all the classes, getting objects of the specified class or subclasses thereof
			for (Entry<Class<? extends PathObject>, SpatialIndex> entry : map.entrySet()) {
				if (cls == null || cls.isInstance(entry.getKey()) || (includeSubclasses && cls.isAssignableFrom(entry.getKey()))) {
					if (entry.getValue() != null) {
						var list = (List<PathObject>)entry.getValue().query(envelope);
						if (list.stream().anyMatch(p -> p.hasROI() && 
								(region == null || (p.getROI().getZ() == z && p.getROI().getT() == t))))
							return true;
//						if (entry.getValue().hasObjectsForRegion(region))
//							return true;
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
		w.lock();
		try {
			boolean singleChange = event.getChangedObjects().size() == 1;
			PathObject singleObject = singleChange ? event.getChangedObjects().get(0) : null;
			if (singleChange && event.getEventType() == HierarchyEventType.ADDED) {
				removeFromCache(singleObject, false);
				addToCache(singleObject, false, singleObject.getClass());
			} else if (singleChange && event.getEventType() == HierarchyEventType.REMOVED) {
				removeFromCache(singleObject, false);
			} else if (event.getEventType() == HierarchyEventType.OTHER_STRUCTURE_CHANGE || event.getEventType() == HierarchyEventType.CHANGE_OTHER) {
//				if (singleChange && !singleObject.isRootObject()) {
//					removeFromCache(singleObject, false);
//					addToCache(singleObject, false, singleObject.getClass());					
//				} else
//				System.err.println(event);
				if (!event.isChanging())
					resetCache();
			}
		} finally {
			w.unlock();
		}
//		else if (event.getEventType() == HierarchyEventType.OBJECT_CHANGE)
//			resetCache(); // TODO: Check if full change is necessary for object change events			
	}
	
}
