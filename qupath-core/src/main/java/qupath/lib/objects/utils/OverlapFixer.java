/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OverlapFixer implements ObjectProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OverlapFixer.class);

    public enum Strategy {
        KEEP_OVERLAPS,
        DROP_OVERLAPS,
        CLIP_OVERLAPS
    }

    private enum ComparatorType {
        AREA,
        SOLIDITY
    }

    /**
     * Comparator to choose which object to keep unchanged when two or more overlap.
     * Objects returned first are kept unchanged, objects returned later are dropped or clipped.
     */
    private final Supplier<Comparator<PathObject>> comparatorSupplier;

    /**
     * Minimum area for objects to be retained.
     */
    private final double minArea;

    private final boolean keepFragments;

    private final Strategy strategy;

    private OverlapFixer(Strategy strategy, double minArea, Supplier<Comparator<PathObject>> comparatorSupplier, boolean keepFragments) {
        this.strategy = strategy;
        this.minArea = minArea;
        this.comparatorSupplier = comparatorSupplier;
        this.keepFragments = keepFragments;
    }


    /**
     * Fix overlaps in a collection of PathObjects, by the criteria specified in the builder.
     * This method is thread-safe.
     * @param pathObjects the input objects
     * @return the output objects. This may be the same as the input objects, or contain fewer objects -
     *         possibly with new (clipped) ROIs - but no object will be added or have its properties changed.
     */
    public List<PathObject> process(Collection<? extends PathObject> pathObjects) {

        int nInput = pathObjects.size();

        // Apply the area filter
        List<PathObject> list = pathObjects.parallelStream()
                .filter(p -> p.hasROI() && p.getROI().getArea() >= minArea)
                .collect(Collectors.toList());

        // Nothing else to do if we're keeping overlaps
        if (strategy == Strategy.KEEP_OVERLAPS) {
            return list;
        }

        // Precompute envelopes - it's better to do it in parallel since it requests geometries,
        // which can sometimes be expensive to compute
        GeometryCache cache = new GeometryCache();
        pathObjects.parallelStream().forEach(cache::add);

        // Build a first spatial index for efficient overlap detection - this can be immutable for performance
        SpatialIndex immutableIndex = new STRtree();
        populateSpatialIndex(pathObjects, immutableIndex, cache);
        for (var pathObject : pathObjects) {
            immutableIndex.insert(cache.getEnvelope(pathObject), pathObject);
        }

        // Split the objects into two groups: those with overlaps and those without
        // Ensure the outputs are ArrayLists so that they are modifiable
        Map<Boolean, List<PathObject>> overlapMap = pathObjects.parallelStream()
                .collect(
                        Collectors.groupingBy(p -> containsOverlaps(p, immutableIndex, cache),
                                Collectors.toCollection(ArrayList::new)));

        // Initialize the output to contain all objects with no overlaps
        List<PathObject> output = overlapMap.computeIfAbsent(Boolean.FALSE, b -> new ArrayList<>());

        // If we've got no objects with overlaps, we're done
        if (overlapMap.getOrDefault(Boolean.TRUE, Collections.emptyList()).isEmpty()) {
            logger.debug("No overlaps found in {} objects", nInput);
            return output;
        }

        // Create a sorted set to store the objects to process, ordered using the comparator
        var comparator = comparatorSupplier.get();
        var toProcess = new TreeSet<>(comparator);
        toProcess.addAll(overlapMap.get(Boolean.TRUE));

        // Build a new (hopefully much smaller!) modifiable spatial index for the objects with overlaps
        // This must be mutable, so that we can both remove and add objects
        SpatialIndex index = new Quadtree();
        populateSpatialIndex(toProcess, index, cache);

        // Query the index to find overlapping objects
        // We are iterating in order of the objects we want to keep
        while (!toProcess.isEmpty()) {
            PathObject pathObject = toProcess.removeFirst();
            var envelope = cache.getEnvelope(pathObject);
            // Query returns *potentially* overlapping objects (including the current object),
            // but we can proceed quickly if there are no others
            List<PathObject> overlapping = index.query(envelope);
            if (!overlapping.contains(pathObject)) {
                // Object has already been removed - skip
                continue;
            }
            // Keep this object
            output.add(pathObject);
            if (overlapping.size() > 1) {
                // Perform stricter overlap check
                var geom = cache.getGeometry(pathObject);
                if (overlapping.size() > 2) {
                    var prepared = PreparedGeometryFactory.prepare(geom);
                    overlapping = overlapping.stream()
                            .filter(p -> p != pathObject)
                            .filter(p -> prepared.overlaps(cache.getGeometry(p)) || geom.equalsExact(cache.getGeometry(p)))
                            .sorted(comparator)
                            .toList();
                } else {
                    overlapping = overlapping.stream()
                            .filter(p -> p != pathObject)
                            .filter(p -> geom.overlaps(cache.getGeometry(p)) || geom.equalsExact(cache.getGeometry(p)))
                            .sorted(comparator)
                            .toList();
                }
            }
            if (overlapping.isEmpty()) {
                // No overlaps - continue
                continue;
            }
            // Drop all overlapping objects
            // We only need to remove them from the index (to avoid the cost of removing them from the list)
            // TODO: Consider looking for non-overlapping clusters of objects to handle together
            for (var overlap : overlapping) {
                if (!index.remove(cache.getEnvelope(overlap), overlap)) {
                    logger.warn("Failed to remove object from index: " + overlap);
                }
                toProcess.remove(overlap);
            }
            if (strategy == Strategy.CLIP_OVERLAPS) {
                // Clip overlapping objects, inserting them back into the list if they are big enough
                List<ROI> previousROIs = new ArrayList<>();
                previousROIs.add(pathObject.getROI());
                for (var overlap : overlapping) {
                    // Subtract the union from the current object
                    ROI roiCurrent = overlap.getROI();
                    int nPiecesOriginally = roiCurrent.getGeometry().getNumGeometries();
                    var roiUpdated = RoiTools.subtract(roiCurrent, previousROIs);
                    ROI roiNucleus = PathObjectTools.getNucleusROI(overlap);
                    if (roiNucleus != null) {
                        roiNucleus = RoiTools.subtract(roiNucleus, previousROIs);
                    }
                    // Only keep the object if it is big enough, and optionally check the number of fragments
                    int nPieces = roiUpdated.getGeometry().getNumGeometries();
                    if (keepFragments || nPieces <= nPiecesOriginally) {
                        if (!roiUpdated.isEmpty() && roiUpdated.isArea() && roiUpdated.getArea() >= minArea) {
                            var clippedObject = PathObjectTools.createLike(pathObject, roiUpdated, roiNucleus);
                            // Don't add clipped objects to the output list!
                            // Rather, add to the set of objects to process & index - and they *might* end up in the output
                            index.insert(cache.getEnvelope(clippedObject), clippedObject);
                            previousROIs.add(roiCurrent);
                            toProcess.add(clippedObject);
                        }
                    }
                }
            }
        }
        logger.debug("Processed {} objects to fix overlaps, retaining {} objects", nInput, output.size());
        return output;
    }

    private static void populateSpatialIndex(Collection<? extends PathObject> pathObjects, SpatialIndex index, GeometryCache cache) {
        for (var pathObject : pathObjects) {
            index.insert(cache.getEnvelope(pathObject), pathObject);
        }
    }


    private static boolean containsOverlaps(PathObject pathObject, SpatialIndex index, GeometryCache cache) {
        var envelope = cache.getEnvelope(pathObject);
        List<PathObject> maybeOverlapping = index.query(envelope);
        if (maybeOverlapping.size() <= 1)
            return false;
        var geom = cache.getGeometry(pathObject);
        for (var maybe : maybeOverlapping) {
            if (maybe == pathObject)
                continue;
            var geomMaybe = cache.getGeometry(maybe);
            if (geom.overlaps(geomMaybe) || geom.equalsExact(geomMaybe))
                return true;
        }
        return false;
    }


    /**
     * A cache of normalized geometries and envelopes.
     * This can be useful for a short time to avoid unnecessary recomputation.
     */
    private static class GeometryCache {

        private final Map<ROI, Geometry> geometryMap = new ConcurrentHashMap<>();
        private final Map<ROI, Envelope> envelopMap = new ConcurrentHashMap<>();

        private void add(PathObject pathObject) {
            var roi = pathObject.getROI();
            var geom = roi.getGeometry().norm();
            geometryMap.put(roi, geom);
            envelopMap.put(roi, getEnvelope(pathObject));
        }

        private Geometry getGeometry(PathObject pathObject) {
            return getGeometry(pathObject.getROI());
        }

        private Geometry getGeometry(ROI roi) {
            return geometryMap.computeIfAbsent(roi, r -> r.getGeometry().norm());
        }

        private Envelope getEnvelope(PathObject pathObject) {
            return getEnvelope(pathObject.getROI());
        }

        private Envelope getEnvelope(ROI roi) {
            return envelopMap.computeIfAbsent(roi, r -> getGeometry(r).getEnvelopeInternal());
        }

    }


    /**
     * Create a new builder for the OverlapFixer.
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for the OverlapFixer.
     */
    public static class Builder {

        private Supplier<Comparator<PathObject>> comparator = () -> Comparators.createAreaFirstComparator();

        private double minArea = Double.NEGATIVE_INFINITY;

        private Strategy strategy = Strategy.CLIP_OVERLAPS;

        private boolean keepFragments = false;

        private Builder() {}

        /**
         * Set the minimum area for objects to be retained, in pixels.
         * Objects with an area less than this (either before or after clipping) will be dropped.
         * @param minArea
         * @return
         */
        public Builder setMinArea(double minArea) {
            this.minArea = minArea;
            return this;
        }

        /**
         * Set the comparator to use for sorting objects.
         * This assigns a 'priority' to objects, which is used to determine which objects are kept when overlaps occur.
         * Objects that are sorted to be earlier in the list are considered to have a higher priority.
         * @param comparator
         * @return
         */
        public Builder setComparator(Comparator<PathObject> comparator) {
            this.comparator = () -> comparator;
            return this;
        }

        /**
         * Set the comparator to sort by solidity, with the most solid objects given a higher priority.
         * Subsequent sorting is by area, length, number of points, and finally by the default comparator.
         * @return
         */
        public Builder sortBySolidity() {
            this.comparator = () -> Comparators.createSolidityFirstComparator();
            return this;
        }

        /**
         * Set the comparator to sort by area, with the largest objects given a higher priority.
         * Subsequent sorting is by length, number of points, and finally by the default comparator.
         * @return
         */
        public Builder sortByArea() {
            this.comparator = () -> Comparators.createAreaFirstComparator();
            return this;
        }

        /**
         * Equivalent to keepFragments(true).
         * @return
         */
        public Builder keepFragments() {
            return keepFragments(true);
        }

        /**
         * Set whether to keep fragments when clipping objects.
         * Fragments are defined as objects that are split into more pieces after clipping than they were before.
         * @param doKeep
         * @return
         */
        public Builder keepFragments(boolean doKeep) {
            this.keepFragments = doKeep;
            return this;
        }

        /**
         * Equivalent to keepFragments(false).
         * @return
         */
        public Builder discardFragments() {
            return keepFragments(false);
        }

        /**
         * Set the strategy for handling overlaps.
         * @param strategy
         * @return
         */
        public Builder setStrategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * Clip overlapping objects, excluding the parts that overlap with a 'higher priority' object
         * according to the comparator.
         * @return
         */
        public Builder clipOverlaps() {
            this.strategy = Strategy.CLIP_OVERLAPS;
            return this;
        }

        /**
         * Retain only the 'highest priority' objects when overlaps occur, and drop the others.
         * Priority is determined by the comparator.
         * @return
         */
        public Builder dropOverlaps() {
            this.strategy = Strategy.DROP_OVERLAPS;
            return this;
        }

        /**
         * Build the overlap fixer.
         * @return
         */
        public OverlapFixer build() {
            return new OverlapFixer(strategy, minArea, comparator, keepFragments);
        }

    }


    /**
     * Class to create comparators for PathObjects based on different criteria.
     * This can cache measurement values, to ensure they don't need to be recomputed.
     */
    private static class Comparators {

        private static Comparator<PathObject> createAreaFirstComparator() {
            var c = new Comparators();
            return c.compareByArea()
                    .thenComparing(c.compareByLength())
                    .thenComparing(c.compareByPoints())
                    .thenComparing(DefaultPathObjectComparator.getInstance());
        }

        private static Comparator<PathObject> createSolidityFirstComparator() {
            var c = new Comparators();
            return c.compareBySolidity()
                    .thenComparing(c.compareByArea())
                    .thenComparing(c.compareByLength())
                    .thenComparing(c.compareByPoints())
                    .thenComparing(DefaultPathObjectComparator.getInstance());
        }

        private Map<ROI, Double> solidityMap = new ConcurrentHashMap<>();
        private Map<ROI, Double> areaMap = new ConcurrentHashMap<>();
        private Map<ROI, Double> lengthMap = new ConcurrentHashMap<>();
        private Map<ROI, Integer> pointsMap = new ConcurrentHashMap<>();

        // We use negative values as a cheap way to sort in descending order

        private Comparator<PathObject> compareBySolidity() {
            return Comparator.comparingDouble(p -> -solidityMap.computeIfAbsent(p.getROI(), ROI::getSolidity));
        }

        private Comparator<PathObject> compareByArea() {
            return Comparator.comparingDouble(p -> -areaMap.computeIfAbsent(p.getROI(), ROI::getArea));
        }

        private Comparator<PathObject> compareByLength() {
            return Comparator.comparingDouble(p -> -lengthMap.computeIfAbsent(p.getROI(), ROI::getArea));
        }

        private Comparator<PathObject> compareByPoints() {
            return Comparator.comparingInt(p -> -pointsMap.computeIfAbsent(p.getROI(), ROI::getNumPoints));
        }

    }

}
