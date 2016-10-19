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


package qupath.lib.gui.tma.entries;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.image.Image;

/**
 * Image cache for storing images related to TMAEntries.
 * 
 * This stores small images until they are cleared, while larger images are kept only was weak references - 
 * so can 'disappear' during garbage collection if they are no longer needed.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAImageCache {
	
	private int maxSmallWidth;
	
	private Map<TMAEntry, Image> imageSmall = Collections.synchronizedMap(new HashMap<>());
	private Map<TMAEntry, SoftReference<Image>> imageLarge = Collections.synchronizedMap(new HashMap<>());

	private Map<TMAEntry, Image> overlaySmall = Collections.synchronizedMap(new HashMap<>());
	private Map<TMAEntry, SoftReference<Image>> overlayLarge = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Create an image cache, with the specified maximum image width used to define what is a 'small image'.
	 * 
	 * Small images are cached until the cache is cleared, while large images may be discarded at any time 
	 * (and therefore require reloading).
	 * 
	 * @param maxSmallWidth
	 */
	public TMAImageCache(final int maxSmallWidth) {
		this.maxSmallWidth = maxSmallWidth;
	}
	
	
	public Image getImage(final TMAEntry entry, final double maxWidth) {
		if (!entry.hasImage())
			return null;
		return getCachedImage(entry, maxWidth, false);
	}
	
	
	public Image getOverlay(final TMAEntry entry, final double maxWidth) {
		if (!entry.hasOverlay())
			return null;
		return getCachedImage(entry, maxWidth, true);		
	}
	
	
	private Image getCachedImage(final TMAEntry entry, final double maxWidth, final boolean isOverlay) {
		boolean isSmall = maxWidth > 0 && maxWidth <= maxSmallWidth;
		if (isSmall)
			return getSmallCachedImage(entry, isOverlay);
		else
			return getLargeCachedImage(entry, isOverlay);
	}
	
	
	public void clear() {
		imageSmall.clear();
		imageLarge.clear();
		overlaySmall.clear();
		overlayLarge.clear();
	}
	
	
	private Image getLargeCachedImage(final TMAEntry entry, final boolean isOverlay) {
		Map<TMAEntry, SoftReference<Image>> cache = isOverlay ? overlayLarge : imageLarge;
		SoftReference<Image> ref = cache.get(entry);
		Image img = ref == null ? null : ref.get();
		if (img == null) {
			img = isOverlay ? entry.getOverlay(-1) : entry.getImage(-1);
			if (img != null) {
				cache.put(entry, new SoftReference<>(img));
			}
		}
		return img;
	}
	
	
	private Image getSmallCachedImage(final TMAEntry entry, final boolean isOverlay) {
		Map<TMAEntry, Image> cache = isOverlay ? overlaySmall : imageSmall;
		Image img = cache.get(entry);
		if (img == null) {
			img = isOverlay ? entry.getOverlay(maxSmallWidth) : entry.getImage(maxSmallWidth);
			if (img != null) {
				cache.put(entry, img);
			}
		}
		return img;
	}
	
	
	
	
//	private Image getCachedImage(final TMAEntry entry, final int maxWidth, final boolean isOverlay) {
//		boolean isSmall = maxWidth > 0 && maxWidth <= maxSmallWidth;
//		Map<TMAEntry, WeakReference<Image>> cache;
//		if (isOverlay) {
//			cache = isSmall ?  overlaySmall : overlayLarge;
//		} else {
//			cache = isSmall ?  imageSmall : imageLarge;
//		}
//		WeakReference<Image> ref = cache.get(entry);
//		Image img = ref == null ? null : ref.get();
//		if (img == null) {
//			img = isOverlay ? entry.getOverlay(isSmall ? maxSmallWidth : -1) : entry.getImage(isSmall ? maxSmallWidth : -1);
//			if (img != null) {
//				cache.put(entry, new WeakReference<>(img));
//			}
//		}
//		return img;
//	}
	

}