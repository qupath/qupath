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

package qupath.lib.measurements;

/**
 * Factory for creating new Measurement objects.
 * <p>
 * Made more sense when dynamic measurements were in use.
 * <p>
 * May not be particularly useful now (?).
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class MeasurementFactory {
	
	/**
	 * Create a measurement with a double value.
	 * @param name
	 * @param value
	 * @return
	 */
	public static Measurement createMeasurement(final String name, final double value) {
		return new DoubleMeasurement(name, value);
	}

	/**
	 * Create a measurement with a float value.
	 * @param name
	 * @param value
	 * @return
	 */
	public static Measurement createMeasurement(final String name, final float value) {
		return new FloatMeasurement(name, (float)value);
	}
	
}

class DoubleMeasurement implements Measurement {
	
	private static final long serialVersionUID = 1L;
	
	final private String name;
	final private double value;
	
	DoubleMeasurement(String name, double value) {
		// There may be many measurements... so interning the names can help considerably
		this.name = name.intern();
		this.value = value;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return getName() + ": " + Double.toString(getValue());
	}

	@Override
	public double getValue() {
		return value;
	}

	@Override
	public boolean isDynamic() {
		return false;
	}

}



class FloatMeasurement implements Measurement {
	
	private static final long serialVersionUID = 1L;
	
	final private String name;
	final private float value;
	
	FloatMeasurement(String name, double value) {
		// There may be many measurements... so interning the names can help considerably
		this.name = name.intern();
		this.value = (float)value;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return getName() + ": " + Double.toString(getValue());
	}

	@Override
	public double getValue() {
		return value;
	}

	@Override
	public boolean isDynamic() {
		return false;
	}

}