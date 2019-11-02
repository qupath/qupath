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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

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
		// Ensure the main selected object is first in the list, if possible
		var selected = new ArrayList<>(viewer.getHierarchy().getSelectionModel().getSelectedObjects());
		var mainObject = viewer.getHierarchy().getSelectionModel().getSelectedObject();
		if (mainObject != null && !selected.isEmpty() && !selected.get(0).equals(mainObject)) {
			selected.remove(mainObject);
			selected.add(0, mainObject);
		}
		combineAnnotations(viewer.getHierarchy(), selected, op);
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
	static void combineAnnotations(PathObjectHierarchy hierarchy, List<PathObject> pathObjects, RoiTools.CombineOp op) {
		if (hierarchy == null || hierarchy.isEmpty() || pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Cannot combine - no annotations found");
			return;
		}
		
		pathObjects = new ArrayList<>(pathObjects);
		PathObject pathObject = pathObjects.get(0);
		if (!pathObject.isAnnotation()) { // || !RoiTools.isShapeROI(pathObject.getROI())) {
			logger.warn("Combine annotations: No annotation with ROI selected");				
			return;
		}
		var plane = pathObject.getROI().getImagePlane();
//		pathObjects.removeIf(p -> !RoiTools.isShapeROI(p.getROI())); // Remove any null or point ROIs, TODO: Consider supporting points
		pathObjects.removeIf(p -> !p.hasROI() || !p.getROI().getImagePlane().equals(plane)); // Remove any null or point ROIs, TODO: Consider supporting points
		if (pathObjects.isEmpty()) {
			logger.warn("Cannot combint annotations - only one suitable annotation found");
			return;
		}
		
		var allROIs = pathObjects.stream().map(p -> p.getROI()).collect(Collectors.toCollection(() -> new ArrayList<>()));
		ROI newROI;
		
		switch (op) {
		case ADD:
			newROI = RoiTools.unionROIs(allROIs);
			break;
		case INTERSECT:
			newROI = RoiTools.intersectROIs(allROIs);
			break;
		case SUBTRACT:
			var first = allROIs.remove(0);
			newROI = RoiTools.combineROIs(first, RoiTools.unionROIs(allROIs), op);
			break;
		default:
			throw new IllegalArgumentException("Unknown combine op " + op);
		}
	
		if (newROI == null) {
			logger.debug("No changes were made");
			return;
		}
		
		PathObject newObject = null;
		if (!newROI.isEmpty()) {
			newObject = PathObjects.createAnnotationObject(newROI, pathObject.getPathClass());
			newObject.setName(pathObject.getName());
			newObject.setColorRGB(pathObject.getColorRGB());
		}

		// Remove previous objects
		hierarchy.removeObjects(pathObjects, true);
		if (newObject != null)
			hierarchy.addPathObject(newObject);
	}
	
}