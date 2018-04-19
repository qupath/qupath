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

package qupath.lib.gui.helpers;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.helpers.PathObjectColorToolsAwt;

/**
 * Helpers class that can be used to map an object's measurement to a color (packed RGB int).
 * By passing a collection of objects, the minimum and maximum of all the measurements are found
 * and these used to determine the lookup table scaling; alternative minimum and maximum values can also
 * be set to override these extrema.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementMapper {

	final private static Logger logger = LoggerFactory.getLogger(MeasurementMapper.class);

	private ColorMapper colorMapper = new PseudoColorMapper();

	// Data min & max values
	private double minValueData = 0;
	private double maxValueData = 1;

	// Display min & max values
	private double minValue = 0;
	private double maxValue = 1;

	private String measurement;
	private boolean isClassProbability = false;
	private boolean valid = false;
	private boolean excludeOutsideRange;

	public MeasurementMapper(String measurement, Collection<PathObject> pathObjects) {
		this.measurement = measurement;
		isClassProbability = measurement.toLowerCase().trim().equals("class probability");

		// Initialize max & min values
		minValueData = Double.POSITIVE_INFINITY;
		maxValueData = Double.NEGATIVE_INFINITY;
		for (PathObject pathObject : pathObjects) {
			double value = getUsefulValue(pathObject, Double.NaN);
			if (Double.isNaN(value) || Double.isInfinite(value))
				continue;
			if (value > maxValueData)
				maxValueData = value;
			if (value < minValueData)
				minValueData = value;
			valid = true;
		}
		// Set display range to match the data
		minValue = minValueData;
		maxValue = maxValueData;
		logger.info("Measurement mapper limits for " + measurement + ": " + minValueData + ", " + maxValueData);
	}

	/**
	 * Returns true if objects with values outside the specified min/max range have the min/max colors returned, false if null should be returned instead.
	 * @return
	 */
	public boolean getExcludeOutsideRange() {
		return excludeOutsideRange;
	}

	/**
	 * Returns true if objects with values outside the specified min/max range have the min/max colors returned, false if null should be returned instead.
	 */
	public void setExcludeOutsideRange(boolean excludeOutsideRange) {
		this.excludeOutsideRange = excludeOutsideRange;
	}

	public boolean isValid() {
		return valid;
	}

	public Integer getColorForObject(PathObject pathObject) {

//		if (!(colorMapper instanceof RedAlphaColorMapper6))
//			colorMapper = new RedAlphaColorMapper6();

		//		if (!pathObject.isDetection())
		if (!(pathObject instanceof PathDetectionObject || pathObject instanceof PathTileObject))
			return PathObjectColorToolsAwt.getDisplayedColor(pathObject);

		// Replace NaNs with the minimum value
		double value = getUsefulValue(pathObject, Double.NaN);

		if (excludeOutsideRange && (value < minValue || value > maxValue))
			return null;

		if (Double.isNaN(value))
			return null;
		//		if (Double.isNaN(value))
		//			value = minValue;

		// Map value to color
		return colorMapper.getColor(value, minValue, maxValue);
	}


	protected double getUsefulValue(PathObject pathObject, double nanValue) {
		double value;
		if (isClassProbability)
			value = pathObject.getClassProbability();
		else
			value = pathObject.getMeasurementList().getMeasurementValue(measurement);
		// Convert NaN to zero
		if (Double.isNaN(value))
			value = nanValue;
		// Convert infinities to the data min/max
		else if (Double.isInfinite(value)) {
			if (value > 0)
				return maxValueData;
			else
				return minValueData;
		}
		return value;
	}

	public ColorMapper getColorMapper() {
		return colorMapper;
	}

	public double getDataMinValue() {
		return minValueData;
	}

	public double getDataMaxValue() {
		return maxValueData;
	}

	public void setDisplayMinValue(double minValue) {
		this.minValue = minValue;
	}

	public void setDisplayMaxValue(double maxValue) {
		this.maxValue = maxValue;
	}

	public double getDisplayMinValue() {
		return minValue;
	}

	public double getDisplayMaxValue() {
		return maxValue;
	}







	public static interface ColorMapper {

		public boolean hasAlpha();

		public Integer getColor(int ind);

		public Integer getColor(double value, double minValue, double maxValue);

	}



	public static class FireColorMapper implements ColorMapper{

		private static final int[] r = {0,0,1,25,49,73,98,122,146,162,173,184,195,207,217,229,240,252,255,255,255,255,255,255,255,255,255,255,255,255,255,255};
		private static final int[] g = {0,0,0,0,0,0,0,0,0,0,0,0,0,14,35,57,79,101,117,133,147,161,175,190,205,219,234,248,255,255,255,255};
		private static final int[] b = {0,61,96,130,165,192,220,227,210,181,151,122,93,64,35,5,0,0,0,0,0,0,0,0,0,0,0,35,98,160,223,255};
		private static int nColors = b.length;
		private static Integer[] colors = new Integer[r.length];

		@Override
		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.makeRGB(r[ind], g[ind], b[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement mapper: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)((value - minValue) / (maxValue - minValue) * nColors + .5);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			}
			return getColor(ind);
		}

		@Override
		public boolean hasAlpha() {
			return false;
		}

	}



	public static class PseudoColorMapper implements ColorMapper {

		private static final int[] r = {0, 0,   0,   0,   255, 255};
		private static final int[] g = {0, 0,   255, 255, 255, 0};
		private static final int[] b = {0, 255, 255, 0,   0,   0};
		private static int nColors = 256;
		private static Integer[] colors = new Integer[nColors];

		static {
			double scale = (double)(r.length - 1) / nColors;
			for (int i = 0; i < nColors; i++) {
				int ind = (int)(i * scale);
				double residual = (i * scale) - ind;
				colors[i] = ColorTools.makeRGB(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.makeRGB(r[r.length-1], g[g.length-1], b[b.length-1]);
		}


		@Override
		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.makeRGB(r[ind], g[ind], b[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement mapper: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)((value - minValue) / (maxValue - minValue) * nColors + .5);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			}
			return getColor(ind);
		}


		@Override
		public boolean hasAlpha() {
			return false;
		}


	}



	public static class AlphaColorMapper implements ColorMapper {

		private static final int[] r = {0, 0,   0,   0,   255, 255};
		private static final int[] g = {0, 0,   255, 255, 255, 0};
		private static final int[] b = {0, 255, 255, 0,   0,   0};
		private static final int[] a = {0, 51, 102, 153, 204, 255};
		//		private static final int[] a = {255, 204, 153, 102,   51,   0};
		private static int nColors = 256;
		private static Integer[] colors = new Integer[nColors];

		static {
			double scale = (double)(r.length - 1) / nColors;
			for (int i = 0; i < nColors; i++) {
				int ind = (int)(i * scale);
				double residual = (i * scale) - ind;
				colors[i] = ColorTools.makeRGBA(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual),
						a[ind] + (int)((a[ind+1] - a[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.makeRGBA(r[r.length-1], g[g.length-1], b[b.length-1], a[a.length-1]);
		}


		@Override
		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.makeRGBA(r[ind], g[ind], b[ind], a[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement mapper: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)((value - minValue) / (maxValue - minValue) * nColors + .5);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			}
			return getColor(ind);
		}

		@Override
		public boolean hasAlpha() {
			return true;
		}

	}



	public static class RedAlphaColorMapper implements ColorMapper {

		private static final int[] r = {255, 255, 255};
		private static final int[] g = {200, 128, 0};
		private static final int[] b = {0, 0, 0};
		private static final int[] a = {0, 100, 128};
		//		private static final int[] a = {255, 204, 153, 102,   51,   0};
		private static int nColors = 256;
		private static Integer[] colors = new Integer[nColors];

		static {
			double scale = (double)(r.length - 1) / nColors;
			for (int i = 0; i < nColors; i++) {
				int ind = (int)(i * scale);
				double residual = (i * scale) - ind;
				System.err.println(a[ind] + (int)((a[ind+1] - a[ind]) * residual));
				colors[i] = ColorTools.makeRGBA(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual),
						a[ind] + (int)((a[ind+1] - a[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.makeRGBA(r[r.length-1], g[g.length-1], b[b.length-1], a[a.length-1]);
		}


		@Override
		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.makeRGBA(r[ind], g[ind], b[ind], a[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement mapper: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)((value - minValue) / (maxValue - minValue) * nColors + .5);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			}
			return getColor(ind);
		}

		@Override
		public boolean hasAlpha() {
			return true;
		}

	}


}
