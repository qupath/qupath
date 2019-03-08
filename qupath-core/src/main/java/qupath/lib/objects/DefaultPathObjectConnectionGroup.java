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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.helpers.PathObjectTools;
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
	
	public static final String KEY_OBJECT_CONNECTIONS = "OBJECT_CONNECTIONS";
	
	public DefaultPathObjectConnectionGroup() {}
	
	public DefaultPathObjectConnectionGroup(final Collection<PathObject> pathObjects) {
		pathObjects.stream().forEach(p -> map.put(p, new ObjectConnector(p)));
	}
	
	public DefaultPathObjectConnectionGroup(final PathObjectConnectionGroup connections) {
		connections.getPathObjects().stream().forEach(p -> map.put(p, new ObjectConnector(p, connections.getConnectedObjects(p))));
	}
	
	public void addConnection(final PathObject pathObject1, final PathObject pathObject2) {
		
		ObjectConnector connector1 = map.get(pathObject1);
		if (connector1 == null) {
			connector1 = new ObjectConnector(pathObject1);
			map.put(pathObject1, connector1);
		}
		connector1.addConnection(pathObject2);

		ObjectConnector connector2 = map.get(pathObject2);
		if (connector2 == null) {
			connector2 = new ObjectConnector(pathObject2);
			map.put(pathObject2, connector2);
		}
		connector2.addConnection(pathObject1);
		
	}
	
	public void breakConnection(final PathObject pathObject1, final PathObject pathObject2) {
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
	
	
	public void sortByDistance() {
		for (ObjectConnector connector : map.values())
			connector.sortConnectionsByDistance();
	}
	
	public static double centroidDistance(final PathObject pathObject1, final PathObject pathObject2) {
		return Math.sqrt(centroidDistanceSquared(pathObject1.getROI(), pathObject2.getROI()));
	}
	
	public static double centroidDistance(final ROI roi1, final ROI roi2) {
		return Math.sqrt(centroidDistanceSquared(roi1, roi2));
	}
	
	public static double centroidDistanceSquared(final PathObject pathObject1, final PathObject pathObject2) {
		return centroidDistanceSquared(PathObjectTools.getROI(pathObject1, true), PathObjectTools.getROI(pathObject2, true));
	}
	
	public static double centroidDistanceSquared(final ROI roi1, final ROI roi2) {
		double dx = roi1.getCentroidX() - roi2.getCentroidX();
		double dy = roi1.getCentroidY() - roi2.getCentroidY();
		return dx*dx + dy*dy;
	}
	
	
	
	public static class MeasurementNormalizer {
		
		static Map<String, RunningStatistics> statsMap = new HashMap<>();
		
		public MeasurementNormalizer(final Collection<PathObject> pathObjects) {
			for (PathObject pathObject : pathObjects) {
				MeasurementList list = pathObject.getMeasurementList();
				for (int i = 0; i < list.size(); i++) {
					double value = list.getMeasurementValue(i);
					if (Double.isNaN(value))
						continue;
					String name = list.getMeasurementName(i);
					RunningStatistics stats = statsMap.get(name);
					if (stats == null) {
						stats = new RunningStatistics();
						statsMap.put(name, stats);
					}
					stats.addValue(value);
				}
			}
		}
		
		public Collection<String> getAvailableMeasurements() {
			return statsMap.keySet();
		}
				
		
		public double[] normalizeMeanStdDev(final PathObject pathObject, final List<String> measurements, final double nanValue, double[] results) {
			if (results == null || results.length < measurements.size())
				results = new double[measurements.size()];
			
			MeasurementList list = pathObject.getMeasurementList();
			for (int i = 0; i < measurements.size(); i++) {
				String name = measurements.get(i);
				RunningStatistics stats = statsMap.get(name);
				double val = list.getMeasurementValue(name);
				if (Double.isNaN(val))
					results[i] = nanValue;
				else if (stats == null)
					results[i] = val;
				else
					results[i] = (val - stats.getMean()) / stats.getStdDev();
			}
			
			return results;
		}
		
		
		public double getMeasurementDistanceSquared(final PathObject pathObject1, final PathObject pathObject2, final List<String> measurements) {
			// Get normalized values, replacing NaNs with zero (i.e. the mean)
			double[] m1 = normalizeMeanStdDev(pathObject1, measurements, 0, null);
			double[] m2 = normalizeMeanStdDev(pathObject2, measurements, 0, null);
			
			double sumSquares = 0;
			for (int i = 0; i < measurements.size(); i++) {
				double d = m1[i] - m2[i];
				sumSquares += d*d;
			}
			
			return sumSquares;
		}

		public double getMeasurementDistance(final PathObject pathObject1, final PathObject pathObject2, final List<String> measurements) {
			return Math.sqrt(getMeasurementDistanceSquared(pathObject1, pathObject2, measurements));
		}

		
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
