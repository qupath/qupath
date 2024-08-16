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

package qupath.lib.objects;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for storing metadata key/value pairs.
 * <p>
 * This currently wraps a synchronized {@link LinkedHashMap}, but the implementation may change.
 * <p>
 * The main features of this class are:
 * <ul>
 *     <li>It can be serialized reasonably efficiently using Object arrays</li>
 *     <li>It tries to minimize overhead by creating its internal map implementation lazily,
 *     only whenever anything is being added.</li>
 * </ul>
 */
class MetadataMap implements Map<String, String>, Externalizable {
	
	private static final Logger logger = LoggerFactory.getLogger(MetadataMap.class);
	
	private static final long serialVersionUID = 1L;
	
	private Map<String, String> map;
	
	public MetadataMap() {}

	@Override
	public int size() {
		return map == null ? 0 : getMapInternal().size();
	}

	@Override
	public boolean isEmpty() {
		return map == null || getMapInternal().isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return getMapInternal().containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return getMapInternal().containsValue(value);
	}

	@Override
	public String get(Object key) {
		return getMapInternal().get(key);
	}

	@Override
	public String put(String key, String value) {
		return getMapInternalOrCreate().put(key, value);
	}

	@Override
	public String remove(Object key) {
		if (map == null)
			return null;
		return map.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends String> m) {
		if (m.isEmpty())
			return;
		getMapInternalOrCreate().putAll(m);
	}

	@Override
	public void clear() {
		if (map == null)
			return;
		map.clear();
	}

	@Override
	public Set<String> keySet() {
		// Returned set does only needs to support removal, so Collections.emptyMap() is ok
		return getMapInternal().keySet();
	}

	@Override
	public Collection<String> values() {
		// Returned set does only needs to support removal, so Collections.emptyMap() is ok
		return getMapInternal().values();
	}

	@Override
	public Set<java.util.Map.Entry<String, String>> entrySet() {
		// Returned set does only needs to support removal, so Collections.emptyMap() is ok
		return getMapInternal().entrySet();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(Integer.valueOf(1));
		var map = getMapInternal();
		out.writeObject(map.keySet().toArray());
		out.writeObject(map.values().toArray());
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		// For now, ignore version (since we don't have multiple versions to deal with)
		in.readObject();
		Object[] keys = (Object[])in.readObject();
		Object[] values = (Object[])in.readObject();
		if (keys.length > 0) {
			var mapInternal = getMapInternalOrCreate();
			for (int i = 0; i < keys.length; i++) {
				mapInternal.put((String)keys[i], (String)values[i]);
			}
		}
	}
	
	private Map<String, String> getMapInternal() {
		if (map == null)
			return Collections.emptyMap();
		else
			return map;
	}
	
	private Map<String, String> getMapInternalOrCreate() {
		if (map == null) {
			logger.trace("Creating MetadataMap");
			map = new LinkedHashMap<>();
		}
		return map;
	}
	
	@Override
	public String toString() {
		return getMapInternal().toString();
	}
	

}