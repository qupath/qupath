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

package qupath.lib.roi;

import java.awt.Shape;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROIWithHull;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;
import qupath.lib.rois.measure.ConvexHull;


/**
 * ROI representing an arbitrary closed polygon.
 * 
 * @see Polyline
 * 
 * @author Pete Bankhead
 *
 */
public class PolygonROI extends AbstractPathAreaROI implements ROIWithHull, TranslatableROI, Serializable {
	
	private static final long serialVersionUID = 1L;
	
//	final private static Logger logger = LoggerFactory.getLogger(PolygonROI.class);
	
	private Vertices vertices;
	
	transient protected PathArea convexHull = null;

	transient ClosedShapeStatistics stats = null;
	

	PolygonROI() {
		super();
	}
	
	
	/**
	 * Constructor that sets the ROI to being modified from the start (i.e. initialized from a single mouse event)
	 * @param x
	 * @param y
	 */
	PolygonROI(double x, double y) {
		this(x, y, -1, 0, 0);
	}
	
	PolygonROI(List<Point2> points) {
		this(points, -1, 0, 0);
	}
	
	
	PolygonROI(double x, double y, int c, int z, int t) {
		super(c, z, t);
		vertices = VerticesFactory.createVertices(new float[]{(float)x, (float)x}, new float[]{(float)y, (float)y}, false);
//		vertices = VerticesFactory.createMutableVertices();
//		vertices.add(x, y);
//		vertices.close();
	}
	
