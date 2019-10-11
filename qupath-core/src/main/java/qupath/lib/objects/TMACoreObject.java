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
import java.util.Map;
import java.util.Set;

import qupath.lib.roi.interfaces.ROI;

/**
 * A special PathObject used exclusively to represent TMA cores.
 * <p>
 * Currently, these only contain circular (or elliptical) ROIs.
 * 
 * @author Pete Bankhead
 *
 */
public class TMACoreObject extends PathROIObject implements MetadataStore {
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * Metadata key for the TMA core unique patient ID;
	 */
	final public static String KEY_UNIQUE_ID = "Unique ID";
	
	/**
	 * Metadata key for an overall survival (temporal) value.
	 */
	final public static String KEY_OVERALL_SURVIVAL = "Overall survival";
	
	/**
	 * Metadata key for an recurrence-free survival (temporal) value.
	 */
	final public static String KEY_RECURRENCE_FREE_SURVIVAL = "Recurrence-free survival";
	
	/**
	 * Metadata key for an overall survival censored flag.
	 */
	final public static String KEY_OS_CENSORED = "OS censored";
	
	/**
	 * Metadata key for an recurrence-free survival censored flag.
	 */
	final public static String KEY_RFS_CENSORED = "RFS censored";
	
	private boolean isMissing = false;
	
	/**
	 * Default constructor. Should not be used directly, instead use {@link PathObjects#createTMACoreObject(double, double, double, boolean)}.
	 */
	public TMACoreObject() {
		super();
	}
	
	TMACoreObject(ROI roi, boolean isMissing) {
		super(roi, null);
		this.isMissing = isMissing;
	}

	/**
	 * Query the 'missing' flag for this core.
	 * @return
	 */
	public boolean isMissing() {
		return isMissing;
	}
	
	/**
	 * Set the missing flag for this core, for example because insufficient tissue is present.
	 * 'Missing' cores are typically ignored during analysis.
	 * @param missing
	 */
	public void setMissing(boolean missing) {
		this.isMissing = missing;
	}
	
	/**
	 * Get the uniqueID metadata value.
	 * @return
	 * 
	 * @see #setUniqueID(String)
	 */
	public String getUniqueID() {
		return getMetadataString(KEY_UNIQUE_ID);
	}

	/**
	 * Set the uniqueID metadata value. This is typically used to store a patient identifier, 
	 * and must be unique for the patient (but multiple cores may have the same ID if they correspond 
	 * to the same patient).
	 * @param uniqueID
	 */
	public void setUniqueID(final String uniqueID) {
		putMetadataValue(KEY_UNIQUE_ID, uniqueID);
	}

	@Override
	public Object putMetadataValue(final String key, final String value) {
		return storeMetadataValue(key, value);
	}
	
	@Override
	public String getMetadataString(final String key) {
		Object value = getMetadataValue(key);
		if (value instanceof String)
			return (String)value;
		return null;
	}
	
	@Override
	public Object getMetadataValue(final String key) {
		return super.retrieveMetadataValue(key);
	}
	
	@Override
	public Set<String> getMetadataKeys() {
		return super.retrieveMetadataKeys();
	}
	
	@Override
	public Map<String, String> getMetadataMap() {
		return super.getUnmodifiableMetadataMap();
	}
	
	/**
	 * Clear all associated metadata.
	 */
	public void clearMetadata() {
		super.clearMetadataMap();
	}
	
	
	@Override
	public String toString() {
		return getDisplayedName() + objectCountPostfix();
//		if (getROI() != null)
//			return getROI().getName() + objectCountPostfix();
//		return "Unnamed TMA core"; // Entire image
	}
	
	
	/**
	 * TMA core cannot be edited if it contains any detections.
	 */
	@Override
	public boolean isEditable() {
		return super.isEditable() && !containsChildOfClass(this, PathDetectionObject.class, true);
	}
	
	private static boolean containsChildOfClass(final PathObject pathObject, final Class<? extends PathObject> cls, final boolean allDescendents) {
		for (PathObject childObject : pathObject.getChildObjectsAsArray()) {
			if (cls.isAssignableFrom(childObject.getClass()))
				return true;
			if (childObject.hasChildren() && allDescendents && containsChildOfClass(childObject, cls, allDescendents))
				return true;
		}
		return false;
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeBoolean(isMissing);
	}


	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {		
		super.readExternal(in);
		isMissing = in.readBoolean();
	}
}
