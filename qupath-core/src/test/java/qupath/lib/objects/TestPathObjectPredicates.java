/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.ROIs;


@SuppressWarnings("javadoc")
public class TestPathObjectPredicates {
	
	@Test
	public void test_predicates() {
		
		// Create lots of objects
		var tumorClass = PathClass.getInstance("Tumor", (Integer)null);
		var tumorPositive = PathClass.getInstance(tumorClass, "Positive", null);
		var tumorNegative = PathClass.getInstance(tumorClass, "Negative", null);
		var tumorOnePlus = PathClass.fromArray("Tumor", "1+");
		var tumorTwoPlus = PathClass.fromArray("Tumor", "2+");
		var tumorThreePlus = PathClass.fromArray("Tumor", "3+");
		
		var stromaClass = PathClass.getInstance("Stroma", (Integer)null);
		var stromaPositive = PathClass.getInstance(stromaClass, "Positive", null);
		var stromaNegative = PathClass.getInstance(stromaClass, "Negative", null);

		var tumorStromaOtherClass = PathClass.fromString("Tumor: Stroma: Other");
		
		int n = 10;
		
		var tumorDetections = createDetections(n++, tumorClass);
		var tumorPositiveDetections = createDetections(n++, tumorPositive);
		var tumorNegativeDetections = createDetections(n++, tumorNegative);
		var tumorOnePlusDetections = createDetections(n++, tumorOnePlus);
		var tumorTwoPlusDetections = createDetections(n++, tumorTwoPlus);
		var tumorThreePlusDetections = createDetections(n++, tumorThreePlus);
		
		var stromaDetections = createDetections(n++, stromaClass);
		var stromaPositiveDetections = createDetections(n++, stromaPositive);
		var stromaNegativeDetections = createDetections(n++, stromaNegative);
		
		var tumorStromaOtherDetections = createDetections(n++, tumorStromaOtherClass);
		
		var unclassifiedDetections = createDetections(n++, null);
		
		var unclassifiedAnnotations = createAnnotations(n++, null);
		var tumorAnnotations = createAnnotations(n++, tumorClass);
		
		var allObjects = Arrays.asList(
				tumorDetections,
				tumorPositiveDetections,
				tumorNegativeDetections,
				tumorOnePlusDetections,
				tumorTwoPlusDetections,
				tumorThreePlusDetections,
				stromaDetections,
				stromaPositiveDetections,
				stromaNegativeDetections,
				tumorStromaOtherDetections,
				unclassifiedDetections,
				unclassifiedAnnotations,
				tumorAnnotations
				).stream().flatMap(p -> p.stream()).collect(Collectors.toList());
		
		Collections.shuffle(allObjects);

		assertEquals(
				total(unclassifiedAnnotations, tumorAnnotations),
				total(allObjects, PathObjectFilter.ANNOTATIONS)
				);
		
		assertEquals(
				total(tumorAnnotations),
				total(allObjects, PathObjectFilter.ANNOTATIONS.and(PathObjectPredicates.baseClassification(tumorClass)))
				);
		
		assertEquals(
				total(tumorAnnotations),
				total(allObjects, PathObjectPredicates.filter(PathObjectFilter.ANNOTATIONS).and(PathObjectPredicates.baseClassification(tumorClass)))
				);
		
		assertTrue(
				PathObjectPredicates.filter(PathObjectFilter.ANNOTATIONS).and(PathObjectPredicates.baseClassification(tumorClass)) instanceof PathObjectPredicate
				);
		
		assertEquals(
				total(unclassifiedAnnotations, tumorAnnotations),
				total(allObjects, PathObjectFilter.DETECTIONS.negate())
				);
		
		assertEquals(
				total(unclassifiedAnnotations),
				total(allObjects, PathObjectPredicates.filter(PathObjectFilter.ANNOTATIONS).and(PathObjectPredicates.baseClassification(tumorClass).negate()))
				);
		
		assertEquals(
				0,
				total(allObjects, PathObjectFilter.ANNOTATIONS.and(PathObjectFilter.DETECTIONS))
				);
		
		assertEquals(
				allObjects.size(),
				total(allObjects, PathObjectFilter.ANNOTATIONS.or(PathObjectFilter.DETECTIONS))
				);
		
		assertEquals(
				total(tumorDetections, tumorPositiveDetections, tumorNegativeDetections, tumorOnePlusDetections, tumorTwoPlusDetections, tumorThreePlusDetections, 
						tumorStromaOtherDetections, tumorAnnotations),
				total(allObjects, PathObjectPredicates.baseClassification(tumorClass))
				);
		
		assertEquals(
				total(tumorDetections, tumorPositiveDetections, tumorNegativeDetections, tumorOnePlusDetections, tumorTwoPlusDetections, tumorThreePlusDetections, 
						tumorStromaOtherDetections, tumorAnnotations),
				total(allObjects, PathObjectPredicates.containsClassification("Tumor"))
				);
		
		assertEquals(
				total(tumorDetections, tumorPositiveDetections, tumorNegativeDetections, tumorOnePlusDetections, tumorTwoPlusDetections, tumorThreePlusDetections, 
						tumorStromaOtherDetections),
				total(allObjects, PathObjectPredicates.filter(PathObjectFilter.DETECTIONS).and(PathObjectPredicates.containsClassification("Tumor")))
				);
		
		assertEquals(
				total(tumorStromaOtherDetections, stromaPositiveDetections, stromaDetections, stromaNegativeDetections),
				total(allObjects, PathObjectPredicates.filter(PathObjectFilter.DETECTIONS).and(PathObjectPredicates.containsClassification("Stroma")))
				);

		assertEquals(
				total(tumorStromaOtherDetections),
				total(allObjects, PathObjectPredicates.filter(PathObjectFilter.DETECTIONS).and(PathObjectPredicates.containsClassification("Other")))
				);

		
		assertEquals(
				total(tumorPositiveDetections, tumorOnePlusDetections, tumorTwoPlusDetections, tumorThreePlusDetections, 
						stromaPositiveDetections),
				total(allObjects, PathObjectPredicates.positiveClassification(true))
				);
		
		assertEquals(
				total(tumorPositiveDetections, stromaPositiveDetections),
				total(allObjects, PathObjectPredicates.positiveClassification(false))
				);
		
		assertEquals(
				total(stromaPositiveDetections),
				total(allObjects, PathObjectPredicates.baseClassification(stromaClass).and(PathObjectPredicates.positiveClassification(false)))
				);
		
		
	}
	
	static long total(List<?>... list) {
		return Arrays.stream(list).mapToLong(l -> l.size()).sum();
	}
	
	static long total(List<PathObject> allObjects, Predicate<PathObject> predicate) {
//		System.err.println("Calling general");
		return allObjects.stream().filter(predicate).count();
	}
	
	static long total(List<PathObject> allObjects, PathObjectPredicate predicate) {
//		System.err.println("Calling specific");
		return allObjects.stream().filter(predicate).count();
	}
	
	
	static List<PathObject> createDetections(int n, PathClass pathClass) {
		return IntStream.range(0, n)
				.mapToObj(i -> PathObjects.createDetectionObject(ROIs.createEmptyROI(), pathClass))
				.collect(Collectors.toList());
	}
	
	static List<PathObject> createAnnotations(int n, PathClass pathClass) {
		return IntStream.range(0, n)
				.mapToObj(i -> PathObjects.createAnnotationObject(ROIs.createEmptyROI(), pathClass))
				.collect(Collectors.toList());
	}
	
	
}
