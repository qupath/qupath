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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.GeometryCombiner;
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
	 */
	public static void detectionToAnnotationDistances(ImageData<?> imageData) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var annotations = hierarchy.getAnnotationObjects();
		var detections = hierarchy.getDetectionObjects();
		
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
			logger.debug("Computing distances for {}", pathClass);
			var filteredAnnotations = annotations.stream().filter(a -> a.getPathClass() == pathClass).collect(Collectors.toList());
			if (!filteredAnnotations.isEmpty()) {
				String name = "Distance to " + pathClass + " " + unit;
				centroidToBoundsDistance2D(detections, filteredAnnotations, pixelWidth, pixelHeight, name);
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
		
		var timePoints = new TreeSet<Integer>();
		var zSlices = new TreeSet<Integer>();
		for (var temp : sourceObjects) {
			timePoints.add(temp.getROI().getT());
			zSlices.add(temp.getROI().getZ());
		}
		
		var builder = new GeometryTools.GeometryConverter.Builder()
				.pixelSize(pixelWidth, pixelHeight);
		
		var converter = builder.build();

		for (int t : timePoints) {
			for (int z : zSlices) {
				
				List<Geometry> areaGeometries = new ArrayList<>();
				List<Geometry> lineGeometries = new ArrayList<>();
				List<Geometry> pointGeometries = new ArrayList<>();
				for (var annotation : targetObjects) {
					var roi = annotation.getROI();
					if (roi != null && roi.getZ() == z && roi.getT() == t) {
						var geom = converter.roiToGeometry(annotation.getROI());
						if (geom instanceof Puntal)
							pointGeometries.add(geom);
						else if (geom instanceof Lineal)
							lineGeometries.add(geom);
						else if (geom instanceof Polygonal)
							areaGeometries.add(geom);
					}
				}
		
				if (areaGeometries.isEmpty() && pointGeometries.isEmpty() && lineGeometries.isEmpty())
					continue;
				
				List<Coordinate> pointCoords = new ArrayList<>();
				
				Geometry temp = null;
				if (!areaGeometries.isEmpty())
					temp = areaGeometries.size() == 1 ? areaGeometries.get(0) : GeometryCombiner.combine(areaGeometries);
				Geometry shapeGeometry = temp;
				
				temp = null;
				if (!lineGeometries.isEmpty())
					temp = lineGeometries.size() == 1 ? lineGeometries.get(0) : GeometryCombiner.combine(lineGeometries);
				Geometry lineGeometry = temp;
				
				if (!pointGeometries.isEmpty()) {
					for (var geom : pointGeometries) {
						for (var coord : geom.getCoordinates())
							pointCoords.add(coord);
					}
				}
				
				int zi = z;
				int ti = t;
				
				var locator = shapeGeometry == null ? null : new IndexedPointInAreaLocator(shapeGeometry);
				sourceObjects.parallelStream().forEach(p -> {
					var roi = PathObjectTools.getROI(p, true);
					if (roi.getZ() != zi || roi.getT() != ti)
						return;
					Coordinate coord = new Coordinate(roi.getCentroidX() * pixelWidth, roi.getCentroidY() * pixelHeight);
					
					double pointDistance = pointCoords == null ? Double.POSITIVE_INFINITY : computeCoordinateDistance(coord, pointCoords);
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
