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

package qupath.lib.images.servers;

/**
 * Image bit-depths and types. Not all may be well-supported; in general, 
 * expected image types are UINT8, UINT16 and FLOAT32.
 * 
 * @author Pete Bankhead
 */
public enum PixelType {
	
	/**
	 * 8-bit unsigned integer
	 */
	UINT8(8, PixelValueType.UNSIGNED_INTEGER, 0, 255),
	/**
	 * 8-bit signed integer
	 */
	INT8(8, PixelValueType.SIGNED_INTEGER, Byte.MIN_VALUE, Byte.MAX_VALUE),
	/**
	 * 16-bit unsigned integer
	 */
	UINT16(16, PixelValueType.UNSIGNED_INTEGER, 0, 65535),
	/**
	 * 16-bit signed integer
	 */
	INT16(16, PixelValueType.SIGNED_INTEGER, Short.MIN_VALUE, Short.MAX_VALUE),
	/**
	 * 32-bit unsigned integer (not supported by BufferedImage)
	 */
	UINT32(32, PixelValueType.UNSIGNED_INTEGER, 0, 4294967295L),
	/**
	 * 32-bit signed integer
	 */
	INT32(32, PixelValueType.SIGNED_INTEGER, Integer.MIN_VALUE, Integer.MAX_VALUE),
	/**
	 * 32-bit floating point
	 */
	FLOAT32(32, PixelValueType.FLOATING_POINT, -Float.MAX_VALUE, Float.MAX_VALUE),
	/**
	 * 64-bit floating point
	 */
	FLOAT64(64, PixelValueType.FLOATING_POINT, -Double.MAX_VALUE, Double.MAX_VALUE);
		
	private int bitsPerPixel;
	private PixelValueType type;
	private Number minValue, maxValue;
	
	private PixelType(int bpp, PixelValueType type, Number minValue, Number maxValue) {
		this.bitsPerPixel = bpp;
		this.type = type;
		this.minValue = minValue;
		this.maxValue = maxValue;
	}
	
	/**
	 * Number of bits per pixel.
	 * @return
	 * 
	 * @see #getBytesPerPixel()
	 */
	public int getBitsPerPixel() {
		return bitsPerPixel;
	}
	
	/**
	 * Get a number representing the minimum value permitted by this type (may be negative).
	 * @return
	 */
	public Number getLowerBound() {
		return minValue;
	}

	/**
	 * Get a number representing the maximum value permitted by this type.
	 * @return
	 */
	public Number getUpperBound() {
		return maxValue;
	}

	/**
	 * Number of bytes per pixel.
	 * @return
	 * 
	 * @see #getBitsPerPixel()
	 */
	public int getBytesPerPixel() {
		return (int)Math.ceil(bitsPerPixel / 8.0);
	}
	
	/**
	 * Returns true if the type is a signed integer representation.
	 * @return
	 */
	public boolean isSignedInteger() {
		return type == PixelValueType.SIGNED_INTEGER;
	}
	
	/**
	 * Returns true if the type is an unsigned integer representation.
	 * @return
	 */
	public boolean isUnsignedInteger() {
		return type == PixelValueType.UNSIGNED_INTEGER;
	}
	
	/**
	 * Returns true if the type is a floating point representation.
	 * @return
	 */
	public boolean isFloatingPoint() {
		return type == PixelValueType.FLOATING_POINT;
	}
	
	private enum PixelValueType {
		SIGNED_INTEGER, UNSIGNED_INTEGER, FLOATING_POINT;
	}

}