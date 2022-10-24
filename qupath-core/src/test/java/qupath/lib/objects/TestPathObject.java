/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


package qupath.lib.objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.roi.ROIs;

public class TestPathObject {
	
	
	private static List<PathObject> provideObjects() {
		return Arrays.asList(
				PathObjects.createAnnotationObject(ROIs.createEmptyROI()),
				PathObjects.createTMACoreObject(0, 0, 100, 100, false),
				PathObjects.createTMACoreObject(0, 0, 100, 100, true),
				PathObjects.createDetectionObject(ROIs.createEmptyROI()),
				PathObjects.createTileObject(ROIs.createEmptyROI()),
				PathObjects.createCellObject(ROIs.createEmptyROI(), ROIs.createEmptyROI(), null, 
						MeasurementListFactory.createMeasurementList(16, MeasurementListType.DOUBLE))
				);
	}
	
	
	@ParameterizedTest
	@MethodSource("provideObjects")
	public void test_measurementMapSynchronization(PathObject p) {
		
		var list = p.getMeasurementList();
		var map = p.getMeasurements();
		
		int n = 1000;
		IntStream.range(0, n)
			.parallel()
			.forEach(i ->list.putMeasurement("Measurement " + i, i));
		
		assertEquals(n, list.size());
		assertEquals(n, map.size());
		
		map.clear();

		assertEquals(0, list.size());
		assertEquals(0, map.size());
		
		IntStream.range(0, n)
			.parallel()
			.forEach(i -> map.put("Measurement " + i, (double)i));

		assertEquals(n, list.size());
		assertEquals(n, map.size());
		
		list.clear();

		assertEquals(0, list.size());
		assertEquals(0, map.size());
		
		IntStream.range(0, n)
		.parallel()
		.forEach(i -> list.putMeasurement("Measurement " + i, (double)i));
		
		IntStream.range(0, n)
		.parallel()
		.forEach(i -> map.put("Measurement " + i, (double)i));
		
		assertEquals(n, list.size());
		assertEquals(n, map.size());
		
		assertSame(list, p.getMeasurementList());
		assertSame(map, p.getMeasurements());
		
	}
	
	@ParameterizedTest
	@MethodSource("provideObjects")
	public void test_measurementMapAndList(PathObject p) {
		
		p.getMeasurementList().addMeasurement("added", 1);
		assertEquals(1, p.getMeasurementList().size());
		p.getMeasurementList().addMeasurement("added", 2);
		assertEquals(1, p.getMeasurementList().size());
		assertEquals(2, p.getMeasurementList().getMeasurementValue("added"));
		
		p.getMeasurementList().putMeasurement("put", 3);
		assertEquals(2, p.getMeasurementList().size());
		p.getMeasurementList().putMeasurement("put", 4);
		assertEquals(2, p.getMeasurementList().size());
		assertEquals(4, p.getMeasurementList().getMeasurementValue("put"));
		
		assertEquals(2, p.getMeasurements().size());
		assertEquals(2, p.getMeasurements().keySet().size());
		assertEquals(2, p.getMeasurements().entrySet().size());
		assertEquals(2, p.getMeasurements().values().size());
		assertEquals(2.0, p.getMeasurements().get("added"));
		assertEquals(4.0, p.getMeasurements().get("put"));
		
		checkSameKeysAndValues(p);
		
		Double val = 5.5; // Note 5.1 wouldn't work because of loss of precision if using a float measurement list!
		p.getMeasurements().put("mapAdded", val);
		assertEquals(val, p.getMeasurements().get("mapAdded"));
		assertEquals(val, p.getMeasurementList().getMeasurementValue("mapAdded"));
		// Not expected to pass! val is unboxed internally, precise value not stored
//		assertSame(val, p.getMeasurements().get("mapAdded"));
		
		p.getMeasurementList().removeMeasurements("Not there");
		assertEquals(3, p.getMeasurementList().size());
		assertEquals(3, p.getMeasurements().size());
		
		p.getMeasurementList().removeMeasurements("put");
		assertEquals(2, p.getMeasurementList().size());
		assertEquals(2, p.getMeasurements().size());

		p.getMeasurements().remove("added");
		assertEquals(1, p.getMeasurementList().size());
		assertEquals(1, p.getMeasurements().size());

		checkSameKeysAndValues(p);
	}
	
	
	/**
	 * Check list and map representations both have identical keys, whether viewed as a list or a set 
	 * (i.e. they must be unique and ordered)
	 * @param p
	 */
	private static void checkSameKeysAndValues(PathObject p) {
		assertEquals(p.getMeasurementList().getMeasurementNames(), new ArrayList<>(p.getMeasurements().keySet()));
		assertEquals(new LinkedHashSet<>(p.getMeasurementList().getMeasurementNames()), p.getMeasurements().keySet());
		
		double[] listValues = new double[p.getMeasurementList().size()];
		double[] listValuesByName = new double[p.getMeasurementList().size()];
		for (int i = 0; i < listValues.length; i++) {
			listValues[i] = p.getMeasurementList().getMeasurementValue(i);
			listValuesByName[i] = p.getMeasurementList().getMeasurementValue(p.getMeasurementList().getMeasurementName(i));
		}
		double[] mapValues = p.getMeasurements().values().stream().mapToDouble(v -> v.doubleValue()).toArray();
		double[] mapValuesByIterator = new double[p.getMeasurements().size()];
		int count = 0;
		for (var entry : p.getMeasurements().entrySet()) {
			mapValuesByIterator[count++] = entry.getValue();
		}
		
		assertArrayEquals(listValues, listValuesByName);
		assertArrayEquals(listValues, mapValues);
		assertArrayEquals(listValues, mapValuesByIterator);
	}
	
	
	

}
