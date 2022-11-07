/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.shape.random.RandomPointsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * A collection of static methods for working with ROIs.
 * 
 * @author Pete Bankhead
 *
 */
public class RoiTools {

	private static final Logger logger = LoggerFactory.getLogger(RoiTools.class);

	/**
	 * Methods of combining two ROIs.
	 */
	public enum CombineOp {
		/**
		 * Add ROIs (union).
		 */
		ADD,
		
		/**
		 * Subtract from first ROI.
		 */
		SUBTRACT,
		
		/**
		 * Calculate intersection (overlap) between ROIs.
		 */
		INTERSECT
		}//, XOR}

	/**
	 * Combine two shape ROIs together.
	 * 
	 * @param shape1
	 * @param shape2
	 * @param op
	 * @return
	 */
	public static ROI combineROIs(ROI shape1, ROI shape2, CombineOp op) {
		logger.trace("Combining {} and {} with op {}", shape1, shape2, op);
		// Check we can combine
		if (!RoiTools.sameImagePlane(shape1, shape2))
			throw new IllegalArgumentException("Cannot combine - shapes " + shape1 + " and " + shape2 + " do not share the same image plane");
		var area1 = shape1.getGeometry();
		var area2 = shape2.getGeometry();
		
		// Do a quick check to see if a combination might be avoided
		switch (op) {
		case ADD:
			return GeometryTools.geometryToROI(area1.union(area2), shape1.getImagePlane());
		case INTERSECT:
			return GeometryTools.geometryToROI(area1.intersection(area2), shape1.getImagePlane());
		case SUBTRACT:
			return GeometryTools.geometryToROI(area1.difference(area2), shape1.getImagePlane());
		default:
			throw new IllegalArgumentException("Unknown op " + op);
		}
	}
	
	/**
	 * Create union of multiple ROIs from a collection.
	 * This assumes that ROIs fall on the same plane, if not an {@link IllegalArgumentException} 
	 * will be thrown. Similarly, ROIs must be of a similar type (e.g. area, point) or an exception will be thrown by Java Topology Suite.
	 * @param rois
	 * @return
	 */
	public static ROI union(Collection<? extends ROI> rois) {
		logger.trace("Calculating union of {} ROIs", rois.size());
		if (rois.isEmpty())
			return ROIs.createEmptyROI();
		if (rois.size() == 1)
			return rois.iterator().next();
		ImagePlane plane = rois.iterator().next().getImagePlane();
		List<Geometry> geometries = new ArrayList<>();
		for (var r : rois) {
			if (!r.getImagePlane().equals(plane)) {
				throw new IllegalArgumentException("Cannot merge ROIs - found plane " 
						+ r.getImagePlane() + " but expected " + plane);
			}
			geometries.add(r.getGeometry());
		}
		return GeometryTools.geometryToROI(GeometryTools.union(geometries), plane);
	}
	
	
	/**
	 * Create union of multiple ROIs. 
	 * ROIs must be of a similar type (e.g. area, point) or an exception will be thrown by Java Topology Suite.
	 * @param rois
	 * @return
	 * @throws IllegalArgumentException if the ROIs do not fall in the same plane
	 */
	public static ROI union(ROI... rois) {
		return union(Arrays.asList(rois));
	}
	
	
	/**
	 * Create intersection of multiple ROIs from a collection.
	 * ROIs must be of a similar type (e.g. area, point) or an exception will be thrown by Java Topology Suite.
	 * @param rois
	 * @return
	 * @throws IllegalArgumentException if the ROIs do not fall in the same plane
	 */
	public static ROI intersection(Collection<? extends ROI> rois) {
		if (rois.isEmpty())
			return ROIs.createEmptyROI();
		if (rois.size() == 1)
			return rois.iterator().next();
		ImagePlane plane = rois.iterator().next().getImagePlane();
		List<Geometry> geometries = new ArrayList<>();
		for (var r : rois) {
			if (!r.getImagePlane().equals(plane)) {
				throw new IllegalArgumentException("Cannot merge ROIs - found plane " 
						+ r.getImagePlane() + " but expected " + plane);
			}
			geometries.add(r.getGeometry());
		}
		Geometry first = geometries.remove(0);
		for (var geom : geometries)
			first = first.intersection(geom);
		return GeometryTools.geometryToROI(first, plane);
	}
	
	
	/**
	 * Create intersection of multiple ROIs.
	 * This assumes that ROIs fall on the same plane, if not an {@link IllegalArgumentException} 
	 * will be thrown. Similarly, ROIs must be of a similar type (e.g. area, point) or an exception will be thrown by Java Topology Suite.
	 * @param rois
	 * @return
	 */
	public static ROI intersection(ROI... rois) {
		return intersection(Arrays.asList(rois));
	}
	
	/**
	 * Compute the difference between two ROIs.
	 * This is equivalent to calling {@link RoiTools#subtract(ROI, ROI...)} to subtract roi2 from roi1.
	 * 
	 * @param roi1 the main ROI
	 * @param roi2 the ROI to subtract
	 * @return
	 * @throws IllegalArgumentException if the ROIs do not fall in the same plane
	 */
	public static ROI difference(ROI roi1, ROI roi2) {
		var plane = roi1.getImagePlane();
		if (!roi2.getImagePlane().equals(plane)) {
			throw new IllegalArgumentException("Cannot compute difference - found plane " 
					+ roi2.getImagePlane() + " but expected " + plane);
		}
		var geom = roi1.getGeometry().difference(roi2.getGeometry());
		return GeometryTools.geometryToROI(geom, plane);
	}
	
