package qupath.lib.analysis.images;

import org.locationtech.jts.dissolve.LineDissolver;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Some utility functions to help with converting traced line segments (coordinate pairs) into
 * JTS geometry objects.
 * <p>
 * Some of the functionality could be replicated by using JTS directly, but this class is optimized
 * or the specific requirements of tracing raster images (where we know the coordinate pairs should
 * represent horizontal or vertical lines).
 */
class ContourTracingUtils {

    private static final Logger logger = LoggerFactory.getLogger(ContourTracingUtils.class);


    /**
     * Create a new collection that contains only pairs that did not have any duplicate in the input.
     * This is different from removing duplicates: if a pair is duplicated, all instances of it are removed.
     * @param lines
     * @return
     */
    static Collection<CoordinatePair> removeDuplicatesCompletely(Collection<CoordinatePair> lines) {
        CoordinatePair lastPair = null;
        List<CoordinatePair> pairs = new ArrayList<>();
        boolean duplicate = false;
        var iterator = lines.stream().sorted().iterator();
        while (iterator.hasNext()) {
            var line = iterator.next();
            if (Objects.equals(lastPair, line)) {
                duplicate = true;
            } else {
                if (!duplicate && lastPair != null)
                    pairs.add(lastPair);
                duplicate = false;
            }
            lastPair = line;
        }
        if (!duplicate)
            pairs.add(lastPair);
        return pairs;
    }

    // Alternative implementation: more readable, but has slightly more overhead
//	private static Collection<CoordinatePair> removeDuplicatesCompletely(List<CoordinatePair> lines) {
//		Set<CoordinatePair> set = HashSet.newHashSet(lines.size());
//		Set<CoordinatePair> duplicates = HashSet.newHashSet(lines.size() / 4);
//		for (var line : lines) {
//			if (!set.add(line)) {
//				duplicates.add(line);
//			}
//		}
//		set.removeAll(duplicates);
//		return List.copyOf(set);
//	}

    /**
     * Convert a collection of pairs from contour tracing into line strings for polygonization.
     * This is the 'default' method based on JTS.
     * It should do the same as {@link #linesFromPairsFast(GeometryFactory, Collection, double, double, double)}...
     * but a bit more slowly for very large geometries.
     */
    static Geometry linesFromPairsLegacy(GeometryFactory factory, Collection<CoordinatePair> pairs,
                                           double xOrigin, double yOrigin, double scale) {
        var dissolver = new LineDissolver();
        for (var p : pairs) {
            dissolver.add(createLineString(p, factory, xOrigin, yOrigin, scale));
        }
        var lineStrings = dissolver.getResult();
        return DouglasPeuckerSimplifier.simplify(lineStrings, 0);
    }

    private static LineString createLineString(CoordinatePair pair, GeometryFactory factory, double xOrigin, double yOrigin, double scale) {
        var pm = factory.getPrecisionModel();
        var c1 = pair.c1();
        var c2 = pair.c2();
        double x1 = pm.makePrecise(xOrigin + c1.getX() * scale);
        double x2 =  pm.makePrecise(xOrigin + c2.getX() * scale);
        double y1 =  pm.makePrecise(yOrigin + c1.getY() * scale);
        double y2 =  pm.makePrecise(yOrigin + c2.getY() * scale);
        return factory.createLineString(new Coordinate[] {
                new Coordinate(x1, y1), new Coordinate(x2, y2)});
    }

    /**
     * Coordinates can only be merged in a linestring if they occur exactly twice (otherwise they must be noded).
     * This method finds all coordinates that can't be merged, and returns them as a set.
     * <p>
     * Note that previous implementations used a HashMap to count occurrences, but this was found to be much slower.
     */
    private static MinimalCoordinateSet findNonMergeableCoordinates(Collection<CoordinatePair> pairList) {
        // Extract all the coordinates
        var allCoordinates = new ArrayList<IntPoint>(pairList.size()*2);
        for (var p : pairList) {
            allCoordinates.add(p.c1());
            allCoordinates.add(p.c2());
        }

        // Sort the list so that we can count occurrences in a single pass
        allCoordinates.sort(null);

        // Find which that can't be merged, i.e. they don't occur exactly twice
        int count = 0;
        IntPoint currentCoord = null;
        long startTime = System.currentTimeMillis();
        var nonMergeable = new MinimalCoordinateSet(allCoordinates.size()/10);
        for (var c : allCoordinates) {
            if (Objects.equals(currentCoord, c)) {
                count++;
            } else {
                if (count != 2 && currentCoord != null)
                    nonMergeable.add(currentCoord);
                currentCoord = c;
                count = 1;
            }
        }

        nonMergeable.rebuild();
        long endTime = System.currentTimeMillis();
        logger.trace("Time to find non-mergeable coordinates: " + (endTime - startTime) + " ms");
        return nonMergeable;
    }

