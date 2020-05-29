/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestImagePlane {

	@Test
	public void testCompareTo() {
		
		ImagePlane pDefault = ImagePlane.getDefaultPlane();
		
		ImagePlane p000 = ImagePlane.getPlaneWithChannel(0, 0, 0);
		ImagePlane p100 = ImagePlane.getPlaneWithChannel(1, 0, 0);
		ImagePlane p010 = ImagePlane.getPlaneWithChannel(0, 1, 0);
		ImagePlane p001 = ImagePlane.getPlaneWithChannel(0, 0, 1);
		ImagePlane p110 = ImagePlane.getPlaneWithChannel(1, 1, 0);
		ImagePlane p101 = ImagePlane.getPlaneWithChannel(1, 0, 1);
		ImagePlane p011 = ImagePlane.getPlaneWithChannel(0, 1, 1);
		ImagePlane p111 = ImagePlane.getPlaneWithChannel(1, 1, 1);
		
		List<ImagePlane> list = new ArrayList<>(Arrays.asList(
				pDefault, p000, p100, p010, p001, p110, p101, p011
				));

		Collections.shuffle(list, new Random(100L));
		list.add(0, p111);

		List<ImagePlane> listSortedManually = Arrays.asList(
				pDefault,
				p000,
				p100,
				p010,
				p110,
				p001,
				p101,
				p011,
				p111
				);
		
		assertNotEquals(list, listSortedManually);
		Collections.sort(list);
		assertEquals(list, listSortedManually);
	}

	@Test
	public void testGetDefaultPlane() {
		ImagePlane planeDefault = ImagePlane.getDefaultPlane();
		ImagePlane plane00 = ImagePlane.getPlane(0, 0);
		ImagePlane planem100 = ImagePlane.getPlaneWithChannel(-1, 0, 0);
		ImagePlane plane000 = ImagePlane.getPlaneWithChannel(0, 0, 0);
		
		assertEquals(planeDefault, planeDefault);
		assertEquals(planeDefault, plane00);
		assertEquals(planeDefault, planem100);
		assertNotEquals(planeDefault, plane000);
	}

	@Test
	public void testGetPlaneWithChannelROI() {
		for (int z = 0; z < 100; z++) {
			for (int t = 0; t < 100; t++) {
				for (int c = -1; c < 100; c++) {
					ImagePlane plane = ImagePlane.getPlaneWithChannel(c, z, t);
					assertEquals(plane.getC(), c);
					assertEquals(plane.getZ(), z);
					assertEquals(plane.getT(), t);
				}							
			}			
		}
	}

}
