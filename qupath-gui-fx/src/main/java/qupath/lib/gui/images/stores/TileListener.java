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

package qupath.lib.gui.images.stores;

import qupath.lib.regions.ImageRegion;

/**
 * Interface for anything that needs to know when image tiles become available.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface TileListener<T> {
	
	/**
	 * Notify a listener that an image tile has been received read &amp; is available.
	 * 
	 * @param serverPath
	 * @param region
	 * @param tile
	 */
	public void tileAvailable(String serverPath, ImageRegion region, T tile);

	/**
	 * Check if the listener requires a particular tile.
	 * This is primarily designed to deal with asynchronous tile requests;
	 * by the time the server is ready to process the the request, it might be out of date
	 * (e.g. if the user has panned to a different part of the image).
	 * 
	 * A server *may* make use of this function to do a last minute poll of all listeners to
	 * check if the region is required.
	 * Any implementation should return quickly (erring conservatively on the side of 'true' if
	 * the calculations would be prohibitively expensive), since otherwise it doesn't save time
	 * in seeking the tile itself.
	 * 
	 * @param serverPath
	 * @param region
	 * @return
	 */
	public boolean requiresTileRegion(String serverPath, ImageRegion region);

}