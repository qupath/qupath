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

package qupath.lib.gui.scripting;

import java.io.File;

/**
 * Minimal interface for a script editor that the GUI can call.
 * 
 * @author Pete Bankhead
 *
 */
public interface ScriptEditor {
	
	/**
	 * Show the script editor.
	 */
	public void showEditor();
	
	/**
	 * Show the script editor, including a new script with the specified name.
	 * @param name name of the script to show
	 * @param script content of the script
	 */
	public void showScript(String name, String script);
	
	/**
	 * Show the script editor, opening an existing script file.
	 * @param file the script file
	 */
	public void showScript(File file);
	
}
