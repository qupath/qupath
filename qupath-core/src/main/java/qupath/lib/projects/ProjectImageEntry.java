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

package qupath.lib.projects;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class to represent an image entry within a project.
 * 
 * This stores the path to the image, and some optional metadata.
 * 
 * @author Pete Bankhead
 *
 * @param <T> Depends upon the project used; typically BufferedImage for QuPath
 */
public interface ProjectImageEntry<T> {
	
	/**
	 * Get the path used to represent this image, which can be used to construct an <code>ImageServer</code>.
	 * 
	 * Note that this may have been cleaned up.
	 * 
	 * @see #getStoredServerPath
	 * 
	 * @return
	 */
	public String getServerPath();
	
	/**
	 * Set the image name for this project entry.
	 * 
	 * @param name
	 */
	public void setName(String name);

	/**
	 * Get a name that may be used for this entry.
	 * <p>
	 * This may be derived automatically from the server path, or set explictly to be something else.
	 * 
	 * @return
	 */
	public String getImageName();
	
	/**
	 * Get a unique name that may be used to represent data associated with this entry.
	 * <p>
	 * It should be unique within the project. In early versions of QuPath, the image name was used - 
	 * which caused trouble if multiple images had the same name.
	 */
	public String getUniqueName();
	
	/**
	 * Get the path used to represent this image, as specified when this entry was created.
	 * <p>
	 * It is generally better to rely on <code>getServerPath</code>, especially if paths will be compared.
	 * 
	 * @see #getServerPath
	 * 
	 * @return
	 */
	public String getStoredServerPath();
	
	/**
	 * Check if this image entry refers to a specified image according to its path.
	 * 
	 * @param serverPath
	 * @return <code>true</code> if the path is a match, <code>false</code> otherwise.
	 */
	public boolean equalsServerPath(final String serverPath);
		
	@Deprecated
	public String getCleanedServerPath();
	
	/**
	 * Remove a metadata value.
	 * 
	 * @param key
	 * @return
	 */
	public String removeMetadataValue(final String key);
	
	/**
	 * Request a metadata value.
	 * Note that this may return <code>null</code>.
	 * 
	 * @param key
	 * @return
	 */
	public String getMetadataValue(final String key);

	/**
	 * Store a metadata value.
	 * This is intended as storage of short key-value pairs.
	 * Extended text should be stored under <code>setDescription</code>.
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public String putMetadataValue(final String key, final String value);
	
	/**
	 * Check if a metadata value is present for a specified key.
	 * 
	 * @param key
	 * @return <code>true</code> if <code>getDescription()</code> does not return null or an empty string, <code>false</code> otherwise.
	 */
	public boolean containsMetadata(final String key);
	
	/**
	 * Get a description; this is free text describing the image.
	 * @return
	 */
	public String getDescription();
	
	/**
	 * Set the description.
	 * 
	 * @see #getDescription
	 * @param description
	 */
	public void setDescription(final String description);
	
	/**
	 * Remove all metadata.
	 */
	public void clearMetadata();
	
	/**
	 * Get an unmodifiable view of the underlying metadata map.
	 * 
	 * @return
	 */
	public Map<String, String> getMetadataMap();
	
	/**
	 * Get an unmodifiable collection of the metadata map's keys.
	 * 
	 * @return
	 */
	public Collection<String> getMetadataKeys();
	
	
	/**
	 * Get a formatted string representation of the metadata map's contents.
	 * 
	 * @return
	 */
	default public String getMetadataSummaryString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		for (Entry<String, String> entry : getMetadataMap().entrySet()) {
			if (sb.length() > 1)
				sb.append(", ");
			sb.append(entry.getKey());
			sb.append(":");
			sb.append(entry.getValue());
		}
		sb.append("}");
		return sb.toString();
	}

}