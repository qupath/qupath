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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * ROI representing a collection of 2D points (distinct x,y coordinates).
 * 
 * @author Pete Bankhead
 *
 */
public class PointsROI extends AbstractPathROI implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final List<Point2> points = new ArrayList<>();
	
	private transient double xMin = Double.NaN, yMin = Double.NaN, xMax = Double.NaN, yMax = Double.NaN;
	private transient double xCentroid = Double.NaN, yCentroid = Double.NaN;
	private transient ROI convexHull = null;

	PointsROI() {
		this(Double.NaN, Double.NaN);
	}

	private PointsROI(double x, double y) {
		this(x, y, null);
	}
	
	PointsROI(double x, double y, ImagePlane plane) {
		super(plane);
		addPoint(x, y);
		recomputeBounds();
	}
	
	PointsROI(List<? extends Point2> points, ImagePlane plane) {
		super(plane);
		for (Point2 p : points)
			addPoint(p.getX(), p.getY());
		recomputeBounds();
	}
	
	private PointsROI(float[] x, float[] y, ImagePlane plane) {
		super(plane);
		if (x.length != y.length)
			throw new IllegalArgumentException("Lengths of x and y arrays are not the same! " + x.length + " and " + y.length);
		for (int i = 0; i < x.length; i++)
			addPoint(x[i], y[i]);
		recomputeBounds();
	}


	@Override
	public double getCentroidX() {
		if (points.isEmpty())
			return Double.NaN;
		if (Double.isNaN(xCentroid))
			computeCentroid();
		return xCentroid;
	}

	@Override
	public double getCentroidY() {
		if (points.isEmpty())
			return Double.NaN;
		if (Double.isNaN(yCentroid))
			computeCentroid();
		return yCentroid;
	}
	
	
	private void computeCentroid() {
		double xSum = 0;
		double ySum = 0;
		int n = points.size();
		for (Point2 p : points) {
			xSum += p.getX() / n;
			ySum += p.getY() / n;
		}
		xCentroid = xSum;
		yCentroid = ySum;
	}


	/**
	 * Identify the closest point within a specified distance to coordinates x,y - or null if no points are found.
	 * @param x
	 * @param y
	 * @param maxDist
	 * @return
	 */
	public Point2 getNearest(double x, double y, double maxDist) {
		double maxDistSq = maxDist * maxDist;
		Point2 pClosest = null;
		double distClosestSq = Double.POSITIVE_INFINITY;
		for (Point2 p : points) {
			double distSq = p.distanceSq(x, y);
			if (distSq <= maxDistSq && distSq < distClosestSq) {
				pClosest = p;
				distClosestSq = distSq;
			}
		}
		return pClosest;
	}
	
	
	protected void recomputeBounds() {
		if (points.isEmpty()) {
			resetBounds();
			return;
		}
		xMin = Double.POSITIVE_INFINITY;
		yMin = Double.POSITIVE_INFINITY;
		xMax = Double.NEGATIVE_INFINITY;
		yMax = Double.NEGATIVE_INFINITY;
		for (Point2 p : points) {
			updateBounds(p.getX(), p.getY());
		}
	}
	
	
	protected void updateBounds(final double x, final double y) {
		if (x < xMin)
			xMin = x;
		if (x > xMax)
			xMax = x;
		if (y < yMin)
			yMin = y;
		if (y > yMax)
			yMax = y;
	}
	
	private void addPoint(double x, double y) {
//		addPoint(x, y, -1);
		// Can't add NaN
		if (Double.isNaN(x) || Double.isNaN(y))
			return;
		points.add(new Point2(x, y));
	}


	/**
	 * A Points ROI is empty if it contains no points (*not* if its bounds have no width or height...
	 * since this would occur for a single-point ROI).
	 */
	@Override
	public boolean isEmpty() {
		return points.isEmpty();
	}
	
	
	@Override
	public String getRoiName() {
		return "Points";
	}
	
	@Override
	public String toString() {
		return String.format("%s (%d points)", getRoiName(), points.size());
	}
	
	@Override
	public int getNumPoints() {
		return points.size();
	}
	
	@Override
	public List<Point2> getAllPoints() {
		return Collections.unmodifiableList(points);
	}
	
	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		return new PointsROI(
				getAllPoints().stream().map(p -> RoiTools.scalePoint(p, scaleX, scaleY, originX, originY)).toList(),
				getImagePlane());
	}
	
	@Override
	@Deprecated
	public ROI duplicate() {
		return new PointsROI(points, getImagePlane());
	}

	@Override
	public ROI getConvexHull() {
		if (convexHull == null) {
			if (points.isEmpty())
				return null;
			convexHull = new PolygonROI(ConvexHull.getConvexHull(points));
//			convexHull.setStrokeColor(null);
		}
		return convexHull;
	}

	
	private void resetBounds() {
		xMin = Double.NaN;
		yMin = Double.NaN;
		xMax = Double.NaN;
		yMax = Double.NaN;		
	}

	@Override
	public double getBoundsX() {
		return xMin;
	}

	@Override
	public double getBoundsY() {
		return yMin;
	}

	@Override
	public double getBoundsWidth() {
		return xMax - xMin;
	}

	@Override
	public double getBoundsHeight() {
		return yMax - yMin;
	}
	
	/**
	 * It is not possible to convert a PointROI to a java.awt.Shape.
	 * throws UnsupportedOperationException
	 */
	@Override
	public Shape getShape() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("PointROI does not support getShape()!");
	}

	@Override
	protected Shape createShape() {
		throw new UnsupportedOperationException("PointROI does not support createShape()!");
	}
	
	
	@Override
	public RoiType getRoiType() {
		return RoiType.POINT;
	}
	
	
	@Override
	public ROI updatePlane(ImagePlane plane) {
		return new PointsROI(
				points,
				plane);
	}


	/**
	 * Test if this ROI is equal to another.
	 * Note that this requires the other object to be a PointsROI on the same plane, with the same points in the
	 * same order.
	 * @param o the object to test
	 * @return true if the objects are equal, false otherwise
	 */
	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		PointsROI pointsROI = (PointsROI) o;
		return Objects.equals(points, pointsROI.points)
				&& Objects.equals(getImagePlane(), pointsROI.getImagePlane());
	}

	@Override
	public int hashCode() {
		return Objects.hash(points, getImagePlane());
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
		
		SerializationProxy(final PointsROI roi) {
			int n = roi.getNumPoints();
			this.x =  new float[n];
			this.y =  new float[n];
			int ind = 0;
			for (Point2 p : roi.points) {
				x[ind] = (float)p.getX();
				y[ind] = (float)p.getY();
				ind++;
			}
			this.name = null; // There used to be names... now there aren't
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
		}
		
		private Object readResolve() {
			PointsROI roi = new PointsROI(x, y, ImagePlane.getPlaneWithChannel(c, z, t));
			return roi;
		}
		
	}


	@Override
	public ROI translate(double dx, double dy) {
		return new PointsROI(
				points.stream().map(p -> new Point2(p.getX()+dx, p.getY()+dy)).toList(),
				getImagePlane());
	}
	
	@Override
	public double getArea() {
		return 0;
	}
	
	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		return 0;
	}
	
	@Override
	public boolean contains(double x, double y) {
		return false;
	}

	@Override
	public boolean intersects(double x, double y, double width, double height) {
		for (var p : points) {
			double px = p.getX();
			double py = p.getY();
			// Note that other classes (e.g. Rectangle, Polygon) exclude on boundaries
			if (px > x && py > y && px < x + width && py < y + height) {
				return true;
			}
		}
		return false;
	}

	@Override
	public double getLength() {
		return 0;
	}

	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		return 0;
	}

}
