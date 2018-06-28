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

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;

/**
 * ROI representing a straight line, defined by its end points.
 * 
 * @author Pete Bankhead
 *
 */
public class LineROI extends AbstractPathROI implements PathLine, TranslatableROI, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private double x = Double.NaN, y = Double.NaN, x2 = Double.NaN, y2 = Double.NaN;
	
	protected LineROI() {
		super();
	}
	
	public LineROI(double x, double y) {
		this(x, y, x, y);
	}
	
	public LineROI(double x, double y, double x2, double y2) {
		this(x, y, x2, y2, -1, 0, 0);
	}
	
	public LineROI(double x, double y, double x2, double y2, int c, int z, int t) {
		super(c, z, t);
		this.x = x;
		this.y = y;
		this.x2 = x2;
		this.y2 = y2;
	}
	
	/* (non-Javadoc)
	 * @see qupath.lib.rois.LineROI#getROIType()
	 */
	@Override
	public String getROIType() {
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
	public ROI duplicate() {
		return new LineROI(x, y, x2, y2, getC(), getZ(), getT());
	}
	

	public double getX1() {
		return x;
	}


	public double getY1() {
		return y;
	}


	public double getX2() {
		return x2;
	}


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
	public TranslatableROI translate(double dx, double dy) {
		if (dx == 0 && dy == 0)
			return this;
		// Shift the bounds
		return new LineROI(x+dx, y+dy, x2+dx, y2+dy, getC(), getZ(), getT());
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
	public List<Point2> getPolygonPoints() {
		return Arrays.asList(new Point2(x, y),
				new Point2(x2, y2));
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
			LineROI roi = new LineROI(x, y, x2, y2, c, z, t);
//			if (name != null)
//				roi.setName(name);
			return roi;
		}
		
	}
	
}