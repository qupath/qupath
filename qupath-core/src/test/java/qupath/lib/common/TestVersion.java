/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestVersion {

	@Test
	public void test() {
		// Check equals
		assertEquals(Version.parse("1.2.3"), Version.parse("1.2.3"));
		
		// Check 'v' is ignored
		assertEquals(Version.parse("v1.2.3"), Version.parse("1.2.3"));
		
		// Check for exceptions
		assertThrows(IllegalArgumentException.class, () -> Version.parse("anything"));
		assertThrows(IllegalArgumentException.class, () -> Version.parse("0.1.2.3"));
		assertThrows(IllegalArgumentException.class, () -> Version.parse("0-1-2"));
		assertThrows(IllegalArgumentException.class, () -> Version.parse("0.1.2-Extra bit is unstructured"));

		// Check additional parts are sanitized
		assertNotEquals(Version.parse("1.2.3-beta"), Version.parse("1.2.3-beta.SNAPSHOT"));
		assertEquals(Version.parse("1.2.3-beta-SNAPSHOT"), Version.parse("1.2.3-beta.SNAPSHOT"));
		assertEquals(Version.parse("1.2.3--beta--SNAPSHOT"), Version.parse("1.2.3-.beta..SNAPSHOT"));
		
		// Check ordering
		assertNotEquals(Version.parse("1.2.3"), Version.parse("1.2.2"));
		assertTrue(Version.parse("v1.2.3").compareTo(Version.parse("1.2.3")) == 0);
		assertTrue(Version.parse("1.2.3").compareTo(Version.parse("1.2.2")) > 0);
		assertTrue(Version.parse("1.2.1").compareTo(Version.parse("1.2.2")) < 0);
		assertTrue(Version.parse("1.2.1").compareTo(Version.parse("1.2.2")) < 0);
		
		var orderedList = Arrays.asList(
				Version.parse("0.0.1-alpha"),
				Version.parse("0.0.1"),
				Version.parse("0.1.0"),
				Version.parse("0.1.5-m1"),
				Version.parse("0.1.5-m2"),
				Version.parse("0.1.5-m3-SNAPSHOT"),
				Version.parse("0.1.5-m3"),
				Version.parse("0.1.5-m30"),
				Version.parse("0.1.5-rc1"),
				Version.parse("0.1.5-rc2"),
				Version.parse("0.1.5-rc10"),
				Version.parse("0.1.5"),
				Version.parse("0.1.9"),
				Version.parse("0.2.0-SNAPSHOT"),
				Version.parse("0.2.0"),
				Version.parse("1.0.0"),
				Version.parse("1.0.2")
				);
		var shuffledList = new ArrayList<>(orderedList);
		assertEquals(orderedList, shuffledList);
		var rand = new Random(11L);
		for (int i = 0; i < 5; i++) {
			Collections.shuffle(shuffledList, rand);
			assertNotEquals(orderedList, shuffledList);
		}
		Collections.sort(shuffledList);
		assertEquals(orderedList, shuffledList);
	}
	
	
	@Test
	public void testComparators() {
		
		Version versionBase = Version.parse("1.2.3-m1");
		
		for (int major = 0; major < 5; major++) {
			for (int minor = 0; minor < 5; minor++) {
				for (int patch = 0; patch < 5; patch++) {
					
					Version version = Version.parse(String.format("%d.%d.%d", major, minor, patch));
					
					int cmpMajor = Version.COMPARATOR_MAJOR.compare(version, versionBase);
					int cmpMajorMinor = Version.COMPARATOR_MAJOR_MINOR.compare(version, versionBase);
					int cmpMajorMinorPatch = Version.COMPARATOR_MAJOR_MINOR_PATCH.compare(version, versionBase);
					int cmpFull = Version.COMPARATOR_FULL.compare(version, versionBase);
					
					if (major > 1) {
						assertEquals(1, cmpMajor);
						assertEquals(1, cmpMajorMinor);
						assertEquals(1, cmpMajorMinorPatch);
						assertEquals(1, cmpFull);
					} else if (major < 1) {
						assertEquals(-1, cmpMajor);
						assertEquals(-1, cmpMajorMinor);
						assertEquals(-1, cmpMajorMinorPatch);
						assertEquals(-1, cmpFull);
					} else {
						assertEquals(0, cmpMajor);
						if (minor > 2) {
							assertEquals(1, cmpMajorMinor);							
							assertEquals(1, cmpMajorMinorPatch);
							assertEquals(1, cmpFull);
						} else if (minor < 2) {
							assertEquals(-1, cmpMajorMinor);							
							assertEquals(-1, cmpMajorMinorPatch);
							assertEquals(-1, cmpFull);							
						} else {
							assertEquals(0, cmpMajorMinor);							
							if (patch > 3) {
								assertEquals(1, cmpMajorMinorPatch);
								assertEquals(1, cmpFull);					
							} else if (patch < 3) {
								assertEquals(-1, cmpMajorMinorPatch);
								assertEquals(-1, cmpFull);									
							} else {
								assertEquals(0, cmpMajorMinorPatch);
								// No suffix should be considered 'higher'
								assertEquals(1, cmpFull);
							}
						}
					}
					
					
				}				
			}
		}
				
		
	}
	

}