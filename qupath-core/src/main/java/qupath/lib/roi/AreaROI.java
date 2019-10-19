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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * Implementation of an arbitrary area ROI - which could contain disjointed or hollow regions.
 * <p>
 * It may be decomposed into one or more polygons (vertices), in which case the sign of the area is important 
 * in indicating whether the region should be considered 'positive' or a hole.
 * <p>
 * The underlying idea is based on java.awt.Area, but implemented so as to avoid a dependency on AWT.
 * <p>
 * However, because this implementation is relatively simple, it doesn't do the complicated checking and 
 * computations of AWT Areas - and so ought not be used directly.  Rather, AWTAreaROI is strongly to be preferred.
 * <p>
 * The real usefulness of this class is in enabling Serialization of all ROIs to avoid a dependency on AWT,
 * since potentially a deserialized version of this could then be used to create different kinds of Area 
 * (e.g. java.awt.Area or some JavaFX alternative.)
 * 
 * @author Pete Bankhead
 *
 */
public class AreaROI extends AbstractPathROI implements Serializable {
	
//	final static private Logger logger = LoggerFactory.getLogger(AreaROI.class);
	
	private static final long serialVersionUID = 1L;
	
	transient List<MutableVertices> vertices;
	
	// We potentially spend a lot of time drawing polygons & assessing whether or not to draw them...
	// By caching the bounds this can be speeded up
	transient private ClosedShapeStatistics stats = null;
	
	// TODO: Consider making this protected - better not to use directly, to ensure validity of vertices
	AreaROI(List<? extends Vertices> vertices, ImagePlane plane) {
		super(plane);
		this.vertices = new ArrayList<>();
		for (Vertices v : vertices)
			this.vertices.add(new DefaultMutableVertices(v));
	}
	
	private AreaROI(float[][] x, float[][] y, ImagePlane plane) {
		super(plane);
		this.vertices = new ArrayList<>();
		if (x.length != y.length)
			throw new IllegalArgumentException("Lengths of x and y are different!");
		for (int i = 0; i < x.length; i++) {
			float[] x2 = x[i];
			float[] y2 = y[i];
			if (x.length != y.length)
				throw new IllegalArgumentException("Lengths of x and y are different!");
			this.vertices.add(new DefaultMutableVertices(new DefaultVertices(x2, y2, false)));
		}
	}
	
	
	/**
	 * Get the number of vertices used to represent this area.  There is some 'fuzziness' to the meaning of this, since
	 * curved regions will be flattened and the same complex areas may be represented in different ways - nevertheless
	 * it provides some measure of the 'complexity' of the area.
	 * @return
	 */
	public int nVertices() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getNVertices();
	}
	
	
	
