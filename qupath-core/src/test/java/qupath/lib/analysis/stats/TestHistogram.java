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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestHistogram {
	
	// Different runs of the tests might yield different results
	private final int[] seeds = new int[] {5, 15, 42, 46, 59, 73, 99, 100};
	
	/**
	 * Test all types of histograms. Even though in this test all the backing arrays 
	 * (and lists) are always a double array, to avoid repetition 3 times. Anyway, all the 
	 * {@code Histogram} constructors call the <b>private</b> {@code Histogram(ArrayWrapper)} method, 
	 * which is not visible from this test file.
	 */
	@Test
	public void test_allHistograms() {
		for (int seed: seeds) {
			Random random = new Random(seed);
			
			/** ---- Check double histogram --- **/
			// Create data array of length 100 with double values between [0.0-100.0]
			double[] data = new double[100];
			for (int i = 0; i < 100; i++) {
				data[i] = random.nextDouble()*100;
			}
			
			// Replace two random values with NaN (missing value)
			int random1 = (int)(random.nextDouble()*100);

			// Create list out of double array, for simplicity with calculations
			List<Double> list = DoubleStream.of(data).boxed().collect(Collectors.toList());
			
			// Remove the NaN values, so they aren't counted by the Collections 'stats' methods
			data[random1] = Double.NaN;
			list.remove(random1);
			
			// Run tests
			test_Histogram(data, list);
			
			
			/** ---- Check float histogram --- **/
			// Create data array of length 100 with float values between [0.0-100.0]
			double[] data2 = new double[100];
			for (int i = 0; i < 100; i++) {
				data2[i] = random.nextFloat()*100;
			}
			
			// Replace two random values with NaN (missing value)
			int random2 = (int)(random.nextFloat()*100);

			// Create list out of double array, for simplicity with calculations
			List<Double> list2 = DoubleStream.of(data2).boxed().collect(Collectors.toList());
			
			// Remove the NaN values, so they aren't counted by the Collections 'stats' methods
			data2[random2] = Double.NaN;
			list2.remove(random2);
			test_Histogram(data2, list2);
			
			/** ---- Check int histogram --- **/
			// Create data array of length 100 with int values between [0-100]
			double[] data3 = new double[100];
			for (int i = 0; i < 100; i++) {
				data3[i] = (int)(random.nextDouble()*100);
			}
			
			// Replace two random values with NaN (missing value)
			int random3 = (int)(random.nextDouble()*100);

			// Create list out of double array, for simplicity with calculations
			List<Double> list3 = DoubleStream.of(data3).boxed().collect(Collectors.toList());
			
			// Remove the NaN values, so they aren't counted by the Collections 'stats' methods
			data3[random3] = Double.NaN;
			list3.remove(random3);
			test_Histogram(data3, list3);
		}
	}

	public void test_Histogram(double[] data, List<Double> list) {
		
		// Create double histogram with 15 bins
		var nBins1 = 15;
		Histogram hist1 = new Histogram(data, nBins1);

		// Create double histogram with 10 bins, min-max = [0.0-100.0]
		var nBins2 = 10;
		Histogram hist2 = new Histogram(data, nBins2, 0.0, 100.0);
		
		// Check isInteger (ArraysWrappers) - always false in this case
		assertFalse(hist1.isInteger());
		assertFalse(hist2.isInteger());
		
		// Check nBins
		assertEquals(nBins1, hist1.nBins());
		assertEquals(nBins2, hist2.nBins());
		
		// Check min/max edges
		assertFalse(list.stream().anyMatch(e -> Double.isNaN(e)));
		assertEquals(Collections.min(list), hist1.getEdgeMin());
		assertEquals(Collections.max(list), hist1.getEdgeMax());
		assertEquals(0.0, hist2.getEdgeMin());
		assertEquals(100.0, hist2.getEdgeMax());
		
		// Check min/max values
		assertEquals(Collections.min(list), hist1.getMinValue());
		assertEquals(Collections.max(list), hist1.getMaxValue());
		assertEquals(Collections.min(list), hist2.getMinValue());
		assertEquals(Collections.max(list), hist2.getMaxValue());
		
		// Check edge range
		var edgeRange1 = Collections.max(list) - Collections.min(list);
		var edgeRange2 = 100.0 - 0.0;
		assertEquals(edgeRange1, hist1.getEdgeRange());
		assertEquals(edgeRange2, hist2.getEdgeRange());
		
		// Check bin width (all equal)
		var binWidth1 = edgeRange1/nBins1;
		var binWidth2 = edgeRange2/nBins2;
		for (int i = 0; i < nBins1; i++) {
			assertEquals(binWidth1, hist1.getBinWidth(i), 0.0002);
		}
		for (int i = 0; i < nBins2; i++) {
			assertEquals(binWidth2, hist2.getBinWidth(i), 0.0002);			
		}
		
		// Check bin edges
		for (int i = 0; i < nBins1; i++) {
			assertEquals(Collections.min(list) + (i*binWidth1), hist1.getBinLeftEdge(i), 0.0002);
			assertEquals(Collections.min(list) + ((i+1)*binWidth1), hist1.getBinRightEdge(i), 0.0002);
		}
		for (int i = 0; i < nBins2; i++) {
			assertEquals(0 + (i*binWidth2), hist2.getBinLeftEdge(i), 0.0002);
			assertEquals(0 + ((i+1)*binWidth2), hist2.getBinRightEdge(i), 0.0002);
		}
		
		// Check variance
		var dataWithoutNaNs = DoubleStream.of(data).boxed().filter(e -> !e.isNaN()).mapToDouble(e -> e).toArray();
		assertEquals(Math.pow(new StandardDeviation(true).evaluate(dataWithoutNaNs), 2), hist1.getVariance(), 0.0002);
		assertEquals(Math.pow(new StandardDeviation(true).evaluate(dataWithoutNaNs), 2), hist2.getVariance(), 0.0002);
		
		// Check standard deviation
		assertEquals(new StandardDeviation(true).evaluate(dataWithoutNaNs), hist1.getStdDev(), 0.0002);
		assertEquals(new StandardDeviation(true).evaluate(dataWithoutNaNs), hist2.getStdDev(), 0.0002);
		
		// Check nValues
		assertEquals(list.size(), hist1.nValues());
		assertEquals(list.size(), hist2.nValues());
		
		// Check missing values (NaNs)
		assertEquals(data.length - list.size(), hist1.nMissingValues());
		assertEquals(data.length - list.size(), hist2.nMissingValues());
		
		// Check count sum
		assertEquals(list.size(), hist1.getCountSum());
		assertEquals(list.size(), hist2.getCountSum());
		
		// Check count for each bin
		Map<Integer, List<Double>> manualCount1 = new HashMap<>();	// Manually gathers all values for each bin
		for (int i = 0; i < nBins1; i++) {
			final int ii = i;
			// Awkward because of precision in bin width (nBins * binWidth ~ maxValue)
			manualCount1.put(i, list.stream()
					.filter(value -> {
						if (ii == nBins1-1)
							return value >= Collections.min(list) + ii*binWidth1;
						else
							return value >= Collections.min(list) + ii*binWidth1 && value < Collections.min(list) + ii*binWidth1 + binWidth1;
					}).collect(Collectors.toList()));
		}
		
		Map<Integer, List<Double>> countPerBin1 = new HashMap<>();	// 'getBinIndexForValue(double)' check
		for (int i = 0; i < list.size(); i++) {
			var bin = hist1.getBinIndexForValue(list.get(i));
			if (countPerBin1.containsKey(bin))
				countPerBin1.get(bin).add(list.get(i));
			else {
				countPerBin1.put(bin, new ArrayList<>());
				countPerBin1.get(bin).add(list.get(i));
			}
		}
		
		Map<Integer, List<Double>> countPerBin2 = new HashMap<>();	// 'getBinIndexForValue(double, double)' check
		for (int i = 0; i < list.size(); i++) {
			var bin = hist1.getBinIndexForValue(list.get(i));
			if (countPerBin2.containsKey(bin))
				countPerBin2.get(bin).add(list.get(i));
			else {
				countPerBin2.put(bin, new ArrayList<>());
				countPerBin2.get(bin).add(list.get(i));
			}
		}

		// Check consistency between methods
		for (int i = 0; i < nBins1; i++) {
			assertEquals(manualCount1.get(i), countPerBin1.get(i));
			assertEquals(manualCount1.get(i), countPerBin2.get(i));
			assertEquals(manualCount1.get(i).size(), hist1.getCountsForBin(i));
			assertEquals(countPerBin1.get(i).size(), hist1.getCountsForBin(i));
			assertEquals(countPerBin2.get(i).size(), hist1.getCountsForBin(i));
			assertEquals((double)manualCount1.get(i).size() / list.size(), hist1.getNormalizedCountsForBin(i));
			assertEquals((double)countPerBin1.get(i).size() / list.size(), hist1.getNormalizedCountsForBin(i));
			assertEquals((double)countPerBin2.get(i).size() / list.size(), hist1.getNormalizedCountsForBin(i));			
		}

		// Check sum & max count
		int countPerBin1Sum = countPerBin1.values().stream().mapToInt(e -> e.size()).sum();
		int countPerBin2Sum = countPerBin2.values().stream().mapToInt(e -> e.size()).sum();
		int countPerBin1Max = countPerBin1.values().stream().mapToInt(e -> e.size()).max().getAsInt();
		int countPerBin2Max = countPerBin2.values().stream().mapToInt(e -> e.size()).max().getAsInt();
		int manualCount1Sum = manualCount1.values().stream().mapToInt(e -> e.size()).sum();
		int manualCount1Max = manualCount1.values().stream().mapToInt(e -> e.size()).max().getAsInt();
		assertEquals(countPerBin1Sum, hist1.getCountSum());
		assertEquals(countPerBin2Sum, hist1.getCountSum());
		assertEquals(manualCount1Sum, hist1.getCountSum());
		assertEquals(countPerBin1Max, hist1.getMaxCount());
		assertEquals(countPerBin2Max, hist1.getMaxCount());
		assertEquals(manualCount1Max, hist1.getMaxCount());
		assertEquals((double)countPerBin1Max / countPerBin1Sum, hist1.getMaxNormalizedCount());
		assertEquals((double)countPerBin2Max / countPerBin2Sum, hist1.getMaxNormalizedCount());
		assertEquals((double)manualCount1Max / manualCount1Sum, hist1.getMaxNormalizedCount());
		
		Map<Integer, List<Double>> manualCount2 = new HashMap<>();	// Manually gathers all values for each bin
		for (int i = 0; i < nBins2; i++) {
			final int ii = i;
			// Awkward because of precision in bin width (nBins * binWidth ~ maxValue)			
			manualCount2.put(i, list.stream()
					.filter(value -> {
						if (ii == nBins2-1)
							return value >= 0 + ii*binWidth2;
						else
							return value >= 0 + ii*binWidth2 && value < 0 + ii*binWidth2 + binWidth2;
					}).collect(Collectors.toList()));
		}
		
		Map<Integer, List<Double>> countPerBin3 = new HashMap<>();	// 'getBinIndexForValue(double)' check
		for (int i = 0; i < list.size(); i++) {
			var bin = hist2.getBinIndexForValue(list.get(i));
			if (countPerBin3.containsKey(bin))
				countPerBin3.get(bin).add(list.get(i));
			else {
				countPerBin3.put(bin, new ArrayList<>());
				countPerBin3.get(bin).add(list.get(i));
			}
		}

		Map<Integer, List<Double>> countPerBin4 = new HashMap<>();	// 'getBinIndexForValue(double, double)' check
		for (int i = 0; i < list.size(); i++) {
			var bin = hist2.getBinIndexForValue(list.get(i));
			if (countPerBin4.containsKey(bin))
				countPerBin4.get(bin).add(list.get(i));
			else {
				countPerBin4.put(bin, new ArrayList<>());
				countPerBin4.get(bin).add(list.get(i));
			}
		}

		// Check consistency between methods
		for (int i = 0; i < nBins2; i++) {
			assertEquals(manualCount2.get(i), countPerBin3.get(i));
			assertEquals(manualCount2.get(i), countPerBin4.get(i));
			assertEquals(manualCount2.get(i).size(), hist2.getCountsForBin(i));
			assertEquals(countPerBin3.get(i).size(), hist2.getCountsForBin(i));
			assertEquals(countPerBin4.get(i).size(), hist2.getCountsForBin(i));
			assertEquals((double)manualCount2.get(i).size() / list.size(), hist2.getNormalizedCountsForBin(i));
			assertEquals((double)countPerBin3.get(i).size() / list.size(), hist2.getNormalizedCountsForBin(i));
			assertEquals((double)countPerBin4.get(i).size() / list.size(), hist2.getNormalizedCountsForBin(i));
		}
		
		// Check sum & max count (+ normalized)
		int countPerBin3Sum = countPerBin3.values().stream().mapToInt(e -> e.size()).sum();
		int countPerBin4Sum = countPerBin4.values().stream().mapToInt(e -> e.size()).sum();
		int countPerBin3Max = countPerBin3.values().stream().mapToInt(e -> e.size()).max().getAsInt();
		int countPerBin4Max = countPerBin4.values().stream().mapToInt(e -> e.size()).max().getAsInt();
		int manualCount2Sum = manualCount2.values().stream().mapToInt(e -> e.size()).sum();
		int manualCount2Max = manualCount2.values().stream().mapToInt(e -> e.size()).max().getAsInt();
		assertEquals(countPerBin3Sum, hist2.getCountSum());
		assertEquals(countPerBin4Sum, hist2.getCountSum());
		assertEquals(manualCount2Sum, hist2.getCountSum());
		assertEquals(countPerBin3Max, hist2.getMaxCount());
		assertEquals(countPerBin4Max, hist2.getMaxCount());
		assertEquals(manualCount2Max, hist2.getMaxCount());
		assertEquals((double)countPerBin3Max / countPerBin3Sum, hist2.getMaxNormalizedCount());
		assertEquals((double)countPerBin4Max / countPerBin4Sum, hist2.getMaxNormalizedCount());
		assertEquals((double)manualCount2Max / manualCount2Sum, hist2.getMaxNormalizedCount());
	}
}