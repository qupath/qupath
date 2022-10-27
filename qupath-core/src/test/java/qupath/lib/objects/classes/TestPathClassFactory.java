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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;

@SuppressWarnings("javadoc")
public class TestPathClassFactory {
	
	@Test
	public void test_getPathClass1() {
		assertEquals("Ignore*", PathClass.StandardPathClasses.IGNORE.getName());
		assertEquals("Image", PathClass.StandardPathClasses.IMAGE_ROOT.getName());
		assertEquals("Immune cells", PathClass.StandardPathClasses.IMMUNE_CELLS.getName());
		assertEquals("Necrosis", PathClass.StandardPathClasses.NECROSIS.getName());
		assertEquals("Negative", PathClass.StandardPathClasses.NEGATIVE.getName());
		assertEquals("Other", PathClass.StandardPathClasses.OTHER.getName());
		assertEquals("Positive",PathClass.StandardPathClasses.POSITIVE.getName());
		assertEquals("Region*", PathClass.StandardPathClasses.REGION.getName());
		assertEquals("Stroma", PathClass.StandardPathClasses.STROMA.getName());
	}
	
	@Test
	public void test_getPathClass2() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> PathClass.fromString("Class with\nnew line", ColorTools.RED));
		
		assertEquals("Third", PathClass.fromArray("First", "Second", "Third").getName());
		assertEquals("First: Second: Third", PathClass.fromArray("First", "Second", "Third").toString());
		assertEquals("Third", PathClass.fromCollection(Arrays.asList("First", "Second", "Third")).getName());
		assertEquals("First: Second: Third", PathClass.fromCollection(Arrays.asList("First", "Second", "Third")).toString());
		assertEquals(PathClass.NULL_CLASS, PathClass.fromCollection(Arrays.asList()));
	}
	
	@Test
	public void test_getPathClass3() {
		var color1 = ColorTools.packRGB(180, 180, 180);
		var colorOnePlus = ColorTools.makeScaledRGB(ColorTools.packRGB(255, 215, 0), 1.25);
		var colorTwoPlus = ColorTools.makeScaledRGB(ColorTools.packRGB(225, 150, 50), 1.25);
		var colorThreePlus = ColorTools.makeScaledRGB(ColorTools.packRGB(200, 50, 50), 1.25);
		String uniqueName = UUID.randomUUID().toString();
		checkFields(uniqueName, uniqueName, color1, PathClass.getInstance(uniqueName, color1));
		sameClass(PathClass.NULL_CLASS, PathClass.getInstance((String)null, color1));
		sameClass(PathClass.NULL_CLASS, PathClass.getInstance(PathClass.NULL_CLASS.getName(), color1));
		sameClass(PathClass.NULL_CLASS, PathClass.getInstance(PathClass.NULL_CLASS.getName(), color1));
		sameClass(PathClass.NULL_CLASS, PathClass.getInstance(PathClass.NULL_CLASS.toString(), color1));
		
		checkFields("Child", "Parent: Child", color1, PathClass.fromString("Parent:Child", color1));
		
		// New in v0.4.0: don't allow creating a PathClass with an empty parent
		assertThrows(IllegalArgumentException.class, () -> PathClass.getInstance(":Child", color1));
		assertThrows(IllegalArgumentException.class, () -> PathClass.fromArray("", "Child"));
		
		checkFields("1+", "1+", colorOnePlus, PathClass.getInstance(PathClass.NAME_ONE_PLUS));
		checkFields("2+", "2+", colorTwoPlus, PathClass.getInstance(PathClass.NAME_TWO_PLUS));
		checkFields("3+", "3+", colorThreePlus, PathClass.getInstance(PathClass.NAME_THREE_PLUS));

		checkFields(PathClass.NAME_ONE_PLUS, PathClass.NAME_ONE_PLUS, PathClass.getInstance(PathClass.NAME_ONE_PLUS));
		checkFields(PathClass.NAME_TWO_PLUS, PathClass.NAME_TWO_PLUS, PathClass.getInstance(PathClass.NAME_TWO_PLUS));
		checkFields(PathClass.NAME_THREE_PLUS, PathClass.NAME_THREE_PLUS, PathClass.getInstance(PathClass.NAME_THREE_PLUS));
		checkFields(PathClass.NAME_POSITIVE, PathClass.NAME_POSITIVE, PathClass.getInstance(PathClass.NAME_POSITIVE));
		checkFields(PathClass.NAME_NEGATIVE, PathClass.NAME_NEGATIVE, PathClass.getInstance(PathClass.NAME_NEGATIVE));
		
		var sameClasses = Arrays.asList(
				"My:Class",
				"My: Class",
				"My:\tClass",
				"My:     Class",
				" My:Class "
//				"My::Class", // Following not allowed since v0.4.0
//				"My: :Class"
//				"My::\nClass" 
		);
		var unclassifiedClasses = Arrays.asList((String)null);
		var invalidClasses = Arrays.asList(":\n", "My::Invalid\nClass", ": :", ":\n:");
		
		for (var clazz: sameClasses) {
			assertEquals("My", PathClass.fromString(clazz, ColorTools.CYAN).getParentClass().getName());
			assertEquals("Class", PathClass.fromString(clazz, ColorTools.CYAN).getName());
			assertEquals("My: Class", PathClass.fromString(clazz, ColorTools.RED).toString());
		}
		for (var clazz: unclassifiedClasses) {
			assertEquals(PathClass.NULL_CLASS, PathClass.fromString(clazz, ColorTools.CYAN));
		}
		for (var clazz: invalidClasses) {
			Assertions.assertThrows(IllegalArgumentException.class, () -> PathClass.fromString(clazz, ColorTools.CYAN));
		}
	}
	
	@Test
	public void test_duplicatePathClass() {
		// Failed in v0.3.2 and before because second part not stripped
		var pc1 = PathClass.fromArray("Something", "else ");
		var pc2 = PathClass.fromArray("Something", "else ");
		assertEquals(pc1, pc2);
		assertEquals(pc1.toString(), pc2.toString());
		assertEquals(pc1.toSet(), pc2.toSet());
		assertEquals(pc1.toList(), pc2.toList());
		assertSame(pc1, pc2);
		assertTrue(pc1 == pc2);

		var pc3 = PathClass.fromArray("Something", "else ", " entirely");
		var pc4 = PathClass.fromArray(" Something", " else ", "\tentirely");
		assertEquals(pc3, pc4);
		assertEquals(pc3.toString(), pc4.toString());
		assertEquals(pc3.toSet(), pc4.toSet());
		assertEquals(pc3.toList(), pc4.toList());
		assertSame(pc3, pc4);
		assertTrue(pc3 == pc4);
	}
	
	
	@Test
	public void test_concurrentCreate() {
		var list = List.of("Some class", "Another");
		var allClasses = IntStream.range(0, 1000)
			.parallel()
			.mapToObj(i -> PathClass.fromCollection(list))
			.collect(Collectors.toList());
		
		var target = PathClass.fromCollection(list);
		for (var source : allClasses) {
			assertSame(target, source);
		}
		assertEquals(1, new HashSet<>(allClasses).size());
	}
	
	
	
	@Test
	public void test_getOnePlus() {
		checkFields("1+", "Test: 1+", PathClass.getOnePlus(PathClass.getInstance("Test")));
		checkFields("1+", "Test: 1+: 1+", PathClass.getOnePlus(PathClass.fromString("Test: 1+")));
	}
	
	@Test
	public void test_getTwoPlus() {
		checkFields("2+", "Test: 2+", PathClass.getTwoPlus(PathClass.getInstance("Test")));
		checkFields("2+", "Test: 2+: 2+", PathClass.getTwoPlus(PathClass.fromString("Test: 2+")));
	}

	@Test
	public void test_getThreePlus() {
		checkFields("3+", "Test: 3+", PathClass.getThreePlus(PathClass.getInstance("Test")));
		checkFields("3+", "Test: 3+: 3+", PathClass.getThreePlus(PathClass.fromString("Test: 3+")));
	}
	
	@Test
	public void test_getDerivedPathClass() {
		// TODO
	}
	
	@Test
	public void test_getSingletonPathClass() {
		// TODO
	}
	
	@Test
	public void test_getPathClassUnclassified() {
		sameClass(PathClass.NULL_CLASS, PathClass.NULL_CLASS.getBaseClass());
		sameClass(PathClass.getInstance((String)null), PathClass.NULL_CLASS.getBaseClass());
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
