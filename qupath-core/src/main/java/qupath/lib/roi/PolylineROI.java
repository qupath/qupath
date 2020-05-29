/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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
import java.util.List;
import java.util.stream.Collectors;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * ROI representing an arbitrary open polyline.
 * 
 * @see PolygonROI
 * 
 * @author Pete Bankhead
 *
 */
public class PolylineROI extends AbstractPathROI implements Serializable {

	private static final long serialVersionUID = 1L;
	
//	final private static Logger logger = LoggerFactory.getLogger(PolylineROI.class);
	
	private Vertices vertices;
	
	private transient PolylineStats stats;
	
	PolylineROI(List<Point2> points, ImagePlane plane) {
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
	
	PolylineROI(final float[] x, final float[] y, ImagePlane plane) {
		this(x, y, plane, true);
	}
	
	private PolylineROI(final float[] x, final float[] y, ImagePlane plane, boolean copyVertices) {
		super(plane);
		this.vertices = VerticesFactory.createVertices(x, y, copyVertices);
	}
		
	@Override
	public String getRoiName() {
		return "Polyline";
	}

	/**
	 * The centroid for a {@code Polyline} here is represented by the centroid of its bounding box.
	 */
	@Override
	public double getCentroidX() {
		return getStats().centroidX;
	}

	/**
	 * The centroid for a {@code Polyline} here is represented by the centroid of its bounding box.
	 */
	@Override
	public double getCentroidY() {
		return getStats().centroidY;
	}

	@Override
	public double getBoundsX() {
		return getStats().boundsX;
	}

	@Override
	public double getBoundsY() {
		return getStats().boundsY;
	}

	@Override
	public double getBoundsWidth() {
		return getStats().boundsWidth;
	}

	@Override
	public double getBoundsHeight() {
		return getStats().boundsHeight;
	}

	@Override
	public List<Point2> getAllPoints() {
		return vertices.getPoints();
	}
	
	Vertices getVertices() {
		return vertices;
	}

	@Override
	@Deprecated
	public ROI duplicate() {
		return new PolylineROI(vertices.getX(null), vertices.getY(null), getImagePlane());
	}

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
		return new PolylineROI(x, y, getImagePlane(), false);
	}

	@Override
	public double getLength() {
		return getStats().length;
	}

	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (pixelWidth == 1 && pixelHeight == 1)
			return getLength();
		else if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0000001))
			return getLength() * ((pixelWidth + pixelHeight) / 2.0);
		return new PolylineStats(vertices, pixelWidth, pixelHeight).length;
	}
	
	
	private PolylineStats getStats() {
		if (stats == null)
			stats = new PolylineStats(this.vertices, 1.0, 1.0);
		return stats;
	}
	
	
	private static class PolylineStats {
		
		private double boundsX = Double.NaN, boundsY = Double.NaN, boundsWidth = 0, boundsHeight = 0;
		private double centroidX = Double.NaN, centroidY = Double.NaN;
		private double length = 0;
		
		PolylineStats(final Vertices vertices, final double pixelWidth, final double pixelHeight) {
			if (vertices.size() == 0) {
				return;
			}
			this.length = 0;
			double x = vertices.getX(0);
			double y = vertices.getY(0);

			double xMin = x;
			double xMax = x;
			double yMin = y;
			double yMax = y;
			
			double sumCenterX = 0;
			double sumCenterY = 0;
			
			for (int i = 1; i < vertices.size(); i++) {
				double x2 = vertices.getX(i);
				double y2 = vertices.getY(i);
				double dx = (x2 - x) * pixelWidth;
				double dy = (y2 - y) * pixelHeight;
				double segLength = Math.sqrt(dx*dx + dy*dy);
				this.length += segLength;
				
				double xCenter = (x + x2) / 2.0;
				double yCenter = (y + y2) / 2.0;
				
				sumCenterX += xCenter * segLength;
				sumCenterY += yCenter * segLength;
				
				x = x2;
				y = y2;
				if (x < xMin)
					xMin = x;
				if (y < yMin)
					yMin = y;
				if (x > xMax)
					xMax = x;
				if (y > yMax)
					yMax = y;
			}
			this.boundsX = xMin;
			this.boundsY = yMin;
			this.boundsWidth = xMax - xMin;
			this.boundsHeight = yMax - yMin;
			
			this.centroidX = sumCenterX / this.length;
			this.centroidY = sumCenterY / this.length;
			
			assert this.centroidX >= this.boundsX && this.centroidX <= this.boundsX + this.boundsWidth;
			assert this.centroidY >= this.boundsY && this.centroidY <= this.boundsY + this.boundsHeight;
//			this.centroidX = boundsX + boundsWidth / 2.0;
//			this.centroidY = boundsY + boundsHeight / 2.0;
		}
		
	}
	
	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		return new PolylineROI(
				getAllPoints().stream().map(p -> RoiTools.scalePoint(p, scaleX, scaleY, originX, originY)).collect(Collectors.toList()),
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
	public RoiType getRoiType() {
		return RoiType.LINE;
	}
	
	
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
		return path;
	}
	
	@Override
	public boolean contains(double x, double y) {
		return false;
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
		private final int c, z, t;
		
		SerializationProxy(final PolylineROI roi) {
			this.x =  roi.vertices.getX(null);
			this.y =  roi.vertices.getY(null);
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
		}
		
		private Object readResolve() {
			return new PolylineROI(x, y, ImagePlane.getPlaneWithChannel(c, z, t), false);
		}
		
	}
	

}
