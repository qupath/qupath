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

package qupath.lib.roi;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.experimental.ShapeSimplifier;
import qupath.lib.roi.interfaces.PathShape;

/**
 * Several useful static methods for working with PathObjects, where AWT is required.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectToolsAwt {
	
	private final static Logger logger = LoggerFactory.getLogger(PathObjectToolsAwt.class);
	

	public static PathObject simplifyShapeAnnotation(PathAnnotationObject pathObject, final double altitudeThreshold) {
		
		PathShape pathROI = (PathShape)pathObject.getROI();
		PathObject pathObjectNew = null;
		if (pathROI instanceof PolygonROI) {
			PolygonROI polygonROI = (PolygonROI)pathROI;
			polygonROI = ShapeSimplifier.simplifyPolygon(polygonROI, altitudeThreshold);
			pathObjectNew = new PathAnnotationObject(polygonROI, pathObject.getPathClass(), pathObject.getMeasurementList());
		} else {
			pathROI = ShapeSimplifierAwt.simplifyShape(pathROI, altitudeThreshold);
			pathObjectNew = new PathAnnotationObject(pathROI, pathObject.getPathClass(), pathObject.getMeasurementList());			
		}
		return pathObjectNew;
	}

	/**
	 * Combine all the annotations that overlap with a selected object.
	 * 
	 * The selected object should itself be an annotation.
	 * 
	 * @param hierarchy
	 * @param pathObjects
	 * @param op
	 */
	public static void combineAnnotations(final PathObjectHierarchy hierarchy, final List<PathObject> pathObjects, PathROIToolsAwt.CombineOp op) {
		if (hierarchy == null || hierarchy.isEmpty() || pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Cannot combine - no annotations found");
			return;
		}
		PathObject pathObject = pathObjects.remove(0);
		if (!(pathObject instanceof PathAnnotationObject) || !(pathObject.getROI() instanceof PathShape)) {
			logger.warn("Combine annotations: No annotation with ROI selected");				
			return;
		}
		PathObjectTools.filterROIs(pathObjects, PathShape.class); // Remove any non-shape ROIs
		if (pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Only one annotation with shape ROIs found");				
			return;
		}
	
		PathShape shapeMask = (PathShape)pathObject.getROI();
		Area areaOriginal = PathROIToolsAwt.getArea(shapeMask);
		Area areaNew = new Area(areaOriginal);
		Iterator<PathObject> iter = pathObjects.iterator();
		List<PathObject> objectsToAdd = new ArrayList<>();
		int changes = 0;
		while (iter.hasNext()) {
			PathObject temp = iter.next();
			Area areaTemp = PathROIToolsAwt.getArea(temp.getROI());
			PathObject annotationNew = null;
			if (op == PathROIToolsAwt.CombineOp.SUBTRACT) {
				areaTemp.subtract(areaNew);
				if (!areaTemp.isEmpty()) {
					PathShape shapeNew = PathROIToolsAwt.getShapeROI(areaTemp, shapeMask.getC(), shapeMask.getZ(), shapeMask.getT());
					annotationNew = new PathAnnotationObject(shapeNew, temp.getPathClass());
				}
			} else if (op == PathROIToolsAwt.CombineOp.INTERSECT) {
				areaTemp.intersect(areaNew);
				if (!areaTemp.isEmpty()) {
					PathShape shapeNew = PathROIToolsAwt.getShapeROI(areaTemp, shapeMask.getC(), shapeMask.getZ(), shapeMask.getT());
					annotationNew = new PathAnnotationObject(shapeNew, temp.getPathClass());
				}
			} else {
				PathROIToolsAwt.combineAreas(areaNew, areaTemp, op);
			}
			if (annotationNew != null) {
				annotationNew.setColorRGB(temp.getColorRGB());
				annotationNew.setName(temp.getName());
				objectsToAdd.add(annotationNew);
			}
			changes++;
		}
		if (changes == 0) {
			logger.debug("No changes were made");
			return;
		}
		if (op == PathROIToolsAwt.CombineOp.ADD) {
			PathShape shapeNew = PathROIToolsAwt.getShapeROI(areaNew, shapeMask.getC(), shapeMask.getZ(), shapeMask.getT());
			if (!shapeNew.isEmpty())
				objectsToAdd.add(new PathAnnotationObject(shapeNew, pathObject.getPathClass()));
		}
		// Remove previous objects
		pathObjects.add(pathObject);
		hierarchy.removeObjects(pathObjects, true);
		if (areaNew.isEmpty()) {
			logger.debug("No area ROI remains");
			return;			
		}
		// Add new objects
		hierarchy.addPathObjects(objectsToAdd, false);
		// TODO: Avoid unnecessary calls to the full hierarchy change
		hierarchy.fireHierarchyChangedEvent(null);
		//		hierarchy.getSelectionModel().setSelectedPathObject(pathObjectNew);
	}

	/**
		 * 
		 * Given an input list of annotations, simplify the ROIs (fewer coordinates) 
		 * using method based on Visvalingam’s Algorithm.
		 * 
		 * See references:
		 * https://hydra.hull.ac.uk/resources/hull:8338
		 * https://www.jasondavies.com/simplify/
		 * http://bost.ocks.org/mike/simplify/
		 * 
		 * @param pathObjects
		 * @param altitudeThreshold
		 * @return
		 */
		public static List<PathObject> simplifyAllShapeAnnotations(Collection<? extends PathObject> pathObjects, final double altitudeThreshold) {
			List<PathObject> simplifiedObjects = new ArrayList<>();
			for (PathObject temp : pathObjects) {
				if (!(temp instanceof PathAnnotationObject)) {
					logger.warn("Cannot simplify {} - not an annotation object", temp);
					simplifiedObjects.add(temp);
				}
				simplifiedObjects.add(simplifyShapeAnnotation((PathAnnotationObject)temp, altitudeThreshold));
			}
			return simplifiedObjects;
		}

}
