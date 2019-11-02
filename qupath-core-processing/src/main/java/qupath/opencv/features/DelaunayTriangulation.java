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

package qupath.opencv.features;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.bytedeco.opencv.opencv_core.Point2f;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_imgproc.Subdiv2D;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;


/**
 * Compute Delaunay triangulation using OpenCV.
 * 
 * @author Pete Bankhead
 *
 */
public class DelaunayTriangulation implements PathObjectConnectionGroup {
	
	private double distanceThreshold = Double.NaN;
	private boolean limitByClass = false;
	
	private Map<Integer, PathObject> vertexMap;
	private Map<PathObject, DelaunayNode> nodeMap;
//	private Subdiv2D subdiv;
	
	/**
	 * Compute Delaunay triangulation - optionally omitting links above a fixed distance.
	 * 
	 * @param pathObjects
	 * @param distanceThresholdPixels - Note, this is in *pixels* (and not scaled according to pixelWidth &amp; pixelHeight)
	 */
	public DelaunayTriangulation(final List<PathObject> pathObjects, final double pixelWidth, final double pixelHeight, final double distanceThresholdPixels, final boolean limitByClass) {
		this.distanceThreshold = distanceThresholdPixels;
		this.limitByClass = limitByClass;
		computeDelaunay(pathObjects, pixelWidth, pixelHeight);
		
		Collection<String> measurements = PathClassifierTools.getAvailableFeatures(pathObjects);
		for (String name : measurements) {
			RunningStatistics stats = new RunningStatistics();
			pathObjects.stream().forEach(p -> stats.addValue(p.getMeasurementList().getMeasurementValue(name)));
		}
	}
	
	
	void getConnectedNodesRecursive(final PathObject pathObject, final Set<PathObject> set) {
		if (set.add(pathObject)) {
			for (PathObject next : getConnectedNodes(pathObject, null)) {
				getConnectedNodesRecursive(next, set);
			}
		}
	}
	
	
	@Override
	public List<PathObject> getConnectedObjects(final PathObject pathObject) {
		DelaunayNode node = nodeMap.get(pathObject);
		if (node == null)
			return Collections.emptyList();
		return node.getNodeList().stream().map(n -> n.getPathObject()).collect(Collectors.toList());
	}
	
	
	@Override
	public Collection<PathObject> getPathObjects() {
		return nodeMap.keySet();
	}

	
	
	/**
	 * Get the ROI for an object - preferring a nucleus ROI for a cell, if possible.
	 * 
	 * @param pathObject
	 * @return
	 */
	static ROI getROI(final PathObject pathObject) {
		if (pathObject instanceof PathCellObject) {
			ROI roi = ((PathCellObject)pathObject).getNucleusROI();
			if (roi != null && !Double.isNaN(roi.getCentroidX()))
				return roi;
		}
		return pathObject.getROI();
	}
	
	
	
