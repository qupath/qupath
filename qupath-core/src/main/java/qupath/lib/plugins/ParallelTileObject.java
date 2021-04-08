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

package qupath.lib.plugins;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TemporaryObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractTileableDetectionPlugin.ParallelDetectionTileManager;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * A temporary object, used during parallel processing to represent a tile currently being used for object
 * detection.
 * <p>
 * ParallelTileObjects maintain knowledge of adjacent tiles, and are thereby able to resolve overlaps
 * after detection is complete (here, based on keeping the object with the larger area).
 * 
 * @author Pete Bankhead
 *
 */
public class ParallelTileObject extends PathTileObject implements TemporaryObject {

	final private static Logger logger = LoggerFactory.getLogger(ParallelTileObject.class);
	
	/**
	 * Current processing status for the tile.
	 */
	public static enum Status {
		/**
		 * Tile waiting to be processed
		 */
		PENDING,
		/**
		 * Tile currently being processed
		 */
		PROCESSING,
		/**
		 * Tile processing complete
		 */
		DONE }
	
	private static PathClass pathClassPending = PathClassFactory.getPathClass(
			"Tile-Pending", ColorTools.packRGB(50, 50, 200));

	private static PathClass pathClassProcessing = PathClassFactory.getPathClass(
			"Tile-Processing", ColorTools.packRGB(50, 200, 50));

	private static PathClass pathClassDone = PathClassFactory.getPathClass(
			"Tile-Done", ColorTools.packRGB(100, 20, 20));

	private ParallelDetectionTileManager manager;
	
	AtomicInteger countdown;
	Rectangle2D bounds;
	PathObjectHierarchy hierarchy;
	Map<ParallelTileObject, Rectangle2D> map = new TreeMap<>(DefaultPathObjectComparator.getInstance());

	private Status status = Status.PENDING;
	
	ParallelTileObject(final ParallelDetectionTileManager manager, final ROI pathROI, final PathObjectHierarchy hierarchy, final AtomicInteger countdown) {
		super(pathROI);
		this.manager = manager;
		setPathClass(pathClassPending);
		this.bounds = getBounds2D(pathROI);
		this.hierarchy = hierarchy;
		this.countdown = countdown;
		setColorRGB(ColorTools.packRGB(128, 128, 128));
	}


	/**
	 * Register a neighboring tile, if it intersects with the bounds of this one
	 * @param pto
	 * @return 
	 */
	public synchronized boolean suggestNeighbor(final ParallelTileObject pto) {
		if (bounds.intersects(pto.bounds)) {
			Rectangle2D intersection = new Rectangle2D.Double();
			Rectangle2D.intersect(bounds, pto.bounds, intersection);
			map.put(pto, intersection);
			return true;
		}
		return false;
	}

	/**
	 * Notify the object if it is currently being processed.
	 * 
	 * This is used to update how it is displayed (here implemented using a classification).
	 * 
	 * @param status
	 */
	public synchronized void updateStatus(Status status) {
		Objects.nonNull(status);
		this.status = status;
		switch(status) {
		case DONE:
			setPathClass(pathClassDone);
			break;
		case PROCESSING:
			setPathClass(pathClassProcessing);
			break;
		case PENDING:
		default:
			setPathClass(pathClassPending);
			break;
		}
	}
	
	/**
	 * Get the current status (pending, processing or done).
	 * @return
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Returns true if setIsProcessing(true) has recently been called.
	 * 
	 * @return
	 */
	public synchronized boolean isProcessing() {
		return status == Status.PROCESSING;
	}

	/**
	 * Returns true if setComplete() has been called.
	 * 
	 * @return
	 */
	public synchronized boolean isComplete() {
		return status == Status.DONE;
	}

