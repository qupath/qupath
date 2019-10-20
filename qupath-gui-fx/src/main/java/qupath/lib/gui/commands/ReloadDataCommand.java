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

import java.awt.image.BufferedImage;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;

/**
 * Command to revert the current ImageData back to any previously-saved version.
 * 
 * @author Pete Bankhead
 *
 */
public class ReloadDataCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ReloadDataCommand.class);
	
	private QuPathGUI qupath;
	
	public ReloadDataCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		// Check if we have an image
		ImageData<BufferedImage> imageData = qupath.getImageData();
		if (imageData == null) {
			DisplayHelpers.showErrorMessage("Revert", "No open image selected!");
			return;
		}

		// Check if we have a saved file
		File savedFile = imageData.getLastSavedPath() == null ? null : new File(imageData.getLastSavedPath());
		if (savedFile == null || !savedFile.isFile()) {
			DisplayHelpers.showErrorMessage("Revert", "No previously saved data found!");
			return;
		}
		
		if (DisplayHelpers.showConfirmDialog("Revert", "Revert to last saved version?  All changes will be lost.")) {
			try {
				logger.info("Reverting to last saved version: {}", savedFile.getAbsolutePath());
				ImageData<BufferedImage> imageDataNew = PathIO.readImageData(savedFile, null, imageData.getServer(), BufferedImage.class);
				qupath.getViewer().setImageData(imageDataNew);
			} catch (Exception e) {
				DisplayHelpers.showErrorMessage("Revert", "Error reverting to previously saved file\n\n" + e.getLocalizedMessage());
			}
		}

	}

}
