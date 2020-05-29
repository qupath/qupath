/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.common;

/**
 * Core QuPath preferences. Currently these are not persistent, but this behavior may change in the future.
 * 
 * @author Pete Bankhead
 */
public class Prefs {
	
	private static int nThreads = Runtime.getRuntime().availableProcessors() - 1;
	
	/**
	 * Get the requested number of threads to use for parallelization.
	 * @return
	 */
	public static int getNumThreads() {
		return nThreads;
	}

	/**
	 * Set the requested number of threads. This will be clipped to be at least 1.
	 * @param n
	 */
	public static void setNumThreads(int n) {
		nThreads = Math.max(1, n);
	}

}