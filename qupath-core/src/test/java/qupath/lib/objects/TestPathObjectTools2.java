/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

/**
 * These tests started out in the (not removed) class {@code PathClassifierTools}.
 * The majority of the methods have now been moved to {@link PathObjectTools}, with one in {@link ServerTools}.
 */
@SuppressWarnings("javadoc")
public class TestPathObjectTools2 {
	
	private static PathClass pathClass1;
	private static PathClass pathClass2;
	private static PathClass pathClass3;
	private static PathClass pathClass4;
	private static PathClass pathClass5;
	private static PathClass pathClassUnclassified;
	
	private static PathObject po1;
	private static PathObject po2;
	private static PathObject po3;
	private static PathObject po4;
	private static PathObject po5;
	private static PathObject po6;
	private static PathObject po7;
	private static PathObject po8;
	private static PathObject po9;
	private static PathObject po10;
	private static PathObject po11;
	
	private static MeasurementList ml1;
	private static MeasurementList ml2;
	private static MeasurementList ml3;
	private static MeasurementList ml4;
	
	private static PathObjectHierarchy hierarchy;
	
	@BeforeEach
	public void init() {
		pathClass1 = PathClass.getInstance("TestClass1",  Color.RED.getRGB());
		pathClass2 = PathClass.getInstance("TestClass2",  Color.GREEN.getRGB());
		pathClass3 = PathClass.getInstance("TestClass3",  Color.BLUE.getRGB());
		pathClass4 = PathClass.getInstance("test*",  Color.BLUE.getRGB());
		pathClass5 = PathClass.getInstance("Ignore*", ColorTools.packRGB(180, 180, 180));
		pathClassUnclassified = PathClass.NULL_CLASS;
		
		ml1 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml2 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml3 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml4 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		
		// Adding measurement to list2 (all measurements)
		ml2.put("intensityMeasurement1", 0.0);
		ml2.put("intensityMeasurement2", 0.2);
		ml2.put("intensityMeasurement3", 0.6);
		ml2.put("intensityMeasurement4", -4.6);
		
		// Adding measurement to list3 (missing intensityMeasurement3)
		ml3.put("intensityMeasurement1", -1.0);
		ml3.put("intensityMeasurement2", 0.9999);
		ml3.put("intensityMeasurement4", 0.999);
		
		// Adding measurement to list4 (missing intensityMeasurement4)
		ml4.put("intensityMeasurement1", 0.2);
		ml4.put("intensityMeasurement2", 0.3);
		ml4.put("intensityMeasurement3", 0.5);
		
		po1 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()), pathClass1, ml1);
		po2 = PathObjects.createDetectionObject(ROIs.createEllipseROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()), pathClass2, ml2);
		po3 = PathObjects.createDetectionObject(ROIs.createLineROI(10, 20, ImagePlane.getDefaultPlane()), pathClass3, ml3);
		po4 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()), null, ml4);
		po5 = PathObjects.createDetectionObject(ROIs.createEllipseROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()));
		po6 = PathObjects.createDetectionObject(ROIs.createLineROI(10, 20, ImagePlane.getDefaultPlane()));
		po7 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass4);
		po8 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass5);
		po9 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClassUnclassified);
		po10 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClassUnclassified, ml4);
		po11 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass4, ml4);
		hierarchy = new PathObjectHierarchy();
	}

	
	@Test
	public void test_classificationMap() {
		var map = PathObjectTools.createClassificationMap(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9));
		
		// Manually create the map
		Map<PathObject, PathClass> manualMap = new HashMap<>();
		manualMap.put(po1, pathClass1);
		manualMap.put(po2, pathClass2);
		manualMap.put(po3, pathClass3);
		manualMap.put(po4, null);
		manualMap.put(po5, null);
		manualMap.put(po6, null);
		manualMap.put(po7, pathClass4);
		manualMap.put(po8, pathClass5);
		manualMap.put(po9, PathClass.NULL_CLASS);
		
		// Check that they are equal
		assertEquals(manualMap.keySet(), map.keySet());
		
		// Set random PathClasses
		po1.resetPathClass();
		po2.setPathClass((PathClass)null);
		po3.resetPathClass();
		po4.setPathClass(pathClass1);
		po5.setPathClass(pathClass2);
		po6.setPathClass(pathClass3);
		po7.setPathClass(pathClass3);
		po8.setPathClass(pathClass2);
		po9.setPathClass(pathClass1);
		
		// Restore them from map and check if they are correct
		PathObjectTools.restoreClassificationsFromMap(map);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(pathClass2, po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(pathClass4, po7.getPathClass());
		assertEquals(pathClass5, po8.getPathClass());
		assertEquals(null, po9.getPathClass());
		
		// Set random PathClasses
		po1.resetPathClass();
		po2.setPathClass((PathClass)null); // Should also reset
		po3.resetPathClass();
		po4.setPathClass(pathClass1);
		po5.setPathClass(pathClass2);
		po6.setPathClass(pathClass3);
		po7.setPathClass(pathClass3);
		po8.setPathClass(pathClass2);
		po9.setPathClass(pathClass1);
		
		// Restore them from manual map and check if they are correct
		PathObjectTools.restoreClassificationsFromMap(manualMap);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(pathClass2, po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(pathClass4, po7.getPathClass());
		assertEquals(pathClass5, po8.getPathClass());
		assertEquals(null, po9.getPathClass());
	}
	
	@Test
	public void test_getAvailableFeatures() {
		var measurements = PathObjectTools.getAvailableFeatures(Arrays.asList());
		assertEquals(new HashSet<>(), measurements);
		
		var measurements2 = PathObjectTools.getAvailableFeatures(Arrays.asList(po1));
		assertEquals(new HashSet<>(), measurements2);
		
		var measurements3 = PathObjectTools.getAvailableFeatures(Arrays.asList(po1, po2, po3, po4, po5, po6));
		assertEquals(new HashSet<>(Arrays.asList(
				"intensityMeasurement1", 
				"intensityMeasurement2", 
				"intensityMeasurement3", 
				"intensityMeasurement4"
				)), measurements3);
	}
	
	@Test
	public void test_getRepresentedPathClasses() {
		hierarchy.addObjects(Arrays.asList(po1, po2, po3, po4, po5, po6));
		
		var classes = PathObjectTools.getRepresentedPathClasses(hierarchy, PathAnnotationObject.class);
		assertEquals(new HashSet<>(), classes); 
		
		var classes1 = PathObjectTools.getRepresentedPathClasses(hierarchy, PathDetectionObject.class);
		assertEquals(new HashSet<>(Arrays.asList(
				pathClass1,
				pathClass2,
				pathClass3
				)), classes1);
	}
	
	@Test
	public void test_setIntensityClassification1() {
		// Check wrong number of thresholds
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathObjectTools.setIntensityClassification(po1, "intensityMeasurement1", 0.5, 1.5, 1.2, 0.6));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathObjectTools.setIntensityClassification(po1, "intensityMeasurement1"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathObjectTools.setIntensityClassification(po1, null, 0.5));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathObjectTools.setIntensityClassification(po1, "", 0.5));
		
		// Check with 'test*' and 'ignore*' class
		// TODO: there is inconsistency between 'ignored classes' (PathClassTools.isIgnoredClass) and 
		// the StandardPathClasses.IGNORE class. Should this method process either? Is 'test*' class an ignored one? 
		// The method only checks for StandardPathClasses.IGNORE
		var poIgnore = PathObjects.createDetectionObject(ROIs.createEmptyROI(), pathClass4, ml1);
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(poIgnore, "intensityMeasurement1", 0.5));

		var poIgnore2 = PathObjects.createDetectionObject(ROIs.createEmptyROI(), pathClass5, ml1);
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(poIgnore2, "intensityMeasurement1", 0.5));

		var poNull = PathObjects.createDetectionObject(ROIs.createEmptyROI(), PathClass.NULL_CLASS, ml1);
		assertEquals(null, PathObjectTools.setIntensityClassification(poNull, "intensityMeasurement1", 0.5));
		
		// Missing measurements -> unchanged
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement1", 0.5));
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement2", 0.5));
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement3", 0.5));
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement4", 0.5));
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		assertEquals(PathClass.getNegative(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement1", 0.5));
		assertEquals(PathClass.getNegative(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement2", 0.5));
		assertEquals(PathClass.getPositive(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement3", 0.5));
		assertEquals(PathClass.getNegative(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", 0.5));
		assertEquals(PathClass.getNegative(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", 0.5, 1.0, 1.5));
		assertEquals(PathClass.getOnePlus(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, 1.0, 1.5));
		assertEquals(PathClass.getThreePlus(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, -4.9, -4.8));
		assertEquals(PathClass.getOnePlus(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, 1.5));
		assertEquals(PathClass.getTwoPlus(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, -4.7));
		assertEquals(PathClass.getNegative(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", 0, 1.5));
		
		assertEquals(PathClass.getNegative(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement1", 0.5));
		assertEquals(PathClass.getPositive(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement2", 0.5));
		assertEquals(pathClass3, PathObjectTools.setIntensityClassification(po3, "intensityMeasurement3", 0.5));	// Missing value -> unchanged
		assertEquals(PathClass.getPositive(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement4", 0.5));
		assertEquals(PathClass.getOnePlus(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement4", 0.5, 1.0, 1.5));
		assertEquals(PathClass.getTwoPlus(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement4", -0.5, 0.0, 1.5));
		assertEquals(PathClass.getThreePlus(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement4", -0.5, 0.0, 0.5));
		
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po4, "intensityMeasurement1", 0.5));
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po4, "intensityMeasurement2", 0.5));
		assertEquals(PathClass.getPositive(null), PathObjectTools.setIntensityClassification(po4, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po4, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po4, "intensityMeasurement4", 0.5, 1.0, 1.5));

		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement1", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement2", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement1", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement2", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'test*' should be ignored by the method (and anyway this object does not have the required measurements)-> Unchanged
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement4", 0.5, 1.0, 1.5));

		// 'Ignore*' (PathClassFactory.StandardPathClasses.IGNORE) should be ignored by the method -> Unchanged
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement1", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement2", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement3", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement4", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'Unclassified' path class should be classified accordingly, but po9 does not have measurementList -> Unchanged
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement1", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement2", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'Unclassified' path class should be classified accordingly (Positive (1+, 2+, 3+)/Negative)
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po10, "intensityMeasurement1", 0.5));
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po10, "intensityMeasurement2", 0.5));
		assertEquals(PathClass.getPositive(null), PathObjectTools.setIntensityClassification(po10, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po10, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po10, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'test*' should be ignored by the method (even if it has the proper measurements to be classified) -> Unchanged
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement4", 0.5, 1.0, 1.5));
	}
	
	@Test
	public void test_setIntensityClassification2() {
		PathObjectTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement1", 0.5);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(PathClass.getNegative(pathClass2), po2.getPathClass());
		assertEquals(PathClass.getNegative(pathClass3), po3.getPathClass());
		assertEquals(PathClass.getNegative(null), po4.getPathClass());
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement1", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement1", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement1", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement1", 0.5));
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po10, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement1", 0.5));
		
		PathObjectTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement2", 0.5);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(PathClass.getNegative(pathClass2), po2.getPathClass());
		assertEquals(PathClass.getPositive(pathClass3), po3.getPathClass());
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po4, "intensityMeasurement2", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement2", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement2", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement2", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement2", 0.5));
		assertEquals(PathClass.getNegative(null), PathObjectTools.setIntensityClassification(po10, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement2", 0.5));

		PathObjectTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement3", 0.5);
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement3", 0.5));
		assertEquals(PathClass.getPositive(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement3", 0.5));
		assertEquals(pathClass3, PathObjectTools.setIntensityClassification(po3, "intensityMeasurement3", 0.5));	// Missing value -> unchanged
		assertEquals(PathClass.getPositive(null), PathObjectTools.setIntensityClassification(po4, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement3", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement3", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement3", 0.5));
		assertEquals(PathClass.getPositive(null), PathObjectTools.setIntensityClassification(po10, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement3", 0.5));
		
		PathObjectTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement4", 0.5);
		assertEquals(pathClass1, PathObjectTools.setIntensityClassification(po1, "intensityMeasurement4", 0.5));
		assertEquals(PathClass.getNegative(pathClass2), PathObjectTools.setIntensityClassification(po2, "intensityMeasurement4", 0.5));
		assertEquals(PathClass.getPositive(pathClass3), PathObjectTools.setIntensityClassification(po3, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po4, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po5, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po6, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po7, "intensityMeasurement4", 0.5));
		assertEquals(pathClass5, PathObjectTools.setIntensityClassification(po8, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po9, "intensityMeasurement4", 0.5));
		assertEquals(null, PathObjectTools.setIntensityClassification(po10, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathObjectTools.setIntensityClassification(po11, "intensityMeasurement4", 0.5));
	}
	
	@Test
	public void test_classificationLabelsToChannels() {
		Map<Integer, PathClass> map = new HashMap<>();
		map.put(-1, pathClass1);	// Wrong value
		map.put(0, pathClass1);
		map.put(1, pathClass2);
		map.put(2, null);
		map.put(3, pathClass3);
		map.put(4, null);
		map.put(5, pathClass4);
		map.put(6, null);
		map.put(7, pathClass5);
		Assertions.assertThrows(IllegalArgumentException.class, () -> ServerTools.classificationLabelsToChannels(map, true));
		
		// Remove wrong value and call method again
		map.remove(-1);
		
		List<ImageChannel> list = ServerTools.classificationLabelsToChannels(map, true);
		List<ImageChannel> manualList = new ArrayList<>();
		manualList.add(ImageChannel.getInstance("TestClass1", Color.RED.getRGB()));
		manualList.add(ImageChannel.getInstance("TestClass2", Color.GREEN.getRGB()));
		manualList.add(ImageChannel.getInstance("Unclassified 1", null));
		manualList.add(ImageChannel.getInstance("TestClass3", Color.BLUE.getRGB()));
		manualList.add(ImageChannel.getInstance("Unclassified 2", null));
		manualList.add(ImageChannel.getInstance("test*", null));
		manualList.add(ImageChannel.getInstance("Unclassified 3", null));
		manualList.add(ImageChannel.getInstance("Ignore*", null));
		assertEquals(manualList, list);
		
		List<ImageChannel> list2 = ServerTools.classificationLabelsToChannels(map, false);
		manualList.remove(manualList.size()-1);
		manualList.remove(manualList.size()-1);
		manualList.remove(manualList.size()-1);
		manualList.add(ImageChannel.getInstance("test*", pathClass4.getColor()));
		manualList.add(ImageChannel.getInstance("Unclassified 3", null));
		manualList.add(ImageChannel.getInstance("Ignore*", pathClass5.getColor()));
		assertEquals(manualList, list2);
	}
	
	
	@Test
	public void test_comparator() {
		
		var comparator = DefaultPathObjectComparator.getInstance();
		var rng = new Random(100L);
		
		List<PathObject> pathObjects = IntStream.range(0, 100)
				.mapToObj(i -> PathObjects.createDetectionObject(
							ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()),
							PathClass.getInstance("Classification " + rng.nextInt(10))
						)).collect(Collectors.toCollection(() -> new ArrayList<>()));
						
		Collections.sort(pathObjects, comparator);
		
		
	}
	
	
}