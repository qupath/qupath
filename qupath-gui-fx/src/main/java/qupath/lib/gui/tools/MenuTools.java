/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.localization.QuPathResources;

/**
 * Static methods to help with creating and populating JavaFX menus.
 * 
 * @author Pete Bankhead
 *
 */
public class MenuTools {
	
	private static final Logger logger = LoggerFactory.getLogger(MenuTools.class);
	
	/**
	 * Create a menu, optionally add new menu items with {@link #addMenuItems(List, Object...)}.
	 * 
	 * @param name
	 * @param items
	 * @return the newly-created and populated menu
	 */
	public static Menu createMenu(final String name, final Object... items) {
		Menu menu = new Menu();
		if (QuPathResources.hasString(name))
			QuPathResources.getLocalizedResourceManager().registerProperty(menu.textProperty(), name);
		else
			menu.setText(name);
		if (items.length > 0)
			addMenuItems(menu, items);
		return menu;
	}
	
	/**
	 * Add menu items to an existing menu.
	 * Items may be
	 * <ul>
	 * <li>a {@link MenuItem}</li>
	 * <li>an {@link Action}</li>
	 * <li>{@code null} (indicating that a separator should be added)</li>
	 * </ul>
	 * 
	 * @param menu menu to which items should be added
	 * @param items the items that should be provided (MenuItems or Actions, or null to insert a separator)
	 * @return the provided menu, so that this method can be nested inside other calls.
	 */
	public static Menu addMenuItems(final Menu menu, final Object... items) {
		addMenuItems(menu.getItems(), items);
		return menu;
	}
	
	/**
	 * Add menu items to the specified list. This is similar to {@link #addMenuItems(Menu, Object...)} but makes it
	 * possible to work also with a {@link ContextMenu} in addition to a standard {@link Menu}.
	 * 
	 * @param menuItems existing list to which items should be added, or null if a new list should be created
	 * @param items the items that should be provided (MenuItems or Actions, or null to insert a separator)
	 * @return the list containing the adding items (same as the original if provided)
	 */
	public static List<MenuItem> addMenuItems(List<MenuItem> menuItems, final Object... items) {
		if (menuItems == null)
			menuItems = new ArrayList<>();

		// Check if the last item was a separator -
		// we don't want two adjacent separators, since this looks a bit weird
		boolean lastIsSeparator = !menuItems.isEmpty() && menuItems.getLast() instanceof SeparatorMenuItem;
		
		List<MenuItem> newItems = new ArrayList<>();
		for (Object item : items) {
			if (item == null) {
				if (!lastIsSeparator)
					newItems.add(new SeparatorMenuItem());
				lastIsSeparator = true;
			}
			else if (item instanceof MenuItem menuItem) {
				newItems.add(menuItem);
				lastIsSeparator = false;
			}
			else if (item instanceof Action action) {
				newItems.add(ActionTools.createMenuItem(action));
				lastIsSeparator = false;
			} else
				logger.warn("Could not add menu item {}", item);
		}
		if (!newItems.isEmpty()) {
			menuItems.addAll(newItems);
		}
		return menuItems;
	}
	
	/**
	 * Get a reference to an existing menu, optionally creating a new menu if it is not present.
	 * 
	 * @param menus 
	 * @param name
	 * @param createMenu
	 * @return
	 */
	public static Menu getMenu(final List<Menu> menus, final String name, final boolean createMenu) {
		Menu menuCurrent = null;
		for (String namePart : name.split(">")) {
			namePart = namePart.trim();
			boolean isResourceString = QuPathResources.hasString(namePart);
			String text = isResourceString ? QuPathResources.getString(namePart) : namePart;
			if (menuCurrent == null) {
				menuCurrent = findMenuByNameNonRecursive(menus, text);
				if (menuCurrent == null) {
					if (createMenu) {
						menuCurrent = createMenu(namePart);
						// Make sure we keep the 'Help' menu at the end
						if (lastMenuIsHelp(menus))
							menus.add(menus.size()-1, menuCurrent);
						else
							menus.add(menuCurrent);
					} else
						return null;
				}
			} else {
				List<MenuItem> menuItems = menuCurrent.getItems();
				menuCurrent = findMenuByNameNonRecursive(menuItems, text);
				if (menuCurrent == null) {
					if (createMenu) {
						menuCurrent = createMenu(namePart);
						menuItems.add(menuCurrent);
					} else
						return null;
				}				
			}
		}
		return menuCurrent;
	}
	
	
	private static Menu findMenuByNameNonRecursive(List<? extends MenuItem> menuItems, String name) {
		for (var item : menuItems) {
			if (item instanceof Menu) {
				if (Objects.equals(name, item.getText()))
					return (Menu)item;
			}
		}
		return null;
	}
	
	
	
	private static boolean lastMenuIsHelp(List<Menu> menus) {
		return !menus.isEmpty() && QuPathResources.getString("Menu.Help").equals(menus.get(menus.size()-1).getText());
	}
	
	
	private static Menu createMenu(String keyOrName) {
		var menu = new Menu();
		if (QuPathResources.hasString(keyOrName))
			QuPathResources.getLocalizedResourceManager().registerProperty(menu.textProperty(), keyOrName);
		else
			menu.setText(keyOrName);
		return menu;
	}
	
	
	
	/**
	 * Get a flattened list of all menu items recursively.
	 * 
	 * @param menuItems initial list of items (some may themselves be menus)
	 * @param excludeMenusAndSeparators if true, exclude all items that are themselves either menus or separators
	 * @return
	 */
	public static List<MenuItem> getFlattenedMenuItems(List<? extends MenuItem> menuItems, boolean excludeMenusAndSeparators) {
		return getFlattenedMenuItems(excludeMenusAndSeparators, menuItems.toArray(MenuItem[]::new));
	}

	/**
	 * Get a flattened list of all menu items recursively.
	 * 
	 * @param excludeMenusAndSeparators if true, exclude all items that are themselves either menus or separators
	 * @param items initial array of items (some may themselves be menus)
	 * @return
	 */
	public static List<MenuItem> getFlattenedMenuItems(boolean excludeMenusAndSeparators, MenuItem... items) {
		List<MenuItem> result = new ArrayList<>();
		for (var temp : items) {
			addMenuItemsRecursive(temp, result, excludeMenusAndSeparators);
		}
		return result;
	}
	
	private static void addMenuItemsRecursive(MenuItem item, List<MenuItem> existingItems, boolean excludeMenusAndSeparators) {
		if (item instanceof Menu) {
			if (!excludeMenusAndSeparators)
				existingItems.add(item);
			for (var item2 : ((Menu)item).getItems())
				addMenuItemsRecursive(item2, existingItems, excludeMenusAndSeparators);
		} else if (!excludeMenusAndSeparators || !(existingItems instanceof SeparatorMenuItem))
			existingItems.add(item);
	}
	

}