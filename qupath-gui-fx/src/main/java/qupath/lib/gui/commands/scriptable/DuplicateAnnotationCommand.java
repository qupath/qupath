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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Duplicate a PathAnnotationObject.
 * <br>
 * The resulting object will have the same PathClass and a duplicate ROI, but a different (empty) MeasurementList.
 *
 * @author Pete Bankhead
 *
 */
public class DuplicateAnnotationCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(DuplicateAnnotationCommand.class);
			
	private ImageDataWrapper<?> manager;
	
	public DuplicateAnnotationCommand(final ImageDataWrapper<?> manager) {
		this.manager = manager;
	}

	@Override
	public void run() {
		ImageData<?> imageData = manager.getImageData();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		if (!(pathObject instanceof PathAnnotationObject)) {
			logger.error("Only annotation objects can be duplicated!");
			return;
		}
		PathObject pathObjectNew = PathObjects.createAnnotationObject(pathObject.getROI().duplicate(), pathObject.getPathClass());
		hierarchy.addPathObject(pathObjectNew);
		hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
	}
	
}