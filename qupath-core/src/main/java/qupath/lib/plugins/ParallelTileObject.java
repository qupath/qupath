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

package qupath.lib.plugins;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TemporaryObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ROIs;
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

	AtomicInteger countdown;
	boolean isProcessing = false;
	boolean isComplete = false;
	Rectangle2D bounds;
	PathObjectHierarchy hierarchy;
	Map<ParallelTileObject, Rectangle2D> map = new HashMap<>();

	ParallelTileObject(final ROI pathROI, final PathObjectHierarchy hierarchy, final AtomicInteger countdown) {
		super(pathROI);
		this.bounds = getBounds2D(pathROI);
		this.hierarchy = hierarchy;
		this.countdown = countdown;
		setColorRGB(ColorTools.makeRGB(128, 128, 128));
	}


	/**
	 * Register a neighboring tile, if it intersects with the bounds of this one
	 * @param pto
	 */
	public boolean suggestNeighbor(final ParallelTileObject pto) {
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
	 * This is used to update its color.
	 * 
	 * @param isProcessing
	 */
	public void setIsProcessing(boolean isProcessing) {
		this.isProcessing = isProcessing;
		if (this.isProcessing)
			setColorRGB(ColorTools.makeRGB(255, 255, 0));
		else
			setColorRGB(ColorTools.makeRGB(128, 128, 128));
	}

	/**
	 * Returns true if setIsProcessing(true) has recently been called.
	 * 
	 * @return
	 */
	public boolean isProcessing() {
		return isProcessing;
	}

	/**
	 * Returns true if setComplete() has been called.
	 * 
	 * @return
	 */
	public boolean isComplete() {
		return isComplete;
	}

	/**
	 * Notify the tile that its processing is done.
	 * 
	 * This both changes its display color, and triggers a check to see if overlaps with
	 * detections made in adjacent tiles can be resolved.
	 */
	public void setComplete() {
		// Flag that the processing is complete
		isComplete = true;
		isProcessing = false;
		setColorRGB(ColorTools.makeRGB(255, 0, 0));

		//			// If we don't have any children, notify that the test is complete
		//			if (!hasChildren()) {
		//				for (ParallelTileObject pto : map.keySet())
		//					pto.notifyTestComplete(this);
		//				map.clear();
		//				checkAllTestsComplete();
		//				return;
		//			}

		long startTime = System.currentTimeMillis();
		int nRemoved = 0;

		// If we do have children, loop through & perform tests
		Iterator<Entry<ParallelTileObject, Rectangle2D>> iterMap = map.entrySet().iterator();
		while (iterMap.hasNext()) {
			Entry<ParallelTileObject, Rectangle2D> entry = iterMap.next();

			// If the parallel tile object hasn't been processed yet, then just continue - nothing to compare
			ParallelTileObject pto = entry.getKey();
			if (!pto.isComplete())
				continue;

			// Compare this object's lists with that object's list
			List<PathObject> listThis = getObjectsForRegion(entry.getValue());
			List<PathObject> listThat = pto.getObjectsForRegion(entry.getValue());

			// Only need to compare potential overlaps if both lists are non-empty
			if (!listThis.isEmpty() && !listThat.isEmpty()) {

				Iterator<PathObject> iterThis = listThis.iterator();
				while (iterThis.hasNext()) {
					PathObject pathObjectNew = iterThis.next();
					ROI pathAreaNew = pathObjectNew.getROI();
					Area areaNew = RoiTools.getArea(pathAreaNew);

					Iterator<PathObject> iterThat = listThat.iterator();
					while (iterThat.hasNext()) {
						PathObject pathObjectOld = iterThat.next();
						ROI pathAreaOld = pathObjectOld.getROI();
						// Check if the existing area intersects the bounds
						if (!areaNew.intersects(getBounds2D(pathAreaOld)))
							continue;
						// If the bounds are intersected, check for an actual intersection between the areas
						Area temp = RoiTools.getArea(pathAreaOld);
						temp.intersect(areaNew);
						if (temp.isEmpty())
							continue;
						// We have an intersection, but it may be minimal... check this
						double intersectionArea = ROIs.createAreaROI(temp, ImagePlane.getDefaultPlane()).getArea();
						double threshold = 0.1;
						// We do have an intersection - keep the object with the larger area if the intersection is a 'reasonable' proportion of the smaller area
						// Here, reasonable is defined as 10%
						if (pathAreaNew.getArea() > pathAreaOld.getArea()) {
							if (intersectionArea < pathAreaOld.getArea() * threshold)
								continue;
							pto.removePathObject(pathObjectOld);
							//								iterThat.remove();
							//								logger.info("Removing from old");
							nRemoved++;
						}
						else {
							if (intersectionArea < pathAreaNew.getArea() * threshold)
								continue;
							removePathObject(pathObjectNew);
							//								iterThis.remove();
							//								logger.info("Removing from new");
							nRemoved++;
							break;
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
		return pathObjects;
	}



	boolean checkAllTestsComplete() {
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