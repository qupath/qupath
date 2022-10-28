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

package qupath.lib.gui.tools;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;

/**
 * Helpers class that can be used to map an object's measurement to a color (packed RGB int).
 * <p>
 * By passing a collection of objects, the minimum and maximum of all the measurements are found
 * and these used to determine the lookup table scaling; alternative minimum and maximum values can also
 * be set to override these extrema.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(MeasurementMapper.class);
	
	private ColorMap colorMapper;

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

	/**
	 * Constructor.
	 * @param mapper color mapper (lookup table)
	 * @param measurement the measurement to colorize
	 * @param pathObjects an initial collection of objects used to determine display ranges (i.e. find the min/max values of the specified measurement)
	 */
	public MeasurementMapper(ColorMap mapper, String measurement, Collection<? extends PathObject> pathObjects) {
		this.colorMapper = mapper;
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
		logger.debug("Measurement mapper limits for " + measurement + ": " + minValueData + ", " + maxValueData);
	}
	

	
	
	/**
	 * Set a new color mapper.
	 * @param mapper
	 */
	public void setColorMapper(ColorMap mapper) {
		this.colorMapper = mapper;
	}

	/**
	 * Returns true if objects with values outside the specified min/max range have the min/max colors returned, false if null should be returned instead.
	 * @return
	 */
	public boolean getExcludeOutsideRange() {
		return excludeOutsideRange;
	}

	/**
	 * Specify whether out-of-range values should be excluded.
	 * @param excludeOutsideRange 
	 */
	public void setExcludeOutsideRange(boolean excludeOutsideRange) {
		this.excludeOutsideRange = excludeOutsideRange;
	}

	/**
	 * Query if the mapper is valid. This returns true if the mapper has been initialized with objects to 
	 * determine an appropriate display range.
	 * @return
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * Get the display color for a specified object, according to the settings of this mapper.
	 * @param pathObject
	 * @return
	 */
	public Integer getColorForObject(PathObject pathObject) {

//		if (!(colorMapper instanceof RedAlphaColorMapper6))
//			colorMapper = new RedAlphaColorMapper6();

		//		if (!pathObject.isDetection())
		if (!(pathObject instanceof PathDetectionObject || pathObject instanceof PathTileObject))
			return ColorToolsFX.getDisplayedColorARGB(pathObject);

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
			value = pathObject.getMeasurementList().get(measurement);
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

	/**
	 * Get the color mapper, which is effectively a lookup table.
	 * @return
	 */
	public ColorMap getColorMapper() {
		return colorMapper;
	}

	/**
	 * Get the minimum measurement value from the objects passed to the constructor of this mapper.
	 * @return
	 */
	public double getDataMinValue() {
		return minValueData;
	}

	/**
	 * Get the maximum measurement value from the objects passed to the constructor of this mapper.
	 * @return
	 */
	public double getDataMaxValue() {
		return maxValueData;
	}

	/**
	 * Set the measurement value that maps to the first color in the color mapper.
	 * @param minValue
	 */
	public void setDisplayMinValue(double minValue) {
		this.minValue = minValue;
	}

	/**
	 * Set the measurement value that maps to the last color in the color mapper.
	 * @param maxValue
	 */
	public void setDisplayMaxValue(double maxValue) {
		this.maxValue = maxValue;
	}

	/**
	 * Get the measurement value that maps to the first color in the color mapper.
	 * @return
	 */
	public double getDisplayMinValue() {
		return minValue;
	}

	/**
	 * Get the measurement value that maps to the last color in the color mapper.
	 * @return
	 */
	public double getDisplayMaxValue() {
		return maxValue;
	}


}
