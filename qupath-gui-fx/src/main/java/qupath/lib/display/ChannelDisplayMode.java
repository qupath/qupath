/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


package qupath.lib.display;

/**
 * Display mode for an image channel, used in combination with {@link ImageDisplay} and {@link ChannelDisplayInfo}.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 * 
 * @see ImageDisplay
 * @see ChannelDisplayInfo
 */
public enum ChannelDisplayMode {
	
	/**
	 * Show using default color LUT (may be composite)
	 */
	COLOR(false),
	
	/**
	 * Show using color LUT with an inverted background
	 */
	INVERTED_COLOR(true),
	
	/**
	 * Show using a grayscale LUT (black to white)
	 */
	GRAYSCALE(false),
	
	/**
	 * Show using an inverted grayscale LUT (white to black)
	 */
	INVERTED_GRAYSCALE(true);
	
	private boolean invertColors;
	
	private ChannelDisplayMode(boolean invertColors) {
		this.invertColors = invertColors;
	}
	
	public boolean invertColors() {
		return invertColors;
	}
	
}