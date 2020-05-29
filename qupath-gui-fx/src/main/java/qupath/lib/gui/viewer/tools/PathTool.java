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

package qupath.lib.gui.viewer.tools;

import javafx.scene.Node;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Interface defining how a toolbar tool interacts with a viewer.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathTool {
	
	/**
	 * Register the tool on the viewer. This typically means adding a mouse listener.
	 * A tool should only be registered on one viewer at a time, and only one tool should 
	 * be registered per viewer.
	 * @param viewer the viewer for which this tool should be registered
	 */
	public void registerTool(QuPathViewer viewer);

	/**
	 * Deregister the tool from the viewer. It is essential that tools clean up properly 
	 * and do not impact other tools that may be registered for the viewer later.
	 * @param viewer the viewer from which this tool should be deregistered
	 */
	public void deregisterTool(QuPathViewer viewer);
	
	/**
	 * Get the name of the tool
	 * @return
	 */
	public String getName();
	
	/**
	 * Get the icon of the tool.
	 * @return
	 */
	public Node getIcon();

}
