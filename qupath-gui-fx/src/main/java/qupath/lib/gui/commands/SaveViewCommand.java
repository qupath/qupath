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
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.ImageWriterTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.SnapshotType;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.io.ImageWriter;

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
	
	public SaveViewCommand(final QuPathGUI qupath, final SnapshotType type) {
		this.qupath = qupath;
		this.type = type;
	}

	@Override
	public void run() {
		BufferedImage img = DisplayHelpers.makeSnapshot(qupath, type);			
		
		String ext = PathPrefs.getDefaultScreenshotExtension();
		SortedMap<ImageWriter<BufferedImage>, String> compatibleWriters = ImageWriterTools.getCompatibleWriters(null, ext);
		if (compatibleWriters.isEmpty()) {
			logger.error("No compatible image writers found for extension: " + ext);
			return;
		}
		
		File fileOutput = qupath.getDialogHelper().promptToSaveFile(null, null, null, ext, ext);
		if (fileOutput == null)
			return;
		try {
			compatibleWriters.firstKey().writeImage(img, fileOutput.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}