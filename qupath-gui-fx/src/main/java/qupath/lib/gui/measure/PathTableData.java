/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.slf4j.LoggerFactory;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathObject;

/**
 * Interface defining a table model that enables measurement names to be mapped to string or numeric values (as appropriate).
 * <p>
 * This can be thought of a table, where items (often {@link PathObject} correspond to rows and named columns either return 
 * numeric or {@link String} data.
 * <p>
 * This provides a useful method of wrapping one or more objects, and providing access to metadata, stored measurements
 * and dynamically computed values in a way that is amenable to display within a table.
 * 
 * @author Pete Bankhead
 * @param <T> 
 *
 */
public interface PathTableData<T> {

	/**
	 * The default number of decimal places when converting floating point values to strings.
	 * This is permitted to change the number of decimal places based upon the magnitude of the value.
	 */
	int DEFAULT_DECIMAL_PLACES = LazyValue.DEFAULT_DECIMAL_PLACES;

	/**
	 * The default delimiter to use.
	 * This is a tab, to avoid confusion between decimal separators in different locales.
	 */
	String DEFAULT_DELIMITER = "\t";

	/**
	 * Return an ordered list of all names, including both numeric measurements and {@link String} values.
	 * 
	 * @return
	 * @see #getMeasurementNames()
	 */
	List<String> getAllNames();

	/**
	 * Get a string representation of the value.
	 * <p>
	 * For this method, numbers should be formatted according to the {@link Locale}.
	 * 
	 * @param item
	 * @param name
	 * @return
	 */
	String getStringValue(final T item, final String name);

	/**
	 * Get a string value, converting to a fixed number of decimal places if the column is numeric.
	 * 
	 * @param item
	 * @param name
	 * @param decimalPlaces
	 * @return
	 */
	String getStringValue(final T item, final String name, final int decimalPlaces);

	/**
	 * Get the names of all numeric measurements.
	 * @return
	 * @see #getAllNames()
	 */
	List<String> getMeasurementNames();

	/**
	 * Get the numeric value from an item for the specific measurement.
	 * @param item
	 * @param name
	 * @return
	 */
	double getNumericValue(final T item, final String name);

	/**
	 * Get all double values for all items.
	 * 
	 * @param name
	 * @return
	 */
	double[] getDoubleValues(final String name);
	
	/**
	 * Get internal list of the items used to provide measurements.
	 * 
	 * @return
	 */
	List<T> getItems();


	/**
	 * Get a list of Strings representing table data for all items.
	 * <p>
	 * This will use {@link #DEFAULT_DELIMITER} and {@link #DEFAULT_DECIMAL_PLACES},
	 * with no column filter.
	 * @return a list of strings, with the first item giving the column names and each additional string representing
	 * 	       a row in the table
	 * @since v0.6.0
	 */
	default List<String> getRowStrings() {
		return getRowStrings(DEFAULT_DELIMITER, DEFAULT_DECIMAL_PLACES, null);
	}

	/**
	 * Get a list of Strings representing table data for all items.
	 * <p>
	 * Each entry in the list corresponds to a row.
	 * <p>
	 * This is equivalent to calling {@code getRowStrings(getItems(), delim, nDecimalPlaces, columnFilter)}.
	 *
	 * @param delim the delimiter to use between columns
	 * @param nDecimalPlaces the number of decimal places to use for numeric values
	 * @param columnFilter a predicate to choose which columns to include; if null, all columns from the table are used
	 * @return a list of strings, with the first item giving the column names and each additional string representing
	 * 	       a row in the table
	 * @since v0.6.0
	 */
	default List<String> getRowStrings(final String delim, int nDecimalPlaces, Predicate<String> columnFilter) {
		return getRowStrings(getItems(), delim, nDecimalPlaces, columnFilter);
	}

	/**
	 * Get a list of Strings representing table data for specific items.
	 * <p>
	 * Each entry in the list corresponds to a row.
	 *
	 * @param items the items to use; one row will be returned for each
	 * @param delim the delimiter to use between columns
	 * @param nDecimalPlaces the number of decimal places to use for numeric values; if negative, the default number of decimal places is used
	 * @param columnFilter a predicate to choose which columns to include; if null, all columns from the table are used
	 * @return a list of strings, with the first item giving the column names and each additional string representing
	 * 	       a row in the table
	 * @since v0.6.0
	 */
	default List<String> getRowStrings(Collection<? extends T> items, String delim, int nDecimalPlaces, Predicate<String> columnFilter) {

		var logger = LoggerFactory.getLogger(PathTableData.class);

		List<String> rows = new ArrayList<>();
		StringBuilder sb = new StringBuilder();

		List<String> names = new ArrayList<>(getAllNames());
		if (columnFilter != null)
			names = names.stream().filter(columnFilter).toList();

		int nColumns = names.size();
		for (int col = 0; col < nColumns; col++) {
			if (names.get(col).chars().filter(e -> e == '"').count() % 2 != 0)
				logger.warn("Syntax is ambiguous (i.e. misuse of '\"'), which might result in inconsistencies/errors.");
			if (names.get(col).contains(delim))
				sb.append("\"")
						.append(names.get(col))
						.append("\"");
			else
				sb.append(names.get(col));

			if (col < nColumns - 1)
				sb.append(delim);
		}
		rows.add(sb.toString());
		sb.setLength(0);

		for (T object : items) {
			for (int col = 0; col < nColumns; col++) {
				String val = getStringValue(object, names.get(col), nDecimalPlaces);
				if (val != null) {
					if (val.contains("\""))
						logger.warn("Syntax is ambiguous (i.e. misuse of '\"'), which might result in inconsistencies/errors.");
					if (val.contains(delim))
						sb.append("\"")
								.append(val)
								.append("\"");
					else
						sb.append(val);
				}
				if (col < nColumns - 1)
					sb.append(delim);
			}
			rows.add(sb.toString());
			sb.setLength(0);
		}
		return rows;
	}
	
}