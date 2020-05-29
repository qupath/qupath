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

package qupath.lib.objects;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import qupath.lib.roi.EllipseROI;

@SuppressWarnings("javadoc")
public class TestTMACoreObject {
	private final Double xcenter = 0.0;
	private final Double ycenter = 0.0;
	private final Double diameter = 10.0;
	private final Boolean ismissing = Boolean.TRUE;
	private final Double epsilon = 1e-15;
	private final String name = "TMACoreObject";
	TMACoreObject myPO = new TMACoreObject();
	TMACoreObject myPO2 = PathObjects.createTMACoreObject(xcenter, ycenter, diameter, ismissing);
	
	@Test
	public void test_BasicPO() {
		assertTrue(myPO instanceof TMACoreObject);
		assertFalse(myPO.isMissing());
		myPO.setMissing(Boolean.TRUE);
		assertTrue(myPO.isMissing());
		assertTrue(myPO2.isMissing());
		assertTrue(myPO2.getROI() instanceof EllipseROI);
		assertEquals(myPO2.getROI().getBoundsX(), xcenter-diameter/2, epsilon);
		assertEquals(myPO2.getROI().getBoundsY(), ycenter-diameter/2, epsilon);
		assertEquals(myPO2.toString(), name);
	}
}
