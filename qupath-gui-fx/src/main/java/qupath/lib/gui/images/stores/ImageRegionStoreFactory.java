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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.prefs.PathPrefs;

/**
 * Factory for creating an ImageRegionStore.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageRegionStoreFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageRegionStoreFactory.class);
	
	/**
	 * Create an {@link ImageRegionStore} with a specified tile cache size, in bytes.
	 * @param tileCacheSizeBytes 
	 * @return
	 */
	public static DefaultImageRegionStore createImageRegionStore(final long tileCacheSizeBytes) {
		return new DefaultImageRegionStore(tileCacheSizeBytes);
	}
	
	
	/**
	 * Create an {@link ImageRegionStore} using a default tile cache size, based upon the available memory and user preferences.
	 * @return
	 */
	public static DefaultImageRegionStore createImageRegionStore() {
		return createImageRegionStore(getTileCacheSizeBytes());
	}
	
	
	/**
	 * Calculate the appropriate tile cache size based upon the user preferences.
	 * @return tile cache size in bytes
	 */
	private static long getTileCacheSizeBytes() {
		// Try to compute a sensible value...
		Runtime rt = Runtime.getRuntime();
		long maxAvailable = rt.maxMemory(); // Max available memory
		if (maxAvailable == Long.MAX_VALUE) {
			logger.warn("No inherent maximum memory set - for caching purposes, will assume 64 GB");
			maxAvailable = 64L * 1024L * 1024L * 1024L;
		}
		double percentage = PathPrefs.tileCachePercentageProperty().get();
		if (percentage < 10) {
			logger.warn("At least 10% of available memory needs to be used for tile caching (you requested {}%)", percentage);
			percentage = 10;
		} else if (percentage > 90) {
			logger.warn("No more than 90% of available memory can be used for tile caching (you requested {}%)", percentage);
			percentage = 90;			
		}
		long tileCacheSize = Math.round(maxAvailable * (percentage / 100.0));
		logger.info(String.format("Setting tile cache size to %.2f MB (%.1f%% max memory)", tileCacheSize/(1024.*1024.), percentage));
		return tileCacheSize;
	}
	
}
