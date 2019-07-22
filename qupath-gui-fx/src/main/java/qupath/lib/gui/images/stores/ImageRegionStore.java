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

import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

/**
 * Interface to define a store for image tiles.
 * 
 * This is used to cache tiles and merge them into larger images, where necessary.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
interface ImageRegionStore<T> {

	T getCachedThumbnail(ImageServer<T> server, int zPosition, int tPosition);

	void addTileListener(TileListener<T> listener);

	void removeTileListener(TileListener<T> listener);

	T getCachedTile(ImageServer<T> server, RegionRequest request);

	T getThumbnail(ImageServer<T> server, int zPosition, int tPosition, boolean addToCache);

	void clearCacheForServer(ImageServer<T> server);

	void clearCacheForRequestOverlap(RegionRequest request);

	void close();

}