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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathShape;


/**
 * Merge together the ROIs of selected objects to create a new object, removing the originals.
 * 
 * @author Pete Bankhead
 *
 */
public class MergeSelectedAnnotationsCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(MergeSelectedAnnotationsCommand.class);
	
	private ImageDataWrapper<?> manager;
	
	public MergeSelectedAnnotationsCommand(final ImageDataWrapper<?> manager) {
		this.manager = manager;
	}
	

	@Override
	public void run() {
		ImageData<?> imageData = manager.getImageData();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Get all the selected annotations with area
		PathShape shapeNew = null;
		List<PathObject> children = new ArrayList<>();
		Set<PathClass> pathClasses = new HashSet<>();
		for (PathObject child : hierarchy.getSelectionModel().getSelectedObjects()) {
			if (child instanceof PathAnnotationObject && child.getROI() instanceof PathArea) {
				if (shapeNew == null)
					shapeNew = (PathShape)child.getROI();//.duplicate();
				else
					shapeNew = PathROIToolsAwt.combineROIs(shapeNew, (PathArea)child.getROI(), PathROIToolsAwt.CombineOp.ADD);
				if (child.getPathClass() != null)
					pathClasses.add(child.getPathClass());
				children.add(child);
			}
		}
		// Check if we actually merged anything
		if (children.size() <= 1)
			return;
		
		// Create and add the new object, removing the old ones
		PathObject pathObjectNew = new PathAnnotationObject(shapeNew);
		if (pathClasses.size() == 1)
			pathObjectNew.setPathClass(pathClasses.iterator().next());
		else
			logger.warn("Cannot assign class unambiguously - " + pathClasses.size() + " classes represented in selection");
		hierarchy.removeObjects(children, true);
		hierarchy.addPathObject(pathObjectNew, false);
//		pathObject.removePathObjects(children);
//		pathObject.addPathObject(pathObjectNew);
//		hierarchy.fireHierarchyChangedEvent(pathObject);
		hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
	}
	
	

}
