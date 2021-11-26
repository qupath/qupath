/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.common;

import java.awt.Color;

/**
 * Static functions to help work with RGB(A) colors using packed ints.
 * 
 * @author Pete Bankhead
 *
 */
public final class ColorTools {

	// Suppressed default constructor for non-instantiability
	private ColorTools() {
		throw new AssertionError();
	}
	
	/**
	 * Packed int representing white.
	 */
	final public static int WHITE = packRGB(255, 255, 255);

	/**
	 * Packed int representing black.
	 */
	final public static Integer BLACK = packRGB(0, 0, 0);

	/**
	 * Packed int representing red.
	 */
	final public static Integer RED = packRGB(255, 0, 0);

	/**
	 * Packed int representing green.
	 */
	final public static Integer GREEN = packRGB(0, 255, 0);

	/**
	 * Packed int representing blue.
	 */
	final public static Integer BLUE = packRGB(0, 0, 255);

	/**
	 * Packed int representing magenta.
	 */
	final public static Integer MAGENTA = packRGB(255, 0, 255);

	/**
	 * Packed int representing cyan.
	 */
	final public static Integer CYAN = packRGB(0, 255, 255);

	/**
	 * Packed int representing yellow.
	 */
	final public static Integer YELLOW = packRGB(255, 255, 0);

	/**
	 * Mask for use when extracting the alpha component from a packed ARGB int value.
	 */
	final public static Integer MASK_ALPHA = 0xff000000;
	
	/**
	 * Mask for use when extracting the red component from a packed (A)RGB int value.
	 */
	final public static Integer MASK_RED = 0xff0000;
	
	/**
	 * Mask for use when extracting the green component from a packed (A)RGB int value.
	 */
	final public static Integer MASK_GREEN = 0xff00;
	
	/**
	 * Mask for use when extracting the blue component from a packed (A)RGB int value.
	 */
	final public static Integer MASK_BLUE = 0xff;

	/**
	 * Make a packed RGB value from specified input values.
	 * <p>
	 * Input r, g and b should be in the range 0-255 - but no checking is applied.
	 * Rather, the input values are simply shifted as they are.
	 * <p>
	 * The alpha value is 255.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 * @deprecated Use {@link #packRGB(int, int, int)} or {@link #packClippedRGB(int, int, int)} instead.
	 */
	@Deprecated
	public static int makeRGB(int r, int g, int b) {
		return (255<<24) + (r<<16) + (g<<8) + b;
	}
	
	/**
	 * Make a packed RGB value from specified input values.
	 * This is equivalent to an ARGB value with alpha set to 255, following Java {@link Color}.
	 * <p>
	 * Input r, g, and b should be in the range 0-255; only the lower 8 bits are used.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @return packed ARGB value
	 * @see #packClippedRGB(int, int, int)
	 */
	public static int packRGB(int r, int g, int b) {
		return ((255 & 0xff)<<24) + 
			   ((r & 0xff)<<16) + 
			   ((g & 0xff)<<8) + 
			    (b & 0xff);
	}
	
	/**
	 * Make a packed RGB value from specified input values, clipping to the range 0-255.
	 * This is equivalent to an ARGB value with alpha set to 255, following Java {@link Color}.
	 * <p>
	 * Input r, g, and b should be in the range 0-255, but if they are not they are clipped to the closest valid value.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @return packed ARGB value
//	 * @see #packRGB(int, int, int)
	 */
	public static int packClippedRGB(int r, int g, int b) {
		return packRGB(
				   do8BitRangeCheck(r),
				   do8BitRangeCheck(g), 
				   do8BitRangeCheck(b)
				   );
	}

	/**
	 * Make a packed ARGB value from specified input values.
	 * <p>
	 * Input r, g, b and a should be in the range 0-255 - but no checking is applied.
	 * Rather, the input values are simply shifted as they are.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @param a
	 * @return
	 * @deprecated The naming and order of arguments is misleading. The output is a packed ARGB value, 
	 *             but arguments are provided in the order red, green, blue, alpha.
	 * @see #packARGB(int, int, int, int)
	 */
	@Deprecated
	public static int makeRGBA(int r, int g, int b, int a) {
		return (a<<24) + (r<<16) + (g<<8) + b;
	}
	
