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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

/**
 * Simple, default implementation of PathObjectConnectionGroup.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultPathObjectConnectionGroup implements PathObjectConnectionGroup, Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultPathObjectConnectionGroup.class);
	
	private Map<PathObject, ObjectConnector> map = new LinkedHashMap<>();
	
	/**
	 * Key to use when storing object connections as a property of an ImageData object.
	 */
	public static final String KEY_OBJECT_CONNECTIONS = "OBJECT_CONNECTIONS";
	
	/**
	 * Default constructor.
	 */
	public DefaultPathObjectConnectionGroup() {}
	
	/**
	 * Create a connections group, copying the connections from an existing group.
	 * This may be useful if the other PathObjectConnectionGroup is not itself serializable.
	 * @param connections
	 */
	public DefaultPathObjectConnectionGroup(final PathObjectConnectionGroup connections) {
		connections.getPathObjects().stream().forEach(p -> map.put(p, new ObjectConnector(p, connections.getConnectedObjects(p))));
	}
	
	private void breakConnection(final PathObject pathObject1, final PathObject pathObject2) {
		if (map.get(pathObject1).breakConnection(pathObject2))
			map.get(pathObject2).breakConnection(pathObject1);		
	}

	@Override
	public Collection<PathObject> getPathObjects() {
		return map.keySet();
	}

	@Override
	public List<PathObject> getConnectedObjects(PathObject pathObject) {
		ObjectConnector connector = map.get(pathObject);
		if (connector == null)
			return Collections.emptyList();
		return Collections.unmodifiableList(connector.getConnections());
	}
	
	
	private void sortByDistance() {
		for (ObjectConnector connector : map.values())
			connector.sortConnectionsByDistance();
	}
	
	private static double centroidDistance(final PathObject pathObject1, final PathObject pathObject2) {
		return Math.sqrt(centroidDistanceSquared(pathObject1.getROI(), pathObject2.getROI()));
	}
	
	private static double centroidDistance(final ROI roi1, final ROI roi2) {
		return Math.sqrt(centroidDistanceSquared(roi1, roi2));
	}
	
	private static double centroidDistanceSquared(final PathObject pathObject1, final PathObject pathObject2) {
		return centroidDistanceSquared(PathObjectTools.getROI(pathObject1, true), PathObjectTools.getROI(pathObject2, true));
	}
	
	private static double centroidDistanceSquared(final ROI roi1, final ROI roi2) {
		double dx = roi1.getCentroidX() - roi2.getCentroidX();
		double dy = roi1.getCentroidY() - roi2.getCentroidY();
		return dx*dx + dy*dy;
	}
	
	
	
	
	static class ObjectConnector implements Externalizable {
		
		private static final long serialVersionUID = 1L;
		
		private PathObject pathObject;
		private List<PathObject> connections = new ArrayList<>();
		
		public ObjectConnector() {}
		
		ObjectConnector(final PathObject pathObject) {
			this.pathObject = pathObject;
		}
		
		ObjectConnector(final PathObject pathObject, final Collection<PathObject> connections) {
			this.pathObject = pathObject;
			this.connections.addAll(connections);
		}
		
		public boolean addConnection(final PathObject newObject) {
			return connections.add(newObject);
		}
		
		public boolean breakConnection(final PathObject newObject) {
			return connections.remove(newObject);
		}
		
		public PathObject getPathObject() {
			return pathObject;
		}
		
		public List<PathObject> getConnections() {
			return connections;
		}
		
		public void sortConnectionsByDistance() {
			Collections.sort(connections, (o1, o2) -> Double.compare(
					centroidDistanceSquared(pathObject, o1),
					centroidDistanceSquared(pathObject, o2)
					));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeLong(1);
			out.writeObject(pathObject);
			out.writeObject(connections);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			long version = in.readLong();
			if (version != 1) {
				logger.warn("Unexpected {} version number {}", ObjectConnector.class, version);
			}
			this.pathObject = (PathObject)in.readObject();
			this.connections = (List<PathObject>)in.readObject();
		}
		
	}



	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(1);
		out.writeObject(map);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		long version = in.readLong();
		if (version != 1) {
			logger.warn("Unexpected {} version number {}", DefaultPathObjectConnectionGroup.class, version);
		}
		Map<PathObject, ObjectConnector> readMap = (Map<PathObject, ObjectConnector>)in.readObject();
		map.putAll(readMap);
	}

	@Override
	public boolean containsObject(PathObject pathObject) {
		return map.containsKey(pathObject);
	}
	
}
