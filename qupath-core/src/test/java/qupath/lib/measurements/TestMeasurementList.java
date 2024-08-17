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

    private static MeasurementList createMeasurementList(ListType type, int nMeasurements) {
        // Create list, permitting resize
        var list = MeasurementListFactory.createMeasurementList(Math.max(1, nMeasurements / 2), type.toMeasurementListType());
        for (int i = 0; i < nMeasurements; i++) {
            list.put("Measurement " + (i+1), i+1);
        }
        if (type.isClosed())
            list.close();
        return list;
    }

    @ParameterizedTest
    @EnumSource(ListType.class)
    void checkNamesUnchanged(ListType type) {
        var list = createMeasurementList(type, 5);
        // This should give an unmodifiable snapshot of names
        var names = list.getMeasurementNames();
        list.put(UUID.randomUUID().toString(), 1.0);
        var names2 = list.getMeasurementNames();
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
        assertTrue(list.getMeasurementNames().isEmpty());
    }

}
