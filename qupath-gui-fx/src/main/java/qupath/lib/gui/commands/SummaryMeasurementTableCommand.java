/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ui.SummaryMeasurementTable;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;


/**
 * Show a summary table for an object of a particular type (annotation, detection, TMA core...)
 * 
 * @author Pete Bankhead
 */
public class SummaryMeasurementTableCommand {

	private static final Logger logger = LoggerFactory.getLogger(SummaryMeasurementTableCommand.class);

	private final QuPathGUI qupath;

	/**
	 * Command to show a summary measurement table, for PathObjects of a specified type (e.g. annotation, detection).
	 * @param qupath
	 */
	public SummaryMeasurementTableCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	/**
	 * Show a measurement table for the specified image data.
	 * @param imageData the image data
	 * @param type the object type to show
	 */
	public void showTable(ImageData<BufferedImage> imageData, Class<? extends PathObject> type) {
		if (imageData == null) {
			logger.debug("Show table called with no image");
			GuiTools.showNoImageError("Show measurement table");
			return;
		}
		logger.debug("Show table called for {} and object filter {}", imageData, PathObjectTools.getSuitableName(type, false));
		new SummaryMeasurementTable(qupath, imageData).showTable(type);
	}
	

}
