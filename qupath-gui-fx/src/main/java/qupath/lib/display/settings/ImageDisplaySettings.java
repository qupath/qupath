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

package qupath.lib.display.settings;

import java.util.Collections;
import java.util.List;

/**
 * A simple class to store the main information needed by QuPath to display an image,
 * in a JSON-friendly form.
 */
public class ImageDisplaySettings {

    private static String DEFAULT_NAME = "Untitled";

    private String name;
    private double gamma = 1.0;
    private boolean invertBackground = false;
    private List<ChannelSettings> channels;

    /**
     * Create a new image display settings object.
     * @param name
     * @param gamma
     * @param invertBackground
     * @param channels
     * @return
     */
    public static ImageDisplaySettings create(String name,
                                              double gamma,
                                              boolean invertBackground,
                                              List<ChannelSettings> channels) {
        var settings = new ImageDisplaySettings();
        settings.name = name;
        settings.gamma = gamma;
        settings.invertBackground = invertBackground;
        settings.channels = channels;
        return settings;
    }

    /**
     * Get the name of the settings.
     * @return
     */
    public String getName() {
        return name == null ? DEFAULT_NAME : name;
    }

    /**
     * Get the requested vamma value for the viewer.
     * @return
     */
    public double getGamma() {
        return gamma;
    }

    /**
     * Get whether the background should be shown 'inverted'.
     * This can make a fluorescence image look more like a brightfield image,
     * and vice versa.
     * @return
     */
    public boolean invertBackground() {
        return invertBackground;
    }

    /**
     * Get all the available channels.
     * @return
     */
    public List<ChannelSettings> getChannels() {
        return channels == null ? Collections.emptyList() : Collections.unmodifiableList(channels);
    }
}
