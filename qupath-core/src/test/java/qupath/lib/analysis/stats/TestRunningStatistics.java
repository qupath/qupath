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