	void computeDelaunay(final List<PathObject> pathObjectList, final double pixelWidth, final double pixelHeight) {
		
		if (pathObjectList.size() <= 2)
			return;
		
		this.vertexMap = new HashMap<>(pathObjectList.size(), 1f);
		
		// Extract the centroids
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		List<Point2f> centroids = new ArrayList<>(pathObjectList.size());
		for (PathObject pathObject : pathObjectList) {
			ROI pathROI = null;
			
			// First, try to get a nucleus ROI if we have a cell - otherwise just get the normal ROI
			pathROI = getROI(pathObject);

			// Check if we have a ROI at all
			if (pathROI == null) {
				centroids.add(null);
				continue;
			}
			double x = pathROI.getCentroidX();
			double y = pathROI.getCentroidY();
			if (Double.isNaN(x) || Double.isNaN(y)) {
				centroids.add(null);
				continue;
			}
			if (x < minX)
				minX = x;
			else if (x > maxX)
				maxX = x;
			if (y < minY)
				minY = y;
			else if (y > maxY)
				maxY = y;
			
			centroids.add(new Point2f((float)x, (float)y));
		}
		
		// Create Delaunay triangulation, updating vertex map
		Subdiv2D subdiv = new Subdiv2D();
		Rect bounds = new Rect((int)minX-1, (int)minY-1, (int)(maxX-minX)+100, (int)(maxY-minY)+100);
		subdiv.initDelaunay(bounds);
		for (int i = 0; i < centroids.size(); i++) {
			Point2f p = centroids.get(i);
			if (p == null)
				continue;
			int v = subdiv.insert(p);
			vertexMap.put(v, pathObjectList.get(i));
		}
		
		updateNodeMap(subdiv, pixelWidth, pixelHeight);
		
		
//		// Connect only the closest paired nodes
//		Map<DelaunayNode, Double> medianDistances = new HashMap<>();
//		for (DelaunayNode node : nodeMap.values()) {
//			medianDistances.put(node, node.medianDistance());
//		}
//		
//		for (DelaunayNode node : nodeMap.values()) {
//			if (node.nNeighbors() <= 2)
//				continue;
//			double distance = medianDistances.get(node);
//			Iterator<DelaunayNode> iter = node.nodeList.iterator();
//			while (iter.hasNext()) {
//				DelaunayNode node2 = iter.next();
//				if (distance(node, node2) >= distance) {
//					node2.nodeList.remove(node);
//					iter.remove();
//				}
//			}
//		}
		
		
//		// Optionally require a minimum number of connected nodes
//		List<DelaunayNode> toRemove = new ArrayList<>();
//		for (DelaunayNode node : nodeMap.values()) {
//			if (node.nNeighbors() <= 2) {
//				toRemove.add(node);
//			}
//		}
//		for (DelaunayNode node : toRemove) {
//			for (DelaunayNode node2 : node.nodeList)
//				node2.nodeList.remove(node);
//			node.nodeList.clear();
//		}
//		for (DelaunayNode node : nodeMap.values()) {
//			node.ensureDistancesUpdated();
//			node.ensureTrianglesCalculated();
//		}
	}
	
	
	
