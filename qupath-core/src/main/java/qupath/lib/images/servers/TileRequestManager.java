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

package qupath.lib.images.servers;

import java.util.Collection;

import qupath.lib.regions.RegionRequest;

/**
 * Helper class to manage tile requests from an {@link ImageServer}.
 * <p>
 * The purpose of this is to make it possible to identify the 'optimal' regions to request 
 * for any particular pixels in the image, based on the resolution levels actually present. 
 * <p>
 * By contrast {@link RegionRequest} objects can be used to easily request any pixels at 
 * any desired resolution, but this flexibility means that one can easily requests pixels 
 * in an inefficient way - or even inadvertently request pixels multiple times 
 * (e.g. through rounding errors when requesting pixels at an arbitrary resolution).
 * 
 * @author Pete Bankhead
 *
 */
public interface TileRequestManager {
	
	/**
	 * Get {@link TileRequest} objects for <i>all</i> tiles that this server supports.
	 * <p>
	 * This should return an exhaustive collection of non-overlapping tiles, such that 
	 * making requests for each of these would result in every supported pixel (at every 
	 * resolution) being returned precisely once.
	 * 
	 * @return
	 */
	Collection<TileRequest> getAllTileRequests();
	
	/**
	 * Get {@link TileRequest} objects for all tiles that this server supports 
	 * at the specified resolution level.
	 * 
	 * @param level
	 * @return
	 */
	Collection<TileRequest> getTileRequestsForLevel(int level);
	
	/**
	 * Get a collection of {@link TileRequest} objects necessary to fulfil a specific {@link RegionRequest}. 
	 * 
	 * @param request
	 * @return
	 */
	Collection<TileRequest> getTileRequests(RegionRequest request);
	
	/**
	 * Get the single {@link TileRequest} containing a specified pixel, or null if no such request exists.
	 * 
	 * @param level
	 * @param x
	 * @param y
	 * @param z
	 * @param t
	 * @return
	 */
	TileRequest getTileRequest(int level, int x, int y, int z, int t);
	
}