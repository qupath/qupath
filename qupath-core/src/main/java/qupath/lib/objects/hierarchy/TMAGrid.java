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

package qupath.lib.objects.hierarchy;

import java.io.Serializable;
import java.util.List;

import qupath.lib.objects.TMACoreObject;

/**
 * Interface defining a (rectangular) TMA grid.
 * 
 * @author Pete Bankhead
 *
 */
public interface TMAGrid extends Serializable {
	
	/**
	 * Total number of cores in the TMA grid.
	 * @return
	 */
	public int nCores();
	
	/**
	 * Number of cores along the horizontal axis of the grid.
	 * @return
	 */
	public int getGridWidth();
	
	/**
	 * Number of cores along the vertical axis of the grid.
	 * @return
	 */
	public int getGridHeight();
	
	/**
	 * Retrieve a TMA core based upon its name.
	 * <p>
	 * The behavior is undefined if multiple cores have the same name.
	 * @param coreName
	 * @return
	 */
	public TMACoreObject getTMACore(String coreName);
	
	/**
	 * Get the TMACoreObject for a specified grid location.
	 * @param row
	 * @param col
	 * @return
	 */
	public TMACoreObject getTMACore(int row, int col);

	/**
	 * Get an unmodifiable list of all TMA core objects.
	 * @return
	 */
	public List<TMACoreObject> getTMACoreList();
	
}
