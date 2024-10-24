/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

/**
 * Interface defining a feature measurement list, consisting of key value pairs.
 * <p>
 * To help enable efficiency for large sets of PathObjects requiring measurement lists,
 * only String keys and numeric values are included.
 * <p>
 * In QuPath v0.4.0 several methods were deprecated, and these were removed in v0.6.0.
 * The main aim was to make the API more consistent and easier to use.
 * 
 * @author Pete Bankhead
 *
 */
public interface MeasurementList extends Serializable, AutoCloseable {
	
	/**
	 * Enum representing different types of measurement list, with different priorities regarding 
	 * flexibility and efficiency.
	 */
	enum MeasurementListType {
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
	 * Put a measurement value into the list, replacing any previous measurement with the same name.
	 * <p>
	 * This is similar to adding, but with a check to remove any existing measurement with the same name
	 * (if multiple measurements have the same name, the first will be replaced).
	 * 
	 * @param name
	 * @param value
	 * @since v0.4.0
	 */
	void put(String name, double value);

	/**
	 * Get an unmodifiable list of all measurements.
	 * This provides a snapshot of the current measurements, and should not be affected by changes to the list.
	 * @return
	 */
	List<Measurement> getMeasurements();

	/**
	 * Get an immutable representation of a single measurement.
	 * This provides a snapshot of the current measurement, and should not be affected by changes to the list.
	 * @return
	 */
	Measurement getByIndex(int ind);

	/**
	 * Get the specified measurement, or the provided default value if it is not contained in the list.
	 * <p>
	 * This provides an alternative to {@link #get(String)} which always uses a default of {@code Double.NaN}.
	 * 
	 * @param name
	 * @param defaultValue
	 * @return
	 * @since v0.4.0
	 */
	default double getOrDefault(String name, double defaultValue) {
		synchronized (this) {
			double val = get(name);
			if (Double.isNaN(val)) {
				if (Double.isNaN(defaultValue) || containsKey(name))
					return val;
				else
					return defaultValue;
			}
			return val;
		}
	}
	
	/**
	 * Get a snapshot of all measurement values as a double array.
	 * Changes to the array will not impact the measurement list.
	 * @return
	 * @since v0.4.0
	 */
	double[] values();
	
	/**
	 * Remove a named measurement
	 * @param name
	 * @return the value that was removed, or Double.NaN if the value was not in the list
	 * @since v0.4.0
	 */
	double remove(String name);
	
	/**
	 * Put all the values from the specified map into this list
	 * @param map
	 * @since v0.4.0
	 */
	default void putAll(Map<String, ? extends Number> map) {
		for (var entry : map.entrySet()) {
			put(entry.getKey(), entry.getValue().doubleValue());			
		}
	}
	
	/**
	 * Put all the measurements from the specified list into this one
	 * @param list
	 * @since v0.4.0
	 */
	default void putAll(MeasurementList list) {
		putAll(list.getMeasurements());
	}

	/**
	 * Put all the measurements from the specified list into this one
	 * @param list
	 * @since v0.4.0
	 */
	default void putAll(Collection<? extends Measurement> list) {
		synchronized (this) {
			for (var measurement : list) {
				put(measurement.getName(), measurement.getValue());
			}
		}
	}
	
	/**
	 * Get all available names as a set.
	 * @return
	 * @implNote the current implementation is much less efficient than {@link #getNames()},
	 *           but is included to more closely resemble Map behavior.
	 *           The list of names and size of the returned set here should be identical; if they aren't, 
	 *           duplicate names seem to be present and a warning is logged.
	 *           This <i>shouldn't</i> be possible with new code, but could conceivably occur if a list from 
	 *           a pre-v0.4.0 QuPath version is deserialized (or there is a bad bug somewhere here - if so, 
	 *           please report it!).
	 */
	default Set<String> keySet() {
		var names = getNames();
		var set = Set.copyOf(getNames());
		// Shouldn't ever happen, but we certainly want to know if it does...
		if (set.size() < names.size()) {
			LoggerFactory.getLogger(getClass()).warn("Duplicate measurement names detected! Set size {}, list size {}", set.size(), names.size());
		}
		return set;
	}

	/**
	 * Get the names of all measurements currently in the list.
	 * Note that this method should return an unmodifiable snapshot of the current names, and not be affected by
	 * changes to the list.
	 * @return
	 */
	List<String> getNames();

	/**
	 * Get the names of all measurements currently in the list.
	 * Note that this method should return an unmodifiable snapshot of the current names, and not be affected by
	 * changes to the list.
	 * @return
	 * @deprecated v0.6.0 use {@link #getNames()} instead
	 */
	@Deprecated
	default List<String> getMeasurementNames() {
		return getNames();
	}

	/**
	 * Get value for the measurement with the specified name.
	 * @param name
	 * @return the measurement value, or Double.NaN if the measurement is not available
	 * @see #put(String, double)
	 * @since v0.4.0
	 */
	double get(String name);

	/**
	 * Returns true if this list contains a measurement with the specified name.
	 * @param name
	 * @return
	 * @since v0.4.0
	 */
	default boolean containsKey(String name) {
		return getNames().contains(name);
	}

	/**
	 * Returns true if the list does not contain any measurements.
	 * @return
	 */
	default boolean isEmpty() {
		return size() == 0;
	}
	
	/**
	 * Returns the number of measurements in the list.
	 * @return
	 */
	default int size() {
		return getNames().size();
	}
	
	/**
	 * Close the list.
	 * Depending on the implementation, the list may then adjust its internal storage to be
	 * more efficient.
	 */
	@Override
	default void close() {
		// Default implementation does nothing
	}
	
	/**
	 * Remove all the measurements with the specified names.
	 * @param measurementNames
	 */
	void removeAll(String... measurementNames);

	/**
	 * Remove all the measurements with the specified names.
	 * @param measurementNames
	 * @deprecated v0.6.0 use {@link #removeAll(String...)} instead
	 */
	@Deprecated
	default void removeMeasurements(String... measurementNames) {
		removeAll(measurementNames);
	}
	
	/**
	 * Remove all the measurements from the list.
	 */
	void clear();
	
	/**
	 * Get a map view of this measurements list. 
	 * This is a map that is backed by the list, which means that putting or retrieving elements 
	 * modifies the list. It also means that there can be some loss of precision if the list does 
	 * not support Double (e.g. it uses a float array for storage).
	 * <p>
	 * @return a map view of this measurement list
	 * @implSpec The returned map should already be synchronized.
	 */
	Map<String, Number> asMap();


}
