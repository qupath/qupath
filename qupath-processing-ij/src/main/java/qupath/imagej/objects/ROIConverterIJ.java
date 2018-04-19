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

package qupath.imagej.objects;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.PathImage;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.AWTAreaROI;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.AreaROI;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import ij.ImagePlus;
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
public class ROIConverterIJ {
	
	public static double convertXtoIJ(double x, double xOrigin, double downsample) {
		return x / downsample + xOrigin;
	}

	public static double convertYtoIJ(double y, double yOrigin, double downsample) {
		return y / downsample + yOrigin;
	}
	
	@Deprecated
	private static <T extends Roi> T setIJRoiProperties(T roi, ROI pathROI) {
////		roi.setStrokeColor(pathROI.getStrokeColor());
////		roi.setStrokeWidth(pathROI.getStrokeWidth());
//		roi.setName(pathROI.getName());
		return roi;
	}

	public static Rectangle2D getTransformedBounds(ROI pathROI, double xOrigin, double yOrigin, double downsampleFactor) {
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
	protected static float[][] getTransformedPoints(Collection<Point2> points, double xOrigin, double yOrigin, double downsampleFactor) {
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
	

	public static Roi getRectangleROI(RectangleROI pathRectangle, double xOrigin, double yOrigin, double downsampleFactor) {
		Rectangle2D bounds = getTransformedBounds(pathRectangle, xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new Roi(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()), pathRectangle);
	}

	public static OvalRoi convertToOvalROI(EllipseROI pathOval, double xOrigin, double yOrigin, double downsampleFactor) {
		Rectangle2D bounds = getTransformedBounds(pathOval, xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new OvalRoi(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()), pathOval);
	}

	public static Line convertToLineROI(LineROI pathLine, double xOrigin, double yOrigin, double downsampleFactor) {
		return setIJRoiProperties(new Line(convertXtoIJ(pathLine.getX1(), xOrigin, downsampleFactor),
											convertYtoIJ(pathLine.getY1(), yOrigin, downsampleFactor),
											convertXtoIJ(pathLine.getX2(), xOrigin, downsampleFactor),
											convertYtoIJ(pathLine.getY2(), yOrigin, downsampleFactor)), pathLine);		
	}

	public static PointRoi convertToPointROI(PointsROI pathPoints, double xOrigin, double yOrigin, double downsampleFactor) {
		float[][] points = getTransformedPoints(pathPoints.getPointList(), xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new PointRoi(points[0], points[1]), pathPoints);
	}

	public static PolygonRoi convertToPolygonROI(PolygonROI pathPolygon, double xOrigin, double yOrigin, double downsampleFactor) {
		float[][] points = getTransformedPoints(pathPolygon.getPolygonPoints(), xOrigin, yOrigin, downsampleFactor);
		return setIJRoiProperties(new PolygonRoi(points[0], points[1], Roi.POLYGON), pathPolygon);
	}
	
	/**
	 * Create an ImageJ Roi from a ROI, suitable for displaying on the ImagePlus of an {@code PathImage<ImagePlus>}.
	 * 
	 * @param pathROI
	 * @param pathImage
	 * @return
	 */
	public static <T extends PathImage<ImagePlus>> Roi convertToIJRoi(ROI pathROI, T pathImage) {
		Calibration cal = null;
		double downsampleFactor = 1;
		if (pathImage != null) {
			cal = pathImage.getImage().getCalibration();
			downsampleFactor = pathImage.getDownsampleFactor();
		}
		// TODO: Integrate ROI not supported exception...?
		return convertToIJRoi(pathROI, cal, downsampleFactor);		
	}

	public static <T extends PathImage<ImagePlus>> Roi convertToIJRoi(ROI pathROI, Calibration cal, double downsampleFactor) {
		if (cal != null)
			return convertToIJRoi(pathROI, cal.xOrigin, cal.yOrigin, downsampleFactor);
		else
			return convertToIJRoi(pathROI, 0, 0, downsampleFactor);
	}

	public static <T extends PathImage<ImagePlus>> Roi convertToIJRoi(ROI pathROI, double xOrigin, double yOrigin, double downsampleFactor) {
		if (pathROI instanceof PolygonROI)
			return convertToPolygonROI((PolygonROI)pathROI, xOrigin, yOrigin, downsampleFactor);
		if (pathROI instanceof RectangleROI)
			return getRectangleROI((RectangleROI)pathROI, xOrigin, yOrigin, downsampleFactor);
		if (pathROI instanceof EllipseROI)
			return convertToOvalROI((EllipseROI)pathROI, xOrigin, yOrigin, downsampleFactor);
		if (pathROI instanceof LineROI)
			return convertToLineROI((LineROI)pathROI, xOrigin, yOrigin, downsampleFactor);
		if (pathROI instanceof PointsROI)
			return convertToPointROI((PointsROI)pathROI, xOrigin, yOrigin, downsampleFactor);
		// If we have any other kind of shape, create a general shape roi
		if (pathROI instanceof AreaROI) { // TODO: Deal with non-AWT area ROIs!
			if (!(pathROI instanceof AWTAreaROI))
				pathROI = new AWTAreaROI((AreaROI)pathROI);
			Shape shape = ((AWTAreaROI)pathROI).getShape();
//			"scaleX", "shearY", "shearX", "scaleY", "translateX", "translateY"
			shape = new AffineTransform(1.0/downsampleFactor, 0, 0, 1.0/downsampleFactor, xOrigin, yOrigin).createTransformedShape(shape);
			return setIJRoiProperties(new ShapeRoi(shape), pathROI);
		}
		// TODO: Integrate ROI not supported exception...?
		return null;		
	}
	
	
	public static double convertXfromIJ(double x, Calibration cal, double downsample) {
		if (cal != null)
			return (x - cal.xOrigin) * downsample;
		else
			return x * downsample;
	}

	public static double convertYfromIJ(double y, Calibration cal, double downsample) {
		if (cal != null)
			return (y - cal.yOrigin) * downsample;
		else
			return y * downsample;
	}
	
	public static double convertLocationfromIJ(double x, double origin, double downsample) {
		return (x - origin) * downsample;
	}
	
	/**
	 * Create a ROI from an ImageJ Roi.
	 * 
	 * @param roi
	 * @param pathImage
	 * @return
	 */
	public static <T extends PathImage<? extends ImagePlus>> ROI convertToPathROI(Roi roi, T pathImage) {
		Calibration cal = null;
		double downsampleFactor = 1;
		ImageRegion region = pathImage.getImageRegion();
		if (pathImage != null) {
			cal = pathImage.getImage().getCalibration();
			downsampleFactor = pathImage.getDownsampleFactor();
		}
		return convertToPathROI(roi, cal, downsampleFactor, -1, region.getZ(), region.getT());	
	}
	
	/**
	 * Create a ROI from an ImageJ Roi.
	 * 
	 * @param roi
	 * @param cal
	 * @param downsampleFactor
	 * @param c
	 * @param z
	 * @param t
	 * @return
	 */
	public static ROI convertToPathROI(Roi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
//		if (roi.getType() == Roi.POLYGON || roi.getType() == Roi.TRACED_ROI)
//			return convertToPolygonROI((PolygonRoi)roi, cal, downsampleFactor);
		if (roi.getType() == Roi.RECTANGLE && roi.getCornerDiameter() == 0)
			return getRectangleROI(roi, cal, downsampleFactor, c, z, t);
		if (roi.getType() == Roi.OVAL)
			return convertToEllipseROI(roi, cal, downsampleFactor, c, z, t);
		if (roi instanceof Line)
			return convertToLineROI((Line)roi, cal, downsampleFactor, c, z, t);
		if (roi instanceof PointRoi)
			return convertToPointROI((PolygonRoi)roi, cal, downsampleFactor, c, z, t);
//		if (roi instanceof ShapeRoi)
//			return convertToAreaROI((ShapeRoi)roi, cal, downsampleFactor);
//		// Shape ROIs should be able to handle most eventualities
		if (roi instanceof ShapeRoi)
			return convertToAreaROI((ShapeRoi)roi, cal, downsampleFactor, c, z, t);
		if (roi.isArea())
			return convertToPolygonOrAreaROI(roi, cal, downsampleFactor, c, z, t);
		// TODO: Integrate ROI not supported exception...?
		return null;	
	}

	
	public static ROI convertToPolygonOrAreaROI(Roi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		Shape shape;
		if (roi instanceof ShapeRoi)
			shape = ((ShapeRoi)roi).getShape();
		else
			shape = new ShapeRoi(roi).getShape();
		AffineTransform transform = new AffineTransform();
		transform.scale(downsampleFactor, downsampleFactor);
		transform.translate(roi.getXBase(), roi.getYBase());
		if (cal != null)
			transform.translate(-cal.xOrigin, -cal.yOrigin);
		return PathROIToolsAwt.getShapeROI(new Area(transform.createTransformedShape(shape)), c, z, t);
//		return setPathROIProperties(new PathAreaROI(transform.createTransformedShape(shape)), roi);
	}
	
	
	public static AreaROI convertToAreaROI(ShapeRoi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		Shape shape = roi.getShape();
		AffineTransform transform = new AffineTransform();
		transform.scale(downsampleFactor, downsampleFactor);
		transform.translate(roi.getXBase(), roi.getYBase());
		if (cal != null)
			transform.translate(-cal.xOrigin, -cal.yOrigin);
//		return setPathROIProperties(PathROIHelpers.getShapeROI(new Area(transform.createTransformedShape(shape)), 0, 0, 0), roi);
		return new AWTAreaROI(transform.createTransformedShape(shape), c, z, t);
	}
	
	
	protected static Rectangle2D getTransformedBoundsFromIJ(Roi roi, Calibration cal, double downsampleFactor) {
		Rectangle2D bounds = roi.getBounds();
		double x1 = convertXfromIJ(bounds.getMinX(), cal, downsampleFactor);
		double y1 = convertYfromIJ(bounds.getMinY(), cal, downsampleFactor);
		double x2 = convertXfromIJ(bounds.getMaxX(), cal, downsampleFactor);
		double y2 = convertYfromIJ(bounds.getMaxY(), cal, downsampleFactor);
		return new Rectangle2D.Double(
				x1, y1, x2-x1, y2-y1);
		
//		return new Rectangle2D.Double(
//				convertXfromIJ(bounds.getX(), cal, downsampleFactor),
//				convertYfromIJ(bounds.getY(), cal, downsampleFactor),
//				convertXfromIJ(bounds.getWidth(), null, downsampleFactor),
//				convertYfromIJ(bounds.getHeight(), null, downsampleFactor));
	}
	
	public static RectangleROI getRectangleROI(Roi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		Rectangle2D bounds = getTransformedBoundsFromIJ(roi, cal, downsampleFactor);
		return new RectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), c, z, t);
	}

