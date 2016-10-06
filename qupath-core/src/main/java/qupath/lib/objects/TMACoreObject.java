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

import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.roi.EllipseROI;

/**
 * A special PathObject used exclusively to represent TMA cores.
 * 
 * Currently, these only contain circular (or elliptical) ROIs.
 * 
 * @author Pete Bankhead
 *
 */
public class TMACoreObject extends PathROIObject implements MetadataStore {
	
	private static final long serialVersionUID = 1L;
	
	final public static String KEY_UNIQUE_ID = "Unique ID";
	final public static String KEY_OVERALL_SURVIVAL = "Overall survival";
	final public static String KEY_RECURRENCE_FREE_SURVIVAL = "Recurrence-free survival";
	final public static String KEY_OS_CENSORED = "OS censored";
	final public static String KEY_RFS_CENSORED = "RFS censored";
	
	private boolean isMissing = false;
	
	public TMACoreObject() {
		super();
	}
	
	public TMACoreObject(double xCenter, double yCenter, double diameter, boolean isMissing) {
		this(xCenter-diameter/2, yCenter-diameter/2, diameter, diameter, isMissing);
	}
	
	public TMACoreObject(double x, double y, double width, double height, boolean isMissing) {
		super(new EllipseROI(x, y, width, height, -1, 0, 0), null);
		this.isMissing = isMissing;
	}

	public boolean isMissing() {
		return isMissing;
	}
	
	public void setMissing(boolean missing) {
		this.isMissing = missing;
	}
	
	public String getUniqueID() {
		return getMetadataString(KEY_UNIQUE_ID);
	}

	public void setUniqueID(final String uniqueID) {
		putMetadataValue(KEY_UNIQUE_ID, uniqueID);
	}

	@Override
	public Object putMetadataValue(final String key, final String value) {
		return storeMetadataValue(key, value);
	}
	
	@Override
	public boolean containsMetadataString(final String key) {
		return getMetadataValue(key) instanceof String;
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
		return super.isEditable() && !PathObjectTools.containsChildOfClass(this, PathDetectionObject.class, true);
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
