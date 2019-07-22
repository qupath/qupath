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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.roi.interfaces.ROI;

/**
 * Abstract class used for PathObjects that have ROIs associated with them.
 * <p>
 * In practice, this is almost all PathObjects (with the notable exception of PathRootObjects).
 * 
 * @author Pete Bankhead
 *
 */
public abstract class PathROIObject extends PathObject {
	
	private static Logger logger = LoggerFactory.getLogger(PathROIObject.class);
	
	private static final long serialVersionUID = 1L;
	
	private PathClass pathClass = null;
	private ROI pathROI = null;
	private double classProbability = Double.NaN;
	private boolean lockedROI = false; //J Lock to determine whether ROI is locked (set by user)

	PathROIObject() {
		super();
	}
	
	PathROIObject(ROI pathROI, PathClass pc) {
		super();
		this.pathROI = pathROI;
		setPathClass(pc);
	}
	
	PathROIObject(MeasurementList measurements) {
		super(measurements);
	}
	
	PathROIObject(ROI pathROI, PathClass pc, MeasurementList measurements) {
		super(measurements);
		this.pathROI = pathROI;
		setPathClass(pc);
	}
	
	/**
	 * Set the ROI for this object. If this is called, one should remember to update any associated 
	 * hierarchy to notify it of the change.
	 * @param roi
	 */
	public void setROI(final ROI roi) {
		if (roi == null)
			throw new IllegalArgumentException("PathROIObject.setROI cannot be called with null!");
		this.pathROI = roi;
	}
	
	/**
	 * Set locked flag, indicating that the object ROI should not be modified.
	 * It directly impacts on {@link #isEditable()}
	 * <p>
	 * Note that this is only a hint that other code should pay attention to - it is not
	 * enforced locally.
	 * <p>
	 * TODO: Consider shifting this method into PathObject rather than PathROIObject (even
	 * if it doesn't really do anything there).
	 * 
	 * @param locked
	 */
	@Override
	public void setLocked(final boolean locked) {
		this.lockedROI = locked;
	}

	/**
	 * Query the locked status for the object, indicating whether it should be editable or not.
	 * 
	 * @return
	 */
	@Override
	public boolean isLocked() {
		return this.lockedROI;
	}

	/**
	 * Currently, a ROI is editable if it isn't locked.
	 *  
	 *  TODO: Consider whether this is a good basis on which to make the decision!
	 */
	@Override
	public boolean isEditable() {
		// Note on commented out code (Pete):
		// Previous code that attempted to automatically set locked status (effectively) without storing it in a variable
		// Kind of worked for common use cases, but not a great long-term solution
//J		return !PathObjectTools.containsChildOfClass(this, PathDetectionObject.class, false) || getParent() == null || !getParent().isRootObject();
		return !this.lockedROI;
	}
	
	@Override
	public void setPathClass(PathClass pathClass, double classProbability) {
		if (pathClass != null && !pathClass.isValid()) {
			logger.warn("Classification {} is invalid! Will be set to null instead", pathClass);
			pathClass = null;
		}
		if (pathClass == null) {
//			if (pathROI != null && this.pathClass != null && this.pathClass.getName().equals(pathROI.getName()))
//				pathROI.setName(null);
			this.pathClass = pathClass;
			this.classProbability = classProbability;
			return;
		}
//		if (pathROI != null) {
//			pathROI.setName(pathClass.getName());
//		}
		this.pathClass = pathClass;
		this.classProbability = classProbability;
		// Forget any previous color, if we have a PathClass
		if (this.pathClass != null)
			setColorRGB(null);
	}
	
	@Override
	public double getClassProbability() {
		return classProbability;
	}


	@Override
	public PathClass getPathClass() {
		return pathClass;
	}

	@Override
	public ROI getROI() {
		return pathROI;
	}

	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);

		out.writeObject(Boolean.valueOf(lockedROI));
		out.writeObject(pathClass);
		out.writeObject(pathROI);
		out.writeDouble(classProbability);
		
	}
	
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);

		// Another example of trying to extend serialization without breaking something else...
		// Forgot to store locked status previously... which wasn't good
		Object firstObject = in.readObject();
		if (firstObject instanceof Boolean) {
			lockedROI = ((Boolean)firstObject).booleanValue();
			firstObject = in.readObject();
		}
		if (firstObject instanceof PathClass)
			pathClass = (PathClass)firstObject;
		
		// TODO: STORE PATHCLASSES AS STRINGS OR SOMETHING BETTER THAN JUST USING SERIALIZATION!
		// Go via the factory to ensure that we don't end up with multiple classes with the same name
		if (pathClass != null)
			pathClass = PathClassFactory.getSingletonPathClass(pathClass);
		pathROI = (ROI)in.readObject();
		classProbability = in.readDouble();
	}

//	@Override
//	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//		super.readExternal(in);
//
//		Object pathClassObject = in.readObject();
//		if (pathClassObject instanceof PathClass)
//			pathClass = (PathClass)pathClassObject;
//		// TODO: STORE PATHCLASSES AS STRINGS OR SOMETHING BETTER THAN JUST USING SERIALIZATION!
//		
//		// Go via the factory to ensure that we don't end up with multiple classes with the same name
//		if (pathClass != null)
//			pathClass = PathClassFactory.getSingletonPathClass(pathClass);
//		pathROI = (ROI)in.readObject();
//		classProbability = in.readDouble();
//	}
	

}
