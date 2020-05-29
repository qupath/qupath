/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.process.gui.ml.PixelClassifierPane;

/**
 * Open GUI for the current viewer to train a new pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierCommand implements Runnable {

	@Override
	public void run() {
		var qupath = QuPathGUI.getInstance();
		var imageData = qupath.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError("Pixel classifier");
		} else
			new PixelClassifierPane(qupath);
	}

}