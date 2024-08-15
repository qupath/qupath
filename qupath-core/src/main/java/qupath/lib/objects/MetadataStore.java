/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
 * Interface indicating that an object can store metadata.
 * <p>
 * Implementing classes should ensure that entries are stored in insertion order.
 * 
 * @author Pete Bankhead
 */
public interface MetadataStore {

	/**
	 * Returns a modifiable map containing the metadata.
	 * <p>
	 * The returned map may or may not be thread-safe. Implementing classes must
	 * document the thread-safeness of the map.
	 *
	 * @return the metadata of this store
	 */
	Map<String, String> getMetadata();
	
	/**
	 * Store a new metadata value.
	 *
	 * @param key
	 * @param value
	 * @return
	 * @deprecated as of v0.6.0. Use {@link #getMetadata()} with the {@link Map#put(Object, Object)}
	 * method instead
	 */
	@Deprecated
	default Object putMetadataValue(String key, String value) {
		return getMetadata().put(key, value);
	}
	
	/**
	 * Get a metadata value, cast as a String if possible.
	 * @param key
	 * @return
	 * @deprecated as of v0.6.0. Use {@link #getMetadata()} with the {@link Map#get(Object)}
	 * method instead
	 */
	@Deprecated
	default String getMetadataString(String key) {
		return getMetadata().get(key);
	}
	
	/**
	 * Get a metadata value of any kind.
	 * 
	 * @param key
	 * @return
	 * @deprecated as of v0.6.0. Use {@link #getMetadata()} with the {@link Map#get(Object)}
	 * method instead
	 */
	@Deprecated
	default Object getMetadataValue(String key) {
		return getMetadata().get(key);
	}
	
	/**
	 * Get all metadata keys.
	 * 
	 * @return
	 * @deprecated as of v0.6.0. Use {@link #getMetadata()} with the {@link Map#keySet()}
	 * method instead
	 */
	@Deprecated
	default Set<String> getMetadataKeys() {
		return getMetadata().keySet();
	}
	
}
