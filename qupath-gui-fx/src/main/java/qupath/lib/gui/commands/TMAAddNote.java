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

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

/**
 * Add a note to the metadata for a TMA core object.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAAddNote implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(TMAAddNote.class);
	
	private QuPathGUI qupath;
	
	private String name = "Note";
	
	public TMAAddNote(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		ImageData<?> imageData = qupath.getImageData();
		PathObject selectedObject = imageData.getHierarchy().getSelectionModel().getSelectedObject();
		if (imageData == null || !(selectedObject instanceof TMACoreObject)) {
			logger.warn("No TMA core selected!  No note will be added.");
			return;
		}
		
		TMACoreObject core = (TMACoreObject)selectedObject;
		
		String currentText = core.getMetadataMap().get(name);
		if (currentText == null)
			currentText = "";
		
		String prompt = core.getName() == null || core.getName().trim().isEmpty() ? "Core" : core.getName();
		String inputText = DisplayHelpers.showInputDialog("Add TMA note", prompt, currentText);
		if (inputText != null) {
			core.putMetadataValue(name, inputText);
			imageData.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(core));			
		}
		
		// It's nice to return focus to the viewer, if possible
		qupath.getStage().requestFocus();
		qupath.getViewer().getView().requestFocus();
	}

}
