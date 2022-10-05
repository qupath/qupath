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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * Simple, default implementation of {@link PathObjectConnectionGroup}.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultPathObjectConnectionGroup implements PathObjectConnectionGroup, Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultPathObjectConnectionGroup.class);
	
	private Map<PathObject, ObjectConnector> map = new LinkedHashMap<>();
	
	private transient SpatialIndex index;
	
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

	@Override
	public Collection<PathObject> getPathObjects() {
		return map.keySet();
	}

	@Override
	public List<PathObject> getConnectedObjects(PathObject pathObject) {
		ObjectConnector connector = map.get(pathObject);
		if (connector == null)
			return Collections.emptyList();
		return connector.getConnections();
	}
	
	private static double centroidDistanceSquared(final PathObject pathObject1, final PathObject pathObject2) {
		return centroidDistanceSquared(PathObjectTools.getROI(pathObject1, true), PathObjectTools.getROI(pathObject2, true));
	}
	
	private static double centroidDistanceSquared(final ROI roi1, final ROI roi2) {
		double dx = roi1.getCentroidX() - roi2.getCentroidX();
		double dy = roi1.getCentroidY() - roi2.getCentroidY();
		return dx*dx + dy*dy;
	}
	
	
	@Override
	public Collection<PathObject> getPathObjectsForRegion(ImageRegion region) {
		if (index == null) {
			synchronized(this) {
				if (index == null)
					index = buildIndex();
			}
		}
		var envelope = getEnvelope(region);
		return ((Collection<PathObject>)index.query(envelope))
				.stream()
				.filter(p -> p.getROI().getZ() == region.getZ() && p.getROI().getT() == region.getT())
				.collect(Collectors.toSet());
	}
	
	
	private SpatialIndex buildIndex() {
		long startTime = System.currentTimeMillis();
		var index = new Quadtree();
		
		for (var entry : map.entrySet()) {
			var pathObject = entry.getKey();
			var envelope = getEnvelope(pathObject.getROI());
			
			var connector = entry.getValue();
			if (connector != null) {
				var connectedObjects = connector.getConnections();
				for (var connected : connectedObjects) {
					envelope.expandToInclude(getEnvelope(connected.getROI()));
				}
			}
			
			index.insert(envelope, pathObject);
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Spatial index built in {} ms", endTime - startTime);
		return index;
	}
	
	
	private static Envelope getEnvelope(ImageRegion region) {
		return new Envelope(region.getMinX(), region.getMaxX(), region.getMinY(), region.getMaxY());
	}
	
	
	private static Envelope getEnvelope(ROI roi) {
		return new Envelope(roi.getBoundsX(), roi.getBoundsX() + roi.getBoundsWidth(),
				roi.getBoundsY(), roi.getBoundsY() + roi.getBoundsHeight());
	}
	
	
	
	
	static class ObjectConnector implements Externalizable {
		
		private static final long serialVersionUID = 1L;
		
		private PathObject pathObject;
		private List<PathObject> connections = new ArrayList<>();
		
		private transient List<PathObject> connectionsUnmodifiable;
		
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
		
		public List<PathObject> getConnectionsUnmodifiable() {
			if (connectionsUnmodifiable == null)
				connectionsUnmodifiable = Collections.unmodifiableList(connections);
			return connectionsUnmodifiable;
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
