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

package qupath.lib.analysis.stats;


/**
 * Simple wrappers for primitive arrays that returns values as doubles.
 * 
 * @author Pete Bankhead
 *
 */
public class ArrayWrappers {

	/**
	 * Create a wrapper for a double array, returning values (predictably) as doubles.
	 * 
	 * @param array
	 * @return
	 */
	public static ArrayWrapper makeDoubleArrayWrapper(double[] array) {
		return new DoubleArrayWrapper(array);
	}

	/**
	 * Create a wrapper for a float array, returning values as doubles.
	 * 
	 * @param array
	 * @return
	 */
	public static ArrayWrapper makeFloatArrayWrapper(float[] array) {
		return new FloatArrayWrapper(array);
	}

	/**
	 * Create a wrapper for an int array, returning values as doubles.
	 * 
	 * @param array
	 * @return
	 */
	public static ArrayWrapper makeIntArrayWrapper(int[] array) {
		return new IntArrayWrapper(array);
	}

	/**
	 * Create a wrapper for a byte array, returning values as doubles.
	 * <p>
	 * Bytes are treated as unsigned 8-bit values (0-255);
	 * 
	 * @param array
	 * @return
	 */
	public static ArrayWrapper makeUnsignedByteArrayWrapper(byte[] array) {
		return new UnsignedByteArrayWrapper(array);
	}

	/**
	 * Create a wrapper for a short array, returning values as doubles.
	 * <p>
	 * Shorts are treated as unsigned 16-bit values (0-65535);
	 * 
	 * @param array
	 * @return
	 */
	public static ArrayWrapper makeUnsignedShortArrayWrapper(short[] array) {
		return new UnsignedShortArrayWrapper(array);
	}

	/**
	 * Simple wrapper for an array or list, enabling values to be returned as doubles.
	 */
	public static interface ArrayWrapper {
		
		/**
		 * Number of entries in the array.
		 * @return
		 */
		public int size();
		
		/**
		 * Extract one entry from the array, converting to double as necessary.
		 * @param ind
		 * @return
		 */
		public double getDouble(int ind);

		/**
		 * Returns true if the array wrapper only supports integer values.
		 * @return
		 */
		public boolean isIntegerWrapper();

	}

	
	
	static class UnsignedByteArrayWrapper implements ArrayWrapper {
		
		final private byte[] array;
		
		public UnsignedByteArrayWrapper(byte[] array) {
			this.array = array;
		}
		
		@Override
		public final int size() {
			return array.length;
		}
		
		@Override
		public final double getDouble(int ind) {
			return array[ind] & 0xff;
		}
		
		@Override
		public boolean isIntegerWrapper() {
			return true;
		}
		
	}
	
	
	static class UnsignedShortArrayWrapper implements ArrayWrapper {
		
		final private short[] array;
		
		public UnsignedShortArrayWrapper(short[] array) {
			this.array = array;
		}
		
		@Override
		public final int size() {
			return array.length;
		}
		
		@Override
		public final double getDouble(int ind) {
			return array[ind] & 0xffff;
		}
		
		@Override
		public boolean isIntegerWrapper() {
			return true;
		}
		
	}

	
	static class IntArrayWrapper implements ArrayWrapper {
		
		final private int[] array;
		
		public IntArrayWrapper(int[] array) {
			this.array = array;
		}
		
		@Override
		public final int size() {
			return array.length;
		}
		
		@Override
		public final double getDouble(int ind) {
			return array[ind];
		}
		
		@Override
		public boolean isIntegerWrapper() {
			return true;
		}
		
	}

	
	static class FloatArrayWrapper implements ArrayWrapper  {
		
		final private float[] array;
		
		public FloatArrayWrapper(float[] array) {
			this.array = array;
		}
		
		@Override
		public final int size() {
			return array.length;
		}
		
		@Override
		public final double getDouble(int ind) {
			return array[ind];
		}
		
		@Override
		public boolean isIntegerWrapper() {
			return false;
		}
		
	}

	
	static class DoubleArrayWrapper implements ArrayWrapper  {
		
		final private double[] array;
		
		public DoubleArrayWrapper(double[] array) {
			this.array = array;
		}
		
		@Override
		public final int size() {
			return array.length;
		}
		
		@Override
		public final double getDouble(int ind) {
			return array[ind];
		}
		
		@Override
		public boolean isIntegerWrapper() {
			return false;
		}

	}

}
