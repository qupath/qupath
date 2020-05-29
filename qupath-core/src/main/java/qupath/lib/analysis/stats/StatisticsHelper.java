/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.analysis.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import qupath.lib.analysis.images.SimpleImage;

/**
 * Static methods for computing statistics from images, with or without a corresponding labeled image.
 * 
 * @author Pete Bankhead
 *
 */
public class StatisticsHelper {
	
	/**
	 * Compute running statistics using all pixels from a SimpleImage.
	 * @param img
	 * @return
	 */
	public static RunningStatistics computeRunningStatistics(SimpleImage img) {
		RunningStatistics stats = new RunningStatistics();
		updateRunningStatistics(stats, img);
		return stats;
	}
	
	
	/**
	 * Add all pixels from a SimpleImage to an existing RunningStatistics object.
	 * 
	 * @param stats 
	 * @param img
	 */
	public static void updateRunningStatistics(RunningStatistics stats, SimpleImage img) {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				stats.addValue(img.getValue(x, y));
			}			
		}
	}
	
	/**
	 * Create a list of n (empty) RunningStatistics objects.
	 * 
	 * @param n
	 * @return
	 */
	public static List<RunningStatistics> createRunningStatisticsList(int n) {
		List<RunningStatistics> stats = new ArrayList<>();
		for (int i = 0; i < n; i++)
			stats.add(new RunningStatistics());
		return stats;
	}
	
	
	/**
	 * Create a RunningStatistics object using all the values from a specified array.
	 * 
	 * @param values
	 * @return
	 */
	public static RunningStatistics computeRunningStatistics(double[] values) {
		RunningStatistics stats = new RunningStatistics();
		for (double v : values) {
			stats.addValue(v);
		}
		return stats;
	}
	
	
	/**
	 * Calculate RunningStatistics for each label &gt; 0 in an image, up to a maximum of {@code statsList.size()}.
	 * <p>
	 * The statistics for pixels in {@code img} corresponding to integer value {@code label} in {@code imgLabels} 
	 * are stored within {@code statsList.get(label-1)}.
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
	 * Determine thresholds for dividing an array of double values into quartiles.
	 * <p>
	 * NaNs are effectively removed first, however the input array is left unchanged so
	 * there is no need to duplicate it first (i.e. it is duplicated within this method anyway).
	 * <p>
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
	 * <p>
	 * NaNs are effectively removed first, however the input array is left unchanged so
	 * there is no need to duplicate it in advance (i.e. it is duplicated within this method anyway).
	 * <p>
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
