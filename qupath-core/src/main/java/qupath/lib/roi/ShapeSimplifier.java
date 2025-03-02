/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2024 - 2025 QuPath developers, The University of Edinburgh
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
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * Helper methods for simplifying shapes, such removing polygon points while retaining a similar overall
 * shape at a coarser level.
 * <p>
 * This can help manage storage and performance requirements when working with large numbers of ROIs,
 * especially in terms of repainting speed.
 * 
 * @author Pete Bankhead
 *
 */
public class ShapeSimplifier {

	private static final Logger logger = LoggerFactory.getLogger(ShapeSimplifier.class);

	/**
	 * 
	 * Create a simplified polygon (fewer coordinates) using method based on Visvalingam's Algorithm.
	 * The input is a list of points (the vertices) belonging to a closed polygon.
	 * This list is modified in place.
	 * <p>
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 * 
	 * @param points
	 * @param altitudeThreshold
	 */
	public static void simplifyPolygonPoints(final List<Point2> points, final double altitudeThreshold) {
		
		if (points.size() <= 1)
			return;
		
		// Remove duplicates first
		removeDuplicates(points);

		if (points.size() <= 3)
			return;
		
		int n = points.size();
		
		// Populate the priority queue
		var queue = new MinimalPriorityQueue(points.size()+8);
//		var queue = new PriorityQueue<PointWithArea>(points.size()+8);

		Point2 pPrevious = points.getLast();
		Point2 pCurrent = points.getFirst();
		PointWithArea pwaPrevious = null;
		PointWithArea pwaFirst = null;
		for (int i = 0; i < n; i++) {
			Point2 pNext = points.get((i+1) % n);
			PointWithArea pwa = new PointWithArea(pCurrent, calculateArea(pPrevious, pCurrent, pNext));
			pwa.setPrevious(pwaPrevious);
			if (pwaPrevious != null)
				pwaPrevious.setNext(pwa);
			queue.add(pwa);
			pwaPrevious = pwa;
			
			pPrevious = pCurrent;
			pCurrent = pNext;
			// Handle first and last cases for closed polygon
			if (i == n - 1) {
				pwa.setNext(pwaFirst);
				pwaFirst.setPrevious(pwa);
			} else if (i == 0)
				pwaFirst = pwa;
		}
		
		
		double maxArea = 0;
		int minSize = Math.max(n / 100, 3);
		Set<Point2> toRemove = HashSet.newHashSet(queue.size()/4);
		while (queue.size() > minSize) {
			PointWithArea pwa = queue.poll();
//			logger.info("BEFORE: " + pwa + " (counter " + counter + ")");

			// Altitude check (?)
			double altitude = pwa.getArea() * 2 / pwa.getNext().getPoint().distance(pwa.getPrevious().getPoint());
			if (altitude > altitudeThreshold)
				break;

			if (pwa.getArea() < maxArea)
				pwa.setArea(maxArea);
			else
				maxArea = pwa.getArea();
			
			// Remove the point & update accordingly
//			points.remove(pwa.getPoint());
			toRemove.add(pwa.getPoint());

			pwaPrevious = pwa.getPrevious();
			PointWithArea pwaNext = pwa.getNext();

			queue.remove(pwaPrevious);
			queue.remove(pwaNext);

			pwaPrevious.setNext(pwaNext);
			pwaPrevious.updateArea();
			pwaNext.setPrevious(pwaPrevious);
			pwaNext.updateArea();
			
			// Reinsert into priority queue
			queue.add(pwaPrevious);
			queue.add(pwaNext);
			
//			logger.info(pwa);
		}
		points.removeAll(toRemove);
	}

	/**
	 * Alternative to Java's PriorityQueue that does just enough for what we need here.
	 * The purpose is to overcome the slow (O(n)) removal of elements from the middle of a PriorityQueue,
	 * which was a bottleneck when simplifying polygons with many points.
	 */
	private static class MinimalPriorityQueue {

		private final Comparator<PointWithArea> comparator = Comparator.reverseOrder();
		private final List<PointWithArea> list;
		private boolean initializing = true;

		private MinimalPriorityQueue(int capacity) {
			list = new ArrayList<>(capacity);
		}

		void add(PointWithArea pwa) {
			if (initializing)
				list.add(pwa);
			else
				insert(pwa);
		}

		void remove(PointWithArea pwa) {
			int ind = Collections.binarySearch(list, pwa, comparator);
			if (ind < 0) {
				throw new IllegalArgumentException("PointWithArea is not in the queue");
			}
			list.remove(ind);
		}

		void insert(PointWithArea pwa) {
			int ind = Collections.binarySearch(list, pwa, comparator);
			if (ind < 0)
				ind = -ind - 1;
			else if (list.get(ind) == pwa)
				throw new IllegalArgumentException("PointWithArea is already in the queue");
			list.add(ind, pwa);
		}

		int size() {
			return list.size();
		}

		PointWithArea poll() {
			if (initializing) {
				list.sort(comparator);
				initializing = false;
			}
			return list.isEmpty() ? null : list.removeLast();
		}

	}


