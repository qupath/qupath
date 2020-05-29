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
import java.util.Collections;
import java.util.List;

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
	
	DefaultMeasurementList() {
		list = new ArrayList<>();
	}
	
	DefaultMeasurementList(int capacity) {
		list = new ArrayList<>(capacity);
	}
	
	@Override
	public synchronized boolean addMeasurement(String name, double value) {
		return add(MeasurementFactory.createMeasurement(name, value));
	}
	
	@Override
	public synchronized void clear() {
		this.list.clear();
	}
	
	@Override
	public synchronized List<String> getMeasurementNames() {
		List<String> names = new ArrayList<>();
		for (Measurement m : list)
			names.add(m.getName());
		return Collections.unmodifiableList(names);
	}
	
	@Override
	public synchronized double getMeasurementValue(int ind) {
		if (ind >= 0 && ind < size())
			return list.get(ind).getValue();
		return Double.NaN;
	}

	@Override
	public synchronized double getMeasurementValue(String name) {
		for (Measurement m : list) {
			if (m.getName().equals(name))
				return m.getValue();
		}
		return Double.NaN;
	}
		
	@Override
	public synchronized boolean containsNamedMeasurement(String measurement) {
		for (Measurement m : list)
			if (m.getName().equals(measurement))
				return true;
		return false;
	}
	
	@Override
	public synchronized String getMeasurementName(int ind) {
		return list.get(ind).getName();
	}

//	@Override
//	public void setMeasurement(int ind, double value) {
//		set(ind, MeasurementFactory.createMeasurement(
//				getMeasurementName(ind), value));
//	}

	@Override
	public boolean supportsDynamicMeasurements() {
		return true;
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

//	@Override
//	public void clear() {
//		list.clear();
//	}

	private void compactStorage() {
		list.trimToSize();
	}

	@Override
	public synchronized Measurement putMeasurement(Measurement measurement) {
		// Ensure we aren't adding duplicate measurements
		String name = measurement.getName();
		int ind = 0;
		for (Measurement m : list) {
			if (m.getName().equals(name))
				break;
			ind++;
		}
		if (ind < list.size()) {
			return list.set(ind, measurement);
		}
		list.add(measurement);
		return null;
	}
	
	private synchronized boolean add(Measurement measurement) {
		return list.add(measurement);
	}

//	@Override
//	public boolean remove(Object o) {
//		return list.remove(o);
//	}

	@Override
	public synchronized void close() {
		compactStorage();
	}

	@Override
	public synchronized void putMeasurement(String name, double value) {
		putMeasurement(MeasurementFactory.createMeasurement(name, value));
	}

	@Override
	public synchronized void removeMeasurements(String... measurementNames) {
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
	public synchronized String toString() {
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