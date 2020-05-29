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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * An implementation of AreaROI that makes use of Java AWT Shapes.
 * <p>
 * If available, this is a better choice than using AreaROI directly, due to the extra checking involved with AWT.
 * 
 * @deprecated Consider using {@link GeometryROI} instead.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
class AWTAreaROI extends AreaROI implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Path2D shape;
	
	// We potentially spend a lot of time drawing polygons & assessing whether or not to draw them...
	// By caching the bounds this can be speeded up
	transient private ClosedShapeStatistics stats = null;
	
	AWTAreaROI(Shape shape, ImagePlane plane) {
		super(RoiTools.getVertices(shape), plane);
		this.shape = new Path2D.Float(shape);
	}
	
	AWTAreaROI(AreaROI roi) {
		super(roi.vertices, roi.getImagePlane());
		shape = new Path2D.Float();
		for (Vertices vertices : vertices) {
			if (vertices.isEmpty())
				continue;
			shape.moveTo(vertices.getX(0), vertices.getY(0));
			for (int i = 1; i < vertices.size(); i++) {
				shape.lineTo(vertices.getX(i), vertices.getY(i));				
			}
			shape.closePath();			
		}
	}
	
	/**
	 * Get the number of vertices used to represent this area.
	 * There is some 'fuzziness' to the meaning of this, since
	 * curved regions will be flattened and the same complex areas may be represented in different ways - nevertheless
	 * it provides some measure of the 'complexity' of the area.
	 * @return
	 */
	@Override
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
	public Shape getShape() {
		return new Path2D.Float(shape);
	}
	
	@Override
	public String getRoiName() {
		return "Area (AWT)";
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
		return shape.contains(x, y);
	}

	@Override
	@Deprecated
	public ROI duplicate() {
		return new AWTAreaROI(shape, getImagePlane());
	}

	@Override
	void calculateShapeMeasurements() {
		stats = new ClosedShapeStatistics(shape);
	}

	
	@Override
	public ROI translate(double dx, double dy) {
		// Shift the bounds
		if (dx == 0 && dy == 0)
			return this;
		// Shift the region
		AffineTransform at = AffineTransform.getTranslateInstance(dx, dy);
		return new AWTAreaROI(new Path2D.Float(shape, at), getImagePlane());
	}

	@Override
	public double getScaledArea(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getArea() * pixelWidth * pixelHeight;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(shape, pixelWidth, pixelHeight).getArea();
	}

	@Override
	public double getScaledLength(double pixelWidth, double pixelHeight) {
		if (GeneralTools.almostTheSame(pixelWidth, pixelHeight, 0.0001))
			return getLength() * (pixelWidth + pixelHeight) * .5;
		// TODO: Need to confirm this is not a performance bottleneck in practice (speed vs. memory issue)
		return new ClosedShapeStatistics(shape, pixelWidth, pixelHeight).getPerimeter();
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

	@Override
	public List<Point2> getAllPoints() {
		if (shape == null)
			return Collections.emptyList();
		return RoiTools.getLinearPathPoints(shape, shape.getPathIterator(null, 0.5));
	}
	
	
	private Object writeReplace() {
		AreaROI roi = new AreaROI(RoiTools.getVertices(shape), ImagePlane.getPlaneWithChannel(c, z, t));
		return roi;
	}
	
}
