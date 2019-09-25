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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.SnapshotType;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ImageWriterTools;

/**
 * 
 * Save a snapshot of the current window, or the current viewer.
 * 
 * Most useful for creating demo screenshots.
 * 
 * @author Pete Bankhead
 *
 */
public class SaveViewCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(SaveViewCommand.class);

	private QuPathGUI qupath;
	private SnapshotType type;
	
	private static File dirPrevious = null;
	
	public SaveViewCommand(final QuPathGUI qupath, final SnapshotType type) {
		this.qupath = qupath;
		this.type = type;
	}

	@Override
	public void run() {
		BufferedImage img = DisplayHelpers.makeSnapshot(qupath, type);			
		
		String ext = PathPrefs.getDefaultScreenshotExtension();
		List<ImageWriter<BufferedImage>> compatibleWriters = ImageWriterTools.getCompatibleWriters(BufferedImage.class, ext);
		if (compatibleWriters.isEmpty()) {
			logger.error("No compatible image writers found for extension: " + ext);
			return;
		}
		
		File fileOutput = qupath.getDialogHelper().promptToSaveFile(null, dirPrevious, null, ext, ext);
		if (fileOutput == null)
			return;
		
		// Loop through the writers and stop when we are successful
		for (var writer : compatibleWriters) {
			try {
				writer.writeImage(img, fileOutput.getAbsolutePath());
				dirPrevious = fileOutput.getParentFile();
				return;
			} catch (Exception e) {
				logger.error("Error saving snapshot " + type + " to " + fileOutput.getAbsolutePath(), e);
			}
		}
	}
	
}