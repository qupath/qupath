/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.imagej.gui;

import ij.gui.Roi;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

/**
 * Import ROIs from ImageJ, saved in .roi or .zip format.
 * 
 * @author Pete Bankhead
 *
 */
class ImportRoisCommand implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(ImportRoisCommand.class);
	
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath QuPath instance where the command should be installed.
	 */
	public ImportRoisCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	/**
	 * Get the name of the command.
	 * @return
	 */
	public String getName() {
		return "Import ImageJ ROIs";
	}
	

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		var imageData = viewer == null ? null : viewer.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError(getName());
			return;
		}
		
		var files = Dialogs.promptForMultipleFiles("ImageJ ROIs", null, "ImageJ ROI files", ".roi", ".zip");
		if (files == null) {
			logger.info("No ImageJ ROI files selected for import");
			return;
		}
		
		List<Roi> rois = files.stream()
				.filter(f -> IJTools.containsImageJRois(f))
				.flatMap(f -> IJTools.readImageJRois(f).stream())
				.collect(Collectors.toList());
		
		if (rois.isEmpty()) {
			Dialogs.showInfoNotification(getName(), "No ROIs found in selected files");
			return;
		}
		
		// TODO: Consider adding options for the import, if needed
		double downsample = 1.0;
		double xOrigin = 0.0;
		double yOrigin = 0.0;
		var pathObjects = rois.stream().map(r -> IJTools.convertToAnnotation(r, xOrigin, yOrigin, downsample, null)).collect(Collectors.toList());

		var hierarchy = imageData.getHierarchy();
		hierarchy.addObjects(pathObjects);
		hierarchy.getSelectionModel().selectObjects(pathObjects);
		
	}
	
	
}