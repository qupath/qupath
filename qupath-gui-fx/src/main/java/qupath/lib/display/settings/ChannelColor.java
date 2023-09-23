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
 * A simple class to store the color of an image channel.
 */
public class ChannelColor {

    private int red, green, blue;

    /**
     * Create a channel color from 8-bit RGB values.
     * @param red
     * @param green
     * @param blue
     * @return
     * @throws IllegalArgumentException if any of the values are outside the range 0 to 255.
     */
    public static ChannelColor fromRGB(int red, int green, int blue) throws IllegalArgumentException {
        ChannelColor color = new ChannelColor();
        color.red = checkValid(red);
        color.green = checkValid(green);
        color.blue = checkValid(blue);
        return color;
    }

    private static int checkValid(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Value must be in the range 0 to 255");
        }
        return value;
    }

    /**
     * Create a channel color from a packed ARGB value.
     * The alpha will be ignored.
     * @param argb
     * @return
     */
    public static ChannelColor fromPackedARGB(int argb) {
        ChannelColor color = new ChannelColor();
        color.red = (argb >> 16) & 0xFF;
        color.green = (argb >> 8) & 0xFF;
        color.blue = argb & 0xFF;
        return color;
    }

    /**
     * Get the red value.
     * @return
     */
    public int getRed() {
        return red;
    }

    /**
     * Get the green value.
     * @return
     */
    public int getGreen() {
        return green;
    }

    /**
     * Get the blue value.
     * @return
     */
    public int getBlue() {
        return blue;
    }
}
