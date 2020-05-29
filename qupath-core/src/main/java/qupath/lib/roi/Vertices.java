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

import java.util.List;

import qupath.lib.geom.Point2;

/**
 * Interface for defining a class that can store a list of 2D vertices, i.e. x,y coordinates.
 * 
 * @author Pete Bankhead
 *
 */
interface Vertices {

	public abstract boolean isEmpty();

	public abstract int size();

	public abstract float[] getX(float[] xArray);

	public abstract float[] getY(float[] yArray);

	public abstract Point2 get(int idx);

	public abstract float getX(int idx);

	public abstract float getY(int idx);

	public abstract List<Point2> getPoints();
	
	public Vertices duplicate();

	/**
	 * Compact the storage if possible, e.g. by trimming arrays used internally.
	 */
	public abstract void compact();
		
//	public VerticesIterator getIterator();

}