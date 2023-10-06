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

import com.sun.jna.Library;

/**
 * JNA interface for OpenSlide.
 */
public interface OpenSlideJNA extends Library {

    String openslide_get_version();
    String openslide_detect_vendor(String file);
    long openslide_open(String file);
    int openslide_get_level_count(long osr);
    void openslide_get_level_dimensions(long osr, int level, long[] w, long[] h);
    double openslide_get_level_downsample(long osr, int level);
    void openslide_close(long osr);
    String[] openslide_get_property_names(long osr);
    String openslide_get_property_value(long osr, String name);
    String[] openslide_get_associated_image_names(long osr);
    void openslide_read_region(long osr, int[] dest, long x, long y, int level, long w, long h);
    void openslide_get_associated_image_dimensions(long osr, String name, long[] w, long[] h);
    void openslide_read_associated_image(long osr, String name, int[] dest);
    String openslide_get_error(long osr);

    // New in OpenSlide 4.0.0
    long openslide_get_icc_profile_size(long osr);
    void openslide_read_icc_profile(long osr, byte[] bytes);

}
