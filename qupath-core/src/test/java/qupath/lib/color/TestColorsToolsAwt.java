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

package qupath.lib.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;

@SuppressWarnings("javadoc")
public class TestColorsToolsAwt {
	
	@Test
	public void test_createIndexColorModel() {
		
		int[] vals = new int[] {0, 31, 127, 150, 255};
		
		// Ensure we have the right start and end colors in our index color models
		for (int r : vals) {
			for (int g : vals) {
				for (int b : vals) {
					
					var cmBlack = ColorToolsAwt.createIndexColorModel(r, g, b, false);
					var cmWhite = ColorToolsAwt.createIndexColorModel(r, g, b, true);
					
					assertEquals(ColorTools.BLACK.intValue(), cmBlack.getRGB(0));
					assertEquals(ColorTools.WHITE.intValue(), cmWhite.getRGB(0));
					
					var rgb = ColorTools.packRGB(r, g, b);
					assertEquals(rgb, cmBlack.getRGB(255));
					assertEquals(rgb, cmWhite.getRGB(255));
				}
			}
		}
		
	}
	
}

