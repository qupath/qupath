/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui.viewer.tools.handlers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;

/**
 * Helper class to manage custom cursors that are associated with some tools.
 */
public abstract class CursorCache<T> {

    private final Cache<T, Cursor> cache;

    public CursorCache() {
        this(10);
    }

    public CursorCache(int cacheSize) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .build();
    }

    Cursor getCursor(T key) {
        var cursor = cache.getIfPresent(key);
        cursor = null; // TODO: Remove this - temporary during development!
        if (cursor instanceof ImageCursor imgCursor) {
            // This looks unnecessary, but without it cursors can become mangled (at least on macOS)
            // See https://github.com/qupath/qupath/issues/194
            return new ImageCursor(imgCursor.getImage(), imgCursor.getHotspotX(), imgCursor.getHotspotY());
        }
        cursor = createCursor(key);
        if (cursor instanceof ImageCursor imgCursor) {
            // TODO: Provide and return default if size requirement can't be met
            var image = imgCursor.getImage();
            var size = ImageCursor.getBestSize(image.getWidth(), image.getHeight());
            System.err.println(size);
        }
        cache.put(key, cursor);
        return cursor;
    }

    protected abstract Cursor createCursor(T key);

}
