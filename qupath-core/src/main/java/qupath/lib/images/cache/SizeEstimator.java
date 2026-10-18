/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.cache;

/**
 * Implementing classes are able to estimate the size given an object of some type -
 * most likely an image (e.g. AWT BufferedImage or JavaFX Image).  This can be used
 * by a cache to determine the approximate memory requirements of the objects it contains.
 *
 * @param <T> generic type for the image
 */
@FunctionalInterface
public interface SizeEstimator<T> {

	/**
	 * Get the approximate image size, in bytes.
	 * @param value the image
	 * @return the approximate size, in bytes
	 */
	long getApproxImageSize(T value);
	
}