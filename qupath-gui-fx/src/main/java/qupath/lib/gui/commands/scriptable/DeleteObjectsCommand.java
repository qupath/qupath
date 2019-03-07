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

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to delete selected objects, or all objects of a specified type.
 * 
 * @author Pete Bankhead
 *
 */
public class DeleteObjectsCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(DeleteObjectsCommand.class);
	
	private ImageDataWrapper<?> manager;
	private Class<? extends PathObject> cls;
	
	public DeleteObjectsCommand(final ImageDataWrapper<?> manager, final Class<? extends PathObject> cls) {
		super();
		this.manager = manager;
		this.cls = cls;
	}

	@Override
	public void run() {
		ImageData<?> imageData = manager.getImageData();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Handle no specified class - indicates all objects of all types should be cleared
		if (cls == null) {
			int n = hierarchy.nObjects();
			if (n == 0)
				return;
			if (DisplayHelpers.showYesNoDialog("Delete objects", "Delete " + n + " objects?")) {
				hierarchy.clearAll();
				hierarchy.getSelectionModel().setSelectedObject(null);
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear all objects", "clearAllObjects();"));
			}
			return;
		}
		
		// Handle clearing TMA grid
		if (TMACoreObject.class.equals(cls)) {
			if (hierarchy.getTMAGrid() != null) {
				if (DisplayHelpers.showYesNoDialog("Delete objects", "Clear TMA grid?")) {
					hierarchy.clearAll();
					
					PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
					if (selected instanceof TMACoreObject)
						hierarchy.getSelectionModel().setSelectedObject(null);

					imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear TMA Grid", "clearTMAGrid();"));
				}
				return;
			}
		}
		
		
		// Handle clearing objects of another specified type
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		if (pathObjects.isEmpty())
			return;
		int n = pathObjects.size();
		String message = n == 1 ? "Delete 1 object?" : "Delete " + n + " objects?";
		if (DisplayHelpers.showYesNoDialog("Delete objects", message)) {
			hierarchy.removeObjects(pathObjects, true);
			
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (cls == PathDetectionObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear detections", "clearDetections();"));
			else if (cls == PathAnnotationObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear annotations", "clearAnnotations();"));
			else if (cls == TMACoreObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear TMA grid", "clearTMAGrid();"));
			else
				logger.warn("Cannot clear all objects for class {}", cls);
		}
	}
	
	

}
