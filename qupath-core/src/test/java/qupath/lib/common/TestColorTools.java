/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestColorTools {
	
	@Test
	public void test_packRGB() {
		assertEquals(ColorTools.packRGB(255, 255, 255), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packRGB(255, 255, 255), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packRGB(0, 255, 255), Integer.parseUnsignedInt("ff00ffff", 16));
		assertEquals(ColorTools.packRGB(255, 0, 255), Integer.parseUnsignedInt("ffff00ff", 16));
		assertEquals(ColorTools.packRGB(255, 255, 0), Integer.parseUnsignedInt("ffffff00", 16));
		assertEquals(ColorTools.packRGB(0, 0, 0), Integer.parseUnsignedInt("ff000000", 16));
		assertEquals(ColorTools.packRGB(255, 0, 0), Integer.parseUnsignedInt("ffff0000", 16));
		assertEquals(ColorTools.packRGB(1, 2, 3), Integer.parseUnsignedInt("ff010203", 16));
		
		// Check how out of range values are handled
		// The documented method is to use only the lower 8 bits
		assertEquals(ColorTools.packRGB(256, 256, 256), Integer.parseUnsignedInt("ff000000", 16));
		assertEquals(ColorTools.packRGB(511, 511, 511), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packRGB(512, 512, 512), Integer.parseUnsignedInt("ff000000", 16));
	}
	
	@Test
	public void test_packClippedRGB() {
		assertEquals(ColorTools.packClippedRGB(255, 255, 255), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packClippedRGB(255, 255, 255), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packClippedRGB(0, 255, 255), Integer.parseUnsignedInt("ff00ffff", 16));
		assertEquals(ColorTools.packClippedRGB(255, 0, 255), Integer.parseUnsignedInt("ffff00ff", 16));
		assertEquals(ColorTools.packClippedRGB(255, 255, 0), Integer.parseUnsignedInt("ffffff00", 16));
		assertEquals(ColorTools.packClippedRGB(0, 0, 0), Integer.parseUnsignedInt("ff000000", 16));
		assertEquals(ColorTools.packClippedRGB(255, 0, 0), Integer.parseUnsignedInt("ffff0000", 16));
		assertEquals(ColorTools.packClippedRGB(1, 2, 3), Integer.parseUnsignedInt("ff010203", 16));
		
		// Check how out of range values are handled
		// The documented method is to use only the lower 8 bits
		assertEquals(ColorTools.packClippedRGB(256, 256, 256), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packClippedRGB(511, 511, 511), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packClippedRGB(512, 512, 512), Integer.parseUnsignedInt("ffffffff", 16));
	}
	
	@Test
	public void test_packARGB() {
		assertEquals(ColorTools.packARGB(255, 255, 255, 255), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packARGB(0, 255, 255, 255), Integer.parseUnsignedInt("00ffffff", 16));
		assertEquals(ColorTools.packARGB(255, 0, 255, 255), Integer.parseUnsignedInt("ff00ffff", 16));
		assertEquals(ColorTools.packARGB(255, 255, 0, 255), Integer.parseUnsignedInt("ffff00ff", 16));
		assertEquals(ColorTools.packARGB(255, 255, 255, 0), Integer.parseUnsignedInt("ffffff00", 16));
		assertEquals(ColorTools.packARGB(255, 0, 0, 0), Integer.parseUnsignedInt("ff000000", 16));
		assertEquals(ColorTools.packARGB(0, 255, 0, 0), Integer.parseUnsignedInt("00ff0000", 16));
		assertEquals(ColorTools.packARGB(0, 1, 2, 3), Integer.parseUnsignedInt("00010203", 16));
		
		// Check how out of range values are handled
		// The documented method is to use only the lower 8 bits
		assertEquals(ColorTools.packARGB(256, 256, 256, 256), Integer.parseUnsignedInt("00000000", 16));
		assertEquals(ColorTools.packARGB(0, 511, 511, 511), Integer.parseUnsignedInt("00ffffff", 16));
		assertEquals(ColorTools.packARGB(0, 512, 512, 512), Integer.parseUnsignedInt("00000000", 16));
	}
	
	@Test
	public void test_packClippedARGB() {
		assertEquals(ColorTools.packClippedARGB(255, 255, 255, 255), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packClippedARGB(0, 255, 255, 255), Integer.parseUnsignedInt("00ffffff", 16));
		assertEquals(ColorTools.packClippedARGB(255, 0, 255, 255), Integer.parseUnsignedInt("ff00ffff", 16));
		assertEquals(ColorTools.packClippedARGB(255, 255, 0, 255), Integer.parseUnsignedInt("ffff00ff", 16));
		assertEquals(ColorTools.packClippedARGB(255, 255, 255, 0), Integer.parseUnsignedInt("ffffff00", 16));
		assertEquals(ColorTools.packClippedARGB(255, 0, 0, 0), Integer.parseUnsignedInt("ff000000", 16));
		assertEquals(ColorTools.packClippedARGB(0, 255, 0, 0), Integer.parseUnsignedInt("00ff0000", 16));
		assertEquals(ColorTools.packClippedARGB(0, 1, 2, 3), Integer.parseUnsignedInt("00010203", 16));
		
		// Check how out of range values are handled
		// The documented method is to use only the lower 8 bits
		assertEquals(ColorTools.packClippedARGB(256, 256, 256, 256), Integer.parseUnsignedInt("ffffffff", 16));
		assertEquals(ColorTools.packClippedARGB(0, 511, 511, 511), Integer.parseUnsignedInt("00ffffff", 16));
		assertEquals(ColorTools.packClippedARGB(0, 512, 512, 512), Integer.parseUnsignedInt("00ffffff", 16));
	}
	
	@Test
	public void test_do8BitRangeCheck() {
		// int
		assertEquals(0, ColorTools.do8BitRangeCheck(-5));
		assertEquals(0, ColorTools.do8BitRangeCheck(0));
		assertEquals(255, ColorTools.do8BitRangeCheck(256));
		assertEquals(255, ColorTools.do8BitRangeCheck(255));
		assertEquals(0, ColorTools.do8BitRangeCheck(-5));
		
		// float
		assertEquals(0, ColorTools.do8BitRangeCheck(-5.0));
		assertEquals(0, ColorTools.do8BitRangeCheck(0.0));
		assertEquals(255, ColorTools.do8BitRangeCheck(256.0));
		assertEquals(255, ColorTools.do8BitRangeCheck(255.0));
		assertEquals(0, ColorTools.do8BitRangeCheck(-5.0));
		
		// double
		assertEquals(0, ColorTools.do8BitRangeCheck((float)-5));
		assertEquals(0, ColorTools.do8BitRangeCheck((float)0));
		assertEquals(255, ColorTools.do8BitRangeCheck((float)256));
		assertEquals(255, ColorTools.do8BitRangeCheck((float)255));
		assertEquals(0, ColorTools.do8BitRangeCheck((float)-5));
	}
	
	@Test
	public void test_red() {
		assertEquals(125, ColorTools.red(ColorTools.packRGB(125, 0, 250)));
		assertEquals(125, ColorTools.red(ColorTools.packRGB(125, 0, 250)));
		assertEquals(0, ColorTools.red(ColorTools.packRGB(0, 125, 200)));
		assertEquals(255, ColorTools.red(ColorTools.YELLOW));
	}
	
	@Test
	public void test_green() {
		assertEquals(0, ColorTools.green(ColorTools.packRGB(125, 0, 250)));
		assertEquals(125, ColorTools.green(ColorTools.packRGB(0, 125, 200)));
		assertEquals(255, ColorTools.green(ColorTools.YELLOW));
	}

	@Test
	public void test_blue() {
		assertEquals(250, ColorTools.blue(ColorTools.packRGB(125, 0, 250)));
		assertEquals(200, ColorTools.blue(ColorTools.packRGB(0, 125, 200)));
		assertEquals(0, ColorTools.blue(ColorTools.YELLOW));
	}
	
	@Test
	public void test_alpha() {
		assertEquals(0, ColorTools.alpha(ColorTools.packARGB(0, 125, 0, 250)));
		assertEquals(255, ColorTools.alpha(ColorTools.packARGB(255, 125, 0, 250)));
		assertEquals(125, ColorTools.alpha(ColorTools.packARGB(125, 125, 0, 250)));
		assertEquals(0, ColorTools.blue(ColorTools.YELLOW));
	}
	
	@Test
	public void test_makeScaledRGB() {
		int r = 0;
		int g = 125;
		int b = 255;
		double scale1 = 2.0;
		double scale2 = 1.5;
		assertEquals(ColorTools.packRGB((int)(r*scale1), (int)(g*scale1), b), ColorTools.makeScaledRGB(ColorTools.packRGB(r, g, b), scale1));
		assertEquals(ColorTools.packRGB((int)(r*scale2), (int)(g*scale2), b), ColorTools.makeScaledRGB(ColorTools.packRGB(r, g, b), scale2));
	}
	
	@Test
	public void test_clip255() {
		assertEquals(124, ColorTools.clip255(124.9));
		assertEquals(255, ColorTools.clip255(255.0));
		assertEquals(255, ColorTools.clip255(255.1));
		assertEquals(255, ColorTools.clip255(300.0));
		assertEquals(0, ColorTools.clip255(0.0));
		assertEquals(0, ColorTools.clip255(-5));
	}
}
