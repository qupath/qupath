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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;

@SuppressWarnings("javadoc")
public class TestPathClassTools {
	
	private static PathClass pc1;
	private static PathClass pc2;
	private static PathClass pc3;
	private static PathClass pc4;
	private static PathClass pc5;
	private static PathClass pc6;
	private static PathClass pc7;
	private static PathClass pc8;
	private static PathClass pc9;
	private static PathClass pc10;
	private static PathClass pc11;
	private static PathClass pc12;
	private static PathClass pc13;
	private static PathClass pc14;
	private static PathClass pc15;
	private static PathClass pc16;
	private static PathClass derivedClass1;
	private static PathClass derivedClass2;
	
	@BeforeAll
	public static void init() {
		// Ignored/non-ignored classes and subclasses
		pc1 = PathClassFactory.getPathClass("test", Color.BLUE.getRGB());
		pc2 = PathClassFactory.getPathClass("test", "subclass");
		pc3 = PathClassFactory.getPathClass("test*", Color.BLACK.getRGB());
		pc4 = PathClassFactory.getPathClass("test*", "subclass");
		pc5 = PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE);
		pc5.setColor(Color.BLACK.getRGB());
		pc6 = PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE);
		pc6 = PathClassFactory.getDerivedPathClass(pc6, "subclass", Color.BLACK.getRGB());
		pc7 = PathClassFactory.getNegative(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE));
		
		// Pos/neg & intensity classes
		pc8 = PathClassFactory.getNegative(PathClassFactory.getPathClass("negativeClass", Color.RED.getRGB()));
		pc9 = PathClassFactory.getPositive(PathClassFactory.getPathClass("positiveClass", Color.RED.getRGB()));
		pc10 = PathClassFactory.getOnePlus(PathClassFactory.getPathClass("intensityClass", Color.RED.getRGB()));
		pc11 = PathClassFactory.getTwoPlus(PathClassFactory.getPathClass("intensityClass", Color.RED.getRGB()));
		pc12 = PathClassFactory.getThreePlus(PathClassFactory.getPathClass("intensityClass", Color.RED.getRGB()));
		
		// Both pos/neg/intensity and subclass classes
		pc13 = PathClassFactory.getPositive(PathClassFactory.getPathClass("positiveClass", "subclass"));
		pc14 = PathClassFactory.getOnePlus(PathClassFactory.getPathClass("intensityClass", "subclass"));
		
		// 'Null' classes
		pc15 = PathClassFactory.getPathClass(null, Color.BLUE.getRGB());
		pc16 = PathClassFactory.getPathClassUnclassified();
		
		// Derived class
		derivedClass1 = PathClassFactory.getPathClass("First");
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Second", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Third", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Fourth", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Fifth", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Sixth", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Seventh", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Eighth", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Ninth", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Tenth", Color.RED.getRGB());
		derivedClass1 = PathClassFactory.getDerivedPathClass(derivedClass1, "Eleventh", Color.RED.getRGB());
		
		derivedClass2 = PathClassFactory.getPathClass("First");
		derivedClass2 = PathClassFactory.getDerivedPathClass(derivedClass2, "Second", Color.RED.getRGB());
		derivedClass2 = PathClassFactory.getDerivedPathClass(derivedClass2, "Third", Color.RED.getRGB());
		derivedClass2 = PathClassFactory.getDerivedPathClass(derivedClass2, "Fourth", Color.RED.getRGB());
		derivedClass2 = PathClassFactory.getDerivedPathClass(derivedClass2, "Fifth", Color.RED.getRGB());
	}

	@Test
	public void test_isGradedIntensityClass() {
		assertFalse(PathClassTools.isGradedIntensityClass(pc1));
		assertFalse(PathClassTools.isGradedIntensityClass(pc2));
		assertFalse(PathClassTools.isGradedIntensityClass(pc3));
		assertFalse(PathClassTools.isGradedIntensityClass(pc4));
		assertFalse(PathClassTools.isGradedIntensityClass(pc5));
		assertFalse(PathClassTools.isGradedIntensityClass(pc6));
		assertFalse(PathClassTools.isGradedIntensityClass(pc7));
		
		assertFalse(PathClassTools.isGradedIntensityClass(pc8));
		assertFalse(PathClassTools.isGradedIntensityClass(pc9));
		assertTrue(PathClassTools.isGradedIntensityClass(pc10));
		assertTrue(PathClassTools.isGradedIntensityClass(pc11));
		assertTrue(PathClassTools.isGradedIntensityClass(pc12));

		assertFalse(PathClassTools.isGradedIntensityClass(pc13));
		assertTrue(PathClassTools.isGradedIntensityClass(pc14));
		
		assertFalse(PathClassTools.isGradedIntensityClass(pc15));
		assertFalse(PathClassTools.isGradedIntensityClass(pc16));

		assertFalse(PathClassTools.isGradedIntensityClass(null));
	}
	
	@Test
	public void test_isIgnoredClass() {
		assertFalse(PathClassTools.isIgnoredClass(pc1));
		assertFalse(PathClassTools.isIgnoredClass(pc2));
		assertTrue(PathClassTools.isIgnoredClass(pc3));
		assertFalse(PathClassTools.isIgnoredClass(pc4));
		assertTrue(PathClassTools.isIgnoredClass(pc5));
		assertFalse(PathClassTools.isIgnoredClass(pc6));	// Classes derived from 'ignored' classes are not 'ignored' anymore
		assertFalse(PathClassTools.isIgnoredClass(pc7));	// Classes derived from 'ignored' classes are not 'ignored' anymore
		
		assertFalse(PathClassTools.isIgnoredClass(pc8));
		assertFalse(PathClassTools.isIgnoredClass(pc9));
		assertFalse(PathClassTools.isIgnoredClass(pc10));
		assertFalse(PathClassTools.isIgnoredClass(pc11));
		assertFalse(PathClassTools.isIgnoredClass(pc12));
		
		assertFalse(PathClassTools.isIgnoredClass(pc13));
		assertFalse(PathClassTools.isIgnoredClass(pc14));
	
		assertTrue(PathClassTools.isIgnoredClass(pc15));
		assertTrue(PathClassTools.isIgnoredClass(pc16));

		assertTrue(PathClassTools.isIgnoredClass(null));
	}
	
	@Test
	public void test_isNullClass() {
		assertFalse(PathClassTools.isNullClass(pc1));
		assertFalse(PathClassTools.isNullClass(pc2));
		assertFalse(PathClassTools.isNullClass(pc3));
		assertFalse(PathClassTools.isNullClass(pc4));
		assertFalse(PathClassTools.isNullClass(pc5));
		assertFalse(PathClassTools.isNullClass(pc6));
		assertFalse(PathClassTools.isNullClass(pc7));
		
		assertFalse(PathClassTools.isNullClass(pc8));
		assertFalse(PathClassTools.isNullClass(pc9));
		assertFalse(PathClassTools.isNullClass(pc10));
		assertFalse(PathClassTools.isNullClass(pc11));
		assertFalse(PathClassTools.isNullClass(pc12));
		
		assertFalse(PathClassTools.isNullClass(pc13));
		assertFalse(PathClassTools.isNullClass(pc14));
		
		assertTrue(PathClassTools.isNullClass(pc15));
		assertTrue(PathClassTools.isNullClass(pc16));

		assertTrue(PathClassTools.isNullClass(null));
	}
	
	@Test
	public void test_isOnePlus() {
		assertFalse(PathClassTools.isOnePlus(pc1));
		assertFalse(PathClassTools.isOnePlus(pc2));
		assertFalse(PathClassTools.isOnePlus(pc3));
		assertFalse(PathClassTools.isOnePlus(pc4));
		assertFalse(PathClassTools.isOnePlus(pc5));
		assertFalse(PathClassTools.isOnePlus(pc6));
		assertFalse(PathClassTools.isOnePlus(pc7));
		
		assertFalse(PathClassTools.isOnePlus(pc8));
		assertFalse(PathClassTools.isOnePlus(pc9));
		assertTrue(PathClassTools.isOnePlus(pc10));
		assertFalse(PathClassTools.isOnePlus(pc11));
		assertFalse(PathClassTools.isOnePlus(pc12));
		
		assertFalse(PathClassTools.isOnePlus(pc13));
		assertTrue(PathClassTools.isOnePlus(pc14));

		assertFalse(PathClassTools.isOnePlus(pc15));
		assertFalse(PathClassTools.isOnePlus(pc16));

		assertFalse(PathClassTools.isOnePlus(null));
	}
	
	@Test
	public void test_isTwoPlus() {
		assertFalse(PathClassTools.isTwoPlus(pc1));
		assertFalse(PathClassTools.isTwoPlus(pc2));
		assertFalse(PathClassTools.isTwoPlus(pc3));
		assertFalse(PathClassTools.isTwoPlus(pc4));
		assertFalse(PathClassTools.isTwoPlus(pc5));
		assertFalse(PathClassTools.isTwoPlus(pc6));
		assertFalse(PathClassTools.isTwoPlus(pc7));
		
		assertFalse(PathClassTools.isTwoPlus(pc8));
		assertFalse(PathClassTools.isTwoPlus(pc9));
		assertFalse(PathClassTools.isTwoPlus(pc10));
		assertTrue(PathClassTools.isTwoPlus(pc11));
		assertFalse(PathClassTools.isTwoPlus(pc12));
		
		assertFalse(PathClassTools.isTwoPlus(pc13));
		assertFalse(PathClassTools.isTwoPlus(pc14));

		assertFalse(PathClassTools.isTwoPlus(pc15));
		assertFalse(PathClassTools.isTwoPlus(pc16));

		assertFalse(PathClassTools.isTwoPlus(null));
	}
	
	@Test
	public void test_isThreePlus() {
		assertFalse(PathClassTools.isThreePlus(pc1));
		assertFalse(PathClassTools.isThreePlus(pc2));
		assertFalse(PathClassTools.isThreePlus(pc3));
		assertFalse(PathClassTools.isThreePlus(pc4));
		assertFalse(PathClassTools.isThreePlus(pc5));
		assertFalse(PathClassTools.isThreePlus(pc6));
		assertFalse(PathClassTools.isThreePlus(pc7));
		
		assertFalse(PathClassTools.isThreePlus(pc8));
		assertFalse(PathClassTools.isThreePlus(pc9));
		assertFalse(PathClassTools.isThreePlus(pc10));
		assertFalse(PathClassTools.isThreePlus(pc11));
		assertTrue(PathClassTools.isThreePlus(pc12));
		
		assertFalse(PathClassTools.isThreePlus(pc13));
		assertFalse(PathClassTools.isThreePlus(pc14));

		assertFalse(PathClassTools.isThreePlus(pc15));
		assertFalse(PathClassTools.isThreePlus(pc16));

		assertFalse(PathClassTools.isThreePlus(null));
	}
	
	@Test
	public void test_isPositiveClass() {
		assertFalse(PathClassTools.isPositiveClass(pc1));
		assertFalse(PathClassTools.isPositiveClass(pc2));
		assertFalse(PathClassTools.isPositiveClass(pc3));
		assertFalse(PathClassTools.isPositiveClass(pc4));
		assertFalse(PathClassTools.isPositiveClass(pc5));
		assertFalse(PathClassTools.isPositiveClass(pc6));
		assertFalse(PathClassTools.isPositiveClass(pc7));
		
		assertFalse(PathClassTools.isPositiveClass(pc8));
		assertTrue(PathClassTools.isPositiveClass(pc9));
		assertFalse(PathClassTools.isPositiveClass(pc10));
		assertFalse(PathClassTools.isPositiveClass(pc11));
		assertFalse(PathClassTools.isPositiveClass(pc12));
		
		assertTrue(PathClassTools.isPositiveClass(pc13));
		assertFalse(PathClassTools.isPositiveClass(pc14));

		assertFalse(PathClassTools.isPositiveClass(pc15));
		assertFalse(PathClassTools.isPositiveClass(pc16));

		assertFalse(PathClassTools.isPositiveClass(null));
	}
	
	@Test
	public void test_isPositiveOrGradedIntensityClass() {
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc1));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc2));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc3));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc4));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc5));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc6));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc7));
		
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc8));
		assertTrue(PathClassTools.isPositiveOrGradedIntensityClass(pc9));
		assertTrue(PathClassTools.isPositiveOrGradedIntensityClass(pc10));
		assertTrue(PathClassTools.isPositiveOrGradedIntensityClass(pc11));
		assertTrue(PathClassTools.isPositiveOrGradedIntensityClass(pc12));
		
		assertTrue(PathClassTools.isPositiveOrGradedIntensityClass(pc13));
		assertTrue(PathClassTools.isPositiveOrGradedIntensityClass(pc14));

		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc15));
		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(pc16));

		assertFalse(PathClassTools.isPositiveOrGradedIntensityClass(null));
	}
	
	@Test
	public void test_isNegativeClass() {
		assertFalse(PathClassTools.isNegativeClass(pc1));
		assertFalse(PathClassTools.isNegativeClass(pc2));
		assertFalse(PathClassTools.isNegativeClass(pc3));
		assertFalse(PathClassTools.isNegativeClass(pc4));
		assertFalse(PathClassTools.isNegativeClass(pc5));
		assertFalse(PathClassTools.isNegativeClass(pc6));
		assertTrue(PathClassTools.isNegativeClass(pc7));
		
		assertTrue(PathClassTools.isNegativeClass(pc8));
		assertFalse(PathClassTools.isNegativeClass(pc9));
		assertFalse(PathClassTools.isNegativeClass(pc10));
		assertFalse(PathClassTools.isNegativeClass(pc11));
		assertFalse(PathClassTools.isNegativeClass(pc12));
		
		assertFalse(PathClassTools.isNegativeClass(pc13));
		assertFalse(PathClassTools.isNegativeClass(pc14));

		assertFalse(PathClassTools.isNegativeClass(pc15));
		assertFalse(PathClassTools.isNegativeClass(pc16));

		assertFalse(PathClassTools.isNegativeClass(null));
	}
	
	@Test
	public void test_getNonIntensityAncestorClass() {
		assertEquals(pc1, PathClassTools.getNonIntensityAncestorClass(pc1));
		assertEquals(pc2, PathClassTools.getNonIntensityAncestorClass(pc2));
		assertEquals(pc3, PathClassTools.getNonIntensityAncestorClass(pc3));
		assertEquals(pc4, PathClassTools.getNonIntensityAncestorClass(pc4));
		assertEquals(pc5, PathClassTools.getNonIntensityAncestorClass(pc5));
		assertEquals(pc6, PathClassTools.getNonIntensityAncestorClass(pc6));
		assertEquals(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE), PathClassTools.getNonIntensityAncestorClass(pc7));
		
		assertEquals(PathClassFactory.getPathClass("negativeClass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc8));
		assertEquals(PathClassFactory.getPathClass("positiveClass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc9));
		assertEquals(PathClassFactory.getPathClass("intensityClass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc10));
		assertEquals(PathClassFactory.getPathClass("intensityClass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc11));
		assertEquals(PathClassFactory.getPathClass("intensityClass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc12));
		
		assertEquals(PathClassFactory.getPathClass("positiveClass: subclass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc13));
		assertEquals(PathClassFactory.getPathClass("intensityClass: subclass", Color.RED.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc14));

		assertEquals(PathClassFactory.getPathClass(null, Color.BLUE.getRGB()), PathClassTools.getNonIntensityAncestorClass(pc15));
		assertEquals(PathClassFactory.getPathClassUnclassified(), PathClassTools.getNonIntensityAncestorClass(pc16));

		assertEquals(null, PathClassTools.getNonIntensityAncestorClass(null));
	}
	
	@Test
	public void test_splitNames() {
		var test = PathClassFactory.getPathClass("test");
		var test2 = PathClassFactory.getPathClass("test*");
		var subclass = PathClassFactory.getPathClass("subclass");
		var intensityClass = PathClassFactory.getPathClass("intensityClass");
		var negativeClass = PathClassFactory.getPathClass("negativeClass", Color.RED.getRGB());
		var positiveClass = PathClassFactory.getPathClass("positiveClass", Color.RED.getRGB());
		var negativeClass2= PathClassFactory.getNegative(null);
		var positiveClass2 = PathClassFactory.getPositive(null);
		
		assertEquals(getClassNames(pc1), PathClassTools.splitNames(pc1));
		assertEquals(getClassNames(test, subclass), PathClassTools.splitNames(pc2));
		assertEquals(getClassNames(pc3), PathClassTools.splitNames(pc3));
		assertEquals(getClassNames(test2, subclass), PathClassTools.splitNames(pc4));
		assertEquals(getClassNames(pc5), PathClassTools.splitNames(pc5));
		assertEquals(getClassNames(pc5, subclass), PathClassTools.splitNames(pc6));
		assertEquals(getClassNames(pc5, PathClassFactory.getNegative(null)), PathClassTools.splitNames(pc7));
		
		assertEquals(getClassNames(negativeClass, negativeClass2), PathClassTools.splitNames(pc8));
		assertEquals(getClassNames(positiveClass, positiveClass2), PathClassTools.splitNames(pc9));
		assertEquals(getClassNames(intensityClass, PathClassFactory.getOnePlus(null)), PathClassTools.splitNames(pc10));
		assertEquals(getClassNames(intensityClass, PathClassFactory.getTwoPlus(null)), PathClassTools.splitNames(pc11));
		assertEquals(getClassNames(intensityClass, PathClassFactory.getThreePlus(null)), PathClassTools.splitNames(pc12));
		
		assertEquals(getClassNames(positiveClass, subclass, positiveClass2), PathClassTools.splitNames(pc13));
		assertEquals(getClassNames(intensityClass, subclass, PathClassFactory.getOnePlus(null)), PathClassTools.splitNames(pc14));

		assertEquals(Arrays.asList(), PathClassTools.splitNames(pc15));
		assertEquals(Arrays.asList(), PathClassTools.splitNames(pc16));

		assertEquals(Arrays.asList(), PathClassTools.splitNames(null));
	}
	
	@Test
	public void test_uniqueNames() {	
		assertEquals(pc1, PathClassTools.uniqueNames(pc1));
		assertEquals(pc2, PathClassTools.uniqueNames(pc2));
		assertEquals(pc3, PathClassTools.uniqueNames(pc3));
		assertEquals(pc4, PathClassTools.uniqueNames(pc4));
		assertEquals(pc5, PathClassTools.uniqueNames(pc5));
		assertEquals(pc6, PathClassTools.uniqueNames(pc6));
		assertEquals(pc7, PathClassTools.uniqueNames(pc7));
		
		assertEquals(pc8, PathClassTools.uniqueNames(pc8));
		assertEquals(pc9, PathClassTools.uniqueNames(pc9));
		assertEquals(pc10, PathClassTools.uniqueNames(pc10));
		assertEquals(pc11, PathClassTools.uniqueNames(pc11));
		assertEquals(pc12, PathClassTools.uniqueNames(pc12));
		
		assertEquals(pc13, PathClassTools.uniqueNames(pc13));
		assertEquals(pc14, PathClassTools.uniqueNames(pc14));
		
		assertEquals(pc15, PathClassTools.uniqueNames(pc15));
		assertEquals(pc16, PathClassTools.uniqueNames(pc16));

		assertEquals(null, PathClassTools.uniqueNames(null));

		var duplicateClasses = PathClassFactory.getNegative(null);
		duplicateClasses = PathClassFactory.getDerivedPathClass(duplicateClasses, "Negative", Color.CYAN.getRGB());
		duplicateClasses = PathClassFactory.getDerivedPathClass(duplicateClasses, "Negative", Color.CYAN.getRGB());
		assertEquals(PathClassFactory.getPathClass("Negative"), PathClassTools.uniqueNames(duplicateClasses));

		var duplicateClasses2 = PathClassFactory.getNegative(null);
		duplicateClasses2 = PathClassFactory.getDerivedPathClass(duplicateClasses2, "negative", Color.CYAN.getRGB());
		duplicateClasses2 = PathClassFactory.getDerivedPathClass(duplicateClasses2, "Negative", Color.CYAN.getRGB());
		assertEquals(PathClassFactory.getPathClass("Negative: negative"), PathClassTools.uniqueNames(duplicateClasses2));
	}
	
	@Test
	public void test_sortNames() {
		assertEquals(PathClassFactory.getPathClass("Eighth", 
				"Eleventh", 
				"Fifth", 
				"First", 
				"Fourth", 
				"Ninth", 
				"Second", 
				"Seventh", 
				"Sixth", 
				"Tenth", 
				"Third"), PathClassTools.sortNames(derivedClass1));
	}
	
	@Test
	public void test_removeNames() {
		var derivedClassAfter = PathClassFactory.getPathClass("First");
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Third", Color.RED.getRGB());
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Fifth", Color.RED.getRGB());
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Sixth", Color.RED.getRGB());
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Eighth", Color.RED.getRGB());
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Ninth", Color.RED.getRGB());
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Tenth", Color.RED.getRGB());
		derivedClassAfter = PathClassFactory.getDerivedPathClass(derivedClassAfter, "Eleventh", Color.RED.getRGB());

		var derivedClassAfter2 = PathClassFactory.getPathClass("Second");
		derivedClassAfter2 = PathClassFactory.getDerivedPathClass(derivedClassAfter2, "Third", Color.RED.getRGB());
		derivedClassAfter2 = PathClassFactory.getDerivedPathClass(derivedClassAfter2, "Fifth", Color.RED.getRGB());

		assertEquals(derivedClassAfter, PathClassTools.removeNames(derivedClass1, "Second", "Fourth", "Seventh"));
		assertEquals(derivedClassAfter2, PathClassTools.removeNames(derivedClass2, "First", "Fourth"));
		assertEquals(derivedClass1, PathClassTools.removeNames(derivedClass1, ""));
	}
	
	@Test
	public void test_mergeClasses() {
		assertEquals(PathClassFactory.getPathClass("test: subclass"), PathClassTools.mergeClasses(pc1, pc2));
		assertEquals(PathClassFactory.getPathClass("test: subclass: test*"), PathClassTools.mergeClasses(pc2, pc3));
		assertEquals(PathClassFactory.getPathClass("test*: subclass"), PathClassTools.mergeClasses(pc3, pc4));
		assertEquals(PathClassFactory.getPathClass("test*: subclass: Ignore*"), PathClassTools.mergeClasses(pc4, pc5));
		assertEquals(PathClassFactory.getPathClass("Ignore*: subclass"), PathClassTools.mergeClasses(pc5, pc6));
		assertEquals(PathClassFactory.getPathClass("Ignore*: subclass: Negative"), PathClassTools.mergeClasses(pc6, pc7));
		assertEquals(PathClassFactory.getPathClass("Ignore*: Negative: negativeClass"), PathClassTools.mergeClasses(pc7, pc8));
		assertEquals(PathClassFactory.getPathClass("negativeClass: Negative: positiveClass: Positive"), PathClassTools.mergeClasses(pc8, pc9));
		assertEquals(PathClassFactory.getPathClass("positiveClass: Positive: intensityClass: 1+"), PathClassTools.mergeClasses(pc9, pc10));
		assertEquals(PathClassFactory.getPathClass("intensityClass: 1+: 2+"), PathClassTools.mergeClasses(pc10, pc11));
		assertEquals(PathClassFactory.getPathClass("intensityClass: 2+: 3+"), PathClassTools.mergeClasses(pc11, pc12));
		assertEquals(PathClassFactory.getPathClass("intensityClass: 3+: positiveClass: subclass: Positive"), PathClassTools.mergeClasses(pc12, pc13));
		assertEquals(PathClassFactory.getPathClass("positiveClass: subclass: Positive: intensityClass: 1+"), PathClassTools.mergeClasses(pc13, pc14));
		assertEquals(PathClassFactory.getPathClass("intensityClass: subclass: 1+"), PathClassTools.mergeClasses(pc14, pc15));
		assertEquals(PathClassFactory.getPathClassUnclassified(), PathClassTools.mergeClasses(pc15, pc16));
		assertEquals(null, PathClassTools.mergeClasses(null, pc16));
		assertEquals(pc1, PathClassTools.mergeClasses(pc16, pc1));
	}
	
	@Test
	public void test_containsName() {
		assertTrue(PathClassTools.containsName(PathClassFactory.getPathClass("test: subclass"), "subclass"));
		assertFalse(PathClassTools.containsName(PathClassFactory.getPathClass("test: subclass"), ""));
		assertFalse(PathClassTools.containsName(PathClassFactory.getPathClass("test: subclass"), "test*"));
		assertFalse(PathClassTools.containsName(null, "test*"));
		assertFalse(PathClassTools.containsName(pc15, "test*"));
		assertFalse(PathClassTools.containsName(pc16, "test*"));
	}
	
	public static List<String> getClassNames(PathClass... pcs) {
		List<String> list = new ArrayList<>();
		for (int i = 0; i < pcs.length; i++) {
			list.add(pcs[i].getName());
		}
		return list;
	}
}
