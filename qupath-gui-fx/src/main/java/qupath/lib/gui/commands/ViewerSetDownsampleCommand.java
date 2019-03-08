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

import qupath.lib.gui.ViewerManager;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Command to set the downsample factor for a viewer to a specific value.
 * 
 * @author Pete Bankhead
 *
 */
public class ViewerSetDownsampleCommand implements PathCommand {
	
	private ViewerManager<?> manager;
	private QuPathViewer viewer;
	private double downsample;
	
	/**
	 * Set the downsample.  May be locked to a particular QuPathViewer or, if this is null, applied to the active viewer.
	 * 
	 * @param viewer
	 * @param downsample
	 */
	public ViewerSetDownsampleCommand(final QuPathViewer viewer, final double downsample) {
		this.viewer = viewer;
		this.downsample = downsample;
	}
	
	public ViewerSetDownsampleCommand(final ViewerManager<?> manager, final double downsample) {
		this.manager = manager;
		this.downsample = downsample;
	}
	
	private QuPathViewer getViewer() {
		if (viewer != null)
			return viewer;
		if (manager != null)
			return manager.getViewer();
		return null;
	}

	@Override
	public void run() {
		QuPathViewer viewer2 = getViewer();
		if (viewer2 != null)
			viewer2.setDownsampleFactor(downsample);
	}
	
}