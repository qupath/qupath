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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.cache.GenericImageCache;

import java.util.Objects;


/**
 * A generic wrapper for an image cache that can also standardize requests for thumbnail (overview) images.
 *
 * @param <T> the generic parameter for an image (most likely BufferedImage)
 */
abstract class AbstractImageRegionStore<T> implements ImageRegionStore<T> {

	private static final Logger logger = LoggerFactory.getLogger(AbstractImageRegionStore.class);

	private final GenericImageCache<T> cache;
	private final ThumbnailCache<T> thumbnailCache;

	protected AbstractImageRegionStore(final GenericImageCache<T> cache, final int thumbnailSize) {
		Objects.requireNonNull(cache);
		if (thumbnailSize <= 0) {
			throw new IllegalArgumentException("Thumbnail size must be positive");
		}
		logger.debug("Creating store with cache {}, thumbnail size {}", cache, thumbnailSize);
		this.cache = cache;
		this.thumbnailCache = new ThumbnailCache<>(cache, thumbnailSize);
	}

	/**
	 * Get the tile cache size, in bytes.
	 * Image tiles larger than this cannot be cached.
	 * @return
	 */
	public long getTileCacheSize() {
		return cache.getMaxSizeBytes();
	}

	@Override
	public GenericImageCache<T> getCache() {
		return cache;
	}

	@Override
	public ThumbnailCache<T> getThumbnailCache() {
		return thumbnailCache;
	}
	
}
