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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import qupath.lib.regions.RegionRequest;

/**
 * Map for storing image tiles, which automatically removes tiles that have not been accessed
 * in a while after it reaches a maximum number of tiles, or maximum memory consumption.
 * 
 * The memory consumption estimate is based on the bit-depth of the image &amp; number of pixels it contains
 * (other overhead is ignored).
 * 
 * @author Pete Bankhead
 *
 */
class DefaultRegionCache<T> implements Map<RegionRequest, T> {

	private Map<RegionRequest, T> map;
	private final SizeEstimator<T> sizeEstimator;
	private int maxCapacity;
	private int nonNullSize = 0;
	private long maxMemoryBytes;
	private long memoryBytes = 0; // Rely on synchronization to control access to map anyway, so no need for atomic...?

	DefaultRegionCache(final SizeEstimator<T> sizeEstimator, final int maxCapacity, final long maxSizeBytes) {
		this.maxMemoryBytes = maxSizeBytes;
		this.sizeEstimator = sizeEstimator;
		this.maxCapacity = maxCapacity;
		map = new LinkedHashMap<RegionRequest, T>(maxCapacity+1, 2f, true) {

			private static final long serialVersionUID = 1L;

			@Override
			protected synchronized boolean removeEldestEntry(Map.Entry<RegionRequest, T> eldest) {
				// Remove if the map is full (in terms of numbers), or occupying too much memory
				boolean doRemove = nonNullSize >= maxCapacity || memoryBytes > maxMemoryBytes;
				if (doRemove) {
					memoryBytes = memoryBytes - sizeEstimator.getApproxImageSize(eldest.getValue());
					if (eldest.getValue() != null)
						nonNullSize--;
//					if (getApproxImageSize(eldest.getValue()) > 10784000)
//											logger.info(String.format("REMOVED! %.2f MB remaining, %d images", memoryBytes/(1024. * 1024.), size()));
				}
				return doRemove;
			}

		}; // Should never have to resize the cache, so loadfactor is > 1
		//			logger.info("Max capacity: " + maxCapacity);
		//			logger.info("Max size: " + maxSizeBytes);
		map = Collections.synchronizedMap(map);
	}

	DefaultRegionCache(final SizeEstimator<T> sizeEstimator, long maxSizeBytes) {
		this(sizeEstimator, Math.max(200, (int)(maxSizeBytes / (256 * 256 * 4) + 10)), maxSizeBytes);
	}

//	synchronized void clearCacheForServer(ImageServer<?> server) {
//		Iterator<Entry<RegionRequest, T>> iter = map.entrySet().iterator();
//		while (iter.hasNext()) {
//			Entry<RegionRequest, T> entry = iter.next();
//			if (entry.getKey().getPath().equals(server.getPath())) {
//				memoryBytes -= sizeEstimator.getApproxImageSize(entry.getValue());
//				if (entry.getValue() != null)
//					nonNullSize--;
//				iter.remove();
//			}
//		}
//	}
//	
//	
//	synchronized void clearCacheForRequestOverlap(RegionRequest request) {
//		Iterator<Entry<RegionRequest, T>> iter = map.entrySet().iterator();
//		while (iter.hasNext()) {
//			Entry<RegionRequest, T> entry = iter.next();
//			if (request.overlapsRequest(entry.getKey())) {
//				memoryBytes -= sizeEstimator.getApproxImageSize(entry.getValue());
//				if (entry.getValue() != null)
//					nonNullSize--;
//				iter.remove();
//			}
//		}
//	}

	/* (non-Javadoc)
	 * @see qupath.lib.images.stores.RegionCache#put(qupath.lib.regions.RegionRequest, T)
	 */
	@Override
	public synchronized T put(RegionRequest request, T img) {
		// Update the memory requirements
		T imgPrevious = map.put(request, img);
		if (img != null) {
			memoryBytes += sizeEstimator.getApproxImageSize(img);
			nonNullSize++;
		}
		if (imgPrevious != null) {
			memoryBytes -= sizeEstimator.getApproxImageSize(imgPrevious);
			nonNullSize--;
		}
//		System.err.println(
//				String.format("Cache: %d entries, %.1f/%.1f GB, %.1f%%", map.size(),
//						memoryBytes/1024.0/1024.0/1024.0,
//						maxMemoryBytes/1024.0/1024.0/1024.0,
//						memoryBytes * 100.0/maxMemoryBytes));
//		else
//			System.out.println("PUTTING NEW: " + nonNullSize + ", " + request + ", " + Thread.currentThread());
		return imgPrevious;
	}

	@Override
	public synchronized void clear() {
		memoryBytes = 0;
		nonNullSize = 0;
		map.clear();
	}
	
	
	@Override
	public String toString() {
		return String.format("Cache: %d (%d/%d non-null), %s", map.size(), nonNullSize, maxCapacity, map.toString());
	}

	@Override
	public synchronized int size() {
		return map.size();
	}

	@Override
	public synchronized boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public synchronized boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public synchronized boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public synchronized T get(Object key) {
		return map.get(key);
	}

	@Override
	public synchronized T remove(Object key) {
		// Update the memory requirements
		T imgPrevious = map.remove(key);
		if (imgPrevious != null) {
			memoryBytes -= sizeEstimator.getApproxImageSize(imgPrevious);
			nonNullSize--;
		}
		return imgPrevious;
	}

	@Override
	public synchronized void putAll(Map<? extends RegionRequest, ? extends T> m) {
		for (Entry<? extends RegionRequest, ? extends T> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public synchronized Set<RegionRequest> keySet() {
		return map.keySet();
	}

	@Override
	public synchronized Collection<T> values() {
		return map.values();
	}

	@Override
	public synchronized Set<Entry<RegionRequest, T>> entrySet() {
		return map.entrySet();
	}
	

}