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

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;

/**
 * Create a new annotation which is the inverse of the existing selected annotation,
 * i.e. it includes the entire (2D) image except for the bit currently annotated.
 * 
 * TODO: Handle multiple selections.
 * 
 * @author Pete Bankhead
 *
 */
public class InverseObjectCommand implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(InverseObjectCommand.class);
	
	private ImageDataWrapper<?> manager;
	
	public InverseObjectCommand(final ImageDataWrapper<?> manager) {
		this.manager = manager;
	}
	

	@Override
	public void run() {
		
		ImageData<?> imageData = manager.getImageData();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		
		// Get the currently-selected area
		PathArea shapeSelected = null;
		if (pathObject instanceof PathAnnotationObject) {
			shapeSelected = getAreaROI(pathObject);
		}
		if (shapeSelected == null) {
			logger.error("Cannot create inverse annotation from " + pathObject);
			return;
		}
		
		// Get the parent area to use
		PathObject parent = pathObject.getParent();
		PathArea shape = getAreaROI(parent);
		if (shape == null)
			shape = new RectangleROI(0, 0, imageData.getServer().getWidth(), imageData.getServer().getHeight(), shapeSelected.getC(), shapeSelected.getZ(), shapeSelected.getT());
		
		// Create the new ROI
		PathShape shapeNew = PathROIToolsAwt.combineROIs(shape, shapeSelected, PathROIToolsAwt.CombineOp.SUBTRACT);
		PathObject pathObjectNew = new PathAnnotationObject(shapeNew);
		
		// Reassign all other children to the new parent
		List<PathObject> children = new ArrayList<>(parent.getChildObjects());
		children.remove(pathObject);
		pathObjectNew.addPathObjects(children);
		
		parent.addPathObject(pathObjectNew);
		hierarchy.fireHierarchyChangedEvent(parent);
		hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
	}
	
	
	static Area getROIArea(PathObject pathObject) {
		ROI pathROI = pathObject.getROI();
		if (!(pathROI instanceof PathShape))
			return null;
		return PathROIToolsAwt.getArea(pathObject.getROI());		
	}
	
	
	static PathArea getAreaROI(PathObject pathObject) {
		if (pathObject == null)
			return null;
		ROI pathROI = pathObject.getROI();
		if (!(pathROI instanceof PathArea))
			return null;
		return (PathArea)pathObject.getROI();
	}
	

}
