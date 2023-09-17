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

public class ChannelColor {

    private int red, green, blue;

    public static ChannelColor fromRGB(int red, int green, int blue) {
        ChannelColor color = new ChannelColor();
        color.red = red;
        color.green = green;
        color.blue = blue;
        return color;
    }

    public static ChannelColor fromPackedARGB(int argb) {
        ChannelColor color = new ChannelColor();
        color.red = (argb >> 16) & 0xFF;
        color.green = (argb >> 8) & 0xFF;
        color.blue = argb & 0xFF;
        return color;
    }

    public int getRed() {
        return red;
    }

    public int getGreen() {
        return green;
    }

    public int getBlue() {
        return blue;
    }
}