	/**
	 * Notify the tile that its processing is done.
	 * 
	 * This both changes its display color, and triggers a check to see if overlaps with
	 * detections made in adjacent tiles can be resolved.
	 * @param wasCancelled 
	 */
	public synchronized void setComplete(boolean wasCancelled) {
		// Flag that the processing is complete
		updateStatus(Status.DONE);
		manager.tileComplete(this, wasCancelled);
	}
	
	
	/**
	 * Request that the tile object attempts to resolve overlaps with its neighboring tiles.
	 */
	public synchronized void resolveOverlaps() {
//		// If we don't have any children, notify that the test is complete
	//			if (!hasChildren()) {
	//				for (ParallelTileObject pto : map.keySet())
	//					pto.notifyTestComplete(this);
	//				map.clear();
	//				checkAllTestsComplete();
	//				return;
	//			}

	long startTime = System.currentTimeMillis();
	int nRemoved = 0;
	
	boolean preferNucleus = false;

	// If we do have children, loop through & perform tests
	Iterator<Entry<ParallelTileObject, Rectangle2D>> iterMap = map.entrySet().iterator();
	while (iterMap.hasNext()) {
		Entry<ParallelTileObject, Rectangle2D> entry = iterMap.next();

		// If the parallel tile object hasn't been processed yet, then just continue - nothing to compare
		ParallelTileObject pto = entry.getKey();
		if (!pto.isComplete())
			continue;
		
		ParallelTileObject first, second;
		
		// Choose a consistent order for the comparison
		if (this.getROI().getBoundsX() > pto.getROI().getBoundsX() || 
				this.getROI().getBoundsY() > pto.getROI().getBoundsY()) {
			first = this;
			second = pto;
		} else {
			first = pto;
			second = this;
		}
//		ROI firstBounds = first.getROI();
//		ROI secondBounds = second.getROI();

		// Compare this object's lists with that object's list
		List<PathObject> listFirst = first.getObjectsForRegion(entry.getValue());
		List<PathObject> listSecond = second.getObjectsForRegion(entry.getValue());

		// Only need to compare potential overlaps if both lists are non-empty
		if (!listFirst.isEmpty() && !listSecond.isEmpty()) {

			Map<ROI, Geometry> cache = new HashMap<>();

			Iterator<PathObject> iterFirst = listFirst.iterator();
			while (iterFirst.hasNext()) {
				PathObject firstObject = iterFirst.next();
				ROI firstROI = PathObjectTools.getROI(firstObject, preferNucleus);
				ImageRegion firstRegion = ImageRegion.createInstance(firstROI);
				Geometry firstGeometry = null;
				double firstArea = Double.NaN;

				Iterator<PathObject> iterSecond = listSecond.iterator();
				while (iterSecond.hasNext()) {
					PathObject secondObject = iterSecond.next();
					ROI secondROI = PathObjectTools.getROI(secondObject, preferNucleus);
//					
					// Do quick overlap test
					if (!firstRegion.intersects(
							secondROI.getBoundsX(), secondROI.getBoundsY(),
							secondROI.getBoundsWidth(), secondROI.getBoundsHeight()))
						continue;
					
					// Get geometries
					if (firstGeometry == null) {
						firstGeometry = firstROI.getGeometry();
						firstArea = firstGeometry.getArea();
					}
					Geometry secondGeometry = cache.get(secondROI);
					if (secondGeometry == null) {
						secondGeometry = secondROI.getGeometry();
						cache.put(secondROI, secondGeometry);
					}

					Geometry intersection;
					try {
						// Get the intersection
						if (!firstGeometry.intersects(secondGeometry))
							continue;

						intersection = firstGeometry.intersection(secondGeometry);
					} catch (Exception e) {
						logger.warn("Error resolving overlaps: {}", e.getLocalizedMessage());
						logger.debug(e.getLocalizedMessage(), e);
						continue;
					}
					if (intersection.isEmpty())
						continue;
					
					// Check areas
					double intersectionArea = intersection.getArea();
					double secondArea = secondGeometry.getArea();
					double threshold = 0.1;
					if (firstArea >= secondArea) {
						if (intersectionArea / secondArea > threshold) {
							second.removePathObject(secondObject);
							nRemoved++;
						}
					} else {
						if (intersectionArea / firstArea > threshold) {
							first.removePathObject(firstObject);
							nRemoved++;
							break;
						}
					}
				}
			}

		}

		// Remove the neighbor from the map
		iterMap.remove();

		pto.notifyTestComplete(this);

	}

	checkAllTestsComplete();

	long endTime = System.currentTimeMillis();
	logger.debug(String.format("Resolved %d overlaps: %.2f seconds", nRemoved, (endTime - startTime) / 1000.));
	//			logger.info(String.format("Resolved %d possible overlaps with %d iterations (tested %d of %d): %.2f seconds", nOverlaps, counter, detectedCounter-skipCounter, detectedCounter, (endTime2 - startTime2) / 1000.));
	}


	/**
	 * Get all the objects whose bounding box intersects with a specified region
	 * @param region
	 * @return
	 */
	List<PathObject> getObjectsForRegion(Rectangle2D region) {
		List<PathObject> pathObjects = new ArrayList<>();
		for (PathObject child : getChildObjectsAsArray()) {
			ROI childROI = child.getROI();
			if (childROI != null && childROI.isArea() && region.intersects(getBounds2D(childROI))) {
				pathObjects.add(child);
			}
		}
		Collections.sort(pathObjects, DefaultPathObjectComparator.getInstance());
		return pathObjects;
	}



	boolean checkAllTestsComplete() {
//		return true;
		if (map.isEmpty() && getParent() != null) {
			if (countdown == null) {
				hierarchy.removeObject(this, true);
			} else if (countdown.decrementAndGet() == 0) {
				PathObject parent = getParent();
				List<PathObject> parallelObjects = new ArrayList<>();
				for (PathObject temp : parent.getChildObjectsAsArray()) {
					if (temp instanceof ParallelTileObject) {
						parallelObjects.add(temp);
					}
				}
				parent.removePathObjects(parallelObjects);
				for (PathObject temp : parallelObjects)
					parent.addPathObjects(temp.getChildObjects());

				if (parent.hasChildren() && parent instanceof PathROIObject)
					((PathROIObject)parent).setLocked(true);

				hierarchy.fireHierarchyChangedEvent(parent);
			}
			return true;
			//				getParent().removePathObject(this);
			//				getParent().addPathObjects(getPathObjectList());
		}
		return false;
	}


	void notifyTestComplete(final ParallelTileObject pto) {
		if (isComplete()) {
			map.remove(pto);
			checkAllTestsComplete();
		}
	}


	private static Rectangle2D getBounds2D(final ROI pathROI) {
		return new Rectangle2D.Double(pathROI.getBoundsX(), pathROI.getBoundsY(), pathROI.getBoundsWidth(), pathROI.getBoundsHeight());
	}


}