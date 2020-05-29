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
import java.awt.geom.Ellipse2D;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * ROI implementing a circle, or (unrotated) ellipse.
 * 
 * @author Pete Bankhead
 *
 */
public class EllipseROI extends AbstractPathBoundedROI implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	EllipseROI() {
		super();
	}

	EllipseROI(double x, double y, double width, double height, ImagePlane plane) {
		super(x, y, width, height, plane);
	}
	
	@Override
	public boolean contains(double xx, double yy) {
		// See http://math.stackexchange.com/questions/76457/check-if-a-point-is-within-an-ellipse
		double dx = xx - getCentroidX();
		double dy = yy - getCentroidY();
		double rx = getBoundsWidth() * 0.5;
		double ry = getBoundsHeight() * 0.5;
		return (dx*dx/(rx*rx) + dy*dy/(ry*ry)) <= 1;
	}
	
	@Override
	public String getRoiName() {
		return "Ellipse";
	}
	
	/**
	 * Query if the width and height of the ellipse bounding box are the same, assuming 'square' pixels.
	 * @return
	 */
	public boolean isCircle() {
		return isCircle(1, 1);
	}
	
	/**
	 * Query if the width and height of the ellipse bounding box are the same, optionally using 'non-square' pixels.
	 * @param pixelWidth 
	 * @param pixelHeight 
	 * @return
	 */
	public boolean isCircle(double pixelWidth, double pixelHeight) {
		return GeneralTools.almostTheSame(getBoundsWidth() * pixelWidth, getBoundsHeight() * pixelHeight, 0.00001);
	}
	
	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		double a = getBoundsWidth() * pixelWidth * .5;
		double b = getBoundsHeight() * pixelHeight * .5;
		return Math.PI * a * b;
	}
	
	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (isCircle(pixelWidth, pixelHeight))
			return Math.PI * getBoundsWidth() * pixelWidth;
		// See circumference approximations at http://en.wikipedia.org/wiki/Ellipse#Circumference
		double a = getBoundsWidth() * pixelWidth * .5;
		double b = getBoundsHeight() * pixelHeight * .5;
		double h = (a - b)*(a - b) / ((a + b) * (a + b));		
		return Math.PI * (a + b) * (1 + 3 * h / (10 + Math.sqrt(4 - 3 * h)));
	}
	
	/**
	 * Returns 4 (since the ellipse is defined by its bounding box).
	 * Note this behavior may change.
	 * @return
	 */
	@Override
	public int getNumPoints() {
		return 4;
	}
	
	@Override
	@Deprecated
	public ROI duplicate() {
		EllipseROI duplicate = new EllipseROI();
		duplicate.x = x;
		duplicate.x2 = x2;
		duplicate.y= y;
		duplicate.y2 = y2;
		duplicate.c = c;
		duplicate.z = z;
		duplicate.t = t;
		return duplicate;
	}

	// TODO: Fix the ellipse polygon points to make it less diamond-y
	/**
	 * Since ellipses aren't represented internally with simple polygon points, 
	 * this currently returns only 4 points (rather more diamond-like that would be ideal).
	 * This behavior may change.
	 */
	@Override
	public List<Point2> getAllPoints() {
		return Arrays.asList(new Point2(x/2+x2/2, y),
				new Point2(x2, y/2+y2/2),
				new Point2(x/2+x2/2, y2),
				new Point2(x, y/2+y2/2));
	}

	
	@Override
	public Shape getShape() {
		return new Ellipse2D.Double(x, y, x2-x, y2-y);
	}
	
//	@Override
//	public double getMaxDiameter() {
//		return Math.max(getBoundsWidth(), getBoundsHeight());
//	}
//	
//	@Override
//	public double getMinDiameter() {
//		return Math.min(getBoundsWidth(), getBoundsHeight());
//	}
//	
//	@Override
//	public double getScaledMaxDiameter(double pixelWidth, double pixelHeight) {
//		return Math.max(getBoundsWidth()*pixelWidth, getBoundsHeight()*pixelHeight);
//	}
//	
//	@Override
//	public double getScaledMinDiameter(double pixelWidth, double pixelHeight) {
//		return Math.min(getBoundsWidth()*pixelWidth, getBoundsHeight()*pixelHeight);
//	}
	
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
		
		SerializationProxy(final EllipseROI roi) {
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
			EllipseROI roi = new EllipseROI(x, y, x2-x, y2-y, ImagePlane.getPlaneWithChannel(c, z, t));
			return roi;
		}
		
	}
	
	@Override
	public ROI getConvexHull() {
		return this;
	}
	
	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		double x1 = RoiTools.scaleOrdinate(getBoundsX(), scaleX, originX);
		double y1 = RoiTools.scaleOrdinate(getBoundsY(), scaleY, originY);
		double x2 = RoiTools.scaleOrdinate(getBoundsX() + getBoundsWidth(), scaleX, originX);
		double y2 = RoiTools.scaleOrdinate(getBoundsY() + getBoundsHeight(), scaleY, originY);
		return new EllipseROI(x1, y1, x2-x1, y2-y1, getImagePlane());
	}


	@Override
	public ROI translate(double dx, double dy) {
		if (dx == 0 && dy == 0)
			return this;
		// Shift the bounds
		return new EllipseROI(getBoundsX()+dx, getBoundsY()+dy, getBoundsWidth(), getBoundsHeight(), getImagePlane());
	}

	@Override
	public RoiType getRoiType() {
		return RoiType.AREA;
	}


}