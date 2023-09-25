package qupath.lib.objects.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ObjectMerger {

    private static final Logger logger = LoggerFactory.getLogger(ObjectMerger.class);

    private List<PathObject> allObjects;

    private Map<ROI, Geometry> geometryMap;
    private SpatialIndex index;

    private BiPredicate<PathObject, PathObject> compatibilityTest = ObjectMerger::objectsAreMergable;
    private BiPredicate<Geometry, Geometry> mergeTest = ObjectMerger::mergeAnyGeometries;

    private double searchDistance = 0.125;

    // Number of queries made to the spatial index - for debugging
    private int nQueries = 0;

    public ObjectMerger(Collection<? extends PathObject> allObjects, BiPredicate<Geometry, Geometry> mergeTest) {
        this.allObjects = new ArrayList<>(allObjects);
        if (mergeTest != null)
            this.mergeTest = mergeTest;
    }

    public List<PathObject> calculateResults() {
        if (allObjects == null || allObjects.isEmpty())
            return Collections.emptyList();
        if (allObjects.size() == 1)
            return Collections.unmodifiableList(allObjects);

        geometryMap = buildMutableGeometryMap(allObjects);
        index = buildSpatialIndex(allObjects, geometryMap);

        // Comparing recursive and non-recursive approaches
        boolean doRecursive = false;
        List<List<PathObject>> clustersToMerge;
        if (doRecursive)
            clustersToMerge = computeClustersRecursive(allObjects);
        else
            clustersToMerge = computeClustersIterative(allObjects);

        logger.trace("Total spatial index queries: {}", nQueries);

        // Parallelize the merging - it can be slow
        return clustersToMerge.stream()
                .parallel()
                .map(cluster -> mergeObjects(cluster))
                .toList();
    }

    /**
     * Recursive method - kept for reference and debugging, but not used.
     * @param allObjects
     * @return
     */
    private List<List<PathObject>> computeClustersRecursive(Collection<? extends PathObject> allObjects) {
        List<List<PathObject>> clusters = new ArrayList<>();
        Set<PathObject> alreadyVisited = new HashSet<>();

        for (var p : allObjects) {
            if (alreadyVisited.contains(p))
                continue;
            var cluster = addMergesRecursive(p, new ArrayList<>(), alreadyVisited);
            if (cluster.isEmpty()) {
                logger.warn("Empty cluster - this is unexpected!");
            } else {
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    /**
     * Iterative method to compute clusters to merge.
     * Some clusters may be singleton lists, in which case no merging is required.
     * @param allObjects
     * @return
     */
    private List<List<PathObject>> computeClustersIterative(Collection<? extends PathObject> allObjects) {
        List<List<PathObject>> clusters = new ArrayList<>();
        Set<PathObject> alreadyVisited = new HashSet<>();
        Queue<PathObject> pending = new ArrayDeque<>();
        for (var p : allObjects) {
            if (alreadyVisited.contains(p))
                continue;
            List<PathObject> cluster = new ArrayList<>();
            pending.clear();
            pending.add(p);
            while (!pending.isEmpty()) {
                var current = pending.poll();
                // Try to mark as visited, and skip if we've already done so
                if (!alreadyVisited.add(current))
                    continue;
                // Add to the current cluster
                cluster.add(current);
                // If this is the current object, or we are using a search distance, then get the compatible neighbors.
                // Otherwise, with no search distance all objects are compatible - and should already be pending.
                if (p == current || useSearchDistance()) {
                    var neighbors = findCompatibleNeighbors(current);
                    for (var neighbor : neighbors) {
                        if (!alreadyVisited.contains(neighbor)) {
                            if (mergeTest.test(
                                    getGeometry(current),
                                    getGeometry(neighbor))) {
                                pending.add(neighbor);
                            }
                        }
                    }
                }
            }
            if (cluster.isEmpty()) {
                logger.warn("Empty cluster - this is unexpected!");
            } else {
                clusters.add(cluster);
            }
        }
        return clusters;
    }


    /**
     * Recursively build a cluster of objects that can be merged.
     * @param pathObject an object to potentially add to the cluster
     * @param currentCluster the current cluster to which objects may be added, if they are compatible
     * @param alreadyVisited
     * @return
     */
    private List<PathObject> addMergesRecursive(PathObject pathObject, List<PathObject> currentCluster, Set<PathObject> alreadyVisited) {
        if (!alreadyVisited.add(pathObject))
            return currentCluster;

        currentCluster.add(pathObject);

        var neighbors = findCompatibleNeighbors(pathObject);
        for (var neighbor : neighbors) {
            if (!alreadyVisited.contains(neighbor)) {
                if (mergeTest.test(
                        getGeometry(pathObject),
                        getGeometry(neighbor)))
                    addMergesRecursive(neighbor, currentCluster, alreadyVisited);
            }
        }
        return currentCluster;
    }

    private boolean useSearchDistance() {
        return searchDistance >= 0 && searchDistance < Double.MAX_VALUE && Double.isFinite(searchDistance);
    }

    /**
     * Find all objects that *could* be merged with the object, i.e. they are compatible.
     * This applies the search distance and compatibility tests, but not the merge test.
     * @param pathObject
     * @return
     */
    private List<PathObject> findCompatibleNeighbors(PathObject pathObject) {
        List<PathObject> potentialNeighbors;
        if (useSearchDistance()) {
            var geom = getGeometry(pathObject);
            var envelopeQuery = geom.getEnvelopeInternal(); // This is documented to be a copy, so we can modify it
            double expansion = 1e-6;
            envelopeQuery.expandBy(expansion);
            potentialNeighbors = index.query(envelopeQuery);
            nQueries++;
        } else {
            potentialNeighbors = allObjects;
        }
        return potentialNeighbors.stream()
                .filter(p -> p != pathObject)
                .filter(p -> compatibilityTest.test(pathObject, p))
                .toList();
    }

    private Geometry getGeometry(PathObject pathObject) {
        return geometryMap.computeIfAbsent(pathObject.getROI(), ROI::getGeometry);
    }


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
        var mergeable = new ObjectMerger(pathObjects, createBoundaryOverlapTest(sharedBoundaryThreshold));
        return mergeable.calculateResults();
    }

    static boolean mergeAnyGeometries(Geometry geom, Geometry geom2) {
        return true;
    }

    static BiPredicate<Geometry, Geometry> createBoundaryOverlapTest(double sharedBoundaryThreshold) {
        return createBoundaryOverlapTest(0.125, sharedBoundaryThreshold);
    }

    static BiPredicate<Geometry, Geometry> createBoundaryOverlapTest(double pixelOverlapTolerance, double sharedBoundaryThreshold) {
        return (geom, geomOverlap) -> {
            if (calculateUpperLowerSharedBoundaryIntersectionScore(geom, geomOverlap, pixelOverlapTolerance) >= sharedBoundaryThreshold)
                return true;
            else if (calculateLeftRightSharedBoundaryIntersectionScore(geom, geomOverlap, pixelOverlapTolerance) >= sharedBoundaryThreshold)
                return true;
            else if (calculateUpperLowerSharedBoundaryIntersectionScore(geomOverlap, geom, pixelOverlapTolerance) >= sharedBoundaryThreshold)
                return true;
            else if (calculateLeftRightSharedBoundaryIntersectionScore(geomOverlap, geom, pixelOverlapTolerance) >= sharedBoundaryThreshold)
                return true;
            else
                return false;
        };
    }

    private static SpatialIndex buildMutableSpatialIndex(Collection<? extends PathObject> pathObjects, Map<ROI, Geometry> geometryMap) {
        var index = new Quadtree();
        populateSpatialIndex(index, pathObjects, geometryMap);
        return index;
    }

    private static SpatialIndex buildSpatialIndex(Collection<? extends PathObject> pathObjects, Map<ROI, Geometry> geometryMap) {
        var index = new HPRtree();
        populateSpatialIndex(index, pathObjects, geometryMap);
        return index;
    }

    private static void populateSpatialIndex(SpatialIndex tree, Collection<? extends PathObject> pathObjects, Map<ROI, Geometry> geometryMap) {
        // Build a spatial index
        for (var p : pathObjects) {
            var geom = geometryMap.getOrDefault(p.getROI(), null);
            if (geom != null) {
                var envelope = geom.getEnvelopeInternal();
                tree.insert(envelope, p);
            }
        }
    }



    private static boolean objectsAreMergable(PathObject p, PathObject overlap) {
        return (overlap != p &&
                Objects.equals(p.getPathClass(), overlap.getPathClass()) &&
                Objects.equals(p.getROI().getImagePlane(), overlap.getROI().getImagePlane()) &&
                Objects.equals(p.getClass(), overlap.getClass()));
    }


    private static Map<ROI, Geometry> buildMutableGeometryMap(Collection<? extends PathObject> pathObjects) {
        // Converting ROIs to Geometries can be one of the slower parts, so we parallelize
        var map = pathObjects.parallelStream()
                .filter(PathObject::hasROI)
                .map(PathObject::getROI)
                .collect(Collectors.toConcurrentMap(Function.identity(), ROI::getGeometry));

        // We have no mutability guarantees from Collectors.toConcurrentMap
        return new ConcurrentHashMap<>(map);
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



    private static PathObject mergeObjects(List<? extends PathObject> pathObjects) {
        if (pathObjects.isEmpty())
            return null;

        var pathObject = pathObjects.get(0);
        if (pathObjects.size() == 1)
            return pathObject;

        var allROIs = pathObjects.stream().map(PathObject::getROI).filter(Objects::nonNull).collect(Collectors.toList());
        ROI mergedROI = RoiTools.union(allROIs);

        if (pathObject.isTile()) {
            return PathObjects.createTileObject(mergedROI, pathObject.getPathClass(), null);
        } else if (pathObject.isCell()) {
            var nucleusROIs = pathObjects.stream()
                    .map(ObjectMerger::getNucleusROI)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            ROI nucleusROI = nucleusROIs.isEmpty() ? null : RoiTools.union(nucleusROIs);
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
        overlapTolerance = 2.0;
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
