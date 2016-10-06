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

package qupath.lib.projects.patients;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Basic implementation of <code>PatientData</code> interface.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultPatientData implements PatientData {
	
	private Map<String, Patient> map = new HashMap<>();
	
	public DefaultPatientData(Patient... patients) {
		this(Arrays.asList(patients));
	}

	public DefaultPatientData(Collection<Patient> patients) {
		for (Patient patient : patients)
			map.put(patient.getUniqueID(), patient);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsKey(value);
	}

	@Override
	public Patient get(Object key) {
		return map.get(key);
	}

	@Override
	public Patient put(String key, Patient patient) {
		if (!key.equals(patient.getUniqueID()))
			throw new RuntimeException("Cannot add patient to map where key != patient.getUniqueID()");
		return map.put(key, patient);
	}

	@Override
	public Patient remove(Object key) {
		return map.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Patient> m) {
		for (Entry<? extends String, ? extends Patient> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Set<String> keySet() {
		return map.keySet();
	}

	@Override
	public Collection<Patient> values() {
		return map.values();
	}

	@Override
	public Set<java.util.Map.Entry<String, Patient>> entrySet() {
		return map.entrySet();
	}

}