	/**
	 * Compute the symmetric difference between two ROIs (XOR).
	 * 
	 * @param roi1 the first ROI
	 * @param roi2 the second ROI
	 * @return
	 * @throws IllegalArgumentException if the ROIs do not fall in the same plane
	 */	
	public static ROI symDifference(ROI roi1, ROI roi2) {
		var plane = roi1.getImagePlane();
		if (!roi2.getImagePlane().equals(plane)) {
			throw new IllegalArgumentException("Cannot compute symmetric difference - found plane " 
					+ roi2.getImagePlane() + " but expected " + plane);
		}
		var geom = roi1.getGeometry().symDifference(roi2.getGeometry());
		return GeometryTools.geometryToROI(geom, plane);
	}
	
	
	/**
	 * Subtract one or more ROIs from another ROI.
	 * @param roiMain the main ROI, defining the positive area
	 * @param roisToSubtract the ROIs to remove from roiMain
	 * @return
	 * @throws IllegalArgumentException if the ROIs do not fall in the same plane
	 */
	public static ROI subtract(ROI roiMain, ROI... roisToSubtract) {
		return subtract(roiMain, Arrays.asList(roisToSubtract));
	}
	
	
	/**
	 * Subtract a collection of ROIs from another ROI.
	 * @param roiMain the main ROI, defining the positive area
	 * @param roisToSubtract the ROIs to remove from roiMain
	 * @return
	 * @throws IllegalArgumentException if the ROIs do not fall in the same plane
	 */
	public static ROI subtract(ROI roiMain, Collection<? extends ROI> roisToSubtract) {
		if (roisToSubtract.isEmpty())
			return roiMain;
		
		if (roisToSubtract.size() == 1)
			return difference(roiMain, roisToSubtract.iterator().next());
		
//		// Seems slower (at least if there are many small roisToSubtract)
//		for (var r : roisToSubtract)
//			roiMain = difference(roiMain, r);

		// Filter out ROIs that don't overlap the bounding box of the main one
		var region = ImageRegion.createInstance(roiMain);
		var roisToSubtract2 = roisToSubtract
				.stream()
				.filter(r -> region.intersects(r.getBoundsX(), r.getBoundsY(), r.getBoundsWidth(), r.getBoundsHeight()))
				.collect(Collectors.toList());
		
		// Quick method using the union of ROIs to subtract
		// Could *possibly* be improved by iteratively removing ROIs if they are large
		roiMain = difference(roiMain, union(roisToSubtract2));

		return roiMain;
	}

	
	
