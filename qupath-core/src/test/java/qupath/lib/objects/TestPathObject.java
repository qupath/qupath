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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
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
	public void test_measurementOrDefault(PathObject p) {
		
		var list = p.getMeasurementList();
		
		list.put("Something", 100.0);
		assertTrue(Double.isNaN(list.get("Something else")));
		assertTrue(Double.isNaN(list.getOrDefault("Something else", Double.NaN)));
		assertFalse(Double.isNaN(list.getOrDefault("Something else", Double.NEGATIVE_INFINITY)));
		assertEquals(-1, list.getOrDefault("Something else", -1));
		
	}
	
	@ParameterizedTest
	@MethodSource("provideObjects")
	public void test_measurementMapSynchronization(PathObject p) {
		
		var list = p.getMeasurementList();
		var map = p.getMeasurements();
		
		int n = 1000;
		IntStream.range(0, n)
			.parallel()
			.forEach(i ->list.put("Measurement " + i, i));
		
		assertEquals(n, list.size());
		assertEquals(n, map.size());
		
		// Closing shouldn't make a difference
		list.close();
		assertEquals(n, list.size());
		
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
		.forEach(i -> list.put("Measurement " + i, (double)i));
		
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
		assertEquals(2, p.getMeasurementList().get("added"));
		
		p.getMeasurementList().put("put", 3);
		assertEquals(2, p.getMeasurementList().size());
		p.getMeasurementList().put("put", 4);
		assertEquals(2, p.getMeasurementList().size());
		assertEquals(4, p.getMeasurementList().get("put"));
		
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
		assertEquals(val, p.getMeasurementList().get("mapAdded"));
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
		
		p.getMeasurementList().close();
		checkSameKeysAndValues(p);
		assertEquals(1, p.getMeasurementList().size());
		assertEquals(1, p.getMeasurements().size());
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
		double[] listValuesAsArray = p.getMeasurementList().values();
		for (int i = 0; i < listValues.length; i++) {
			listValues[i] = p.getMeasurementList().getMeasurementValue(i);
			listValuesByName[i] = p.getMeasurementList().get(p.getMeasurementList().getMeasurementName(i));
		}
		double[] mapValues = p.getMeasurements().values().stream().mapToDouble(v -> v.doubleValue()).toArray();
		double[] mapValuesByIterator = new double[p.getMeasurements().size()];
		int count = 0;
		for (var entry : p.getMeasurements().entrySet()) {
			mapValuesByIterator[count++] = entry.getValue();
		}
		
		assertArrayEquals(listValues, listValuesAsArray);
		assertArrayEquals(listValues, listValuesByName);
		assertArrayEquals(listValues, mapValues);
		assertArrayEquals(listValues, mapValuesByIterator);
	}
	
	
	@ParameterizedTest
	@MethodSource("provideObjects")
	public void testSerialization(PathObject p) {
		try {
			var p2 = deserialize(serialize(p));
			assertEquals(p.getClass(), p2.getClass());
			assertEquals(p.getPathClass(), p2.getPathClass());
			assertEquals(p.getName(), p2.getName());
			assertEquals(p.getDisplayedName(), p2.getDisplayedName());
			assertEquals(p.getColor(), p2.getColor());
			assertEquals(p.getMeasurements(), p2.getMeasurements());
			if (p instanceof TMACoreObject) {
				((TMACoreObject)p).isMissing();
				assertTrue(((TMACoreObject)p).isMissing() == ((TMACoreObject)p2).isMissing());				
			}
			// ROIs don't implement hashcode and equals - try geometries instead
			assertEquals(p.getROI().getGeometry(), p2.getROI().getGeometry());
			
		} catch (Exception e) {
			fail(e);
		}
	}
	
	@Test
	public void testTMACoreSerialization() {
		try {
			var id = "Something";
			
			var core = PathObjects.createTMACoreObject(0, 10, 20, 30, false);
			assertNull(core.getCaseID());
			core.setCaseID(id);
			assertEquals(id, core.getCaseID());

			var core2 = (TMACoreObject)deserialize(serialize(core));
			assertEquals(core.getCaseID(), core2.getCaseID());

			// Set the legacy ID - don't automatically update it
			var core3 = PathObjects.createTMACoreObject(0, 10, 20, 30, false);
			((MetadataStore)core).putMetadataValue(TMACoreObject.LEGACY_KEY_UNIQUE_ID, id);
			assertNull(core3.getCaseID());

			// Update legacy ID during deserialization
			var core4 = (TMACoreObject)deserialize(serialize(core));
			assertEquals(id, core4.getCaseID());

		} catch (Exception e) {
			fail(e);
		}
	}
	
	
	private static byte[] serialize(PathObject p) throws IOException {
		var bos = new ByteArrayOutputStream();
		try (var stream = new ObjectOutputStream(bos)) {
			stream.writeObject(p);
		}
		return bos.toByteArray();
	}
	
	private static PathObject deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		try (var stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
			return (PathObject)stream.readObject();
		}
	}
	
	
	/**
	 * For v0.4.0 (and likely before) this would throw a concurrent modification exception
	 */
	@Test
	public void checkPathObjectSynchronization() {
		var pathObject = PathObjects.createAnnotationObject(ROIs.createEmptyROI());
		
		int n = 1000;
		int delay = 2;
		var tAdd = new Thread(() -> addChildrenDelayed(pathObject, n, delay));
		tAdd.setDaemon(true);
		tAdd.start();
		while (tAdd.isAlive()) {
			pathObject.nDescendants();
			pathObject.nChildObjects();
			pathObject.getDescendantObjects(null);
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				fail();
			}
		}
		assertEquals(pathObject.nDescendants(), n);
	}
	
	
	private static void addChildrenDelayed(PathObject pathObject, int n, int delayMillis) {
		if (n == 0)
			return;
		for (int i = 0; i < n; i++) {
			var child = PathObjects.createDetectionObject(ROIs.createEmptyROI());
			pathObject.addChildObject(child);
		}
	}
	
	
	
	
	

}
