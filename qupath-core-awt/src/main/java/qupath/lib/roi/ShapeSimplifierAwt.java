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
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.roi.experimental.ShapeSimplifier;
import qupath.lib.roi.interfaces.PathShape;

/**
 * Class to assist with shape simplification, similar to qupath.lib.roi.experimental.ShapeSimplifier but using Java AWT shapes.
 * 
 * @author Pete Bankhead
 *
 */
public class ShapeSimplifierAwt {
	
	final private static Logger logger = LoggerFactory.getLogger(ShapeSimplifierAwt.class);

	/**
	 * 
	 * Create a simplified path (fewer coordinates) using method based on Visvalingam’s Algorithm.
	 * 
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 * 
	 * @param path
	 * @param altitudeThreshold
	 * @return
	 */
	public static Path2D simplifyPath(Path2D path, double altitudeThreshold) {
		
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
	 * 
	 * Create a simplified shape (fewer coordinates) using method based on Visvalingam’s Algorithm.
	 * 
	 * See references:
	 * https://hydra.hull.ac.uk/resources/hull:8338
	 * https://www.jasondavies.com/simplify/
	 * http://bost.ocks.org/mike/simplify/
	 * 
	 * @param shapeROI
	 * @param altitudeThreshold
	 * @return
	 */
	public static PathShape simplifyShape(PathShape shapeROI, double altitudeThreshold) {
		Shape shape = PathROIToolsAwt.getShape(shapeROI);
		Path2D path = shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape);
		path = simplifyPath(path, altitudeThreshold);
		// Construct a new polygon
		return new AWTAreaROI(path, shapeROI.getC(), shapeROI.getZ(), shapeROI.getT());
	}

	static void getNextClosedSegment(PathIterator iter, List<Point2> points) {
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
