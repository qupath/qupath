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

package qupath.lib.analysis.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestRunningStatistics {
	
	private static final RunningStatistics stats = new RunningStatistics();
	private static final List<Double> list = new ArrayList<>();
	
	@BeforeAll
	public static void test_addValues() {
		Random random = new Random();
		int size = random.nextInt(10_000);
		
		// Add some initial random values
		for (int i = 0; i < size; i++) {
			list.add(random.nextDouble());
		}
		
		// Insert NaN here
		list.add(Double.NaN);
		
		// Add other random values
		for (int i = 0; i < size; i++) {
			list.add(random.nextDouble());
		}
		
		// Insert another NaN here
		list.add(Double.NaN);
		
		// Add some last random values
		for (int i = 0; i < size; i++) {
			list.add(random.nextDouble());
		}
		
		
		// Add values to stats
		for (int i = 0; i < list.size(); i++) {
			stats.addValue(list.get(i));
		}
		
		// Check size does not count NaN values
		assertEquals(list.size() - 2, stats.size());
	}
	
	@Test
	public void test_numNaNs() {
		assertEquals(2, stats.getNumNaNs());
	}
	
	@Test
	public void test_metrics() {
		// Sum
		assertEquals(list.stream().filter(e -> !e.isNaN()).mapToDouble(e -> e).sum(), stats.getSum(), 0.0002);
		
		// Mean
		assertEquals(list.stream().filter(e -> !e.isNaN()).mapToDouble(e -> e).average().getAsDouble(), stats.getMean(), 0.0002);
		
		// Variance
		double[] array = list.stream().filter(e -> !e.isNaN()).mapToDouble(e -> e).toArray();	// Convert to array for apache common methods
		assertEquals(new Variance(true).evaluate(array), stats.getVariance(), 0.0002);
		
		// Standard deviation
		assertEquals(new StandardDeviation(true).evaluate(array), stats.getStdDev(), 0.0002);
		
		// Sort the array
		Arrays.sort(array);
		
		// Min
		assertEquals(array[0], stats.getMin());
		
		// Max
		assertEquals(array[array.length-1], stats.getMax());
		
		// Range
		assertEquals(array[array.length-1] - array[0], stats.getRange());
	}
}