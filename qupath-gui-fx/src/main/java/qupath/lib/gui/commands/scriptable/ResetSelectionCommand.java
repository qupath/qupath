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

import javafx.beans.value.ObservableValue;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;


/**
 * Reset the selection, i.e. ensure no objects are selected.
 * 
 * @author Pete Bankhead
 * 
 * @param <T> generic parameter for {@link ImageData}
 *
 */
public class ResetSelectionCommand<T> implements PathCommand {
	
	public final static Logger logger = LoggerFactory.getLogger(ResetSelectionCommand.class);
	
	private ObservableValue<ImageData<T>> manager;
	
	public ResetSelectionCommand(final ObservableValue<ImageData<T>> manager) {
		super();
		this.manager = manager;
	}

	@Override
	public void run() {
		ImageData<?> imageData = manager.getValue();
		if (imageData == null)
			return;
		resetSelection(imageData);
	}
	
	public static void resetSelection(final ImageData<?> imageData) {
		if (imageData == null) {
			logger.warn("No image available!");
			return;
		}
		
		// Do the action reset
		imageData.getHierarchy().getSelectionModel().clearSelection();
		
		// Log the appropriate command
		String method = "resetSelection();";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Reset selection", method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}
	

}