	/**
	 * Test whether a {@link ROI} and an {@link ImageRegion} intersect.
	 * <p>
	 * This returns false quickly if the ROI and region do not share the same z-slice or timepoint,
	 * or the ROI's bounding box does not intersect the region.
	 * Otherwise, a more expensive geometry test is performed to check for intersection.
	 * 
	 * @param roi
	 * @param region
	 * @return true if the ROI and the region intersect, false otherwise
	 */
	public static boolean intersectsRegion(ROI roi, ImageRegion region) {
		if (roi.getZ() != region.getZ() || roi.getT() != region.getT())
			return false;
		if (!region.intersects(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight()))
			return false;
		if (roi instanceof RectangleROI)
			return true;
		return GeometryTools.regionToGeometry(region).intersects(roi.getGeometry());
	}
	
	
	/**
	 * Apply an affine transform to a ROI, returning the result.
	 * @param roi the ROI to transform
	 * @param transform the affine transform to apply; if null or the identity transform, the original ROI is returned unchanged
	 * @return the transformed ROI, or the original if no (non-identity) transform is specified
	 */
	public static ROI transformROI(ROI roi, AffineTransform transform) {
		logger.trace("Applying affine transform {} to ROI {}", transform, roi);
		if (roi == null || transform == null || transform.isIdentity())
			return roi;
		if (roi instanceof EllipseROI) {
			var bounds = new Rectangle2D.Double(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
			var shape = transform.createTransformedShape(bounds);
			if (new Area(shape).isRectangular()) {
				bounds.setRect(shape.getBounds2D());
				return ROIs.createEllipseROI(bounds.x, bounds.y, bounds.width, bounds.height, roi.getImagePlane());
			}
		}
		var t = GeometryTools.convertTransform(transform);
		var geometry2 = t.transform(roi.getGeometry());
		return GeometryTools.geometryToROI(geometry2, roi.getImagePlane());
	}
	
	
	/**
	 * Intersect a collection of ROIs with a single parent ROI, returning all results that are valid.
	 * Where possible, ROIs are returned unchanged.
	 * 
	 * @param parent the parent ROI, used to define the clip boundary
	 * @param rois a collection of ROIs that should be intersected with parent
	 * @return list of intersected ROIs; this may be shorter than rois if some lie completely outside parent
	 */
	public static List<ROI> clipToROI(ROI parent, Collection<? extends ROI> rois) {
		logger.trace("Clipping {} ROIs to {}", rois.size(), parent);
		var geom = parent.getGeometry();
		List<ROI> results = new ArrayList<>();
		for (var r : rois) {
			if (!sameImagePlane(parent, r))
				continue;
			var g = r.getGeometry();
			// Quick check to see if we can use the ROI unchanged
			if (geom.covers(g))
				results.add(r);
			else {
				// Compute the intersection
				g = geom.intersection(g);
				// If we have a collection, we need to ensure homogeneity - intersections between two areas can result in lines occurring
				g = GeometryTools.homogenizeGeometryCollection(g);
				// Return the intersection if it is non-null, and also avoids any 'collapse', e.g. an area becoming a line
				if (!g.isEmpty()) {
					var r2 = GeometryTools.geometryToROI(g, r.getImagePlane());
					if (r.isArea() == r2.isArea() && r.isLine() == r2.isLine() && r.isPoint() == r2.isPoint())
						results.add(r2);
				}
			}
		}
		return results;
	}
	
	/**
	 * Fill the holes of an Area ROI, or return the ROI unchanged if it contains no holes.
	 * 
	 * @param roi
	 * @return
	 */
	public static ROI fillHoles(ROI roi) {
		return removeSmallPieces(roi, 0, Double.POSITIVE_INFINITY);
	}

	/**
	 * Remove small fragments and fill small holes of an area ROI.
	 * 
	 * @param roi the ROI to refine
	 * @param minAreaPixels the minimum size of a fragment to retain
	 * @param minHoleAreaPixels the minimum size of a hole to retain, or -1 if all holes should be retained
	 * @return an updated ROI - which may be empty if the modifications caused the ROI to disappear
	 * @see GeometryTools#refineAreas(Geometry, double, double)
	 */
	public static ROI removeSmallPieces(ROI roi, double minAreaPixels, double minHoleAreaPixels) {
		
		logger.trace("Removing small pieces from {} (min = {}, max = {})", roi, minAreaPixels, minHoleAreaPixels);
		
		// We can't have holes if we don't have an AreaROI
		if (roi instanceof RectangleROI || roi instanceof EllipseROI || roi instanceof LineROI || roi instanceof PolylineROI) {
			if (roi.getArea() < minAreaPixels)
				return ROIs.createEmptyROI(roi.getImagePlane());
			else
				return roi;
		}
		
		var geometry = roi.getGeometry();
		var geometry2 = GeometryTools.refineAreas(geometry, minAreaPixels, minHoleAreaPixels);
		if (geometry == geometry2)
			return roi;
		if (geometry2 == null)
			return ROIs.createEmptyROI(roi.getImagePlane());
		return GeometryTools.geometryToROI(geometry2, roi.getImagePlane());
	}


	/**
	 * Get a {@link ROI} from an Area.
	 * This will try to return a simple representation of the ROI if possible (e.g. a rectangle or polygon).
	 * 
	 * @param area
	 * @param plane
	 * @param flatness - can be used to prefer polygons, see Shape.getPathIterator(AffineTransform at, double flatness)
	 * @return
	 */
	static ROI getShapeROI(Area area, ImagePlane plane, double flatness) {
		if (area.isRectangular()) {
			Rectangle2D bounds = area.getBounds2D();
			return ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), plane);
		}
		//		else if (area.isPolygonal() && area.isSingular())
		else if (area.isSingular() && (area.isPolygonal() || flatness > 0)) {
			Path2D path = new Path2D.Float(area);
			List<Point2> points = flatness > 0 ? RoiTools.getLinearPathPoints(path, path.getPathIterator(null, flatness)) : RoiTools.getLinearPathPoints(path, path.getPathIterator(null));
			if (points.size() > 2) {
				// Remove end point if it is a duplicate of the start point
				// since the polygon will be closed anyway
				// This avoids a point being 'doubled-up' when rotating a rectangle
				var pStart = points.get(0);
				var pEnd = points.get(points.size()-1);
				if (pEnd.equals(pStart))
					points.remove(points.size()-1);
			}
			return ROIs.createPolygonROI(points, plane);
		}
		return ROIs.createAreaROI(area, plane);		
	}
	
	
	/**
	 * Get circularity measurement for Area ROIs, calculated as {@code 4 * PI * (area / perimeter^2)}.
	 * Non-area ROIs return Double.NaN.
	 * <p>
	 * This ranges between 0 (for a line) and 1 (for a circle).  Note that the pixel (i.e. not scaled) areas and perimeters are used.
	 * @param roi the ROI to measure
	 * @return a circularity value, between 0 (a line) and 1 (a perfect circle)
	 */
	public static double getCircularity(ROI roi) {
		return getCircularity(roi, 1, 1);
	}

	/**
	 * Get circularity measurement for area ROIs, with optional pixel calibration, calculated as {@code 4 * PI * (area / perimeter^2)}.
	 * Non-area ROIs return Double.NaN.
	 * <p>
	 * This ranges between 0 (for a line) and 1 (for a circle).  This version optionally allows non-square pixels to be used.
	 * @param roi the ROI to measure
	 * @param pixelWidth the calibrated pixel width (use 1.0 for uncalibrated pixels)
	 * @param pixelHeight the calibrated pixel height (use 1.0 for uncalibrated pixels)
	 * @return a circularity value, between 0 (a line) and 1 (a perfect circle)
	 */
	public static double getCircularity(ROI roi, double pixelWidth, double pixelHeight) {
		if (roi.isArea()) {
			double perim = roi.getScaledLength(pixelWidth, pixelHeight);
			return 4.0 * Math.PI * (roi.getScaledArea(pixelWidth, pixelHeight) / (perim * perim));
		}
		return Double.NaN;
	}
	

	/**
	 * Create a {@link ROI} from an Shape with a specified 'flatness'.
	 * This will try to return a RectangleROI or PolygonROI if possible,
	 * or AreaROI if neither of the other classes can adequately represent the area.
	 * 
	 * In the input shape is an Ellipse2D then an EllipseROI will be returned.
	 * 
	 * @param shape
	 * @param plane
	 * @param flatness - can be used to prefer polygons, see {@code Shape.getPathIterator(AffineTransform, double)}
	 * @return
	 */
	public static ROI getShapeROI(Shape shape, ImagePlane plane, double flatness) {
		if (shape instanceof Rectangle2D) {
			Rectangle2D bounds = shape.getBounds2D();
			return ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), plane);
		}
		if (shape instanceof Ellipse2D) {
			Rectangle2D bounds = shape.getBounds2D();
			return ROIs.createEllipseROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), plane);
		}
		if (shape instanceof Line2D) {
			Line2D line = (Line2D)shape;
			return ROIs.createLineROI(line.getX1(), line.getY1(), line.getX2(), line.getY2(), plane);
		}
		boolean isClosed = false;
		List<Point2> points = null;
		if (!(shape instanceof Area)) {
			PathIterator iterator = shape.getPathIterator(null, flatness);
			double[] coords = new double[6];
			points = new ArrayList<>();
			while (!iterator.isDone()) {
				int type = iterator.currentSegment(coords);
				if (type == PathIterator.SEG_CLOSE) {
					isClosed = true;
					break;
				} else
					points.add(new Point2(coords[0], coords[1]));
				iterator.next();
			}
		}
		
		// Handle closed shapes via Area objects, as this gives more options to simplify 
		// (e.g. by checking isRectangular, isPolygonal)
		if (isClosed) {
			Area area;
			if (shape instanceof Area) {
				area = (Area)shape;
			} else
				area = new Area(shape);
			return getShapeROI(area, plane, flatness);
		} else if (points.size() == 2) {
			// Handle straight lines, with only two end points
			Point2 p1 = points.get(0);
			Point2 p2 = points.get(1);
			return ROIs.createLineROI(p1.getX(), p1.getY(), p2.getX(), p2.getY(), plane);
		} else
			// Handle polylines
			return ROIs.createPolylineROI(points, plane);
	}

	/**
	 * Get the Euclidean distance between the centroids of two ROIs.
	 * @param roi1 first ROI
	 * @param roi2 second ROI
	 * @return the distance between centroids
	 */
	public static double getCentroidDistance(ROI roi1, ROI roi2) {
		return getCentroidDistance(roi1, roi2, 1.0, 1.0);
	}
	
	/**
	 * Get the calibrated Euclidean distance between the centroids of two ROIs using specified pixel sizes.
	 * @param roi1 first ROI
	 * @param roi2 second ROI
	 * @param pixelWidth horizontal scale factor for pixels
	 * @param pixelHeight vertical scale factor for pixels
	 * @return the distance between centroids
	 */
	public static double getCentroidDistance(ROI roi1, ROI roi2, double pixelWidth, double pixelHeight) {
		double dx = (roi1.getCentroidX() - roi2.getCentroidX()) * pixelWidth;
		double dy = (roi1.getCentroidY() - roi2.getCentroidY()) * pixelHeight;
		return Math.sqrt(dx*dx + dy*dy);
	}
	
	/**
	 * Get the Euclidean distance between the boundaries of two ROIs.
	 * @param roi1 first ROI
	 * @param roi2 second ROI
	 * @return the distance between boundaries
	 */
	public static double getBoundaryDistance(ROI roi1, ROI roi2) {
		return getBoundaryDistance(roi1, roi2, 1.0, 1.0);
	}
	
	/**
	 * Get the calibrated Euclidean distance between the boundaries of two ROIs using specified pixel sizes.
	 * @param roi1 first ROI
	 * @param roi2 second ROI
	 * @param pixelWidth horizontal scale factor for pixels
	 * @param pixelHeight vertical scale factor for pixels
	 * @return the distance between boundaries
	 */
	public static double getBoundaryDistance(ROI roi1, ROI roi2, double pixelWidth, double pixelHeight) {
		if (pixelWidth == pixelHeight) {
			double pixelSize = pixelWidth;
			return roi1.getGeometry().distance(roi2.getGeometry()) * pixelSize;
		}
		var transform = AffineTransformation.scaleInstance(pixelWidth, pixelHeight);
		var g1 = transform.transform(roi1.getGeometry());
		var g2 = transform.transform(roi2.getGeometry());
		return g1.distance(g2);
	}

	/**
	 * Create a {@link ROI} from an Shape.
	 * This will try to return a RectangleROI or PolygonROI if possible,
	 * or AreaROI if neither of the other classes can adequately represent the area.
	 * 
	 * In the input shape is an Ellipse2D then an EllipseROI will be returned.
	 * 
	 * @param area
	 * @param plane
	 * @return
	 */
	public static ROI getShapeROI(Area area, ImagePlane plane) {
		return getShapeROI(area, plane, -1);
	}




	/**
	 * Get a {@link Shape} object representing a ROI.
	 * Previously this did more work; now it only calls {@link ROI#getShape()}
	 * 
	 * @param roi
	 * @return
	 * @throws IllegalArgumentException if the ROI is a Point ROI, which cannot be converted to a java.awtshape.
	 */
	public static Shape getShape(final ROI roi) throws IllegalArgumentException {
		if (roi.isPoint())
			throw new IllegalArgumentException(roi + " cannot be converted to a shape!");
		return roi.getShape();
	}


	/**
	 * Get an {@link Area} object representing a ROI.
	 * 
	 * @param roi
	 * @return
	 */
	public static Area getArea(final ROI roi) {
		Shape shape = getShape(roi);
		if (shape instanceof Area)
			return (Area)shape;
		return new Area(shape);

	}

	
	/**
	 * Make fixed-size rectangular tile ROIs for a specified area.
	 * 
	 * @param roi area to be tiled
	 * @param tileWidth requested tile width, in pixels
	 * @param tileHeight requested tile height, in pixels
	 * @param trimToROI if true, trim tiles at the ROI boundary according to the ROI shape, otherwise retain full tiles that may only partially overlap
	 * @return
	 */
	public static List<ROI> makeTiles(final ROI roi, final int tileWidth, final int tileHeight, final boolean trimToROI) {
		// TODO: Convert to use JTS Geometries rather than AWT Areas.
		// Create a collection of tiles
		Rectangle bounds = AwtTools.getBounds(roi);
		Area area = getArea(roi);
		List<ROI> tiles = new ArrayList<>();
		//		int ind = 0;
		for (int y = bounds.y; y < bounds.y + bounds.height; y += tileHeight) {
			for (int x = bounds.x; x < bounds.x + bounds.width; x += tileWidth) {
				//				int width = Math.min(x + tileWidth, bounds.x + bounds.width) - x;
				//				int height = Math.min(y + tileHeight, bounds.y + bounds.height) - y;
				int width = tileWidth;
				int height = tileHeight;
				Rectangle tileBounds = new Rectangle(x, y, width, height);
				ROI tile;
				// If the tile is completely contained by the ROI, it's straightforward
				if (area.contains(x, y, width, height))
					tile = ROIs.createRectangleROI(x, y, width, height, roi.getImagePlane());
				else if (!trimToROI) {
					// If we aren't trimming, then check if the centroid is contained
					if (area.contains(x+0.5*width, y+0.5*height))
						tile = ROIs.createRectangleROI(x, y, width, height, roi.getImagePlane());
					else
						continue;
				}
				else {
					// Check if we are actually within the object
					if (!area.intersects(x, y, width, height))
						continue;
					// Shrink the tile if that is sensible
					// TODO: Avoid converting tiles to Areas where not essential
					Area tileArea = new Area(tileBounds);
					tileArea.intersect(area);
					if (tileArea.isEmpty())
						continue;
					if (tileArea.isRectangular()) {
						Rectangle2D bounds2 = tileArea.getBounds2D();
						tile = ROIs.createRectangleROI(bounds2.getX(), bounds2.getY(), bounds2.getWidth(), bounds2.getHeight(), roi.getImagePlane());
					}
					else
						tile = ROIs.createAreaROI(tileArea, roi.getImagePlane());
				}
				//				tile.setName("Tile " + (++ind));
				tiles.add(tile);
			}			
		}
		return tiles;
	}
	
	
	
	/**
	 * Legacy implementation (pre v0.3.0) of {@link #computeTiledROIs(ROI, ImmutableDimension, ImmutableDimension, boolean, int)}
	 * 
	 * @param parentROI main ROI to be tiled
	 * @param sizePreferred the preferred size; in general tiles should have this size
	 * @param sizeMax the maximum allowed size; occasionally it is more efficient to have a tile larger than the preferred size towards a ROI boundary to avoid creating very small tiles unnecessarily
	 * @param fixedSize if true, the tile size is enforced so that complete tiles have the same size
	 * @param overlap optional requested overlap between tiles
	 * @return
	 * 
	 * @see #makeTiles(ROI, int, int, boolean)
	 */
	static Collection<? extends ROI> computeTiledROIsLegacy(ROI parentROI, ImmutableDimension sizePreferred, ImmutableDimension sizeMax, boolean fixedSize, int overlap) {

		ROI pathArea = parentROI != null && parentROI.isArea() ? parentROI : null;
		Rectangle2D bounds = AwtTools.getBounds2D(parentROI);
		if (pathArea == null || (bounds.getWidth() <= sizeMax.width && bounds.getHeight() <= sizeMax.height)) {
			return Collections.singletonList(parentROI);
		}


		List<ROI> pathROIs = new ArrayList<>();

		Geometry area = pathArea.getGeometry();

		double xMin = bounds.getMinX();
		double yMin = bounds.getMinY();
		int nx = (int)Math.ceil(bounds.getWidth() / sizePreferred.width);
		int ny = (int)Math.ceil(bounds.getHeight() / sizePreferred.height);
		double w = fixedSize ? sizePreferred.width : (int)Math.ceil(bounds.getWidth() / nx);
		double h = fixedSize ? sizePreferred.height : (int)Math.ceil(bounds.getHeight() / ny);

		// Center the tiles
		xMin = (int)(bounds.getCenterX() - (nx * w * .5));
		yMin = (int)(bounds.getCenterY() - (ny * h * .5));
		
		var plane = parentROI.getImagePlane();
		int skipCount = 0;

		for (int yi = 0; yi < ny; yi++) {
			for (int xi = 0; xi < nx; xi++) {

				double x = xMin + xi * w - overlap;
				double y = yMin + yi * h - overlap;

				var rect = ROIs.createRectangleROI(x, y, w + overlap*2, h + overlap*2, plane);
				var boundsTile = rect.getGeometry();

				//				double x = xMin + xi * w;
				//				double y = yMin + yi * h;
				//				
				//				Rectangle2D boundsTile = new Rectangle2D.Double(x, y, w, h);
				//					logger.info(boundsTile);
				ROI pathROI = null;
				if (area.contains(boundsTile))
					pathROI = rect;
				else {
					if (!area.intersects(boundsTile))
						continue;
					try {
						// Errors were reported when computing the intersection here - so try to recover as best we can
						Geometry areaTemp = GeometryTools.homogenizeGeometryCollection(boundsTile.intersection(area));
						if (!areaTemp.isEmpty())
							pathROI = GeometryTools.geometryToROI(areaTemp, plane);
					} catch (Exception e) {
						logger.warn("Tile skipped because of error computing intersection: " + e.getLocalizedMessage(), e);
						skipCount++;
					}
				}
				if (pathROI != null)
					pathROIs.add(pathROI);
			}
		}
		if (skipCount > 0) {
			logger.warn("You may be able to avoid tiling errors by calling 'Simplify shape' on any complex annotations first.");
		}
		return pathROIs;
	}
	
	
	
	
	/**
	 * Create a collection of tiled ROIs corresponding to a specified parentROI if it is larger than sizeMax, with optional overlaps.
	 * <p>
	 * The purpose of this is to create useful tiles whenever the exact tile size may not be essential, and overlaps may be required.
	 * Tiles at the parentROI boundary will be trimmed to fit inside. If the parentROI is smaller, it is returned as is.
	 *
	 * @param parentROI main ROI to be tiled
	 * @param sizePreferred the preferred size; in general tiles should have this size
	 * @param sizeMax the maximum allowed size; occasionally it is more efficient to have a tile larger than the preferred size towards a ROI boundary to avoid creating very small tiles unnecessarily
	 * @param fixedSize if true, the tile size is enforced so that complete tiles have the same size
	 * @param overlap optional requested overlap between tiles
	 * @return
	 *
	 * @see #makeTiles(ROI, int, int, boolean)
	 */
	public static Collection<? extends ROI> computeTiledROIs(ROI parentROI, ImmutableDimension sizePreferred, ImmutableDimension sizeMax, boolean fixedSize, int overlap) {
		
		ROI pathArea = parentROI != null && parentROI.isArea() ? parentROI : null;
		Rectangle2D bounds = AwtTools.getBounds2D(parentROI);
		if (pathArea == null || (bounds.getWidth() <= sizeMax.width && bounds.getHeight() <= sizeMax.height)) {
			return Collections.singletonList(parentROI);
		}

		Geometry geometry = pathArea.getGeometry();
		PreparedGeometry prepared = null;
		
		double xMin = bounds.getMinX();
		double yMin = bounds.getMinY();
		int nx = (int)Math.ceil(bounds.getWidth() / sizePreferred.width);
		int ny = (int)Math.ceil(bounds.getHeight() / sizePreferred.height);
		double w = fixedSize ? sizePreferred.width : (int)Math.ceil(bounds.getWidth() / nx);
		double h = fixedSize ? sizePreferred.height : (int)Math.ceil(bounds.getHeight() / ny);

		// Center the tiles
		xMin = (int)(bounds.getCenterX() - (nx * w * .5));
		yMin = (int)(bounds.getCenterY() - (ny * h * .5));
		
		// This can be very slow if we have an extremely large number of vertices/tiles.
		// For that reason, we try to split initially by either rows or columns if needed.
		boolean byRow = false;
		boolean byColumn = false;
		Map<Integer, Geometry> rowParents = null;
		Map<Integer, Geometry> columnParents = null;
		var envelope = geometry.getEnvelopeInternal();
		if (ny > 1 && nx > 1 && geometry.getNumPoints() > 1000) {
			
			// If we have a lot of points, create a prepared geometry so we can check covers/intersects quickly;
			// (for a regular geometry, it would be faster to just compute an intersection and see if it's empty)
			prepared = PreparedGeometryFactory.prepare(geometry);
			var prepared2 = prepared;
			var empty = geometry.getFactory().createEmpty(2);
			
			byRow = nx > ny;
			byColumn = !byRow;
			double yMin2 = yMin;
			double xMin2 = xMin;
			// Compute intersection by row so that later intersections are simplified
			if (byRow) {
				rowParents = IntStream.range(0, ny)
						.parallel()
						.mapToObj(yi -> yi)
						.collect(
								Collectors.toMap(
										yi -> yi,
										yi -> {
											double y = yMin2 + yi * h - overlap;
											var row = GeometryTools.createRectangle(
													envelope.getMinX(),
													y,
													envelope.getMaxX() - envelope.getMinX(),
													h + overlap*2);
											if (!prepared2.intersects(row))
												return empty;
											else if (prepared2.covers(row))
												return row;
											var temp = intersect(geometry, row);
											return temp == null ? geometry : temp;
										}
										)
								);
			}
			if (byColumn) {
				columnParents = IntStream.range(0, nx)
						.parallel()
						.mapToObj(xi -> xi)
						.collect(
								Collectors.toMap(
										xi -> xi,
										xi -> {
											double x = xMin2 + xi * w - overlap;
											var col = GeometryTools.createRectangle(
													x,
													envelope.getMinY(),
													w + overlap*2,
													envelope.getMaxY() - envelope.getMinY());
											if (!prepared2.intersects(col))
												return empty;
											else if (prepared2.covers(col))
												return col;
											var temp = intersect(geometry, col);
											return temp == null ? geometry : temp;
										}
										)
								);
			}
		}
		
		// Geometry local is the one we're working with for the current row or column
		// (often it's the same as the full ROI)
		Geometry geometryLocal = geometry;
		
		// Generate all the rectangles as geometries
		Map<Geometry, Geometry> tileGeometries = new LinkedHashMap<>();
		for (int yi = 0; yi < ny; yi++) {

			double y = yMin + yi * h - overlap;
			if (rowParents != null)
				geometryLocal = rowParents.getOrDefault(yi, geometry);

			for (int xi = 0; xi < nx; xi++) {

				double x = xMin + xi * w - overlap;
				if (columnParents != null)
					geometryLocal = columnParents.getOrDefault(xi, geometry);
				
				if (geometryLocal.isEmpty())
					continue;
				
//				if (geometry != geometryLocal)
//					System.err.println("Using row or column geometry!");
				
				// Create the tile
				var rect = GeometryTools.createRectangle(x, y, w + overlap*2, h + overlap*2);
				
				// Use a prepared geometry if we have one to check covers/intersects & save some effort
				if (prepared != null) {
					if (!prepared.intersects(rect)) {
						continue;
					} else if (prepared.covers(rect)) {
						tileGeometries.put(rect, rect);
						continue;
					}
				}

				// Checking geometryLocal.intersects(rect) first is actually much slower!
				// So add everything and filter out empty tiles later.
				tileGeometries.put(rect, geometryLocal);
			}
		}
		// Compute intersections & map to ROIs
		var plane = parentROI.getImagePlane();
		var tileROIs = tileGeometries
				.entrySet()
				.parallelStream()
				.map(entry -> intersect(entry.getKey(), entry.getValue()))
				.filter(g -> g != null)
				.map(g -> GeometryTools.geometryToROI(g, plane))
				.collect(Collectors.toList());
		
		// If there was an exception, the tile will be null
		if (tileROIs.size() < tileGeometries.size()) {
			logger.warn("Tiles lost during tiling: {}", tileGeometries.size() - tileROIs.size());
			logger.warn("You may be able to avoid tiling errors by calling 'Simplify shape' on any complex annotations first.");
		}
		
		// Remove any empty/non-area tiles
		return tileROIs.stream()
				.filter(t -> !t.isEmpty() && t.isArea())
				.collect(Collectors.toList());
	}
	
	
	/**
	 * Try to intersect two geometries, returning null if this fails.
	 * Intended for use in a stream.
	 * @param g1
	 * @param g2
	 * @return
	 */
	private static Geometry intersect(Geometry g1, Geometry g2) {
//		if (g1.covers(g2))
//			return g2;
//		if (g2.covers(g1))
//			return g1;
		if (g1 == g2)
			return g1;
		
		try {
			return GeometryTools.homogenizeGeometryCollection(g1.intersection(g2));
		} catch (Exception e) {
			logger.warn(e.getLocalizedMessage(), e);
			return null;
		}
	}
	
	
	
	/**
	 * Buffer the specified ROI, dilating (or eroding) by the specified distance.
	 * @param roi the ROI to buffer
	 * @param distance the distance to buffer, in pixels. If negative an erosion will be performed.
	 * @return the modified ROI (which may be empty)
	 */
	public static ROI buffer(ROI roi, double distance) {
		return GeometryTools.geometryToROI(roi.getGeometry().buffer(distance), roi.getImagePlane());
	}
	
	
	/**
	 * Split a multi-part ROI into separate pieces.
	 * <p>
	 * If the ROI is already a distinct, single region or line it is returned as a singleton list.
	 * 
	 * @param roi
	 * @return
	 */
	public static List<ROI> splitROI(final ROI roi) {
		if (roi instanceof RectangleROI || roi instanceof LineROI || roi instanceof EllipseROI) {
			return Collections.singletonList(roi);
		}

		var list = new ArrayList<ROI>();
		var plane = ImagePlane.getPlane(roi);
		
		if (roi.isPoint()) {
			// Handle point ROIs
			if (roi.getNumPoints() <= 1)
				return Collections.singletonList(roi);
			for (var p : roi.getAllPoints()) {
				list.add(ROIs.createPointsROI(p.getX(), p.getY(), plane));
			}
		} else {
			// Handle everything else
			var geometry = roi.getGeometry();
			if (geometry.getNumGeometries() == 1)
				return Collections.singletonList(roi);
			for (int i = 0; i < geometry.getNumGeometries(); i++) {
				list.add(GeometryTools.geometryToROI(geometry.getGeometryN(i), plane));
			}
		}
		return list;
	}
	

	/**
	 * Split Area into PolygonROIs for the exterior and the holes.
	 * <p>
	 * The first array returned gives the <i>holes</i> and the second the positive regions (admittedly, it might have 
	 * been more logical the other way around).
	 * 
	 * <pre>
	 * {@code
	 * var polygons = splitAreaToPolygons(area, -1, 0, 0);
	 * var holes = polygons[0];
	 * var regions = polygons[1];
	 * }
	 * </pre>
	 * 
	 * @param area
	 * @param c
	 * @param z
	 * @param t
	 * @return
	 */
	public static PolygonROI[][] splitAreaToPolygons(final Area area, int c, int z, int t) {

		Map<Boolean, List<PolygonROI>> map = new HashMap<>();
		map.put(Boolean.TRUE, new ArrayList<>());
		map.put(Boolean.FALSE, new ArrayList<>());

		PathIterator iter = area.getPathIterator(null, 0.5);

		var plane = ImagePlane.getPlaneWithChannel(c, z, t);
		List<Point2> points = new ArrayList<>();


		double areaTempSigned = 0;
		double areaCached = 0;

		double[] seg = new double[6];
		double startX = Double.NaN, startY = Double.NaN;
		double x0 = 0, y0 = 0, x1 = 0, y1 = 0;
		boolean closed = false;
		while (!iter.isDone()) {
			switch(iter.currentSegment(seg)) {
			case PathIterator.SEG_MOVETO:
				// Log starting positions - need them again for closing the path
				startX = seg[0];
				startY = seg[1];
				x0 = startX;
				y0 = startY;
				iter.next();
				areaCached += areaTempSigned;
				areaTempSigned = 0;
				points.clear();
				points.add(new Point2(startX, startY));
				closed = false;
				continue;
			case PathIterator.SEG_CLOSE:
				x1 = startX;
				y1 = startY;
				closed = true;
				break;
			case PathIterator.SEG_LINETO:
				x1 = seg[0];
				y1 = seg[1];
				points.add(new Point2(x1, y1));
				closed = false;
				break;
			default:
				// Shouldn't happen because of flattened PathIterator
				throw new RuntimeException("Invalid area computation!");
			};
			areaTempSigned += 0.5 * (x0 * y1 - x1 * y0);
			// Add polygon if it has just been closed
			if (closed) {
				if (areaTempSigned < 0)
					map.get(Boolean.FALSE).add(ROIs.createPolygonROI(points, plane));
				else if (areaTempSigned > 0)
					map.get(Boolean.TRUE).add(ROIs.createPolygonROI(points, plane));
				// Zero indicates the shape is empty...
			}
			// Update the coordinates
			x0 = x1;
			y0 = y1;
			iter.next();
		}
		// TODO: Decide which is positive and which is negative
		areaCached += areaTempSigned;
		PolygonROI[][] polyOutput = new PolygonROI[2][];
		if (areaCached < 0) {
			polyOutput[0] = map.get(Boolean.TRUE).toArray(PolygonROI[]::new);
			polyOutput[1] = map.get(Boolean.FALSE).toArray(PolygonROI[]::new);
		} else {
			polyOutput[0] = map.get(Boolean.FALSE).toArray(PolygonROI[]::new);
			polyOutput[1] = map.get(Boolean.TRUE).toArray(PolygonROI[]::new);			
		}
		//		areaCached = Math.abs(areaCached + areaTempSigned);

		return polyOutput;
	}
	
	
	/**
	 * Create a randomly-located rectangle ROI with the specified width and height, constrained to fall within the provided mask region.
	 * @param mask region defining the area in which the rectangle can be located, including the image plane information
	 * @param width width of the rectangle to create
	 * @param height height of the rectangle to create
	 * @return a rectangle with the specified width and height, covered by the mask
	 * @throws IllegalArgumentException if either the mask width or height is too small for the requested width and height
	 */
	public static ROI createRandomRectangle(ImageRegion mask, double width, double height) throws IllegalArgumentException {
		return createRandomRectangle(mask, width, height, null);
	}

	/**
	 * Create a randomly-located rectangle ROI with the specified width and height, constrained to fall within the provided mask region.
	 * @param mask region defining the area in which the rectangle can be located, including the image plane information
	 * @param width width of the rectangle to create
	 * @param height height of the rectangle to create
	 * @param random random number generator to use (may be null to use a default)
	 * @return a rectangle with the specified width and height, covered by the mask
	 * @throws IllegalArgumentException if either the mask width or height is too small for the requested width and height
	 */
	public static ROI createRandomRectangle(ImageRegion mask, double width, double height, Random random) throws IllegalArgumentException {
		Objects.requireNonNull(mask, "Cannot create random rectangle - region mask must not be null");
		if (mask.getWidth() < width || mask.getHeight() < height)
			throw new IllegalArgumentException(
					"Cannot create random rectangle - region mask " + mask + " is too small to create a " + 
							GeneralTools.formatNumber(width, 2) + " x " + GeneralTools.formatNumber(height, 2) + " region");
		
		if (random == null)
			random = new Random();
		double x = width == mask.getWidth() ? 0 : mask.getMinX() + random.nextDouble() * mask.getWidth() - width;
		double y = height == mask.getHeight() ? 0 : mask.getMinY() + random.nextDouble() * mask.getHeight() - height;
		return ROIs.createRectangleROI(x, y, width, height, mask.getImagePlane());
	}
	
	/**
	 * Create a randomly-located rectangle ROI with the specified width and height, constrained to fall within the provided mask ROI.
	 * <p>
	 * For greater control, see {@link #createRandomRectangle(ROI, double, double, int, boolean, Random)}.
	 * 
	 * @param mask region defining the area in which the rectangle can be located, including the image plane information
	 * @param width width of the rectangle to create
	 * @param height height of the rectangle to create
	 * @return a rectangle with the specified width and height and covered by the mask, or null if it was not possible to find a rectangle 
	 *         that meets this criterion
	 * @throws IllegalArgumentException if either the mask width or height is too small for the requested width and height
	 * @see #createRandomRectangle(ROI, double, double, int, boolean, Random)
	 */
	public static ROI createRandomRectangle(ROI mask, double width, double height) throws IllegalArgumentException {
		return createRandomRectangle(mask, width, height, 1000, true, null);
	}
	
	/**
	 * Create a randomly-located rectangle ROI with the specified width and height, constrained to fall within the provided mask ROI, 
	 * using a specified maximum number of attempts.
	 * 
	 * @param mask region defining the area in which the rectangle can be located, including the image plane information
	 * @param width width of the rectangle to create
	 * @param height height of the rectangle to create
	 * @param maxAttempts the maximum number of attempts to make when attempting to fit the rectangle within the ROI
	 * @param permitErosion optionally make an additional attempt to locate a rectangle by eroding the mask and using the remaining 
	 *                      region. For a non-square rectangle, this uses the length of the longest side for erosion - and therefore 
	 *                      may exclude some possible rectangles from consideration.
	 * @param random random number generator to use for the initial attempts (may be null to use a default)
	 * @return a rectangle with the specified width and height and covered by the mask, or null if it was not possible to find a rectangle 
	 *         that meets this criterion
	 * @throws IllegalArgumentException if either the mask width or height is too small for the requested width and height
	 * @see #createRandomRectangle(ROI, double, double)
	 * @implNote The initial effort generates nAttempts randomly-located rectangles within the ROI bounding box, and checks each to see if it 
	 *           falls completely within the ROI itself or not. A future implementation might use a smarter method that better handles cases 
	 *           where most of the bounding box is not part of the ROI.
	 */
	public static ROI createRandomRectangle(ROI mask, double width, double height, int maxAttempts, boolean permitErosion, Random random) throws IllegalArgumentException {
		Objects.requireNonNull(mask, "Cannot create random rectangle - region mask must not be null");
		if (mask.getBoundsWidth() < width || mask.getBoundsHeight() < height || mask.getArea() < width * height)
			throw new IllegalArgumentException(
					"Cannot create random rectangle - region mask " + mask + " is too small to create a " + 
							GeneralTools.formatNumber(width, 2) + " x " + GeneralTools.formatNumber(height, 2) + " region");

		if (random == null)
			random = new Random();

		// Get prepared geometry just in case it's a complex region
		var geometry = mask.getGeometry();
		var prepared = PreparedGeometryFactory.prepare(geometry);
		boolean success = false;
		double x = 0;
		double y = 0;
		for (int i = 0; i < maxAttempts; i++) {
			if (width == mask.getBoundsWidth())
				x = mask.getBoundsX();
			else
				x = mask.getBoundsX() + random.nextDouble() * (mask.getBoundsWidth() - width);
			if (height == mask.getBoundsHeight())
				y = mask.getBoundsY();
			else
				y = mask.getBoundsY() + random.nextDouble() * (mask.getBoundsHeight() - height);
			var rect = GeometryTools.createRectangle(x, y, width, height);
			if (prepared.covers(rect)) {
				success = true;
				break;
			}
		}
		
		if (!success && permitErosion) {
			// If we are creating a square, we can try eroding the geometry and finding a random point 
			// within what's left - as a more expensive way of finding a point inside
			try {
				// We need to erode by half the length of the square's diagonal
				double erode = Math.sqrt(2) * Math.max(width/2.0, height/2.0);
				var geometrySmaller = geometry.buffer(-erode);
				if (!geometrySmaller.isEmpty()) {
					var builder = new RandomPointsBuilder(geometrySmaller.getFactory());
					builder.setExtent(geometrySmaller);
					builder.setNumPoints(1);
					var points = builder.getGeometry();
					var c = points.getCoordinate();
					x = c.getX() - width/2.0;
					y = c.getY() - height/2.0;
					var rect = GeometryTools.createRectangle(x, y, width, height);
					if (prepared.covers(rect)) {
						if (GeneralTools.almostTheSame(width, height, 0.001))
							logger.debug("Creating square region with RandomPointsBuilder");
						else
							logger.warn("Creating non-square region with RandomPointsBuilder - this will be constrained to the center of the ROI based on the largest side length (i.e. requested width or height)");
						success = true;
					} else {
						logger.warn("Can't created random region - the one created with RandomPointsBuilder was not covered by the original ROI! This is unexpected...");
					}
				}
			} catch (Exception e) {
				logger.warn(e.getLocalizedMessage(), e);
			}
		}
		if (!success) {
			logger.warn("Unable to find a large enough random region within the selected object after {} attempts, sorry", maxAttempts);
			return null;						
		}
		return ROIs.createRectangleROI(x, y, width, height, mask.getImagePlane());
	}
	
	
	

	static PolygonROI[][] splitAreaToPolygons(final ROI pathROI) {
		return splitAreaToPolygons(getArea(pathROI), pathROI.getC(), pathROI.getZ(), pathROI.getT());
	}

	/**
	 * Test if two PathROIs share the same channel, z-slice &amp; time-point
	 * 
	 * @param roi1
	 * @param roi2
	 * @return
	 */
	static boolean sameImagePlane(ROI roi1, ROI roi2) {
//		if (roi1.getC() != roi2.getC())
//			logger.info("Channels differ");
//		if (roi1.getT() != roi2.getT())
//			logger.info("Timepoints differ");
//		if (roi1.getZ() != roi2.getZ())
//			logger.info("Z-slices differ");
		return roi1.getC() == roi2.getC() && roi1.getT() == roi2.getT() && roi1.getZ() == roi2.getZ();
	}

	/**
	 * Returns true if pathROI is an area that contains x &amp; y somewhere within it.
	 * 
	 * @param pathROI
	 * @param x
	 * @param y
	 * @return
	 */
	public static boolean areaContains(final ROI pathROI, final double x, final double y) {
		return pathROI.isArea() && pathROI.contains(x, y);
	}

	static List<Point2> getLinearPathPoints(final Path2D path, final PathIterator iter) {
			List<Point2> points = new ArrayList<>();
			double[] seg = new double[6];
			while (!iter.isDone()) {
				switch(iter.currentSegment(seg)) {
				case PathIterator.SEG_MOVETO:
					// Fall through
				case PathIterator.SEG_LINETO:
					points.add(new Point2(seg[0], seg[1]));
					break;
				case PathIterator.SEG_CLOSE:
	//				// Add first point again
	//				if (!points.isEmpty())
	//					points.add(points.get(0));
					break;
				default:
					throw new IllegalArgumentException("Invalid polygon " + path + " - only line connections are allowed");
				};
				iter.next();
			}
			return points;
		}

	static List<Vertices> getVertices(final Shape shape) {
			Path2D path = shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape);
			PathIterator iter = path.getPathIterator(null, 0.5);
			List<Vertices> verticesList = new ArrayList<>();
			MutableVertices vertices = null;
			double[] seg = new double[6];
			while (!iter.isDone()) {
				switch(iter.currentSegment(seg)) {
				case PathIterator.SEG_MOVETO:
					vertices = new DefaultMutableVertices(new DefaultVertices());
					// Fall through
				case PathIterator.SEG_LINETO:
					vertices.add(seg[0], seg[1]);
					break;
				case PathIterator.SEG_CLOSE:
	//				// Add first point again
					vertices.close();
					verticesList.add(vertices.getVertices());
					break;
				default:
					throw new RuntimeException("Invalid polygon " + path + " - only line connections are allowed");
				};
				iter.next();
			}
			return verticesList;
		}

	static Point2 scalePoint(Point2 p, double scaleX, double scaleY, double originX, double originY) {
		return new Point2(
				scaleOrdinate(p.getX(), scaleX, originX),
				scaleOrdinate(p.getY(), scaleY, originY)
				);
	}

	static double scaleOrdinate(double v, double scale, double origin) {
		v -= origin;
		v *= scale;
		v += origin;
		return v;
	}

	/**
	 * Returns true if the ROI is not null and is not a point ROI.
	 * @param roi
	 * @return
	 */
	public static boolean isShapeROI(ROI roi) {
		return roi != null && !roi.isPoint();
	}

}