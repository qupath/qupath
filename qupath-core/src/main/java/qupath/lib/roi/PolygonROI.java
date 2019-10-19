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
import java.awt.geom.Path2D;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

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
	
//	final private static Logger logger = LoggerFactory.getLogger(PolygonROI.class);
	
	private Vertices vertices;
	
	transient private ROI convexHull = null;

	transient private ClosedShapeStatistics stats = null;
	

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
//		vertices = VerticesFactory.createMutableVertices();
//		vertices.add(x, y);
//		vertices.close();
	}
	
	PolygonROI(List<Point2> points, ImagePlane plane) {
		super(plane);
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
	
	
	PolygonROI(float[] x, float[] y, ImagePlane plane) {
		this(x, y, plane, true);
//		List<Point2> points = new ArrayList<>();
//		for (int i = 0; i < x.length; i++) {
//			points.add(new Point2(x[i], y[i]));
//		}
//		vertices = VerticesFactory.createMutableVertices(points.size()+1);
//		setPoints(points);
//		vertices.close();
	}
	
	
	PolygonROI(float[] x, float[] y, ImagePlane plane, boolean copyVertices) {
		super(plane);
		vertices = VerticesFactory.createVertices(x, y, copyVertices);
	}
	
	
//	public PolygonROI(Vertices vertices, int c, int z, int t) {
//		super(c, z, t);
//		this.vertices = Vertices.createMutableVertices(vertices.size());
//		setPoints(vertices.getPoints()); // TODO: Implement this more efficiency, if it remains...
//		isAdjusting = false;
//	}
	
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
				getAllPoints().stream().map(p -> RoiTools.scalePoint(p, scaleX, scaleY, originX, originY)).collect(Collectors.toList()),
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
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getArea();
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.PolygonROI#getPerimeter()
	 */
	@Override
	public double getLength() {
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
			PolygonROI roi = new PolygonROI(x, y, ImagePlane.getPlaneWithChannel(c, z, t), false);
			roi.stats = this.stats; // Doesn't matter if this is null...
//			if (roi.stats == null) {
//				System.err.println("Null count: " + (++nullCounter));
//			}
			return roi;
		}
		
	}


	@Override
	public RoiType getRoiType() {
		return RoiType.AREA;
	}
	
	
	
}