	public static EllipseROI convertToEllipseROI(Roi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		Rectangle2D bounds = getTransformedBoundsFromIJ(roi, cal, downsampleFactor);
		return new EllipseROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), c, z, t);
	}

	public static LineROI convertToLineROI(Line roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		double x1 = convertXfromIJ(roi.x1d, cal, downsampleFactor);
		double x2 = convertXfromIJ(roi.x2d, cal, downsampleFactor);
		double y1 = convertYfromIJ(roi.y1d, cal, downsampleFactor);
		double y2 = convertYfromIJ(roi.y2d, cal, downsampleFactor);
		return new LineROI(x1, y1, x2, y2, c, z, t);		
	}
	
	public static PointsROI convertToPointROI(PolygonRoi roi, Calibration cal, double downsampleFactor) {
		return convertToPointROI(roi, cal, downsampleFactor, -1, 0, 0);
	}


	public static PointsROI convertToPointROI(PolygonRoi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		List<Point2> points = convertToPointsList(roi.getFloatPolygon(), cal, downsampleFactor);
		if (points == null)
			return null;
		return new PointsROI(points, c, z, t);
	}
	
	public static PolygonROI convertToPolygonROI(PolygonRoi roi, Calibration cal, double downsampleFactor) {
		return convertToPolygonROI(roi, cal, downsampleFactor, -1, 0, 0);
	}

	public static PolygonROI convertToPolygonROI(PolygonRoi roi, Calibration cal, double downsampleFactor, final int c, final int z, final int t) {
		List<Point2> points = convertToPointsList(roi.getFloatPolygon(), cal, downsampleFactor);
		if (points == null)
			return null;
		return new PolygonROI(points, c, z, t);
	}
	
	public static List<Point2> convertToPointsList(FloatPolygon polygon, Calibration cal, double downsampleFactor) {
		if (polygon == null)
			return null;
		List<Point2> points = new ArrayList<>();
		for (int i = 0; i < polygon.npoints; i++) {
			float x = (float)convertXfromIJ(polygon.xpoints[i], cal, downsampleFactor);
			float y = (float)convertYfromIJ(polygon.ypoints[i], cal, downsampleFactor);
			points.add(new Point2(x, y));
		}
		return points;
	}
	

}