    /**
     * Faster alternative to {@link #linesFromPairsLegacy(GeometryFactory, Collection, double, double, double)}
     * This takes a collection of {@link CoordinatePair} which should represent <i>all</i> the lines generated by
     * coordinate tracing (as one-pixel horizontal or vertical segments).
     * It then builds line strings from these pairs, merging them where possible, before passing the results to
     * a {@link org.locationtech.jts.operation.polygonize.Polygonizer} to build the final geometry.
     */
    static Geometry linesFromPairsFast(GeometryFactory factory, Collection<CoordinatePair> pairs,
                                            double xOrigin, double yOrigin, double scale) throws InterruptedException {

        // Sort now to avoid sorting later
        var pairList = pairs.stream()
                .sorted()
                .toList();

        var nonMergeable = findNonMergeableCoordinates(pairs);

        // Find horizontal & vertical edges, split by row and column
        var horizontal = new TreeMap<Integer, List<CoordinatePair>>();
        var vertical = new TreeMap<Integer, List<CoordinatePair>>();
        for (var p : pairList) {
            if (p.isHorizontal())
                horizontal.computeIfAbsent(p.c1().getY(), y -> new ArrayList<>()).add(p);
            else if (p.isVertical())
                vertical.computeIfAbsent(p.c2().getX(), x -> new ArrayList<>()).add(p);
        }

        // May not really matter if we start with horizontal or vertical
        var firstDirection = horizontal.size() <= vertical.size() ? horizontal : vertical;
        var secondDirection = firstDirection == horizontal ? vertical : horizontal;

        Collection<List<IntPoint>> lines = new ArrayList<>(pairs.size()/10);
//        Map<IntPoint, List<IntPoint>> mergeable = HashMap.newHashMap(pairs.size()); // Retained for debugging & performance checks
        var mergeable = new MinimalCoordinateMap<List<IntPoint>>(Math.max(100, (int)Math.sqrt(pairList.size())));
        for (var entry : firstDirection.entrySet()) {
            var list = entry.getValue();
            for (var ls : buildLineStrings(list, nonMergeable::contains)) {
                var c1 = ls.getFirst();
                var c2 = ls.getLast();
                boolean isMergeable = false;
                if (!nonMergeable.contains(c1)) {
                    if (mergeable.put(c1, ls) != null)
                        throw new RuntimeException("Unexpected mergeable already exists");
                    isMergeable = true;
                }
                if (!nonMergeable.contains(c2)) {
                    if (mergeable.put(c2, ls) != null)
                        throw new RuntimeException("Unexpected mergeable already exists");
                    isMergeable = true;
                }
                if (!isMergeable)
                    lines.add(ls);
            }
        }

        var queued = new ArrayDeque<List<IntPoint>>();
        for (var entry : secondDirection.entrySet()) {
            var list = entry.getValue();
            queued.addAll(buildLineStrings(list, nonMergeable::contains));
            while (!queued.isEmpty()) {
                var ls = queued.pop();
                if (isClosed(ls)) {
                    lines.add(ls);
                    continue;
                }
                if (Thread.interrupted())
                    throw new InterruptedException("Contour tracing interrupted!");
                var c1 = ls.getFirst();
                var c2 = ls.getLast();
                boolean c1Mergeable = !nonMergeable.contains(c1);
                boolean c2Mergeable = !nonMergeable.contains(c2);
                if (c1Mergeable) {
                    var existing = mergeable.remove(c1);
                    if (existing != null) {
                        if (c1.equals(existing.getFirst()))
                            mergeable.remove(existing.getLast());
                        else
                            mergeable.remove(existing.getFirst());

                        queued.add(mergeLines(existing, ls));
                        continue;
                    }
                }
                if (c2Mergeable) {
                    var existing = mergeable.remove(c2);
                    if (existing != null) {
                        if (c2.equals(existing.getFirst()))
                            mergeable.remove(existing.getLast());
                        else
                            mergeable.remove(existing.getFirst());
                        queued.add(mergeLines(existing, ls));
                        continue;
                    }
                }
                if (c1Mergeable || c2Mergeable) {
                    if (c1Mergeable)
                        mergeable.put(c1, ls);
                    if (c2Mergeable)
                        mergeable.put(c2, ls);
                } else {
                    // Line is complete
                    lines.add(ls);
                }
            }
        }

        if (!mergeable.isEmpty())
            logger.warn("Remaining mergeable lines: {}", mergeable.size());
        lines.addAll(mergeable.values());

        var lineStrings = new ArrayList<LineString>();
        var pm = factory.getPrecisionModel();
        for (var line : lines) {
            var coords = new Coordinate[line.size()];
            for (int i = 0; i < line.size(); i++) {
                var c = line.get(i);
                double x = pm.makePrecise(xOrigin + c.getX() * scale);
                double y = pm.makePrecise(yOrigin + c.getY() * scale);
                coords[i] = new Coordinate(x, y);
            }
            lineStrings.add(factory.createLineString(coords));
        }

        return factory.buildGeometry(lineStrings);
    }

    private static boolean isClosed(List<IntPoint> coords) {
        return coords.size() > 2 && coords.getFirst().equals(coords.getLast());
    }

