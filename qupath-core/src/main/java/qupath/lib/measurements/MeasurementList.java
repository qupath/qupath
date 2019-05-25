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

import java.io.Serializable;
import java.util.List;

/**
 * Interface defining a feature measurement list, consisting of key value pairs.
 * <p>
 * To help enable efficiency for large sets of PathObjects requiring measurement lists,
 * only String keys and numeric values are included.
 * 
 * @author Pete Bankhead
 *
 */
public interface MeasurementList extends Serializable, AutoCloseable {
	
	public enum TYPE {
		GENERAL,
		DOUBLE,
		FLOAT}
	
	public boolean addMeasurement(String name, double value);
	
	/**
	 * Put a measurement into the list, replacing any previous measurement with the same name.
	 * <p>
	 * This is similar to add, but with a check to remove any existing measurement with the same name
	 * (if multiple measurements have the same name, the first will be replaced).
	 * <p>
	 * While it's probably a good idea for measurements to always have unique names, for some implementations
	 * putMeasurement can be must slower than add or addMeasurement - so adding should be preferred if it is
	 * known that a measurement with the same name is not present.
	 * 
	 * @param measurement
	 * @return
	 */
	public Measurement putMeasurement(Measurement measurement);
	
	public void putMeasurement(String name, double value);

	public List<String> getMeasurementNames();

	public String getMeasurementName(int ind);

	public double getMeasurementValue(int ind);

	public double getMeasurementValue(String name);

	public boolean containsNamedMeasurement(String name);

	public boolean isEmpty();
	
	public int size();
	
	public boolean supportsDynamicMeasurements();
	
	/**
	 * Close the list. Depending on the implementation, the list may then adjust its internal storage to be
	 * more efficient.
	 * 
	 */
	@Override
	public void close();
	
	public void removeMeasurements(String...measurementNames);
	
	public void clear();

}
