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

package qupath.lib.gui.commands.scriptable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

/**
 * Command to convert detection objects into Point objects, where each point 
 * is created based upon the centroid of each detection.
 * 
 * Note: This command is not scriptable.
 * 
 * @author Pete Bankhead
 *
 */
public class DetectionsToPointsCommand implements PathCommand {
	
	private ImageDataWrapper<?> manager;
	
	public DetectionsToPointsCommand(final ImageDataWrapper<?> manager) {
		super();
		this.manager = manager;
	}

	@Override
	public void run() {
		ImageData<?> imageData = manager.getImageData();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
		if (pathObjects.isEmpty()) {
			DisplayHelpers.showErrorMessage("Detections to points", "No detections found!");
			return;
		}
		
		// Remove any detections that don't have a ROI - can't do much with them
		Iterator<PathObject> iter = pathObjects.iterator();
		while (iter.hasNext()) {
			if (!iter.next().hasROI())
				iter.remove();
		}
		
		// If there is more than one object, seek assurance this is really wanted
		if (pathObjects.size() > 1) {
			if (!DisplayHelpers.showYesNoDialog("Detections to points", String.format("Convert %d detections to points?\nThis cannot be undone.", pathObjects.size())))
				return;
		}
		
		// Create Points lists for each class
		HashMap<PathClass, List<Point2>> pointsMap = new HashMap<>();
		for (PathObject pathObject : pathObjects) {
			PathClass pathClass = pathObject.getPathClass();
			
			List<Point2> points = pointsMap.get(pathClass);
			if (points == null) {
				points = new ArrayList<>();
				pointsMap.put(pathClass, points);
			}
			points.add(new Point2(pathObject.getROI().getCentroidX(), pathObject.getROI().getCentroidY()));
		}
		
		
//		HashMap<PathClass, PointsROI> pointsMap = new HashMap<>();
//		for (PathObject pathObject : pathObjects) {
//			PathClass pathClass = pathObject.getPathClass();
//			
//			PointsROI points = pointsMap.get(pathClass);
//			if (points == null) {
//				points = new PointsROI();
//				pointsMap.put(pathClass, points);
//				PathObject pointObject = new PathAnnotationObject(points);
//				pointObject.setPathClass(pathClass);
//				hierarchy.addPathObject(pointObject, false);
//			}
//			points.addPoint(pathObject.getROI().getCentroidX(), pathObject.getROI().getCentroidY());
//		}
		
		
		// Remove the detection objects
		hierarchy.removeObjects(pathObjects, true);
		
		// Create & add annotation objects to hierarchy
		for (Entry<PathClass, List<Point2>> entry : pointsMap.entrySet()) {
			PathObject pointObject = PathObjects.createAnnotationObject(ROIs.createPointsROI(entry.getValue(), ImagePlane.getDefaultPlane()));
			pointObject.setPathClass(entry.getKey());
			hierarchy.addPathObject(pointObject);			
		}
		
//		hierarchy.fireChangeEvent(hierarchy.getRootObject());
//		viewer.repaint();
	}
	
	

}
