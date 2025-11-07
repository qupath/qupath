/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023, 2025 QuPath developers, The University of Edinburgh
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.imagej.tools.IJProperties;
import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;

import java.util.List;

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
			GuiTools.showNoImageError(getName());
			return;
		}
		
		var files = FileChoosers.promptForMultipleFiles("ImageJ ROIs",
				FileChoosers.createExtensionFilter("ImageJ ROI files", "*.roi", "*.zip"));
		if (files == null) {
			logger.info("No ImageJ ROI files selected for import");
			return;
		}
		
		List<Roi> rois = files.stream()
				.filter(f -> IJTools.containsImageJRois(f))
				.flatMap(f -> IJTools.readImageJRois(f).stream())
				.toList();
		
		if (rois.isEmpty()) {
			Dialogs.showInfoNotification(getName(), "No ROIs found in selected files");
			return;
		}
		
		var pathObjects = rois.stream().map(ImportRoisCommand::createObject).toList();

		var hierarchy = imageData.getHierarchy();
		hierarchy.addObjects(pathObjects);
		hierarchy.getSelectionModel().selectObjects(pathObjects);
		
	}

    private static PathObject createObject(Roi roi) {
        return IJTools.convertToPathObject(roi, 1.0, 0, 0,
                IJProperties.getObjectCreator(roi).orElse(PathObjects::createAnnotationObject), null);
    }
	
	
}