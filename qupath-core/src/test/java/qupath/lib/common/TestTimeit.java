/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class TestTimeit {
	
	@Test
	public void test_timeit() {
		var timeit = new Timeit()
				.autoUnits()
				.start()
				.checkpointAndRun(() -> doSomething(10))
				.checkpointAndRun(() -> doSomething(20))
				.stop();
		
		assertNotNull(timeit.toString());
		
		assertThrows(UnsupportedOperationException.class, () -> timeit.start());
		assertThrows(UnsupportedOperationException.class, () -> timeit.stop());
		assertThrows(UnsupportedOperationException.class, () -> timeit.checkpoint());
		
		// Check that we get different strings for all the possible units
		var strings = Set.of(
				timeit.microseconds().toString(),
				timeit.nanoseconds().toString(),
				timeit.minutes().toString(),
				timeit.seconds().toString(),
				timeit.milliseconds().toString()
				);
		assertEquals(strings.size(), 5);
		
		// Check we get the same strings if we call the method twice
		var strings2 = Set.of(
				timeit.microseconds().toString(),
				timeit.nanoseconds().toString(),
				timeit.minutes().toString(),
				timeit.seconds().toString(),
				timeit.milliseconds().toString()
				);
		
		assertEquals(strings, strings2);
	}
	
	
	static void doSomething(long durationMillis) {
		try {
			Thread.sleep(durationMillis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}