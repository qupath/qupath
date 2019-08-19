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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

import org.junit.Test;

import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

@SuppressWarnings("javadoc")
public class TestPathCellObject {
	private final Double x = 0.0, y = 0.0, w = 10.0, h = 10.0;
	private final Double xn = 0.0, yn = 0.0, wn = 5.0, hn = 5.0;
	ROI myROI = ROIs.createEllipseROI(x, y, w, h, ImagePlane.getDefaultPlane());
	ROI myNROI = ROIs.createEllipseROI(xn, yn, wn, hn, ImagePlane.getDefaultPlane());
	PathClass myPC = PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT);
	PathCellObject myPO = new PathCellObject();
	PathCellObject myPO2 = new PathCellObject(myROI, myNROI, myPC);
	
	@Test
	public void test_BasicPO() {
		assertThat(myPO, instanceOf(PathCellObject.class));
		assertThat(myPO, instanceOf(PathDetectionObject.class));
		assertFalse(myPO.hasNucleus());
		assertTrue(myPO2.hasNucleus());
		assertEquals(myPO2.getNucleusROI(), myNROI);
	}
}
