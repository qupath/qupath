/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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



package qupath.lib.gui;

import java.util.Collection;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MenuTools;

/**
 * Class to handle setting the visibility of menu items based upon a predicate.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
class MenuItemVisibilityManager {
	
	private static final Logger logger = LoggerFactory.getLogger(MenuItemVisibilityManager.class);
	
	private BooleanProperty showExperimentalOptions = PathPrefs.showExperimentalOptionsProperty();
	private BooleanProperty showTMAOptions = PathPrefs.showTMAOptionsProperty();
	private BooleanProperty showLegacyOptions = PathPrefs.showLegacyOptionsProperty();
	
	private BooleanProperty ignorePredicateProperty = new SimpleBooleanProperty(false);

	private BooleanProperty includeSubmenusProperty = new SimpleBooleanProperty(true);

	private final Predicate<MenuItem> defaultPredicate = (MenuItem item) -> checkVisibilityFromPreferences(item);
			
	private ObjectProperty<Predicate<MenuItem>> predicateProperty = new SimpleObjectProperty<>(defaultPredicate);
	
	private ObservableList<? extends MenuItem> menuItems;
	
	private boolean excludeMenusAndSeparators = false;
	
	private ChangeListener<Predicate<MenuItem>> predicateChangeListener = (v, o, n) -> updateVisibility();
	private ChangeListener<Boolean> booleanChangeListener = (v, o, n) -> updateVisibility();
	private ListChangeListener<MenuItem> listChangeListener = this::handleListChange;
	
	private MenuItemVisibilityManager(ObservableList<? extends MenuItem> menuItems) {
		this.menuItems = menuItems;
		attachListeners();
		updateVisibility();
	}

	/**
	 * Stop the visibility manager.
	 * Once stopped, it cannot be restarted and a new manager needs to be created instead.
	 * <p>
	 * Note that this does not change the visibility status of an items.
	 */
	public void stop() {
		if (menuItems == null) {
			logger.warn("Menu visibility manager is not running!");
			return;
		}
		detachListeners();
		menuItems = null;
		logger.info("Menu visibility manager stopped");
	}
	
	private void attachListeners() {
		predicateProperty.addListener(predicateChangeListener);
		ignorePredicateProperty.addListener(booleanChangeListener);
		includeSubmenusProperty.addListener(booleanChangeListener);
		showExperimentalOptions.addListener(booleanChangeListener);
		showTMAOptions.addListener(booleanChangeListener);
		showLegacyOptions.addListener(booleanChangeListener);
		menuItems.addListener(listChangeListener);
	}
	
	private void detachListeners() {
		menuItems.removeListener(listChangeListener);
		ignorePredicateProperty.removeListener(booleanChangeListener);
		includeSubmenusProperty.removeListener(booleanChangeListener);
		showExperimentalOptions.removeListener(booleanChangeListener);
		predicateProperty.removeListener(predicateChangeListener);
		showTMAOptions.removeListener(booleanChangeListener);
		showLegacyOptions.removeListener(booleanChangeListener);
	}
	
	
	/**
	 * Create a visibility manager to control the visibility of all items in a menubar (recursively).
	 * @param menubar
	 * @return
	 */
	public static MenuItemVisibilityManager createMenubarVisibilityManager(MenuBar menubar) {
		return createMenubarVisibilityManager(menubar.getMenus());
	}
	
	/**
	 * Create a visibility manager to control the visibility of all items in an observable list.
	 * 
	 * @param items
	 * @return
	 */
	public static MenuItemVisibilityManager createMenubarVisibilityManager(ObservableList<? extends MenuItem> items) {
		return new MenuItemVisibilityManager(items);
	}
	
	
	private void handleListChange(Change<? extends MenuItem> c) {
		while (c.next()) {
			var items = c.getAddedSubList();
			if (getIncludeSubmenus()) {
				items = MenuTools.getFlattenedMenuItems(items, excludeMenusAndSeparators);
			}
			updateVisibilityForItems(items);
		}
	}
	
	
	private boolean computeVisibility(MenuItem item) {
		if (ignorePredicateProperty.get())
			return true;
		var predicate = predicateProperty.get();
		if (predicate == null)
			return true;
		else
			return predicate.test(item);
	}
	
	/**
	 * Get the default predicate.
	 * Currently, this uses the preferences to determine the visibility of menu items based on 
	 * their names (e.g. 'experimental', 'TMA' or 'legacy' items).
	 * <p>
	 * This can be combined with other predicates via {@code .and(Predicate)} 
	 * or {@code .or(Predicate)}.
	 * @return the default predict (must be non-null)
	 */
	public Predicate<MenuItem> getDefaultPredicate() {
		return defaultPredicate;
	}
	
	/**
	 * Property containing the predicate used to determine if a MenuItem should be 
	 * set as visible or not.
	 * @return
	 */
	public ObjectProperty<Predicate<MenuItem>> predicateProperty() {
		return predicateProperty;
	}
	
	/**
	 * Get the value of {@link #predicateProperty()}
	 * @return
	 */
	public Predicate<MenuItem> getPredicate() {
		return predicateProperty.get();
	}

	/**
	 * Set the value of {@link #predicateProperty()}
	 * @param predicate
	 */
	public void setPredicate(Predicate<MenuItem> predicate) {
		predicateProperty.set(predicate);
	}

	/**
	 * Property specifying that the predicate should be ignored.
	 * <p>
	 * When set to 'true', this sets all MenuItems with unbound 'visible' properties to be visible.
	 * This is necessary to avoid exceptions and even segfaults, which can arise when menus are being 
	 * initialized or modified.
	 * <p>
	 * <b>Important!</b> This should generally not be modified directly, and may be bound to properties 
	 * in the main QuPath UI class.
	 * @return
	 */
	public BooleanProperty ignorePredicateProperty() {
		return ignorePredicateProperty;
	}
	
	/**
	 * Set the value of {@link #ignorePredicateProperty()}
	 * @param ignore
	 */
	public void setIgnorePredicate(boolean ignore) {
		ignorePredicateProperty().set(ignore);
	}

	/**
	 * Get the value of {@link #ignorePredicateProperty()}
	 * @return
	 */
	public boolean getIgnorePredicate() {
		return ignorePredicateProperty().get();
	}
	
	/**
	 * Property specifying that submenus should be included recursively.
	 * 
	 * @return
	 */
	public BooleanProperty includeSubmenusProperty() {
		return includeSubmenusProperty;
	}
	
	/**
	 * Get the value of {@link #includeSubmenusProperty()}
	 * @return
	 */
	public boolean getIncludeSubmenus() {
		return includeSubmenusProperty().get();
	}
	
	/**
	 * Set the value of {@link #includeSubmenusProperty()}
	 * @param include
	 */
	public void setIncludeSubmenus(boolean include) {
		includeSubmenusProperty().set(include);
	}

	private boolean checkVisibilityFromPreferences(MenuItem item) {
		String text = item.getText();
		if (text == null)
			return true;
		text = text.trim();
		String lowerText = text.toLowerCase();
		if (!showExperimentalOptions.get() && (lowerText.equals("experimental") || lowerText.endsWith("experimental)")))
			return false;
		if (!showTMAOptions.get() && (text.equals("TMA") || text.endsWith("TMA)"))) // TMA should always be capitalized (to avoid false matches)
			return false;
		if (!showLegacyOptions.get() && (lowerText.equals("deprecated") || lowerText.endsWith("deprecated") || lowerText.equals("legacy") || lowerText.endsWith("legacy)")))
			return false;
		return true;
	}
	
	private void updateVisibility() {
		if (menuItems == null) {
			logger.debug("No menu items!");
			return;
		}
		var items = getIncludeSubmenus() ? MenuTools.getFlattenedMenuItems(menuItems, excludeMenusAndSeparators) : menuItems;
		updateVisibilityForItems(items);
	}

	private void updateVisibilityForItems(Collection<? extends MenuItem> items) {
		try {
			for (var item : items) {
				if (!item.visibleProperty().isBound()) {
					item.setVisible(computeVisibility(item));
				}
			}
		} catch (Exception e) {
			logger.warn("Error setting menu visibility: {}", e.getLocalizedMessage());
			logger.warn("", e);
		}
	}
			
}