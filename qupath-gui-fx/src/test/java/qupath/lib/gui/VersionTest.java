package qupath.lib.gui;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class VersionTest {

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

}
