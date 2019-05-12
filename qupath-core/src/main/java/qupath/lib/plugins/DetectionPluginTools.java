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

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Helper methods to convert ObjectDetectors into runnable tasks, which take care of resolving 
 * overlaps when using ParallelTileObjects after the detection is complete and firing notification 
 * events in a PathObjectHierarchy.
 * <p>
 * Internally, a PathTask is used with the important resolution/event-firing occurring within the
 * taskComplete method.
 * 
 * 
 * @author Pete Bankhead
 *
 */
public class DetectionPluginTools {
	
	
	public static <T> Runnable createRunnableTask(final ObjectDetector<T> task, final ParameterList params, final ImageData<T> imageData, final PathObject parentObject, final ROI pathROI, final int overlapAmount) {
		return new DetectionRunnable<>(task, params, imageData, parentObject, pathROI, overlapAmount);
	}


	public static <T> Runnable createRunnableTask(final ObjectDetector<T> task, final ParameterList params, final ImageData<T> imageData, final PathObject parentObject) {
		return createRunnableTask(task, params, imageData, parentObject, parentObject.getROI(), 0);
	}
	

	static class DetectionRunnable<T> implements PathTask {

		final private static Logger logger = LoggerFactory.getLogger(DetectionPluginTools.class);

		ObjectDetector<T> detector;
		private ParameterList params;
		private PathObject parentObject;
		private ROI pathROI;
		private ImageData<T> imageData;
		private String result;
		private int overlapAmount;
		private Collection<PathObject> pathObjectsDetected;

		public DetectionRunnable(final ObjectDetector<T> detector, final ParameterList params, final ImageData<T> imageData, final PathObject parentObject, final ROI pathROI, final int overlapAmount) {
			this.detector = detector;
			this.params = params;
			this.parentObject = parentObject;
			this.imageData = imageData;
			this.pathROI = pathROI;
			this.overlapAmount = overlapAmount;
		}


