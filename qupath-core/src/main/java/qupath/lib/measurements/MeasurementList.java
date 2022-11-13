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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import qupath.lib.common.LogTools;

/**
 * Interface defining a feature measurement list, consisting of key value pairs.
 * <p>
 * To help enable efficiency for large sets of PathObjects requiring measurement lists,
 * only String keys and numeric values are included.
 * <p>
 * <b>QuPath v0.4.0: </b> MeasurementList was updated to have more map-like behavior, 
 * while still using primitive values. In particular, {@link #addMeasurement(String, double)} 
 * was deprecated and now simply defers to {@link #put(String, double)}.
 * <p>
 * Additionally, the wordy {@link #putMeasurement(String, double)} and {@link #getMeasurementValue(String)} 
 * were deprecated in favor of {@link #put(String, double)} and {@link #get(String)} - which do the same thing, 
 * but with more familiar syntax.
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
	 * in general {@link #put(String, double)} is to be preferred.
	 * @param name
	 * @param value
	 * @return
	 * 
	 * @see #put(String, double)
	 * @deprecated v0.4.0 use {@link #put(String, double)} instead
	 * @implNote Since v0.4.0 the default implementation delegates to {@link #put(String, double)}, 
	 *           and will therefore replace any existing value with the same name.
	 *           This different behavior is introduced to facilitate moving measurement lists towards 
	 *           a map implementation for improved performance, consistency and scripting.
	 */
	@Deprecated
	public default boolean addMeasurement(String name, double value) {
		synchronized (this) {
			boolean contains = containsKey(name);
			var logger = LoggerFactory.getLogger(getClass());
			if (contains) {
				logger.warn("Duplicate '{}' not allowed - previous measurement will be dropped (duplicate names no longer permitted since v0.4.0)", name);
				logger.warn("MeasurementList.addMeasurement(String, double) is deprecated in QuPath v0.4.0 - calling putMeasurement(String, double) instead");
			} else {
				LogTools.warnOnce(logger,
						"MeasurementList.addMeasurement(String, double) is deprecated in QuPath v0.4.0 - calling putMeasurement(String, double) instead");
			}
			put(name, value);
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
	 * This is similar to adding, but with a check to remove any existing measurement with the same name
	 * (if multiple measurements have the same name, the first will be replaced).
	 * 
	 * @param name
	 * @param value
	 * @since v0.4.0
	 */
	public void put(String name, double value);
	
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
	public default double getOrDefault(String name, double defaultValue) {
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
	 * Query if a value with the specified name is in the list.
	 * @param name
	 * @return
	 * @deprecated since v0.4.0; replaced by {@link #containsKey(String)}
	 */
	@Deprecated
	public default boolean containsNamedMeasurement(String name) {
		return containsKey(name);
	}
	
	/**
	 * Get all measurement values as a double array
	 * @return
	 * @since v0.4.0
	 */
	public default double[] values() {
		synchronized(this) {
			double[] values = new double[size()];
			for (int i = 0; i < size(); i++)
				values[i] = getMeasurementValue(i);
			return values;
		}
	}
	
	/**
	 * Get the measurement with the specified name.
	 * @param name
	 * @return the value, or Double.NaN if no measurement is available with the specified name
	 * @deprecated since v0.4.0; use {@link #get(String)} instead
	 */
	@Deprecated
	public default double getMeasurementValue(String name) {
		return get(name);
	}
	
	/**
	 * Alternative method to call {@link #putMeasurement(String, double)}
	 * @param name
	 * @param value 
	 * @deprecated since v0.4.0; replaced by {@link #put(String, double)}
	 */
	@Deprecated
	public default void putMeasurement(String name, double value) {
		put(name, value);
	}
	
	/**
	 * Remove a named measurement
	 * @param name
	 * @return the value that was removed, or Double.NaN if the value was not in the list
	 * @since v0.4.0
	 */
	public default double remove(String name) {
		synchronized (this) {
			int sizeBefore = size();
			int ind = getMeasurementNames().indexOf(name);
			double val = Double.NaN;
			if (ind >= 0) {
				val = getMeasurementValue(ind);
				removeMeasurements(name);
			}
			assert sizeBefore == size() + 1;
			return val;
		}
	}
	
	/**
	 * Put all the values from the specified  map into this list
	 * @param map
	 * @since v0.4.0
	 */
	public default void putAll(Map<String, ? extends Number> map) {
		for (var entry : map.entrySet()) {
			put(entry.getKey(), entry.getValue().doubleValue());			
		}
	}
	
	/**
	 * Put all the values from the specified list into this one
	 * @param list
	 * @since v0.4.0
	 */
	public default void putAll(MeasurementList list) {
		synchronized (list) {
			for (String name : list.getMeasurementNames()) {
				put(name, list.getMeasurementValue(name));			
			}
		}
	}
	
	/**
	 * Get all available names as a set.
	 * @return
	 * @implNote the current implementation is much less efficient than {@link #getMeasurementNames()}, 
	 *           but is included to more closely resemble Map behavior.
	 *           The list of names and size of the returned set here should be identical; if they aren't, 
	 *           duplicate names seem to be present and a warning is logged.
	 *           This <i>shouldn't</i> be possible with new code, but could conceivably occur if a list from 
	 *           a pre-v0.4.0 QuPath version is deserialized (or there is a bad bug somewhere here - if so, 
	 *           please report it!).
	 */
	public default Set<String> keySet() {
		var names = getMeasurementNames();
		var set = new LinkedHashSet<>(names);
		// Shouldn't happen now that addMeasurements is ineffective... but conceivably could with legacy lists
		if (set.size() < names.size()) {
			LoggerFactory.getLogger(getClass()).warn("Duplicate measurement names detected! Set size {}, list size {}", set.size(), names.size());
		}
		return Collections.unmodifiableSet(set);
	}

	/**
	 * Get the names of all measurements currently in the list.
	 * @return
	 */
	public List<String> getMeasurementNames();

	/**
	 * Get name for the measurement at the specified index in the list.
	 * @param ind
	 * @return
	 * @deprecated since v0.4.0; using names is preferred over indexing but {@link #getMeasurementNames()} can still be used
	 */
	@Deprecated
	public String getMeasurementName(int ind);

	/**
	 * Get value for the measurement at the specified index in the list.
	 * @param ind
	 * @return
	 * @deprecated since v0.4.0; using {@link #get(String)} is preferred over using an index
	 */
	@Deprecated
	public double getMeasurementValue(int ind);

	/**
	 * Get value for the measurement with the specified name.
	 * @param name
	 * @return the measurement value, or Double.NaN if the measurement is not available
	 * @see #put(String, double)
	 * @since v0.4.0
	 */
	public double get(String name);

	/**
	 * Returns true if this list contains a measurement with the specified name.
	 * @param name
	 * @return
	 * @since v0.4.0
	 */
	public boolean containsKey(String name);

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
	 * @deprecated since v0.4.0; the initial implementation of dynamic measurements was never used
	 */
	@Deprecated
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
	public void removeMeasurements(String... measurementNames);
	
	/**
	 * Remove all the measurements from the list.
	 */
	public void clear();
	
	/**
	 * Get a map view of this measurements list. 
	 * This is a map that is backed by the list, which means that putting or retrieving elements 
	 * modifies the list. It also means that there can be some loss of precision if the list does 
	 * not support Double (e.g. it uses a float array for storage).
	 * <p>
	 * @return a map view of this measurement list
	 * @implSpec The returned map should already be synchronized.
	 */
	public default Map<String, Double> asMap() {
		return Collections.synchronizedMap(new MeasurementsMap(this));
	}
	
//	/**
//	 * Return a Map view over this list. Changes made to the map will be stored in the list.
//	 * @return
//	 */
//	public Map<String, Double> asMap();
	

}
