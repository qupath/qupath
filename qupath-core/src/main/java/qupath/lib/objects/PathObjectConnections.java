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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data structure for storing multiple PathObjectConnectionGroups.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectConnections implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(PathObjectConnections.class);
	
	private List<PathObjectConnectionGroup> connections = new ArrayList<>();
	
	/**
	 * Default constructor.
	 */
	public PathObjectConnections() {}
	
	/**
	 * Get all the connections to a specified PathObject.
	 * <p>
	 * This will iterate through all the groups and return the connections from the first group 
	 * containing this object.
	 * <p>
	 * If the object may be contained in multiple groups, then the groups will need to be requested 
	 * instead with getConnectionGroups(), and searched elsewhere.
	 * 
	 * @param pathObject
	 * @return
	 */
	public List<PathObject> getConnections(final PathObject pathObject) {
		for (PathObjectConnectionGroup group : connections) {
			if (group.containsObject(pathObject))
				return group.getConnectedObjects(pathObject);
		}
		return Collections.emptyList();
	}
	
	/**
	 * Get an unmodifiable list containing all connections groups.
	 * @return
	 */
	public List<PathObjectConnectionGroup> getConnectionGroups() {
		return Collections.unmodifiableList(connections);
	}
	
	/**
	 * Add a new connections group.
	 * @param group
	 */
	public void addGroup(final PathObjectConnectionGroup group) {
		connections.add(group);
	}

	/**
	 * Remove a connections group.
	 * @param group
	 * @return
	 */
	public boolean removeGroup(final PathObjectConnectionGroup group) {
		return connections.remove(group);
	}
	
	/**
	 * Returns true if the group is empty.
	 * @return
	 */
	public boolean isEmpty() {
		return connections.isEmpty();
	}
	
	/**
	 * Clear all existing connections.
	 */
	public void clear() {
		connections.clear();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(1);
		out.writeObject(connections);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		long version = in.readLong();
		if (version != 1) {
			logger.warn("Unexpected {} version number {}", PathObjectConnections.class, version);
		}
		List<PathObjectConnectionGroup> list = (List<PathObjectConnectionGroup>)in.readObject();
		connections.addAll(list);
	}

}