	void updateNodeMap(Subdiv2D subdiv, final double pixelWidth, final double pixelHeight) {
		if (subdiv == null)
			return;
		
		int[] firstEdgeArray = new int[1];
		
//		double distanceThreshold = 0; 
		boolean ignoreDistance = Double.isNaN(distanceThreshold) || Double.isInfinite(distanceThreshold) || distanceThreshold <= 0;
		
		DelaunayNodeFactory factory = new DelaunayNodeFactory(pixelWidth, pixelHeight);
		nodeMap = new HashMap<>(vertexMap.size(), 1f);
		for (Entry<Integer, PathObject> entry : vertexMap.entrySet()) {
			int v = entry.getKey();
			PathObject pathObject = entry.getValue();
			
			PathClass pathClass = pathObject.getPathClass() == null ? null : pathObject.getPathClass().getBaseClass();
			
//			// TODO: CHECK INTENSITY DIFFERENT THRESHOLD
//			String measurementName = "Nucleus: DAB OD mean";
//			double measurementDiffThreshold = 0.1;
//			double od = pathObject.getMeasurementList().getMeasurementValue(measurementName);
			
			
			subdiv.getVertex(v, firstEdgeArray);
			int firstEdge = firstEdgeArray[0];
			int edge = firstEdge;
			DelaunayNode node = factory.getNode(pathObject);
			while (true) {
				int edgeDest = subdiv.edgeDst(edge);
				PathObject destination = vertexMap.get(edgeDest);
				if (destination == null)
					break;
				
				
				boolean distanceOK = ignoreDistance || distance(getROI(pathObject), getROI(destination)) < distanceThreshold;
				boolean classOK = !limitByClass || pathClass == destination.getPathClass() || (destination.getPathClass() != null && destination.getPathClass().getBaseClass() == pathClass);
				
				if (distanceOK && classOK) {
					// Intensity test (works, but currently commented out)
//					if (Math.abs(od - destination.getMeasurementList().getMeasurementValue(measurementName)) < measurementDiffThreshold)
					DelaunayNode destinationNode = factory.getNode(destination);
					node.addEdge(destinationNode);
					
					destinationNode.addEdge(node);
				}

				// Unused code exploring how a similarity test could be included
//				if (ignoreDistance || distance(pathObject.getROI(), destination.getROI()) < distanceThreshold) {
//					MeasurementList m1 = pathObject.getMeasurementList();
//					MeasurementList m2 = destination.getMeasurementList();
//					double d2 = 0;
//					for (String name : new String[]{"Nucleus: Area", "Nucleus: DAB OD mean", "Nucleus: Eccentricity"}) {
//						double t1 = m1.getMeasurementValue(name);
//						double t2 = m2.getMeasurementValue(name);
//						double temp = ((t1 - t2) / (t1 + t2)) * 2;
//						d2 += temp*temp;
//					}
//					if (d2 < 1)
////					System.out.println(d2);
//						node.addEdge(factory.getNode(destination));
//				}

				edge = subdiv.getEdge(edge, Subdiv2D.NEXT_AROUND_ORG);
				if (edge == firstEdge)
					break;
			}
			Object previous = nodeMap.put(pathObject, node);
			assert previous == null;
		}
	}
	
	
	/**
	 * Get connected nodes.  Returned as a list where pairs are consecutive, i.e.
	 * get(i) links to get(i+1)
	 * (although get(i+1) doesn't necessarily link to get(i+2)...)
	 * 
	 * @param pathObjects
	 * @return
	 */
	@Deprecated
	public Collection<double[]> getConnectedNodes(final Collection<PathObject> pathObjects, Collection<double[]> connections) {
		if (connections == null)
			connections = new HashSet<>();
		if (nodeMap == null || pathObjects.isEmpty())
			return connections;
		for (PathObject temp : pathObjects) {
			DelaunayNode node = nodeMap.get(temp);
			if (node == null)
				continue;
			ROI roi = getROI(temp);
			double x1 = roi.getCentroidX();
			double y1 = roi.getCentroidY();
			for (DelaunayNode node2 : node.nodeList) {
				ROI roi2 = getROI(node2.getPathObject());
				double x2 = roi2.getCentroidX();
				double y2 = roi2.getCentroidY();
				if (x1 < x2 || (x1 == x2 && y1 <= y2))
					connections.add(new double[]{x1, y1, x2, y2});
				else
					connections.add(new double[]{x2, y2, x1, y1});
			}
		}
		return connections;
	}
	
	
	
//	/**
//	 * Get connected nodes.  Returned as a list were pairs are consecutive, i.e.
//	 * get(i) links to get(i+1)
//	 * (although get(i+1) doesn't necessarily link to get(i+2)...)
//	 * 
//	 * @param pathObjects
//	 * @return
//	 */
//	@Deprecated
//	public List<PathObject> getConnectedNodes(final Collection<PathObject> pathObjects) {
//		if (nodeMap == null || pathObjects.isEmpty())
//			return Collections.emptyList();
//		List<PathObject> connections = new ArrayList<>();
//		for (PathObject temp : pathObjects) {
//			DelaunayNode node = nodeMap.get(temp);
//			if (node == null)
//				continue;
//			for (DelaunayNode node2 : node.nodeList) {
//				connections.add(temp);
//				connections.add(node2.pathObject);
//			}
//		}
//		return connections;
//	}
	
	
	/**
	 * Get all the PathObjects immediately connected to the specified object, adding the points into a collection (or creating a new one).
	 * 
	 * @param pathObject
	 * @param list
	 * @return
	 */
	public Collection<PathObject> getConnectedNodes(final PathObject pathObject, Collection<PathObject> list) {
		if (list == null)
			list = new ArrayList<>();
		DelaunayNode node = nodeMap == null ? null : nodeMap.get(pathObject);
		if (node == null)
			return list;
		for (DelaunayNode temp : node.nodeList)
			list.add(temp.getPathObject());
		return list;
	}
	
	
	/**
	 * Get a list of PathObjects that are connected to each other in this triangulation.
	 * 
	 * Warning: This list is recomputed on every call, therefore references should be cached by the caller if necessary
	 * to avoid too much recomputation.
	 * 
	 * @return
	 */
	public List<Set<PathObject>> getConnectedClusters() {
		if (nodeMap == null || nodeMap.isEmpty())
			return Collections.emptyList();
		// Compute distinct clusters
		List<PathObject> toProcess = new ArrayList<>(nodeMap.keySet());
		List<Set<PathObject>> clusters = new ArrayList<>();
		while (!toProcess.isEmpty()) {
			Set<PathObject> inCluster = new HashSet<>();
			Deque<PathObject> toCheck = new ArrayDeque<>();
			PathObject next = toProcess.remove(toProcess.size()-1);
			toCheck.add(next);
			while (!toCheck.isEmpty()) {
				next = toCheck.pop();
				if (inCluster.add(next)) {
					toCheck.addAll(getConnectedObjects(next));
				}
			}
			// Avoid recursive call in case of stack overflow
//			getConnectedNodesRecursive(next, inCluster);
			toProcess.removeAll(inCluster);
			clusters.add(inCluster);
		}
		return clusters;
	}
	
	
	/**
	 * Compute mean measurements from clustering all connected objects.
	 */
	public void addClusterMeasurements() {
		if (nodeMap == null || nodeMap.isEmpty())
			return;
		
		List<Set<PathObject>> clusters = getConnectedClusters();
		
		String key = "Cluster ";
		List<String> measurementNames = new ArrayList<>();
		for (String s : PathClassifierTools.getAvailableFeatures(nodeMap.keySet())) {
			if (!s.startsWith(key))
				measurementNames.add(s);
		}
		RunningStatistics[] averagedMeasurements = new RunningStatistics[measurementNames.size()]; 
		for (int i = 0; i < averagedMeasurements.length; i++)
			averagedMeasurements[i] = new RunningStatistics();
		for (Set<PathObject> cluster : clusters) {
//			Arrays.fill(averagedMeasurements, 0);
			int n = cluster.size();
			for (PathObject pathObject : cluster) {
				MeasurementList ml = pathObject.getMeasurementList();
				for (int i = 0; i < measurementNames.size(); i++) {
					double val = ml.getMeasurementValue(i);
					if (Double.isFinite(val)) {
						averagedMeasurements[i].addValue(val);
					}
				}
			}

			for (PathObject pathObject : cluster) {
				MeasurementList ml = pathObject.getMeasurementList();
				for (int i = 0; i < measurementNames.size(); i++) {
					ml.putMeasurement(key + "mean: " + measurementNames.get(i), averagedMeasurements[i].getMean());
				}
				ml.putMeasurement(key + "size", n);
				ml.close();
			}

		}
		
	}
	
	
	/**
	 * Add Delaunay measurements to each pathObject.
	 */
	public void addNodeMeasurements() {
		if (nodeMap == null)
			return;

		// If 0, no averaging is performed
		// If 1, the averages of each object's measurements & those of its immediate neighbors are added
		// If 2, the neighbors of the neighbors are included as well
		// If 3, the neighbors or the neighbors of the neighbors are included... and so on...
		int averagingSeparation = 0;

		String[] measurementNames = new String[0];
		double[] averagedMeasurements = new double[0];
		Set<PathObject> neighborSet = new HashSet<>();
		for (Entry<PathObject, DelaunayNode> entry : nodeMap.entrySet()) {
			MeasurementList measurementList = entry.getKey().getMeasurementList();
			DelaunayNode node = entry.getValue();
			
			// Create a set of neighbors
			if (averagingSeparation > 0) {
				neighborSet.clear();
				node.addNeighborsToSet(neighborSet, averagingSeparation);
				
				// Get the smoothed measurements now, since access is likely to be much faster we start modifying it
				measurementNames = measurementList.getMeasurementNames().toArray(measurementNames);
				if (averagedMeasurements.length < measurementNames.length)
					averagedMeasurements = new double[measurementNames.length];
				for (int i = 0; i < measurementNames.length; i++) {
					String name = measurementNames[i];
					if (name == null || name.startsWith("Delaunay"))
						continue;
					
					double sum = 0;
					int n = 0;
					for (PathObject tempObject : neighborSet) {
						double value = tempObject.getMeasurementList().getMeasurementValue(name);
						if (Double.isNaN(value))
							continue;
						sum += value;
						n++;
					}
					averagedMeasurements[i] = n > 0 ? sum/n : Double.NaN;
				}
			}
			
			// TODO: PUT MEASUREMENTS IN UNITS OTHER THAN PIXELS????
			measurementList.putMeasurement("Delaunay: Num neighbors", node.nNeighbors());
			measurementList.putMeasurement("Delaunay: Mean distance", node.meanDistance());
			measurementList.putMeasurement("Delaunay: Median distance", node.medianDistance());
			measurementList.putMeasurement("Delaunay: Max distance", node.maxDistance());
			measurementList.putMeasurement("Delaunay: Min distance", node.minDistance());
//			measurementList.putMeasurement("Delaunay: Displacement sum mag", node.magDisplacementSum());
			
			measurementList.putMeasurement("Delaunay: Mean triangle area", node.getMeanTriangleArea());
			measurementList.putMeasurement("Delaunay: Max triangle area", node.getMaxTriangleArea());
			
			
			// Put in averaged measurements using immediate neighbours
			if (averagingSeparation > 0) {
				for (int i = 0; i < measurementNames.length; i++) {
					String name = measurementNames[i];
					if (name == null || name.startsWith("Delaunay"))
						continue;
					measurementList.putMeasurement("Delaunay averaged (" + averagingSeparation + "): " + name, averagedMeasurements[i]);
				}			
			}
			
			measurementList.close();
		}
	}
		
	
	static double distance(final ROI r1, final ROI r2) {
		double dx = r1.getCentroidX() - r2.getCentroidX();
		double dy = r1.getCentroidY() - r2.getCentroidY();
		return Math.sqrt(dx*dx + dy*dy);
	}
	
	
	static double distance(final DelaunayNode node1, final DelaunayNode node2) {
		double dx = node1.x - node2.x;
		double dy = node1.y - node2.y;
		return Math.sqrt(dx*dx + dy*dy);
	}
	
	
	
