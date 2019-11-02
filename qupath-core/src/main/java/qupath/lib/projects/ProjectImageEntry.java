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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ResourceManager.Manager;

/**
 * Class to represent an image entry within a project.
 * <p>
 * This stores the path to the image, and some optional metadata.
 * 
 * @author Pete Bankhead
 *
 * @param <T> Depends upon the project used; typically BufferedImage for QuPath
 */
public interface ProjectImageEntry<T> {
	
//	/**
//	 * Get the path used to represent this image, which can be used to construct an <code>ImageServer</code>.
//	 * 
//	 * @return
//	 */
//	public String getServerPath();
	
	/**
	 * Get a unique ID to represent this entry.
	 * @return
	 */
	public String getID();
	
	/**
	 * Set the image name for this project entry.
	 * 
	 * @param name
	 */
	public void setImageName(String name);

	/**
	 * Get a name that may be used for this entry.
	 * <p>
	 * This may be derived automatically from the server path, or set explicitly to be something else. 
	 * It may also be randomized to support blinded analysis.
	 * 
	 * @return
	 * 
	 * @see #getOriginalImageName()
	 * @see qupath.lib.projects.Project#setMaskImageNames(boolean)
	 * @see qupath.lib.projects.Project#getMaskImageNames()
	 */
	public String getImageName();
	
	/**
	 * Get the original image name, without any randomization.  Most UI elements should prefer {@link #getImageName} to 
	 * ensure that the randomization does its job.
	 * 
	 * @return
	 */
	public String getOriginalImageName();
		
	
	/**
	 * Get a path to the data for this image entry, or null if this entry is not 
	 * stored on the local file system.
	 * <p>
	 * If not null, the path may be a file or a directory and is <i>not</i> guaranteed to exist. 
	 * Rather, it represents where the data for this entry either is or would be stored.
	 * 
	 * @return
	 */
	public Path getEntryPath();
	
	
	/**
	 * Remove a metadata value.
	 * 
	 * @param key
	 * @return
	 */
	public String removeMetadataValue(final String key);
	
	/**
	 * Request a metadata value.
	 * <p>
	 * Note that this may return <code>null</code>.
	 * 
	 * @param key
	 * @return
	 */
	public String getMetadataValue(final String key);

	/**
	 * Store a metadata value.
	 * <p>
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
	 * Get a {@link ServerBuilder} that can be used to open this image.
	 * 
	 * @return
	 */
	public ServerBuilder<T> getServerBuilder();
	
	/**
	 * Read the {@link ImageData} associated with this entry, or create a new ImageData if none is currently present.
	 * <p>
	 * If the full data is not needed, but rather only the objects {@link #readHierarchy()} can be much faster.
	 * 
	 * @return
	 * 
	 * @see #readHierarchy()
	 */
	public ImageData<T> readImageData() throws IOException;
	
	/**
	 * Save the {@link ImageData} for this entry using the default storage location for the project.
	 */
	public void saveImageData(ImageData<T> imageData) throws IOException;
	
	/**
	 * Read the {@link PathObjectHierarchy} for this entry, or return an empty hierarchy if none is available.
	 * @return
	 * 
	 * @see #readImageData()
	 * @see #hasImageData()
	 */
	public PathObjectHierarchy readHierarchy() throws IOException;
	
	/**
	 * Check if this entry has saved {@link ImageData} already available.
	 * 
	 * @return
	 */
	public boolean hasImageData();
	
	/**
	 * Get a summary string representing this image entry.
	 * @return
	 */
	public String getSummary();
	
	/**
	 * Request a thumbnail for the image.
	 * 
	 * @return a thumbnail if one has already been set, otherwise null.
	 * @throws IOException
	 */
	public T getThumbnail() throws IOException;
	
	/**
	 * Set a thumbnail for the image. This will replace any existing thumbnail.
	 * 
	 * @param img
	 * @throws IOException
	 */
	public void setThumbnail(T img) throws IOException;	
	
	/**
	 * Get a collection of the URIs required by this project's ImageServer.
	 * <p>
	 * The purpose of this is to help query if they can be found. They might not be 
	 * e.g. if the images have been moved.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Collection<URI> getServerURIs() throws IOException;
	
	/**
	 * Update the URIs for the server (optional operation).
	 * 
	 * @param replacements a map with current URIs as keys, and desired URIs as values.
	 * @return true if changes were made
	 * @throws IOException
	 */
	public boolean updateServerURIs(Map<URI, URI> replacements) throws IOException;
	
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
	
	
	/**
	 * Access additional images associated with the project entry, e.g. pixel classifications or
	 * aligned slides.
	 * 
	 * @return
	 */
	public Manager<ImageServer<T>> getImages();
	
	

}