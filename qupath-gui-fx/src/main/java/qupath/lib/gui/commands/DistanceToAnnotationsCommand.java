package qupath.lib.gui.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.distance.DistanceToPoint;
import org.locationtech.jts.algorithm.distance.PointPairDistance;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.roi.jts.ConverterJTS;

/**
 * New command to get the distance from cells to annotations.
 * <p>
 * Note that this is subject to change! This is currently not scriptable and may be better 
 * as a plugin rather than command.
 * 
 * @author Pete Bankhead
 *
 */
public class DistanceToAnnotationsCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(DistanceToAnnotationsCommand.class);
	
	private QuPathGUI qupath;
	
	public DistanceToAnnotationsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {

		var imageData = qupath.getImageData();
		if (imageData == null)
			return;
		
		var hierarchy = imageData.getHierarchy();
		var ignoreClasses = new HashSet<>(Arrays.asList(null, PathClassFactory.getPathClass(StandardPathClasses.IGNORE), PathClassFactory.getPathClass(StandardPathClasses.REGION)));
		var pathClasses = hierarchy.getAnnotationObjects().stream()
				.map(p -> p.getPathClass())
				.filter(p -> !ignoreClasses.contains(p))
				.collect(Collectors.toSet());
		
		if (imageData.getServer().nZSlices() > 1) {
			if (!DisplayHelpers.showConfirmDialog("Distance to annotations 2D", 
					"Distance to annotations command works only in 2D - distances will not be calculated for objects on different z-slices or time-points"))
				return;
		}
		
		for (PathClass pathClass : pathClasses) {
			logger.info("Computing distances for {}", pathClass);
			computeDistances(imageData, pathClass);
		}
		
	}
	
	
	public static void computeDistances(ImageData<?> imageData, PathClass pathClass) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var testPathClass = pathClass != null && !pathClass.isValid() ? null : pathClass;
		
		PixelCalibration cal = server.getPixelCalibration();
		
		String xUnit = cal.getPixelWidthUnit();
		String yUnit = cal.getPixelHeightUnit();
		if (!xUnit.equals(yUnit))
			throw new IllegalArgumentException("Pixel width & height units do not match! Width " + xUnit + ", height " + yUnit);
		
		double pixelWidth = cal.getPixelWidth().doubleValue();
		double pixelHeight = cal.getPixelHeight().doubleValue();
		String unit = xUnit;
		String name = "Distance to " + pathClass + " " + unit;
		var builder = new ConverterJTS.Builder()
				.pixelSize(pixelWidth, pixelHeight);
		var converter = builder.build();
		
		var allAnnotations = hierarchy.getAnnotationObjects();
		var detections = hierarchy.getDetectionObjects();

		for (int t = 0; t < server.nTimepoints(); t++) {
			for (int z = 0; z < server.nZSlices(); z++) {
				
				List<Geometry> areaGeometries = new ArrayList<>();
				List<Geometry> lineGeometries = new ArrayList<>();
				List<Geometry> pointGeometries = new ArrayList<>();
				for (var annotation : allAnnotations) {
					var roi = annotation.getROI();
					if (roi != null && roi.getZ() == z && roi.getT() == t && annotation.getPathClass() == testPathClass) {
						var geom = converter.roiToGeometry(annotation.getROI());
						if (geom instanceof Puntal)
							pointGeometries.add(geom);
						else if (geom instanceof Lineal)
							lineGeometries.add(geom);
						else
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
				
				var locator = new IndexedPointInAreaLocator(shapeGeometry);
				detections.parallelStream().forEach(p -> {
					var roi = p.getROI();
					if (roi.getZ() != zi || roi.getT() != ti)
						return;
					Coordinate coord = new Coordinate(roi.getCentroidX() * pixelWidth, roi.getCentroidY() * pixelHeight);
					double pointDistance = pointCoords == null ? Double.POSITIVE_INFINITY : computeCoordinateDistance(coord, pointCoords);
					double lineDistance = lineGeometry == null ? Double.POSITIVE_INFINITY : computeDistance(coord, lineGeometry, null);
					double shapeDistance = shapeGeometry == null ? Double.POSITIVE_INFINITY : computeDistance(coord, shapeGeometry, locator);
					double distance = Math.min(lineDistance, Math.min(pointDistance, shapeDistance));
					
					try (var ml = p.getMeasurementList()) {
						ml.putMeasurement(name, distance);
					}
				});
			}
		}
		hierarchy.fireObjectMeasurementsChangedEvent(DistanceToAnnotationsCommand.class, detections);
	}
	
	
	private static double computeCoordinateDistance(Coordinate coord, Collection<Coordinate> targets) {
		double d = Double.POSITIVE_INFINITY;
		for (var target : targets)
			d = Math.min(d, coord.distance(target));
		return d;
	}
	
	private static double computeDistance(Coordinate coord, Geometry geometry, PointOnGeometryLocator locator) {
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
