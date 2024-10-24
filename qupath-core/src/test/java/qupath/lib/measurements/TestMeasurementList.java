/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMeasurementList {

    enum ListType {
        GENERAL,
        DOUBLE,
        FLOAT,
        GENERAL_CLOSED,
        DOUBLE_CLOSED,
        FLOAT_CLOSED;

        private MeasurementList.MeasurementListType toMeasurementListType() {
            return switch (this) {
                case GENERAL, GENERAL_CLOSED -> MeasurementList.MeasurementListType.GENERAL;
                case DOUBLE, DOUBLE_CLOSED -> MeasurementList.MeasurementListType.DOUBLE;
                case FLOAT, FLOAT_CLOSED -> MeasurementList.MeasurementListType.FLOAT;
            };
        }

        private boolean isClosed() {
            return switch (this) {
                case FLOAT_CLOSED, DOUBLE_CLOSED, GENERAL_CLOSED -> true;
                default -> false;
            };
        }
    }

    /**
     * Create a measurement list of the specified type, closed or not.
     * @param type
     * @param nMeasurements
     * @return
     */
    private static MeasurementList createMeasurementList(ListType type, int nMeasurements) {
        // Create list, permitting resize
        var list = MeasurementListFactory.createMeasurementList(Math.max(1, nMeasurements / 2), type.toMeasurementListType());
        for (int i = 0; i < nMeasurements; i++) {
            list.put("Measurement " + i, i);
        }
        if (type.isClosed())
            list.close();
        return list;
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_namesSnapshot(ListType type) {
        var list = createMeasurementList(type, 5);
        // This should give an unmodifiable snapshot of names
        var names = list.getNames();
        list.put(UUID.randomUUID().toString(), 1.0);
        var names2 = list.getNames();
        assertNotEquals(names, names2);
        assertEquals(5, names.size());
        assertEquals(6, names2.size());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_noDuplicates(ListType type) {
        var list = createMeasurementList(type, 0);
        list.put("First", 1.0);
        list.put("Second", 2.0);
        list.put("Third", 3.0);
        assertEquals(3, list.size());

        list.put("Third", 6.0);
        list.put("Second", 5.0);
        list.put("First", 4.0);
        assertEquals(3, list.size());

        assertEquals(4.0, list.get("First"));
        assertEquals(5.0, list.get("Second"));
        assertEquals(6.0, list.get("Third"));
    }


    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_mapAccess(ListType type) {
        var list = createMeasurementList(type, 0);
        var mapOriginal = list.asMap();

        list.put("First", 1.0);
        list.put("Second", 2.0);
        list.put("Third", 3.0);
        assertEquals(3, list.size());

        // Ensure map requested before modification is correct
        assertEquals(Set.of("First", "Second", "Third"), mapOriginal.keySet());
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, mapOriginal.values().stream().mapToDouble(Number::doubleValue).toArray());

        // Ensure map requested after modification is correct
        var map = list.asMap();
        assertEquals(Set.of("First", "Second", "Third"), map.keySet());
        assertEquals(list.keySet(), map.keySet());
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, map.values().stream().mapToDouble(Number::doubleValue).toArray());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_clear(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        assertEquals(n, list.size());
        assertFalse(list.isEmpty());
        list.clear();
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
        assertTrue(list.getNames().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_removeMissing(ListType type) {
        var list = createMeasurementList(type, 0);
        list.put("First", 1.0);
        list.put("Second", 2.0);
        list.put("Third", 3.0);
        assertEquals(3, list.size());
        // Failed remove
        var result = list.remove("Not found");
        assertEquals(3, list.size());
        assertTrue(Double.isNaN(result));
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_removeFirst(ListType type) {
        var list = createMeasurementList(type, 0);
        list.put("First", 1.0);
        list.put("Second", 2.0);
        list.put("Third", 3.0);
        assertEquals(3, list.size());
        // Successful remove
        var result = list.remove("First");
        assertEquals(2, list.size());
        assertEquals(1.0, result);
        assertFalse(list.containsKey("First"));
        assertEquals(2.0, list.get("Second"));
        assertEquals(3.0, list.get("Third"));
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_removeLast(ListType type) {
        var list = createMeasurementList(type, 0);
        list.put("First", 1.0);
        list.put("Second", 2.0);
        list.put("Third", 3.0);
        assertEquals(3, list.size());
        // Successful remove
        var result = list.remove("Third");
        assertEquals(2, list.size());
        assertEquals(3.0, result);
        assertFalse(list.containsKey("Third"));
        assertEquals(1.0, list.get("First"));
        assertEquals(2.0, list.get("Second"));
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_removeMiddle(ListType type) {
        var list = createMeasurementList(type, 0);
        list.put("First", 1.0);
        list.put("Second", 2.0);
        list.put("Third", 3.0);
        assertEquals(3, list.size());
        // Successful remove
        var result = list.remove("Second");
        assertEquals(2, list.size());
        assertEquals(2.0, result);
        assertFalse(list.containsKey("Second"));
        assertEquals(1.0, list.get("First"));
        assertEquals(3.0, list.get("Third"));
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_removeAll(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        assertEquals(n, list.size());
        // Successful remove
        var names = list.getNames();
        var toRemove = names.subList(1, 3);
        list.removeAll(toRemove.toArray(String[]::new));
        var newNames = list.getNames();
        assertEquals(n - toRemove.size(), list.size());
        for (var name : toRemove) {
            assertFalse(newNames.contains(name));
        }
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_putAllMeasurements(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        // Check putting measurements into all list types
        for (var typeNew : ListType.values()) {
            var newList = createMeasurementList(typeNew, 0);
            assertEquals(0, newList.size());
            newList.putAll(list);
            assertEquals(n, newList.size());
            assertEquals(list.getMeasurements(), newList.getMeasurements());
        }
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_putAllMeasurementsFromMap(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        // Check putting measurements into all list types
        for (var typeNew : ListType.values()) {
            var newList = createMeasurementList(typeNew, 0);
            assertEquals(0, newList.size());
            newList.putAll(list.asMap());
            assertEquals(n, newList.size());
            assertEquals(list.getMeasurements(), newList.getMeasurements());
        }
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_mapEntriesImmutable(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        for (var entry : list.asMap().entrySet()) {
            assertThrows(UnsupportedOperationException.class, () -> entry.setValue(0));
        }
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_mapEntryIteration(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        var entries = list.asMap().entrySet();
        var iter = entries.iterator();
        int i = 0;
        while (iter.hasNext()) {
            assertEquals(n - i, list.size());
            var entry = iter.next();
            iter.remove();
            i++;
            assertEquals(n - i, list.size());
        }
        assertEquals(n, i);
        assertTrue(entries.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_mapKeyIteration(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        var keys = list.asMap().keySet();
        var iter = keys.iterator();
        int i = 0;
        while (iter.hasNext()) {
            assertEquals(n - i, list.size());
            var entry = iter.next();
            iter.remove();
            i++;
            assertEquals(n - i, list.size());
        }
        assertEquals(n, i);
        assertTrue(keys.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_mapKeyRemoval(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        var names = list.getNames();
        var toRemove = names.subList(1, 3);
        var keys = list.asMap().keySet();
        keys.removeAll(toRemove);
        assertEquals(n-2, list.size());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_mapValueIteration(ListType type) {
        int n = 5;
        var list = createMeasurementList(type, n);
        var values = list.asMap().values();
        var iter = values.iterator();
        int i = 0;
        while (iter.hasNext()) {
            assertEquals(n - i, list.size());
            var entry = iter.next();
            iter.remove();
            i++;
            assertEquals(n - i, list.size());
        }
        assertEquals(n, i);
        assertTrue(values.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void test_stringInterning(ListType type) {
        var list = createMeasurementList(type, 1);
        var list2 = createMeasurementList(type, 1);
        // Shouldn't normally test strings like this!
        // But here we want to make sure that String.intern() has been called to keep
        // memory usage down.
        // Interning is less important when lists are 'closed', so names are reused,
        // but if the user forgets to do this then memory usage could become *much* higher.
        assertTrue(list.getNames().get(0) == list2.getNames().get(0));
    }

}
