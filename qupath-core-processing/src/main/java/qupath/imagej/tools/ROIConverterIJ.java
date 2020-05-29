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

package qupath.imagej.tools;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.process.FloatPolygon;

/**
 * Class for converting between PathROIs and ImageJ's own Rois of various types.
 * 
 * @author Pete Bankhead
 *
 */
class ROIConverterIJ {
	
	private static double convertXtoIJ(double x, double xOrigin, double downsample) {
		return x / downsample + xOrigin;
	}

	private static double convertYtoIJ(double y, double yOrigin, double downsample) {
		return y / downsample + yOrigin;
	}
	
	@Deprecated
	static <T extends Roi> T setIJRoiProperties(T roi, ROI pathROI) {
////		roi.setStrokeColor(pathROI.getStrokeColor());
////		roi.setStrokeWidth(pathROI.getStrokeWidth());
//		roi.setName(pathROI.getName());
		return roi;
	}

	private static Rectangle2D getTransformedBounds(ROI pathROI, double xOrigin, double yOrigin, double downsampleFactor) {
		Rectangle2D bounds = AwtTools.getBounds2D(pathROI);
		double x1 = convertXtoIJ(bounds.getMinX(), xOrigin, downsampleFactor);
		double y1 = convertYtoIJ(bounds.getMinY(), yOrigin, downsampleFactor);
		double x2 = convertXtoIJ(bounds.getMaxX(), xOrigin, downsampleFactor);
		double y2 = convertYtoIJ(bounds.getMaxY(), yOrigin, downsampleFactor);
		return new Rectangle2D.Double(
				x1, y1, x2-x1, y2-y1);
	}
	
	/**
	 * Convert a collection of points from a ROI into the coordinate space determined from the calibration information.
	 * 
	 * @param points
	 * @param xOrigin
	 * @param yOrigin
	 * @param downsampleFactor
	 * @return float arrays, where result[0] gives the x coordinates and result[1] the y coordinates
	 */
	private static float[][] getTransformedPoints(Collection<Point2> points, double xOrigin, double yOrigin, double downsampleFactor) {
		float[] xPoints = new float[points.size()];
		float[] yPoints = new float[points.size()];
		int i = 0;
		for (Point2 p : points) {
			xPoints[i] = (float)convertXtoIJ(p.getX(), xOrigin, downsampleFactor);
			yPoints[i] = (float)convertYtoIJ(p.getY(), yOrigin, downsampleFactor);
			i++;
		}
		return new float[][]{xPoints, yPoints};
	}
	

	static Roi getRectangleROI(RectangleROI pathRectangle, double xOrigin, double yOrigin, double downsampleFactor) {
		Rectangle2D bounds = getTransformedBounds(pathRectangle, xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new Roi(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()), pathRectangle);
	}

	static OvalRoi convertToOvalROI(EllipseROI pathOval, double xOrigin, double yOrigin, double downsampleFactor) {
		Rectangle2D bounds = getTransformedBounds(pathOval, xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new OvalRoi(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()), pathOval);
	}

	static Line convertToLineROI(LineROI pathLine, double xOrigin, double yOrigin, double downsampleFactor) {
		return setIJRoiProperties(new Line(convertXtoIJ(pathLine.getX1(), xOrigin, downsampleFactor),
											convertYtoIJ(pathLine.getY1(), yOrigin, downsampleFactor),
											convertXtoIJ(pathLine.getX2(), xOrigin, downsampleFactor),
											convertYtoIJ(pathLine.getY2(), yOrigin, downsampleFactor)), pathLine);		
	}

	static PointRoi convertToPointROI(PointsROI pathPoints, double xOrigin, double yOrigin, double downsampleFactor) {
		float[][] points = getTransformedPoints(pathPoints.getAllPoints(), xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new PointRoi(points[0], points[1]), pathPoints);
	}

	static PolygonRoi convertToPolygonROI(PolygonROI pathPolygon, double xOrigin, double yOrigin, double downsampleFactor) {
		float[][] points = getTransformedPoints(pathPolygon.getAllPoints(), xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new PolygonRoi(points[0], points[1], Roi.POLYGON), pathPolygon);
	}
	
	static PolygonRoi convertToPolygonROI(PolylineROI pathPolygon, double xOrigin, double yOrigin, double downsampleFactor) {
		float[][] points = getTransformedPoints(pathPolygon.getAllPoints(), xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new PolygonRoi(points[0], points[1], Roi.POLYLINE), pathPolygon);
	}
	
	/**
	 * Take an x or y coordinate for a pixel in ImageJ, and convert to a full image pixel using the 
	 * Calibration.xOrigin or Calibration.yOrigin.
	 * 
	 * @param xory
	 * @param origin
	 * @param downsample
	 * @return
	 */
	private static double convertLocationfromIJ(double xory, double origin, double downsample) {
		return (xory - origin) * downsample;
	}
	
	static ROI convertToPolylineROI(PolygonRoi roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		List<Point2> points = convertToPointsList(roi.getFloatPolygon(), xOrigin, yOrigin, downsampleFactor);
		if (points == null)
			return null;
		return ROIs.createPolylineROI(points, ImagePlane.getPlaneWithChannel(c, z, t));
	}
	
