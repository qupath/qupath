/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import qupath.lib.measurements.MeasurementList.MeasurementListType;

@SuppressWarnings("javadoc")
public class TestMeasurementListFactory {

	@Test
	public void test() {
		testList(MeasurementListFactory.createMeasurementList(50, MeasurementListType.DOUBLE));
		testList(MeasurementListFactory.createMeasurementList(50, MeasurementListType.FLOAT));
		testList(MeasurementListFactory.createMeasurementList(50, MeasurementListType.GENERAL));
	}
	
	
	static void testList(MeasurementList list) {
		
		list.clear();
		assertTrue(list.isEmpty());
		
		var rand = new Random(100L);
		Map<String, Double> map = new LinkedHashMap<>();
		List<String> names = new ArrayList<>();
		int n = 100;
		for (int i = 0; i < n; i++) {
			String name = "Something " + i;
			double val = rand.nextDouble() * 100.0;
			map.put(name, val);
			names.add(name);
			// We can use addMeasurement because it's empty
			list.addMeasurement(name, val);
		}
		
		// Change same names
		assertEquals(names, list.getMeasurementNames());
		
		// Check access
		Collections.shuffle(names, rand);
		assertTrue(checkAgreement(list, map, names));
		
		// Check access after closing
		list.close();
		Collections.shuffle(names, rand);
		assertTrue(checkAgreement(list, map, names));

		// Check access after removing
		for (int i = 0; i < 10; i++) {
			String name = names.remove(i);
			list.removeMeasurements(name);
		}
		assertTrue(list.size() == names.size());
		assertTrue(list.getMeasurementNames().containsAll(names));
		assertTrue(checkAgreement(list, map, names));	
		
		// Check list after adding (some with same name as previously)
		for (int i = 50; i < 50+n; i++) {
			String name = "Something " + i;
			double val = rand.nextDouble() * 100.0;
			map.put(name, val);
			if (!names.contains(name))
				names.add(name);
			// We need to use putMeasurement because it's probably not empty
			list.putMeasurement(name, val);
		}
		
		Collections.shuffle(names, rand);
		assertTrue(checkAgreement(list, map, names));
		list.close();
		assertTrue(checkAgreement(list, map, names));
		
		assertTrue(list.size() == names.size());
		assertTrue(list.getMeasurementNames().containsAll(names));
		
		list.clear();
		assertTrue(list.isEmpty());
		assertTrue(list.getMeasurementNames().isEmpty());
	}
	
	
	static boolean checkAgreement(MeasurementList list, Map<String, Double> map, Collection<String> namesToCheck) {
		double eps = 1e-4;
		for (String name : namesToCheck) {
			assertEquals(map.get(name), list.getMeasurementValue(name), eps);
		}
		return true;
	}

}