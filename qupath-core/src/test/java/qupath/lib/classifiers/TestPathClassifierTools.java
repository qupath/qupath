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

package qupath.lib.classifiers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestPathClassifierTools {
	
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
	
	private static PathObjectClassifier pc1;
	private static PathObjectClassifier pc2;
	private static PathObjectClassifier pc3;
	private static PathObjectClassifier pc4;
	private static PathObjectClassifier pcInvalid;
	private static PathObjectClassifier pcInvalid2;
	private static PathObjectClassifier pcIgnoreClass;
	private static PathObjectClassifier pcIgnoreClass2;
	
	private static CompositeClassifier cp1;
	private static CompositeClassifier cp2;
	private static CompositeClassifier cpInvalid;
	private static CompositeClassifier cpInvalid2;
	private static CompositeClassifier cpIgnoreClass;
	private static CompositeClassifier cpIgnoreClass2;
	
	private static MeasurementList ml1;
	private static MeasurementList ml2;
	private static MeasurementList ml3;
	private static MeasurementList ml4;
	
	private static PathObjectHierarchy hierarchy;
	
	@BeforeEach
	public void init() {
		pathClass1 = PathClassFactory.getPathClass("TestClass1",  Color.RED.getRGB());
		pathClass2 = PathClassFactory.getPathClass("TestClass2",  Color.GREEN.getRGB());
		pathClass3 = PathClassFactory.getPathClass("TestClass3",  Color.BLUE.getRGB());
		pathClass4 = PathClassFactory.getPathClass("test*",  Color.BLUE.getRGB());
		pathClass5 = PathClassFactory.getPathClass("Ignore*", ColorTools.packRGB(180, 180, 180));
		pathClassUnclassified = PathClassFactory.getPathClassUnclassified();
		
		pc1 = PathClassifierTools.createIntensityClassifier(pathClass1, "intensityMeasurement1", 0.5);
		pc2 = PathClassifierTools.createIntensityClassifier(pathClass2, "intensityMeasurement2", 0.999);
		pc3 = PathClassifierTools.createIntensityClassifier(pathClass3, "intensityMeasurement3", -0.2);
		pc4 = PathClassifierTools.createIntensityClassifier(null, "intensityMeasurement4", -0.2);
		pcInvalid = PathClassifierTools.createIntensityClassifier(null, null, -0.2, 0.5, 13);
		pcInvalid2 = PathClassifierTools.createIntensityClassifier(null, "", -6);
		pcIgnoreClass = PathClassifierTools.createIntensityClassifier(pathClass4, "intensityMeasurement1", -6);
		pcIgnoreClass2 = PathClassifierTools.createIntensityClassifier(pathClass5, "intensityMeasurement1", -6);
		
		cp1 = new CompositeClassifier(Arrays.asList(pc1, pc2, pc3));
		cp2 = new CompositeClassifier(Arrays.asList(pc4));
		cpInvalid = new CompositeClassifier(Arrays.asList());
		cpInvalid2 = new CompositeClassifier(Arrays.asList(pcInvalid, pcInvalid2));
		cpIgnoreClass = new CompositeClassifier(Arrays.asList(pcIgnoreClass));
		cpIgnoreClass2 = new CompositeClassifier(Arrays.asList(pcIgnoreClass2));
		
		ml1 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml2 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml3 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml4 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		
		// Adding measurement to list2 (all measurements)
		ml2.addMeasurement("intensityMeasurement1", 0.0);
		ml2.addMeasurement("intensityMeasurement2", 0.2);
		ml2.addMeasurement("intensityMeasurement3", 0.6);
		ml2.addMeasurement("intensityMeasurement4", -4.6);
		
		// Adding measurement to list3 (missing intensityMeasurement3)
		ml3.addMeasurement("intensityMeasurement1", -1.0);
		ml3.addMeasurement("intensityMeasurement2", 0.9999);
		ml3.addMeasurement("intensityMeasurement4", 0.999);
		
		// Adding measurement to list4 (missing intensityMeasurement4)
		ml4.addMeasurement("intensityMeasurement1", 0.2);
		ml4.addMeasurement("intensityMeasurement2", 0.3);
		ml4.addMeasurement("intensityMeasurement3", 0.5);
		
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
	public void test_runClassifier() {
		hierarchy.addPathObjects(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9));
		
		// Run cp1 on all hierarchy
		PathClassifierTools.runClassifier(hierarchy, cp1);
		
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());	// Unchanged
		assertEquals(null, po4.getPathClass());	// Unchanged
		assertEquals(null, po5.getPathClass());	// Unchanged
		assertEquals(null, po6.getPathClass());	// Unchanged
		assertEquals(pathClass4, po7.getPathClass());	// Unchanged
		assertEquals(pathClass5, po8.getPathClass());	// Unchanged
		assertEquals(null, po9.getPathClass());	// Unchanged

		// Run cp2 on all hierarchy
		PathClassifierTools.runClassifier(hierarchy, cp2);
		
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());
		assertEquals(PathClassFactory.getPositive(pathClass3), po3.getPathClass());
		assertEquals(null, po4.getPathClass());	// Unchanged
		assertEquals(null, po5.getPathClass());	// Unchanged
		assertEquals(null, po6.getPathClass());	// Unchanged
		assertEquals(pathClass4, po7.getPathClass());	// Unchanged
		assertEquals(pathClass5, po8.getPathClass());	// Unchanged
		assertEquals(null, po9.getPathClass());	// Unchanged
		
		// Run cpInvalid on all hierarchy
		PathClassifierTools.runClassifier(hierarchy, cpInvalid);
		
		assertEquals(pathClass1, po1.getPathClass());	// Unchanged
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());	// Unchanged
		assertEquals(PathClassFactory.getPositive(pathClass3), po3.getPathClass());	// Unchanged
		assertEquals(null, po4.getPathClass());	// Unchanged
		assertEquals(null, po5.getPathClass());	// Unchanged
		assertEquals(null, po6.getPathClass());	// Unchanged
		assertEquals(pathClass4, po7.getPathClass());	// Unchanged
		assertEquals(pathClass5, po8.getPathClass());	// Unchanged
		assertEquals(null, po9.getPathClass());	// Unchanged
		
		// Run cpInvalid2 on all hierarchy
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassifierTools.runClassifier(hierarchy, cpInvalid2));
		
