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

public class ChannelSettings {

    private String name;
    private float minDisplay;
    private float maxDisplay;
    private ChannelColor color;
    private boolean isShowing;

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

    public String getName() {
        return name;
    }

    public float getMinDisplay() {
        return minDisplay;
    }

    public float getMaxDisplay() {
        return maxDisplay;
    }

    public ChannelColor getColor() {
        return color;
    }

    public boolean isShowing() {
        return isShowing;
    }
}
