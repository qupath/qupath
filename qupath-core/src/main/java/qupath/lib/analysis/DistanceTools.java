/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.distance.DistanceToPoint;
import org.locationtech.jts.algorithm.distance.PointPairDistance;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.locationtech.jts.index.strtree.ItemBoundable;
import org.locationtech.jts.index.strtree.ItemDistance;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.roi.GeometryTools;

/**
 * Static methods for calculating distances between objects.
 * 
 * @author Pete Bankhead
 */
public class DistanceTools {
	
	private final static Logger logger = LoggerFactory.getLogger(DistanceTools.class);
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list.
	 * @param imageData
	 * @param splitClassNames if true, split the classification name. For example, if an image contains classifications for both "CD3: CD4" and "CD3: CD8",
	 *                        distances will be calculated for all components (e.g. "CD3", "CD4" and "CD8").
	 */
	public static void detectionToAnnotationDistances(ImageData<?> imageData, boolean splitClassNames) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var annotations = hierarchy.getAnnotationObjects();
		var detections = hierarchy.getCellObjects();
		if (detections.isEmpty())
			detections = hierarchy.getDetectionObjects();
		
		var pathClasses = annotations.stream()
				.map(p -> p.getPathClass())
				.filter(p -> p != null && p.isValid() && !PathClassTools.isIgnoredClass(p))
				.collect(Collectors.toSet());
		
		var cal = server.getPixelCalibration();
		String xUnit = cal.getPixelWidthUnit();
		String yUnit = cal.getPixelHeightUnit();
		double pixelWidth = cal.getPixelWidth().doubleValue();
		double pixelHeight = cal.getPixelHeight().doubleValue();
		if (!xUnit.equals(yUnit))
			throw new IllegalArgumentException("Pixel width & height units do not match! Width " + xUnit + ", height " + yUnit);
		String unit = xUnit;
		
		for (PathClass pathClass : pathClasses) {
			if (splitClassNames) {
				var names = PathClassTools.splitNames(pathClass);
				for (var name : names) {
					logger.debug("Computing distances for {}", pathClass);
					var filteredAnnotations = annotations.stream().filter(a -> PathClassTools.containsName(a.getPathClass(), name)).collect(Collectors.toList());
					if (!filteredAnnotations.isEmpty()) {
						String measurementName = "Distance to annotation with " + name + " " + unit;
						centroidToBoundsDistance2D(detections, filteredAnnotations, pixelWidth, pixelHeight, measurementName);
					}
				}
			} else {
				logger.debug("Computing distances for {}", pathClass);
				var filteredAnnotations = annotations.stream().filter(a -> a.getPathClass() == pathClass).collect(Collectors.toList());
				if (!filteredAnnotations.isEmpty()) {
					String name = "Distance to annotation " + pathClass + " " + unit;
					centroidToBoundsDistance2D(detections, filteredAnnotations, pixelWidth, pixelHeight, name);
				}
			}
		}
		hierarchy.fireObjectMeasurementsChangedEvent(DistanceTools.class, detections);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list.
	 * @param imageData
	 * @param splitClassNames if true, split the classification name. For example, if an image contains classifications for both "CD3: CD4" and "CD3: CD8",
	 *                        distances will be calculated for all components (e.g. "CD3", "CD4" and "CD8").
	 */
	public static void detectionCentroidDistances(ImageData<?> imageData, boolean splitClassNames) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var detections = hierarchy.getCellObjects();
		if (detections.isEmpty())
			detections = hierarchy.getDetectionObjects();
		
		var pathClasses = detections.stream()
				.map(p -> p.getPathClass())
				.filter(p -> p != null && p.isValid() && !PathClassTools.isIgnoredClass(p))
				.collect(Collectors.toSet());
		
		var cal = server.getPixelCalibration();
		String xUnit = cal.getPixelWidthUnit();
		String yUnit = cal.getPixelHeightUnit();
		double pixelWidth = cal.getPixelWidth().doubleValue();
		double pixelHeight = cal.getPixelHeight().doubleValue();
		if (!xUnit.equals(yUnit))
			throw new IllegalArgumentException("Pixel width & height units do not match! Width " + xUnit + ", height " + yUnit);
		String unit = xUnit;
		
