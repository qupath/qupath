/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.analysis.stats;

//import java.io.Serializable;
import java.util.Collection;

import qupath.lib.objects.PathObject;


/**
 * Class for storing histogram data &amp; basic statistics.
 * 
 * See also HistogramPanel.
 * 
 * TODO: Document int check - if all values are integers, the bin size cannot be &lt; 1.
 * 
 * @author Pete Bankhead
 *
 */
public class Histogram { // implements Serializable {
	
	 // TODO: Check if this needs to be Serializable... if so, then it may fail on the RunningStatistics not being serializable
//	private static final long serialVersionUID = 1L;
	
	private double[] edges;
	private double[] counts;
	private double maxCount;
	private double edgeMin, edgeMax;
	private double countSum;
	private boolean normalizeCounts = false;
	private boolean isInteger = true;
	
	private RunningStatistics stats;
	
	public void setNormalizeCounts(boolean normalizeCounts) {
		this.normalizeCounts = normalizeCounts;
	}

	public boolean getNormalizeCounts() {
		return normalizeCounts;
	}

	public double getEdgeMin() {
		return edgeMin;
	}

	public double getEdgeMax() {
		return edgeMax;
	}
	
	public double getEdgeRange() {
		return getEdgeMax() - getEdgeMin();
	}

	public double getBinLeftEdge(int ind) {
		return edges[ind];
	}

	public double getBinRightEdge(int ind) {
		return edges[ind+1];
	}
	
	public double getBinWidth(int ind) {
		return getBinRightEdge(ind) - getBinLeftEdge(ind);
	}

	public double getCountsForBin(int ind) {
		return normalizeCounts ? counts[ind] / countSum : counts[ind];
	}
	
	public boolean isInteger() {
		return isInteger;
	}
	
	// Some statistics...
	public double getMinValue() {
		return stats != null ? stats.getMin() : Double.NaN;
	}

	public double getMaxValue() {
		return stats != null ? stats.getMax() : Double.NaN;
	}

	public double getMeanValue() {
		return stats != null ? stats.getMean() : Double.NaN;
	}

	public double getVariance() {
		return stats != null ? stats.getVariance() : Double.NaN;
	}

	public double getStdDev() {
		return stats != null ? stats.getStdDev() : Double.NaN;
	}
	
	public double getSum() {
		return stats != null ? stats.getSum() : Double.NaN;
	}
	
	/**
	 * Number of values represented in the histogram (i.e. the length of the input array).
	 * @return
	 */
	public long nValues() {
		return stats != null ? stats.size() : -1;
	}

	/**
	 * Number of NaNs in the input array.
	 * @return
	 */
	public long nMissingValues() {
		return stats != null ? stats.getNumNaNs() : 0;
	}

	
	
	public int getBinIndexForValue(double value) {
		// Return -1 if out of range
		if (value > edgeMax || value < edgeMin)
			return -1;
		int i = edges.length-2;
		while (i >= 0) {
			if (edges[i] <= value)
				return i;
			i--;
		}
		return i;
	}
	
	public double getMaxCount() {
		return normalizeCounts ? maxCount / countSum : maxCount;
	}
	
	public int nBins() {
		return counts.length;
	}
	
	public double getCountSum() {
		return countSum;
	}

//	public Histogram(double[] edges, double[] counts) {
//		this.edges = edges;
//		this.counts = counts;
//		maxCount = 0;
//		countSum = 0;
//		for (double c : counts) {
//			if (c > maxCount)
//				maxCount = c;
//			countSum += c;
//		}
//		edgeMin = edges[0];
//		edgeMax = edges[edges.length-1];
//	}
	
	
	public Histogram(double[] values, int nBins) {
		this(values, nBins, Double.NaN, Double.NaN);
	}
	
	public Histogram(float[] values, int nBins) {
		this(values, nBins, Double.NaN, Double.NaN);
	}
	
	
	@Override
	public String toString() {
		double count = getCountSum();
		if (count == (long)count)
			return String.format("Histogram: Min %.2f, Max %.2f, Total count: %d, N Bins %d", getEdgeMin(), getEdgeMax(), (long)count, nBins());
		else
			return String.format("Histogram: Min %.2f, Max %.2f, Total count: %.2f, N Bins %d", getEdgeMin(), getEdgeMax(), getCountSum(), nBins());
	}
	
	
	/**
	 * Create a histogram from an array of values, optionally specifying the minimum &amp; maximum values to include.
	 * NaNs will be ignored from the histogram.
	 * 
	 * @param valuesArray The data values from which the histogram should be computed
	 * @param nBins Number of histogram bins (will be number of edges - 1)
	 * @param minEdge The minimum (edge) value to include in the histogram, or Double.NaN (to use the data minimum)
	 * @param maxEdge The maximum (edge) value to include in the histogram, or Double.NaN (to use the data maximum)
	 */
	public Histogram(double[] valuesArray, int nBins, double minEdge, double maxEdge) {
		ArrayWrappers.ArrayWrapper values = ArrayWrappers.makeDoubleArrayWrapper(valuesArray);
		buildHistogram(values, nBins, minEdge, maxEdge);
	}

