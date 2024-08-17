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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestMeasurements {

    @Test
    void test_MeasurementsEquals() {
        var mDouble = MeasurementFactory.createMeasurement("m1", 1.0d);
        var mDouble2 = MeasurementFactory.createMeasurement("m1", 1.0f);
        var mFloat = MeasurementFactory.createMeasurement("m1", 1.0d);
        var mFloat2 = MeasurementFactory.createMeasurement("m1", 1.0f);
        assertEquals(mDouble, mDouble);
        assertEquals(mDouble, mDouble2);
        assertEquals(mFloat, mFloat);
        assertEquals(mFloat, mFloat2);
        assertEquals(mDouble, mFloat);
        assertEquals(mDouble.hashCode(), mFloat.hashCode());
    }

    @Test
    void test_MeasurementsDoubleDifferentNames() {
        var mDouble = MeasurementFactory.createMeasurement("m1", 1.0d);
        var mDouble2 = MeasurementFactory.createMeasurement("m2", 1.0d);
        assertNotEquals(mDouble.getName(), mDouble2.getName());
        assertEquals(mDouble.getValue(), mDouble2.getValue());
        assertNotEquals(mDouble, mDouble2);
    }

    @Test
    void test_MeasurementsFloatDifferentNames() {
        var mFloat = MeasurementFactory.createMeasurement("m1", 1.0f);
        var mFloat2 = MeasurementFactory.createMeasurement("m2", 1.0f);
        assertNotEquals(mFloat.getName(), mFloat2.getName());
        assertEquals(mFloat.getValue(), mFloat2.getValue());
        assertNotEquals(mFloat, mFloat2);
    }

    @Test
    void test_MeasurementsDoubleDifferentValues() {
        var mDouble = MeasurementFactory.createMeasurement("m1", 1.0d);
        var mDouble2 = MeasurementFactory.createMeasurement("m1", 2.0d);
        assertEquals(mDouble.getName(), mDouble2.getName());
        assertNotEquals(mDouble.getValue(), mDouble2.getValue());
        assertNotEquals(mDouble, mDouble2);
    }

    @Test
    void test_MeasurementsFloatDifferentValues() {
        var mFloat = MeasurementFactory.createMeasurement("m1", 1.0f);
        var mFloat2 = MeasurementFactory.createMeasurement("m1", 2.0f);
        assertEquals(mFloat.getName(), mFloat2.getName());
        assertNotEquals(mFloat.getValue(), mFloat2.getValue());
        assertNotEquals(mFloat, mFloat2);
    }

}
