/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.objects.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;

@SuppressWarnings("javadoc")
public class TestPathClassFactory {
	
	@Test
	public void test_getPathClass1() {
		sameClass(new PathClass("Ignore*", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE));
		sameClass(new PathClass("Image", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT));
		sameClass(new PathClass("Immune cells", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMMUNE_CELLS));
		sameClass(new PathClass("Necrosis", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.NECROSIS));
		sameClass(new PathClass("Negative", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.NEGATIVE));
		sameClass(new PathClass("Other", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.OTHER));
		sameClass(new PathClass("Positive", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.POSITIVE));
		sameClass(new PathClass("Region*", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.REGION));
		sameClass(new PathClass("Stroma", ColorTools.makeRGB(180, 180, 180)), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.STROMA));
		
		// Can't use PathClass' constructor for 'Tumor' as getDerivedPathClass() creates an instance of 'Tumor' already.
		sameClass(PathClassFactory.getPathClass("Tumor"), PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.TUMOR));
	}
	
	@Test
	public void test_getPathClass2() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassFactory.getPathClass("Class with" + System.lineSeparator() + "new line", Color.RED.getRGB()));
		
		assertEquals("Third", PathClassFactory.getPathClass("First", "Second", "Third").getName());
		assertEquals("First: Second: Third", PathClassFactory.getPathClass("First", "Second", "Third").toString());
		assertEquals("Third", PathClassFactory.getPathClass(Arrays.asList("First", "Second", "Third")).getName());
		assertEquals("First: Second: Third", PathClassFactory.getPathClass(Arrays.asList("First", "Second", "Third")).toString());
		assertEquals(null, PathClassFactory.getPathClass(Arrays.asList()));
	}
	
	@Test
	public void test_getPathClass3() {
		var color1 = ColorTools.makeRGB(180, 180, 180);
		var colorOnePlus = ColorTools.makeScaledRGB(ColorTools.makeRGB(255, 215, 0), 1.25);
		var colorTwoPlus = ColorTools.makeScaledRGB(ColorTools.makeRGB(225, 150, 50), 1.25);
		var colorThreePlus = ColorTools.makeScaledRGB(ColorTools.makeRGB(200, 50, 50), 1.25);
		checkFields("test", "test", color1, new PathClass("test", color1));
		sameClass(PathClassFactory.getPathClassUnclassified(), PathClassFactory.getPathClass(null, color1));
		sameClass(PathClassFactory.getPathClassUnclassified(), PathClassFactory.getPathClass(PathClassFactory.getPathClassUnclassified().getName(), color1));
		sameClass(PathClassFactory.getPathClassUnclassified(), PathClassFactory.getPathClass(PathClassFactory.getPathClassUnclassified().getName(), color1));
		sameClass(PathClassFactory.getPathClassUnclassified(), PathClassFactory.getPathClass(PathClassFactory.getPathClassUnclassified().toString(), color1));
		
		checkFields("Child", "Parent: Child", color1, PathClassFactory.getPathClass("Parent: Child", color1));
		checkFields("Child", ": Child", color1, PathClassFactory.getPathClass(": Child", color1));
		
		checkFields("1+", "1+", colorOnePlus, PathClassFactory.getPathClass(PathClassFactory.ONE_PLUS));
		checkFields("2+", "2+", colorTwoPlus, PathClassFactory.getPathClass(PathClassFactory.TWO_PLUS));
		checkFields("3+", "3+", colorThreePlus, PathClassFactory.getPathClass(PathClassFactory.THREE_PLUS));

		checkFields(PathClassFactory.ONE_PLUS, ": " + PathClassFactory.ONE_PLUS, PathClassFactory.getPathClass("", PathClassFactory.ONE_PLUS));
		checkFields(PathClassFactory.TWO_PLUS, ": " + PathClassFactory.TWO_PLUS, PathClassFactory.getPathClass("", PathClassFactory.TWO_PLUS));
		checkFields(PathClassFactory.THREE_PLUS, ": " + PathClassFactory.THREE_PLUS, PathClassFactory.getPathClass("", PathClassFactory.THREE_PLUS));
		checkFields(PathClassFactory.POSITIVE, ": " + PathClassFactory.POSITIVE, PathClassFactory.getPathClass("", PathClassFactory.POSITIVE));
		checkFields(PathClassFactory.NEGATIVE, ": " + PathClassFactory.NEGATIVE, PathClassFactory.getPathClass("", PathClassFactory.NEGATIVE));
	}
	
	@Test
	public void test_getOnePlus() {
		checkFields("1+", "Test: 1+", PathClassFactory.getOnePlus(PathClassFactory.getPathClass("Test")));
		checkFields("1+", "Test: 1+: 1+", PathClassFactory.getOnePlus(PathClassFactory.getPathClass("Test: 1+")));
	}
	
	@Test
	public void test_getTwoPlus() {
		checkFields("2+", "Test: 2+", PathClassFactory.getTwoPlus(PathClassFactory.getPathClass("Test")));
		checkFields("2+", "Test: 2+: 2+", PathClassFactory.getTwoPlus(PathClassFactory.getPathClass("Test: 2+")));
	}

	@Test
	public void test_getThreePlus() {
		checkFields("3+", "Test: 3+", PathClassFactory.getThreePlus(PathClassFactory.getPathClass("Test")));
		checkFields("3+", "Test: 3+: 3+", PathClassFactory.getThreePlus(PathClassFactory.getPathClass("Test: 3+")));
	}
	
	@Test
	public void test_getDerivedPathClass() {
		// TODO: Investigate whether PathClassFactory.validateName() should behave the same as 
		// PathClass.isValid(), as one restricts new lines and the other doesn't. See next 2 lines.
		assertTrue(new PathClass("invalid" + System.lineSeparator() + "class", Color.RED.getRGB()).isValid());
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClassFactory.getPathClass("invalid" + System.lineSeparator() + "class", Color.RED.getRGB()));
	}
	
	@Test
	public void test_getSingletonPathClass() {
		// TODO
	}
	
	@Test
	public void test_getPathClassUnclassified() {
		sameClass(new PathClass(), PathClassFactory.getPathClassUnclassified().getBaseClass());
	}
	
	private static void sameClass(PathClass expected, PathClass actual) {
		assertEquals(expected.toString(), actual.toString());
	}
	
	private static void checkFields(String name, String toString, int color, PathClass pathClass) {
		assertEquals(name, pathClass.getName());
		assertEquals(toString, pathClass.toString());
		assertEquals(color, pathClass.getColor());
	}
	
	private static void checkFields(String name, String toString, PathClass pathClass) {
		assertEquals(name, pathClass.getName());
		assertEquals(toString, pathClass.toString());
	}
}
