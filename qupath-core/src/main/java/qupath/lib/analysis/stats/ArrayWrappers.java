/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
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


import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Simple wrappers for primitive arrays that returns values as doubles.
 * <p>
 * This is intended for cases where we don't want to write different code to handle different primitive array
 * types, and we also don't want to have to convert the entire arrays.
 * 
 * @author Pete Bankhead
 *
 */
public class ArrayWrappers {

	/**
	 * Immutable empty wrapper.
	 */
	private static final ArrayWrapper EMPTY = new EmptyWrapper();

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
	 * Concatenate an array containing multiple array wrappers, to act as one long wrapper.
	 * @param wrappers
	 * @return a concatenated wrapper, or the original wrapper if only one is provided
	 */
	public static ArrayWrapper concatenate(ArrayWrapper... wrappers) {
		return concatenate(Arrays.asList(wrappers));
	}

	/**
	 * Concatenate a collection of array wrappers, to act as one long wrapper.
	 * @param wrappers
	 * @return a concatenated wrapper, or the original wrapper if only one is provided
	 */
	public static ArrayWrapper concatenate(Collection<? extends ArrayWrapper> wrappers) {
		if (wrappers.isEmpty())
			return EMPTY;
		if (wrappers.size() == 1) {
			return wrappers.iterator().next();
		}
		return new ConcatArrayWrapper(wrappers);
	}


	/**
	 * Simple wrapper for an array or list, enabling values to be returned as doubles.
	 */
	public interface ArrayWrapper {
		
		/**
		 * Number of entries in the array.
		 * @return
		 */
		int size();

		/**
		 * Query if {@code size() == 1}
		 * return
		 */
		default boolean isEmpty() {
			return size() == 0;
		}
		
		/**
		 * Extract one entry from the array, converting to double as necessary.
		 * @param ind
		 * @return
		 */
		double getDouble(int ind);

		/**
		 * Returns true if the array wrapper only supports integer values.
		 * @return
		 */
		boolean isIntegerWrapper();

	}

	static class EmptyWrapper implements ArrayWrapper {


		@Override
		public int size() {
			return 0;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public double getDouble(int ind) {
			throw new IndexOutOfBoundsException("Index: " + ind);
		}

		@Override
		public boolean isIntegerWrapper() {
			return true; // Can assume we have integers... since we don't have any non-integers
		}
	}
	
	
	static class UnsignedByteArrayWrapper implements ArrayWrapper {
		
		private final byte[] array;
		
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
		
		private final short[] array;
		
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
		
		private final int[] array;
		
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
		
		private final float[] array;
		
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

	static class ConcatArrayWrapper implements ArrayWrapper  {

		private final List<ArrayWrapper> wrappers;
		private final int[] offsets;
		private final int size;
		private final boolean isIntegerWrapper;

		public ConcatArrayWrapper(Collection<? extends ArrayWrapper> wrappers) {
			this.wrappers = wrappers.stream()
					.filter(w -> !w.isEmpty())
					.map(w -> (ArrayWrapper)w)
					.toList();
			long size = 0;
			int[] offsets = new int[wrappers.size()];
			boolean isIntegerWrapper = true;
			int ind = 0;
			for (var wrapper : wrappers) {
				size += wrapper.size();
				if (size > Integer.MAX_VALUE)
					throw new IllegalArgumentException("Array wrapper size too large");
				offsets[ind] = (int)size;
				if (!wrapper.isIntegerWrapper())
					isIntegerWrapper = false;
				ind++;
			}
			this.isIntegerWrapper = isIntegerWrapper;
			this.offsets = offsets;
			this.size = (int)size;
		}

		@Override
		public final int size() {
			return size;
		}

		@Override
		public final double getDouble(int ind) {
			if (ind < 0 || ind >= size())
				throw new ArrayIndexOutOfBoundsException(ind);
			int wrapperIdx = Arrays.binarySearch(offsets, ind);
			if (wrapperIdx < 0) {
				// We have an insertion index
				wrapperIdx = -wrapperIdx - 1;
				if (wrapperIdx > 0)
					ind = (ind - offsets[wrapperIdx - 1]);
				var wrapper = wrappers.get(wrapperIdx);
				return wrapper.getDouble(ind);
			} else {
				// We have an exact offset value - indicating we are at the start of the next wrapper
				var wrapper = wrappers.get(wrapperIdx+1);
				ind -= offsets[wrapperIdx];
				return wrapper.getDouble(ind);
			}
		}

		@Override
		public boolean isIntegerWrapper() {
			return isIntegerWrapper;
		}

	}

	
	static class DoubleArrayWrapper implements ArrayWrapper  {
		
		private final double[] array;
		
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