	PolygonROI(List<Point2> points, int c, int z, int t) {
		super(c, z, t);
//		vertices = VerticesFactory.createMutableVertices(points.size()+1);
//		setPoints(points);
//		vertices.close();
		float[] x = new float[points.size()];
		float[] y = new float[points.size()];
		for (int i = 0; i < points.size(); i++) {
			Point2 p = points.get(i);
			x[i] = (float)p.getX();
			y[i] = (float)p.getY();
		}
		vertices = VerticesFactory.createVertices(x, y, false);
	}
	
	
	PolygonROI(float[] x, float[] y, int c, int z, int t) {
		this(x, y, c, z, t, true);
//		List<Point2> points = new ArrayList<>();
//		for (int i = 0; i < x.length; i++) {
//			points.add(new Point2(x[i], y[i]));
//		}
//		vertices = VerticesFactory.createMutableVertices(points.size()+1);
//		setPoints(points);
//		vertices.close();
	}
	
	
	PolygonROI(float[] x, float[] y, int c, int z, int t, boolean copyVertices) {
		super(c, z, t);
		vertices = VerticesFactory.createVertices(x, y, copyVertices);
	}
	
	
//	public PolygonROI(Vertices vertices, int c, int z, int t) {
//		super(c, z, t);
//		this.vertices = Vertices.createMutableVertices(vertices.size());
//		setPoints(vertices.getPoints()); // TODO: Implement this more efficiency, if it remains...
//		isAdjusting = false;
//	}
	
	
	public int nVertices() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getNVertices();
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#duplicate()
	 */
	@Override
	public ROI duplicate() {
		return new PolygonROI(vertices.getPoints(), getC(), getZ(), getT());
	}
	

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getCentroidX()
	 */
	@Override
	public double getCentroidX() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getCentroidX();
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getCentroidY()
	 */
	@Override
	public double getCentroidY() {
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
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#translate(double, double)
	 */
	@Override
	public TranslatableROI translate(double dx, double dy) {
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
		return new PolygonROI(x, y, getC(), getZ(), getT());
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
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getArea();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getPerimeter()
	 */
	@Override
	public double getPerimeter() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getPerimeter();
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getConvexArea()
	 */
	@Override
	public double getConvexArea() {
		PathArea hull = getConvexHull();
		if (hull != null)
			return hull.getArea();
		return Double.NaN;
	}

	public double getSolidity() {
		return getArea() / getConvexArea();
	}
	
	public double getSolidity(double pixelWidth, double pixelHeight) {
		return getScaledArea(pixelWidth, pixelHeight) / getScaledConvexArea(pixelWidth, pixelHeight);
	}

	
	@Override
	public Shape getShape() {
		return PathROIToolsAwt.getShape(this);
	}
	
	
//	protected void resetCachedMeasurements() {
//		stats = null;
//		// Reset convex hull
//		convexHull = null;
//	}
	
	
//	protected boolean hasMeasurements() {
//		return stats == null;
//	}
	

//	// TODO: Consider that this isn't very efficient code at all
//	// pointAdditional is an optional extra point that hasn't been used to add the path yet -
//	// so this may be used to make the decision as to whether to add the point or not
//	protected boolean calculateIsSelfIntersecting(Point2D pointAdditional) {
//		List<Point2D> points = getLinearPathPoints(path);
//		// Add point during creation if necessary
//		if (pointAdditional != null)
//			points.add(pointAdditional);
//		int n = points.size();
//		Line2D line1 = new Line2D.Float();
//		Line2D line2 = new Line2D.Float();
//		for (int i = 0; i < n; i++) {
//			// Get the first line segment
//			line1.setLine(points.get(i), points.get((i+1) % n));
//			for (int j = 0; j < n; j++) {
//				// Don't compare point with itself... or its immediate neighbors
//				if (Math.abs(i - j) <= 1)
//					continue;
//				line2.setLine(points.get(i), points.get((i+1) % n));
//				if (line1.getP1().equals(line2.getP2()) || line1.getP2().equals(line2.getP1()))
//					continue;
//				if (line1.intersectsLine(line2)) {
//					// TODO: Check the self-intersection code for polygons - could be improved, may fail for some shared points
//					// (simplifying polygons would help)
//					// Check for shared end points - we won't count these as intersections
//					if (line1.getP1().equals(line2.getP1()) || line1.getP1().equals(line2.getP2()) || line1.getP2().equals(line2.getP1()) || line1.getP2().equals(line2.getP2()))
//						continue;
////					logger.info(String.format("plot([%.0f, %.0f], [%.0f, %.0f]); line([%.0f, %.0f], [%.0f, %.0f], 'color', 'r')", line1.getX1(), line1.getY1(), line1.getX2(), line1.getY2(),
////							line2.getX1(), line2.getY1(), line2.getX2(), line2.getY2()));
////					logger.info(String.format("Line 1: %s, %s", line1.getP1().toString(), line1.getP2().toString()));
////					logger.info(String.format("Line 2: %s, %s", line2.getP1().toString(), line2.getP2().toString()));
////					for (Point2D p : points)
////						logger.info(String.format("%.2f, %.2f;", p.getX(), p.getY()));
//					return true;
//				}
//			}
//		}
//		return false;
//	}
	
	
//	/**
//	 * Check if the polygon is self-intersecting.
//	 * If so, the measurements are not going to be very reliable - and should not be shown.
//	 * 
//	 * @return
//	 */
//	public boolean isSelfIntersecting() {
//		if (!hasMeasurements())
//			calculateShapeMeasurements();
//		return isSelfIntersecting;
//	}

	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getConvexHull()
	 */
	@Override
	public PathArea getConvexHull() {
		if (convexHull == null) {
			List<Point2> points = getPolygonPoints();
			List<Point2> convexPoints = ConvexHull.getConvexHull(points);
			// The 'containsAll' test probably doesn't need to be there...
			if (points.size() >= convexPoints.size()-1 && convexPoints.containsAll(points))
				convexHull = this;
			else
				convexHull = new PolygonROI(convexPoints);
//			convexHull.setStrokeColor(null);
		}
		return convexHull;
	}
	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getPolygonPoints()
	 */
	@Override
	public List<Point2> getPolygonPoints() {
		return vertices.getPoints();
	}
	
	
	public Vertices getVertices() {
		return vertices;
	}
	
//	public VerticesIterator getVerticesIterator() {
//		return vertices.getIterator();
//	}
	
	
	void calculateShapeMeasurements() {
		stats = new ClosedShapeStatistics(vertices);
	}
	
	
//	void setPoints(List<Point2> points) {
//		vertices.clear();
//		vertices.ensureCapacity(points.size() + 1);
//		for (Point2 p : points) {
//			vertices.add(p.getX(), p.getY());
//		}
//		vertices.close();
//		resetCachedMeasurements();
//	}


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
	public double getScaledPerimeter(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getPerimeter() * (pixelWidth + pixelHeight) * .5;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(vertices, pixelWidth, pixelHeight).getPerimeter();
	}


	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getScaledConvexArea(double, double)
	 */
	@Override
	public double getScaledConvexArea(double pixelWidth, double pixelHeight) {
		PathArea hull = getConvexHull();
		if (hull == null)
			return Double.NaN;
		return hull.getScaledArea(pixelWidth, pixelHeight);
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


//	@Override
//	public void writeExternal(ObjectOutput out) throws IOException {
//		// Write x & y arrays (reusing same array)
//		float[] x = vertices.getX(null);
//		super.wr
//		out.writeObject(x);
//		out.writeObject(vertices.getY(x));
//	}
//
//
//	@Override
//	public void readExternal(ObjectInput in) throws IOException,
//			ClassNotFoundException {
//		// TODO Auto-generated method stub
//		
//	}
	
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
			PolygonROI roi = new PolygonROI(x, y, c, z, t, false);
			roi.stats = this.stats; // Doesn't matter if this is null...
//			if (roi.stats == null) {
//				System.err.println("Null count: " + (++nullCounter));
//			}
			return roi;
		}
		
	}
	
	
	
}
