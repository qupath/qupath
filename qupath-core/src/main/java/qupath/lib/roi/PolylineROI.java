/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2017 - 2018 the QuPath contributors.
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
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;
import qupath.lib.rois.vertices.Vertices;

public class PolylineROI extends AbstractPathROI implements PathLine, TranslatableROI, Serializable {

	private static final long serialVersionUID = 1L;
	
//	final private static Logger logger = LoggerFactory.getLogger(PolylineROI.class);
	
	private Vertices vertices;
	
	private transient PolylineStats stats;
	
	public PolylineROI(double x, double y, int c, int z, int t) {
		this((float)x, (float)y, c, z, t);
	}
	
	public PolylineROI(float x, float y, int c, int z, int t) {
		this(new float[]{x}, new float[]{y}, c, z, t, false);
	}
	
	public PolylineROI(List<Point2> points, int c, int z, int t) {
		super(c, z, t);
		float[] x = new float[points.size()];
		float[] y = new float[points.size()];
		for (int i = 0; i < points.size(); i++) {
			Point2 p = points.get(i);
			x[i] = (float)p.getX();
			y[i] = (float)p.getY();
		}
		vertices = VerticesFactory.createVertices(x, y, false);
	}
	
	public PolylineROI(final float[] x, final float[] y, final int c, final int z, final int t) {
		this(x, y, c, z, t, true);
	}
	
	private PolylineROI(final float[] x, final float[] y, final int c, final int z, final int t, boolean copyVertices) {
		super(c, z, t);
		this.vertices = VerticesFactory.createVertices(x, y, copyVertices);
	}
		
	@Override
	public String getROIType() {
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
	public List<Point2> getPolygonPoints() {
		return vertices.getPoints();
	}
	
	public Vertices getVertices() {
		return vertices;
	}

	@Override
	public ROI duplicate() {
		return new PolylineROI(vertices.getX(null), vertices.getY(null), getC(), getZ(), getT());
	}

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
		return new PolylineROI(x, y, getC(), getZ(), getT(), false);
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
			double x = vertices.getX(0) * pixelWidth;
			double y = vertices.getY(0) * pixelHeight;

			double xMin = x;
			double xMax = x;
			double yMin = y;
			double yMax = y;
			
			for (int i = 1; i < vertices.size(); i++) {
				double x2 = vertices.getX(i) * pixelWidth;
				double y2 = vertices.getY(i) * pixelHeight;
				double dx = (x2 - x) * pixelWidth;
				double dy = (y2 - y) * pixelHeight;
				this.length += Math.sqrt(dx*dx + dy*dy);
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
			
			this.centroidX = boundsX + boundsWidth / 2.0;
			this.centroidY = boundsY + boundsHeight / 2.0;
		}
		
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
			return new PolylineROI(x, y, c, z, t, false);
		}
		
	}
	

}
