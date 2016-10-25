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

package qupath.lib.gui.tma.cells;

import javafx.geometry.Pos;
import javafx.scene.control.TreeTableCell;

/**
 * A TableCell with centered text.
 * 
 * @author Pete Bankhead
 *
 * @param <S>
 * @param <T>
 */
public class BasicTableCell<S, T> extends TreeTableCell<S, T> {

	public BasicTableCell() {
		setAlignment(Pos.CENTER);
	}

	@Override
	protected void updateItem(T item, boolean empty) {
		super.updateItem(item, empty);
		if (item == null || empty) {
			setText(null);
			setGraphic(null);
			return;
		}
		setText(item.toString());
	}

}