/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.projects;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.interfaces.MinimalMetadataStore;
import qupath.lib.io.UriResource;
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
public interface ProjectImageEntry<T> extends UriResource, MinimalMetadataStore {
	
	/**
	 * Get a unique ID to represent this entry.
	 * @return
	 */
	String getID();
	
	/**
	 * Set the image name for this project entry.
	 * 
	 * @param name
	 */
	void setImageName(String name);

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
	String getImageName();
	
	/**
	 * Get the original image name, without any randomization.  Most UI elements should prefer {@link #getImageName} to 
	 * ensure that the randomization does its job.
	 * 
	 * @return
	 */
	String getOriginalImageName();
		
	
	/**
	 * Get a path to the data for this image entry, or null if this entry is not 
	 * stored on the local file system.
	 * <p>
	 * If not null, the path may be a file or a directory and is <i>not</i> guaranteed to exist. 
	 * Rather, it represents where the data for this entry either is or would be stored.
	 * 
	 * @return
	 */
	Path getEntryPath();
	
	
	/**
	 * Remove a metadata value.
	 * 
	 * @param key
	 * @return
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default String removeMetadataValue(final String key) {
		return getMetadata().remove(key);
	}
	
	/**
	 * Request a metadata value.
	 * <p>
	 * Note that this may return <code>null</code>.
	 * 
	 * @param key
	 * @return
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default String getMetadataValue(final String key) {
		return getMetadata().get(key);
	}

	/**
	 * Store a metadata value.
	 * <p>
	 * This is intended as storage of short key-value pairs.
	 * Extended text should be stored under <code>setDescription</code>.
	 * 
	 * @param key
	 * @param value
	 * @return
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default String putMetadataValue(final String key, final String value) {
		return getMetadata().put(key, value);
	}
	
	/**
	 * Check if a metadata value is present for a specified key.
	 * 
	 * @param key
	 * @return <code>true</code> if <code>getDescription()</code> does not return null or an empty string, <code>false</code> otherwise.
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default boolean containsMetadata(final String key) {
		return getMetadata().containsKey(key);
	}
	
	/**
	 * Get a description; this is free text describing the image.
	 * @return
	 */
	String getDescription();
	
	/**
	 * Set the description.
	 * 
	 * @see #getDescription
	 * @param description
	 */
	void setDescription(final String description);
	
	/**
	 * Remove all metadata.
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default void clearMetadata() {
		getMetadata().clear();
	}
	
	/**
	 * Get an unmodifiable view of the underlying metadata map.
	 * 
	 * @return
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default Map<String, String> getMetadataMap() {
		return Collections.unmodifiableMap(getMetadata());
	}
	
	/**
	 * Get an unmodifiable collection of the metadata map's keys.
	 * 
	 * @return
	 * @deprecated v0.6.0, use {@link #getMetadata()} instead to directly access the metadata.
	 */
	@Deprecated
	default Collection<String> getMetadataKeys() {
		return getMetadata().keySet();
	}

	/**
	 * Get a {@link ServerBuilder} that can be used to open this image.
	 * 
	 * @return
	 */
	ServerBuilder<T> getServerBuilder();
	
	/**
	 * Read the {@link ImageData} associated with this entry, or create a new ImageData if none is currently present.
	 * <p>
	 * If the full data is not needed, but rather only the objects {@link #readHierarchy()} can be much faster.
	 * 
	 * @return
	 * @throws IOException 
	 * 
	 * @see #readHierarchy()
	 */
	ImageData<T> readImageData() throws IOException;
	
	/**
	 * Save the {@link ImageData} for this entry using the default storage location for the project.
	 * @param imageData 
	 * @throws IOException 
	 */
	void saveImageData(ImageData<T> imageData) throws IOException;
	
	/**
	 * Read the {@link PathObjectHierarchy} for this entry, or return an empty hierarchy if none is available.
	 * @return
	 * @throws IOException 
	 * 
	 * @see #readImageData()
	 * @see #hasImageData()
	 */
	PathObjectHierarchy readHierarchy() throws IOException;
	
	/**
	 * Check if this entry has saved {@link ImageData} already available.
	 * 
	 * @return
	 */
	boolean hasImageData();
	
	/**
	 * Get a summary string representing this image entry.
	 * @return
	 */
	String getSummary();
	
	/**
	 * Request a thumbnail for the image.
	 * 
	 * @return a thumbnail if one has already been set, otherwise null.
	 * @throws IOException
	 */
	T getThumbnail() throws IOException;
	
	/**
	 * Set a thumbnail for the image. This will replace any existing thumbnail.
	 * 
	 * @param img
	 * @throws IOException
	 */
	void setThumbnail(T img) throws IOException;
	
	/**
	 * Get a formatted string representation of the metadata map's contents.
	 * 
	 * @return
	 */
	default String getMetadataSummaryString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		for (Entry<String, String> entry : getMetadata().entrySet()) {
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
	Manager<ImageServer<T>> getImages();

	/**
	 * Returns a modifiable set containing tag values.
	 * <p>
	 * The returned set may or may not be thread-safe. Implementing classes must
	 * document the thread-safeness of the set.
	 *
	 * @return the set of tags of this entry
	 */
	Set<String> getTags();
}