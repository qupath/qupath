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

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import qupath.lib.common.URLTools;

/**
 * Class to represent an image entry within a project.
 * 
 * This stores the path to the image, and some optional metadata.
 * 
 * @author Pete Bankhead
 *
 * @param <T> Depends upon the project used; typically BufferedImage for QuPath
 */
// TODO: URGENTLY NEED TO CONSIDER ESCAPING CHARACTERS IN URLS MORE GENERALLY
public class ProjectImageEntry<T> implements Comparable<ProjectImageEntry<T>> {

	private Project<T> project;
	private transient String cleanedPath = null;
	
	private String serverPath;
	private String imageName;
	
	private Map<String, String> metadata = new HashMap<>();
	
	private String description;

	public ProjectImageEntry(final Project<T> project, final String serverPath, final String imageName, final String description, final Map<String, String> metadataMap) {
		this.project = project;
		this.serverPath = serverPath;
		
		// TODO: Check if this is a remotely acceptable way to achieve relative pathnames!  I suspect it is not really...
		String projectPath = project.getBaseDirectory().getAbsolutePath();
		if (this.serverPath.startsWith(projectPath))
			this.serverPath = "{$PROJECT_DIR}" + this.serverPath.substring(projectPath.length());
		
		if (imageName == null) {
			if (URLTools.checkURL(serverPath))
				this.imageName = URLTools.getNameFromBaseURL(serverPath);
			else
				this.imageName = new File(serverPath).getName();
		} else
			this.imageName = imageName;
		
		if (description != null)
			setDescription(description);
		
		if (metadataMap != null)
			metadata.putAll(metadataMap);		
	}
	
	public ProjectImageEntry(final Project<T> project, final String serverPath, final String imageName, final Map<String, String> metadataMap) {
		this(project, serverPath, imageName, null, metadataMap);
	}
	
	/**
	 * Get the path used to represent this image, which can be used to construct an <code>ImageServer</code>.
	 * 
	 * Note that this may have been cleaned up.
	 * 
	 * @see #getStoredServerPath
	 * 
	 * @return
	 */
	public String getServerPath() {
//		return serverPath;
		return getCleanedServerPath();
	}

	/**
	 * Get a name that may be used for this entry.
	 * 
	 * This may be derived automatically from the server path, or set explictly to be something else.
	 * 
	 * @return
	 */
	public String getImageName() {
		return imageName;
	}

	@Override
	public String toString() {
		String s = getImageName();
		if (!metadata.isEmpty())
			s += " - " + getMetadataSummaryString();
		return s;
		//			return getServerPath();
	}
	
	/**
	 * Get the path used to represent this image, as specified when this entry was created.
	 * 
	 * It is generally better to rely on <code>getServerPath</code>, especially if paths will be compared.
	 * 
	 * @see #getServerPath
	 * 
	 * @return
	 */
	public String getStoredServerPath() {
		return serverPath;
	}
	
	// TODO: Improve implementation!
	private String getCleanedServerPath() {
		if (cleanedPath != null)
			return cleanedPath;
		cleanedPath = project.cleanServerPath(serverPath);
		return cleanedPath;
	}
	
	/**
	 * Check if this image entry refers to a specified image according to its path.
	 * 
	 * @param serverPath
	 * @return <code>true</code> if the path is a match, <code>false</code> otherwise.
	 */
	public boolean equalsServerPath(final String serverPath) {
		return getCleanedServerPath().equals(project.cleanServerPath(serverPath));
	}
	
	@Override
	public int compareTo(ProjectImageEntry<T> entry) {
		return getCleanedServerPath().compareTo(entry.getCleanedServerPath());
	}
	
	/**
	 * Remove a metadata value.
	 * 
	 * @param key
	 * @return
	 */
	public String removeMetadataValue(final String key) {
		return metadata.remove(key);
	}
	
	/**
	 * Request a metadata value.
	 * Note that this may return <code>null</code>.
	 * 
	 * @param key
	 * @return
	 */
	public String getMetadataValue(final String key) {
		return metadata.get(key);
	}

	/**
	 * Store a metadata value.
	 * This is intended as storage of short key-value pairs.
	 * Extended text should be stored under <code>setDescription</code>.
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public String putMetadataValue(final String key, final String value) {
		return metadata.put(key, value);
	}
	
	/**
	 * Check if a metadata value is present for a specified key.
	 * 
	 * @param key
	 * @return <code>true</code> if <code>getDescription()</code> does not return null or an empty string, <code>false</code> otherwise.
	 */
	public boolean containsMetadata(final String key) {
		return metadata.containsKey(key);
	}
	
	/**
	 * Get a description; this is free text describing the image.
	 * @return
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Set the description.
	 * 
	 * @see #getDescription
	 * @param description
	 */
	public void setDescription(final String description) {
		this.description = description;
	}
	
	/**
	 * Check if a description is present.
	 * 
	 * @return <code>true</code> if <code>getDescription()</code> does not return null or an empty string, <code>false</code> otherwise.
	 */
	public boolean hasDescription() {
		return this.description != null && !this.description.isEmpty();
	}
	
	/**
	 * Remove all metadata.
	 */
	public void clearMetadata() {
		this.metadata.clear();
	}
	
	/**
	 * Get an unmodifiable view of the underlying metadata map.
	 * 
	 * @return
	 */
	public Map<String, String> getMetadataMap() {
		return Collections.unmodifiableMap(metadata);
	}
	
	/**
	 * Get an unmodifiable collection of the metadata map's keys.
	 * 
	 * @return
	 */
	public Collection<String> getMetadataKeys() {
		return Collections.unmodifiableSet(metadata.keySet());
	}
	
	/**
	 * Get a formatted string representation of the metadata map's contents.
	 * 
	 * @return
	 */
	public String getMetadataSummaryString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		for (Entry<String, String> entry : metadata.entrySet()) {
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