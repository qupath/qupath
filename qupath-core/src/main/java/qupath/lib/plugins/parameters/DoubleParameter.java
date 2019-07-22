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

package qupath.lib.plugins.parameters;

/**
 * Parameter to represent a floating point numeric value.
 * <p>
 * May be bounded.
 * 
 * @author Pete Bankhead
 *
 */
public class DoubleParameter extends NumericParameter<Double> {
	
	private static final long serialVersionUID = 1L;

	DoubleParameter(String prompt, Double defaultValue, String unit, Double minValue, Double maxValue, Double lastValue, String helpText, boolean isHidden) {
		super(prompt, defaultValue, unit, minValue, maxValue, lastValue, helpText, isHidden);
	}

	DoubleParameter(String prompt, Double defaultValue, String unit, Double minValue, Double maxValue, String helpText) {
		super(prompt, defaultValue, unit, minValue, maxValue, helpText);
	}

	DoubleParameter(String prompt, Double defaultValue, String unit, String helpText) {
		super(prompt, defaultValue, unit, helpText);
	}
	
	/**
	 * Set the last value; this will apply a range check using clipping.
	 * 
	 * @param lastValue
	 */
	@Override
	public boolean setValue(Double lastValue) {
		if (!isValidInput(lastValue))
			return false;
		this.lastValue = lastValue;
//		this.lastValue = Math.max(Math.min(lastValue, getUpperBound()), getLowerBound());
		return true;
	}
	
	@Override
	public boolean setValueWithBoundsCheck(Double lastValue) {
		if (!isValidInput(lastValue))
			return false;
		this.lastValue = Math.max(Math.min(lastValue, getUpperBound()), getLowerBound());
		return true;
	}

	@Override
	public boolean setDoubleLastValue(double val) {
		return setValue(val);
	}

	@Override
	public Parameter<Double> duplicate() {
		return new DoubleParameter(getPrompt(), getDefaultValue(), getUnit(), getLowerBound(), getUpperBound(), getValue(), getHelpText(), isHidden());
	}
	
}
