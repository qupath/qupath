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
import qupath.lib.common.GeneralTools;

/**
 * A TableCell to display numbers in a formatted way, with decimal places adjusted according to magnitude.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class NumericTableCell<T> extends TreeTableCell<T, Number> {

	@Override
	protected void updateItem(Number item, boolean empty) {
		super.updateItem(item, empty);
		if (item == null || empty) {
			setText(null);
			setStyle("");
		} else {
			setAlignment(Pos.CENTER);
			if (Double.isNaN(item.doubleValue()))
				setText("-");
			else {
				if (item.doubleValue() >= 1000)
					setText(GeneralTools.getFormatter(1).format(item));
				else if (item.doubleValue() >= 10)
					setText(GeneralTools.getFormatter(2).format(item));
				else
					setText(GeneralTools.getFormatter(3).format(item));
			}
		}
	}

}