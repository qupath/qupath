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

package qupath.lib.objects.helpers;

import java.awt.Color;

import qupath.lib.awt.color.ColorToolsAwt;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Static helper methods when working with PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectColorToolsAwt {
	
	private PathObjectColorToolsAwt() {}

	public static Color getDisplayedColorAWT(final PathObject pathObject) {
		Integer rgb = PathObjectColorToolsAwt.getDisplayedColor(pathObject);
		Color color = ColorToolsAwt.getCachedColor(rgb);
		return color;
	}

	public static Color getPathClassColorAWT(final PathClass pathClass) {
		Color color = ColorToolsAwt.getCachedColor(pathClass.getColor());
		if (color == null)
			return Color.GRAY;
		return color;
	}

	/**
	 * Get the color with which an object should be displayed.
	 * 
	 * This could be stored internally, or obtained from its PathClass.
	 * 
	 * If neither of these produces a result, a default color will be returned based on PathPrefs
	 * for the specific (Java) class of the PathObject.
	 * 
	 * Assuming PathPrefs does not contain any nulls, this will therefore not return nulls either.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static Integer getDisplayedColor(final PathObject pathObject) {
		// Check if any color has been set - if so, return it
		Integer color = pathObject.getColorRGB();
		if (color != null)
			return color;
		// Check if any class has been set, if so then use its color
		PathClass pathClass = pathObject.getPathClass();
		if (pathClass != null)
			color = pathClass.getColor();
		if (color != null)
			return color;
	
		if (pathObject instanceof PathTileObject)
			return PathPrefs.getTileColor();
		if (pathObject instanceof TMACoreObject) {
			if (((TMACoreObject)pathObject).isMissing())
				return PathPrefs.getTMACoreMissingColor();
			else
				return PathPrefs.getTMACoreColor();
		}
		return PathPrefs.getColorDefaultAnnotations();
	}


}