	static class DelaunayNodeFactory {

		private Map<PathObject, DelaunayNode> nodeMap = new HashMap<>();
		private double pixelWidth, pixelHeight;
		
		DelaunayNodeFactory(final double pixelWidth, final double pixelHeight) {
			this.pixelWidth = pixelWidth;
			this.pixelHeight = pixelHeight;
		}
		
		public DelaunayNode getNode(final PathObject pathObject) {
			DelaunayNode node = nodeMap.get(pathObject);
			if (node == null) {
				node = new DelaunayNode(pathObject, pixelWidth, pixelHeight);
				nodeMap.put(pathObject, node);
			}
			return node;
		}
		
	}
	
	
	static class DelaunayNode {
		
		private PathObject pathObject;
		private double x, y;
		private List<DelaunayNode> nodeList = new ArrayList<>(6);
		private List<DelaunayTriangle> triangleList = new ArrayList<>();
		private double[] distances = null;
		
		private DelaunayNode(final PathObject pathObject, final double pixelWidth, final double pixelHeight) {
			this.pathObject = pathObject;
			ROI roi = getROI(pathObject);
			this.x = roi.getCentroidX() * pixelWidth;
			this.y = roi.getCentroidY() * pixelHeight;
		}
		
//		public void addEdge(final PathObject destination) {
//			if (destination == null)
//				return;
//			DelaunayNode node = getNode(destination);
//			if (!nodeList.contains(node)) { // May not need this, depending on how subdiv is implemented; also could use Set (but higher memory requirements)
//				nodeList.add(node);
//				distances = null;
//			}
//		}
		
