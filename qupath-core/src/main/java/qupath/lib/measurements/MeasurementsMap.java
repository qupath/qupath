/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022-2024 QuPath developers, The University of Edinburgh
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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Wrapper class to enable access to a {@link MeasurementList} as if it were a map.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
class MeasurementsMap extends AbstractMap<String, Number> implements Map<String, Number> {
	
	private final MeasurementList list;
	
	public MeasurementsMap(MeasurementList list) {
		this.list = list;
	}

	@Override
	public Set<Entry<String, Number>> entrySet() {
		return new MeasurementEntrySet();
	}
	
	
	@Override
	public int size() {
		return list.size();
	}
	
	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}
	
	@Override
	public boolean containsKey(Object key) {
		return key instanceof String && this.list.containsKey((String)key);
	}
	
	@Override
	public boolean containsValue(Object value) {
		double val;
		if (value instanceof Number number)
			val = number.doubleValue();
		else
			return false;
		return list.getMeasurements()
				.stream()
				.anyMatch(m -> m.getValue() == val);
	}
	
	@Override
	public void clear() {
		this.list.clear();
	}
	
	@Override
	public Double get(Object key) {
		if (key instanceof String name) {
			synchronized (list) {
				if (list.containsKey(name))
					return list.get(name);
			}
		}
		return null;
	}
	
	/**
	 * Put the value into the map.
	 * Note that value must be non-null, and the value of the number will be stored as a double 
	 * or float primitive, depending upon the backing {@link MeasurementList} implementation.
	 * As such, object equality is not guaranteed when retrieving values and loss of precision 
	 * is possible.
	 */
	@Override
	public Number put(String name, Number value) {
		Objects.requireNonNull(value);
		Number current = null;
		synchronized (list) {
			if (list.containsKey(name))
				current = list.get(name);
			list.put(name, value.doubleValue());
			list.close();
		}
		return current;
	}
	
	@Override
	public void putAll(Map<? extends String, ? extends Number> map) {
		synchronized (list) {
			// Don't repeatedly call put because we only want to close the list once 
			// and don't need to extract previous values
			for (Map.Entry<? extends String, ? extends Number> e : map.entrySet()) {
				Objects.requireNonNull(e.getValue()); // Don't support null values
				list.put(e.getKey(), e.getValue().doubleValue());
			}
			list.close();
		}
	}
	
	@Override
	public Double remove(Object key) {
		synchronized (list) {
			if (key instanceof String name) {
				if (list.containsKey(name)) {
					return list.remove(name);
				}
			}
			return null;
		}
	}
	
	
	class MeasurementEntrySet extends AbstractSet<Entry<String, Number>> {

		@Override
		public int size() {
			return list.size();
		}

		@Override
		public Iterator<Entry<String, Number>> iterator() {
			return new Iterator<>() {
				
				private int i = 0;

				@Override
				public boolean hasNext() {
					return i < size();
				}

				@Override
				public Entry<String, Number> next() {
					var measurement = list.getByIndex(i);
					Entry<String, Number> entry = new SimpleImmutableEntry<>(
							measurement.getName(), measurement.getValue());
					i++;
					return entry;
				}
				
				@Override
				public void remove() {
					if (i <= 0)
						throw new IllegalStateException();
					list.remove(list.getNames().get(i - 1));
					i--;
				}
				
			};
		}
		
	}

}
