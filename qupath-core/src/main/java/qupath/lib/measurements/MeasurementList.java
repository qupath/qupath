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

import java.io.Serializable;
import java.util.List;

import org.slf4j.LoggerFactory;

import qupath.lib.common.LogTools;

/**
 * Interface defining a feature measurement list, consisting of key value pairs.
 * <p>
 * To help enable efficiency for large sets of PathObjects requiring measurement lists,
 * only String keys and numeric values are included.
 * 
 * @author Pete Bankhead
 *
 */
public interface MeasurementList extends Serializable, AutoCloseable {
	
	/**
	 * Enum representing different types of measurement list, with different priorities regarding 
	 * flexibility and efficiency.
	 */
	public enum MeasurementListType {
		/**
		 * A general list, which can contain any kind of measurement - at the expense of 
		 * being rather memory-hungry.
		 */
		GENERAL,
		/**
		 * A list backed by an array of doubles.
		 */
		DOUBLE,
		/**
		 * A list backed by an array of floats.
		 */
		FLOAT
	}
	
	/**
	 * Add a new measurement. No check is made to ensure the name is unique, and 
	 * in general {@link #putMeasurement(String, double)} is to be preferred.
	 * @param name
	 * @param value
	 * @return
	 * 
	 * @see #putMeasurement(String, double)
	 * @deprecated v0.4.0 use {@link #putMeasurement(String, double)} instead
	 * @implNote Since v0.4.0 the default implementation delegates to {@link #putMeasurement(String, double)}, 
	 *           and will therefore replace any existing value with the same name.
	 *           This different behavior is introduced to facilitate moving measurement lists towards 
	 *           a map implementation for improved performance, consistency and scripting.
	 */
	@Deprecated
	public default boolean addMeasurement(String name, double value) {
		synchronized (this) {
			boolean contains = containsNamedMeasurement(name);
			var logger = LoggerFactory.getLogger(getClass());
			if (contains) {
				logger.warn("Duplicate '{}' not allowed - previous measurement will be dropped (duplicate names no longer permitted since v0.4.0)", name);
				logger.warn("MeasurementList.addMeasurement(String, double) is deprecated in QuPath v0.4.0 - calling putMeasurement(String, double) instead");
			} else {
				LogTools.warnOnce(logger,
						"MeasurementList.addMeasurement(String, double) is deprecated in QuPath v0.4.0 - calling putMeasurement(String, double) instead");
			}
			putMeasurement(name, value);
			return contains;
		}
	}
	
	/**
	 * Put a measurement into the list, replacing any previous measurement with the same name.
	 * <p>
	 * This is similar to add, but with a check to remove any existing measurement with the same name
	 * (if multiple measurements have the same name, the first will be replaced).
	 * <p>
	 * While it's probably a good idea for measurements to always have unique names, for some implementations
	 * putMeasurement can be must slower than add or addMeasurement - so adding should be preferred if it is
	 * known that a measurement with the same name is not present.
	 * 
	 * @param measurement
	 * @return
	 * @deprecated since v0.4.0, since there is no real need to create a {@link Measurement} object and 
	 *             we don't currently use dynamic measurements
	 */
	@Deprecated
	public Measurement putMeasurement(Measurement measurement);
	
	/**
	 * Put a measurement value into the list, replacing any previous measurement with the same name.
	 * <p>
	 * This is similar to add, but with a check to remove any existing measurement with the same name
	 * (if multiple measurements have the same name, the first will be replaced).
	 * <p>
	 * While it's probably a good idea for measurements to always have unique names, for some implementations
	 * putMeasurement can be must slower than add or addMeasurement - so adding should be preferred if it is
	 * known that a measurement with the same name is not present.
	 * 
	 * @param name
	 * @param value
	 */
	public void putMeasurement(String name, double value);

	/**
	 * Get the names of all measurements currently in the list.
	 * @return
	 */
	public List<String> getMeasurementNames();

	/**
	 * Get name for the measurement at the specified index in the list.
	 * @param ind
	 * @return
	 */
	public String getMeasurementName(int ind);

	/**
	 * Get value for the measurement at the specified index in the list.
	 * @param ind
	 * @return
	 */
	public double getMeasurementValue(int ind);

	/**
	 * Get value for the measurement with the specified name.
	 * Note that the behavior is undefined if multiple measurements have the same name.
	 * @param name
	 * @return
	 * 
	 * @see #putMeasurement(String, double)
	 */
	public double getMeasurementValue(String name);

	/**
	 * Returns true if this list contains a measurement with the specified name.
	 * @param name
	 * @return
	 */
	public boolean containsNamedMeasurement(String name);

	/**
	 * Returns true if the list does not contain any measurements.
	 * @return
	 */
	public boolean isEmpty();
	
	/**
	 * Returns the number of measurements in the list.
	 * @return
	 */
	public int size();
	
	/**
	 * Returns true if the list supports dynamic measurements. 
	 * Dynamic measurements can change their values, and in the interests of efficiency 
	 * are not supported by all MeasurementList implementations.
	 * <p>
	 * Use of this is strongly discouraged.
	 * 
	 * @return
	 */
	public boolean supportsDynamicMeasurements();
	
	/**
	 * Close the list. Depending on the implementation, the list may then adjust its internal storage to be
	 * more efficient.
	 */
	@Override
	public void close();
	
	/**
	 * Remove all the measurements with the specified names.
	 * @param measurementNames
	 */
	public void removeMeasurements(String...measurementNames);
	
	/**
	 * Remove all the measurements from the list.
	 */
	public void clear();

}
