/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020, 2024 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.roi;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.operation.predicate.RectangleIntersects;
import org.locationtech.jts.operation.valid.IsValidOp;
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
	
	private static final Logger logger = LoggerFactory.getLogger(GeometryROI.class);
	
	private final Geometry geometry;
	private boolean checkValid = false;
	
	private transient GeometryStats stats = null;

	// May be expensive
	private transient int hashCode;

	/**
	 * Cache a locator for faster 'contains' checks.
	 */
	private transient PointOnGeometryLocator cachedLocator;

	/**
	 * Create a GeometryROI, without checking for validity.
	 * @param geometry
	 * @param plane
	 */
	GeometryROI(Geometry geometry, ImagePlane plane) {
		this(geometry, plane, false);
	}
	
	/**
	 * Create a new GeometryROI.
	 * @param geometry
	 * @param plane
	 * @param checkValid if true, check the Geometry is valid before computing measurements. 
	 *                   Because the validity check can be (very) slow, it may be desirable to skip it if not needed.
	 */
	GeometryROI(Geometry geometry, ImagePlane plane, boolean checkValid) {
		super(plane);
		this.checkValid = checkValid;
		this.geometry = geometry.copy();
		// We can cache a locator for 'contains' checks now because it is built lazily anyway
		// but we only want it for large polygonal geometries
		if (geometry instanceof Polygonal && geometry.getNumPoints() > 1000) {
			logger.trace("Creating IndexedPointInAreaLocator for large geometry");
			cachedLocator = new IndexedPointInAreaLocator(geometry);
		}
		// Check the precision model & warn if it doesn't match
		if (!Objects.equals(geometry.getPrecisionModel(), GeometryTools.getDefaultFactory().getPrecisionModel())) {
			logger.warn("Geometry precision model for ROI {} does not match default precision model {}",
					geometry.getPrecisionModel(), GeometryTools.getDefaultFactory().getPrecisionModel());
		}
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
		return Arrays.stream(geometry.getCoordinates()).map(c -> new Point2(c.x, c.y)).toList();
	}
	
	GeometryStats getGeometryStats() {
		if (stats == null) {
			synchronized(this) {
				if (stats == null) 
					stats = computeGeometryStats(geometry, 1, 1, checkValid);
				// Avoid checking validity in the future
				if (checkValid && stats.isValid())
					checkValid = false;
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
		var shape = getShapeInternal();
		if (shape instanceof Area)
			return new Area(shape);
		else
			return new Path2D.Float(shape);
	}

	@Override
	public Shape createShape() {
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
		return computeGeometryStats(geometry, pixelWidth, pixelHeight, checkValid).getArea();
	}

	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getLength() / 2.0 * (pixelWidth + pixelHeight);
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return computeGeometryStats(geometry, pixelWidth, pixelHeight, checkValid).getLength();
	}

	@Override
	public boolean contains(double x, double y) {
		if (isArea()) {
			var coord = new Coordinate(x, y);
			if (cachedLocator != null) {
				return cachedLocator.locate(coord) != Location.EXTERIOR;
			} else {
				return SimplePointInAreaLocator.locate(coord, geometry) != Location.EXTERIOR;
			}
		} else
			return false;
	}

	@Override
	public boolean intersects(double x, double y, double width, double height) {
		if (!intersectsBounds(x, y, width, height))
			return false;
		return RectangleIntersects.intersects(GeometryTools.createRectangle(x, y, width, height), geometry);
	}


	@Override
	public ROI translate(double dx, double dy) {
		return new GeometryROI(AffineTransformation.translationInstance(dx, dy).transform(geometry), getImagePlane());
	}
	
	private Object writeReplace() {
		// This relies on JTS serialization
		return new WKBSerializationProxy(this);
	}
	
	
	static GeometryStats computeGeometryStats(Geometry geometry, double pixelWidth, double pixelHeight, boolean checkValid) {
		if (pixelWidth == 1 && pixelHeight == 1)
			return new GeometryStats(geometry, checkValid);
		var transform = AffineTransformation.scaleInstance(pixelWidth, pixelHeight);
		return new GeometryStats(transform.transform(geometry), checkValid);
	}
	
	static class GeometryStats implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private static String UNKNOWN = "Unknown";
		
		private double boundsMinX = Double.NaN;
		private double boundsMinY = Double.NaN;
		private double boundsMaxX = Double.NaN;
		private double boundsMaxY = Double.NaN;
		private double centroidX = Double.NaN;
		private double centroidY = Double.NaN;
		private double area = Double.NaN;
		private double length = Double.NaN;
//		private double maxDiameter = Double.NaN;
//		private double minDiameter = Double.NaN;
		private String error = UNKNOWN;
		
		GeometryStats(Geometry geometry, boolean checkValid) {
			if (checkValid) {
				var validationError = new IsValidOp(geometry).getValidationError();
				error = validationError == null ? null : validationError.getMessage();
			}
			if (checkValid && error != null) {
				logger.warn("Stats requested for invalid geometry: " + error);
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
//			maxDiameter = new MinimumBoundingCircle(geometry).getRadius() * 2.0;
//			minDiameter = new MinimumDiameter(geometry).getLength();
		}
		
//		public double getMaxDiameter() {
//			return maxDiameter;
//		}
//		
//		public double getMinDiameter() {
//			return minDiameter;
//		}
		
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
		
		public String getError() {
			return error;
		}

	}

	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		var transform = AffineTransformation.scaleInstance(scaleX, scaleY, originX, originY);
		return new GeometryROI(transform.transform(geometry), getImagePlane());
	}
	
	@Override
	public ROI updatePlane(ImagePlane plane) {
		return new GeometryROI(
				this.geometry,
				plane);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		GeometryROI that = (GeometryROI) o;
		return Objects.equals(getImagePlane(), that.getImagePlane()) && Objects.equals(geometry, that.geometry);
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			hashCode = Objects.hash(geometry, getImagePlane());
		}
		return hashCode;
	}

	//	@Override
