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