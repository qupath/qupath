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

package qupath.lib.gui.models;

import java.util.List;

/**
 * Interface defining a table model that enables measurement names to be mapped to string or numeric values (as appropriate).
 * 
 * This provides a useful method of wrapping one or more objects, and providing access to metadata, stored measurements and dynamically computed values 
 * in a way that is amenable to display within a table.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathTableData<T> {
	
	/**
	 * Return an ordered list of all names.
	 * 
	 * @return
	 */
	public List<String> getAllNames();

	/**
	 * Get a string representation of the value.
	 * 
	 * For this method, numbers should be formatted according to the Locale.
	 * 
	 * @param pathObject
	 * @param column
	 * @return
	 */
	public String getStringValue(final T pathObject, final String column);

	/**
	 * Get a string value, converting to a fixed number of decimal places if the column is numeric.
	 * 
	 * @param pathObject
	 * @param column
	 * @param decimalPlaces
	 * @return
	 */
	public String getStringValue(final T pathObject, final String column, final int decimalPlaces);

	public List<String> getMeasurementNames();

	public double getNumericValue(final T pathObject, final String column);

	/**
	 * Get all double values for a list of PathObjects.
	 * 
	 * @param column
	 * @return
	 */
	public double[] getDoubleValues(final String column);
	
	/**
	 * Get internal list of the entries used to provide measurements.
	 * 
	 * @return
	 */
	public List<T> getEntries();

	
}