		public void addEdge(final DelaunayNode destination) {
			if (destination == null)
				return;
			if (!nodeList.contains(destination)) { // May not need this, depending on how subdiv is implemented; also could use Set (but higher memory requirements)
				nodeList.add(destination);
				distances = null;
				triangleList.clear();
			}
		}
		
		public List<DelaunayNode> getNodeList() {
			return nodeList;
		}
		
		public PathObject getPathObject() {
			return pathObject;
		}
		
		
		public int nNeighbors() {
			return nodeList.size();
		}
		
		void ensureDistancesUpdated() {
			if (distances != null && distances.length == nodeList.size())
				return;
			distances = new double[nNeighbors()];
			for (int i = 0; i < nodeList.size(); i++) {
				DelaunayNode node = nodeList.get(i);
				distances[i] = distance(this, node);
			}
			Arrays.sort(distances);
		}
		
//		public double magDisplacementSum() {
//			double dx = 0;
//			double dy = 0;
//			for (int i = 0; i < nodeList.size(); i++) {
//				dx += (x - nodeList.get(i).x);
//				dy += (y - nodeList.get(i).y);
//			}
//			return Math.sqrt(dx*dx + dy*dy);
//		}

		public double meanDistance() {
			ensureDistancesUpdated();
			if (distances.length == 0)
				return Double.NaN;
			double mean = 0;
			double n = nNeighbors();
			for (double d : distances)
				mean += d / n;
			return mean;
		}
		