		/**
		 * Check if the detection can run using the current ROI.
		 * Current purpose is to return false if the ROI is a PointsROI... but may be overridden.
		 * @return
		 */
		protected boolean checkROI() {
			return !(pathROI instanceof PointsROI);
		}


		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			if (parentObject instanceof ParallelTileObject) {
				((ParallelTileObject)parentObject).setIsProcessing(true);
				imageData.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(parentObject), true);
			}
			if (checkROI()) {
				try {
					pathObjectsDetected = detector.runDetection(imageData, params, pathROI);
				} catch (IOException e) {
					logger.error("Error processing " + pathROI, e);
				}
				result = detector.getLastResultsDescription();
				long endTime = System.currentTimeMillis();
				if (result != null)
					logger.info(result + String.format(" (processing time: %.2f seconds)", (endTime-startTime)/1000.));
				else
					logger.info(parentObject + String.format(" (processing time: %.2f seconds)", (endTime-startTime)/1000.));
			} else {
				logger.info("Cannot run detection using ROI {}", pathROI);
			}
		}




		private void tryToSetObjectLock(final PathObject pathObject, final boolean locked) {
			if (pathObject instanceof PathROIObject)
				((PathROIObject)pathObject).setLocked(locked);
		}


		@Override
		public void taskComplete() {
			if (parentObject.getROI() == pathROI) {
				if (!Thread.currentThread().isInterrupted()) {
					parentObject.clearPathObjects();
					tryToSetObjectLock(parentObject, false); // Try to unlock the parent
				}
				if (pathObjectsDetected != null) {
					parentObject.addPathObjects(pathObjectsDetected);
					tryToSetObjectLock(parentObject, true); // Try to lock the parent
				}
				if (parentObject instanceof ParallelTileObject)
					((ParallelTileObject)parentObject).setComplete();
			} else if (!parentObject.hasChildren() || overlapAmount <= 0) {
				parentObject.addPathObjects(pathObjectsDetected);
				tryToSetObjectLock(parentObject, true); // Try to lock the parent
			} else if (pathObjectsDetected != null && !pathObjectsDetected.isEmpty()) {
				long startTime2 = System.currentTimeMillis();
				// Prepare to resolve any overlaps
				Class<? extends PathObject> cls = pathObjectsDetected.iterator().next().getClass();

				Rectangle bounds = AwtTools.getBounds(pathROI);
				// Get the region definitely not overlapping
				Rectangle boundsInternal = new Rectangle(bounds.x+overlapAmount, bounds.y+overlapAmount, bounds.width-overlapAmount*2, bounds.height-overlapAmount*2);

				Collection<PathObject> overlapObjects = imageData.getHierarchy().getObjectsForRegion(cls, ImageRegion.createInstance(pathROI), null);
				if (!overlapObjects.isEmpty()) {

					// Generate a composite area to test potential overlaps
					Shape pathOldComposite = new Path2D.Float();

					Map<Area, PathObject> mapOld = new HashMap<>();
					for (PathObject tempObject : overlapObjects) {
						if (tempObject.getROI() instanceof PathArea) {
							Shape shapeTemp = PathROIToolsAwt.getShape(tempObject.getROI());
							Area areaTemp = new Area(shapeTemp);
							mapOld.put(areaTemp, tempObject);
							((Path2D)pathOldComposite).append(shapeTemp, false);
						}
					}
					//						pathOldComposite = new Area(pathOldComposite);

					int nOverlaps = mapOld.size();
					int detectedCounter = pathObjectsDetected.size();
					int counter = 0;
					int skipCounter = 0;

					Iterator<PathObject> iterNew = pathObjectsDetected.iterator();
					while (iterNew.hasNext() && !mapOld.isEmpty()) {
						PathObject pathObjectNew = iterNew.next();
						PathArea pathAreaNew = pathObjectNew.getROI() instanceof PathArea ? (PathArea)pathObjectNew.getROI() : null;
						if (pathAreaNew == null)
							continue;
						Rectangle2D boundsNew = AwtTools.getBounds2D(pathAreaNew);

						// Check if we are definitely inside, then don't need to check further
						if (boundsInternal.contains(boundsNew) || !pathOldComposite.intersects(boundsNew)) {
							skipCounter++;
							continue;
						}
						// Remove if the detection falls on a tile boundary - it may be clipped
						if (boundsNew.getMinX() <= bounds.getMinX() || boundsNew.getMinY() <= bounds.getMinY() || boundsNew.getMaxY() >= bounds.getMaxY() || boundsNew.getMaxX() >= bounds.getMaxX()) {
							iterNew.remove();
							skipCounter++;
							continue;
						}

						Area areaNew = null;
						Iterator<Entry<Area, PathObject>> iterMap = mapOld.entrySet().iterator();
						while (iterMap.hasNext()) {
							counter++;
							Entry<Area, PathObject> entryOld = iterMap.next();
							// Check if the existing area intersects the bounds
							Area areaOld = entryOld.getKey();
							if (!areaOld.intersects(boundsNew))
								continue;
							// If the bounds are intersected, check for an actual intersection between the areas
							Area temp = new Area(areaOld);
							if (areaNew == null)
								areaNew = PathROIToolsAwt.getArea(pathAreaNew);
							temp.intersect(areaNew);
							if (temp.isEmpty())
								continue;
							// We have an intersection, but it may be minimal... check this
							double intersectionArea = ROIs.createAreaROI(temp, ImagePlane.getDefaultPlane()).getArea();
							double threshold = 0.1;
							// We do have an intersection - keep the object with the larger area if the intersection is a 'reasonable' proportion of the smaller area
							// Here, reasonable is defined as 10%
							PathArea pathAreaOld = (PathArea)entryOld.getValue().getROI();
							if (pathAreaNew.getArea() > pathAreaOld.getArea()) {
								if (intersectionArea < pathAreaOld.getArea() * threshold)
									continue;
								parentObject.removePathObject(entryOld.getValue());
								iterMap.remove();
								//									System.out.println("Removing from old");
							}
							else {
								if (intersectionArea < pathAreaNew.getArea() * threshold)
									continue;
								iterNew.remove();
								//									System.out.println("Removing from new");
								break;
							}
						}
					}

					long endTime2 = System.currentTimeMillis();
					logger.info(String.format("Resolved %d possible overlaps with %d iterations (tested %d of %d): %.2f seconds", nOverlaps, counter, detectedCounter-skipCounter, detectedCounter, (endTime2 - startTime2) / 1000.));

				}


				parentObject.addPathObjects(pathObjectsDetected);
				tryToSetObjectLock(parentObject, parentObject.hasChildren()); // Update the log depending on child status
			}

			//			if (previousChildren != null && !previousChildren.isEmpty())
			//				imageData.getHierarchy().addPathObjects(previousChildren, false);

			// TODO: Note that this can block for an annoying amount of time due to hierarchy lock & repaints etc.
			//				if (!(parentObject instanceof ParallelTileObject))
			imageData.getHierarchy().fireHierarchyChangedEvent(parentObject);

			//			}

			//			if (!Thread.currentThread().isInterrupted() && pathObjectsDetected != null) {
			//				if (parentObject.getROI() == pathROI)
			//					parentObject.clearPathObjects();
			//				parentObject.addPathObjects(pathObjectsDetected);
			//				imageData.getHierarchy().fireHierarchyChangedEvent(parentObject);
			//			}
			pathObjectsDetected = null;
			parentObject = null;
			imageData = null;
		}

		@Override
		public String getLastResultsDescription() {
			return result;
		}

	}


}