    /**
     * Merge two lines.
     * Important! It is assumed that neither line is required after merging.
     * This method is therefore permitted to reuse either list for the output, if it is mutable.
     */
    private static List<IntPoint> mergeLines(List<IntPoint> l1, List<IntPoint> l2) {
        // TODO: This could be optimized by ensuring lists are mutable, and reusing an existing list
        var c1Start = l1.getFirst();
        var c1End = l1.getLast();
        var c2Start = l2.getFirst();
        var c2End = l2.getLast();

        if (c1End.equals(c2Start)) {
            return concat(l1, l2);
        } else if (c1Start.equals(c2End)) {
            return concat(l2, l1);
        } else if (c1Start.equals(c2Start)) {
            return concat(l1.reversed(), l2);
        } else if (c1End.equals(c2End)) {
            return concat(l1, l2.reversed());
        } else {
            throw new IllegalArgumentException("Lines are not mergeable");
        }
    }

    private static List<IntPoint> concat(List<IntPoint> l1, List<IntPoint> l2) {
        int n = l1.size() + l2.size() - 1;
        // ArrayLists are mutable - so we can just add to them
        if (l1 instanceof ArrayList<IntPoint> list) {
            list.addAll(l2);
            return list;
        } else if (l2 instanceof ArrayList<IntPoint> list) {
            list.addAll(0, l1);
            return list;
        }
        var list = new ArrayList<IntPoint>(n);
        list.addAll(l1);
        list.addAll(l2.subList(1, l2.size()));
        return list;
    }


    private static List<List<IntPoint>> buildLineStrings(List<CoordinatePair> pairs, Predicate<IntPoint> counter) {
        if (pairs.isEmpty())
            return List.of();

        List<List<IntPoint>> lines = new ArrayList<>();
        IntPoint firstCoord = pairs.getFirst().c1();
        IntPoint secondCoord = pairs.getFirst().c2();
        for (int i = 1; i < pairs.size(); i++) {
            var p = pairs.get(i);
            if (!secondCoord.equals(p.c1()) || counter.test(secondCoord)) {
                // Finish the line we were building & start a new one
                lines.add(createLineString(firstCoord, secondCoord));
                firstCoord = p.c1();
                secondCoord = p.c2();
                continue;
            } else {
                // Continue the line
                secondCoord = p.c2();
            }
        }
        lines.add(createLineString(firstCoord, secondCoord));
        return lines;
    }

    private static List<IntPoint> createLineString(IntPoint c1, IntPoint c2) {
        return List.of(c1, c2);
    }


    /**
     * A minimal map-like class that can use coordinates as keys.
     * I know this looks awkward... but it can perform much better than a single HashMap<Coordinate, T>.
     * @param <T>
     */
    private static class MinimalCoordinateMap<T> {

        private final int numMappings;
        private final Map<Integer, Map<Integer, T>> map;

        MinimalCoordinateMap(int numMappings) {
            this.numMappings = numMappings;
            this.map = HashMap.newHashMap(numMappings);
        }

        T put(IntPoint c, T val) {
            return map.computeIfAbsent(c.getX(), x -> HashMap.newHashMap(numMappings)).put(c.getY(), val);
        }

        T remove(IntPoint c) {
            var temp = map.getOrDefault(c.getX(), null);
            if (temp != null) {
                T value = temp.remove(c.getY());
                if (temp.isEmpty())
                    map.remove(c.getX());
                return value;
            }
            return null;
        }

        T get(IntPoint c) {
            return getOrDefault(c, null);
        }

        T getOrDefault(IntPoint c, T defaultValue) {
            return map.getOrDefault(c.getX(), Collections.emptyMap()).getOrDefault(c.getY(), defaultValue);
        }

        Collection<T> values() {
            if (isEmpty())
                return Collections.emptyList();
            return map.values().stream().flatMap(m -> m.values().stream()).toList();
        }

        int size() {
            return map.values().stream().mapToInt(Map::size).sum();
        }

        boolean isEmpty() {
            return map.values().stream().allMatch(Map::isEmpty);
        }

    }


    /**
     * A minimal set-like class to store coordinates.
     * This tries to be at least slightly faster than using a HashSet only (which is a bottleneck for main coordinates),
     * and is a placeholder for future optimizations.
     */
    private static class MinimalCoordinateSet {

        private Set<IntPoint> set;

        private IntPoint lastContained;
        private IntPoint lastNotContained;

        private long[] values;

        MinimalCoordinateSet(int numElements) {
            set = HashSet.newHashSet(numElements);
        }

        boolean add(IntPoint p) {
            this.values = null; // Reset values
            return set.add(p);
        }

        void rebuild() {
            // Performance of hashset seems to degrade a lot when it's huge - so revert to binary search instead
            if (set.size() > 1_000_000) {
                this.values = set.stream().mapToLong(IntPoint::value).sorted().toArray();
            }
        }

        boolean contains(IntPoint p) {
            // Querying the same points soon after each other is likely (due to how we scan the image),
            // so storing the last result can reduce at least some queries.
            if (Objects.equals(lastContained, p))
                return true;
            if (Objects.equals(lastNotContained, p))
                return false;
            if (values != null)
                return Arrays.binarySearch(values, p.value()) >= 0;
            if (set.contains(p)) {
                lastContained = p;
                return true;
            } else {
                lastNotContained = p;
                return false;
            }
        }

    }

}
