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
public class SelectionManager<T> {

	final private ObjectProperty<T> selected;
	final private BooleanProperty itemSelected = new SimpleBooleanProperty();
	final private T item;
	
	final ChangeListener<T> selectedListener;
	final ChangeListener<Boolean> itemSelectedListener;
	
	public SelectionManager(final ObjectProperty<T> selected, final T item) {
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

	public boolean isSelected() {
		return selected.get() == item;
	}

	public void setSelected(boolean selected) {
		if (selected)
			this.selected.set(item);
	}
	
	public BooleanProperty selectedProperty() {
		return itemSelected;
	}
	
	public T getItem() {
		return this.item;
	}
	
}