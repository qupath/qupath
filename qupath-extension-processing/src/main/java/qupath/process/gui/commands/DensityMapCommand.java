/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.process.gui.commands.density.DensityMapDialog;


/**
 * Command for generating density maps from detections on an image.
 * 
 * @author Pete Bankhead
 */
public class DensityMapCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(DensityMapCommand.class);
	
	private QuPathGUI qupath;
	private DensityMapDialog dialog;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public DensityMapCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		if (dialog == null) {
			logger.trace("Creating density map dialog");
			dialog = new DensityMapDialog(qupath);
			if (qupath.getImageData() != null)
				dialog.updateDefaults(qupath.getImageData());
		}
		
		var stage = dialog.getStage();
		stage.setOnCloseRequest(e -> closeDialog());
		stage.show();
	}
	
	void closeDialog() {
		if (dialog != null) {
			dialog.deregister();
			dialog = null;
		}
	}	
	

}
