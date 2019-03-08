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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.analysis.algorithms.SimpleImage;

/**
 * Static methods for computing statistics from images, with or without a corresponding labeled image.
 * 
 * @author Pete Bankhead
 *
 */
public class StatisticsHelper {
	
	public static RunningStatistics computeRunningStatistics(SimpleImage img) {
		RunningStatistics stats = new RunningStatistics();
		updateRunningStatistics(stats, img);
		return stats;
	}
	
	
	public static void updateRunningStatistics(RunningStatistics stats, SimpleImage img) {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				stats.addValue(img.getValue(x, y));
			}			
		}
	}
	
	
//	public static RunningStatistics computeRunningStatistics(float[] pxIntensities, byte[] pxMask, int width, Rectangle bounds) {
//		RunningStatistics stats = new RunningStatistics();
//		for (int i = 0; i < pxMask.length; i++) {
//			if (pxMask[i] == 0)
//				continue;
//			// Compute the image index
//			int x = i % bounds.width + bounds.x;
//			int y = i % bounds.width + bounds.y;
//			// Add the value
//			stats.addValue(pxIntensities[y * width + x]);
//		}
//		return stats;
//	}
	
	
	/**
	 * Create a list of n (empty) RunningStatistics objects
	 * @param n
	 * @return
	 */
	public static List<RunningStatistics> createRunningStatisticsList(int n) {
		List<RunningStatistics> stats = new ArrayList<>();
		for (int i = 0; i < n; i++)
			stats.add(new RunningStatistics());
		return stats;
	}
	
	
	public static RunningStatistics computeRunningStatistics(double[] values) {
		RunningStatistics stats = new RunningStatistics();
		for (double v : values) {
			stats.addValue(v);
		}
		return stats;
	}
	
	public static RunningStatistics computeRunningStatistics(float[] values) {
		RunningStatistics stats = new RunningStatistics();
		for (double v : values) {
			stats.addValue(v);
		}
		return stats;
	}
	
	
	
	/**
	 * This should (but isn't well tested!) 
	 * 
	 * TODO: Test running statistics!
	 * 
	 * @param img
	 * @param imgLabels
	 * @param statsList
	 */
	public static void computeRunningStatistics(SimpleImage img, SimpleImage imgLabels, List<RunningStatistics> statsList) {
		float lastLabel = Float.NaN;
		int nLabels = statsList.size();
		RunningStatistics stats = null;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				float label = imgLabels.getValue(x, y);
				if (label == 0 || label > nLabels)
					continue;
				// Get a new statistics object if necessary
				if (label != lastLabel) {
					stats = statsList.get((int)label-1);
					lastLabel = label;
				}
				// Add the value
				stats.addValue(img.getValue(x, y));
			}			
		}
	}
	
	
	/**
	 * Create a map between labels and RunningStatistics.
	 * 
	 * @param img
	 * @param imgLabels
	 */
	public static Map<Float, RunningStatistics> computeRunningStatisticsMap(SimpleImage img, SimpleImage imgLabels) {
		float lastLabel = Float.NaN;
		Map<Float, RunningStatistics> map = new HashMap<>();
		RunningStatistics stats = null;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				float label = imgLabels.getValue(x, y);
				if (label == 0)
					continue;
				// Get a new statistics object if necessary
				if (label != lastLabel) {
					Float fLabel = Float.valueOf(label);
					stats = map.get(fLabel);
					if (stats == null) {
						stats = new RunningStatistics();
						map.put(fLabel, stats);
					}
					lastLabel = label;
				}
				// Add the value
				stats.addValue(img.getValue(x, y));
			}			
		}
		return map;
	}
	
	
	
	
	
	/**
	 * Determine thresholds for dividing an array of double values into quartiles.
	 * 
	 * NaNs are effectively removed first, however the input array is left unchanged so
	 * there is no need to duplicate it first (i.e. it is duplicated within this method anyway).
	 * 
	 * Depending upon the number of non-NaN values in the input array, linear interpolation may be used 
	 * to obtain the split points.
	 * 
	 * @param scores
	 * @return Array containing three values, corresponding to the split points.
	 */
	public static double[] getQuartiles(double[] scores) {
		scores = Arrays.copyOf(scores, scores.length);
		Arrays.sort(scores);
		int nScores = scores.length;
		while (nScores > 0) {
			if (Double.isNaN(scores[nScores-1])) {
				nScores--;
			} else
				break;
		}
		if (nScores <= 0)
			return new double[]{Double.NaN, Double.NaN, Double.NaN};
		
		double v1 = getInterpolatedSortedValue(scores, (double)(nScores - 1) / 4);
		double v2 = getInterpolatedSortedValue(scores, (double)(nScores - 1) / 2);
		double v3 = getInterpolatedSortedValue(scores, (double)(nScores - 1) * 3 / 4);
		return new double[]{v1, v2, v3};
//		return new double[]{scores[indFinal/4], scores[indFinal/2], scores[indFinal*3/4]};
	}
	
	
	/**
	 * Helper function for getting value from a sorted array using linear interpolation to deal with
	 * a non-integer index.
	 */
	private static double getInterpolatedSortedValue(final double[] values, final double ind) {
		if (ind > values.length-1)
			return Double.NaN;
		int flooredInd = (int)ind;
		double rem = ind - flooredInd;
		if (rem == 0)
			return values[flooredInd];
		return values[flooredInd] + rem * (values[flooredInd+1] - values[flooredInd]);
	}
	
	
	/**
	 * Determine thresholds for dividing an array of double values into tertiles.
	 * 
	 * NaNs are effectively removed first, however the input array is left unchanged so
	 * there is no need to duplicate it first (i.e. it is duplicated within this method anyway).
	 * 
	 * Depending upon the number of non-NaN values in the input array, linear interpolation may be used 
	 * to obtain the split points.
	 * 
	 * @param scores
	 * @return Array containing two values, corresponding to the split points.
	 */
	public static double[] getTertiles(double[] scores) {
		scores = Arrays.copyOf(scores, scores.length);
		Arrays.sort(scores);
		int nScores = scores.length;
		while (nScores > 0) {
			if (Double.isNaN(scores[nScores-1])) {
				nScores--;
			} else
				break;
		}
		if (nScores <= 0)
			return new double[]{Double.NaN, Double.NaN};
		
		double v1 = getInterpolatedSortedValue(scores, (double)(nScores - 1) / 3);
		double v2 = getInterpolatedSortedValue(scores, (double)(nScores - 1)*2 / 3);
		return new double[]{v1, v2};
//		return new double[]{scores[indFinal/3], scores[indFinal*2/3]};
	}

}
