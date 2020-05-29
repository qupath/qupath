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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * A MeasurementList that stores its measurements in either a float or a double array, 
 * to avoid the overhead of storing large numbers of {@link Measurement} objects.
 * <p>
 * This makes the storage quite efficient for lists that don't require supporting dynamic measurements.
 * <p>
 * In this implementation, lookups by measurement name initially use indexOf with a list - and
 * can be rather slow.  Therefore while 'adding' is fast, 'putting' is not.
 * <p>
 * However, upon calling {@code close()}, name lists are shared between similarly closed NumericMeasurementLists,
 * and a map used to improve random access of measurements.  Therefore if many lists of the same measurements
 * are made, remembering to close each list when it is fully populated can improve performance and greatly
 * reduce memory requirements.
 * <p>
 * These lists can be instantiated through the {@link MeasurementListFactory} class.
 * 
 * @author Pete Bankhead
 *
 */
class NumericMeasurementList {
	
	final private static Logger logger = LoggerFactory.getLogger(NumericMeasurementList.class);
	
	private static Map<List<String>, NameMap> namesPool = Collections.synchronizedMap(new WeakHashMap<>());
	

	private static class NameMap {
		
		private List<String> names;
		private Map<String, Integer> map;
		
		NameMap(List<String> names) {
			this.names = Collections.unmodifiableList(new ArrayList<>(names)); // Make a defensive copy
			createHashMap();
		}
		
		private void createHashMap() {
			map = new HashMap<>();
			int i = 0;
			for (String s : names) {
				map.put(s, i);
				i++;
			}
		}
		
		List<String> getUnmodifiableNames() {
			return names;
		}
		
		Map<String, Integer> getMap() {
			return map;
		}
		
	}
	
	
	
	private static abstract class AbstractNumericMeasurementList implements MeasurementList {
		
		private static final long serialVersionUID = 1L;
		
		protected static final int EXPAND = 8; // Amount by which to expand array as required
		
		List<String> names;
		transient List<String> namesUnmodifiable; // Cache an unmodifiable list so we can return the same one
		boolean isClosed = false;

		private Map<String, Integer> map; // Optional map for fast measurement lookup

		AbstractNumericMeasurementList(int capacity) {
			names = new ArrayList<>(capacity);
			namesUnmodifiable = null;
		}
		
		/**
		 * Set the value at the specified list index
		 * 
		 * @param index
		 * @param value
		 */
		protected abstract void setValue(int index, double value);
		
		boolean isClosed() {
			return isClosed;
		}

		@Override
		public void close() {
			if (isClosed())
				return;
			compactStorage();
			// Try to get a shared list & map
			NameMap nameMap = getNameMap();				
			this.names = nameMap.getUnmodifiableNames();
			this.namesUnmodifiable = names; // NameMap always returns an unmodifiable list
			this.map = nameMap.getMap();
			isClosed = true;
		}
		
		
		private NameMap getNameMap() {
			NameMap nameMap = namesPool.get(names);
			if (nameMap != null)
				return nameMap;
			synchronized(namesPool) {
				nameMap = namesPool.get(names);
				if (nameMap == null) {
					nameMap = new NameMap(this.names);
					namesPool.put(nameMap.names, nameMap);
				}
				return nameMap;
			}
		}
		

		@Override
		public boolean isEmpty() {
			return names.isEmpty();
		}
		
		/**
		 * Consider that this simply uses indexOf with a list - so it is not fast!
		 * @param name
		 * @return
		 */
		int getMeasurementIndex(String name) {
			// Read from map, if possible
			if (map != null) {
				Integer ind = map.get(name);
				return ind == null ? -1 : ind.intValue();
			}
			return names.indexOf(name);
		}
		
		private boolean add(Measurement measurement) {
			if (measurement.isDynamic())
				throw new UnsupportedOperationException("This MeasurementList does not support dynamic measurements");
			return addMeasurement(measurement.getName(), measurement.getValue());
	    }
		
		@Override
		final public int size() {
			return names.size();
		}

		@Override
		public synchronized List<String> getMeasurementNames() {
			if (names.isEmpty())
				return Collections.emptyList();
			// Try to return the same unmodifiable list of names if we can - this speeds up comparisons
			if (isClosed()) {
				if (namesUnmodifiable == null) {
					var nameMap = getNameMap();
					namesUnmodifiable = nameMap.getUnmodifiableNames();
				}
			}
			if (namesUnmodifiable == null)
				namesUnmodifiable = Collections.unmodifiableList(names);
			else
				assert names.size() == namesUnmodifiable.size();
			return namesUnmodifiable;
		}
		
		@Override
		public double getMeasurementValue(String name) {
			return getMeasurementValue(getMeasurementIndex(name));
		}

