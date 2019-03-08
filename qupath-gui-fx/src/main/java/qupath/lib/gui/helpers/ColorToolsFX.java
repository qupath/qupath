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

package qupath.lib.gui.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javafx.scene.paint.Color;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.objects.helpers.PathObjectColorToolsAwt;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Helper class for converting between packed RGB colors and Java's AWT representation, as well as creating some ColorModels for BufferedImages.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorToolsFX {

	// Shouldn't need to synchronize, because each key value pair should always be a fixed & unique combination?
	// In other words, if the same Integer is used as a key, it will *always* may to a Color object that is the
	// same (in terms of 'equals' - not necessarily reference).
//	private static Map<Integer, Color> colorMap = Collections.synchronizedMap(new HashMap<Integer, Color>());
//	private static Map<Integer, Color> colorMapWithAlpha = Collections.synchronizedMap(new HashMap<Integer, Color>());

	public static final Color TRANSLUCENT_BLACK_FX = Color.rgb(0, 0, 0, 0.5);
	public static final Color TRANSLUCENT_WHITE_FX = Color.rgb(255, 255, 255, 0.5);

	private static Map<Integer, Color> colorMap = new HashMap<Integer, Color>();
	private static Map<Integer, Color> colorMapWithAlpha = new HashMap<Integer, Color>();
	
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
		return getCachedColor(ColorTools.makeRGBA(r, g, b, a), true);
	}

	/**
	 * Get a Color object, possibly from a shared map (used to avoid creating too many objects unnecessarily).
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static Color getCachedColor(final int r, final int g, final int b) {
		return getCachedColor(ColorTools.makeRGB(r, g, b));
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
			Color color;
			if (hasAlpha) {
				color = Color.rgb(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb), ColorTools.alpha(rgb)/255.0);
				colorMapWithAlpha.put(rgb, color);
			} else {
				color = Color.rgb(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
				colorMap.put(rgb, color);
			}
			return color;
		} finally {
			w.unlock();
		}
	}
	
	
	
	public static int getRGB(final Color color) {
		return ColorTools.makeRGB((int)(color.getRed() * 255), (int)(color.getGreen() * 255), (int)(color.getBlue() * 255));
	}
	
	public static int getRGBA(final Color color) {
		return ColorTools.makeRGBA((int)(color.getRed() * 255), (int)(color.getGreen() * 255), (int)(color.getBlue() * 255), (int)(color.getOpacity() * 255));
	}
	
	
	
	
	
	
	
	public static Color getDisplayedColor(final PathObject pathObject) {
		Integer rgb = PathObjectColorToolsAwt.getDisplayedColor(pathObject);
		Color color = getCachedColor(rgb);
		return color;
	}

	public static Color getPathClassColor(final PathClass pathClass) {
		Color color = getCachedColor(pathClass.getColor());
		if (color == null)
			return Color.GRAY;
		return color;
	}

	public static Color getColorWithOpacityFX(Integer rgb, double opacity) {
		if (rgb == null)
			return null;
		if (opacity > 1)
			opacity = 1;
		else if (opacity < 0)
			opacity = 0;
		//		IJ.log("Opacity: " + (int)(opacity * 255 + .5));
		return Color.rgb(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb), opacity);
	}

	public static Color getColorWithOpacity(Color color, double opacity) {
		if (color == null)
			return null;
		if (opacity > 1)
			opacity = 1;
		else if (opacity < 0)
			opacity = 0;
		//		IJ.log("Opacity: " + (int)(opacity * 255 + .5));
		return Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
	}
	

}
