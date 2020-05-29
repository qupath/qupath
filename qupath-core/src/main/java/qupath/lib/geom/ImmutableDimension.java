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

package qupath.lib.geom;

/**
 * An immutable alternative to Java's AWT Dimension.
 * 
 * @author Pete Bankhead
 *
 */
public class ImmutableDimension {
	
	/**
	 * Width of the ImmutableDimension.
	 */
	final public int width;
	
	/**
	 * Height of the ImmutableDimension.
	 */
	final public int height;
	
	/**
	 * Constructor for a new ImmutableDimension.
	 * @param width
	 * @param height
	 */
	private ImmutableDimension(final int width, final int height) {
		this.width = width;
		this.height = height;
	}
	
	/**
	 * Get an ImmutableDimension representing the specified width and height.
	 * @param width
	 * @param height
	 * @return
	 */
	public static ImmutableDimension getInstance(final int width, final int height) {
		return new ImmutableDimension(width, height);
	}
	
	/**
	 * Get the ImmutableDimension width.
	 * @return
	 */
	public int getWidth() {
		return width;
	}
	
	/**
	 * Get the ImmutableDimension height.
	 * @return
	 */
	public int getHeight() {
		return height;
	}
	
}