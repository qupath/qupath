package qupath.lib.gui.commands;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.distance.DistanceToPoint;
import org.locationtech.jts.algorithm.distance.PointPairDistance;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.PathClasses;
import qupath.lib.roi.interfaces.ROI;
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
		var ignoreClasses = new HashSet<>(Arrays.asList(PathClassFactory.getDefaultPathClass(PathClasses.IGNORE), PathClassFactory.getDefaultPathClass(PathClasses.REGION)));
		var pathClasses = hierarchy.getAnnotationObjects().stream()
				.map(p -> p.getPathClass())
				.filter(p -> !ignoreClasses.contains(p))
				.collect(Collectors.toSet());
		
		for (PathClass pathClass : pathClasses) {
			logger.info("Computing distances for {}", pathClass);
			computeDistances(imageData, pathClass);
		}
		
	}
	
	
	public static void computeDistances(ImageData<?> imageData, PathClass pathClass) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var testPathClass = pathClass != null && !pathClass.isValid() ? null : pathClass;
		
		double pixelWidth = server.hasPixelSizeMicrons() ? server.getPixelWidthMicrons() : 1.0;
		double pixelHeight = server.hasPixelSizeMicrons() ? server.getPixelHeightMicrons() : 1.0;
		String unit = server.hasPixelSizeMicrons() ? GeneralTools.micrometerSymbol() : "px";
		String name = "Distance to " + pathClass + " " + unit;
		var builder = new ConverterJTS.Builder()
				.pixelSize(pixelWidth, pixelHeight);
		var converter = builder.build();
		List<Geometry> annotations = hierarchy.getAnnotationObjects()
				.stream()
				.filter(p -> p.getPathClass() == testPathClass && p.hasROI())
				.map(p -> converter.roiToGeometry(p.getROI()))
				.collect(Collectors.toList());

		if (annotations.isEmpty())
			return;
		
		Geometry geometry = annotations.size() == 1 ? annotations.get(0) : GeometryCombiner.combine(annotations);
		
		var detections = hierarchy.getDetectionObjects();
		
		var locator = new IndexedPointInAreaLocator(geometry);
		String measurementName = name;
		detections.parallelStream().forEach(p -> computeDistance(p, geometry, locator, measurementName, pixelWidth, pixelHeight));
		
		hierarchy.fireObjectMeasurementsChangedEvent(DistanceToAnnotationsCommand.class, detections);
	}
	
	
	private static void computeDistance(PathObject pathObject, Geometry geometry, PointOnGeometryLocator locator, String name, double pixelWidth, double pixelHeight) {

		ROI roi = pathObject.getROI();
		Coordinate coord = new Coordinate(roi.getCentroidX() * pixelWidth, roi.getCentroidY() * pixelHeight);

		int location = locator.locate(coord);
		double distance = 0;
		if (location == Location.EXTERIOR) {
			PointPairDistance dist = new PointPairDistance();
			DistanceToPoint.computeDistance(geometry, coord, dist);
			distance = dist.getDistance();
		}
		try (var ml = pathObject.getMeasurementList()) {
			ml.putMeasurement(name, distance);
		}
	}
	
	
}