		@Override
		public boolean containsNamedMeasurement(String measurementName) {
			if (!isClosed)
				logger.trace("containsNamedMeasurement called on open NumericMeasurementList - consider closing list earlier for efficiency");
			return names.contains(measurementName);
		}

		@Override
		public String getMeasurementName(int ind) {
			return names.get(ind);
		}
		
		@Override
		public void clear() {
			ensureListOpen();
			names.clear();
			namesUnmodifiable = null;
			compactStorage();
		}
		
		void ensureListOpen() {
			if (isClosed()) {
				isClosed = false;
				map = null;
				names = new ArrayList<>(names);	
				namesUnmodifiable = null;
			}
		}
		
		@Override
		public synchronized boolean addMeasurement(String name, double value) {
			// If the list is closed, we have to reopen it
			ensureListOpen();
			names.add(name);
			setValue(size()-1, value);
			return true;
		}
		
		
		@Override
		public synchronized void putMeasurement(String name, double value) {
			ensureListOpen();
			int index = getMeasurementIndex(name);
			if (index >= 0)
				setValue(index, value);
			else
				addMeasurement(name, value);
		}
		

		@Override
		public boolean supportsDynamicMeasurements() {
			return false;
		}
		
		void compactStorage() {
			if (isClosed())
				return;
			if (names instanceof ArrayList)
				((ArrayList<String>)names).trimToSize();
		}
		
		
		@Override
		public Measurement putMeasurement(Measurement measurement) {
			if (measurement.isDynamic())
				throw new UnsupportedOperationException("This MeasurementList does not support dynamic measurements");
			ensureListOpen();
			String name = measurement.getName();
			double value = measurement.getValue();
			int ind = getMeasurementIndex(name);
			if (ind >= 0) {
				Measurement temp = MeasurementFactory.createMeasurement(name, value);
				setValue(ind, value);
				return temp;
			}
			add(measurement);
			return null;
		}
		
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			int n = size();
			sb.append("[");
			for (int i = 0; i < n; i++) {
				sb.append(getMeasurementName(i)).append(": ").append(getMeasurementValue(i));
				if (i < n - 1)
					sb.append(", ");
			}
			sb.append("]");
			return sb.toString();
		}
		
	}



	public static class DoubleList extends AbstractNumericMeasurementList {
		
		private static final long serialVersionUID = 1L;
		
		private double[] values;

		public DoubleList(int capacity) {
			super(capacity);
			this.values = new double[capacity];
			// Close from the start... will be opened as needed
			close();
		}
		
		@Override
		public double getMeasurementValue(int ind) {
			if (ind >= 0 && ind < size())
				return values[ind];
			return Double.NaN;
		}

		private void ensureArraySize(int length) {
			if (values.length < length)
				values = Arrays.copyOf(values, Math.max(values.length + EXPAND, length));
		}

		@Override
		protected void setValue(int index, double value) {
			ensureArraySize(index + 1);
			values[index] = (float)value;
		}
		
		@Override
		public void compactStorage() {
			super.compactStorage();
			if (size() < values.length)
				values = Arrays.copyOf(values, size());
		}

		@Override
		public void removeMeasurements(String... measurementNames) {
			ensureListOpen();
			for (String name : measurementNames) {
				int ind = getMeasurementIndex(name);
				if (ind < 0)
					continue;
				names.remove(name);
				System.arraycopy(values, ind+1, values, ind, values.length-ind-1);
			}
		}

		
	}


	public static class FloatList extends AbstractNumericMeasurementList {
		
		private static final long serialVersionUID = 1L;
		
		private float[] values;

		public FloatList(int capacity) {
			super(capacity);
			this.values = new float[capacity];
			// Close from the start... will be opened as needed
			close();
		}

		@Override
		public double getMeasurementValue(int ind) {
			if (ind >= 0 && ind < size())
				return values[ind];
			return Double.NaN;
		}

		private void ensureArraySize(int length) {
			if (values.length < length)
				values = Arrays.copyOf(values, Math.max(values.length + EXPAND, length));
		}

		@Override
		protected void setValue(int index, double value) {
			ensureArraySize(index + 1);
			values[index] = (float)value;
		}
		
		@Override
		public void compactStorage() {
			super.compactStorage();
			if (size() < values.length)
				values = Arrays.copyOf(values, size());
		}

		
		@Override
		public void removeMeasurements(String... measurementNames) {
			ensureListOpen();
			for (String name : measurementNames) {
				int ind = getMeasurementIndex(name);
				if (ind < 0)
					continue;
				names.remove(name);
				System.arraycopy(values, ind+1, values, ind, values.length-ind-1);
			}
		}

	}
	

}