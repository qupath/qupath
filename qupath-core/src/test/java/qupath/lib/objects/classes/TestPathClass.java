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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestPathClass {
	
	private static PathClass pathClass1;
	private static PathClass pathClass2;
	private static PathClass pathClass3;
	private static PathClass pathClass4;
	private static PathClass pathClass5;
	private static PathClass pathClass6;
	private static PathClass pathClass7;
	private static PathClass pathClass8;
	private static PathClass pathClass9;
	private static PathClass pathClass10;
	private static PathClass pathClass11;
	private static PathClass pathClass12;
	private static PathClass pathClass13;

	@BeforeAll
	public static void test_constructor() {
		// No arg constructor
		pathClass1 = new PathClass();
		assertEquals(null, pathClass1.getName());
		assertEquals(null, pathClass1.getParentClass());
		assertEquals(null, pathClass1.getColor());
		
		// Two-arg constructor		
		pathClass2 = new PathClass("", Color.RED.getRGB());
		assertEquals("", pathClass2.getName());
		assertEquals(null, pathClass2.getParentClass());
		assertEquals(Color.RED.getRGB(), pathClass2.getColor());
		
		pathClass3 = new PathClass("test", Color.BLUE.getRGB());
		assertEquals("test", pathClass3.getName());
		assertEquals(null, pathClass3.getParentClass());
		assertEquals(Color.BLUE.getRGB(), pathClass3.getColor());		

		pathClass4 = new PathClass("test", null);
		assertEquals("test", pathClass4.getName());
		assertEquals(null, pathClass4.getParentClass());
		assertNotEquals(null, pathClass4.getColor());	// Should be default
		
		pathClass5 = new PathClass(null, Color.BLUE.getRGB());
		assertEquals(null, pathClass5.getName());
		assertEquals(null, pathClass5.getParentClass());
		assertEquals(Color.BLUE.getRGB(), pathClass5.getColor());
		
		pathClass6 = new PathClass(null, null);
		assertEquals(null, pathClass6.getName());
		assertEquals(null, pathClass6.getParentClass());
		assertNotEquals(null, pathClass6.getColor());	// Should be default
		
		// Three-arg constructor		
		pathClass7 = new PathClass(null, "", Color.RED.getRGB());
		assertEquals("", pathClass7.getName());
		assertEquals(null, pathClass7.getParentClass());
		assertEquals(Color.RED.getRGB(), pathClass7.getColor());	
		
		pathClass8 = new PathClass(pathClass1, "", Color.BLUE.getRGB());
		assertEquals("", pathClass8.getName());
		assertEquals(pathClass1, pathClass8.getParentClass());
		assertEquals(Color.BLUE.getRGB(), pathClass8.getColor());
		
		pathClass9 = new PathClass(pathClass1, "test", Color.GREEN.getRGB());
		assertEquals("test", pathClass9.getName());
		assertEquals(pathClass1, pathClass9.getParentClass());
		assertEquals(Color.GREEN.getRGB(), pathClass9.getColor());
		
		pathClass10 = new PathClass(pathClass1, "", null);
		assertEquals("", pathClass10.getName());
		assertEquals(pathClass1, pathClass10.getParentClass());
		assertNotEquals(null, pathClass10.getColor());	// Should be default
		
		pathClass11 = new PathClass(null, null, null);
		assertEquals(null, pathClass11.getName());
		assertEquals(null, pathClass11.getParentClass());
		assertNotEquals(null, pathClass11.getColor());	// Should be default
		
		pathClass12 = new PathClass(pathClass9, "child", Color.CYAN.getRGB());
		assertEquals("child", pathClass12.getName());
		assertEquals(pathClass9, pathClass12.getParentClass());
		assertEquals(Color.CYAN.getRGB(), pathClass12.getColor());

		pathClass13 = new PathClass(pathClass7, "badChild", Color.CYAN.getRGB());
		assertEquals("badChild", pathClass13.getName());
		assertEquals(pathClass7, pathClass13.getParentClass());
		assertEquals(Color.CYAN.getRGB(), pathClass13.getColor());
		
		Assertions.assertThrows(IllegalArgumentException.class, () -> new PathClass(pathClass1, null, Color.BLUE.getRGB()));
	}
	
	@Test
	public void test_isValid() {
		assertFalse(pathClass1.isValid());
		assertTrue(pathClass2.isValid());
		assertTrue(pathClass3.isValid());
		assertTrue(pathClass4.isValid());
		assertFalse(pathClass5.isValid());
		assertFalse(pathClass6.isValid());
		assertTrue(pathClass7.isValid());
		assertTrue(pathClass8.isValid());
		assertTrue(pathClass9.isValid());
		assertTrue(pathClass10.isValid());
		assertFalse(pathClass11.isValid());
		assertTrue(pathClass12.isValid());
		assertTrue(pathClass13.isValid());
	}
	
	@Test
	public void test_isDerivedClass() {
		assertFalse(pathClass1.isDerivedClass());
		assertFalse(pathClass2.isDerivedClass());
		assertFalse(pathClass3.isDerivedClass());
		assertFalse(pathClass4.isDerivedClass());
		assertFalse(pathClass5.isDerivedClass());
		assertFalse(pathClass6.isDerivedClass());
		assertFalse(pathClass7.isDerivedClass());
		assertTrue(pathClass8.isDerivedClass());
		assertTrue(pathClass9.isDerivedClass());
		assertTrue(pathClass10.isDerivedClass());
		assertFalse(pathClass11.isDerivedClass());
		assertTrue(pathClass12.isDerivedClass());
		assertTrue(pathClass13.isDerivedClass());
	}
	
	@Test
	public void test_isDerivedFrom() {
		// Same class
		assertTrue(pathClass1.isDerivedFrom(pathClass1));
		
		// No parent class
		assertFalse(pathClass2.isDerivedFrom(pathClass1));
		assertFalse(pathClass3.isDerivedFrom(pathClass1));
		assertFalse(pathClass4.isDerivedFrom(pathClass1));
		assertFalse(pathClass5.isDerivedFrom(pathClass1));
		assertFalse(pathClass6.isDerivedFrom(pathClass1));
		assertFalse(pathClass7.isDerivedFrom(pathClass1));
		
		// Parent class
		assertTrue(pathClass8.isDerivedFrom(pathClass1));
		assertFalse(pathClass8.isDerivedFrom(pathClass2));
		assertTrue(pathClass9.isDerivedFrom(pathClass1));
		assertFalse(pathClass9.isDerivedFrom(pathClass2));
		assertTrue(pathClass10.isDerivedFrom(pathClass1));
		assertFalse(pathClass10.isDerivedFrom(pathClass2));
		assertFalse(pathClass11.isDerivedFrom(pathClass1));
		
		// '2' parent classes
		assertTrue(pathClass12.isDerivedFrom(pathClass9));	// Parent
		assertTrue(pathClass12.isDerivedFrom(pathClass1));	// Parent of parent
		assertFalse(pathClass12.isDerivedFrom(pathClass2));
		
		assertTrue(pathClass13.isDerivedFrom(pathClass7));	// Parent
		assertFalse(pathClass13.isDerivedFrom(pathClass2));
		
		// Check ancestor
		assertFalse(pathClass9.isDerivedFrom(pathClass12));
		assertFalse(pathClass7.isDerivedFrom(pathClass13));
	}
	
	@Test
	public void test_isAncestorOf() {
		// Same class
		assertTrue(pathClass1.isAncestorOf(pathClass1));
		
		// No ancestor 
		assertFalse(pathClass1.isAncestorOf(pathClass2));
		assertFalse(pathClass1.isAncestorOf(pathClass3));
		assertFalse(pathClass1.isAncestorOf(pathClass4));
		assertFalse(pathClass1.isAncestorOf(pathClass5));
		assertFalse(pathClass1.isAncestorOf(pathClass6));
		assertFalse(pathClass1.isAncestorOf(pathClass7));
		
		// One ancestor
		assertTrue(pathClass1.isAncestorOf(pathClass8));
		assertFalse(pathClass2.isAncestorOf(pathClass8));
		assertTrue(pathClass1.isAncestorOf(pathClass9));
		assertFalse(pathClass2.isAncestorOf(pathClass9));
		assertTrue(pathClass1.isAncestorOf(pathClass10));
		assertFalse(pathClass2.isAncestorOf(pathClass10));
		assertFalse(pathClass1.isAncestorOf(pathClass11));
		assertFalse(pathClass2.isAncestorOf(pathClass11));
		
		// 2 ancestors
		assertTrue(pathClass9.isAncestorOf(pathClass12));
		assertTrue(pathClass1.isAncestorOf(pathClass12));
		
		assertTrue(pathClass7.isAncestorOf(pathClass13));
		assertFalse(pathClass2.isAncestorOf(pathClass13));
	}
	
	@Test
	public void test_getBaseClass() {
		assertEquals(pathClass1, pathClass1.getBaseClass());
		assertEquals(pathClass2, pathClass2.getBaseClass());
		assertEquals(pathClass3, pathClass3.getBaseClass());
		assertEquals(pathClass4, pathClass4.getBaseClass());
		assertEquals(pathClass5, pathClass5.getBaseClass());
		assertEquals(pathClass6, pathClass6.getBaseClass());
		assertEquals(pathClass7, pathClass7.getBaseClass());
		assertEquals(pathClass1, pathClass8.getBaseClass());
		assertEquals(pathClass1, pathClass9.getBaseClass());
		assertEquals(pathClass1, pathClass10.getBaseClass());
		assertEquals(pathClass11, pathClass11.getBaseClass());
		assertEquals(pathClass1, pathClass12.getBaseClass());
		assertEquals(pathClass7, pathClass13.getBaseClass());
	}
	
	@Test
	public void test_setColor() {
		var tempPathClass1 = new PathClass();
		var tempPathClass2 = new PathClass("colorTest", null);
		var tempPathClass3 = new PathClass("colorTest", Color.GREEN.getRGB());
		var tempPathClass4 = new PathClass("colorTest", Color.RED.getRGB());
		var tempPathClass5 = new PathClass("colorTest", Color.RED.getRGB());
		
		tempPathClass1.setColor(Color.GREEN.getRGB());
		tempPathClass2.setColor(Color.GREEN.getRGB());
		tempPathClass3.setColor(Color.GREEN.getRGB());
		tempPathClass4.setColor(Color.GREEN.getRGB());
		tempPathClass5.setColor(null);
		
		assertEquals(Color.GREEN.getRGB(), tempPathClass1.getColor());
		assertEquals(Color.GREEN.getRGB(), tempPathClass2.getColor());
		assertEquals(Color.GREEN.getRGB(), tempPathClass3.getColor());
		assertEquals(Color.GREEN.getRGB(), tempPathClass4.getColor());
		assertEquals(null, tempPathClass5.getColor());
	}
	
	@Test
	public void test_derivedClassToString() {
		assertEquals("Unclassified: child", PathClass.derivedClassToString(pathClass1, "child"));
		assertEquals(": child", PathClass.derivedClassToString(pathClass2, "child"));
		assertEquals("test: child", PathClass.derivedClassToString(pathClass3, "child"));
		assertEquals("test: child", PathClass.derivedClassToString(pathClass4, "child"));
		assertEquals("Unclassified: child", PathClass.derivedClassToString(pathClass5, "child"));
		assertEquals("Unclassified: child", PathClass.derivedClassToString(pathClass6, "child"));
		assertEquals(": child", PathClass.derivedClassToString(pathClass7, "child"));
		assertEquals("Unclassified: : child", PathClass.derivedClassToString(pathClass8, "child"));
		assertEquals("Unclassified: test: child", PathClass.derivedClassToString(pathClass9, "child"));
		assertEquals("Unclassified: : child", PathClass.derivedClassToString(pathClass10, "child"));
		assertEquals("Unclassified: child", PathClass.derivedClassToString(pathClass11, "child"));
		assertEquals("Unclassified: test: child: child", PathClass.derivedClassToString(pathClass12, "child"));
		assertEquals(": badChild: child", PathClass.derivedClassToString(pathClass13, "child"));

		assertEquals("child", PathClass.derivedClassToString(null, "child"));
		assertEquals(null, PathClass.derivedClassToString(null, null));
		assertEquals("Unclassified: null", PathClass.derivedClassToString(pathClass1, null));
	}
	
	@Test
	public void test_equality() {
		// Test to assert that the compareTo() method works as intended (see javadoc for the method)
		assertNotEquals(0, new PathClass("Tumor: Positive", null).compareTo(new PathClass("Stroma: Positive", null)));
	}
}
