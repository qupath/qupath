/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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


package qupath.opencv.dnn;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to represent input and output shapes associated with {@link PredictionFunction}.
 * 
 * In general, the shape is expected to be in the format <b>NCHW</b>.
 * <p>
 * NCHW is used by OpenCV https://docs.opencv.org/4.5.2/d6/d0f/group__dnn.html#ga29f34df9376379a603acd8df581ac8d7
 * and also by PyTorch; for TensorFlow some rearrangement may be needed.
 * <p>
 * Note: NDCHW support may be added in the future, but is not currently supported.
 * @version 0.3.0
 */
public final class DnnShape {
	
	private static final Logger logger = LoggerFactory.getLogger(DnnShape.class);
	
	/**
	 * Constant to represent an unknown shape.
	 */
	public static DnnShape UNKNOWN_SHAPE = new DnnShape("");
	
	/**
	 * Constant to represent an unknown dimension length.
	 */
	public static long UNKNOWN_LENGTH = -1L;

	/**
	 * Constant to represent an unknown size (i.e. number of elements within a blob, mat or tensor).
	 */
	public static long UNKNOWN_SIZE = -1L;

	private final long[] shape;
	
	private DnnShape(String id) {
		if (UNKNOWN_SHAPE != null)
			throw new UnsupportedOperationException("Unknown shape cannot be constructed more than once!");
		this.shape = null;
	}
	
	private DnnShape(long... shape) {
		this.shape = shape.clone();
		if (this.shape.length > 4)
			logger.warn("Number of dimensions is {} (expected 3 or 4)", this.shape.length);
		logger.trace("Creating shape: {}", Arrays.toString(shape));
	}
	
	private DnnShape(int nDims) {
		if (nDims > 4)
			logger.warn("Number of dimensions is {} (expected 3 or 4)", nDims);
		this.shape = new long[nDims];
		Arrays.fill(this.shape, -1);
		logger.trace("Creating shape: {}", Arrays.toString(shape));
	}
	
	/**
	 * Get a copy of the internal dimensions array.
	 * @return
	 */
	public final long[] getShape() {
		return shape == null ? null : shape.clone();
	}
	
	/**
	 * Get the length of the specified dimension.
	 * @param i
	 * @return
	 */
	public final long get(int i) {
		return shape == null ? -1 : shape[i];
	}
	
	/**
	 * Create a new {@link DnnShape} with the specified dimension lengths.
	 * @param shape
	 * @return
	 */
	public static DnnShape of(long... shape) {
		return new DnnShape(shape);
	}
	
	/**
	 * The total number of dimensions, i.e. the length of the array returned by {@link #getShape()}.
	 * @return
	 */
	public final int numDimensions() {
		return shape == null ? 0 : shape.length;
	}
	
	/**
	 * Returns true if the shape is unknown.
	 * @return
	 */
	public final boolean isUnknown() {
		return shape == null;
	}
	
	/**
	 * Total number of elements in a blob with this shape
	 * This is the product of the lengths returned by {@link #getShape()} or {@link #UNKNOWN_SHAPE}.
	 * @return
	 */
	public final long size() {
		if (shape == null)
			return UNKNOWN_SIZE;
		long total = 1L;
		for (long s : shape) {
			if (s == UNKNOWN_LENGTH)
				return UNKNOWN_SIZE;
			total *= s;
		}
		return total;
	}
	
	@Override
	public String toString() {
		return isUnknown() ? "Unknown shape" : Arrays.toString(shape);
	}
	
}