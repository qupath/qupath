/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers.openslide.jna;

/*
 * This class was originally based on the OpenSlide Java (JNI) bindings at https://github.com/openslide/openslide-java
 *
 *  OpenSlide, a library for reading whole slide image files
 *
 *  Copyright (c) 2007-2011 Carnegie Mellon University
 *  All rights reserved.
 *
 *  OpenSlide is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation, version 2.1.
 *
 *  OpenSlide is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with OpenSlide. If not, see
 *  <http://www.gnu.org/licenses/>.
 *
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Minimal Java wrapper for OpenSlide.
 */
public final class OpenSlide implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(OpenSlide.class);

    public static final String PROPERTY_NAME_COMMENT = "openslide.comment";
    public static final String PROPERTY_NAME_VENDOR = "openslide.vendor";
    public static final String PROPERTY_NAME_QUICKHASH1 = "openslide.quickhash-1";
    public static final String PROPERTY_NAME_BACKGROUND_COLOR = "openslide.background-color";
    public static final String PROPERTY_NAME_OBJECTIVE_POWER = "openslide.objective-power";
    public static final String PROPERTY_NAME_MPP_X = "openslide.mpp-x";
    public static final String PROPERTY_NAME_MPP_Y = "openslide.mpp-y";
    public static final String PROPERTY_NAME_BOUNDS_X = "openslide.bounds-x";
    public static final String PROPERTY_NAME_BOUNDS_Y = "openslide.bounds-y";
    public static final String PROPERTY_NAME_BOUNDS_WIDTH = "openslide.bounds-width";
    public static final String PROPERTY_NAME_BOUNDS_HEIGHT = "openslide.bounds-height";

    private final List<String> associatedImages;

    private long osr;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final long[] levelWidths;
    private final long[] levelHeights;
    private final int levelCount;
    private final Map<String, String> properties;

    private OpenSlideJNA jna;

    OpenSlide(OpenSlideJNA jna, String path) throws IOException {
        Objects.requireNonNull(jna, "OpenSlide JNA instance cannot be null");
        this.jna = jna;

        logger.trace("Initializing OpenSlide for {}", path);
        osr = jna.openslide_open(path);

        if (osr == 0) {
            throw new IOException(path + " is not a file that OpenSlide can recognize");
        }

        // Need to dispose on error, we are in the constructor
        try {
            checkError();
        } catch (IOException e) {
            dispose();
            throw e;
        }

        // Store level count
        levelCount = jna.openslide_get_level_count(osr);

        // Store dimensions
        levelWidths = new long[levelCount];
        levelHeights = new long[levelCount];

        for (int i = 0; i < levelCount; i++) {
            long[] w = new long[1], h = new long[1];
            jna.openslide_get_level_dimensions(osr, i, w, h);
            levelWidths[i] = w[0];
            levelHeights[i] = h[0];
        }

        // Cache all available properties
        Map<String, String> props = new LinkedHashMap<>();
        for (String s : jna.openslide_get_property_names(osr)) {
            props.put(s, jna.openslide_get_property_value(osr, s));
        }
        properties = Collections.unmodifiableMap(props);

        // Load associated images now
        associatedImages = new ArrayList<>();
        Collections.addAll(associatedImages, jna.openslide_get_associated_image_names(osr));

        // Dispose on error, we are in the constructor
        try {
            checkError();
        } catch (IOException e) {
            dispose();
            throw e;
        }
    }

    // Call with the reader lock held, or from the constructor
    private void checkError() throws IOException {
        String msg = jna.openslide_get_error(osr);
        if (msg != null) {
            throw new IOException(msg);
        }
    }

    /**
     * Dispose the OpenSlide object; this is equivalent to {@link #close()}.
     */
    public void dispose() {
        Lock wl = lock.writeLock();
        wl.lock();
        try {
            if (osr != 0) {
                jna.openslide_close(osr);
                osr = 0;
            }
        } finally {
            wl.unlock();
        }
    }

    /**
     * Get the total number of pyramid levels.
     * @return
     */
    public int getLevelCount() {
        return levelCount;
    }

    // call with the reader lock held
    private void checkNotDisposed() {
        if (osr == 0) {
            throw new OpenSlideDisposedException();
        }
    }

    /**
     * Get the width of the full-resolution image (level 0).
     * @return
     */
    public long getLevel0Width() {
        return levelWidths[0];
    }

    /**
     * Get the height of the full-resolution image (level 0).
     * @return
     */
    public long getLevel0Height() {
        return levelHeights[0];
    }

    /**
     * Get the image width at the specified level.
     * @param level
     * @return
     */
    public long getLevelWidth(int level) {
        return levelWidths[level];
    }

    /**
     * Get the image height at the specified level.
     * @param level
     * @return
     */
    public long getLevelHeight(int level) {
        return levelHeights[level];
    }

    // takes the reader lock
    public void paintRegionARGB(int[] dest, long x, long y, int level, int w,
                                int h) throws IOException {
        if ((long) w * (long) h > dest.length) {
            throw new ArrayIndexOutOfBoundsException("Size of data ("
                    + dest.length + ") is less than w * h");
        }

        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("w and h must be nonnegative");
        }

        Lock rl = lock.readLock();
        rl.lock();
        try {
            checkNotDisposed();
            jna.openslide_read_region(osr, dest, x, y, level, w, h);
            checkError();
        } finally {
            rl.unlock();
        }
    }

    /**
     * Get an unmodifiable map of all available properties.
     * @return
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Get a named associated image.
     * @param name
     * @return
     * @throws IOException
     */
    public BufferedImage getAssociatedImage(String name) throws IOException {
        Lock rl = lock.readLock();
        rl.lock();
        try {
            checkNotDisposed();

            long[]d0 = new long[1];
            long[]d1 = new long[1];
            jna.openslide_get_associated_image_dimensions(osr, name, d0, d1);
            checkError();
            if (d0[0] == -1) {
                throw new IOException("Failure reading associated image");
            }

            BufferedImage img = new BufferedImage((int) d0[0], (int) d1[0], BufferedImage.TYPE_INT_ARGB_PRE);
            int[] data = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
            jna.openslide_read_associated_image(osr, name, data);
            checkError();
            return img;
        } finally {
            rl.unlock();
        }
    }

    /**
     * Get a list of all associated image names.
     * @return
     */
    public List<String> getAssociatedImages() {
        return associatedImages;
    }

    /**
     * Close; this is important for cleanup.
     */
    @Override
    public void close() {
        dispose();
    }

    /**
     * Request the bytes for an ICC profile.
     * @return the bytes of an ICC profile is available, or null otherwise.
     * @throws UnsupportedOperationException if an unsatisfied link error occurred, which indicates that
     *                                       the OpenSlide version is not compatible (it should be 4.0.0 or greater).
     */
    public byte[] getICCProfileBytes() throws UnsupportedOperationException {
        Lock rl = lock.readLock();
        rl.lock();
        try {
            long size = jna.openslide_get_icc_profile_size(osr);
            if (size > 0) {
                byte[] bytes = new byte[(int) size];
                jna.openslide_read_icc_profile(osr, bytes);
                return bytes;
            } else {
                return null;
            }
        } catch (UnsatisfiedLinkError e) {
            throw new UnsupportedOperationException(
                    "ICC Profile could not be found - OpenSlide version may not be compatible", e);
        } finally {
            rl.unlock();
        }
    }


    /**
     * Exception thrown whenever a request is made after the OpenSlide object has been closed.
     */
    public static class OpenSlideDisposedException extends RuntimeException {

        private static final String MSG = "OpenSlide object has been disposed";

        public OpenSlideDisposedException() {
            super(MSG);
        }
    }

}
