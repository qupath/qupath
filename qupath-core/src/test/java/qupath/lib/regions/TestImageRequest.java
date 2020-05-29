/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.regions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestImageRequest {

	@Test
	public void testImageRegions() {
		
		var region = ImageRegion.createInstance(0, 0, 1024, 1024, 0, 0);
		
		assertTrue(region.contains(0, 0, 0, 0));
		assertTrue(region.contains(100, 0, 0, 0));
		assertTrue(region.contains(0, 100, 0, 0));
		assertTrue(region.contains(1023, 1023, 0, 0));
		
		assertFalse(region.contains(0, 0, 1, 0));
		assertFalse(region.contains(0, 0, 0, 1));
		assertFalse(region.contains(1023, 1024, 0, 0));
		assertFalse(region.contains(1024, 1023, 0, 0));
		assertFalse(region.contains(1024, 1024, 0, 0));
		
		assertTrue(region.intersects(region));
		
		assertFalse(region.intersects(ImageRegion.createInstance(0, 0, 1024, 1024, 1, 0)));
		assertFalse(region.intersects(ImageRegion.createInstance(0, 0, 1024, 1024, 0, 1)));
		
		assertTrue(region.intersects(-10, -10, 50, 50));
		assertTrue(region.intersects(1023, 1023, 50, 50));
		assertFalse(region.intersects(-50, -50, 50, 50));
		assertFalse(region.intersects(1024, 1024, 50, 50));
		assertFalse(region.intersects(1023, 1024, 50, 50));
		assertFalse(region.intersects(1024, 1023, 50, 50));
		assertFalse(region.intersects(-50, 0, 50, 50));
		assertFalse(region.intersects(0, -50, 50, 50));
		
		assertFalse(region.intersects(4096, 4096, 50, 50));
		
		var region2 = ImageRegion.createInstance(8192, 16384, 512, 512, 0, 0);
		assertFalse(region2.intersects(12287, 16044, 228, 237));

		
	}

}