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
 * Primary interface for defining a 'plugin' command.
 * <p>
 * Although the interface is very basic, developers wanting to create new plugins should general start 
 * by modifying an existing plugin that does something similar.
 * <p>
 * By paying attention to the type hierarchy and making use of the most appropriate interfaces/abstract classes, 
 * it is possible to get quite a bit of functionality 'for free', including scriptability and parallelization 
 * with appropriate calls to update the object hierarchy.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface PathPlugin<T> {
	
	
	/**
	 * Get the name of the plugin for display.
	 * <p>
	 * This should be descriptive and, above all, short - as it may be used for menu item names &amp; dialog box titles.
	 * 
	 * @return
	 */
	public String getName();
	
	/**
	 * Get a brief description of the plugin's purpose &amp; operation.
	 * <p>
	 * If no description is provided, this may return null.
	 * 
	 * @return
	 */
	public String getDescription();
	
	/**
	 * Run the plugin.  A PluginRunner may be provided that this plugin can use to update
	 * the user on its progress.
	 * <p>
	 * Note: This command should block until it has completed processing.
	 * 
	 * @param pluginRunner
	 * @param arg
	 * @return
	 */
	public boolean runPlugin(PluginRunner<T> pluginRunner, String arg);
	
	/**
	 * (Optional) short one-line description of the results, e.g. to say how many objects detected.
	 * GUIs may choose to display this on a label during interactive processing.
	 * 
	 * @return
	 */
	public String getLastResultsDescription();
	
	
}