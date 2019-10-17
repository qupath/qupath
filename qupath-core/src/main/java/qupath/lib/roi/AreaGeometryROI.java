package qupath.lib.roi;

import java.awt.Shape;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.util.AffineTransformation;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;

public class AreaGeometryROI extends AbstractPathAreaROI implements TranslatableROI, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Geometry geometry;
	transient private ClosedShapeStatistics stats = null;
	
	AreaGeometryROI(Geometry geometry, ImagePlane plane) {
		super(plane);
		this.geometry = geometry.copy();
	}

	@Override
	public String getRoiName() {
		return "Geometry";
	}

	@Override
	public double getCentroidX() {
		return geometry.getCentroid().getX();
	}

	@Override
	public double getCentroidY() {
		return geometry.getCentroid().getY();
	}

	@Override
	public double getBoundsX() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsX();
	}


	@Override
	public double getBoundsY() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsY();
	}


	@Override
	public double getBoundsWidth() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsWidth();
	}


	@Override
	public double getBoundsHeight() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsHeight();
	}

	@Override
	public List<Point2> getPolygonPoints() {
		return Arrays.stream(geometry.getCoordinates()).map(c -> new Point2(c.x, c.y)).collect(Collectors.toList());
	}
	
	void calculateShapeMeasurements() {
		stats = new ClosedShapeStatistics(getShape(), 1, 1);
	}

	@Override
	public ROI duplicate() {
		return new AreaGeometryROI(geometry, plane);
	}
	
	@Override
	public Geometry getGeometry() {
		return geometry.copy();
	}

	@Override
	public Shape getShape() {
		return GeometryTools.convertROIToShape(geometry);
	}

	@Override
	public RoiType getRoiType() {
		return RoiType.AREA;
	}
	
	@Override
	public double getArea() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getArea();
	}

	@Override
	public double getPerimeter() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getPerimeter();
	}

	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getArea() * pixelWidth * pixelHeight;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(getShape(), pixelWidth, pixelHeight).getArea();
	}

	@Override
	public double getScaledPerimeter(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getPerimeter() * (pixelWidth + pixelHeight) * .5;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(getShape(), pixelWidth, pixelHeight).getPerimeter();
	}

	@Override
	public boolean contains(double x, double y) {
		return SimplePointInAreaLocator.locate(
				new Coordinate(x, y), geometry) != Location.EXTERIOR;
	}

	@Override
	public TranslatableROI translate(double dx, double dy) {
		return new AreaGeometryROI(AffineTransformation.translationInstance(dx, dy).transform(geometry), plane);
	}
	
	private Object writeReplace() {
		AreaROI roi = new AreaROI(RoiTools.getVertices(getShape()), ImagePlane.getPlaneWithChannel(c, z, t));
		return roi;
	}

}
