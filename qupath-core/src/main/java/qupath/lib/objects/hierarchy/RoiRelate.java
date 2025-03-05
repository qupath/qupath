package qupath.lib.objects.hierarchy;

import org.locationtech.jts.algorithm.PointLocator;
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

import java.util.Map;
import java.util.WeakHashMap;

class RoiRelate {

    private final ROI roi;
    private final Geometry geometry;
    private volatile PreparedGeometry preparedGeometry;
    private volatile PointOnGeometryLocator locator;

    private final Map<ROI, Boolean> coversMap = new WeakHashMap<>();

    RoiRelate(ROI roi, Geometry geometry) {
        this.roi = roi;
        this.geometry = geometry == null ? roi.getGeometry() : geometry;
    }

    public boolean coversWithTolerance(ROI roi) {
        return coversMap.computeIfAbsent(roi, this::computeCoversWithTolerance);
    }

    private boolean computeCoversWithTolerance(ROI roi) {
        var child = roi.getGeometry();
        var parent = getPreparedGeometry();

        if (child.isEmpty())
            return false;
        if (parent.covers(child))
            return true;
        if (parent.disjoint(child) || parent.touches(child))
            return false;

        double parentArea = parent.getGeometry().getArea();
        double childArea = child.getArea();
        if (parentArea < childArea || childArea == 0)
            return false;

//		double dist = Math.max(1e-3, 1.0/parent.getGeometry().getFactory().getPrecisionModel().getScale());
        double dist = Math.max(1e-3, 2.0/parent.getGeometry().getFactory().getPrecisionModel().getScale());

        var env = parent.getGeometry().getEnvelopeInternal();
        env.expandBy(dist);
        if (!env.covers(child.getEnvelopeInternal()))
            return false;

        var intersection = parent.getGeometry().intersection(child);
        double actualDist = DiscreteHausdorffDistance.distance(intersection, child, 0.01);
        return actualDist < dist;
    }

    public boolean containsCentroid(ROI roi) {
        return contains(new Coordinate(roi.getCentroidX(), roi.getCentroidY()));
    }

    public boolean contains(double x, double y) {
        var coord = new Coordinate(x, y);
        if (contains(coord))
            return true;
        geometry.getFactory().getPrecisionModel().makePrecise(coord);
        return contains(coord);
    }

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
