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

package qupath.lib.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
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
	
	@Test
	public void test_detectionCentroidDistance2D() {
		
		double radius = 5;
		
		List<Point2> tumorCoordinates = Arrays.asList(
				new Point2(100, 100),
				new Point2(100, 200),
				new Point2(150, 150)
				);
		
		List<Point2> stromaCoordinates = Arrays.asList(
				new Point2(120, 100),
				new Point2(100, 210)
				);
		
		var planes = Arrays.asList(ImagePlane.getDefaultPlane(), ImagePlane.getPlane(2, 0), ImagePlane.getPlane(1, 1));
		
		for (var sourcePlane : planes) {
			for (var targetPlane : planes) {
			
				var tumorDetections = tumorCoordinates.stream().map(p -> PathObjects.createDetectionObject(
						ROIs.createEllipseROI(p.getX()-radius, p.getY()-radius, radius*2, radius*2, sourcePlane),
						PathClassFactory.getPathClass(StandardPathClasses.TUMOR))).collect(Collectors.toList());
				
				var stromaDetections = stromaCoordinates.stream().map(p -> PathObjects.createDetectionObject(
						ROIs.createEllipseROI(p.getX()-radius, p.getY()-radius, radius*2, radius*2, targetPlane),
						PathClassFactory.getPathClass(StandardPathClasses.STROMA))).collect(Collectors.toList());
				
				if (sourcePlane.equals(targetPlane)) {
		
					// Distance to stroma, default pixels
					DistanceTools.centroidToCentroidDistance2D(tumorDetections, stromaDetections, 1.0, 1.0, "Distance to stroma");
					assertEquals(tumorDetections.get(0).getMeasurementList().getMeasurementValue("Distance to stroma"), 20, 0.0001);
					assertEquals(tumorDetections.get(1).getMeasurementList().getMeasurementValue("Distance to stroma"), 10, 0.0001);
					assertEquals(tumorDetections.get(2).getMeasurementList().getMeasurementValue("Distance to stroma"), Math.sqrt(30*30+50*50), 0.0001);
					
					// Distance to stroma, scaled pixels
					DistanceTools.centroidToCentroidDistance2D(tumorDetections, stromaDetections, 2.0, 2.0, "Distance to stroma");
					assertEquals(tumorDetections.get(0).getMeasurementList().getMeasurementValue("Distance to stroma"), 20*2, 0.0001);
					assertEquals(tumorDetections.get(1).getMeasurementList().getMeasurementValue("Distance to stroma"), 10*2, 0.0001);
					assertEquals(tumorDetections.get(2).getMeasurementList().getMeasurementValue("Distance to stroma"), Math.sqrt(30*30*4+50*50*4), 0.0001);
					
					// Distance to stroma, scaled non-square pixels
					DistanceTools.centroidToCentroidDistance2D(tumorDetections, stromaDetections, 1.4, 1.6, "Distance to stroma");
					assertEquals(tumorDetections.get(0).getMeasurementList().getMeasurementValue("Distance to stroma"), 20*1.4, 0.0001);
					assertEquals(tumorDetections.get(1).getMeasurementList().getMeasurementValue("Distance to stroma"), 10*1.6, 0.0001);
					assertEquals(tumorDetections.get(2).getMeasurementList().getMeasurementValue("Distance to stroma"), Math.sqrt(30*30*1.4*1.4+50*50*1.6*1.6), 0.0001);
					
					// Ensure no other measurements added
					for (var stroma : stromaDetections) {
						assertFalse(stroma.getMeasurementList().containsNamedMeasurement("Distance to stroma"));
						assertTrue(Double.isNaN(stroma.getMeasurementList().getMeasurementValue("Distance to stroma")));
					}
					for (var tumor : tumorDetections) {
						assertTrue(tumor.getMeasurementList().containsNamedMeasurement("Distance to stroma"));
					}
				} else {
					// Ensure no measurements added
					DistanceTools.centroidToCentroidDistance2D(tumorDetections, stromaDetections, 1.0, 1.0, "Distance to stroma");
					for (var tumor : tumorDetections) {
						// Currently, we don't add a measurement if not on the same plane
//						assertTrue(tumor.getMeasurementList().containsNamedMeasurement("Distance to stroma"));
						assertTrue(Double.isNaN(tumor.getMeasurementList().getMeasurementValue("Distance to stroma")));
					}
					for (var stroma : stromaDetections) {
						assertFalse(stroma.getMeasurementList().containsNamedMeasurement("Distance to stroma"));
						assertTrue(Double.isNaN(stroma.getMeasurementList().getMeasurementValue("Distance to stroma")));
					}
				}
			}
			
		}

	}
	
}
