package qupath.lib.roi;

import java.awt.Shape;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * ROI based on Java Topology Suite Geometry objects.
 * This gives a very flexible representation (except for a lack of support for ellipses), 
 * which needs only copy itself to return {@link #getGeometry()}.
 * Consequently it can be much more performant whenever the underlying Geometry is 
 * required frequently compared to other ROI types with a new Geometry must be 
 * constructed and validated.
 * 
 * @author Pete Bankhead
 *
 */
public class GeometryROI extends AbstractPathROI implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private static Logger logger = LoggerFactory.getLogger(GeometryROI.class);
	
	private Geometry geometry;
	
	private transient GeometryStats stats = null;
	
	GeometryROI(Geometry geometry, ImagePlane plane) {
		super(plane);
		this.geometry = geometry.copy();
//		this.stats = computeGeometryStats(geometry, 1, 1);
//		if (!stats.isValid())
//			logger.warn("Creating invalid geometry: {}", stats.getError());
	}

	@Override
	public String getRoiName() {
		return "Geometry";
	}

	@Override
	public double getCentroidX() {
		return getGeometryStats().getCentroidX();
	}

	@Override
	public double getCentroidY() {
		return getGeometryStats().getCentroidY();
	}

	@Override
	public double getBoundsX() {
		return getGeometryStats().getBoundsX();
	}


	@Override
	public double getBoundsY() {
		return getGeometryStats().getBoundsY();
	}


	@Override
	public double getBoundsWidth() {
		return getGeometryStats().getBoundsWidth();
	}


	@Override
	public double getBoundsHeight() {
		return getGeometryStats().getBoundsHeight();
	}

	@Override
	public List<Point2> getAllPoints() {
		return Arrays.stream(geometry.getCoordinates()).map(c -> new Point2(c.x, c.y)).collect(Collectors.toList());
	}
	
	GeometryStats getGeometryStats() {
		if (stats == null) {
			synchronized(this) {
				if (stats == null)
					stats = computeGeometryStats(geometry, 1, 1);
			}
		}
		return stats;
	}

	@Override
	public ROI duplicate() {
		return new GeometryROI(geometry, getImagePlane());
	}
	
	@Override
	public Geometry getGeometry() {
		return geometry.copy();
	}

	@Override
	public Shape getShape() {
		return GeometryTools.geometryToShape(geometry);
	}

	@Override
	public RoiType getRoiType() {
		if (geometry instanceof Puntal)
			return RoiType.POINT;
		if (geometry instanceof Lineal)
			return RoiType.LINE;
		return RoiType.AREA;
	}
	
	@Override
	public double getArea() {
		return getGeometryStats().getArea();
	}

	@Override
	public double getLength() {
		return getGeometryStats().getLength();
	}

	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getArea() * pixelWidth * pixelHeight;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return computeGeometryStats(geometry, pixelWidth, pixelHeight).getArea();
	}

	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getLength() / 2.0 * (pixelWidth + pixelHeight);
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return computeGeometryStats(geometry, pixelWidth, pixelHeight).getLength();
	}

	@Override
	public boolean contains(double x, double y) {
		if (isArea())
			return SimplePointInAreaLocator.locate(
				new Coordinate(x, y), geometry) != Location.EXTERIOR;
		else
			return false;
	}

	@Override
	public ROI translate(double dx, double dy) {
		return new GeometryROI(AffineTransformation.translationInstance(dx, dy).transform(geometry), getImagePlane());
	}
	
	private Object writeReplace() {
		// Try to preserve areas as they were... but we need to use JTS serialization for others
		if (isArea()) {
			AreaROI roi = new AreaROI(RoiTools.getVertices(getShape()), ImagePlane.getPlaneWithChannel(c, z, t));
			return roi;
		} else
			return this;
	}
	
	
	static GeometryStats computeGeometryStats(Geometry geometry, double pixelWidth, double pixelHeight) {
		if (pixelWidth == 1 && pixelHeight == 1)
			return new GeometryStats(geometry);
		var transform = AffineTransformation.scaleInstance(pixelWidth, pixelHeight);
		return new GeometryStats(transform.transform(geometry));
	}
	
	static class GeometryStats {
		
		private double boundsMinX = Double.NaN;
		private double boundsMinY = Double.NaN;
		private double boundsMaxX = Double.NaN;
		private double boundsMaxY = Double.NaN;
		private double centroidX = Double.NaN;
		private double centroidY = Double.NaN;
		private double area = Double.NaN;
		private double length = Double.NaN;
		private TopologyValidationError error;
		
		GeometryStats(Geometry geometry) {
			error = new IsValidOp(geometry).getValidationError();
			if (error != null) {
				logger.warn("Stats requested for invalid geometry: " + error.getMessage());
			} else {
				area = geometry.getArea();
				length = geometry.getLength();
			}
			var centroid = geometry.getCentroid();
			if (!centroid.isEmpty()) {
				centroidX = centroid.getX();
				centroidY = centroid.getY();
			}
			var envelope = geometry.getEnvelopeInternal();
			if (envelope != null) {
				boundsMinX = envelope.getMinX();
				boundsMinY = envelope.getMinY();
				boundsMaxX = envelope.getMaxX();
				boundsMaxY = envelope.getMaxY();
			}
		}
		
		public double getCentroidX() {
			return centroidX;
		}

		public double getCentroidY() {
			return centroidY;
		}

		public double getBoundsX() {
			return boundsMinX;
		}

		public double getBoundsY() {
			return boundsMinY;
		}
		
		public double getBoundsWidth() {
			return boundsMaxX - boundsMinX;
		}

		public double getBoundsHeight() {
			return boundsMaxY - boundsMinY;
		}
		
		public double getArea() {
			return area;
		}
		
		public double getLength() {
			return length;
		}
		
		public boolean isValid() {
			return error == null;
		}
		
		public TopologyValidationError getError() {
			return error;
		}

	}

	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		var transform = AffineTransformation.scaleInstance(scaleX, scaleY, originX, originY);
		return new GeometryROI(transform.transform(geometry), getImagePlane());
	}

}
