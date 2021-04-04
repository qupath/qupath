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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestCompositeClassifier {
	
	private static DecimalFormat df = new DecimalFormat("#.###");
	private static PathClass pathClass1;
	private static PathClass pathClass2;
	private static PathClass pathClass3;
	
	private static PathObjectClassifier pc1;
	private static PathObjectClassifier pc2;
	private static PathObjectClassifier pc3;
	private static PathObjectClassifier pc4;
	private static PathObjectClassifier pcInvalid;
	private static PathObjectClassifier pcInvalid2;
	
	private static CompositeClassifier cp1;
	private static CompositeClassifier cp2;
	private static CompositeClassifier cpInvalid;
	private static CompositeClassifier cpInvalid2;
	
	@BeforeAll
	public static void init() {
		pathClass1 = PathClassFactory.getPathClass("TestClass1",  Color.RED.getRGB());
		pathClass2 = PathClassFactory.getPathClass("TestClass2",  Color.GREEN.getRGB());
		pathClass3 = PathClassFactory.getPathClass("TestClass3",  Color.BLUE.getRGB());
		pc1 = PathClassifierTools.createIntensityClassifier(pathClass1, "intensityMeasurement1", 0.5);
		pc2 = PathClassifierTools.createIntensityClassifier(pathClass2, "intensityMeasurement2", 0.999);
		pc3 = PathClassifierTools.createIntensityClassifier(pathClass3, "intensityMeasurement3", -0.2);
		pc4 = PathClassifierTools.createIntensityClassifier(null, "intensityMeasurement4", -0.2);
		pcInvalid = PathClassifierTools.createIntensityClassifier(null, null, -0.2, 0.5, 13);
		pcInvalid2 = PathClassifierTools.createIntensityClassifier(null, "", -6);
		cp1 = new CompositeClassifier(Arrays.asList(pc1, pc2, pc3));
		cp2 = new CompositeClassifier(Arrays.asList(pc4));
		cpInvalid = new CompositeClassifier(Arrays.asList());
		cpInvalid2 = new CompositeClassifier(Arrays.asList(pcInvalid, pcInvalid2));
	}
	
	@Test
	public void test_isValid() {
		assertTrue(cp1.isValid());
		assertTrue(cp2.isValid());
		assertFalse(cpInvalid.isValid());
		assertFalse(cpInvalid2.isValid());
	}
	
	@Test
	public void test_getPathClass() {
		List<PathClass> outputPathClasses = new ArrayList<>();
		for (PathClass pathClass: new PathClass[] {pathClass1, pathClass2, pathClass3}) {
			outputPathClasses.add(PathClassFactory.getPositive(pathClass));
			outputPathClasses.add(PathClassFactory.getNegative(pathClass));
		}
		
		var posNegClasses = Arrays.asList(PathClassFactory.getPositive(null), PathClassFactory.getNegative(null));

		assertTrue(outputPathClasses.size() == cp1.getPathClasses().size() && 
				outputPathClasses.containsAll(cp1.getPathClasses()));
		
		assertTrue(cp2.getPathClasses().size() == posNegClasses.size() &&
				cp2.getPathClasses().containsAll(posNegClasses));
		
		assertTrue(cp2.getPathClasses().size() == posNegClasses.size() &&
				cp2.getPathClasses().containsAll(posNegClasses));
		
		assertTrue(cpInvalid.getPathClasses().size() == 0);
	}
	
	@Test
	public void test_getRequiredMeasurements() {
		assertEquals(3, cp1.getRequiredMeasurements().size());
		assertEquals("intensityMeasurement1", cp1.getRequiredMeasurements().get(0));
		assertEquals("intensityMeasurement2", cp1.getRequiredMeasurements().get(1));
		assertEquals("intensityMeasurement3", cp1.getRequiredMeasurements().get(2));

		assertEquals(1, cp2.getRequiredMeasurements().size());
		assertEquals("intensityMeasurement4", cp2.getRequiredMeasurements().get(0));
		
		assertEquals(0, cpInvalid.getRequiredMeasurements().size());

		// Because validity is not checked, the measurement returned is a blank String
		assertEquals(1, cpInvalid2.getRequiredMeasurements().size());
	}
	
	@Test
	public void test_getDescription() {
		String cp1Description = "Intensity classifier\n\n"
				+ "Input class:\t" + "TestClass1" + "\n\n"
				+ "Thresholded measurement:\t" + "intensityMeasurement1" + "\n\n"
				+ "Thresholded value:\t" + df.format(0.5)
				+ "\n-----------\n"
				+ "Intensity classifier\n\n"
				+ "Input class:\t" + "TestClass2" + "\n\n"
				+ "Thresholded measurement:\t" + "intensityMeasurement2" + "\n\n"
				+ "Thresholded value:\t" + df.format(0.999)
				+ "\n-----------\n"
				+ "Intensity classifier\n\n"
				+ "Input class:\t" + "TestClass3" + "\n\n"
				+ "Thresholded measurement:\t" + "intensityMeasurement3" + "\n\n"
				+ "Thresholded value:\t" + df.format(-0.2)
				+ "\n-----------\n";
		
		String cp2Description = "Intensity classifier\n\n"
				+ "Input class:\tAll" + "" + "\n\n"
				+ "Thresholded measurement:\t" + "intensityMeasurement4" + "\n\n"
				+ "Thresholded value:\t" + df.format(-0.2);
		
		assertEquals(cp1Description, cp1.getDescription());
		assertEquals(cp2Description, cp2.getDescription());
		assertEquals("", cpInvalid.getDescription());
	}
	
	@Test
	public void test_getName() {
		assertEquals("Composite classifier: Intensity classifier, Intensity classifier, Intensity classifier", cp1.getName());
		assertEquals("Intensity classifier", cp2.getName());
		assertEquals("Composite classifier (empty)", cpInvalid.getName());
	}
	
	@Test
	public void test_supportAutoUpdate() {
		assertFalse(cp1.supportsAutoUpdate());
		assertFalse(cp2.supportsAutoUpdate());
		assertFalse(cpInvalid.supportsAutoUpdate());
	}
	
	@Test
	public void test_updateClassifier() {
		Map<PathClass, List<PathObject>> map = new HashMap<>();	// Dummy map
		List<String> list = new ArrayList<>();	// Dummy list
		
		// These below should be true even if they are all composite classifiers with intensity classifiers (and do nothing really)
		assertTrue(cp1.updateClassifier(map, list, Normalization.NONE));
		assertTrue(cp2.updateClassifier(map, list, Normalization.MEAN_VARIANCE));
		
		// Empty composite classifier never trigger the updateCalled flag, so return false
		assertFalse(cpInvalid.updateClassifier(map, list, Normalization.MIN_MAX));
	}
	
	@Test
	public void test_classifyPathObjects() {
		MeasurementList ml1 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		MeasurementList ml2 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		MeasurementList ml3 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		MeasurementList ml4 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		
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
		
		// Create annotation objects
		PathObject obj1 = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), null, ml1);
		PathObject obj2 = PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 10, 5, 5, ImagePlane.getDefaultPlane()), pathClass1, ml2);
		PathObject obj3 = PathObjects.createAnnotationObject(ROIs.createEllipseROI(100, 100, 5, 5, ImagePlane.getDefaultPlane()), pathClass2, ml3);
		PathObject obj4 = PathObjects.createAnnotationObject(ROIs.createLineROI(0, 0, ImagePlane.getDefaultPlane()), pathClass3, ml4);

		// Classify objects and check classification manually
		var objs = Arrays.asList(obj1, obj2, obj3, obj4);
		
		// Composite classifier 1
		var classifiedObjs1 = cp1.classifyPathObjects(objs);
		assertEquals(1, classifiedObjs1);	// Last classifier classified one object only (obj4)
		assertEquals(null, obj1.getPathClass());	// Unchanged
		assertEquals(PathClassFactory.getNegative(pathClass1), obj2.getPathClass());
		assertEquals(PathClassFactory.getPositive(pathClass2), obj3.getPathClass());
		assertEquals(PathClassFactory.getPositive(pathClass3), obj4.getPathClass());

		// Composite classifier 2
		var classifiedObjs2 = cp2.classifyPathObjects(objs);
		assertEquals(4, classifiedObjs2);
		assertEquals(null, obj1.getPathClass());	// Missing measurement -> unchanged
		assertEquals(PathClassFactory.getNegative(pathClass1), obj2.getPathClass());
		assertEquals(PathClassFactory.getPositive(pathClass2), obj3.getPathClass());
		assertEquals(pathClass3, obj4.getPathClass());	// Missing measurement -> reset
		
		// Invalid composite classifier
		var classifiedObjs3 = cpInvalid.classifyPathObjects(objs);
		assertEquals(0, classifiedObjs3);
	}
}