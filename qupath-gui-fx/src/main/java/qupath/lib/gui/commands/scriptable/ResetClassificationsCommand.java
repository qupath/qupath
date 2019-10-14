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

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;


/**
 * Select objects according to a specified predicate.
 * 
 * @author Pete Bankhead
 *
 */
public class ResetClassificationsCommand implements PathCommand {
	
	public final static Logger logger = LoggerFactory.getLogger(ResetClassificationsCommand.class);
	
	private ImageDataWrapper<?> manager;
	private Class<? extends PathObject> cls;
	
	public ResetClassificationsCommand(final ImageDataWrapper<?> manager, final Class<? extends PathObject> cls) {
		super();
		this.manager = manager;
		this.cls = cls;
	}

	@Override
	public void run() {
		ImageData<?> imageData = manager.getImageData();
		if (imageData == null)
			return;
		resetClassifications(imageData, cls);
	}
	
	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData
	 * @param cls
	 */
	public static void resetClassifications(final ImageData<?> imageData, final Class<? extends PathObject> cls) {
		if (imageData == null) {
			logger.warn("No classifications to reset!");
			return;
		}
		// Do the reset
		QP.resetClassifications(imageData.getHierarchy(), cls);
		
		// Log the appropriate command
		Map<String, String> params = Collections.singletonMap("Type", PathObjectTools.getSuitableName(cls, false));
		String method;
		if (cls == PathDetectionObject.class)
			method = "resetDetectionClassifications();";
		else // TODO: Get a suitable name to disguise Java classes
			method = "resetClassifications(" + cls.getName() + ");";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Reset classifications", params, method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}
	

}
