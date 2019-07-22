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

import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.SnapshotType;

/**
 * Command to copy a screenshot to the system clipboard.
 * 
 * @author Pete Bankhead
 *
 */
public class CopyViewToClipboardCommand implements PathCommand {
	
	final private QuPathGUI qupath;
	final private SnapshotType type;
	
	public CopyViewToClipboardCommand(final QuPathGUI qupath, final SnapshotType type) {
		super();
		this.qupath = qupath;
		this.type = type;
	}

	@Override
	public void run() {
		Image img = DisplayHelpers.makeSnapshotFX(qupath, type);
		Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.IMAGE, img));
	}
	
}