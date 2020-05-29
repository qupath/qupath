/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.scripting.richtextfx;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * QuPath extension to add a more attractive script editor with syntax highlighting, 
 * making use of RichTextFX, Copyright (c) 2013-2014, Tomas Mikula (BSD 2-clause license).
 * 
 * @author Pete Bankhead
 *
 */
public class RichScriptEditorExtension implements QuPathExtension {

	@Override
	public void installExtension(QuPathGUI qupath) {
		qupath.setScriptEditor(new RichScriptEditor(qupath));
	}

	@Override
	public String getName() {
		return "Rich script editor extension";
	}

	@Override
	public String getDescription() {
		return "Adds a more attractive script editor with syntax highlighting, making use of RichTextFX - https://github.com/TomasMikula/RichTextFX";
	}

}
