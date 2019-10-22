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

package qupath.lib.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestDistanceTools {
	
	@Test
	public void test_centroidToBoundsDistance2D() {
		
		var tumorAnnotation = PathObjects.createAnnotationObject(
				ROIs.createEllipseROI(100, 100, 100, 100, ImagePlane.getDefaultPlane())
				);
		var tumorAnnotationZ2 = PathObjects.createAnnotationObject(
				ROIs.createEllipseROI(100, 100, 100, 100, ImagePlane.getPlane(2, 0))
				);
		var tumorAnnotationT2 = PathObjects.createAnnotationObject(
				ROIs.createEllipseROI(100, 100, 100, 100, ImagePlane.getPlane(0, 2))
				);
		
		int radius = 5;
		var detection = PathObjects.createAnnotationObject(
				ROIs.createEllipseROI(100-radius, 100-radius, radius*2, radius*2, ImagePlane.getDefaultPlane())
				);
		
		DistanceTools.centroidToBoundsDistance2D(
				Arrays.asList(detection), Arrays.asList(tumorAnnotation), 1.0, 1.0, "Distance 1");
		
		DistanceTools.centroidToBoundsDistance2D(
				Arrays.asList(detection), Arrays.asList(tumorAnnotation), 2.0, 2.0, "Distance 2");

		DistanceTools.centroidToBoundsDistance2D(
				Arrays.asList(detection), Arrays.asList(tumorAnnotationZ2), 1.0, 1.0, "Distance 3");

		DistanceTools.centroidToBoundsDistance2D(
				Arrays.asList(detection), Arrays.asList(tumorAnnotationT2), 1.0, 1.0, "Distance 4");
		
		double expectedDistance = Math.sqrt(2 * 50.0 * 50.0) - 50.0;
		assertEquals(expectedDistance, detection.getMeasurementList().getMeasurementValue("Distance 1"), 0.05);
		assertEquals(detection.getMeasurementList().getMeasurementValue("Distance 1") * 2.0, detection.getMeasurementList().getMeasurementValue("Distance 2"), 0.01);
		assertTrue(Double.isNaN(detection.getMeasurementList().getMeasurementValue("Distance 3")));
		assertTrue(Double.isNaN(detection.getMeasurementList().getMeasurementValue("Distance 4")));
	}
	
}