//	/**
//	 * For a while, ironically, PathAreaROIs didn't know their own areas...
//	 */
	@Override
	public double getArea() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getArea();
	}

	@Override
	public double getLength() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getPerimeter();
	}

	@Override
	public String getRoiName() {
		return "Area";
	}

	/**
	 * Get the x coordinate of the ROI centroid;
	 * <p>
	 * Warning: If the centroid computation was too difficult (i.e. the area is particularly elaborate),
	 * then the center of the bounding box will be used instead!  (However this should not be relied upon as it is liable to change in later versions)
	 */
	@Override
	public double getCentroidX() {
		if (stats == null)
			calculateShapeMeasurements();
		double centroidX = stats.getCentroidX();
		if (Double.isNaN(centroidX))
			return getBoundsX() + .5 * getBoundsWidth();
		else
			return centroidX;
	}

	/**
	 * Get the y coordinate of the ROI centroid;
	 * <p>
	 * Warning: If the centroid computation was too difficult (i.e. the area is particularly elaborate),
	 * then the center of the bounding box will be used instead!  (However this should not be relied upon as it is liable to change in later versions)
	 */
	@Override
	public double getCentroidY() {
		if (stats == null)
			calculateShapeMeasurements();
		double centroidY = stats.getCentroidY();
		if (Double.isNaN(centroidY))
			return getBoundsY() + .5 * getBoundsHeight();
		else
			return centroidY;
	}

	@Override
	public boolean contains(double x, double y) {
		// I'm not entirely sure this is right...
		// But the idea is that the vertices ought to give 'positive' and 'negative' areas - and ought to be non-empty and non-overlapping (if using java.awt.Area)
		// So if we are inside a 'hole' we can get one positive value and one negative... adding to zero
		// If we are outside we get zero automatically.
		// From several tests this appears to work, but may not always - so AWTAreaROI is to be preferred
		int sum = 0;
		for (Vertices v : vertices) {
			sum += WindingTest.getWindingNumber(v, x, y);
//			if (WindingTest.getWindingNumber(v, x, y) != 0) {
//				System.err.println(WindingTest.getWindingNumber(v, x, y) + "(" + ind + " of " + vertices.size() + ")");
////				return true;
//			}
		}
//		return false;
		return sum != 0;
	}

	@Override
	@Deprecated
	public ROI duplicate() {
		return new AreaROI(vertices, getImagePlane());
	}

	void calculateShapeMeasurements() {
		stats = new ClosedShapeStatistics(vertices);
	}

	
	@Override
	public ROI translate(double dx, double dy) {
		// Shift the bounds
		if (dx == 0 && dy == 0)
			return this;
		// Create shifted vertices
		float[][] xx = new float[vertices.size()][];
		float[][] yy = new float[vertices.size()][];
		int idx = 0;
		for (MutableVertices v : vertices) {
			// Shift the region
			float[] x = v.getX(null);
			float[] y = v.getY(null);
			for (int i = 0; i < x.length; i++) {
				x[i] = (float)(x[i] + dx);
				y[i] = (float)(y[i] + dy);
			}
			xx[idx] = x;
			yy[idx] = y;
			idx++;
		}
		return new AreaROI(xx, yy, getImagePlane());
	}
	
	@Override
	public ROI scale(double scaleX, double scaleY, double originX, double originY) {
		// Create shifted vertices
		float[][] xx = new float[vertices.size()][];
		float[][] yy = new float[vertices.size()][];
		int idx = 0;
		for (MutableVertices v : vertices) {
			// Shift the region
			float[] x = v.getX(null);
			float[] y = v.getY(null);
			for (int i = 0; i < x.length; i++) {
				x[i] = (float)RoiTools.scaleOrdinate(x[i], scaleX, originX);
				y[i] = (float)RoiTools.scaleOrdinate(y[i], scaleY, originY);
			}
			xx[idx] = x;
			yy[idx] = y;
			idx++;
		}
		return new AreaROI(xx, yy, getImagePlane());
	}

	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getArea() * pixelWidth * pixelHeight;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(vertices, pixelWidth, pixelHeight).getArea();
	}

	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getLength() * (pixelWidth + pixelHeight) * .5;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(vertices, pixelWidth, pixelHeight).getPerimeter();
	}

	@Override
	public double getBoundsX() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsX();
	}


	@Override
	public double getBoundsY() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsY();
	}


	@Override
	public double getBoundsWidth() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsWidth();
	}


	@Override
	public double getBoundsHeight() {
		if (stats == null)
			calculateShapeMeasurements();
		return stats.getBoundsHeight();
	}

	/**
	 * Beware this method!  This will return all polygon points (useful e.g. for convex hull calculations), but
	 * since the area itself isn't necessarily polygonal then tracing through the points does *not* necessarily 
	 * result in the same shape as that which the area represents.
	 */
	@Override
	public List<Point2> getAllPoints() {
		if (vertices == null || vertices.isEmpty())
			return Collections.emptyList();
		List<Point2> list = new ArrayList<>();
		for (Vertices v : vertices)
			list.addAll(v.getPoints());
		return list;
	}
	
	
	
	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Proxy required for reading");
	}

	
	private static class SerializationProxy implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private final float[][] x;
		private final float[][] y;
		private final String name;
		private final int c, z, t;
		
		private ClosedShapeStatistics stats;
		
		SerializationProxy(final AreaROI roi) {
			int n = roi.vertices.size();
			this.x = new float[n][];
			this.y = new float[n][];
			for (int i = 0; i < n; i++) {
				this.x[i] = roi.vertices.get(i).getX(null);
				this.y[i] = roi.vertices.get(i).getY(null);				
			}
			this.name = null; // There used to be names... now there aren't
//			this.name = roi.getName();
			this.c = roi.c;
			this.z = roi.z;
			this.t = roi.t;
			
			this.stats = roi.stats;
		}
		
		private Object readResolve() {
			AreaROI roi = new AreaROI(x, y, ImagePlane.getPlaneWithChannel(c, z, t));
			roi.stats = this.stats;
			return roi;
		}
		
	}

	@Override
	public Shape getShape() {
		return new AWTAreaROI(this).getShape();
	}

	@Override
	public RoiType getRoiType() {
		return RoiType.AREA;
	}
	
}
