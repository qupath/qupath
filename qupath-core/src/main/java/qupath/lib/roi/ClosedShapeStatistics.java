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
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Calculate several shape measurements based on supplied lists of vertices.
 * 
 * @author Pete Bankhead
 *
 */
class ClosedShapeStatistics implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private double areaCached = Double.NaN;
	private double perimeterCached = Double.NaN;
	private float centroidXCached = Float.NaN;
	private float centroidYCached = Float.NaN;
	private double minX = Double.NaN, maxX = Double.NaN, minY = Double.NaN, maxY = Double.NaN;
	private int nVertices = 0;
	
	/**
	 * Calculate shape statistics for default pixel width &amp; height of 1.
	 * 
	 * @param vertices
	 */
	public ClosedShapeStatistics(final Vertices vertices) {
		this(vertices, 1, 1);
	}

	/**
	 * Calculate shape statistics for default pixel width &amp; height of 1.
	 * 
	 * Note: This method access a list of multiple vertices relating to a single area, as stored by AreaROI.
	 * It isn't advised to work with this directly, since the manner in which these vertices are stored is important.
	 * 
	 * @param vertices
	 */
	public ClosedShapeStatistics(final List<? extends Vertices> vertices) {
		this(vertices, 1, 1);
	}

	/**
	 * Calculate shape statistics with scaling according to a specified pixel width &amp; height
	 * (affecting the x and y coordinates respectively).
	 * 
	 * @param vertices
	 * @param pixelWidth
	 * @param pixelHeight
	 */
	public ClosedShapeStatistics(final Vertices vertices, final double pixelWidth, final double pixelHeight) {
		this(Collections.singletonList(vertices), pixelWidth, pixelHeight);
	}
	
	/**
	 * Calculate shape statistics with scaling according to a specified pixel width &amp; height
	 * (affecting the x and y coordinates respectively).
	 * 
	 * Note: This method access a list of multiple vertices relating to a single area, as stored by AreaROI.
	 * It isn't advised to work with this directly, since the manner in which these vertices are stored is important.
	 * 
	 * @param verticesList
	 * @param pixelWidth
	 * @param pixelHeight
	 */
	public ClosedShapeStatistics(final List<? extends Vertices> verticesList, final double pixelWidth, final double pixelHeight) {
		if (verticesList.isEmpty())
			return;
		Path2D path = new Path2D.Float();
		for (Vertices vertices : verticesList) {
			if (vertices.isEmpty())
				continue;
			path.moveTo(vertices.getX(0), vertices.getY(0));
			for (int i = 1; i < vertices.size(); i++) {
				path.lineTo(vertices.getX(i), vertices.getY(i));				
			}
			path.closePath();			
		}
		calculateShapeMeasurements(path, pixelWidth, pixelHeight);
	}
	
	
	@Deprecated
	ClosedShapeStatistics(final Shape shape) {
		this(shape, 1, 1);
	}
	
	@Deprecated
	ClosedShapeStatistics(final Shape shape, final double pixelWidth, final double pixelHeight) {
		calculateShapeMeasurements(shape, pixelWidth, pixelHeight);
	}
	
	private void calculateShapeMeasurements(final Shape shape, final double pixelWidth, final double pixelHeight) {
		double perimeter = 0;		
		double cx = 0;
		double cy = 0;
		double areaTempSigned = 0;
		double areaCached = 0;
		
		double flatness = 0.01;
		
		// Get a path iterator, with flattening involved
		PathIterator iter;
		
		if (shape instanceof Area)
			iter = ((Area)shape).getPathIterator(null, flatness);
		else {
			// Try to get path iterator from an area - but if this is empty, it suggests we just have a line, in which case we should use the default iterator (whatever that is)
			Area area = new Area(shape);
			if (area.isEmpty())
				iter = shape.getPathIterator(null, flatness);
			else {
				iter = area.getPathIterator(null, flatness);
			}
		}
		double[] seg = new double[6];
		double startX = Double.NaN, startY = Double.NaN;
		minX = Double.POSITIVE_INFINITY;
		maxX = Double.NEGATIVE_INFINITY;
		minY = Double.POSITIVE_INFINITY;
		maxY = Double.NEGATIVE_INFINITY;
		double x0 = 0, y0 = 0, x1 = 0, y1 = 0;
		int nPaths = 0;
		while (!iter.isDone()) {
			switch(iter.currentSegment(seg)) {
			case PathIterator.SEG_MOVETO:
				// Log starting positions - need them again for closing the path
				startX = seg[0] * pixelWidth;
				startY = seg[1] * pixelHeight;
				x0 = startX;
				y0 = startY;
				updateMinMax(x0, y0);
				iter.next();
				nPaths++; // Starting another sub-path
				areaCached += areaTempSigned;
				areaTempSigned = 0;
				nVertices++;
				continue;
			case PathIterator.SEG_CLOSE:
				x1 = startX;
				y1 = startY;
				break;
			case PathIterator.SEG_LINETO:
				x1 = seg[0] * pixelWidth;
				y1 = seg[1] * pixelHeight;
				updateMinMax(x1, y1);
				nVertices++;
				break;
			default:
				// Shouldn't happen because of flattened PathIterator
				throw new RuntimeException("Invalid polygon in " + this + " - only line connections are allowed");
			};
			// Update the calculations
			perimeter += Math.sqrt((x1 - x0)*(x1 - x0) + (y1 - y0)*(y1 - y0));
//			area += ((x1 - x0) * (y1 + y0)*0.5);
			// For centroid calculations, see http://en.wikipedia.org/wiki/Centroid#Centroid_of_polygon			
			cx += (x0 + x1) * (x0 * y1 - x1 * y0);
			cy += (y0 + y1) * (x0 * y1 - x1 * y0);
			areaTempSigned += 0.5 * (x0 * y1 - x1 * y0);
			// Update the coordinates
			x0 = x1;
			y0 = y1;
			iter.next();
		}
//		Line2D.Double line = new Line2D.Double();
//		Line2D.linesIntersect(x1, y1, x2, y2, x3, y3, x4, y4)
//		logger.info("POLYGON - Area: " + area + ", Signed area: " + areaSigned);
		if (nPaths == 1) {
			centroidXCached = (float)(cx / (6 * areaTempSigned));
			centroidYCached = (float)(cy / (6 * areaTempSigned));
			// The perimeter only makes sense if we have a single path
			perimeterCached = perimeter;
		} else {
			centroidXCached = (float)(cx / (6 * (areaCached + areaTempSigned)));
			centroidYCached = (float)(cy / (6 * (areaCached + areaTempSigned)));
			perimeterCached = Double.NaN;
		}
		this.areaCached = Math.abs(areaCached + areaTempSigned);
		
		// I'm not entirely sure I have correctly deciphered Java's shapes... so do some basic sanity checking
		Rectangle2D bounds = shape.getBounds2D();
		if (pixelWidth != 1 || pixelHeight != 1) {
			bounds.setFrame(
					bounds.getX() * pixelWidth,
					bounds.getY() * pixelHeight,
					bounds.getWidth() * pixelWidth,
					bounds.getHeight() * pixelHeight);
		}
		assert this.areaCached <= bounds.getWidth() * bounds.getHeight();
		assert bounds.contains(centroidXCached, centroidYCached);
	}
	
	
	final public double getBoundsX() {
		return minX;
	}

	final public double getBoundsY() {
		return minY;
	}

	final public double getBoundsWidth() {
		return maxX - minX;
	}

	final public double getBoundsHeight() {
		return maxY - minY;
	}
	
	final private void updateMinMax(final double x, final double y) {
		if (x < minX)
			minX = x;
		if (x > maxX)
			maxX = x;
		if (y < minY)
			minY = y;
		if (y > maxY)
			maxY = y;
	}
	
	
	final public double getCentroidX() {
		return centroidXCached;
	}

	final public double getCentroidY() {
		return centroidYCached;
	}

	final public double getArea() {
		return areaCached;
	}

	final public double getPerimeter() {
		return perimeterCached;
	}
	
//	public void translateCentroid(final double dx, final double dy) {
//		centroidXCached += dx;
//		centroidYCached += dy;
//		minX += dx;
//		minY += dy;
//		maxX += dx;
//		maxY += dy;
//	}
	
	final public int getNVertices() {
		return nVertices;
	}
	
}
