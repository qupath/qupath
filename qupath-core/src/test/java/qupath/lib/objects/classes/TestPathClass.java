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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import qupath.lib.common.ColorTools;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("javadoc")
@Isolated
public class TestPathClass {

	@BeforeEach
	void resetClasses() {
		PathClass.resetCaches();
	}
	
	private static List<Integer> colors = Arrays.asList(
			ColorTools.BLACK,
			ColorTools.BLUE,
			ColorTools.RED,
			ColorTools.GREEN,
			ColorTools.CYAN,
			ColorTools.MAGENTA,
			ColorTools.WHITE,
			ColorTools.YELLOW
			);
	
	
	@Test
	public void test_classes() {
		// Check we can get the same null class
		assertSame(PathClass.getNullClass(), PathClass.getInstance(null, null, null));
		assertSame(PathClass.getNullClass(), PathClass.getInstance(null, null, ColorTools.RED));
		
		// Check we can create a parent
		PathClass parent = PathClass.getInstance(null, "parent", null);
		assertNotNull(parent);
		assertNotEquals(PathClass.getNullClass(), parent);
		assertTrue(parent.isValid());
		
		// Can't have a parent but null name
		assertThrows(IllegalArgumentException.class, () -> PathClass.getInstance(parent, null, null));

		// Can't have invalid characters
		List<String> invalidNames = Arrays.asList(
				"", ":", "any\nthing", "some:thing", "  ", "\t"
				);
		for (String invalid : invalidNames) {
			assertThrows(IllegalArgumentException.class, () -> PathClass.getInstance(null, invalid, null));
			assertThrows(IllegalArgumentException.class, () -> PathClass.getInstance(parent, invalid, null));
		}
		
		// Check parent and base classes
		assertNull(PathClass.getNullClass().getParentClass());
		assertNull(parent.getParentClass());
		assertSame(PathClass.getNullClass(), PathClass.getNullClass().getBaseClass());
		assertSame(parent, parent.getBaseClass());

		// Check we can create derived classes
		List<PathClass> derived = new ArrayList<>();
		PathClass lastClass = parent;
		for (int i = 0; i < colors.size(); i++) {
			String name = "Derived " + i;
			var color = colors.get(i);
			var nextClass = PathClass.getInstance(lastClass, name, color);
			assertSame(lastClass, nextClass.getParentClass());
			assertSame(parent, nextClass.getBaseClass());
			assertEquals(name, nextClass.getName());
			assertEquals(color, nextClass.getColor());
			
			// Check toString() method contains what it should
			assertTrue(nextClass.getName().endsWith(name));
			assertEquals(i, countColons(lastClass.toString()));
			assertEquals(i+1, countColons(nextClass.toString()));
			
			derived.add(nextClass);
			lastClass = nextClass;
		}
		
		// Try to create derived classes again - these should be identical
		lastClass = parent;
		for (int i = 0; i < colors.size(); i++) {
			String name = "Derived " + i;
			// Change the color
			var color = colors.get(colors.size()-1-i);
			var nextClass = PathClass.getInstance(lastClass, name, color);
			
			// What we should receive is actually the original class - not a new one
			assertEquals(derived.get(i).toString(), nextClass.toString());
			assertSame(derived.get(i), nextClass);
			
			assertSame(lastClass, nextClass.getParentClass());
			assertSame(parent, nextClass.getBaseClass());
			assertEquals(name, nextClass.getName());
			// Color isn't necessarily the same!
//			assertEquals(color, nextClass.getColor());
			
			// Check toString() method contains what it should
			assertTrue(nextClass.getName().endsWith(name));
			assertEquals(i, countColons(lastClass.toString()));
			assertEquals(i+1, countColons(nextClass.toString()));
			
			derived.add(nextClass);
			lastClass = nextClass;
		}
		
		// Check class names are trimmed
		assertEquals("Something", PathClass.getInstance(null, " Something", null).toString());
		assertEquals("Else", PathClass.getInstance(null, "Else   ", null).toString());
		
	}

	/**
	 * Check deserializing using the v0.4 JSON representation.
	 * @throws IOException
	 */
	@Test
	public void test_pathClassJsonDeserialization() throws IOException {
		try (var reader = new InputStreamReader(TestPathClass.class.getResourceAsStream("derived-class.json"))) {
			var pathClass = GsonTools.getInstance().fromJson(reader, PathClass.class);
			checkPathClassIsSingleton(pathClass);
		}
	}

	/**
	 * Check deserializing using the v0.3 JSON representation.
	 * @throws IOException
	 */
	@Test
	public void test_pathClassJsonLegacyDeserialization() throws IOException {
		try (var reader = new InputStreamReader(TestPathClass.class.getResourceAsStream("derived-class-legacy.json"))) {
			var pathClass = GsonTools.getInstance().fromJson(reader, PathClass.class);
			checkPathClassIsSingleton(pathClass);
		}
	}

	/**
	 * Check deserializing a PathClass directly from an object output stream.
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@Test
	public void test_pathClassJavaDeserialization() throws IOException, ClassNotFoundException {
		try (var stream = PathIO.createObjectInputStream(TestPathClass.class.getResourceAsStream("derived-class.serialized"))) {
			var pathClass = (PathClass)stream.readObject();
			checkPathClassIsSingleton(pathClass);
		}
	}

	/**
	 * Check deserializing directly from an object output stream, extracting PathClass from a PathObject.
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@Test
	public void test_pathObjectJavaDeserialization() throws IOException, ClassNotFoundException {
		try (var stream = PathIO.createObjectInputStream(TestPathClass.class.getResourceAsStream("derived-class-object.serialized"))) {
			var pathObject = (PathObject)stream.readObject();
			var pathClass = pathObject.getPathClass();
			checkPathClassIsSingleton(pathClass);
		}
	}

	/**
	 * Perform checks that a specified PathClass is a singleton, and that its base/parent classes also match those
	 * that can be obtained separately.
	 * <p>
	 * Introduced because of https://github.com/qupath/qupath/issues/1306
	 * @param pathClass
	 */
	private void checkPathClassIsSingleton(PathClass pathClass) {
		assertEquals(pathClass, PathClass.getSingleton(pathClass));
		assertEquals(pathClass, PathClass.fromString(pathClass.toString()));

		var list = pathClass.toList();
		assertEquals(pathClass.getBaseClass(), PathClass.fromString(list.get(0)));
		assertEquals(pathClass, PathClass.fromCollection(list));
		if (pathClass.isDerivedClass())
			assertEquals(pathClass.getParentClass(), PathClass.fromCollection(list.subList(0, list.size()-1)));
	}

	private static int countColons(String s) {
		return s.length() - s.replaceAll(":", "").length();
	}
	
}
