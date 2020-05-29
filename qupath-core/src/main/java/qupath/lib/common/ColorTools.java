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

/**
 * Static functions to help work with RGB(A) colors using packed ints.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorTools {
	
	/**
	 * Packed int representing white.
	 */
	final public static int WHITE = makeRGB(255, 255, 255);

	/**
	 * Packed int representing black.
	 */
	final public static Integer BLACK = makeRGB(0, 0, 0);

	/**
	 * Packed int representing red.
	 */
	final public static Integer RED = makeRGB(255, 0, 0);

	/**
	 * Packed int representing green.
	 */
	final public static Integer GREEN = makeRGB(0, 255, 0);

	/**
	 * Packed int representing blue.
	 */
	final public static Integer BLUE = makeRGB(0, 0, 255);

	/**
	 * Packed int representing magenta.
	 */
	final public static Integer MAGENTA = makeRGB(255, 0, 255);

	/**
	 * Packed int representing cyan.
	 */
	final public static Integer CYAN = makeRGB(0, 255, 255);

	/**
	 * Packed int representing yellow.
	 */
	final public static Integer YELLOW = makeRGB(255, 255, 0);

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
	 */
	public static int makeRGB(int r, int g, int b) {
		return (255<<24) + (r<<16) + (g<<8) + b;
	}

	/**
	 * Make a packed RGBA value from specified input values.
	 * <p>
	 * Input r, g, b and a should be in the range 0-255 - but no checking is applied.
	 * Rather, the input values are simply shifted as they are.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @param a
	 * @return
	 */
	// TODO: RENAME! The order here may be misleading...
	public static int makeRGBA(int r, int g, int b, int a) {
		return (a<<24) + (r<<16) + (g<<8) + b;
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
	 * Extract the 8-bit red value from a packed RGB value.
	 * 
	 * @param rgb
	 * @return
	 */
	public static int red(int rgb) {
		return (rgb >> 16) & 0xff;
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
	 * Extract the 8-bit green value from a packed RGB value.
	 * 
	 * @param rgb
	 * @return
	 */
	public static int green(int rgb) {
		return (rgb >> 8) & 0xff;
	}

	/**
	 * Extract the 8-bit alpha value from a packed RGB value.
	 * 
	 * @param rgb
	 * @return
	 */
	public static int alpha(int rgb) {
		return (rgb >> 24) & 0xff;
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
		return makeRGB(
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