	static ROI convertToPolygonOrAreaROI(Roi roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		Shape shape;
		if (roi instanceof ShapeRoi)
			shape = ((ShapeRoi)roi).getShape();
		else
			shape = new ShapeRoi(roi).getShape();
		AffineTransform transform = new AffineTransform();
		transform.scale(downsampleFactor, downsampleFactor);
		transform.translate(roi.getXBase(), roi.getYBase());
		transform.translate(-xOrigin, -yOrigin);
		return ROIs.createAreaROI(new Area(transform.createTransformedShape(shape)), ImagePlane.getPlaneWithChannel(c, z, t));
//		return setPathROIProperties(new PathAreaROI(transform.createTransformedShape(shape)), roi);
	}
	
	static ROI convertToAreaROI(ShapeRoi roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		Shape shape = roi.getShape();
		AffineTransform transform = new AffineTransform();
		transform.scale(downsampleFactor, downsampleFactor);
		transform.translate(roi.getXBase(), roi.getYBase());
		transform.translate(-xOrigin, -yOrigin);
//		return setPathROIProperties(PathROIHelpers.getShapeROI(new Area(transform.createTransformedShape(shape)), 0, 0, 0), roi);
		return ROIs.createAreaROI(new Area(transform.createTransformedShape(shape)), ImagePlane.getPlaneWithChannel(c, z, t));
	}
	
	
	protected static Rectangle2D getTransformedBoundsFromIJ(Roi roi, double xOrigin, double yOrigin, double downsampleFactor) {
		Rectangle2D bounds = roi.getBounds();
		double x1 = convertLocationfromIJ(bounds.getMinX(), xOrigin, downsampleFactor);
		double y1 = convertLocationfromIJ(bounds.getMinY(), yOrigin, downsampleFactor);
		double x2 = convertLocationfromIJ(bounds.getMaxX(), xOrigin, downsampleFactor);
		double y2 = convertLocationfromIJ(bounds.getMaxY(), yOrigin, downsampleFactor);
		return new Rectangle2D.Double(
				x1, y1, x2-x1, y2-y1);
		
//		return new Rectangle2D.Double(
//				convertXfromIJ(bounds.getX(), cal, downsampleFactor),
//				convertYfromIJ(bounds.getY(), cal, downsampleFactor),
//				convertXfromIJ(bounds.getWidth(), null, downsampleFactor),
//				convertYfromIJ(bounds.getHeight(), null, downsampleFactor));
	}
	
	
	static ROI getRectangleROI(Roi roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		Rectangle2D bounds = getTransformedBoundsFromIJ(roi, xOrigin, yOrigin, downsampleFactor);
		return ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlaneWithChannel(c, z, t));
	}

	static ROI convertToEllipseROI(Roi roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		Rectangle2D bounds = getTransformedBoundsFromIJ(roi, xOrigin, yOrigin, downsampleFactor);
		return ROIs.createEllipseROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlaneWithChannel(c, z, t));
	}

	static ROI convertToLineROI(Line roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		double x1 = convertLocationfromIJ(roi.x1d, xOrigin, downsampleFactor);
		double x2 = convertLocationfromIJ(roi.x2d, xOrigin, downsampleFactor);
		double y1 = convertLocationfromIJ(roi.y1d, yOrigin, downsampleFactor);
		double y2 = convertLocationfromIJ(roi.y2d, yOrigin, downsampleFactor);
		return ROIs.createLineROI(x1, y1, x2, y2, ImagePlane.getPlaneWithChannel(c, z, t));		
	}
	
	private static ROI convertToPointROI(PolygonRoi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		double x = cal == null ? 0 : cal.xOrigin;
		double y = cal == null ? 0 : cal.yOrigin;
		return convertToPointROI(roi, x, y, downsampleFactor, c, z, t);
	}

	static ROI convertToPointROI(PolygonRoi roi, double xOrigin, double yOrigin, double downsampleFactor, final int c, final int z, final int t) {
		List<Point2> points = convertToPointsList(roi.getFloatPolygon(), xOrigin, yOrigin, downsampleFactor);
		if (points == null)
			return null;
		return ROIs.createPointsROI(points, ImagePlane.getPlaneWithChannel(c, z, t));
	}
	
	static List<Point2> convertToPointsList(FloatPolygon polygon, Calibration cal, double downsampleFactor) {
		double x = cal == null ? 0 : cal.xOrigin;
		double y = cal == null ? 0 : cal.yOrigin;
		return convertToPointsList(polygon, x, y, downsampleFactor);
	}

	private static List<Point2> convertToPointsList(FloatPolygon polygon, double xOrigin, double yOrigin, double downsampleFactor) {
		List<Point2> points = new ArrayList<>();
		for (int i = 0; i < polygon.npoints; i++) {
			float x = (float)convertLocationfromIJ(polygon.xpoints[i], xOrigin, downsampleFactor);
			float y = (float)convertLocationfromIJ(polygon.ypoints[i], yOrigin, downsampleFactor);
			points.add(new Point2(x, y));
		}
		return points;
	}
	
}