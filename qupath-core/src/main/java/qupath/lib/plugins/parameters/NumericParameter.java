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

package qupath.lib.plugins.parameters;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Abstract parameter to represent a numeric value.
 * 
 * @see DoubleParameter
 * @see IntParameter
 * 
 * @author Pete Bankhead
 *
 * @param <S>
 */
public abstract class NumericParameter<S extends Number> extends AbstractParameter<S> {
	
	private static final long serialVersionUID = 1L;
	
	private String unit = null;
	private double minValue = Double.NEGATIVE_INFINITY;
	private double maxValue = Double.POSITIVE_INFINITY;
	
	NumericParameter(String prompt, S defaultValue, String unit, double minValue, double maxValue, S lastValue, String helpText, boolean isHidden) {
		super(prompt, defaultValue, lastValue, helpText, isHidden);
		this.unit = unit;
		this.minValue = minValue;
		this.maxValue = maxValue;
	}
	
	NumericParameter(String prompt, S defaultValue, String unit, double minValue, double maxValue, String helpText) {
		this(prompt, defaultValue, unit, minValue, maxValue, null, helpText, false);
	}

	NumericParameter(String prompt, S defaultValue, String unit, String helpText) {
		this(prompt, defaultValue, unit, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, helpText);
	}
	
	/**
	 * Returns true if this parameter has <i>both</i> lower and upper bounds constraining valid values.
	 * @return
	 */
	public boolean hasLowerAndUpperBounds() {
		return hasLowerBound() && hasUpperBound();
	}
	
	/**
	 * Returns true if this <i>neither</i> a lower nor an upper bound constraining valid values.
	 * @return
	 */
	public boolean isUnbounded() {
		return !hasLowerBound() && !hasUpperBound();
	}
	
	/**
	 * Retrieve the lower bound. May be Double.NEGATIVE_INFINITY if the parameter has no lower bound.
	 * @return
	 */
	public double getLowerBound() {
		return minValue;
	}

	/**
	 * Retrieve the upper bound. May be Double.POSITIVE_INFINITY if the parameter has no upper bound.
	 * @return
	 */
	public double getUpperBound() {
		return maxValue;
	}
	
	/**
	 * Set the upper and lower bounds.
	 * <p>
	 * Note: depending on how the parameter is displayed (or if it is displayed) this might not appear
	 * to make a difference.  If shown through a ParameterPanel (in QuPath's JavaFX GUI) it is better to
	 * set limits via the panel rather than directly using this method.
	 * 
	 * @param minValue
	 * @param maxValue
	 */
	public void setRange(double minValue, double maxValue) {
		if (Double.isNaN(minValue))
			minValue = Double.NEGATIVE_INFINITY;
		if (Double.isNaN(maxValue))
			minValue = Double.POSITIVE_INFINITY;
		if (minValue <= maxValue) {
			this.minValue = minValue;
			this.maxValue = maxValue;
		} else
			throw new IllegalArgumentException("Invalid range set " + minValue + "-" + maxValue + ": minValue must be <= maxValue");
	}

	/**
	 * Returns true if the parameter has a valid lower bound.
	 * @return
	 */
	public boolean hasLowerBound() {
		return Math.abs(minValue) <= Double.MAX_VALUE;
	}
	
	/**
	 * Returns true if the parameter has a valid upper bound.
	 * @return
	 */
	public boolean hasUpperBound() {
		return Math.abs(maxValue) <= Double.MAX_VALUE;
	}

	/**
	 * Get the unit to display for this parameter (may be null if no unit is available).
	 * @return
	 */
	public String getUnit() {
		return unit;
	}
	
	/**
	 * Set the value of this parameter, constraining it to be within any lower and upper bounds if necessary.
	 * @param lastValue
	 * @return
	 */
	public abstract boolean setValueWithBoundsCheck(S lastValue);
	
	/**
	 * A class for setting the numeric value as a double (subclasses should convert this as needed).
	 * 
	 * @param val
	 * @return
	 */
	public abstract boolean setDoubleLastValue(double val);
	
	/**
	 * Numbers are considered valid if they are not NaN
	 */
	@Override
	public boolean isValidInput(S value) {
		return !Double.isNaN(value.doubleValue());
	}
	
	@Override
	public boolean setStringLastValue(Locale locale, String value) {
		try {
			Number number;
			if (locale == null)
				number = NumberFormat.getInstance(locale).parse(value);
			else
				number = NumberFormat.getInstance(locale).parse(value);				
			return setDoubleLastValue(number.doubleValue());
		} catch (Exception e) {}
		// Old code (shouldn't be needed?)
		try {
			double d = Double.parseDouble(value);
			return setDoubleLastValue(d);
		} catch (NumberFormatException e) {}
		return false;
	}
	
}
