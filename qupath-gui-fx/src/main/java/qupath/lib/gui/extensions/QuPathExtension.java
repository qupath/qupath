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

package qupath.lib.gui.extensions;

import qupath.lib.gui.QuPathGUI;

/**
 * Simple interface for QuPath extensions.
 * 
 * This allows dynamic discovery of new extensions.
 * 
 * @author Pete Bankhead
 *
 */
public interface QuPathExtension {
	
	/**
	 * Install the extension for a QuPathGUI instance.
	 * 
	 * This generally involves adding new commands to appropriate menus.
	 * 
	 * (Where multiple extensions are present, the order in which they will be installed is undefined.)
	 * 
	 * @param qupath
	 */
	public void installExtension(QuPathGUI qupath);
	
	/**
	 * A readable name for the extension.
	 * 
	 * @return
	 */
	public String getName();
	
	/**
	 * A short description of the extension for displaying in the main GUI.
	 * 
	 * This could also contain licensing information.
	 * 
	 * @return
	 */
	public String getDescription();

}
