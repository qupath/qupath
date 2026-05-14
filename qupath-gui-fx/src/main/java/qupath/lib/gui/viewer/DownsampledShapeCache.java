/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helper class for managing simplified versions of a shape for rendering shapes at lower resolutions.
 * @since v0.6.0
 */
public class DownsampledShapeCache {

    // No simplification for any shape with fewer points than this
    private static final int pointCountThreshold = 1000;

    // No simplification for any downsample less than this
    private static final double minDownsample = 4.0;

    // Downsamples calculated as multiples of this value
    private final double downsampleStep;

    private final DownsampledShape shape;
    private final boolean canSimplify;
    private final List<DownsampledShape> downsampledShapes;

    // (Only if shape simplification is often used for detection objects)
    private static final Map<ROI, DownsampledShapeCache> shapeCache = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Get the instance of the cache for a particular ROI.
     * Note that this method should generally only be called for ROI objects that are likely to be reused,
     * and which have sufficiently many points to benefit from simplification.
     * @param roi
     * @return
     */
    private static DownsampledShapeCache getInstance(ROI roi) {
        return shapeCache.computeIfAbsent(roi, DownsampledShapeCache::new);
    }

    /**
     * Get the shape for a particular downsample.
     * <b>Important!</b> Because the purpose of this class is efficiency, the returned shape must not be modified.
     * Also, it is permitted to return {@link ROI#getShape()} for simple shapes that can already be rendered
     * efficiently.
     * @param roi the roi for which to get the shape
     * @param downsample the downsample factor
     * @return the shape to render at the specified downsample
     */
    public static Shape getShapeForDownsample(ROI roi, double downsample) {
        if (roi.isArea() && roi.getNumPoints() > pointCountThreshold) {
            return DownsampledShapeCache.getInstance(roi).getForDownsample(downsample);
        } else {
            return roi.getShape();
        }
    }

    private DownsampledShapeCache(ROI roi) {
        // The basic shape
        // Rather than count the points, just set to a very high value
        int nPoints = roi.getNumPoints();
        this.shape = new DownsampledShape(roi.getShape(), 1.0, nPoints);
        this.canSimplify = nPoints > pointCountThreshold && roi.isArea();
        if (canSimplify) {
            // If we have an astronomically large number of points, we want to simplify for fewer steps
            // (because it can be time-consuming and memory-intensive)
            if (nPoints < 5_000_000)
                downsampleStep = 1.25;
            else if (nPoints < 10_000_000)
                downsampleStep = 1.5;
            else
                downsampleStep = 2.0;
            downsampledShapes = new ArrayList<>();
        } else {
            downsampleStep = 2; // Not relevant
            downsampledShapes = Collections.emptyList();
        }
    }

    private Shape getForDownsample(double downsample) {
        if (!canSimplify || downsample <= minDownsample) {
            return shape.shape();
        }
        DownsampledShape lastShape = shape;
        double lastDownsample = minDownsample;
        int ind = 0;
        while (true) {
            if (lastShape.nPoints() < pointCountThreshold || lastDownsample > downsample) {
                return lastShape.shape();
            }
            DownsampledShape currentShape;
            if (ind >= downsampledShapes.size()) {
                // Progressively simplify the shape for downsamples.
                // For this to work, it's important that downsamples are fixed multiples - otherwise we can have
                // weird effects when we downsample a shape that has already been rounded to other values.
                currentShape = downsampleShape(lastShape.shape(),
                        ind == 0 ? minDownsample : lastDownsample * downsampleStep);
                downsampledShapes.add(currentShape);
            } else {
                currentShape = downsampledShapes.get(ind);
            }
            lastShape = currentShape;
            lastDownsample = currentShape.downsample();
            ind++;
        }
    }


    private record DownsampledShape(Shape shape, double downsample, int nPoints) {}

    private static DownsampledShape downsampleShape(Shape shape, double downsample) {

        List<Point2> points = new ArrayList<>();
        var path = shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape);
        var bounds = shape.getBounds2D();

        PathIterator iter = path.getPathIterator(null, downsample/2.0);

        Path2D pathNew = new Path2D.Float();
        Rectangle2D segmentBounds = new Rectangle2D.Double();

        int n = 0;
        while (!iter.isDone()) {
            points.clear();
            getNextClosedSegment(iter, points, downsample, bounds);
            if (points.isEmpty())
                break;

            if (points.size() < 3)
                continue;

            // Check the segment bounds are sufficient to include
            getBounds(points, segmentBounds);
            if (segmentBounds.getWidth() < downsample || segmentBounds.getHeight() < downsample)
                continue;

            boolean firstPoint = true;
            for (Point2 p : points) {
                double xx = p.getX();
                double yy = p.getY();
                if (firstPoint) {
                    firstPoint = false;
                    pathNew.moveTo(xx, yy);
                } else {
                    pathNew.lineTo(xx, yy);
                }
            }
            pathNew.closePath();

            n += points.size();
        }

        if (n == 0)
            return new DownsampledShape(bounds, downsample, 4);
        else
            return new DownsampledShape(pathNew, downsample, n);
    }


    private static Rectangle2D getBounds(List<Point2> points, Rectangle2D bounds) {
        if (bounds == null)
            bounds = new Rectangle2D.Double();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (var p : points) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }
        bounds.setFrame(minX, minY, maxX - minX, maxY - minY);
        return bounds;
    }


    private static void getNextClosedSegment(PathIterator iter, List<Point2> points, double downsample, Rectangle2D bounds) {
        double[] seg = new double[6];
        Point2 point = null;

        double minX = bounds.getMinX();
        double maxX = bounds.getMaxX();
        double minY = bounds.getMinY();
        double maxY = bounds.getMaxY();
        double eps = 1e-3;

        while (!iter.isDone()) {
            switch(iter.currentSegment(seg)) {
                case PathIterator.SEG_MOVETO:
                    // Fall through
                case PathIterator.SEG_LINETO:
                    double x = seg[0];
                    double y = seg[1];

                    // We want to retain the bounding box, so don't downsample the edges
                    // (This isn't necessary... I just thought it when investigating another bug...)
                    if (!GeneralTools.almostTheSame(x, minX, eps) && !GeneralTools.almostTheSame(x, maxX, eps))
                        x = GeneralTools.clipValue(minX + roundToDownsample(x-minX, downsample), minX, maxX);

                    if (!GeneralTools.almostTheSame(y, minY, eps) && !GeneralTools.almostTheSame(y, maxY, eps))
                        y = GeneralTools.clipValue(minY + roundToDownsample(y-minY, downsample), minY, maxY);

                    if (point == null || point.getX() != x || point.getY() != y) {
                        point = new Point2(x, y);
                        points.add(point);
                    }
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

    private static double roundToDownsample(double value, double downsample) {
        return Math.round(value / downsample) * downsample;
    }

}
