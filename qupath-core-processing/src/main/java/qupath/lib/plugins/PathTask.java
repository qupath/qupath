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

package qupath.lib.plugins;

/**
 * Helper interface to define plugin tasks that require post-processing on a specific thread 
 * (i.e. the EDT, or JavaFX Platform equivalent).
 * 
 * @author Pete Bankhead
 *
 */
public interface PathTask extends Runnable {
	
	/**
	 * Post-processing.
	 * 
	 * Should be called on Event Dispatch Thread when using Swing, or Application thread for JavaFX.
	 */
	public void taskComplete();
	
	/**
	 * Get a description of the results from running this task, which may be used e.g. in a progress monitor or output to the command line.
	 * 
	 * @return
	 */
	public String getLastResultsDescription();

}