	/**
	 *
	 * Create a simplified shape (fewer coordinates) using method based on Visvalingam's Algorithm.
	 * <p>
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 *
	 * @param shapeROI
	 * @param altitudeThreshold
	 * @return
	 */
	public static ROI simplifyShape(ROI shapeROI, double altitudeThreshold) {
		Shape shape = RoiTools.getShape(shapeROI);
		Path2D path = shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape);
		path = simplifyPath(path, altitudeThreshold);
		// Construct a new polygon
		return RoiTools.getShapeROI(path, shapeROI.getImagePlane(), 0.5);
	}

	/**
	 *
	 * Create a simplified path (fewer coordinates) using method based on Visvalingam's Algorithm,
	 * processing all segments.
	 * <p>
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 *
	 * @param path
	 * @param altitudeThreshold
	 * @return
	 * @see #simplifyPath(Path2D, double, int, double)
	 */
	public static Path2D simplifyPath(Path2D path, double altitudeThreshold) {
		return simplifyPath(path, altitudeThreshold, -1, -1);
	}

	/**
	 *
	 * Create a simplified path (fewer coordinates) using method based on Visvalingam's Algorithm,
	 * optionally skipping segments with few points.
	 * <p>
	 * This method introduces {@code segmentPointThreshold} because simplifying multipolygons can produce a
	 * 'jagged' effect when the simplification is applied to segments that contain only a few vertices.
	 * This was particularly noticeable in QuPath's viewer, where a high altitude threshold is used.
	 * <p>
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 *
	 * @param path the path to simplify
	 * @param altitudeThreshold the altitude threshold
	 * @param segmentPointThreshold the minimum number of points in a closed segment for simplification to be applied;
	 *                              segments with fewer points (after removing duplicates) will be retained unchanged.
	 * @param discardBoundsLength discard segments if bounding box dimensions are both smaller than this value;
	 *                            if &leq; 0, no segments are discarded based on bounding box size.
	 *                            This can be important when simplification is use to help render very large shapes at
	 *                            a low resolution.
	 * @return the path with vertices (and possibly some segments) removed
	 * @see #simplifyPath(Path2D, double)
	 * @since v0.6.0
	 */
	public static Path2D simplifyPath(Path2D path, double altitudeThreshold, int segmentPointThreshold, double discardBoundsLength) {

		List<Point2> points = new ArrayList<>();
		PathIterator iter = path.getPathIterator(null, 0.5);
//		int nVerticesBefore = 0;
//		int nVerticesAfter = 0;

		Path2D pathNew = new Path2D.Float();
		while (!iter.isDone()) {
			points.clear();
			getNextClosedSegment(iter, points);
//			nVerticesBefore += points.size();
			if (points.isEmpty())
				break;

			// Do bounding box check
			if (discardBoundsLength > 0 && Double.isFinite(discardBoundsLength)) {
				double minX = Double.POSITIVE_INFINITY;
				double minY = Double.POSITIVE_INFINITY;
				double maxX = Double.NEGATIVE_INFINITY;
				double maxY = Double.NEGATIVE_INFINITY;
				for (var p : points) {
					double x = p.getX();
					double y = p.getY();
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
				if (maxX - minX < discardBoundsLength && maxY - minY < discardBoundsLength) {
					logger.trace("Discarding small segment based on bounding box: {} x {} ({} points)",
							maxX - minX, maxY - minY, points.size());
					continue;
				}
			}

			boolean doSimplify = true;
			if (segmentPointThreshold >= 0) {
				removeDuplicates(points);
				doSimplify = points.size() >= segmentPointThreshold;
			}

			if (doSimplify)
				ShapeSimplifier.simplifyPolygonPoints(points, altitudeThreshold);
//			nVerticesAfter += points.size();

			boolean firstPoint = true;
			for (Point2 p : points) {
				double xx = p.getX();
				double yy = p.getY();
				if (firstPoint) {
					pathNew.moveTo(xx, yy);
					firstPoint = false;
				} else
					pathNew.lineTo(xx, yy);
			}
			pathNew.closePath();
		}

//		logger.trace("Path simplified: {} vertices reduced to {} ({}%)", nVerticesBefore, nVerticesAfter, (nVerticesAfter*100./nVerticesBefore));

		return pathNew;
	}

	/**
	 * Apply a simple 3-point moving average to a list of points.
	 *
	 * @param points
	 * @return
	 */
	public static List<Point2> smoothPoints(List<Point2> points) {
		List<Point2> points2 = new ArrayList<>(points.size());
		for (int i = 0; i < points.size(); i++) {
			Point2 p1 = points.get((i+points.size()-1)%points.size());
			Point2 p2 = points.get(i);
			Point2 p3 = points.get((i+1)%points.size());
			points2.add(new Point2((p1.getX() + p2.getX() + p3.getX())/3, (p1.getY() + p2.getY() + p3.getY())/3));
		}
		return points2;
	}

	/**
	 * Create a simplified polygon (fewer coordinates) using method based on Visvalingam's Algorithm.
	 * <p>
	 * See references:
	 * <a href="https://hydra.hull.ac.uk/resources/hull:8338">Hydra</a>
	 * <a href="https://www.jasondavies.com/simplify/">Jason Davies</a>
	 * <a href="http://bost.ocks.org/mike/simplify/">Mike Bostock</a>
	 *
	 * @param polygon
	 * @param altitudeThreshold
	 * @return
	 */
	public static PolygonROI simplifyPolygon(PolygonROI polygon, final double altitudeThreshold) {
		List<Point2> points = polygon.getAllPoints();
		simplifyPolygonPoints(points, altitudeThreshold);
		// Construct a new polygon
		return ROIs.createPolygonROI(points, ImagePlane.getPlaneWithChannel(polygon));
	}

	/**
	 * Simplify a ROI using either polygon or general shape methods.
	 * @param roi the input ROI
	 * @return a simplified copy of the input
	 */
	public static ROI simplifyROI(ROI roi, double altitudeThreshold) {
		ROI out;
		if (roi instanceof PolygonROI polygonROI) {
			out = ShapeSimplifier.simplifyPolygon(polygonROI, altitudeThreshold);
		} else {
			// TODO: Handle GeometryROI as a special case (so we don't need to go through java.awt.Shape) -
			// 		 conversion back to Geometry can be slow.
			//		 But need to be cautious, because we could easily make invalid geometries...
			out = ShapeSimplifier.simplifyShape(roi, altitudeThreshold);
		}
		return out;
	}

	/**
	 * Remove consecutive duplicate points from a list, in-place.
	 * Assumes the list is mutable.
	 * @param points
	 */
	private static void removeDuplicates(List<Point2> points) {
		var iter = points.iterator();
		Point2 lastPoint = iter.next();
		while (iter.hasNext()) {
			var nextPoint = iter.next();
			if (nextPoint.equals(lastPoint))
				iter.remove();
			else
				lastPoint = nextPoint;
		}
		if (lastPoint.equals(points.getFirst()))
			iter.remove();
	}

	/**
	 * Calculate the area of a triangle from its vertices.
	 * 
	 * @param p1
	 * @param p2
	 * @param p3
	 * @return
	 */
	static double calculateArea(Point2 p1, Point2 p2, Point2 p3) {
		return Math.abs(0.5 * (p1.getX() * (p2.getY() - p3.getY()) + 
						p2.getX() * (p3.getY() - p1.getY()) + 
						p3.getX() * (p1.getY() - p2.getY())));
	}

	static class PointWithArea implements Comparable<PointWithArea> {

		private static final Comparator<PointWithArea> comparator = Comparator.comparingDouble(PointWithArea::getArea)
				.reversed()
				.thenComparingDouble(PointWithArea::getX)
				.thenComparingDouble(PointWithArea::getY);

		private PointWithArea pPrevious;
		private PointWithArea pNext;
		private Point2 p;
		private double area;
		
		PointWithArea(Point2 p, double area) {
			this.p = p;
			this.area = area;
		}
		
		public void setArea(double area) {
			this.area = area;
		}
		
		public void updateArea() {
			this.area = calculateArea(pPrevious.getPoint(), p, pNext.getPoint());
		}
		
		public double getX() {
			return p.getX();
		}

		public double getY() {
			return p.getY();
		}
		
		public double getArea() {
			return area;
		}
		
		public Point2 getPoint() {
			return p;
		}
		
		public void setPrevious(PointWithArea pPrevious) {
			this.pPrevious = pPrevious;
		}

		public void setNext(PointWithArea pNext) {
			this.pNext = pNext;
		}
		
		public PointWithArea getPrevious() {
			return pPrevious;
		}

		public PointWithArea getNext() {
			return pNext;
		}

		@Override
		public int compareTo(PointWithArea p) {
			if (area < p.area)
				return -1;
			else if (area > p.area)
				return 1;
			else if (getX() < p.getX())
				return -1;
			else if (getX() > p.getX())
				return 1;
			else if (getY() < p.getY())
				return -1;
			else if (getY() > p.getY())
				return 1;
			else
				return 0;
//			return comparator.compare(this, p);
		}
		
		@Override
		public String toString() {
			return getX() + ", " + getY() + ", Area: " + getArea();
		}

	}

	private static void getNextClosedSegment(PathIterator iter, List<Point2> points) {
		double[] seg = new double[6];
		while (!iter.isDone()) {
			switch(iter.currentSegment(seg)) {
			case PathIterator.SEG_MOVETO:
				// Fall through
			case PathIterator.SEG_LINETO:
//				points.add(new Point2(Math.round(seg[0]), Math.round(seg[1])));
				points.add(new Point2(seg[0], seg[1]));
				break;
			case PathIterator.SEG_CLOSE:
				iter.next();
				return;
			default:
				throw new RuntimeException("Invalid path iterator " + iter + " - only line connections are allowed");
			};
			iter.next();
		}
	}

}
