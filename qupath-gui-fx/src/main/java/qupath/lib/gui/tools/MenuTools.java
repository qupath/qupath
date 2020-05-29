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

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import qupath.lib.gui.ActionTools;

/**
 * Static methods to help with creating and populating JavaFX menus.
 * 
 * @author Pete Bankhead
 *
 */
public class MenuTools {
	
	private final static Logger logger = LoggerFactory.getLogger(MenuTools.class);
	
	/**
	 * Create a menu, optionally add new menu items with {@link #addMenuItems(List, Object...)}.
	 * 
	 * @param name
	 * @param items
	 * @return the newly-created and populated menu
	 */
	public static Menu createMenu(final String name, final Object... items) {
		Menu menu = new Menu(name);
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
	 * @param menu
	 * @param items
	 * @return the provided menu, so that this method can be nested inside other calls.
	 */
	public static Menu addMenuItems(final Menu menu, final Object... items) {
		// Check if the last item was a separator -
		// we don't want two adjacent separators, since this looks a bit weird
		boolean lastIsSeparator = menu.getItems().isEmpty() ? false : menu.getItems().get(menu.getItems().size()-1) instanceof SeparatorMenuItem;
		
		List<MenuItem> newItems = new ArrayList<>();
		for (Object item : items) {
			if (item == null) {
				if (!lastIsSeparator)
					newItems.add(new SeparatorMenuItem());
				lastIsSeparator = true;
			}
			else if (item instanceof MenuItem) {
				newItems.add((MenuItem)item);
				lastIsSeparator = false;
			}
			else if (item instanceof Action) {
				newItems.add(ActionTools.createMenuItem((Action)item));
				lastIsSeparator = false;
			} else
				logger.warn("Could not add menu item {}", item);
		}
		if (!newItems.isEmpty()) {
			menu.getItems().addAll(newItems);
		}
		return menu;
	}
	
	/**
	 * Add menu items to the specified list. This is similar to {@link #addMenuItems(Menu, Object...)} but makes it
	 * possible to work also with a {@link ContextMenu} in addition to a standard {@link Menu}.
	 * 
	 * @param menuItems existing list to which items should be added, or null if a new list should be created
	 * @param items the items that should be provided (MenuItems or Actions)
	 * @return the list containing the adding items (same as the original if provided)
	 */
	public static List<MenuItem> addMenuItems(List<MenuItem> menuItems, final Object... items) {
		if (menuItems == null)
			menuItems = new ArrayList<>();
		
		boolean lastIsSeparator = menuItems.isEmpty() ? false : menuItems.get(menuItems.size()-1) instanceof SeparatorMenuItem;
		
		List<MenuItem> newItems = new ArrayList<>();
		for (Object item : items) {
			if (item == null) {
				if (!lastIsSeparator)
					newItems.add(new SeparatorMenuItem());
				lastIsSeparator = true;
			}
			else if (item instanceof MenuItem) {
				newItems.add((MenuItem)item);
				lastIsSeparator = false;
			}
			else if (item instanceof Action) {
				newItems.add(ActionTools.createMenuItem((Action)item));
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
		for (String n : name.split(">")) {
			if (menuCurrent == null) {
				for (Menu menu : menus) {
					if (n.equals(menu.getText())) {
						menuCurrent = menu;
						break;
					}
				}
				if (menuCurrent == null) {
					if (createMenu) {
						menuCurrent = new Menu(n.trim());
						// Make sure we don't replace the 'Help' menu at the end
						if (!menus.isEmpty() && "Help".equals(menus.get(menus.size()-1).getText()))
							menus.add(menus.size()-1, menuCurrent);
						else
							menus.add(menuCurrent);
					} else
						return null;
				}
			} else {
				List<MenuItem> searchItems = menuCurrent.getItems();
				menuCurrent = null;
				for (MenuItem menuItem : searchItems) {
					if (menuItem instanceof Menu && (menuItem.getText().equals(n) || menuItem.getText().equals(n.trim()))) {
						menuCurrent = (Menu)menuItem;
						break;
					}
				}
				if (menuCurrent == null) {
					if (createMenu) {
						menuCurrent = new Menu(n.trim());
						searchItems.add(menuCurrent);
					} else
						return null;
				}				
			}
		}
		return menuCurrent;
	}
	
	
	
	
//	public static MenuItem getMenuItem(List<Menu> menus, String itemName) {
//		Collection<MenuItem> menuItems;
//		int ind = itemName.lastIndexOf(">");
//		if (ind >= 0) {
//			Menu menu = getMenu(menus, itemName.substring(0, ind), false);
//			if (menu == null) {
//				logger.warn("No menu found for {}", itemName);
//				return null;
//			}
//			menuItems = menu.getItems();
//			itemName = itemName.substring(ind+1);
//		} else {
//			menuItems = new HashSet<>();
//			for (Menu menu : menus)
//				menuItems.addAll(menu.getItems());
//		}
//		for (MenuItem menuItem : menuItems) {
//			if (itemName.equals(menuItem.getText()))
//				return menuItem;
//		}
//		logger.warn("No menu item found for {}", itemName);
//		return null;
//	}
	
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