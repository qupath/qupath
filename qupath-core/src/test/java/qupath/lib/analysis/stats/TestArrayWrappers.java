/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.analysis.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestArrayWrappers {
	
	@Test
	public void test_integerWrapper() {
		int[] array = new int[1000];
		for (int i = 0; i < 1000; i++) {
			array[i] = i * 3;
		}
		ArrayWrappers.ArrayWrapper wrapper = ArrayWrappers.makeIntArrayWrapper(array);
		
		// Check wrapper type
		assertTrue(wrapper.isIntegerWrapper());
		
		// Check wrapper values
		for (int i = 0; i < 1000; i++) {
			assertEquals((double)i*3, wrapper.getDouble(i));
		}
		
		// Check sizes
		assertEquals(array.length, wrapper.size());
	}

	@Test
	public void test_doubleWrapper() {
		double[] array = new double[1000];
		for (int i = 0; i < 1000; i++) {
			array[i] = i * 1.5;
		}
		ArrayWrappers.ArrayWrapper wrapper = ArrayWrappers.makeDoubleArrayWrapper(array);
		
		// Check wrapper type
		assertFalse(wrapper.isIntegerWrapper());
		
		// Check wrapper values
		for (int i = 0; i < 1000; i++) {
			assertEquals(i*1.5, wrapper.getDouble(i));
		}
		
		// Check sizes
		assertEquals(array.length, wrapper.size());
	}

	@Test
	public void test_floatWrapper() {
		float[] array = new float[1000];
		for (int i = 0; i < 1000; i++) {
			array[i] = (float)(i * 1.5);
		}
		ArrayWrappers.ArrayWrapper wrapper = ArrayWrappers.makeFloatArrayWrapper(array);
		
		// Check wrapper type
		assertFalse(wrapper.isIntegerWrapper());
		
		// Check wrapper values
		for (int i = 0; i < 1000; i++) {
			assertEquals(i*1.5, wrapper.getDouble(i));
		}
		
		// Check sizes
		assertEquals(array.length, wrapper.size());
	}

	
	@Test
	public void test_unsignedByteWrapper() {
		byte[] array = new byte[1000];
		// Fill half of the array with negative values and half with positive values
		for (int i = 0; i < 500; i++) {
			array[i] = (byte)(-i);
		}
		for (int i = 0; i < 500; i++) {
			array[500 + i] = (byte)(i);
		}
		ArrayWrappers.ArrayWrapper wrapper = ArrayWrappers.makeUnsignedByteArrayWrapper(array);
		
		// Check wrapper type
		assertTrue(wrapper.isIntegerWrapper());
		
		// Check wrapper values
		for (int i = 0; i < 500; i++) {
			assertEquals((double)Byte.toUnsignedInt((byte)-i), wrapper.getDouble(i));
			assertEquals((double)Byte.toUnsignedInt((byte)i), wrapper.getDouble(500 + i));
		}
		
		// Check sizes
		assertEquals(array.length, wrapper.size());
	}
	
	@Test
	public void test_unsignedShortWrapper() {
		short[] array = new short[1000];
		// Fill half of the array with negative values and half with positive values
		for (int i = 0; i < 500; i++) {
			array[i] = (short)(i * -1.5);
		}
		for (int i = 0; i < 500; i++) {
			array[500 + i] = (short)(i * 1.5);
		}
		ArrayWrappers.ArrayWrapper wrapper = ArrayWrappers.makeUnsignedShortArrayWrapper(array);
		
		// Check wrapper type
		assertTrue(wrapper.isIntegerWrapper());
		
		// Check wrapper values
		for (int i = 0; i < 500; i++) {
			assertEquals((double)Short.toUnsignedInt((short)(i*-1.5)), wrapper.getDouble(i));
			assertEquals((double)Short.toUnsignedInt((short)(i*1.5)), wrapper.getDouble(500 + i));
		}
		
		// Check sizes
		assertEquals(array.length, wrapper.size());
	}
}