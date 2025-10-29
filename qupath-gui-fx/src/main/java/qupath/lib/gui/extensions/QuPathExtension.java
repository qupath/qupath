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

import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;

/**
 * Simple interface for QuPath extensions.
 * <p>
 * This allows dynamic discovery of new extensions.
 * 
 * @author Pete Bankhead
 *
 */
public interface QuPathExtension {
	
	/**
	 * Install the extension for a QuPathGUI instance. This function is only called if the graphical user
	 * interface is used.
	 * <p>
	 * This generally involves adding new commands to appropriate menus.
	 * <p>
	 * Note that if an extension is only expected to be compatible with a specific QuPath version, 
	 * this method provides an opportunity to test version compatibility before making any changes.
	 * <p>
	 * See {@link #installHeadless()} for installing the extension in headless mode.
	 * 
	 * @param qupath
	 * @see QuPathGUI#getVersion()
	 * 
	 * @implNote When multiple extensions are present, the order in which they will be installed is undefined.
	 */
	public void installExtension(QuPathGUI qupath);

	/**
	 * Install the extension in headless mode. This function is only called if QuPath is run without the user
	 * interface.
	 * <p>
	 * See {@link #installExtension(QuPathGUI)} for installing the extension when the graphical user interface
	 * is used.
	 *
	 * @implNote When multiple extensions are present, the order in which they will be installed is undefined.
	 */
	default void installHeadless() {}
	
	/**
	 * A readable name for the extension.
	 * 
	 * @return
	 */
	public String getName();
	
	/**
	 * A short description of the extension for displaying in the main GUI.
	 * <p>
	 * This could also contain licensing information.
	 * 
	 * @return
	 */
	public String getDescription();
	
	/**
	 * Get a QuPath version for which this extension was written.
	 * <p>
	 * This is used to provide an explanation if the extension could not be loaded.
	 * It has a default implementation that returns {@link Version#UNKNOWN} to allow backwards compatibility, 
	 * however it strongly recommended to return the actual QuPath version against which 
	 * the extension was developed and tested.
	 * @return a semantic version corresponding to a QuPath version, e.g. "0.3.0".
	 * @see Version
	 */
	public default Version getQuPathVersion() {
		return Version.UNKNOWN;
	}
	
	/**
	 * Get the version of the current extension.
	 * @return
	 * @implNote the default implementation looks for any package version associated with the implementing class, 
	 *           returning {@link Version#UNKNOWN} if none can be found.
	 * @see GeneralTools#getPackageVersion(Class)
	 */
	public default Version getVersion() {
		var packageVersion = GeneralTools.getPackageVersion(getClass());
		return Version.parse(packageVersion);
	}

}
