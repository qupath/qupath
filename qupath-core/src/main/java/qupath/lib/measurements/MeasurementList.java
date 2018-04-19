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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Interface defining a feature measurement list, consisting of key value pairs.
 * 
 * To help enable efficiency for large sets of PathObjects requiring measurement lists,
 * only String keys and numeric values are included.
 * 
 * @author Pete Bankhead
 *
 */
public interface MeasurementList extends Serializable, Iterable<Measurement> {
	
	public enum TYPE {GENERAL, DOUBLE, FLOAT}
	
	public boolean addMeasurement(String name, double value);
	
	/**
	 * Put a measurement into the list, replacing any previous measurement with the same name.
	 * This is similar to add, but with a check to remove any existing measurement with the same name
	 * (if multiple measurements have the same name, the first will be replaced)
	 * 
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
	

//	/**
//	 * Request that the list tries to reduce the memory requirements internally - 
//	 * this may be useful if no more measurements will be added,
//	 * and potentially large numbers of similar lists will be created.
//	 */
//	public void compactStorage();

	public boolean containsAllNamedMeasurements(Collection<String> keys);
	
	public boolean containsNamedMeasurement(String name);
	
//	/**
//	 * Create a new MeasurementList with the same type as the current MeasurementList.
//	 * 
//	 * @param retainValues
//	 * @return
//	 */
//	public MeasurementList newInstance();

	public boolean isEmpty();
	
	public int size();
	
	public boolean add(Measurement measurement);

	@Override
	public Iterator<Measurement> iterator();

	public boolean supportsDynamicMeasurements();

	/**
	 * Returns TRUE if this list contains any dynamic measurements, false if all measurements are stored.
	 * This can be useful for determining if the list can be represented by another list that only supports
	 * stored measurements.
	 * 
	 * @return
	 */
	public boolean hasDynamicMeasurements();

//	/**
//	 * TRUE if the list is closed (i.e. cannot be modified), FALSE if it can accept new measurements.
//	 * @return
//	 */
//	public boolean isClosed();
	
	/**
	 * Close the list so that it cannot accept new measurements.
	 * Depending on the implementation, the list may then adjust its internal storage to be
	 * more efficient.
	 * 
	 * Any attempt to modify the list after closing it will lead to an UnsupportedOperationException.
	 * 
	 * Note: a closed list cannot be reopened!
	 */
	public void closeList();
	
	
//	public void ensureListOpen();
	
	
	public void removeMeasurements(String...measurementNames);
	
	
	public void clear();

//	/**
//	 * Remove a specific Measurement object (note, *not* the String name).
//	 * (Optional operation)
//	 * 
//	 * @param o
//	 * @return
//	 */
//	public boolean remove(Object o);

}
