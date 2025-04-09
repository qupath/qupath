/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
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
import java.awt.geom.Path2D;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Geometry;

import org.locationtech.jts.operation.predicate.RectangleIntersects;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;


/**
 * ROI representing an arbitrary closed polygon.
 * 
 * @see PolylineROI
 * 
 * @author Pete Bankhead
 *
 */
public class PolygonROI extends AbstractPathROI implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
//	private static final Logger logger = LoggerFactory.getLogger(PolygonROI.class);
	
	private Vertices vertices;
	
	private transient ClosedShapeStatistics stats = null;

	/**
	 * Cache a soft reference to the geometry because calculating a valid
	 * geometry can be a performance bottleneck (e.g. if there are self-intersections).
	 */
	private transient SoftReference<Geometry> cachedGeometry;

	// Hash code may be expensive to calculate
	private transient int hashCode;

	PolygonROI() {
		super();
	}
	
	
	/**
	 * Constructor that sets the ROI to being modified from the start (i.e. initialized from a single mouse event)
	 * @param x
	 * @param y
	 */
	PolygonROI(double x, double y) {
		this(x, y, null);
	}
	
	PolygonROI(List<Point2> points) {
		this(points, null);
	}
	
	
	PolygonROI(double x, double y, ImagePlane plane) {
		super(plane);
		vertices = VerticesFactory.createVertices(new float[]{(float)x, (float)x}, new float[]{(float)y, (float)y}, false);
	}
	
	PolygonROI(List<? extends Point2> points, ImagePlane plane) {
		super(plane);
		float[] x = new float[points.size()];
		float[] y = new float[points.size()];
		for (int i = 0; i < points.size(); i++) {
			Point2 p = points.get(i);
			x[i] = (float)p.getX();
			y[i] = (float)p.getY();
		}
		vertices = VerticesFactory.createVertices(x, y, false);
	}
	
	
	PolygonROI(float[] x, float[] y, ImagePlane plane) {
		this(x, y, plane, true);
	}
	
	
	PolygonROI(float[] x, float[] y, ImagePlane plane, boolean copyVertices) {
		super(plane);
		vertices = VerticesFactory.createVertices(x, y, copyVertices);
	}


	@Override
	public Geometry getGeometry() {
		return getGeometryInternal().copy();
	}

	/**
	 * Get a Geometry for internal use. This will <i>not</i> be copied, so shouldn't be leaked to consumers.
	 */
	private Geometry getGeometryInternal() {
		// Cache a soft reference because converting polygons to
		// (valid) geometries can be expensive
		var geom = cachedGeometry == null ? null : cachedGeometry.get();
		if (geom == null) {
			geom = super.getGeometry();
			cachedGeometry = new SoftReference<>(geom);
		}
		return geom;
	}
	
	/**
	 * Get the total number of vertices in the polygon.
	 * @return
	 */
	public int nVertices() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getNVertices();
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#duplicate()
	 */
	@Override
	@Deprecated
	public ROI duplicate() {
		return new PolygonROI(vertices.getPoints(), getImagePlane());
	}
	

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getCentroidX()
	 */
	@Override
	public double getCentroidX() {
		if (vertices.size() == 1)
			return vertices.getX(0);
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getCentroidX();
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getCentroidY()
	 */
	@Override
	public double getCentroidY() {
		if (vertices.size() == 1)
			return vertices.getY(0);
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getCentroidY();
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#contains(double, double)
	 */
	@Override
	public boolean contains(double x, double y) {
		return WindingTest.getWindingNumber(vertices, x, y) != 0;
	}

	@Override
	public boolean intersects(double x, double y, double width, double height) {
		if (!intersectsBounds(x, y, width, height))
			return false;
		// Use the Geometry because it should be more reliable than the shape
		return RectangleIntersects.intersects(GeometryTools.createRectangle(x, y, width, height), getGeometryInternal());
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#translate(double, double)
	 */
	@Override
	public ROI translate(double dx, double dy) {
		// Shift the bounds
		if (dx == 0 && dy == 0)
			return this;
		// Shift the region
		float[] x = vertices.getX(null);
		float[] y = vertices.getY(null);
		for (int i = 0; i < x.length; i++) {
			x[i] = (float)(x[i] + dx);
			y[i] = (float)(y[i] + dy);
		}
		return new PolygonROI(x, y, getImagePlane());
	}
	
	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		return new PolygonROI(
				getAllPoints().stream().map(p -> RoiTools.scalePoint(p, scaleX, scaleY, originX, originY)).toList(),
				getImagePlane());
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getROIType()
	 */
	@Override
	public String getRoiName() {
		return "Polygon";
	}

	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getArea()
	 */
	@Override
	public double getArea() {
		if (vertices.size() <= 2)
			return 0;
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getArea();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getPerimeter()
	 */
	@Override
	public double getLength() {
		if (getNumPoints() <= 1)
			return 0;
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getPerimeter();
	}

//	/**
//	 * Get the solidity of the polygon assuming 'square' pixels, defined as {@code getArea()/getConvexArea()}.
//	 * @return
//	 */
//	public double getSolidity() {
//		return getArea() / getConvexArea();
//	}
//	
//	/**
//	 * Get the solidity of the polygon, defined as {@code getScaledArea(pixelWidth, pixelHeight)/getScaledConvexArea(pixelWidth, pixelHeight)}.
//	 * @return
//	 */
//	public double getSolidity(double pixelWidth, double pixelHeight) {
//		return getScaledArea(pixelWidth, pixelHeight) / getScaledConvexArea(pixelWidth, pixelHeight);
//	}

	@Override
	public Shape getShape() {
		return new Path2D.Float(getShapeInternal());
	}

	@Override
	public Shape createShape() {
		Path2D path = new Path2D.Float();
		Vertices vertices = getVertices();
		for (int i = 0; i <  vertices.size(); i++) {
			if (i == 0)
				path.moveTo(vertices.getX(i), vertices.getY(i));
			else
				path.lineTo(vertices.getX(i), vertices.getY(i));
		}
		path.closePath();
		return path;
	}
	
	
	@Override
	public ROI updatePlane(ImagePlane plane) {
		return new PolygonROI(
				getAllPoints(),
				plane);
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getPolygonPoints()
	 */
	@Override
	public List<Point2> getAllPoints() {
		return vertices.getPoints();
	}
	
	
	Vertices getVertices() {
		return vertices;
	}
	
	
	void calculateShapeMeasurements() {
		stats = new ClosedShapeStatistics(vertices);
	}


	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getScaledArea(double, double)
	 */
	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getArea() * pixelWidth * pixelHeight;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(vertices, pixelWidth, pixelHeight).getArea();
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getScaledPerimeter(double, double)
	 */
	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getLength() * (pixelWidth + pixelHeight) * .5;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(vertices, pixelWidth, pixelHeight).getPerimeter();
	}


	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getBoundsX()
	 */
	@Override
	public double getBoundsX() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsX();
	}


	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getBoundsY()
	 */
	@Override
	public double getBoundsY() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsY();
	}


	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getBoundsWidth()
	 */
	@Override
	public double getBoundsWidth() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsWidth();
	}


	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getBoundsHeight()
	 */
	@Override
	public double getBoundsHeight() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsHeight();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		PolygonROI that = (PolygonROI) o;
		// TODO: Consider that this may be inefficient
		return Objects.equals(getImagePlane(), that.getImagePlane()) &&
				vertices.size() == that.vertices.size() &&
				Objects.equals(vertices.getPoints(), that.vertices.getPoints());
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			hashCode = Objects.hash(vertices.getPoints(), getImagePlane());
		}
		return hashCode;
	}

	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Proxy required for reading");
	}

	
	private static class SerializationProxy implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private final float[] x;
		private final float[] y;
		@SuppressWarnings("unused")
		private final String name;
		private final int c, z, t;
		
		private ClosedShapeStatistics stats;
		
//		static int nullCounter = 0;
		
		SerializationProxy(final PolygonROI roi) {
			this.x =  roi.vertices.getX(null);
			this.y =  roi.vertices.getY(null);
			this.name = null; // There used to be names... now there aren't
//			this.name = roi.getName();
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
			this.stats = roi.stats;
		}
		
		private Object readResolve() {
			PolygonROI roi = new PolygonROI(x, y, ImagePlane.getPlaneWithChannel(c, z, t), false);
			roi.stats = this.stats; // Doesn't matter if this is null...
			return roi;
		}
		
	}


	@Override
	public RoiType getRoiType() {
		return RoiType.AREA;
	}
	
	
	
}
