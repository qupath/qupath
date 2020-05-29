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

package qupath.lib.objects;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

/**
 * A subclass of a PathDetectionObject, which also supports storing an (optional) extra ROI to represent 
 * the cell nucleus.
 * <p>
 * The primary ROI represents the cell boundary.
 * 
 * @author Pete Bankhead
 *
 * @see PathDetectionObject
 */
public class PathCellObject extends PathDetectionObject {
	
	private static final long serialVersionUID = 1L;
	
	private ROI nucleus;

	/**
	 * Default constructor. Should not be used directly, instead use {@link PathObjects#createCellObject(ROI, ROI, PathClass, MeasurementList)}.
	 */
	public PathCellObject() {
		super();
	}
	
	PathCellObject(ROI pathROI, ROI nucleusROI, PathClass pathClass, MeasurementList measurementList) {
		super(pathROI, pathClass, measurementList);
		this.nucleus = nucleusROI;
	}

	PathCellObject(ROI pathROI, ROI nucleusROI, PathClass pathClass) {
		this(pathROI, nucleusROI, pathClass, null);
	}

	/**
	 * Returns true if a nucleus ROI is stored for this cell.
	 * @return
	 */
	public boolean hasNucleus() {
		return nucleus != null;
	}
	
	/**
	 * Get the nucleus ROI for this cell. This may be null, or may be a ROI stored in addition to {@link #getROI()}, 
	 * which returns the overall cell boundary.
	 * @return
	 */
	public ROI getNucleusROI() {
		return nucleus;
	}
	
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(nucleus);
	}


	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {		
		super.readExternal(in);
		nucleus = (ROI)in.readObject();
	}
		
}