	/**
	 * Make a packed ARGB value from specified input values.
	 * <p>
	 * Input a, r, g, and b should be in the range 0-255; only the lower 8 bits are used.
	 * <p>
	 * Warning! Note the order of the input values.
	 * This differs from the (deprecated) method {@link #makeRGBA(int, int, int, int)}
	 * 
	 * @param a
	 * @param r
	 * @param g
	 * @param b
	 * @return packed ARGB value
	 * @see #packClippedARGB(int, int, int, int)
	 */
	public static int packARGB(int a, int r, int g, int b) {
		return ((a & 0xff)<<24) + 
			   ((r & 0xff)<<16) + 
			   ((g & 0xff)<<8) + 
				(b & 0xff);
	}
	
	/**
	 * Make a packed ARGB value from specified input values, clipping to the range 0-255.
	 * <p>
	 * Input a, r, g, and b should be in the range 0-255, but if they are not they are clipped to the closest valid value.
	 * <p>
	 * Warning! Note the order of the input values.
	 * This differs from the (deprecated) method {@link #makeRGBA(int, int, int, int)}
	 * 
	 * @param a
	 * @param r
	 * @param g
	 * @param b
	 * @return packed ARGB value
	 * @see #packARGB(int, int, int, int)
	 */
	public static int packClippedARGB(int a, int r, int g, int b) {
		return packARGB(
				do8BitRangeCheck(a), 
			    do8BitRangeCheck(r),
			    do8BitRangeCheck(g), 
			    do8BitRangeCheck(b)
			    );
	}
	
	/**
	 * Clip an input value to be an integer in the range 0-255.
	 * 
	 * @param v
	 * @return
	 */
	public static int do8BitRangeCheck(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}

	/**
	 * Clip an input value to be an integer in the range 0-255 (with rounding down).
	 * 
	 * @param v
	 * @return
	 */
	public static int do8BitRangeCheck(float v) {
		return v < 0 ? 0 : (v > 255 ? 255 : (int)v);
	}

	/**
	 * Clip an input value to be an integer in the range 0-255 (with rounding down).
	 * 
	 * @param v
	 * @return
	 */
	public static int do8BitRangeCheck(double v) {
		return v < 0 ? 0 : (v > 255 ? 255 : (int)v);
	}
	
	/**
	 * Extract the 8-bit alpha value from a packed ARGB value.
	 * 
	 * @param argb
	 * @return
	 */
	public static int alpha(int argb) {
		return (argb >> 24) & 0xff;
	}

	/**
	 * Extract the 8-bit red value from a packed RGB value.
	 * 
	 * @param rgb
	 * @return
	 */
	public static int red(int rgb) {
		return (rgb >> 16) & 0xff;
	}
	
	/**
	 * Extract the 8-bit green value from a packed RGB value.
	 * 
	 * @param rgb
	 * @return
	 */
	public static int green(int rgb) {
		return (rgb >> 8) & 0xff;
	}

	/**
	 * Extract the 8-bit blue value from a packed RGB value.
	 * 
	 * @param rgb
	 * @return
	 */
	public static int blue(int rgb) {
		return (rgb & 0xff);
	}
	
	/**
	 * Scale the RGB channels for a color by a fixed amount.
	 * <p>
	 * This is useful for brightening/darkening an input color.
	 * 
	 * @param rgb
	 * @param scale
	 * @return
	 */
	public static int makeScaledRGB(final int rgb, final double scale) {
		return packRGB(
				(int)Math.min(255, (ColorTools.red(rgb)*scale)),
				(int)Math.min(255, (ColorTools.green(rgb)*scale)),
				(int)Math.min(255, (ColorTools.blue(rgb)*scale)));
	}
	
	/**
	 * Convert a double value to an int, flooring and clipping to the range 0-255.
	 * @param val
	 * @return
	 */
	public static int clip255(double val) {
		return (int)Math.min(255, Math.max(val, 0));
	}

}
