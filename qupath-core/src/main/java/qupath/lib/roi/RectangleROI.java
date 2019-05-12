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
import java.awt.geom.Rectangle2D;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;

/**
 * ROI representing a square or rectangle (unrotated).
 * 
 * @author Pete Bankhead
 *
 */
public class RectangleROI extends AbstractPathBoundedROI implements PathArea, Serializable {

	private static final long serialVersionUID = 1L;
	
	RectangleROI() {
		super();
	}

	RectangleROI(double x, double y, double width, double height) {
		this(x, y, width, height, -1, 0, 0);
	}

	RectangleROI(double x, double y, double width, double height, int c, int z, int t) {
		super(x, y, width, height, c, z, t);
	}
	
	@Override
	public boolean contains(double x, double y) {
		return x >= this.x && x < x2 && y >= this.y && y < y2;
	}
	
	@Override
	public String getRoiName() {
		return "Rectangle";
	}
	

	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		return getBoundsWidth() * getBoundsHeight() * pixelWidth * pixelHeight;
	}
	
	@Override
	public double getScaledPerimeter(double pixelWidth, double pixelHeight) {
		return (getBoundsWidth() * pixelWidth + getBoundsHeight() * pixelHeight) * 2;
	}

	@Override
	public ROI duplicate() {
		RectangleROI duplicate = new RectangleROI();
		duplicate.x = x;
		duplicate.x2 = x2;
		duplicate.y= y;
		duplicate.y2 = y2;
		duplicate.c = c;
		duplicate.z = z;
		duplicate.t = t;
		return duplicate;
	}

	@Override
	public List<Point2> getPolygonPoints() {
		return Arrays.asList(new Point2(x, y),
				new Point2(x2, y),
				new Point2(x2, y2),
				new Point2(x, y2));
	}

//	@Override
//	public Rectangle2D getShape() {
//		return new Rectangle2D.Double(getBoundsX(), getBoundsY(), getBoundsWidth(), getBoundsHeight());
//	}
	
	
	@Override
	public Shape getShape() {
		return new Rectangle2D.Double(x, y, x2-x, y2-y);
	}
	
	
	@Override
	public TranslatableROI translate(double dx, double dy) {
		if (dx == 0 && dy == 0)
			return this;
		// Shift the bounds
		return new RectangleROI(getBoundsX()+dx, getBoundsY()+dy, getBoundsWidth(), getBoundsHeight(), getC(), getZ(), getT());
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
		
		SerializationProxy(final RectangleROI roi) {
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
			RectangleROI roi = new RectangleROI(x, y, x2-x, y2-y, c, z, t);
			return roi;
		}
		
	}
	
	
}