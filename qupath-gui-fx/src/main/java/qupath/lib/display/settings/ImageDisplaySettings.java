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

public class ImageDisplaySettings {

    private static String DEFAULT_NAME = "Untitled";

    private String name;
    private double gamma = 1.0;
    private boolean invertBackground = false;
    private List<ChannelSettings> channels;

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

    public String getName() {
        return name == null ? DEFAULT_NAME : name;
    }

    public double getGamma() {
        return gamma;
    }

    public boolean invertBackground() {
        return invertBackground;
    }

    public List<ChannelSettings> getChannels() {
        return channels == null ? Collections.emptyList() : Collections.unmodifiableList(channels);
    }
}
