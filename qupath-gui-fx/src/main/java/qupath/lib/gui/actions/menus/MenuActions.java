/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

/**
 * Common interface to define the contents of a menu in terms of actions.
 * This can be used to generate the actual menu by using the actions to configure the menu items.
 */
public interface MenuActions {

	/**
	 * Get all the actions to include in the menu, in order.
	 * @return
	 */
	List<Action> getActions();

	/**
	 * Get the name of the menu.
	 * @return
	 */
	String getName();
	
}
