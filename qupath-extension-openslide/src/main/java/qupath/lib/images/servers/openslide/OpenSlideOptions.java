/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers.openslide;

/**
 * Helper class to store options related to how image servers using OpenSlide should be created.
 * <p>
 * Note that these options can result in different arguments being used when images are added to a project,
 * but they should not modify images that are opened from previous projects.
 */
public class OpenSlideOptions {

    private static final OpenSlideOptions instance = new OpenSlideOptions();

    private boolean applyIccProfiles = false;

    private OpenSlideOptions() {}

    /**
     * Optionally request that images read using OpenSlide are modified using embedded ICC profiles,
     * where available.
     * @param doApply true if the ICC profile should be applied, false otherwise
     */
    public void setApplyIccProfiles(boolean doApply) {
        applyIccProfiles = doApply;
    }

    /**
     * Query whether ICC profiles should be applied by default when new image servers are created.
     * @return true if ICC profiles should be used, false otherwise
     */
    public boolean doApplyIccProfiles() {
        return applyIccProfiles;
    }

    /**
     * Get the main (singleton) instance of the options.
     * @return
     */
    public static OpenSlideOptions getInstance() {
        return instance;
    }

}
