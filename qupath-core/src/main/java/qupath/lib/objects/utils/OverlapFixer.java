package qupath.lib.objects.utils;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.quadtree.Quadtree;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OverlapFixer {

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

    private final Strategy strategy;

    private OverlapFixer(Strategy strategy, double minArea, Supplier<Comparator<PathObject>> comparatorSupplier) {
        this.strategy = strategy;
        this.minArea = minArea;
        this.comparatorSupplier = comparatorSupplier;
    }



    public List<PathObject> fix(Collection<? extends PathObject> pathObjects) {
        var index = new Quadtree();

        // Sort objects in *reverse* order using the comparator.
        // This is because removals from the end of the list are faster than the start.
        // We don't use a queue because then we can't insert objects in the middle,
        // and we don't use a priority queue because it doesn't have great performance.
        var comparator = comparatorSupplier.get();
        var reversedComparator = comparator.reversed();
        List<PathObject> list = pathObjects.parallelStream()
                .filter(p -> p.hasROI() && p.getROI().getArea() >= minArea)
                .sorted(reversedComparator)
                .collect(Collectors.toCollection(ArrayList::new));

        // Nothing else to do if we're keeping overlaps
        if (strategy == Strategy.KEEP_OVERLAPS) {
            return list;
        }

        // Precompute envelopes - it's better to do it in parallel since it requests geometries,
        // which can sometimes be expensive to compute
        GeometryCache cache = new GeometryCache();
        pathObjects.parallelStream().forEach(cache::add);

        // Build the spatial index
        for (var pathObject : pathObjects) {
            index.insert(cache.getEnvelope(pathObject), pathObject);
        }

        // Query the index to find overlapping objects
        // We are iterating in order of the objects we want to keep
        List<PathObject> output = new ArrayList<>();
        while (!list.isEmpty()) {
            PathObject pathObject = list.removeLast();
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
            for (var overlap : overlapping) {
                index.remove(cache.getEnvelope(overlap), overlap);
            }
            if (strategy == Strategy.CLIP_OVERLAPS) {
                // Clip overlapping objects, inserting them back into the list if they are big enough
                List<ROI> previousROIs = new ArrayList<>();
                previousROIs.add(pathObject.getROI());
                for (var overlap : overlapping) {
                    // Subtract the union from the current object
                    ROI roiCurrent = overlap.getROI();
                    roiCurrent = RoiTools.subtract(roiCurrent, previousROIs);
                    ROI roiNucleus = PathObjectTools.getNucleusROI(overlap);
                    if (roiNucleus != null) {
                        roiNucleus = RoiTools.subtract(roiNucleus, previousROIs);
                    }
                    // Retain the ROI if it is big enough
                    if (!roiCurrent.isEmpty() && roiCurrent.isArea() && roiCurrent.getArea() >= minArea) {
                        var clippedObject = PathObjectTools.createLike(pathObject, roiCurrent, roiNucleus);
                        output.add(clippedObject);
                        index.insert(cache.getEnvelope(clippedObject), clippedObject);
                        previousROIs.add(roiCurrent);
                        // Insert into the list, while ensuring it remains sorted
                        int ind = Collections.binarySearch(list, clippedObject, reversedComparator);
                        if (ind >= 0) {
                            list.add(ind, clippedObject);
                        } else {
                            list.add(-ind - 1, clippedObject);
                        }
                    }
                }
            }
        }
        return output;
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

    public static class Builder {

        private Supplier<Comparator<PathObject>> comparator = () -> Comparators.createAreaFirstComparator();

        private double minArea = Double.NEGATIVE_INFINITY;

        private Strategy strategy = Strategy.CLIP_OVERLAPS;

        private Builder() {}

        public Builder setComparator(Comparator<PathObject> comparator) {
            this.comparator = () -> comparator;
            return this;
        }

        public Builder setMinArea(double minArea) {
            this.minArea = minArea;
            return this;
        }

        public Builder sortBySolidity() {
            this.comparator = () -> Comparators.createSolidityFirstComparator();
            return this;
        }

        public Builder sortByArea() {
            this.comparator = () -> Comparators.createAreaFirstComparator();
            return this;
        }

        public Builder setStrategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder clipOverlaps() {
            this.strategy = Strategy.CLIP_OVERLAPS;
            return this;
        }

        public Builder dropOverlaps() {
            this.strategy = Strategy.DROP_OVERLAPS;
            return this;
        }

        public OverlapFixer build() {
            return new OverlapFixer(strategy, minArea, comparator);
        }

    }



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
