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
 * <p>
 * TODO: Document int check - if all values are integers, the bin size cannot be &lt; 1.
 * 
 * @author Pete Bankhead
 *
 */
public class Histogram { // implements Serializable {
	
	 // TODO: Check if this needs to be Serializable... if so, then it may fail on the RunningStatistics not being serializable
//	private static final long serialVersionUID = 1L;
	
	private double[] edges;
	private long[] counts;
	private long maxCount;
	private double edgeMin, edgeMax;
	private long countSum;
	private boolean isInteger = true;
	
	private RunningStatistics stats;
	
	/**
	 * Get the minimum edge of the histogram.
	 * @return
	 */
	public double getEdgeMin() {
		return edgeMin;
	}

	/**
	 * Get the maximum edge of the histogram.
	 * @return
	 */
	public double getEdgeMax() {
		return edgeMax;
	}
	
	/**
	 * Get the histogram edge range, defined as the maximum edge - the minimum edge.
	 * @return
	 */
	public double getEdgeRange() {
		return getEdgeMax() - getEdgeMin();
	}

	/**
	 * Get the lower edge for a specified bin.
	 * @param ind index of the bin
	 * @return
	 */
	public double getBinLeftEdge(int ind) {
		return edges[ind];
	}

	/**
	 * Get the upper edge for a specified bin.
	 * @param ind index of the bin
	 * @return
	 */
	public double getBinRightEdge(int ind) {
		return edges[ind+1];
	}
	
	/**
	 * Get the width of a bin, which is the difference between its upper and lower edges.
	 * @param ind index of the bin
	 * @return
	 */
	public double getBinWidth(int ind) {
		return getBinRightEdge(ind) - getBinLeftEdge(ind);
	}

	/**
	 * Get the histogram counts for the specified bin.
	 * @param ind index of the bin
	 * @return
	 */
	public long getCountsForBin(int ind) {
		return counts[ind];
	}
	
	/**
	 * Get the normalized histogram count for the specified bin.
	 * <p>
	 * This is the count for the bin divided by the sum of all counts.
	 * @param ind
	 * @return
	 */
	public double getNormalizedCountsForBin(int ind) {
		return (double)getCountsForBin(ind) / countSum;
	}
	
	/**
	 * Check if the histogram has been generated from integer values only.
	 * @return
	 */
	public boolean isInteger() {
		return isInteger;
	}
	
	/**
	 * Get the minimum of all the values being histogrammed.
	 * @return
	 */
	public double getMinValue() {
		return stats != null ? stats.getMin() : Double.NaN;
	}

	/**
	 * Get the maximum of all the values being histogrammed.
	 * @return
	 */
	public double getMaxValue() {
		return stats != null ? stats.getMax() : Double.NaN;
	}

	/**
	 * Get the mean of all the values being histogrammed.
	 * @return
	 */
	public double getMeanValue() {
		return stats != null ? stats.getMean() : Double.NaN;
	}

	/**
	 * Get the variance of all the values being histogrammed.
	 * @return
	 */
	public double getVariance() {
		return stats != null ? stats.getVariance() : Double.NaN;
	}

	/**
	 * Get the standard deviation of all the values being histogrammed.
	 * @return
	 */
	public double getStdDev() {
		return stats != null ? stats.getStdDev() : Double.NaN;
	}
	
	/**
	 * Get the sum of all the values being histogrammed.
	 * @return
	 */
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

	/**
	 * Get the index of the bin that should contain the specified value.
	 * @return
	 */
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
	
	/**
	 * Get the highest count found for any bin.
	 * @return
	 * 
	 * {@link #getMaxNormalizedCount()}
	 */
	public long getMaxCount() {
		return maxCount;
	}
	
	/**
	 * Get the highest count found for any bin, divided by the total counts across the entire histogram.
	 * @return
	 * 
	 * {@link #getMaxCount()}
	 */
	public double getMaxNormalizedCount() {
		return (double)getMaxCount() / getCountSum();
	}

	/**
	 * Total number of histogram bins.
	 * @return
	 */
	public int nBins() {
		return counts.length;
	}
	
	/**
	 * Sum of all histogram counts.
	 * @return
	 */
	public long getCountSum() {
		return countSum;
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
	 * @param valuesArray the data values from which the histogram should be computed
	 * @param nBins number of histogram bins (will be number of edges - 1)
	 * @param minEdge the minimum (edge) value to include in the histogram, or Double.NaN (to use the data minimum)
	 * @param maxEdge the maximum (edge) value to include in the histogram, or Double.NaN (to use the data maximum)
	 */
	public Histogram(double[] valuesArray, int nBins, double minEdge, double maxEdge) {
		ArrayWrappers.ArrayWrapper values = ArrayWrappers.makeDoubleArrayWrapper(valuesArray);
		buildHistogram(values, nBins, minEdge, maxEdge);
	}

	/**
	 * Create a histogram from an array of values, optionally specifying the minimum &amp; maximum values to include.
	 * NaNs will be ignored from the histogram.
	 * 
	 * @param valuesArray the data values from which the histogram should be computed
	 * @param nBins number of histogram bins (will be number of edges - 1)
	 * @param minEdge the minimum (edge) value to include in the histogram, or Double.NaN (to use the data minimum)
	 * @param maxEdge the maximum (edge) value to include in the histogram, or Double.NaN (to use the data maximum)
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
	
	/**
	 * Create histogram from a double array, using a specified number of bins and the data min/max as the min/max edges.
	 * @param values
	 * @param nBins
	 */
	public Histogram(double[] values, int nBins) {
		this(values, nBins, Double.NaN, Double.NaN);
	}
	
	/**
	 * Create histogram from a float array, using a specified number of bins and the data min/max as the min/max edges.
	 * @param values
	 * @param nBins
	 */
	public Histogram(float[] values, int nBins) {
		this(values, nBins, Double.NaN, Double.NaN);
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
			if (maybeInteger && v != (int)v) { //v != Math.rint(v)) {
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
		this.counts = new long[nBins];
		
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
			long count = counts[bin] + 1;
			counts[bin] = count;
			if (count > maxCount)
				maxCount = count;
			countSum++;
		}
		
//		System.out.println("Time " + (counter++) + ": " + (System.currentTimeMillis() - t)); t = System.currentTimeMillis();

	}
	
	/**
	 * Extract a specific measurement for each PathObject in a collection, storing the result in an array.
	 * <p>
	 * The order of entries in the array will depend upon the iteration order of the collection (usually a list).
	 * 
	 * @param pathObjects
	 * @param measurementName
	 * @return
	 * 
	 * @see #makeMeasurementHistogram(Collection, String, int)
	 */
	public static double[] getMeasurementValues(final Collection<PathObject> pathObjects, final String measurementName) {
		double[] values = new double[pathObjects.size()];
		int ind = 0;
		for (PathObject pathObject : pathObjects) {
			values[ind] = pathObject.getMeasurementList().getMeasurementValue(measurementName);
			ind++;
		}
		return values;
	}
	
	/**
	 * Create a histogram depicting values of a specific measurement extracted from a collection of PathObjects.
	 * 
	 * @param pathObjects
	 * @param measurementName
	 * @param nBins
	 * @return
	 * 
	 * @see #getMeasurementValues(Collection, String)
	 */
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