	/**
	 * Create a histogram from an array of values, optionally specifying the minimum &amp; maximum values to include.
	 * NaNs will be ignored from the histogram.
	 * 
	 * @param valuesArray The data values from which the histogram should be computed
	 * @param nBins Number of histogram bins (will be number of edges - 1)
	 * @param minEdge The minimum (edge) value to include in the histogram, or Double.NaN (to use the data minimum)
	 * @param maxEdge The maximum (edge) value to include in the histogram, or Double.NaN (to use the data maximum)
	 */
	public Histogram(float[] valuesArray, int nBins, double minEdge, double maxEdge) {
		ArrayWrappers.ArrayWrapper values = ArrayWrappers.makeFloatArrayWrapper(valuesArray);
		buildHistogram(values, nBins, minEdge, maxEdge);
	}
	
	/**
	 * Create a histogram from an array of values, optionally specifying the minimum &amp; maximum values to include.
	 * NaNs will be ignored from the histogram.
	 * 
	 * @param valuesArray The data values from which the histogram should be computed
	 * @param nBins Number of histogram bins (will be number of edges - 1)
	 * @param minEdge The minimum (edge) value to include in the histogram, or Double.NaN (to use the data minimum)
	 * @param maxEdge The maximum (edge) value to include in the histogram, or Double.NaN (to use the data maximum)
	 */
	public Histogram(int[] valuesArray, int nBins, double minEdge, double maxEdge) {
		ArrayWrappers.ArrayWrapper values = ArrayWrappers.makeIntArrayWrapper(valuesArray);
		buildHistogram(values, nBins, minEdge, maxEdge);
	}

	
	private void buildHistogram(final ArrayWrappers.ArrayWrapper values, int nBins, double minEdge, double maxEdge) {
		
//		long t = System.currentTimeMillis();
//		int counter = 0;
		
		// Compute running statistics as we iterate through the values
		// If we don't know for sure if we have integer values, perform a check as we go
//		System.out.println(values.getClass());
		isInteger = values.isIntegerWrapper();
		boolean maybeInteger = !isInteger;
		stats = new RunningStatistics();
		int n = values.size();
		for (int i = 0; i < n; i++) {
			double v = values.getDouble(i);
			stats.addValue(v);
			// Also check if we have integers only
			if (maybeInteger && v != Math.rint(v)) { //v != (int)v) {
				maybeInteger = false;
			}
		}
		if (!isInteger)
			isInteger = maybeInteger;
//		System.out.println("Time " + (counter++) + ": " + (System.currentTimeMillis() - t)); t = System.currentTimeMillis();
		
		// Set min/max values, if required
		if (Double.isNaN(minEdge))
			edgeMin = stats.getMin();
		else
			edgeMin = minEdge;
		if (Double.isNaN(maxEdge))
			edgeMax = stats.getMax();
		else
			edgeMax = maxEdge;
		
		
		// Compute the width of each bin
		double binWidth = (edgeMax - edgeMin)/nBins;
		
		// If we have integer values, don't set the bin width to be < 1
		if (!Double.isFinite(binWidth))
			nBins = 0;
		else if (binWidth < 1 && isInteger) {
//			boolean is8Bit = edgeMin == 0 && edgeMax == 255 && nBins == 256;
//			if (!is8Bit) {
			binWidth = 1;
			nBins = (int)(edgeMax - edgeMin + 1);
//			}
		}
		
		
		// Create the arrays
		this.edges = new double[nBins+1];
		this.counts = new double[nBins];
		
		if (nBins == 0)
			return;
		
		// Fill in edges
		for (int i = 0; i <= nBins; i++)
			edges[i] = edgeMin + i * binWidth;

//		System.out.println("Time " + (counter++) + ": " + (System.currentTimeMillis() - t)); t = System.currentTimeMillis();

		// Compute counts
		maxCount = 0;
		countSum = 0;
		for (int i = 0; i < n; i++) {
			double v = values.getDouble(i);
			// Skip NaNs, or out of range values
			if (Double.isNaN(v) || v < edgeMin || v > edgeMax)
				continue;
			int bin = (int)((v - edgeMin) / binWidth);
			if (bin >= counts.length)
				bin = counts.length - 1;
			double count = counts[bin] + 1;
			counts[bin] = count;
			if (count > maxCount)
				maxCount = count;
			countSum++;
		}
		
//		System.out.println("Time " + (counter++) + ": " + (System.currentTimeMillis() - t)); t = System.currentTimeMillis();

	}
	

	public static double[] getMeasurementValues(final Collection<PathObject> pathObjects, final String measurementName) {
		double[] values = new double[pathObjects.size()];
		int ind = 0;
		for (PathObject pathObject : pathObjects) {
			values[ind] = pathObject.getMeasurementList().getMeasurementValue(measurementName);
			ind++;
		}
		return values;
	}
	
	public static Histogram makeMeasurementHistogram(final Collection<PathObject> pathObjects, final String measurementName, final int nBins) {
		if (pathObjects.isEmpty()) {
			return null;
		}
		
		double[] values = getMeasurementValues(pathObjects, measurementName);
		Histogram histogram = new Histogram(values, nBins);
		if (histogram.getCountSum() > 0)
			return histogram;
		return null;
	}
	
	
}