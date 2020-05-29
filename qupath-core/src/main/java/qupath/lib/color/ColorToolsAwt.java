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

package qupath.lib.color;

import java.awt.Color;
import java.awt.image.IndexColorModel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import qupath.lib.common.ColorTools;

/**
 * Helper class for converting between packed RGB colors and Java's AWT representation, as well as creating some ColorModels for BufferedImages.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorToolsAwt {

	// Shouldn't need to synchronize, because each key value pair should always be a fixed & unique combination?
	// In other words, if the same Integer is used as a key, it will *always* may to a Color object that is the
	// same (in terms of 'equals' - not necessarily reference).
//	private static Map<Integer, Color> colorMap = Collections.synchronizedMap(new HashMap<>());
//	private static Map<Integer, Color> colorMapWithAlpha = Collections.synchronizedMap(new HashMap<>());

	private static Map<Integer, Color> colorMap = new HashMap<>();
	private static Map<Integer, Color> colorMapWithAlpha = new HashMap<>();
	
	private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private static final Lock r = rwl.readLock();
    private static final Lock w = rwl.writeLock();

	/**
	 * Get a Color object, possibly from a shared map (used to avoid creating too many objects unnecessarily).
	 * @param r
	 * @param g
	 * @param b
	 * @param a
	 * @return
	 */
	public static Color getCachedColor(final int r, final int g, final int b, final int a) {
		return ColorToolsAwt.getCachedColor(ColorTools.makeRGBA(r, g, b, a));
	}

	/**
	 * Get a Color object, possibly from a shared map (used to avoid creating too many objects unnecessarily).
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static Color getCachedColor(final int r, final int g, final int b) {
		return ColorToolsAwt.getCachedColor(ColorTools.makeRGB(r, g, b));
	}

	/**
	 * Get cached color. Assumed not to have alpha set, unless the relevant bits are non-zero.
	 * @param rgb
	 * @return
	 */
	public static Color getCachedColor(final Integer rgb) {
		return getCachedColor(rgb, ColorTools.alpha(rgb.intValue()) > 0);
	}

	/**
	 * Get cached color, explicitly stating whether alpha should be included or not.
	 * @param rgb
	 * @param hasAlpha
	 * @return
	 */
	public static Color getCachedColor(final Integer rgb, final boolean hasAlpha) {
		if (rgb == null)
			return null;
		r.lock();
		try {
			Color color = hasAlpha ? colorMapWithAlpha.get(rgb) : colorMap.get(rgb);
			if (color != null)
				return color;
		} finally {
			r.unlock();
		}
		w.lock();
		try {
			Color color = new Color(rgb, hasAlpha);
			if (hasAlpha)
				colorMapWithAlpha.put(rgb, color);
			else
				colorMap.put(rgb, color);
			return color;
		} finally {
			w.unlock();
		}
	}

	/**
	 * Create an 8-bit ColorModel stretching from black (0) to the specified color (255).
	 * <p>
	 * Based on the getLUTFromColorModel ImageJ function (ij.process.LUT)
	 * 
	 * @param color
	 * @return
	 */
	static IndexColorModel createIndexColorModel(Color color) {
		byte[] rLut = new byte[256];
		byte[] gLut = new byte[256];
		byte[] bLut = new byte[256];
		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();
		double rIncr = ((double)red)/255d;
		double gIncr = ((double)green)/255d;
		double bIncr = ((double)blue)/255d;
		for (int i=0; i<256; ++i) {
			rLut[i] = (byte)(i*rIncr);
			gLut[i] = (byte)(i*gIncr);
			bLut[i] = (byte)(i*bIncr);
		}
		return new IndexColorModel(8, 256, rLut, gLut, bLut);
	}



	static IndexColorModel getIndexColorModel(final StainVector stain, boolean whiteBackground) {
		if (!whiteBackground)
			return createIndexColorModel(new Color(stain.getColor()));
		double r = stain.getRed();
		double g = stain.getGreen();
		double b = stain.getBlue();
		byte[] r2 = new byte[256];
		byte[] g2 = new byte[256];
		byte[] b2 = new byte[256];
		for (int i = 0; i < 256; i++) {
			r2[i] = (byte)ColorTools.clip255(255.0 - r * i);
			g2[i] = (byte)ColorTools.clip255(255.0 - g * i);
			b2[i] = (byte)ColorTools.clip255(255.0 - b * i);
			//		r2[i] = (byte)clip255(Math.exp(-r) * (255 - i));
			//		g2[i] = (byte)clip255(Math.exp(-g) * (255 - i));
			//		b2[i] = (byte)clip255(Math.exp(-b) * (255 - i));
		}
		return new IndexColorModel(8, 256, r2, g2, b2);		
	}
	
	
	static IndexColorModel createHueColorModel() {
		byte[] r2 = new byte[256];
		byte[] g2 = new byte[256];
		byte[] b2 = new byte[256];
		for (int i = 0; i < 256; i++) {
			int rgb = Color.HSBtoRGB(i/255f, 1f, 0.75f);
			r2[i] = (byte)ColorTools.red(rgb);
			g2[i] = (byte)ColorTools.green(rgb);
			b2[i] = (byte)ColorTools.blue(rgb);
		}
		return new IndexColorModel(8, 256, r2, g2, b2);		
	}
	
	/**
	 * Create an IndexColorModel, ranging from white (low values) to a stain vector color (high values).
	 * 
	 * @param stain
	 * @return
	 */
	public static IndexColorModel getIndexColorModel(final StainVector stain) {
		return getIndexColorModel(stain, true);
	}

	private static Map<Color, Color> colorsTransparent = Collections.synchronizedMap(new HashMap<>());
	private static Map<Color, Color> colorsDarkened = Collections.synchronizedMap(new HashMap<>());
	private static Map<Color, Color> colorsMoreTransparent = Collections.synchronizedMap(new HashMap<>());
	
	/**
	 * White, with 50% opacity.
	 */
	public static final Color TRANSLUCENT_WHITE = new Color(255, 255, 255, 128);
	
	/**
	 * Black, with 50% opacity.
	 */
	public static final Color TRANSLUCENT_BLACK = new Color(0, 0, 0, 128);

	/**
	 * Get a (slightly more) translucent version of the specified color.
	 * <p>
	 * If possible, a cached version will be used.
	 * 
	 * @param color
	 * @return
	 * 
	 * @see #getTranslucentColor
	 */

	public static Color getMoreTranslucentColor(Color color) {
		Color colorTranslucent = colorsMoreTransparent.get(color);
		if (colorTranslucent == null) {
			colorTranslucent = getColorWithOpacity(color, 0.3);
			colorsMoreTransparent.put(color, colorTranslucent);
		}
		return colorTranslucent;
	}

	/**
	 * Get a (slightly) darker version of the specified color.
	 * <p>
	 * If possible, a cached version will be used.
	 * 
	 * @param color
	 * @return
	 */
	public static Color darkenColor(Color color) {
		Color colorDarkened = colorsDarkened.get(color);
		if (colorDarkened == null) {
			colorDarkened = scaleColor(color, 0.8);
			colorsDarkened.put(color, colorDarkened);
		}
		return colorDarkened;
	}

	/**
	 * Get a (slightly) translucent version of the specified color.
	 * <p>
	 * If possible, a cached version will be used.
	 * 
	 * @param color
	 * @return
	 */
	public static Color getTranslucentColor(Color color) {
		Color colorTranslucent = colorsTransparent.get(color);
		colorsTransparent.clear();
		if (colorTranslucent == null) {
			colorTranslucent = getColorWithOpacity(color, 0.8);
			colorsTransparent.put(color, colorTranslucent);
		}
		return colorTranslucent;
	}

	/**
	 * Get a scaled version of the specified color, where the RGB values are independently scaled by a specified factor.
	 * <p>
	 * The alpha value is preserved unchanged.
	 * 
	 * @param color
	 * @param factor 
	 * @return
	 */
	public static Color scaleColor(Color color, double factor) {
		return new Color(ColorTools.do8BitRangeCheck(color.getRed()*factor),
				ColorTools.do8BitRangeCheck(color.getGreen()*factor),
				ColorTools.do8BitRangeCheck(color.getBlue()*factor),
				color.getAlpha());
	}

	
	/**
	 * Get a color with a specified opacity, based on the packed RGB values in an Integer.
	 * 
	 * @param rgb
	 * @param opacity
	 * @return
	 */
	public static Color getColorWithOpacity(Integer rgb, double opacity) {
		if (rgb == null)
			return null;
		if (opacity > 1)
			opacity = 1;
		else if (opacity < 0)
			opacity = 0;
		//		IJ.log("Opacity: " + (int)(opacity * 255 + .5));
		return new Color(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb), (int)(opacity * 255 + .5));
	}

	/**
	 * Get a color with a specified opacity, setting the alpha channel accordingly.
	 * 
	 * @param color
	 * @param opacity
	 * @return
	 */
	public static Color getColorWithOpacity(Color color, double opacity) {
		if (color == null)
			return null;
		if (opacity > 1)
			opacity = 1;
		else if (opacity < 0)
			opacity = 0;
		//		IJ.log("Opacity: " + (int)(opacity * 255 + .5));
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(opacity * 255 + .5));
	}
	

}
