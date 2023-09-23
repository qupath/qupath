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

/**
 * A simple class to store the main information needed by QuPath to display an image channel
 * with a requested color and brightness/contrast setting.
 */
public class ChannelSettings {

    private String name;
    private float minDisplay;
    private float maxDisplay;
    private ChannelColor color;
    private boolean isShowing;

    /**
     * Create a new channel settings object.
     * @param name
     * @param minDisplay
     * @param maxDisplay
     * @param color
     * @param isShowing
     * @return
     */
    public static ChannelSettings create(String name,
                                         float minDisplay,
                                         float maxDisplay,
                                         ChannelColor color,
                                         boolean isShowing) {
        var settings = new ChannelSettings();
        settings.name = name;
        settings.minDisplay = minDisplay;
        settings.maxDisplay = maxDisplay;
        settings.color = color;
        settings.isShowing = isShowing;
        return settings;
    }

    /**
     * Get the name of the channel.
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Get the requested minimum display value for the channel.
     * This should be used with the first entry in any lookup table.
     * @return
     */
    public float getMinDisplay() {
        return minDisplay;
    }

    /**
     * Get the requested maximum display value for the channel,
     * This should be used with the last entry in any lookup table.
     * @return
     */
    public float getMaxDisplay() {
        return maxDisplay;
    }

    /**
     * Get the color used to display the channel, or null if a color is not relevant.
     * @return
     */
    public ChannelColor getColor() {
        return color;
    }

    /**
     * Get whether the channel should be displayed.
     * @return
     */
    public boolean isShowing() {
        return isShowing;
    }
}
