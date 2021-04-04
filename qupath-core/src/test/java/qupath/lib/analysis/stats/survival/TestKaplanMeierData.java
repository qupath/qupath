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

package qupath.lib.analysis.stats.survival;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.DoubleStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestKaplanMeierData {
	private static KaplanMeierData km;
	private final static double[] times = new double[] {0.0, 5.0, 7.5, 15.0, 15.0, 17.5, 25.0, 37.5, 37.5, 40.0, 67.5, 78.0, 80.0};
	private final static boolean[] censored = new boolean[] {false, true, false, false, false, true, false, false, false, false, false, false};
	private final static int[] eventsAtTime = new int[] {0, 1, 0, 2, 2, 1, 0, 2, 2, 1, 1, 1, 1};
	private final static int[] atRisk = new int[] {12, 12, 11, 10, 10, 8, 7, 6, 6, 4, 3, 2, 1};
	private final static double[] stats = new double[times.length-2];
	
	@BeforeAll
	public static void init() {
		// Creating new object
		km = new KaplanMeierData("test");
		
		// Check isEmpty() & nEvents
		assertTrue(km.isEmpty());
		assertEquals(0, km.nEvents());
		
		// Check getMaxTime()
		assertEquals(-1, km.getMaxTime());
		
		// Add all events
		km.addEvent(5.0, false);
		km.addEvent(7.5, true);
		km.addEvent(15.0, false);
		km.addEvent(15.0, false);
		// Here will go the events created after
		km.addEvent(37.5, false);
		km.addEvent(37.5, false);
		km.addEvent(40.0, false);
		km.addEvent(67.5, false);
		km.addEvent(78.0, false);
		km.addEvent(80.0, false);
		
		// Create events
		var event1 = new KaplanMeierData.KaplanMeierEvent(17.5, false);
		var event2 = new KaplanMeierData.KaplanMeierEvent(25.0, true);
		
		// Check time method of KaplanMeierDataEvent object
		assertEquals(event1.getTimeToEvent(), 17.5);
		assertEquals(event2.getTimeToEvent(), 25.0);
		
		// Check isCensored method of KaplanMeierDataEvent object
		assertFalse(event1.isCensored());
		assertTrue(event2.isCensored());
		
		// Add events now (and they should find their way back to the middle of the events)
		km.addEvents(Arrays.asList(event1, event2));
		
		// Check nEvents()
		assertEquals(times.length - 1, km.nEvents());
		
		// Check nObserved
		assertEquals(10, km.nObserved());

		// Check nCensored
		assertEquals(2, km.nCensored());
		
		// Check isEmpty again
		assertFalse(km.isEmpty());
	}
	
	@Test
	public void test_kaplanMeierData() {
		// Check name
		assertEquals("test", km.getName());
		
		// Check all events
		var events = km.getEvents();
		assertEquals(times.length - 1, events.size());
		for (int i = 0; i < times.length - 1; i++) {
			assertEquals(times[i + 1], events.get(i).getTimeToEvent());
			assertEquals(censored[i], events.get(i).isCensored());
		}
		
		// Check getAllTimes()
		assertArrayEquals(DoubleStream.of(times).boxed().mapToDouble(e -> e).distinct().toArray(), km.getAllTimes());
		
		// Check getEventsAtTime() - exact & boundary values
		assertEquals(0.0, km.getEventsAtTime(Double.NEGATIVE_INFINITY));
		assertEquals(0.0, km.getEventsAtTime(Double.MIN_VALUE));
		assertEquals(0.0, km.getEventsAtTime(0.0));
		assertEquals(0.0, km.getEventsAtTime(Double.MAX_VALUE));
		assertEquals(0.0, km.getEventsAtTime(Double.POSITIVE_INFINITY));
		for (int i = 0; i < times.length - 1; i++) {
			assertEquals(eventsAtTime[i + 1], km.getEventsAtTime(times[i + 1]));
		}
		
		// Check getAtRisk()
		for (int i = 0; i < times.length; i++) {
			final double time = times[i];
			assertEquals(km.getEvents().size() - km.getEvents().stream().filter(e -> e.getTimeToEvent() < time).count(), km.getAtRisk(times[i]));
			assertEquals(atRisk[i], km.getAtRisk(times[i]));
		}
		
		// Compute survival values at each time
		int index = 0;
		double lastVal = 1.0;
		for (int i = 0; i < times.length-1; i++) {
			if (i > 0 && times[i] == times[i - 1]) {
				continue;
			}
			stats[index] = lastVal * (1 - (double)eventsAtTime[i]/atRisk[i]);
			lastVal = stats[index++];
		}
		
		// Check getStatistics()
		assertArrayEquals(stats, km.getStatistic(), 0.0002);
		
		// Check getMaxTime()
		assertEquals(times[times.length - 1], km.getMaxTime());
	}
}