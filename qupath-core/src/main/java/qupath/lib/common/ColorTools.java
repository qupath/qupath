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

package qupath.lib.common;

/**
 * Static functions to help work with RGB(A) colors using packed ints.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorTools {

	/**
	 * Make a packed RGB value from specified input values.
	 * 
	 * Input r, g and b should be in the range 0-255 - but no checking is applied.
	 * Rather, the input values are simply masked and shifted as they are.
	 * 
	 * The alpha value is 255.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static int makeRGB(int r, int g, int b) {
		// TODO: Consider inclusion of alpha
		return (255<<24) + (r<<16) + (g<<8) + b;
	}

	/**
	 * Make a packed RGBA value from specified input values.
	 * 
	 * Input r, g, b and a should be in the range 0-255 - but no checking is applied.
	 * Rather, the input values are simply masked and shifted as they are.
	 * 
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static int makeRGBA(int r, int g, int b, int a) {
		// TODO: Consider inclusion of alpha
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
	 * 
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

	// Masks to extract ARGB values
	final public static int MASK_ALPHA = 0xff000000;
	final public static int MASK_RED = 0xff0000;
	final public static int MASK_GREEN = 0xff00;
	final public static int MASK_BLUE = 0xff;

}
