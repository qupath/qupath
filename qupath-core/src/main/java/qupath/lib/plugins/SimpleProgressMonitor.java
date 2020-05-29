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

package qupath.lib.plugins;

import qupath.lib.regions.ImageRegion;

/**
 * Class for monitoring the process of a plugin and displaying feedback to the user.
 * <p>
 * Implementing classes receive notifications from the plugin as it executes, and should display these in an appropriate way -
 * such as with a dialog box and progress bar, or logging the progress to the system output.
 * Classes may also choose to send cancel requests to the plugin, e.g. if the user presses a 'cancel' button.
 * <p>
 * SimpleProgressMonitor are not intended for reuse, i.e. the startMonitoring method should only be called once.
 * 
 * @author Pete Bankhead
 *
 */
public interface SimpleProgressMonitor {
	
	/**
	 * Set the plugin to monitor, and begin monitoring.  Note that since SimpleProgressMonitor are not intended for reuse,
	 * this method should only be called once.
	 * 
	 * @param message The message to display
	 * @param maxProgress The progress value considered complete.
	 * @param mayCancel 
	 */
	public void startMonitoring(String message, int maxProgress, boolean mayCancel);
	
	/**
	 * Update the displayed progress, and optionally inform the PluginRunner that data related to a specified image region has been updated.
	 * If progress &gt;= 1 this indicates that the task is finished, and the monitor may stop.
	 * 
	 * @param increment update progress by the specified increment
	 * @param message optional message that may be displayed to reflect the current progress status.
	 * @param region optional region of the image that has been changed; in interactive mode, this region may be repainted.
	 */
	public void updateProgress(int increment, String message, ImageRegion region);
	
	/**
	 * Notify the monitor that the plugin has completed its work.  This is called automatically by updateProgress if progress &gt;= 1,
	 * but may also be called if the plugin was cancelled or otherwise terminated abnormally.
	 * 
	 * @param message message to show upon completion.
	 */
	public void pluginCompleted(String message);
	
	/**
	 * Returns true if cancel has been requested, for example by the user pressing a 'cancel' button.
	 * @return
	 */
	public boolean cancelled();

}