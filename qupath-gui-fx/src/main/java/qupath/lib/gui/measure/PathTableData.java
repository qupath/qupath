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

package qupath.lib.gui.measure;

import java.util.List;
import java.util.Locale;

import qupath.lib.objects.PathObject;

/**
 * Interface defining a table model that enables measurement names to be mapped to string or numeric values (as appropriate).
 * <p>
 * This can be thought of a table, where items (often {@link PathObject} correspond to rows and named columns either return 
 * numeric or {@link String} data.
 * <p>
 * This provides a useful method of wrapping one or more objects, and providing access to metadata, stored measurements and dynamically computed values 
 * in a way that is amenable to display within a table.
 * 
 * @author Pete Bankhead
 * @param <T> 
 *
 */
public interface PathTableData<T> {
	
	/**
	 * Return an ordered list of all names, including both numeric measurements and {@link String} values.
	 * 
	 * @return
	 */
	public List<String> getAllNames();

	/**
	 * Get a string representation of the value.
	 * 
	 * For this method, numbers should be formatted according to the {@link Locale}.
	 * 
	 * @param item
	 * @param name
	 * @return
	 */
	public String getStringValue(final T item, final String name);

	/**
	 * Get a string value, converting to a fixed number of decimal places if the column is numeric.
	 * 
	 * @param item
	 * @param name
	 * @param decimalPlaces
	 * @return
	 */
	public String getStringValue(final T item, final String name, final int decimalPlaces);

	/**
	 * Get the names of all numeric measurements.
	 * @return
	 */
	public List<String> getMeasurementNames();

	/**
	 * Get the numeric value from an object for the specific measurement.
	 * @param pathObject
	 * @param column
	 * @return
	 */
	public double getNumericValue(final T pathObject, final String column);

	/**
	 * Get all double values for all items.
	 * 
	 * @param column
	 * @return
	 */
	public double[] getDoubleValues(final String column);
	
	/**
	 * Get internal list of the items used to provide measurements.
	 * 
	 * @return
	 */
	public List<T> getItems();

	
}