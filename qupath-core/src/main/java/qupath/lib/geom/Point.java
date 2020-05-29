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
 * Simple interface defining a point.
 * <p>
 * Warning: This may change in the future (by being extended), so developers are advised not to subclass it...
 * 
 * @author Pete Bankhead
 *
 */
public interface Point {

	/**
	 * Number of values used to represent this point.
	 * <p>
	 * For an x,y coordinate pair this should return 2.
	 * @return
	 */
	public int dim();

	/**
	 * Get the value of the ordinate for the specified dimension.
	 * @param dim
	 * @return
	 */
	public double get(final int dim);
	
	/**
	 * Calculate the squared distance to another point, with the same {@link #dim()}.
	 * @param p
	 * @return
	 * 
	 * @see #distance(Point)
	 */
	public double distanceSq(final Point p);
	
	/**
	 * Calculate the distance to another point, with the same {@link #dim()}.
	 * @param p
	 * @return
	 * 
	 * @see #distanceSq(Point)
	 */
	public double distance(final Point p);

}
