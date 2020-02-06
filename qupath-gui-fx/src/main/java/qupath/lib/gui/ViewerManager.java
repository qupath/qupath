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

package qupath.lib.gui;

import java.util.List;

import qupath.lib.gui.viewer.QuPathViewer;

/**
 * ViewerManagers contain a list of one or more viewers, at most one of which can be active.
 * 
 * The purpose is to enable commands that operate on a viewer to be instantiated in advance with a {@link ViewerManager} instance,
 * and then query this to find the active viewer only when necessary.
 * 
 * @author Pete Bankhead
 *
 */
public interface ViewerManager<T extends QuPathViewer> {
	
	/**
	 * @return the currently active viewer
	 */
	public T getViewer();

	/**
	 * @return a list of all available viewers
	 */
	public List<T> getViewers();

}
