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

import java.util.Map;
import java.util.Set;

/**
 * Interface that may be used to indicate that a PathObject (or other object) can store metadata.
 * <p>
 * Implementing classes should ensure that entries are stored in insertion order.
 * 
 * @author Pete Bankhead
 *
 */
public interface MetadataStore {
	
	/**
	 * Store a new metadata value.
	 * @param key
	 * @param value
	 * @return
	 */
	public Object putMetadataValue(final String key, final String value);
	
	/**
	 * Get a metadata value, cast as a String if possible.
	 * @param key
	 * @return
	 */
	public String getMetadataString(final String key);
	
	/**
	 * Get a metadata value of any kind.
	 * 
	 * @param key
	 * @return
	 */
	public Object getMetadataValue(final String key);
	
	/**
	 * Get all metadata keys.
	 * 
	 * @return
	 */
	public Set<String> getMetadataKeys();
	
	/**
	 * Returns an unmodifiable map containing the metadata.
	 * 
	 * @return
	 */
	public Map<String, String> getMetadataMap();
	
}
