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

package qupath.lib.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ToggleGroup;

/**
 * Helper class for managing items when only one of them may be selected.
 * This is similar to a {@link ToggleGroup}, but without a dependency on any GUI components.
 * 
 * @param <T>
 */
public class SelectableItem<T> {

	final private ObjectProperty<T> selected;
	final private BooleanProperty itemSelected = new SimpleBooleanProperty();
	final private T item;
	
	final private ChangeListener<T> selectedListener;
	final private ChangeListener<Boolean> itemSelectedListener;
	
	/**
	 * Constructor.
	 * @param selected the property that identifies which item is currently selected
	 * @param item the current item to be wrapped within this class, and which may or may not be selected
	 */
	public SelectableItem(final ObjectProperty<T> selected, final T item) {
		this.selected = selected;
		this.item = item;
		selectedListener = ((v, o, n) -> {
			itemSelected.set(n == item);
		});
		itemSelectedListener = ((v, o, n) -> {
			if (n)
				selected.set(item);
		});
		this.selected.addListener(selectedListener);
		this.itemSelected.addListener(itemSelectedListener);
	}

	/**
	 * Returns true if the value of the selected property equals {@link #getItem()}.
	 * @return
	 */
	public boolean isSelected() {
		return selected.get() == item;
	}

	/**
	 * Set the item to be selected.
	 * @param selected
	 */
	public void setSelected(boolean selected) {
		if (selected)
			this.selected.set(item);
	}
	
	/**
	 * Property representing the item that has been selected (which may or may not be the same as {@link #getItem()}).
	 * @return
	 */
	public BooleanProperty selectedProperty() {
		return itemSelected;
	}
	
	/**
	 * Get the current item.
	 * @return
	 */
	public T getItem() {
		return this.item;
	}
	
}