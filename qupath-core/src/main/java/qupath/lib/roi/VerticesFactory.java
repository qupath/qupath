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
 * Factory used to help create vertices objects.
 * 
 * @author Pete Bankhead
 *
 */
class VerticesFactory {

	public static Vertices createVertices(final int capacity) {
		return new DefaultVertices(capacity);
	}

	public static Vertices createMutableVertices() {
		return new DefaultMutableVertices(createVertices(DefaultVertices.DEFAULT_CAPACITY));
	}

	public static Vertices createVertices(final float[] x, final float[] y, final boolean copyArrays) {
		return new DefaultVertices(x, y, copyArrays);
	}

	public static Vertices createVertices() {
		return createVertices(DefaultVertices.DEFAULT_CAPACITY);
	}

	public static Vertices createMutableVertices(final int capacity) {
		return new DefaultMutableVertices(new DefaultVertices(capacity));
	}

	public static Vertices createMutableVertices(final float[] x, final float[] y, final boolean copyArrays) {
		return new DefaultMutableVertices(new DefaultVertices(x, y, copyArrays));
	}

}
