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
import java.awt.geom.Line2D;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * ROI representing a straight line, defined by its end points.
 * 
 * @author Pete Bankhead
 *
 */
public class LineROI extends AbstractPathROI implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private double x = Double.NaN, y = Double.NaN, x2 = Double.NaN, y2 = Double.NaN;
	
	LineROI() {
		super();
	}
	
	LineROI(double x, double y, double x2, double y2) {
		this(x, y, x2, y2, null);
	}
	
	LineROI(double x, double y, double x2, double y2, ImagePlane plane) {
		super(plane);
		this.x = x;
		this.y = y;
		this.x2 = x2;
		this.y2 = y2;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getROIType()
	 */
	@Override
	public String getRoiName() {
		return "Line";
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getLength()
	 */
	@Override
	public double getLength() {
		return getScaledLength(1, 1);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getScaledLength(double, double)
	 */
	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		double dx = (x2 - x) * pixelWidth;
		double dy = (y2 - y) * pixelHeight;
		return Math.sqrt(dx*dx + dy*dy);
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#duplicate()
	 */
	@Override
	@Deprecated
	public ROI duplicate() {
		return new LineROI(x, y, x2, y2, getImagePlane());
	}
	
	/**
	 * Returns 2 (since the line is defined by its end points).
	 * @return
	 */
	@Override
	public int getNumPoints() {
		return 2;
	}
	
	/**
	 * Get the first x co-ordinate (start of the line).
	 * @return
	 */
	public double getX1() {
		return x;
	}

	/**
	 * Get the first y co-ordinate (start of the line).
	 * @return
	 */
	public double getY1() {
		return y;
	}

	/**
	 * Get the second x co-ordinate (end of the line).
	 * @return
	 */
	public double getX2() {
		return x2;
	}

	/**
	 * Get the second y co-ordinate (end of the line).
	 * @return
	 */
	public double getY2() {
		return y2;
	}

	
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return x == x2 && y == y2;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getCentroidX()
	 */
	@Override
	public double getCentroidX() {
		return 0.5 * (x + x2);
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getCentroidY()
	 */
	@Override
	public double getCentroidY() {
		return 0.5 * (y + y2);
	}

	@Override
	public ROI translate(double dx, double dy) {
		if (dx == 0 && dy == 0)
			return this;
		// Shift the bounds
		return new LineROI(x+dx, y+dy, x2+dx, y2+dy, getImagePlane());
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getBoundsX()
	 */
	@Override
	public double getBoundsX() {
		return x < x2 ? x : x2;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getBoundsY()
	 */
	@Override
	public double getBoundsY() {
		return y < y2 ? y : y2;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getBoundsWidth()
	 */
	@Override
	public double getBoundsWidth() {
		return Math.abs(x - x2);
	}

	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getBoundsHeight()
	 */
	@Override
	public double getBoundsHeight() {
		return Math.abs(y - y2);
	}
	
	@Override
	public List<Point2> getAllPoints() {
		return Arrays.asList(new Point2(x, y),
				new Point2(x2, y2));
	}
	
	
	@Override
	public Shape getShape() {
		return new Line2D.Double(x, y, x2, y2);
	}
	
	@Override
	public ROI updatePlane(ImagePlane plane) {
		return new LineROI(
				x, y, x2, y2,
				plane);
	}
	
	
//	public Geometry getGeometry() {
//		GeometryFactory factory = new GeometryFactory();
//	}
	
	
	@Override
	public RoiType getRoiType() {
		return RoiType.LINE;
	}
	
	@Override
	public ROI getConvexHull() {
		return this;
	}
	
	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Proxy required for reading");
	}
	
	
	private static class SerializationProxy implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private final double x, x2, y, y2;
		private final String name;
		private final int c, z, t;
		
		SerializationProxy(final LineROI roi) {
			this.x =  roi.x;
			this.x2 =  roi.x2;
			this.y =  roi.y;
			this.y2 =  roi.y2;
			this.name = null; // There used to be names... now there aren't
//			this.name = roi.getName();
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
		}
		
		private Object readResolve() {
			LineROI roi = new LineROI(x, y, x2, y2, ImagePlane.getPlaneWithChannel(c, z, t));
//			if (name != null)
//				roi.setName(name);
			return roi;
		}
		
	}

//	@Override
//	public double getMaxDiameter() {
//		return getLength();
//	}
//	
//	@Override
//	public double getMinDiameter() {
//		return 0;
//	}
//	
//	@Override
//	public double getScaledMaxDiameter(double pixelWidth, double pixelHeight) {
//		return getScaledLength(pixelWidth, pixelHeight);
//	}
//	
//	@Override
//	public double getScaledMinDiameter(double pixelWidth, double pixelHeight) {
//		return 0;
//	}

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
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		return new LineROI(
				RoiTools.scaleOrdinate(x, scaleX, originX),
				RoiTools.scaleOrdinate(y, scaleY, originY),
				RoiTools.scaleOrdinate(x2, scaleX, originX),
				RoiTools.scaleOrdinate(y2, scaleY, originY),
				getImagePlane()
				);
	}
	
}