		for (PathClass pathClass : pathClasses) {
			if (splitClassNames) {
				var names = PathClassTools.splitNames(pathClass);
				for (var name : names) {
					logger.debug("Computing distances for {}", pathClass);
					var filteredDetections = detections.stream().filter(a -> PathClassTools.containsName(a.getPathClass(), name)).collect(Collectors.toList());
					if (!filteredDetections.isEmpty()) {
						String measurementName = "Distance to detection with " + name + " " + unit;
						centroidToCentroidDistance2D(detections, filteredDetections, pixelWidth, pixelHeight, measurementName);
					}
				}
			} else {
				logger.debug("Computing distances for {}", pathClass);
				var filteredDetections = detections.stream().filter(a -> a.getPathClass() == pathClass).collect(Collectors.toList());
				if (!filteredDetections.isEmpty()) {
					String name = "Distance to detection " + pathClass + " " + unit;
					centroidToCentroidDistance2D(detections, filteredDetections, pixelWidth, pixelHeight, name);
				}
			}
		}
		hierarchy.fireObjectMeasurementsChangedEvent(DistanceTools.class, detections);
	}
	
	/**
	 * Calculate the distance between source object centroids and the boundary of specified target objects, adding the result to the measurement list of the source objects.
	 * Calculations are all made in 2D; distances will not be calculated between objects occurring on different z-planes of at different timepoints.
	 * 
	 * @param sourceObjects source objects; measurements will be added based on centroid distances
	 * @param targetObjects target objects; no measurements will be added
	 * @param pixelWidth pixel width to use in Geometry conversion (use 1 for pixel units)
	 * @param pixelHeight pixel height to use in Geometry conversion (use 1 for pixel units)
	 * @param measurementName the name of the measurement to add to the measurement list
	 */
	public static void centroidToBoundsDistance2D(Collection<PathObject> sourceObjects, Collection<PathObject> targetObjects, double pixelWidth, double pixelHeight, String measurementName) {		
		
		boolean preferNucleus = true;
		
		var timePoints = new TreeSet<Integer>();
		var zSlices = new TreeSet<Integer>();
		for (var temp : sourceObjects) {
			timePoints.add(temp.getROI().getT());
			zSlices.add(temp.getROI().getZ());
		}
		
		var transform = pixelWidth == 1 && pixelHeight == 1 ? null : AffineTransformation.scaleInstance(pixelWidth, pixelHeight);
//		var builder = new GeometryTools.GeometryConverter.Builder()
//				.pixelSize(pixelWidth, pixelHeight);
////		
//		var converter = builder.build();

		for (int t : timePoints) {
			for (int z : zSlices) {
				
				PrecisionModel precision = null;
				
				List<Geometry> areaGeometries = new ArrayList<>();
				List<Geometry> lineGeometries = new ArrayList<>();
				List<Geometry> pointGeometries = new ArrayList<>();
				for (var annotation : targetObjects) {
					var roi = annotation.getROI();
					if (roi != null && roi.getZ() == z && roi.getT() == t) {
						var geom = annotation.getROI().getGeometry();
						if (transform != null) {
							geom = transform.transform(geom);
							if (precision == null)
								precision = geom.getPrecisionModel();
						}
//						var geom = converter.roiToGeometry(annotation.getROI());
						if (geom instanceof Puntal)
							pointGeometries.add(geom);
						else if (geom instanceof Lineal)
							lineGeometries.add(geom);
						else if (geom instanceof Polygonal)
							areaGeometries.add(geom);
						else {
							for (int i = 0; i < geom.getNumGeometries(); i++) {
								var geom2 = geom.getGeometryN(i);
								if (geom2 instanceof Puntal)
									pointGeometries.add(geom2);
								else if (geom2 instanceof Lineal)
									lineGeometries.add(geom2);
								else if (geom2 instanceof Polygonal)
									areaGeometries.add(geom2);
								else
									logger.warn("Unexpected nested Geometry collection, some Geometries may be ignored");
							}
						}
					}
				}
		
				if (areaGeometries.isEmpty() && pointGeometries.isEmpty() && lineGeometries.isEmpty())
					continue;
				
				var precisionModel = precision == null ? GeometryTools.getDefaultFactory().getPrecisionModel() : precision;
				
				List<Coordinate> pointCoords = new ArrayList<>();
				
				Geometry temp = null;
				if (!areaGeometries.isEmpty())
					temp = areaGeometries.size() == 1 ? areaGeometries.get(0) : GeometryCombiner.combine(areaGeometries);
				Geometry shapeGeometry = temp;
				
				temp = null;
				if (!lineGeometries.isEmpty())
					temp = lineGeometries.size() == 1 ? lineGeometries.get(0) : GeometryCombiner.combine(lineGeometries);
				Geometry lineGeometry = temp;
				
				// Identify points, and create an STRtree to find nearest neighbors more quickly if there are a lot of them
				if (!pointGeometries.isEmpty()) {
					for (var geom : pointGeometries) {
						for (var coord : geom.getCoordinates()) {
							precisionModel.makePrecise(coord);
							pointCoords.add(coord);
						}
					}
				}
				STRtree pointTree = pointCoords != null && pointCoords.size() > 1000 ? createCoordinateCache(pointCoords) : null;
				CoordinateDistance coordinateDistance = new CoordinateDistance();
				
				int zi = z;
				int ti = t;
				
				
				var locator = shapeGeometry == null ? null : new IndexedPointInAreaLocator(shapeGeometry);
				sourceObjects.parallelStream().forEach(p -> {
					var roi = PathObjectTools.getROI(p, preferNucleus);
					if (roi.getZ() != zi || roi.getT() != ti)
						return;
					Coordinate coord = new Coordinate(roi.getCentroidX() * pixelWidth, roi.getCentroidY() * pixelHeight);
					precisionModel.makePrecise(coord);
					
					double pointDistance = pointCoords == null ? Double.POSITIVE_INFINITY : computeCoordinateDistance(coord, pointCoords, pointTree, coordinateDistance);
					double lineDistance = lineGeometry == null ? Double.POSITIVE_INFINITY : computeDistance(coord, lineGeometry, null);
					double shapeDistance = shapeGeometry == null ? Double.POSITIVE_INFINITY : computeDistance(coord, shapeGeometry, locator);
					double distance = Math.min(lineDistance, Math.min(pointDistance, shapeDistance));
					
					try (var ml = p.getMeasurementList()) {
						ml.putMeasurement(measurementName, distance);
					}
				});
			}
		}
	}
	
	
	/**
	 * Calculate the distance between source object centroids and the centroid of specified target objects, adding the result to the measurement list of the source objects.
	 * Calculations are all made in 2D; distances will not be calculated between objects occurring on different z-planes of at different timepoints.
	 * 
	 * @param sourceObjects source objects; measurements will be added based on centroid distances
	 * @param targetObjects target objects; no measurements will be added
	 * @param pixelWidth pixel width to use in Geometry conversion (use 1 for pixel units)
	 * @param pixelHeight pixel height to use in Geometry conversion (use 1 for pixel units)
	 * @param measurementName the name of the measurement to add to the measurement list
	 */
	public static void centroidToCentroidDistance2D(Collection<PathObject> sourceObjects, Collection<PathObject> targetObjects, double pixelWidth, double pixelHeight, String measurementName) {
		boolean preferNucleus = true;
		var targetPoints = PathObjectTools.convertToPoints(targetObjects, preferNucleus);
		centroidToBoundsDistance2D(sourceObjects, targetPoints, pixelWidth, pixelHeight, measurementName);
	}
	
	
	/**
	 * Compute the shortest distance from a coordinate to one of a collection of target coordinates.
	 * @param coord
	 * @param targets
	 * @return
	 */
	public static double computeCoordinateDistance(Coordinate coord, Collection<Coordinate> targets) {
		double d = Double.POSITIVE_INFINITY;
		for (var target : targets)
			d = Math.min(d, coord.distance(target));
		return d;
	}
	
	/**
	 * Compute the distance to the nearest coordinate stored within an {@link STRtree}.
	 * This assumes that all items in the tree are coordinates (and nothing else!).
	 * @param coord the query coordinate
	 * @param tree the {@link STRtree} containing existing coordinates
	 * @return distance to the closest coordinate found in tree, or Double.POSITIVE_INFINITY is no coordinate is found
	 * 
	 * @see #createCoordinateCache(Collection)
	 */
	public static double computeCoordinateDistance(Coordinate coord, STRtree tree) {
		return computeCoordinateDistance(coord, tree, new CoordinateDistance());
	}
	
	/**
	 * Create an {@link STRtree} to enable nearest neighbor searching for a collection of coordinates.
	 * @param coords the coordinates to insert into the tree
	 * @return the tree
	 */
	public static STRtree createCoordinateCache(Collection<Coordinate> coords) {
		var tree = new STRtree();
		for (var c : coords) {
			tree.insert(new Envelope(c), c);							
		}
		return tree;
	}

	
	private static double computeCoordinateDistance(Coordinate coord, STRtree tree, ItemDistance distance) {
		if (tree.isEmpty())
			return Double.POSITIVE_INFINITY;
		var env = new Envelope(coord);
		var nearest = (Coordinate)tree.nearestNeighbour(env, coord, new CoordinateDistance());
		return nearest == null ? Double.POSITIVE_INFINITY : coord.distance(nearest);
	}
	
	private static double computeCoordinateDistance(Coordinate coord, Collection<Coordinate> targets, STRtree tree, ItemDistance distance) {
		if (tree != null) {
			return computeCoordinateDistance(coord, tree, distance);
		}
		double d = Double.POSITIVE_INFINITY;
		for (var target : targets)
			d = Math.min(d, coord.distance(target));
		return d;
	}
	
	private static class CoordinateDistance implements ItemDistance {

		@Override
		public double distance(ItemBoundable item1, ItemBoundable item2) {
			var o1 = (Coordinate)item1.getItem();
			var o2 = (Coordinate)item2.getItem();
			return o1.distance(o2);
		}
		
	}
	
	/**
	 * Compute the shortest distance from a coordinate to a target geometry.
	 * @param coord the coordinate
	 * @param geometry the target geometry
	 * @param locator a locator created for the target Geometry or null; if available, computations may be faster
	 * @return
	 */
	public static double computeDistance(Coordinate coord, Geometry geometry, PointOnGeometryLocator locator) {
		if (locator == null) {
			PointPairDistance dist = new PointPairDistance();
			DistanceToPoint.computeDistance(geometry, coord, dist);
			return dist.getDistance();
		}
		int location = locator.locate(coord);
		double distance = 0;
		if (location == Location.EXTERIOR) {
			PointPairDistance dist = new PointPairDistance();
			DistanceToPoint.computeDistance(geometry, coord, dist);
			distance = dist.getDistance();
		}
		return distance;
	}

}