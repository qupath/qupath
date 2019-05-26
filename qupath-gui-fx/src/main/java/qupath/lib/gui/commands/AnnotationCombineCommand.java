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

package qupath.lib.gui.commands;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.PathShape;

/**
 * Command to combine multiple annotations by merging their ROIs.
 * 
 * @author Pete Bankhead
 *
 */
public class AnnotationCombineCommand implements PathCommand {
	
	private static Logger logger = LoggerFactory.getLogger(AnnotationCombineCommand.class);

	final private QuPathViewer viewer;
	final private RoiTools.CombineOp op;

	public AnnotationCombineCommand(final QuPathViewer viewer, final RoiTools.CombineOp op) {
		super();
		this.viewer = viewer;
		this.op = op;
	}

	@Override
	public void run() {
		combineAnnotations(viewer.getHierarchy(), new ArrayList<>(viewer.getHierarchy().getSelectionModel().getSelectedObjects()), op);
	}
	
	
	/**
	 * Combine all the annotations that overlap with a selected object.
	 * <p>
	 * The selected object should itself be an annotation.
	 * 
	 * @param hierarchy
	 * @param pathObjects
	 * @param op
	 */
	static void combineAnnotations(final PathObjectHierarchy hierarchy, final List<PathObject> pathObjects, RoiTools.CombineOp op) {
		if (hierarchy == null || hierarchy.isEmpty() || pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Cannot combine - no annotations found");
			return;
		}
		PathObject pathObject = pathObjects.remove(0);
		if (!(pathObject instanceof PathAnnotationObject) || !(pathObject.getROI() instanceof PathShape)) {
			logger.warn("Combine annotations: No annotation with ROI selected");				
			return;
		}
		pathObjects.removeIf(p -> !(p.getROI() instanceof PathShape)); // Remove any non-shape ROIs
		if (pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Only one annotation with shape ROIs found");				
			return;
		}
	
		PathShape shapeMask = (PathShape)pathObject.getROI();
		Area areaOriginal = RoiTools.getArea(shapeMask);
		Area areaNew = new Area(areaOriginal);
		Iterator<PathObject> iter = pathObjects.iterator();
		List<PathObject> objectsToAdd = new ArrayList<>();
		int changes = 0;
		while (iter.hasNext()) {
			PathObject temp = iter.next();
			Area areaTemp = RoiTools.getArea(temp.getROI());
			PathObject annotationNew = null;
			if (op == RoiTools.CombineOp.SUBTRACT) {
				areaTemp.subtract(areaNew);
				if (!areaTemp.isEmpty()) {
					PathShape shapeNew = RoiTools.getShapeROI(areaTemp, shapeMask.getImagePlane());
					annotationNew = PathObjects.createAnnotationObject(shapeNew, temp.getPathClass());
				}
			} else if (op == RoiTools.CombineOp.INTERSECT) {
				areaTemp.intersect(areaNew);
				if (!areaTemp.isEmpty()) {
					PathShape shapeNew = RoiTools.getShapeROI(areaTemp, shapeMask.getImagePlane());
					annotationNew = PathObjects.createAnnotationObject(shapeNew, temp.getPathClass());
				}
			} else {
				RoiTools.combineAreas(areaNew, areaTemp, op);
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
		if (op == RoiTools.CombineOp.ADD) {
			PathShape shapeNew = RoiTools.getShapeROI(areaNew, shapeMask.getImagePlane());
			if (!shapeNew.isEmpty())
				objectsToAdd.add(PathObjects.createAnnotationObject(shapeNew, pathObject.getPathClass()));
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
	
}