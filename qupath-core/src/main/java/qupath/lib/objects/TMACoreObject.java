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
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.LogTools;
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
	
	private static final Logger logger = LoggerFactory.getLogger(TMACoreObject.class);
	
	private static final long serialVersionUID = 1L;

	/**
	 * The key used before v0.4.0 to represent a unique (usually patient) ID.
	 * This was replaced by #KEY_CASE_ID.
	 */
	@Deprecated
	public static final String LEGACY_KEY_UNIQUE_ID = "Unique ID";

	/**
	 * Metadata key to store a case identify for the TMA core.
	 * This can be used to group cores that belong to the same case.
	 */
	public static final String KEY_CASE_ID = "Case ID";
	
	/**
	 * Metadata key for an overall survival (temporal) value.
	 */
	public static final String KEY_OVERALL_SURVIVAL = "Overall survival";
	
	/**
	 * Metadata key for an recurrence-free survival (temporal) value.
	 */
	public static final String KEY_RECURRENCE_FREE_SURVIVAL = "Recurrence-free survival";
	
	/**
	 * Metadata key for an overall survival censored flag.
	 */
	public static final String KEY_OS_CENSORED = "OS censored";
	
	/**
	 * Metadata key for an recurrence-free survival censored flag.
	 */
	public static final String KEY_RFS_CENSORED = "RFS censored";
	
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
	 * Get the case ID metadata value.
	 * @return
	 * 
	 * @see #setCaseID(String)
	 */
	public String getCaseID() {
		return getMetadataString(KEY_CASE_ID);
	}

	/**
	 * Set the case ID metadata value. This is typically used to store a patient identifier, 
	 * and must be unique for the patient (but multiple cores may have the same ID if they correspond 
	 * to the same patient).
	 * @param caseID
	 */
	public void setCaseID(final String caseID) {
		putMetadataValue(KEY_CASE_ID, caseID);
	}

	/**
	 * Put a metadata value
	 * @param key
	 * @param value
	 * @return any previous metadata value, or null if none
	 * @deprecated v0.6.0. Use {@link #putMetadataValue(String, String)} instead.
	 */
	@Override
	@Deprecated
	public Object putMetadataValue(final String key, final String value) {
		return storeMetadataValue(key, value);
	}

	/**
	 * Get a string metadata value
	 * @param key
	 * @return
	 * @deprecated v0.6.0. Use {@link #getMetadata()} to directly access metadata instead.
	 */
	@Override
	@Deprecated
	public String getMetadataString(final String key) {
		Object value = getMetadataValue(key);
		if (value instanceof String)
			return (String)value;
		return null;
	}

	/**
	 * Get a metadata value of any kind.
	 * @param key
	 * @return
	 * @deprecated v0.6.0. Use {@link #getMetadata()} to directly access metadata instead.
	 */
	@Override
	@Deprecated
	public Object getMetadataValue(final String key) {
		return super.retrieveMetadataValue(key);
	}

	/**
	 * Get all metadata keys.
	 * @return
	 * @deprecated v0.6.0. Use {@link #getMetadata()} to directly access metadata instead.
	 */
	@Override
	@Deprecated
	public Set<String> getMetadataKeys() {
		return super.retrieveMetadataKeys();
	}
	
	/**
	 * Clear all associated metadata.
	 * @deprecated v0.6.0. Use {@link #getMetadata()} to directly access metadata instead.
	 */
	@Deprecated
	public void clearMetadata() {
		super.clearMetadataMap();
	}
	
	
	@Override
	public String toString() {
		return getDisplayedName() + objectCountPostfix();
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
		var legacyCaseID = getMetadataString(LEGACY_KEY_UNIQUE_ID);
		if (getCaseID() == null && legacyCaseID != null) {
			LogTools.warnOnce(logger, "Updating legacy 'Unique ID' to 'Case ID' (introduced in v0.4.0)");
			setCaseID(legacyCaseID);
		}
	}
}
