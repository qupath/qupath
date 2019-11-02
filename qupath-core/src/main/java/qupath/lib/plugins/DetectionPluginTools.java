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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.plugins.ParallelTileObject.Status;
import qupath.lib.plugins.parameters.ParameterList;
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
	

	/**
	 * Create a task that applies an object detector to a parent object.
	 * <p>
	 * Detected objects will be added as children of the parent. If the parent has a ROI, this may define the detection ROI.
	 * @param <T>
	 * @param task
	 * @param params
	 * @param imageData
	 * @param parentObject
	 * @return
	 */
	public static <T> Runnable createRunnableTask(final ObjectDetector<T> task, final ParameterList params, final ImageData<T> imageData,
			final PathObject parentObject) {
		return new DetectionRunnable<>(task, params, imageData, parentObject);
	}
	

	static class DetectionRunnable<T> implements PathTask {

		final private static Logger logger = LoggerFactory.getLogger(DetectionPluginTools.class);

		ObjectDetector<T> detector;
		private ParameterList params;
		private PathObject parentObject;
		private ROI roi;
		private ImageData<T> imageData;
		private String result;
		private Collection<PathObject> pathObjectsDetected;

		public DetectionRunnable(final ObjectDetector<T> detector, final ParameterList params, final ImageData<T> imageData, final PathObject parentObject) {
			this.detector = detector;
			this.params = params;
			this.parentObject = parentObject;
			this.imageData = imageData;
			this.roi = parentObject != null ? parentObject.getROI() : null;
		}


		/**
		 * Check if the detection can run using the current ROI.
		 * Current purpose is to return false if the ROI is a PointsROI... but may be overridden.
		 * @return
		 */
		protected boolean checkROI() {
			return roi == null || !roi.isPoint();
		}


		@Override
		public void run() {
			try {
				long startTime = System.currentTimeMillis();
				if (parentObject instanceof ParallelTileObject) {
					((ParallelTileObject) parentObject).updateStatus(Status.PROCESSING);
					imageData.getHierarchy().fireObjectClassificationsChangedEvent(this, Collections.singleton(parentObject));
				}
				if (checkROI()) {
					try {
						pathObjectsDetected = detector.runDetection(imageData, params, roi);
					} catch (IOException e) {
						logger.error("Error processing " + roi, e);
					}
					result = detector.getLastResultsDescription();
					long endTime = System.currentTimeMillis();
					if (result != null)
						logger.info(result + String.format(" (processing time: %.2f seconds)", (endTime-startTime)/1000.));
					else
						logger.info(parentObject + String.format(" (processing time: %.2f seconds)", (endTime-startTime)/1000.));
				} else {
					logger.info("Cannot run detection using ROI {}", roi);
				}
			} finally {
				if (parentObject instanceof ParallelTileObject) {
					((ParallelTileObject) parentObject).updateStatus(Status.DONE);
					imageData.getHierarchy().fireObjectClassificationsChangedEvent(this, Collections.singleton(parentObject));
				}				
			}
		}




		private static void tryToSetObjectLock(final PathObject pathObject, final boolean locked) {
			if (pathObject instanceof PathROIObject)
				((PathROIObject)pathObject).setLocked(locked);
		}


		@Override
		public void taskComplete(boolean wasCancelled) {
			if (parentObject == null)
				return;
			
			try {
//				// Tile objects handle their own completion
				if (parentObject instanceof ParallelTileObject) {
					parentObject.clearPathObjects();
					parentObject.addPathObjects(pathObjectsDetected);
					((ParallelTileObject)parentObject).setComplete(wasCancelled);
				} else {
					if (!wasCancelled) {
						parentObject.clearPathObjects();
						if (pathObjectsDetected != null)
							parentObject.addPathObjects(pathObjectsDetected);
						tryToSetObjectLock(parentObject, !pathObjectsDetected.isEmpty());
						imageData.getHierarchy().fireObjectsChangedEvent(this, Collections.singletonList(parentObject));
					}
				}
			} finally {
				pathObjectsDetected = null;
				parentObject = null;
				imageData = null;
				roi = null;
				detector = null;
			}
		}

		@Override
		public String getLastResultsDescription() {
			return result;
		}

	}


}