//		assertEquals(pathClass1, po1.getPathClass());	// Unchanged
//		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());	// Unchanged
//		assertEquals(PathClassFactory.getPositive(pathClass3), po3.getPathClass());	// Unchanged
//		assertEquals(null, po4.getPathClass());	// Unchanged
//		assertEquals(null, po5.getPathClass());	// Unchanged
//		assertEquals(null, po6.getPathClass());	// Unchanged
//		assertEquals(pathClass4, po7.getPathClass());	// Unchanged
//		assertEquals(pathClass5, po8.getPathClass());	// Unchanged
//		assertEquals(null, po9.getPathClass());	// Unchanged
		
		// Run cpIgnoreClass on all hierarchy
		PathClassifierTools.runClassifier(hierarchy, cpIgnoreClass);
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());	// Unchanged
		assertEquals(PathClassFactory.getPositive(pathClass3), po3.getPathClass());	// Unchanged
		assertEquals(null, po4.getPathClass());	// Unchanged
		assertEquals(null, po5.getPathClass());	// Unchanged
		assertEquals(null, po6.getPathClass());	// Unchanged
		assertEquals(pathClass4, po7.getPathClass());	// Unchanged
		assertEquals(pathClass5, po8.getPathClass());	// Unchanged
		assertEquals(null, po9.getPathClass());	// Unchanged

		
		// Run cpIgnoreClass2 on all hierarchy
		PathClassifierTools.runClassifier(hierarchy, cpIgnoreClass2);
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());	// Unchanged
		assertEquals(PathClassFactory.getPositive(pathClass3), po3.getPathClass());	// Unchanged
		assertEquals(null, po4.getPathClass());	// Unchanged
		assertEquals(null, po5.getPathClass());	// Unchanged
		assertEquals(null, po6.getPathClass());	// Unchanged
		assertEquals(pathClass4, po7.getPathClass());	// Unchanged
		assertEquals(pathClass5, po8.getPathClass());	// Unchanged
		assertEquals(null, po9.getPathClass());	// Unchanged
	}
	
	@Test
	public void test_classificationMap() {
		var map = PathClassifierTools.createClassificationMap(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9));
		
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
		manualMap.put(po9, PathClassFactory.getPathClassUnclassified());
		
		// Check that they are equal
		assertEquals(manualMap.keySet(), map.keySet());
		
		// Set random PathClasses
		po1.setPathClass(null);
		po2.setPathClass(null);
		po3.setPathClass(null);
		po4.setPathClass(pathClass1);
		po5.setPathClass(pathClass2);
		po6.setPathClass(pathClass3);
		po7.setPathClass(pathClass3);
		po8.setPathClass(pathClass2);
		po9.setPathClass(pathClass1);
		
		// Restore them from map and check if they are correct
		PathClassifierTools.restoreClassificationsFromMap(map);
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
		po1.setPathClass(null);
		po2.setPathClass(null);
		po3.setPathClass(null);
		po4.setPathClass(pathClass1);
		po5.setPathClass(pathClass2);
		po6.setPathClass(pathClass3);
		po7.setPathClass(pathClass3);
		po8.setPathClass(pathClass2);
		po9.setPathClass(pathClass1);
		
		// Restore them from manual map and check if they are correct
		PathClassifierTools.restoreClassificationsFromMap(manualMap);
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
	public void test_createCompositeClassifier() {
		var compositeClassifier = PathClassifierTools.createCompositeClassifier(pc1, pc2, pc3, pc4);
		var compositeClassifier2 = PathClassifierTools.createCompositeClassifier(Arrays.asList(pc1, pc2, pc3, pc4));
		var manualCompositeClassifier = new CompositeClassifier(Arrays.asList(pc1, pc2, pc3, pc4));
		
		assertEquals(manualCompositeClassifier.getRequiredMeasurements(), compositeClassifier.getRequiredMeasurements());
		assertEquals(manualCompositeClassifier.getDescription(), compositeClassifier.getDescription());
		assertEquals(manualCompositeClassifier.getName(), compositeClassifier.getName());
		assertEquals(manualCompositeClassifier.getPathClasses(), compositeClassifier.getPathClasses());
		assertEquals(manualCompositeClassifier.isValid(), compositeClassifier.isValid());
		assertEquals(manualCompositeClassifier.supportsAutoUpdate(), compositeClassifier.supportsAutoUpdate());
		
		assertEquals(manualCompositeClassifier.getRequiredMeasurements(), compositeClassifier2.getRequiredMeasurements());
		assertEquals(manualCompositeClassifier.getDescription(), compositeClassifier2.getDescription());
		assertEquals(manualCompositeClassifier.getName(), compositeClassifier2.getName());
		assertEquals(manualCompositeClassifier.getPathClasses(), compositeClassifier2.getPathClasses());
		assertEquals(manualCompositeClassifier.isValid(), compositeClassifier2.isValid());
		assertEquals(manualCompositeClassifier.supportsAutoUpdate(), compositeClassifier2.supportsAutoUpdate());
	}
	
	@Test
	public void test_createIntensityClassifier() {
		var intensityClassifier = PathClassifierTools.createIntensityClassifier(pathClass1, "intensityMeasurement1", 0.5);
		var intensityClassifier2 = PathClassifierTools.createIntensityClassifier(pathClass2, "intensityMeasurement2", 0.5, 1.0, 1.5);
		
		var manualIntensityClassifier = new PathIntensityClassifier(pathClass1, "intensityMeasurement1", 0.5);
		var manualIntensityClassifier2 = new PathIntensityClassifier(pathClass2, "intensityMeasurement2", 0.5, 1.0, 1.5);
		
		assertTrue(manualIntensityClassifier.isValid());
		assertTrue(manualIntensityClassifier.supportsAutoUpdate());
		assertEquals("Intensity classifier", manualIntensityClassifier.getName());
		assertEquals(Arrays.asList(PathClassFactory.getPositive(pathClass1), PathClassFactory.getNegative(pathClass1)), manualIntensityClassifier.getPathClasses());
		
		assertTrue(manualIntensityClassifier2.isValid());
		assertTrue(manualIntensityClassifier2.supportsAutoUpdate());
		assertEquals("Intensity classifier", manualIntensityClassifier2.getName());
		assertEquals(Arrays.asList(
				PathClassFactory.getNegative(pathClass2),
				PathClassFactory.getOnePlus(pathClass2), 
				PathClassFactory.getTwoPlus(pathClass2), 
				PathClassFactory.getThreePlus(pathClass2)
				), manualIntensityClassifier2.getPathClasses());
		
		assertEquals(intensityClassifier.isValid(), manualIntensityClassifier.isValid());
		assertEquals(intensityClassifier.supportsAutoUpdate(), manualIntensityClassifier.supportsAutoUpdate());
		assertEquals(intensityClassifier.getName(), manualIntensityClassifier.getName());
		assertEquals(intensityClassifier.getPathClasses(), manualIntensityClassifier.getPathClasses());

		
		assertEquals(intensityClassifier2.isValid(), manualIntensityClassifier2.isValid());
		assertEquals(intensityClassifier2.supportsAutoUpdate(), manualIntensityClassifier2.supportsAutoUpdate());
		assertEquals(intensityClassifier2.getName(), manualIntensityClassifier2.getName());
		assertEquals(intensityClassifier2.getPathClasses(), manualIntensityClassifier2.getPathClasses());
	}
	
	@Test
	public void test_getAvailableFeatures() {
		var measurements = PathClassifierTools.getAvailableFeatures(Arrays.asList());
		assertEquals(new HashSet<>(), measurements);
		
		var measurements2 = PathClassifierTools.getAvailableFeatures(Arrays.asList(po1));
		assertEquals(new HashSet<>(), measurements2);
		
		var measurements3 = PathClassifierTools.getAvailableFeatures(Arrays.asList(po1, po2, po3, po4, po5, po6));
		assertEquals(new HashSet<>(Arrays.asList(
				"intensityMeasurement1", 
				"intensityMeasurement2", 
				"intensityMeasurement3", 
				"intensityMeasurement4"
				)), measurements3);
	}
	
	@Test
	public void test_getRepresentedPathClasses() {
		hierarchy.addPathObjects(Arrays.asList(po1, po2, po3, po4, po5, po6));
		
		var classes = PathClassifierTools.getRepresentedPathClasses(hierarchy, PathAnnotationObject.class);
		assertEquals(new HashSet<>(), classes); 
		
		var classes1 = PathClassifierTools.getRepresentedPathClasses(hierarchy, PathDetectionObject.class);
		assertEquals(new HashSet<>(Arrays.asList(
				pathClass1,
				pathClass2,
				pathClass3
				)), classes1);
	}
	
	@Test
	public void test_setIntensityClassification1() {
		// Check wrong number of thresholds
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement1", 0.5, 1.5, 1.2, 0.6));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement1"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassifierTools.setIntensityClassification(po1, null, 0.5));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassifierTools.setIntensityClassification(po1, "", 0.5));
		
		// Check with 'test*' and 'ignore*' class
		// TODO: there is inconsistency between 'ignored classes' (PathClassTools.isIgnoredClass) and 
		// the StandardPathClasses.IGNORE class. Should this method process either? Is 'test*' class an ignored one? 
		// The method only checks for StandardPathClasses.IGNORE
		var poIgnore = PathObjects.createDetectionObject(ROIs.createEmptyROI(), pathClass4, ml1);
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(poIgnore, "intensityMeasurement1", 0.5));

		var poIgnore2 = PathObjects.createDetectionObject(ROIs.createEmptyROI(), pathClass5, ml1);
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(poIgnore2, "intensityMeasurement1", 0.5));

		var poNull = PathObjects.createDetectionObject(ROIs.createEmptyROI(), PathClassFactory.getPathClassUnclassified(), ml1);
		assertEquals(null, PathClassifierTools.setIntensityClassification(poNull, "intensityMeasurement1", 0.5));
		
		// Missing measurements -> unchanged
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement1", 0.5));
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement2", 0.5));
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement3", 0.5));
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement4", 0.5));
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		assertEquals(PathClassFactory.getNegative(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement1", 0.5));
		assertEquals(PathClassFactory.getNegative(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement2", 0.5));
		assertEquals(PathClassFactory.getPositive(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement3", 0.5));
		assertEquals(PathClassFactory.getNegative(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", 0.5));
		assertEquals(PathClassFactory.getNegative(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", 0.5, 1.0, 1.5));
		assertEquals(PathClassFactory.getOnePlus(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, 1.0, 1.5));
		assertEquals(PathClassFactory.getThreePlus(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, -4.9, -4.8));
		assertEquals(PathClassFactory.getOnePlus(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, 1.5));
		assertEquals(PathClassFactory.getTwoPlus(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", -5.0, -4.7));
		assertEquals(PathClassFactory.getNegative(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", 0, 1.5));
		
		assertEquals(PathClassFactory.getNegative(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement1", 0.5));
		assertEquals(PathClassFactory.getPositive(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement2", 0.5));
		assertEquals(pathClass3, PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement3", 0.5));	// Missing value -> unchanged
		assertEquals(PathClassFactory.getPositive(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement4", 0.5));
		assertEquals(PathClassFactory.getOnePlus(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement4", 0.5, 1.0, 1.5));
		assertEquals(PathClassFactory.getTwoPlus(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement4", -0.5, 0.0, 1.5));
		assertEquals(PathClassFactory.getThreePlus(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement4", -0.5, 0.0, 0.5));
		
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement1", 0.5));
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement2", 0.5));
		assertEquals(PathClassFactory.getPositive(null), PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement4", 0.5, 1.0, 1.5));

		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement1", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement2", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement1", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement2", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'test*' should be ignored by the method (and anyway this object does not have the required measurements)-> Unchanged
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement4", 0.5, 1.0, 1.5));

		// 'Ignore*' (PathClassFactory.StandardPathClasses.IGNORE) should be ignored by the method -> Unchanged
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement1", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement2", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement3", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement4", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'Unclassified' path class should be classified accordingly, but po9 does not have measurementList -> Unchanged
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement1", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement2", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'Unclassified' path class should be classified accordingly (Positive (1+, 2+, 3+)/Negative)
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement1", 0.5));
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement2", 0.5));
		assertEquals(PathClassFactory.getPositive(null), PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement4", 0.5, 1.0, 1.5));
		
		// 'test*' should be ignored by the method (even if it has the proper measurements to be classified) -> Unchanged
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement4", 0.5, 1.0, 1.5));
	}
	
	@Test
	public void test_setIntensityClassification2() {
		PathClassifierTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement1", 0.5);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());
		assertEquals(PathClassFactory.getNegative(pathClass3), po3.getPathClass());
		assertEquals(PathClassFactory.getNegative(null), po4.getPathClass());
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement1", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement1", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement1", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement1", 0.5));
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement1", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement1", 0.5));
		
		PathClassifierTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement2", 0.5);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(PathClassFactory.getNegative(pathClass2), po2.getPathClass());
		assertEquals(PathClassFactory.getPositive(pathClass3), po3.getPathClass());
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement2", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement2", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement2", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement2", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement2", 0.5));
		assertEquals(PathClassFactory.getNegative(null), PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement2", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement2", 0.5));

		PathClassifierTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement3", 0.5);
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement3", 0.5));
		assertEquals(PathClassFactory.getPositive(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement3", 0.5));
		assertEquals(pathClass3, PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement3", 0.5));	// Missing value -> unchanged
		assertEquals(PathClassFactory.getPositive(null), PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement3", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement3", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement3", 0.5));
		assertEquals(PathClassFactory.getPositive(null), PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement3", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement3", 0.5));
		
		PathClassifierTools.setIntensityClassifications(Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9, po10, po11), "intensityMeasurement4", 0.5);
		assertEquals(pathClass1, PathClassifierTools.setIntensityClassification(po1, "intensityMeasurement4", 0.5));
		assertEquals(PathClassFactory.getNegative(pathClass2), PathClassifierTools.setIntensityClassification(po2, "intensityMeasurement4", 0.5));
		assertEquals(PathClassFactory.getPositive(pathClass3), PathClassifierTools.setIntensityClassification(po3, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po4, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po5, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po6, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po7, "intensityMeasurement4", 0.5));
		assertEquals(pathClass5, PathClassifierTools.setIntensityClassification(po8, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po9, "intensityMeasurement4", 0.5));
		assertEquals(null, PathClassifierTools.setIntensityClassification(po10, "intensityMeasurement4", 0.5));
		assertEquals(pathClass4, PathClassifierTools.setIntensityClassification(po11, "intensityMeasurement4", 0.5));
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
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassifierTools.classificationLabelsToChannels(map, true));
		
		// Remove wrong value and call method again
		map.remove(-1);
		
		List<ImageChannel> list = PathClassifierTools.classificationLabelsToChannels(map, true);
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
		
		List<ImageChannel> list2 = PathClassifierTools.classificationLabelsToChannels(map, false);
		manualList.remove(manualList.size()-1);
		manualList.remove(manualList.size()-1);
		manualList.remove(manualList.size()-1);
		manualList.add(ImageChannel.getInstance("test*", pathClass4.getColor()));
		manualList.add(ImageChannel.getInstance("Unclassified 3", null));
		manualList.add(ImageChannel.getInstance("Ignore*", pathClass5.getColor()));
		assertEquals(manualList, list2);
	}
}