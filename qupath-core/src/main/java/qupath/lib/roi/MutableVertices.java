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

package qupath.lib.roi;

/**
 * Vertices that can be modified.
 * 
 * @author Pete Bankhead
 *
 */
interface MutableVertices extends Vertices {

	public abstract void clear();

	public abstract void add(double x, double y);

	public abstract void add(float x, float y);

	public abstract void set(int idx, float x, float y);

	public abstract void set(int idx, double x, double y);

	public abstract void close();

	/**
	 * Ensure the minimum capacity is at least equal to the specified parameter.
	 * Note that if N coordinates need to be added, then this method should be called with parameters size() + N.
	 * @param capacity
	 */
	public abstract void ensureCapacity(int capacity);

	public abstract Vertices getVertices();

	public abstract void translate(float dx, float dy);

}