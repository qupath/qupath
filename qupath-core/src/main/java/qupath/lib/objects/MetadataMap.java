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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for storing metadata key/value pairs.
 * <p>
 * Currently wraps a LinkedHashMap, but this implementation may change.
 * <p>
 * Serializes itself efficiently by using Object arrays.
 * <p>
 * Warning: everything that goes into the map should be serializable, or bad things may happen...
 * in practice, only Strings and Numbers should be used.  As such, this class isn't exposed publicly to the world.
 * 
 * @author Pete Bankhead
 *
 */
class MetadataMap implements Map<String, String>, Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	private Map<String, String> map = new LinkedHashMap<>();
	
	public MetadataMap() {}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public String get(Object key) {
		return map.get(key);
	}

	@Override
	public String put(String key, String value) {
		return map.put(key, value);
	}

	@Override
	public String remove(Object key) {
		return map.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends String> m) {
		map.putAll(m);
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Set<String> keySet() {
		return map.keySet();
	}

	@Override
	public Collection<String> values() {
		return map.values();
	}

	@Override
	public Set<java.util.Map.Entry<String, String>> entrySet() {
		return map.entrySet();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(Integer.valueOf(1));
		out.writeObject(map.keySet().toArray());
		out.writeObject(map.values().toArray());
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		// For now, ignore version (since we don't have multiple versions to deal with)
		in.readObject();
		Object[] keys = (Object[])in.readObject();
		Object[] values = (Object[])in.readObject();
		for (int i = 0; i < keys.length; i++) {
			map.put((String)keys[i], (String)values[i]);
		}
	}
	
	

}