/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.objects;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.instanceOf;

import org.junit.Test;

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
		assertThat(myPO, instanceOf(TMACoreObject.class));
		assertFalse(myPO.isMissing());
		myPO.setMissing(Boolean.TRUE);
		assertTrue(myPO.isMissing());
		assertTrue(myPO2.isMissing());
		assertThat(myPO2.getROI(), instanceOf(EllipseROI.class));
		assertEquals(myPO2.getROI().getBoundsX(), xcenter-diameter/2, epsilon);
		assertEquals(myPO2.getROI().getBoundsY(), ycenter-diameter/2, epsilon);
		assertEquals(myPO2.toString(), name);
	}
}
