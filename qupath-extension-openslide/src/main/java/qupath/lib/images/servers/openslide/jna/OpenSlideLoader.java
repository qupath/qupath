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

import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Static helper class to manage loading the OpenSlide library.
 */
public class OpenSlideLoader {

    private static final Logger logger = LoggerFactory.getLogger(OpenSlideLoader.class);

    private static OpenSlideJNA INSTANCE;

    private static String LIBRARY_VERSION = null;

    /**
     * Try to load OpenSlide, but don't throw an exception if it fails.
     * @param searchPath optional search paths that may be provided to assist with finding the preferred library.
     * @return true if OpenSlide was loaded successfully, false otherwise.
     */
    public static boolean tryToLoadQuietly(String... searchPath) {
        try {
            return tryToLoad(searchPath);
        } catch (Throwable t) {
            logger.debug("Unable to load OpenSlide", t);
            return false;
        }
    }

    /**
     * Try to load OpenSlide. This is liable to throw an exception if the library cannot be loaded.
     * @param searchPath optional search paths that may be provided to assist with finding the preferred library.
     * @return true if OpenSlide was loaded successfully, false otherwise.
     */
    public static synchronized boolean tryToLoad(String... searchPath) throws UnsatisfiedLinkError {
        if (INSTANCE != null)
            return true;
        INSTANCE = tryToLoadJnaInstance(searchPath);
        return INSTANCE != null;
    }

    private static synchronized OpenSlideJNA tryToLoadJnaInstance(String... searchPath) {
        // if no user-specified paths, try to load from the jar by extracting to a tempfile
        if (searchPath.length == 0 || (searchPath.length == 1 && searchPath[0].isEmpty())) {
            logger.debug("No search path provided; trying to load OpenSlide from packaged jar");
            try {
                File openslideFile = Native.extractFromResourcePath("openslide");
                return Native.load(openslideFile.getAbsolutePath(), OpenSlideJNA.class);
            } catch (IOException e) {
                logger.error("No OpenSlide search path supplied and failed to load OpenSlide from packaged jar! OpenSlide will not work.", e);
            }
        }

        // otherwise, search the user dirs by setting jna.library.path
        String jnaPath = System.getProperty("jna.library.path", null);
        try {
            if (searchPath.length > 0) {
                String path = Arrays.stream(searchPath)
                        .filter(s -> s != null && !s.isBlank())
                        .filter(s -> new File(s).isDirectory())
                        .collect(Collectors.joining(File.pathSeparator));
                if (!path.isBlank())
                    System.setProperty("jna.library.path", String.join(File.pathSeparator, path));
            }
            return Native.load("openslide", OpenSlideJNA.class);
        } finally {
            if (jnaPath == null)
                System.clearProperty("jna.library.path");
            else
                System.setProperty("jna.library.path", jnaPath);
        }
    }

    /**
     * Get the version of the OpenSlide library.
     * This will attempt to load OpenSlide if it is not already available.
     * @return a version string for the library, or null if the library is not available
     */
    public static String getLibraryVersion() {
        if (LIBRARY_VERSION == null) {
            tryToLoadQuietly();
            if (INSTANCE != null)
                LIBRARY_VERSION = INSTANCE.openslide_get_version();
        }
        return LIBRARY_VERSION;
    }

    /**
     * Query whether OpenSlide has already been loaded.
     * This will <i>not</i> attempt to load the library; for that, use #tryToLoadQuietly(), {@link #getLibraryVersion()} 
     * or {@link #tryToLoad(String...)}.
     * @return true if OpenSlide has been loaded successfully, false otherwise
     */
    public static boolean isOpenSlideAvailable() {
        return INSTANCE != null;
    }

    /**
     * Open an image using OpenSlide, returning an OpenSlide instance to access pixels and metadata.
     * <p>
     * This will attempt to load OpenSlide if it has not already been loaded, throwing an {@link IOException} if
     * this fails.
     * Use {@link #isOpenSlideAvailable()} to check whether OpenSlide is available before calling this.
     * @param path the image path (usually an absolute file path)
     * @return an OpenSlide instance
     * @throws IOException if OpenSlide could not be loaded failed to open the file
     */
    public static OpenSlide openImage(String path) throws IOException {
        if (INSTANCE == null)
            tryToLoadQuietly();
        if (INSTANCE == null)
            throw new IOException("OpenSlide library not available");
        return new OpenSlide(INSTANCE, path);
    }

    /**
     * Try to detect the vendor of the image at the specified path.
     * This will attempt to load OpenSlide if it has not already been loaded.
     * @param path the image path (usually an absolute file path)
     * @return a vendor string if available, or null if OpenSlide could not be loaded or does not recognize the file
     */
    public static String detectVendor(String path) {
        if (INSTANCE == null)
            tryToLoadQuietly();
        if (INSTANCE == null)
            return null;
        return INSTANCE.openslide_detect_vendor(path);
    }

}