		public double medianDistance() {
			ensureDistancesUpdated();
			if (distances.length == 0)
				return Double.NaN;
			if (distances.length % 2 == 1)
				return distances[distances.length / 2];
			return distances[distances.length / 2 - 1] / 2 + distances[distances.length / 2] / 2;
		}
		
		public double minDistance() {
			ensureDistancesUpdated();
			if (distances.length == 0)
				return Double.NaN;
			return distances[0];
		}

		public double maxDistance() {
			ensureDistancesUpdated();
			if (distances.length == 0)
				return Double.NaN;
			return distances[distances.length - 1];
		}
		
		
		private double getMeasurementValue(final String measurement) {
			return pathObject.getMeasurementList().getMeasurementValue(measurement);
		}
		
		
		private void addNeighborsToSet(final Set<PathObject> set, final int maxSeparation) {
			if (set.add(pathObject) && maxSeparation > 0) {
				for (DelaunayNode node : nodeList)
					node.addNeighborsToSet(set, maxSeparation-1);
			}
//			if (maxSeparation == 0)
//				set.add(pathObject);
//			else {
//				for (DelaunayNode node : nodeList)
//					node.addNeighborsToSet(set, maxSeparation-1);
//			}
		}
		
		
		private double getMeanMeasurement(final String measurement) {
			double sum = getMeasurementValue(measurement);
			int n = 1;
			if (Double.isNaN(sum)) {
				sum = 0;
				n = 0;
			}
			for (int i = 0; i < nodeList.size(); i++) {
				DelaunayNode node = nodeList.get(i);
				double value = node.getMeasurementValue(measurement);
				if (Double.isNaN(value))
					continue;
				sum += value;
				n++;
			}
			if (n == 0)
				return Double.NaN;
			return sum / n;
		}
		
		
		private void ensureTrianglesCalculated() {
			if (!triangleList.isEmpty())
				return;
			triangleList.clear();
			for (int i = 0; i < nodeList.size(); i++) {
				DelaunayNode node = nodeList.get(i);
				for (int j = i+1; j < nodeList.size(); j++) {
					DelaunayNode node2 = nodeList.get(j);
					if (node.nodeList.contains(node2))
						triangleList.add(new DelaunayTriangle(this, node, node2));
				}				
			}
		}
		
		public double getMeanTriangleArea() {
			ensureTrianglesCalculated();
			double d = 0;
			for (DelaunayTriangle t : triangleList)
				d += t.getArea();
			return d / triangleList.size();
		}
		
		public double getMaxTriangleArea() {
			ensureTrianglesCalculated();
			if (triangleList.isEmpty())
				return Double.NaN;
			double maxArea = Double.NEGATIVE_INFINITY;
			for (DelaunayTriangle t : triangleList) {
				double area = t.getArea();
				if (area > maxArea)
					maxArea = area;
			}
			return Double.isFinite(maxArea) ? maxArea : Double.NaN;
		}

		
	}
	
	
	
	
	static class DelaunayTriangle {
		
		private DelaunayNode node1;
		private DelaunayNode node2;
		private DelaunayNode node3;
		
		public DelaunayTriangle(final DelaunayNode node1, final DelaunayNode node2, final DelaunayNode node3) {
			this.node1 = node1;
			this.node2 = node2;
			this.node3 = node3;
		}
		
		public double getArea() {
			
			double ax = node1.x - node3.x;
			double ay = node1.y - node3.y;
			double bx = node2.x - node3.x;
			double by = node2.y - node3.y;
			
//			// Little bit of checking...
//			List<Point2> points = new ArrayList<>();
//			points.add(new Point2(node1.x, node1.y));
//			points.add(new Point2(node2.x, node2.y));
//			points.add(new Point2(node3.x, node3.y));
//			double area = Math.abs(ax * by - ay * bx)/2;
//			PolygonROI polygon = new PolygonROI(points);
//			System.out.println(area + "\t-\t" + polygon.getArea() + "\tDiff: " + (area - polygon.getArea()));
			
			return Math.abs(ax * by - ay * bx)/2;
			
		}
		
	}



	@Override
	public boolean containsObject(PathObject pathObject) {
		return nodeMap.containsKey(pathObject);
	}


}