//	public double getMaxDiameter() {
//		return getGeometryStats().getMaxDiameter();
//	}
//
//	@Override
//	public double getMinDiameter() {
//		return getGeometryStats().getMinDiameter();
//	}
//
//	@Override
//	public double getScaledMaxDiameter(double pixelWidth, double pixelHeight) {
//		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
//			return getMaxDiameter() / 2.0 * (pixelWidth + pixelHeight);
//		return computeGeometryStats(geometry, pixelWidth, pixelHeight, checkValid).getMaxDiameter();
//	}
//
//	@Override
//	public double getScaledMinDiameter(double pixelWidth, double pixelHeight) {
//		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
//			return getMinDiameter() / 2.0 * (pixelWidth + pixelHeight);
//		return computeGeometryStats(geometry, pixelWidth, pixelHeight, checkValid).getMinDiameter();
//	}
	
	
	private static class WKBSerializationProxy implements Serializable {

		private static final long serialVersionUID = 1L;

		private final byte[] wkb;
		private final int c, z, t;

		private final GeometryStats stats;

		WKBSerializationProxy(final GeometryROI roi) {
			this.wkb = new WKBWriter(2).write(roi.geometry);
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
			this.stats = roi.stats;


		}

		private Object readResolve() throws ParseException {
			// Assume we can use the default factory, since we wrote the ROI -
			// although if we were decreasing precision this could be problematic
			var geometry = new WKBReader(GeometryTools.getDefaultFactory()).read(wkb);
			GeometryROI roi = new GeometryROI(geometry, ImagePlane.getPlaneWithChannel(c, z, t));
			roi.stats = this.stats;
			return roi;
		}

	}
	
	/**
	 * Proxy that uses Geometry serialization.
	 */
	private static class SerializationProxy implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private final Geometry geometry;
		private final int c, z, t;
		
		private GeometryStats stats;
		
		SerializationProxy(final GeometryROI roi) {
			this.geometry = roi.geometry;
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
			this.stats = roi.stats;
			
			
		}
		
		private Object readResolve() {
			GeometryROI roi = new GeometryROI(geometry, ImagePlane.getPlaneWithChannel(c, z, t));
			roi.stats = this.stats;
			return roi;
		}
		
	}

}