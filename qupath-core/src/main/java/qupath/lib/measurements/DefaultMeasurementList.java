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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A MeasurementList implementation that simply stores a list of Measurement objects.
 * <p>
 * These can be of any kind, including dynamic measurements (computed on request).
 * <p>
 * Generally, if only simple, non-dynamic numeric measurements are required, then
 * another MeasurementList would be preferable to reduce memory requirements.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultMeasurementList implements MeasurementList {
	
	private static final long serialVersionUID = 1L;
	
	private ArrayList<Measurement> list;
	
	private transient volatile Map<String, Number> mapView;
	
	DefaultMeasurementList() {
		list = new ArrayList<>();
	}
	
	DefaultMeasurementList(int capacity) {
		list = new ArrayList<>(capacity);
	}
	
	@Override
	public synchronized void clear() {
		this.list.clear();
	}
	
	@Override
	public synchronized List<String> getNames() {
		return list.stream()
				.map(Measurement::getName)
				.toList();
	}

	@Override
	public List<Measurement> getMeasurements() {
		return List.copyOf(list);
	}

	@Override
	public Measurement getByIndex(int ind) {
		return list.get(ind);
	}

	@Override
	public synchronized double[] values() {
		return list.stream()
				.mapToDouble(Measurement::getValue)
				.toArray();
	}

	@Override
	public synchronized double remove(String name) {
		var iter = list.iterator();
		while (iter.hasNext()) {
			var next = iter.next();
			if (next.getName().equals(name)) {
				iter.remove();
				return next.getValue();
			}
		}
		return Double.NaN;
	}

	@Override
	public synchronized double get(String name) {
		for (Measurement m : list) {
			if (m.getName().equals(name))
				return m.getValue();
		}
		return Double.NaN;
	}
		
	@Override
	public synchronized boolean containsKey(String measurement) {
		for (Measurement m : list)
			if (m.getName().equals(measurement))
				return true;
		return false;
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	private void compactStorage() {
		list.trimToSize();
	}

	@Override
	public synchronized void close() {
		compactStorage();
	}

	@Override
	public synchronized void put(String name, double value) {
		Objects.requireNonNull(name, "Measurement name cannot be null");
		// Ensure we aren't adding duplicate measurements
		var measurement = MeasurementFactory.createMeasurement(name, value);
		int ind = 0;
		for (Measurement m : list) {
			if (m.getName().equals(name))
				break;
			ind++;
		}
		if (ind < list.size()) {
			list.set(ind, measurement);
		} else {
			list.add(measurement);
		}
	}

	@Override
	public synchronized void removeAll(String... measurementNames) {
		for (String name : measurementNames) {
			int ind = 0;
			for (Measurement m : list) {
				if (m.getName().equals(name))
					break;
				else
					ind++;
			}
			if (ind < list.size())
				list.remove(ind);
		}
	}

	@Override
	public Map<String, Number> asMap() {
		if (mapView == null) {
			synchronized(this) {
				if (mapView == null)
					mapView = Collections.synchronizedMap(new MeasurementsMap(this));
			}
		}
		return mapView;
	}
	
	@Override
	public synchronized String toString() {
		return "[" + list.stream()
				.map(DefaultMeasurementList::toString)
				.collect(Collectors.joining(", ")) + "]";
	}

	private static String toString(Measurement m) {
		return m.getName() + ": " + m.getValue();
	}
	
}