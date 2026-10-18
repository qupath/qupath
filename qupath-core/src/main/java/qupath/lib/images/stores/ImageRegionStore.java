/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

/**
 * Interface to wrap around an image tile cache.
 * <p>
 * This can be used to access the main cache, and a second cache used to standardized 'thumbnail' image requests.
 * Implementations determine whether the thumbnail cache stores the thumbnail pixels in the main cache,
 * or separately.
 *
 * @param <T>
 */
interface ImageRegionStore<T> {

	/**
	 * Get the main image cache associated with this store.
	 * @return the cache
	 */
	GenericImageCache<T> getCache();

	/**
	 * Get the cache of thumbnail images.
	 * This is a specialized cache that can only return standardized, low-resolution versions of an image.
	 * @return the thumbnail store
	 */
	ThumbnailCache<T> getThumbnailCache();

}