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

package qupath.lib.objects.hierarchy;

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator;
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helper class for determining relationships between ROIs.
 * <br/>
 * Using this class helps reduce expensive calculations by enabling the results to be cached.
 * It also adds support for {@code coversWithTolerance} to help with more intuitive hierarchy
 * resolution, in an attempt to address https://github.com/qupath/qupath/issues/1771
 */
class RoiRelate {

    private final ROI roi;
    private final Geometry geometry;
    private final double area;
    private final double tolerance;

    private final double minBoundsX, minBoundsY, maxBoundsX, maxBoundsY;

    private volatile PreparedGeometry preparedGeometry;
    private volatile PointOnGeometryLocator locator;

    private final Map<ROI, Boolean> coversMap = Collections.synchronizedMap(new WeakHashMap<>());

    RoiRelate(ROI roi, Geometry geometry) {
        this.roi = roi;
        this.geometry = geometry == null ? roi.getGeometry() : geometry;
        this.area = roi.getArea();
        // Define our distance tolerance here
        this.tolerance = Math.max(1e-3, 2.0/this.geometry.getFactory().getPrecisionModel().getScale());
        this.minBoundsX = roi.getBoundsX()-tolerance;
        this.minBoundsY = roi.getBoundsY()-tolerance;
        this.maxBoundsX = roi.getBoundsX()+roi.getBoundsWidth()+tolerance;
        this.maxBoundsY = roi.getBoundsY()+roi.getBoundsHeight()+tolerance;
    }

    /**
     * Determine whether the ROI stored here covers another ROI, including a small amount of tolerance.
     * <br/>
     * This is intended to help deal with the fact that overlay options in JTS don't "obey the axioms of set theory"
     * due to precision model limits, see e.g. https://locationtech.github.io/jts/jts-faq.html#D7
     * <br/>
     * Practically, this means that the intersection of two ROIs is not necessarily 'covered' by either of the two ROIs,
     * which produces unintuitive results when we tried to resolve the object hierarchy using QuPath's defined rules.
     * @param roi the roi that may be covered
     * @return true if the stored ROI covers the roi passed as a parameter, false otherwise
     */
    public boolean coversWithTolerance(ROI roi) {
        if (samePlane(roi) && boundsCovers(roi) && area >= roi.getArea()) {
            return coversMap.computeIfAbsent(roi, this::computeCoversWithTolerance);
        } else {
            return false;
        }
    }

    private boolean boundsCovers(ROI roi) {
        return minBoundsX <= roi.getBoundsX() &&
                minBoundsY <= roi.getBoundsY() &&
                maxBoundsX >= roi.getBoundsX() + roi.getBoundsWidth() &&
                maxBoundsY >= roi.getBoundsY() + roi.getBoundsHeight();
    }

    private boolean samePlane(ROI roi) {
        return this.roi.getZ() == roi.getZ() && this.roi.getT() == roi.getT();
    }

    private boolean computeCoversWithTolerance(ROI roi) {
        var child = roi.getGeometry();
        if (child.isEmpty())
            return false;

        var parent = getPreparedGeometry();
        if (parent.covers(child))
            return true;
        if (parent.disjoint(child) || parent.touches(child))
            return false;

        double parentArea = parent.getGeometry().getArea();
        double childArea = child.getArea();
        if (parentArea < childArea || childArea == 0)
            return false;

        var env = parent.getGeometry().getEnvelopeInternal();
        env.expandBy(tolerance);
        if (!env.covers(child.getEnvelopeInternal()))
            return false;

        // Expensive calculation!
        var intersection = parent.getGeometry().intersection(child);
        double actualDist = DiscreteHausdorffDistance.distance(intersection, child, 0.01);
        return actualDist < tolerance;
    }

    /**
     * Query if the stored ROI contains the centroid of the specified ROI.
     * @param roi the roi whose centroid should be checked
     * @return true if the stored ROI contains the centroid, false otherwise
     */
    public boolean containsCentroid(ROI roi) {
        if (!samePlane(roi))
            return false;
        return contains(new Coordinate(roi.getCentroidX(), roi.getCentroidY()));
    }

    /**
     * Query if the stored ROI contains the specified coordinates, assuming the same plane.
     * This tests both the original x and y coordinates, and a (possibly) adjusted version using the precision model,
     * returning true if either pass the test.
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if the ROI contains the coordinate, false otherwise
     */
    public boolean contains(double x, double y) {
        var coord = new Coordinate(x, y);
        if (contains(coord))
            return true;
        geometry.getFactory().getPrecisionModel().makePrecise(coord);
        return contains(coord);
    }

    /**
     * Query if the stored ROI contains the specified coordinate.
     * No adjustment is made for the precision model.
     * @param coord the coordinate to test
     * @return true if the ROI contains the coordinate, false otherwise
     */
    public boolean contains(Coordinate coord) {
        return getLocator().locate(coord) != Location.EXTERIOR;
    }


    private PreparedGeometry getPreparedGeometry() {
        if (preparedGeometry == null) {
            synchronized (this) {
                if (preparedGeometry == null) {
                    preparedGeometry = PreparedGeometryFactory.prepare(geometry);
                }
            }
        }
        return preparedGeometry;
    }

    private PointOnGeometryLocator getLocator() {
        if (locator == null) {
            synchronized (this) {
                if (locator == null) {
                    locator = createLocator(geometry);
                }
            }
        }
        return locator;
    }

    private static synchronized PointOnGeometryLocator createLocator(Geometry geometry) {
        PointOnGeometryLocator locator;
        if (geometry instanceof Polygonal || geometry instanceof LinearRing)
            locator = new IndexedPointInAreaLocator(geometry);
        else
            locator = new SimplePointInAreaLocator(geometry);
        // Workaround for multithreading bug in JTS 1.17.0 - see https://github.com/locationtech/jts/issues/571
        locator.locate(new Coordinate());
        return locator;
    }

}
