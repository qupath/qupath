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
 * Abstract implementation of a (multidimensional) point.
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPoint implements Point {
	
	@Override
	public double distanceSq(final Point p) {
		if (p.dim() != dim())
			throw new RuntimeException("Cannot measure distance between points with different dimensionality (" + dim() + " and " + p.dim());
		double sum = 0;
		for (int i = 0; i < dim(); i++) {
			double delta = get(i) - p.get(i);
			sum += delta*delta;
		}
		return sum;
	}
	
	@Override
	public double distance(Point p) {
		return Math.sqrt(distanceSq(p));
	}


}
