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

package qupath.lib.images.stores;

import qupath.lib.images.cache.GenericImageCache;
import qupath.lib.images.servers.ImageServer;

/**
 * Interface to define a store for image tiles.
 * <p>
 * This is used to cache tiles and merge them into larger images, where necessary.
 *
 * @param <T>
 */
interface ImageRegionStore<T> {

	/**
	 * Get a default thumbnail image
	 * @param server
	 * @param zPosition
	 * @param tPosition
	 * @return
	 */
	T getCachedThumbnail(ImageServer<T> server, int zPosition, int tPosition);

	T getThumbnail(ImageServer<T> server, int zPosition, int tPosition);

	/**
	 * Get the cache associated with this store.
	 * @return the cache
	 */
	GenericImageCache<T> getCache();

}