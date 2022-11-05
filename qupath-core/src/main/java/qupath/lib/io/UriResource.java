/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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


package qupath.lib.io;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.slf4j.LoggerFactory;

import qupath.lib.common.LogTools;

/**
 * Interface for objects that depend upon URIs.
 * Examples include images, where the URI refer to the image file or URL, or prediction models.
 */
public interface UriResource {
	
	
	public default Collection<URI> getUris() throws IOException {
		LogTools.warnOnce(LoggerFactory.getLogger(getClass()), "getUris() is deprecated in v0.4.0 - use getURIs() instead");
		return getURIs();
	}
	
	public default boolean updateUris(Map<URI, URI>  replacements) throws IOException {
		LogTools.warnOnce(LoggerFactory.getLogger(getClass()), "updateUris is deprecated in v0.4.0 - use updateURIs instead");
		return updateURIs(replacements);
	}

	
	/**
	 * Get all the URIs required for this resource. This is often an empty or singleton list.
	 * @return the required URIs
	 * @throws IOException
	 * @since v0.4.0
	 */
	public Collection<URI> getURIs() throws IOException;
	
	/**
	 * Update the specified URIs.
	 * <p>
	 * <b>Warning!</b> This should be used sparingly, particularly for objects that are otherwise immutable.
	 * It is intended <i>only</i> for correcting paths that have become invalid (e.g. because of files being relocated 
	 * or transferred between computers) <i>before</i> first use of the object.
	 * It should <b>not</b> be used to manipulate an object after construction. 
	 * Indeed, some implementations may throw an {@link UnsupportedOperationException} if called too late.
	 * 
	 * @param replacements replacement map, where the key gives the current URI and the value gives its replacement.
	 * @return true if URIs were changed, false otherwise
	 * @throws IOException
	 * @since v0.4.0
	 */
	public boolean updateURIs(Map<URI, URI>  replacements) throws IOException;

	
	
}