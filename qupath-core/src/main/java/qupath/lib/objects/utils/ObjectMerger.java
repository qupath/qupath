/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023-2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.objects.utils;

import java.util.Comparator;
import java.util.HashMap;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.hprtree.HPRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.GeometryTools;
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

/**
 * Helper class for merging objects using different criteria.
 * <p>
 * This is designed to be used for post-processing a segmentation, to help resolve tile boundaries.
 *
 * @since v0.5.0
 */
public class ObjectMerger implements ObjectProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ObjectMerger.class);

    private final BiPredicate<PathObject, PathObject> compatibilityTest;
    private final BiPredicate<Geometry, Geometry> mergeTest;
    private final double searchDistance;
    private final MeasurementStrategy measurementStrategy;

    /**
     * Constructor.
     * @param compatibilityTest the test to apply to check if objects are compatible (e.g. same type, plane and classification).
     * @param mergeTest the test to apply to check if objects can be merged (e.g. a boundary or overlap test).
     * @param searchDistance the distance to search for compatible objects. If negative, all objects are considered,
     *                       and merging can be applied for disconnected ROIs.
     *                       If zero, only objects that touch are considered.
     */
    private ObjectMerger(BiPredicate<PathObject, PathObject> compatibilityTest, BiPredicate<Geometry, Geometry> mergeTest, double searchDistance) {
        this(compatibilityTest, mergeTest, searchDistance, MeasurementStrategy.IGNORE);
    }

    /**
     * Constructor.
     * @param compatibilityTest the test to apply to check if objects are compatible (e.g. same type, plane and classification).
     * @param mergeTest the test to apply to check if objects can be merged (e.g. a boundary or overlap test).
     * @param searchDistance the distance to search for compatible objects. If negative, all objects are considered,
     *                       and merging can be applied for disconnected ROIs.
     *                       If zero, only objects that touch are considered.
     * @param measurementStrategy Strategy for merging measurements from merged objects.
     */
    private ObjectMerger(BiPredicate<PathObject, PathObject> compatibilityTest, BiPredicate<Geometry, Geometry> mergeTest, double searchDistance, MeasurementStrategy measurementStrategy) {
        this.compatibilityTest = compatibilityTest;
        this.mergeTest = mergeTest;
        this.searchDistance = searchDistance;
        this.measurementStrategy = measurementStrategy;
    }


    /**
     * Merge the input objects using the merging strategy.
     * @param pathObjects the input objects for which merges should be calculated
     * @return a list of objects, with the same number or fewer than the input
     * @deprecated Use {@link #process(Collection)} instead
     */
    @Deprecated
    public List<PathObject> merge(Collection<? extends PathObject> pathObjects) {
        return process(pathObjects);
    }

    /**
     * Calculate the result of applying the merging strategy to the input objects.
     * <p>
     * The output list will contain the same number of objects or fewer.
     * Objects that are not merged will be returned unchanged, while objects that are merged will be replaced by a new
     * objects with a new ROI.
     * <p>
     * New objects will be assigned new IDs.
     * Classifications will be preserved, but other measurements and properties will not be.
     * <p>
     * No guarantees are made about the mutability or ordering of the returned list.
     * @param pathObjects the input objects for which merges should be calculated
     * @return a list of objects, with the same number or fewer than the input
     */
    public List<PathObject> process(Collection<? extends PathObject> pathObjects) {

        if (pathObjects == null || pathObjects.isEmpty())
            return Collections.emptyList();
        if (pathObjects.size() == 1)
            return new ArrayList<>(pathObjects);

        // Comparing recursive and non-recursive approaches
        boolean doRecursive = useSearchDistance() &&
                System.getProperty("qupath.merge.recursive", "false").equalsIgnoreCase("true");
        if (doRecursive)
            logger.warn("Using recursive merging!");

        List<List<PathObject>> clustersToMerge;
        if (doRecursive)
            clustersToMerge = computeClustersRecursive(pathObjects);
        else
            clustersToMerge = computeClustersIterative(pathObjects);

        // Parallelize the merging - it can be slow
        var output = clustersToMerge.stream()
                .parallel()
                .map((List<PathObject> pathObjects1) -> mergeObjects(pathObjects1, measurementStrategy))
                .toList();
        assert output.size() <= pathObjects.size();
        return output;
    }

    /**
     * Create an object merger that uses a shared boundary IoU criterion and default overlap tolerance.
     * <p>
     * Objects will be merged if they share a common boundary and have the same classification.
     * A small overlap tolerance is used to compensate for sub-pixel misalignment of tiles.
     * <p>
     * This is intended for post-processing a tile-based segmentation, where the tiling has been strictly enforced
     * (i.e. any objects have been clipped to non-overlapping tile boundaries).
     *
     * @param sharedBoundaryThreshold minimum intersection-over-union (IoU) proportion of the possibly-clipped boundary
     *                                for merging
     * @param measurementStrategy strategy for merging measurements from merged objects.
     *
     * @return an object merger that uses a shared boundary criterion
     * @see #createSharedTileBoundaryMerger(double, double)
     */
    public static ObjectMerger createSharedTileBoundaryMerger(double sharedBoundaryThreshold, MeasurementStrategy measurementStrategy) {
        return createSharedTileBoundaryMerger(sharedBoundaryThreshold, 0.125, measurementStrategy);
    }

    /**
     * @see ObjectMerger#createSharedTileBoundaryMerger(double, double, MeasurementStrategy)
     */
    public static ObjectMerger createSharedTileBoundaryMerger(double sharedBoundaryThreshold) {
        return createSharedTileBoundaryMerger(sharedBoundaryThreshold, 0.125, MeasurementStrategy.IGNORE);
    }

    /**
     * Create an object merger that uses a shared boundary IoU criterion and overlap tolerance.
     * <p>
     * Objects will be merged if they share a common boundary and have the same classification.
     * A small overlap tolerance can be used to compensate for slight misalignment of tiles.
     * <p>
     * After identifying a common boundary line between ROIs, the ROI boundaries are intersected with the line,
     * and the two intersections are subsequently intersected with each other to determine the shared intersection.
     * The length of the shared intersection is then used to compute the intersection over union.
     * <p>
     * This is intended for post-processing a tile-based segmentation, where the tiling has been strictly enforced
     * (i.e. any objects have been clipped to non-overlapping tile boundaries).
     *
     * @param sharedBoundaryThreshold minimum intersection-over-union (IoU) proportion of the possibly-clipped boundary
     *      *                                for merging
     * @param overlapTolerance amount of overlap allowed between objects, in pixels. If zero, the boundary must be
     *                         shared exactly. A typical value is 0.125, which allows for a small, sub-pixel overlap.
     * @param measurementStrategy strategy for merging measurements from merged objects.
     * @return an object merger that uses a shared boundary criterion and overlap tolerance
     */
    public static ObjectMerger createSharedTileBoundaryMerger(double sharedBoundaryThreshold, double overlapTolerance, MeasurementStrategy measurementStrategy) {
        return new ObjectMerger(
                ObjectMerger::sameClassTypePlaneTest,
                createBoundaryOverlapTest(sharedBoundaryThreshold, overlapTolerance),
                0.0625,
                measurementStrategy);
    }

    /**
     * @see ObjectMerger#createSharedTileBoundaryMerger(double, double, MeasurementStrategy)
     */
    public static ObjectMerger createSharedTileBoundaryMerger(double sharedBoundaryThreshold, double overlapTolerance) {
        return createSharedTileBoundaryMerger(sharedBoundaryThreshold, overlapTolerance, MeasurementStrategy.IGNORE);
    }

    /**
     * Create an object merger that can merge together any objects with similar ROIs (e.g. points, areas), the same
     * classification, and are on the same image plane.
     * <p>
     * The ROIs do not need to be touching; the resulting merged objects can have discontinuous ROIs.
     * @param measurementStrategy strategy for merging measurements from merged objects.
     * @return an object merger that can merge together any objects with similar ROIs and the same classification
     */
    public static ObjectMerger createSharedClassificationMerger(MeasurementStrategy measurementStrategy) {
        return new ObjectMerger(
                ObjectMerger::sameClassTypePlaneTest,
                ObjectMerger::sameDimensions,
                -1,
                measurementStrategy);
    }

    /**
     * @see ObjectMerger#createSharedClassificationMerger(MeasurementStrategy)
     */
    public static ObjectMerger createSharedClassificationMerger() {
        return createSharedClassificationMerger(MeasurementStrategy.IGNORE);
    }

    /**
     * Create an object merger that can merge together any objects with similar ROIs (e.g. points, areas) that also
     * touch one another.
     * <p>
     * Objects must also have the same classification and be on the same image plane to be mergeable.
     * <p>
     * Note that this is a strict criterion following the Java Topology Suite definition of touching, which requires
     * that the boundaries of the geometries intersect, but the interiors do not intersect.
     * <p>
     * This strictness can cause unexpected results due to floating point precision issues, unless it is certain that
     * the ROIs are perfectly aligned (e.g they are generated using integer coordinates on a pixel grid).
     * <p>
     * If this is not the case, {@link #createSharedTileBoundaryMerger(double, double)} is usually preferable, since it
     * can include a small overlap tolerance.
     * @param measurementStrategy strategy for merging measurements from merged objects.
     *
     * @return an object merger that can merge together any objects with similar ROIs and the same classification
     * @see #createSharedTileBoundaryMerger(double, double)
     */
    public static ObjectMerger createTouchingMerger(MeasurementStrategy measurementStrategy) {
        return new ObjectMerger(
                ObjectMerger::sameClassTypePlaneTest,
                ObjectMerger::sameDimensionsAndTouching,
                0,
                measurementStrategy);
    }

    /**
     * @see ObjectMerger#createTouchingMerger(MeasurementStrategy)
     */
    public static ObjectMerger createTouchingMerger() {
        return createTouchingMerger(MeasurementStrategy.IGNORE);
    }


    /**
     * Create an object merger that can merge together any objects with sufficiently large intersection over union.
     * <p>
     * Objects must also have the same classification and be on the same image plane to be mergeable.
     * <p>
     * IoU is calculated using Java Topology Suite intersection, union, and getArea calls.
     * <p>
     * This merger assumes that you are using an OutputHandler that doesn't clip to tile boundaries (only to region
     * requests) and that you are using sufficient padding to ensure that objects are being detected in more than on
     * tile/region request.
     * You should probably also remove any objects that touch the regionRequest boundaries, as these will probably be
     * clipped, and merging them will result in weirdly shaped detections.
     * @param iouThreshold Intersection over union threshold; any pairs with values greater than or equal to this are merged.
     * @param measurementStrategy strategy for merging measurements from merged objects.
     * @return an object merger that can merge together any objects with sufficiently high IoU and the same classification
     */
    public static ObjectMerger createIoUMerger(double iouThreshold, MeasurementStrategy measurementStrategy) {
        return new ObjectMerger(
                ObjectMerger::sameClassTypePlaneTest,
                createIoUMergeTest(iouThreshold),
                0.0625,
                measurementStrategy);
    }

    /**
     * @see ObjectMerger#createIoUMerger(double, MeasurementStrategy)
     */
    public static ObjectMerger createIoUMerger(double iouThreshold) {
        return createIoUMerger(iouThreshold, MeasurementStrategy.IGNORE);
    }


    /**
     * Create an object merger that can merge together any objects with sufficiently large intersection over minimum
     * area (IoMin).
     * This is similar to IoU, but uses the minimum area of the two objects as the denominator.
     * <p>
     * This is useful in the (common) case where we are happy for small objects falling within larger objects to be
     * swallowed up by the larger object.
     * <p>
     * Objects must also have the same classification and be on the same image plane to be mergeable.
     * <p>
     * IoM is calculated using Java Topology Suite intersection, union, and getArea calls.
     * <p>
     * This merger assumes that you are using an OutputHandler that doesn't clip to tile boundaries (only to region
     * requests) and that you are using sufficient padding to ensure that objects are being detected in more than on
     * tile/region request.
     * You should probably also remove any objects that touch the regionRequest boundaries, as these will probably be
     * clipped, and merging them will result in weirdly shaped detections.
     * @param iomThreshold Intersection over minimum threshold; any pairs with values greater than or equal to this are merged.
     * @param measurementStrategy strategy for merging measurements from merged objects.
     * @return an object merger that can merge together any objects with sufficiently high IoM and the same classification
     * @implNote This method does not currently merge objects with zero area. It is assumed that they will be handled separately.
     */
    public static ObjectMerger createIoMinMerger(double iomThreshold, MeasurementStrategy measurementStrategy) {
        return new ObjectMerger(
                ObjectMerger::sameClassTypePlaneTest,
                createIoMinMergeTest(iomThreshold),
                0.0625,
                measurementStrategy);
    }

    /**
     * @see ObjectMerger#createIoMinMerger(double, MeasurementStrategy)
     */
    public static ObjectMerger createIoMinMerger(double iomThreshold) {
        return createIoMinMerger(iomThreshold, MeasurementStrategy.IGNORE);
    }


    /**
     * Recursive method - kept for reference and debugging, but not used.
     * @param allObjects
     * @return
     */
    private List<List<PathObject>> computeClustersRecursive(Collection<? extends PathObject> allObjects) {
        List<List<PathObject>> clusters = new ArrayList<>();
        Set<PathObject> alreadyVisited = new HashSet<>();

        var geometryMap = buildMutableGeometryMap(allObjects);
        var index = buildSpatialIndex(allObjects, geometryMap);

        for (var p : allObjects) {
            if (alreadyVisited.contains(p))
                continue;
            var cluster = addMergesRecursive(p, new ArrayList<>(), alreadyVisited, index, geometryMap);
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
     * <p>
     * This method is designed to be thread-safe.
     *
     * @param allObjects
     * @return
     */
    private List<List<PathObject>> computeClustersIterative(Collection<? extends PathObject> allObjects) {
        var geometryMap = buildMutableGeometryMap(allObjects);
        var index = buildSpatialIndex(allObjects, geometryMap);

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
                    var currentGeometry = getGeometry(current, geometryMap);
                    Collection<? extends PathObject> allPotentialNeighbors;
                    if (useSearchDistance())
                        allPotentialNeighbors = findCompatibleNeighbors(currentGeometry, index);
                    else
                        allPotentialNeighbors = allObjects;
                    var neighbors = filterCompatibleNeighbors(current, allPotentialNeighbors);
                    // alreadyVisited is not concurrent, so do this serially
                    var addable = neighbors.stream()
                            .filter(neighbor -> !alreadyVisited.contains(neighbor)).toList();
                    addable = addable.parallelStream()
                            .filter(neighbor -> mergeTest.test(
                                    currentGeometry,
                                    getGeometry(neighbor, geometryMap))).toList();
                    pending.addAll(addable);
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
     * This is a recursive implementation of the iterative method above, useful for debugging.
     *
     * @param pathObject an object to potentially add to the cluster
     * @param currentCluster the current cluster to which objects may be added, if they are compatible
     * @param alreadyVisited
     * @return
     * @throws UnsupportedOperationException if {@link #useSearchDistance()} is false
     */
    private List<PathObject> addMergesRecursive(PathObject pathObject, List<PathObject> currentCluster, Set<PathObject> alreadyVisited,
                                                SpatialIndex index, Map<ROI, Geometry> geometryMap) throws UnsupportedOperationException {
        if (!alreadyVisited.add(pathObject))
            return currentCluster;

        currentCluster.add(pathObject);

        if (!useSearchDistance())
            throw new UnsupportedOperationException("Recursive merging requires a search distance");

        var neighbors = findCompatibleNeighbors(getGeometry(pathObject, geometryMap), index);
        for (var neighbor : neighbors) {
            if (!alreadyVisited.contains(neighbor)) {
                if (mergeTest.test(
                        getGeometry(pathObject, geometryMap),
                        getGeometry(neighbor, geometryMap)))
                    addMergesRecursive(neighbor, currentCluster, alreadyVisited, index, geometryMap);
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
     * @param geometry
     * @return
     */
    private List<PathObject> findCompatibleNeighbors(Geometry geometry, SpatialIndex index) {
        var envelopeQuery = geometry.getEnvelopeInternal(); // This is documented to be a copy, so we can modify it
        double expansion = 1e-6;
        envelopeQuery.expandBy(expansion);
        return index.query(envelopeQuery);
    }

    private List<PathObject> filterCompatibleNeighbors(PathObject pathObject, Collection<? extends PathObject> potentialNeighbors) {
        return filterCompatibleNeighbors(pathObject, potentialNeighbors, compatibilityTest);
    }

    private static List<PathObject> filterCompatibleNeighbors(PathObject pathObject, Collection<? extends PathObject> potentialNeighbors, BiPredicate<PathObject, PathObject> compatibilityTest) {
        return potentialNeighbors.stream()
                .filter(p -> compatibilityTest.test(pathObject, p))
                .map(p -> (PathObject) p)
                .toList();
    }

    private Geometry getGeometry(PathObject pathObject, Map<ROI, Geometry> geometryMap) {
        return geometryMap.computeIfAbsent(pathObject.getROI(), ROI::getGeometry);
    }

    private static BiPredicate<Geometry, Geometry> createIoUMergeTest(double iouThreshold) {
        return (geom, geomOverlap) -> {
            var i = geom.intersection(geomOverlap);
            var intersection = i.getArea();
            double union = geom.getArea() + geomOverlap.getArea() - intersection;
            if (union == 0) {
                return false;
            }
            return (intersection / union) >= iouThreshold;
        };
    }

    private static BiPredicate<Geometry, Geometry> createIoMinMergeTest(double iomThreshold) {
        return (geom, geomOverlap) -> {
            double minArea = Math.min(geom.getArea(), geomOverlap.getArea());
            // If the minimum area is zero, then we can't calculate the IoM
            // Here, we don't merge - assuming that empty ROIs should be handled separately
            if (minArea == 0) {
                return false;
            }
            var i = geom.intersection(geomOverlap);
            var intersection = i.getArea();
            return (intersection / minArea) >= iomThreshold;
        };
    }

    /**
     * Method to use as a predicate, indicating that two geometries have the same dimension and also touch.
     * @param geom
     * @param geom2
     * @return
     */
    private static boolean sameDimensionsAndTouching(Geometry geom, Geometry geom2) {
        return sameDimensions(geom, geom2) && geom.touches(geom2);
    }


    /**
     * Method to use as a predicate, indicating that two geometries have the same dimension.
     * @param geom
     * @param geom2
     * @return
     */
    private static boolean sameDimensions(Geometry geom, Geometry geom2) {
        return geom.getDimension() == geom2.getDimension();
    }

    private static BiPredicate<Geometry, Geometry> createBoundaryOverlapTest(double sharedBoundaryThreshold, double pixelOverlapTolerance) {
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

    /**
     * Build a spatial index. Note that this implementation should not be modified later, only queried.
     * @param pathObjects
     * @param geometryMap
     * @return
     */
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


    /**
     * Simple test of object compatibility by checking if the objects are not identical but have the same
     * classification, ROI plane and type.
     * @param first
     * @param second
     * @return
     */
    private static boolean sameClassTypePlaneTest(PathObject first, PathObject second) {
        return (second != first &&
                Objects.equals(first.getPathClass(), second.getPathClass()) &&
                Objects.equals(first.getROI().getImagePlane(), second.getROI().getImagePlane()) &&
                Objects.equals(first.getClass(), second.getClass()));
    }


    private static Map<ROI, Geometry> buildMutableGeometryMap(Collection<? extends PathObject> pathObjects) {
        // Converting ROIs to Geometries can be one of the slower parts, so we parallelize
        var map = pathObjects.parallelStream()
                .filter(PathObject::hasROI)
                .map(PathObject::getROI)
                .distinct()
                .collect(Collectors.toConcurrentMap(Function.identity(), ROI::getGeometry));

        // We have no mutability guarantees from Collectors.toConcurrentMap
        return new ConcurrentHashMap<>(map);
    }


    private static PathObject mergeObjects(List<? extends PathObject> pathObjects, MeasurementStrategy measurementStrategy) {
        if (pathObjects.isEmpty())
            return null;

        var pathObject = pathObjects.getFirst();
        if (pathObjects.size() == 1)
            return pathObject;

        var allROIs = pathObjects.stream().map(PathObject::getROI)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        ROI mergedROI = RoiTools.union(allROIs);

        PathObject mergedObject = null;
        if (pathObject.isTile()) {
            mergedObject = PathObjects.createTileObject(mergedROI, pathObject.getPathClass());
        } else if (pathObject.isCell()) {
            var nucleusROIs = pathObjects.stream()
                    .map(PathObjectTools::getNucleusROI)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            ROI nucleusROI = nucleusROIs.isEmpty() ? null : RoiTools.union(nucleusROIs);
            mergedObject = PathObjects.createCellObject(mergedROI, nucleusROI, pathObject.getPathClass());
        } else if (pathObject.isDetection()) {
            mergedObject = PathObjects.createDetectionObject(mergedROI, pathObject.getPathClass());
        } else if (pathObject.isAnnotation()) {
            mergedObject = PathObjects.createAnnotationObject(mergedROI, pathObject.getPathClass());
        } else
            throw new IllegalArgumentException("Unsupported object type for merging: " + pathObject.getClass());

        measurementStrategy.mergeMeasurements(pathObjects, mergedObject.getMeasurementList());

        // We might need to transfer over the color as well
        var color = pathObject.getColor();
        if (color != null)
            mergedObject.setColor(color);
        return mergedObject;
    }


    private static double calculateUpperLowerSharedBoundaryIntersectionScore(Geometry upper, Geometry lower, double overlapTolerance) {
        var envUpper = upper.getEnvelopeInternal();
        var envLower = lower.getEnvelopeInternal();
        // We only consider the lower and upper boundaries, which must be very close to one another - with no gaps
        if (envUpper.getMaxY() >= envLower.getMinY() && Math.abs(envUpper.getMaxY() - envLower.getMinY()) < overlapTolerance) {
            var upperIntersection = createEnvelopeIntersection(upper, envUpper.getMinX(), envUpper.getMaxY(), envUpper.getMaxX(), envUpper.getMaxY());
            var lowerIntersection = createEnvelopeIntersection(lower, envLower.getMinX(), envLower.getMinY(), envLower.getMaxX(), envLower.getMinY());
            double upperLength = upperIntersection.getLength();
            double lowerLength = lowerIntersection.getLength();
            if (Math.min(upperLength, lowerLength) <= 0)
                return 0.0;
            // For a non-zero tolerance, we may need to shift the geometries
            if (envUpper.getMaxY() != envLower.getMinY()) {
                lowerIntersection.apply(new SetOrdinateFilter(CoordinateSequence.Y, envUpper.getMaxY()));
            }
            // Use intersection over union
            lowerIntersection = GeometryTools.homogenizeGeometryCollection(lowerIntersection);
            upperIntersection = GeometryTools.homogenizeGeometryCollection(upperIntersection);
            var sharedIntersection = upperIntersection.intersection(lowerIntersection);
            double intersectionLength = sharedIntersection.getLength();
            return intersectionLength / (upperLength + lowerLength - intersectionLength);
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
            double leftLength = leftIntersection.getLength();
            double rightLength = rightIntersection.getLength();
            if (Math.min(leftLength, rightLength) <= 0)
                return 0.0;
            // For a non-zero tolerance, we may need to shift the geometries
            if (envLeft.getMaxX() != envRight.getMinX()) {
                rightIntersection.apply(new SetOrdinateFilter(CoordinateSequence.Y, envLeft.getMaxY()));
            }
            // Use intersection over union
            leftIntersection = GeometryTools.homogenizeGeometryCollection(leftIntersection);
            rightIntersection = GeometryTools.homogenizeGeometryCollection(rightIntersection);
            var sharedIntersection = rightIntersection.intersection(leftIntersection);
            double intersectionLength = sharedIntersection.getLength();
            return intersectionLength / (rightLength + leftLength - intersectionLength);
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

    /**
     * Ordinate filter to set a single ordinate to a fixed value, in-place.
     */
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
