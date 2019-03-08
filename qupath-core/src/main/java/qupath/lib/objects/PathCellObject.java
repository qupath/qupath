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
 * 
 * The primary ROI represents the cell boundary.
 * 
 * @author Pete Bankhead
 *
 * @see PathDetectionObject
 */
public class PathCellObject extends PathDetectionObject {
	
	private static final long serialVersionUID = 1L;
	
	private ROI nucleus;

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

	public boolean hasNucleus() {
		return nucleus != null;
	}
	
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
