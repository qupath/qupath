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

package qupath.lib.measurements;

/**
 * Factory for creating new measurement lists.
 * <p>
 * For efficiently, (static) Float lists are generally preferred for detection objects.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementListFactory {
	
	private MeasurementListFactory() {}

	/**
	 * Create a measurement list.
	 * @param capacity
	 * @param type
	 * @return
	 */
	public static MeasurementList createMeasurementList(int capacity, MeasurementList.MeasurementListType type) {
		switch (type) {
		case DOUBLE:
			return new NumericMeasurementList.DoubleList(capacity);
		case FLOAT:
			return new NumericMeasurementList.FloatList(capacity);
		case GENERAL:
		default:
			return new DefaultMeasurementList(capacity);
		}
	}
}
