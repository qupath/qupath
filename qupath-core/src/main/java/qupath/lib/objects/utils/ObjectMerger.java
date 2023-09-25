package qupath.lib.objects.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.hprtree.HPRtree;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ObjectMerger {

    private static final Logger logger = LoggerFactory.getLogger(ObjectMerger.class);

    /**
     * Merge objects that share a common boundary and have the same classification.
     * <p>
     * This is intended for post-processing a tile-based segmentation, where the tiling has been strictly enforced
     * (i.e. any objects have been clipped to non-overlapping tile boundaries).
     *
     * @param pathObjects
     * @param sharedBoundaryThreshold
     * @return
     */
    public static List<PathObject> resolveTileSplits(Collection<? extends PathObject> pathObjects, double sharedBoundaryThreshold) {

        // Create a priority queue to ensure the objects are sorted by ROI bounds
        Comparator<PathObject> comparator = Comparator
                .comparingDouble((PathObject p) -> p.getROI().getBoundsX())
                    .thenComparingDouble(p -> p.getROI().getBoundsY())
                    .thenComparingDouble(p -> p.getROI().getBoundsWidth())
                    .thenComparingDouble(p -> p.getROI().getBoundsHeight());

        comparator = Comparator.comparingDouble((PathObject p) -> p.getROI().getArea());

        PriorityQueue<PathObject> queue = new PriorityQueue<>(comparator);
        queue.addAll(pathObjects);

        // Build a map of all geometries - this can be one of the slower parts, so we parallelize
        Map<ROI, Geometry> geometryMap = pathObjects.parallelStream()
                .filter(PathObject::hasROI)
                .map(PathObject::getROI)
                .collect(Collectors.toConcurrentMap(Function.identity(), ROI::getGeometry));

        // We have no mutability guarantees from Collectors.toConcurrentMap
        if (!(ConcurrentHashMap.class.equals(geometryMap.getClass())))
            geometryMap = new ConcurrentHashMap<>(geometryMap);

        // Build a spatial index
        SpatialIndex tree = new Quadtree();
        for (var p : pathObjects) {
            var geom = geometryMap.getOrDefault(p.getROI(), null);
            if (geom != null) {
                var envelope = geom.getEnvelopeInternal();
                tree.insert(envelope, p);
            }
        }

        // Retain a set of objects we have already decided to remove
        Set<PathObject> toRemove = new HashSet<>();

        // Find potential overlaps
        List<PathObject> results = new ArrayList<>();
        while (!queue.isEmpty()) {
            PathObject p = queue.poll();
            if (toRemove.contains(p))
                continue;

            // Add the current object to the results (we still might remove it later)
            results.add(p);

            // Choose a small pixel overlap tolerance
            double pixelOverlapTolerance = 0.125;

            // Find all objects that potentially touch the current object (using a small bounding box expansion)
            var geom = geometryMap.computeIfAbsent(p.getROI(), ROI::getGeometry);
            var envelopeQuery = geom.getEnvelopeInternal(); // This is documented to be a copy, so we can modify it
            double expansion = 1e-6;
            envelopeQuery.expandToInclude(envelopeQuery.getMaxX()+expansion, envelopeQuery.getMaxY()+expansion);
            var potentialOverlaps = (List<PathObject>)tree.query(envelopeQuery);

            for (var overlap : potentialOverlaps) {
                if (overlap == p ||
                        toRemove.contains(overlap) ||
                        !Objects.equals(p.getPathClass(), overlap.getPathClass()) ||
                        !Objects.equals(p.getROI().getImagePlane(), overlap.getROI().getImagePlane()) ||
                        !Objects.equals(p.getClass(), overlap.getClass()))
                    continue;

                // If the potential overlap is completely contained, merging is equivalent to removal
                var geomOverlap = geometryMap.computeIfAbsent(overlap.getROI(), k -> overlap.getROI().getGeometry());
                if (geom.covers(geomOverlap)) {
                    toRemove.add(overlap);
                    continue;
                }

                // Search for a shared bounding box edge
                // If the shared intersection is large enough, merge the two objects
                if (testMergeBySharedBoundary(geom, geomOverlap, pixelOverlapTolerance, sharedBoundaryThreshold)) {
                    logger.debug("Merging {} and {}", p, overlap);
                    PathObject newObject = mergeObjects(p, overlap);

                    // Update our queue & spatial tree
                    queue.add(newObject);
                    var mergedROI = newObject.getROI();
                    var geomNew = mergedROI.getGeometry();
                    tree.insert(geomNew.getEnvelopeInternal(), newObject);
                    geometryMap.put(mergedROI, geom);

                    // Flag both objects for removal
                    toRemove.add(p);
                    toRemove.add(overlap);
                    break;
                }
            }
        }
        logger.debug("Removing {} merged objects", toRemove.size());
        results.removeAll(toRemove);
        return results;
    }

//    // TODO: Consider implementing this in the future
//    private static boolean testMergeBySharedArea(Geometry geom, Geometry geomOverlap, double sharedAreaThreshold) {
//    }

    /**
     * Test whether to merge two objects based upon a shared boundary.
     * @param geom the main geometry
     * @param geomOverlap the geometry that (possibly) touches or overlaps
     * @param pixelOverlapTolerance how much the objects are allowed to overlap (to compensate for slightyly misaligned tiles)
     * @param sharedBoundaryThreshold proportion of the possibly-clipped boundary that must be shared
     * @return
     */
    private static boolean testMergeBySharedBoundary(Geometry geom, Geometry geomOverlap, double pixelOverlapTolerance, double sharedBoundaryThreshold) {
        if (calculateUpperLowerSharedBoundaryIntersectionScore(geom, geomOverlap, pixelOverlapTolerance) >= sharedBoundaryThreshold)
            return true;
        else if (calculateLeftRightSharedBoundaryIntersectionScore(geom, geomOverlap, pixelOverlapTolerance) >= sharedBoundaryThreshold)
            return true;
        else
            return false;
    }



    private static PathObject mergeObjects(PathObject pathObject, PathObject pathObject2) {
        var mergedROI = RoiTools.union(pathObject.getROI(), pathObject2.getROI());
        if (pathObject.isTile()) {
            return PathObjects.createTileObject(mergedROI, pathObject.getPathClass(), null);
        } else if (pathObject.isCell()) {
            ROI nucleusROI = getNucleusROI(pathObject);
            if (nucleusROI == null)
                nucleusROI = getNucleusROI(pathObject2);
            else if (getNucleusROI(pathObject2) != null)
                nucleusROI = RoiTools.union(nucleusROI, getNucleusROI(pathObject2));
            return PathObjects.createCellObject(mergedROI, nucleusROI, pathObject.getPathClass(), null);
        } else if (pathObject.isDetection()) {
            return PathObjects.createDetectionObject(mergedROI, pathObject.getPathClass());
        } else if (pathObject.isAnnotation()) {
            return PathObjects.createAnnotationObject(mergedROI, pathObject.getPathClass());
        } else
            throw new IllegalArgumentException("Unsupported object type for merging: " + pathObject.getClass());
    }

    private static ROI getNucleusROI(PathObject pathObject) {
        if (pathObject instanceof PathCellObject cell)
            return cell.getNucleusROI();
        return null;
    }



    private static double calculateUpperLowerSharedBoundaryIntersectionScore(Geometry upper, Geometry lower, double overlapTolerance) {
        var envUpper = upper.getEnvelopeInternal();
        var envLower = lower.getEnvelopeInternal();
        // We only consider the lower and upper boundaries, which must be very close to one another - with no gaps
        if (envUpper.getMaxY() >= envLower.getMinY() && Math.abs(envUpper.getMaxY() - envLower.getMinY()) < overlapTolerance) {
            var upperIntersection = createEnvelopeIntersection(upper, envUpper.getMinX(), envUpper.getMaxY(), envUpper.getMaxX(), envUpper.getMaxY());
            var lowerIntersection = createEnvelopeIntersection(lower, envLower.getMinX(), envLower.getMinY(), envLower.getMaxX(), envLower.getMinY());
            double smallestIntersectionLength = Math.min(upperIntersection.getLength(), lowerIntersection.getLength());
            if (smallestIntersectionLength <= 0)
                return 0.0;
            // For a non-zero tolerance, we may need to shift the geometries
            if (envUpper.getMaxY() != envLower.getMinY()) {
                lowerIntersection.apply(new SetOrdinateFilter(CoordinateSequence.Y, envUpper.getMaxY()));
            }
            var sharedIntersection = upperIntersection.intersection(lowerIntersection);
            return sharedIntersection.getLength() / smallestIntersectionLength;
        } else {
            return 0.0;
        }
    }

    private static double calculateLeftRightSharedBoundaryIntersectionScore(Geometry left, Geometry right, double overlapTolerance) {
        var envLeft = left.getEnvelopeInternal();
        var envRight = right.getEnvelopeInternal();
        // We only consider the right and left boundaries, which must be very close to one another - with no gaps
        if (envLeft.getMaxX() >= envRight.getMinX() && Math.abs(envLeft.getMaxX() - envRight.getMinX()) < overlapTolerance) {
            var leftIntersection = createEnvelopeIntersection(left, envLeft.getMaxX(), envLeft.getMinY(), envLeft.getMaxX(), envLeft.getMaxY());
            var rightIntersection = createEnvelopeIntersection(right, envRight.getMinX(), envRight.getMinY(), envRight.getMinX(), envRight.getMaxY());
            double smallestIntersectionLength = Math.min(leftIntersection.getLength(), rightIntersection.getLength());
            if (smallestIntersectionLength <= 0)
                return 0.0;
            // For a non-zero tolerance, we may need to shift the geometries
            if (envLeft.getMaxX() != envRight.getMinX()) {
                rightIntersection.apply(new SetOrdinateFilter(CoordinateSequence.X, envLeft.getMaxX()));
            }
            var sharedIntersection = leftIntersection.intersection(rightIntersection);
            return sharedIntersection.getLength() / smallestIntersectionLength;
        } else {
            return 0.0;
        }
    }


    private static Geometry createEnvelopeIntersection(Geometry geom, double x1, double y1, double x2, double y2) {
        var factory = geom.getFactory();
        var line = createLine(factory, x1, y1, x2, y2);
        return geom.intersection(line);
    }

    private static LineString createLine(GeometryFactory factory, double x1, double y1, double x2, double y2) {
        return factory.createLineString(new Coordinate[]{new Coordinate(x1, y1), new Coordinate(x2, y2)});
    }

    private static class SetOrdinateFilter implements CoordinateSequenceFilter {

        private final int ordinateIndex;
        private final double value;

        SetOrdinateFilter(int ordinateIndex, double value) {
            this.ordinateIndex = ordinateIndex;
            this.value = value;
        }

        @Override
        public void filter(CoordinateSequence seq, int i) {
            seq.setOrdinate(i, ordinateIndex, value);
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean isGeometryChanged() {
            return false;
        }

    }


}
