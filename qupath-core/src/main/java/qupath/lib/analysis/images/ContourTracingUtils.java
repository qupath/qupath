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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
        var comparator = Comparator.comparing(CoordinatePair::getC1).thenComparing(CoordinatePair::getC2);
        var iterator = lines.stream().sorted(comparator).iterator();
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
        var c1 = pair.getC1();
        var c2 = pair.getC2();
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
    private static Set<Coordinate> findNonMergeableCoordinates(Collection<CoordinatePair> pairList) {
        // Extract all the coordinates
        var allCoordinates = new ArrayList<Coordinate>(pairList.size()*2);
        for (var p : pairList) {
            allCoordinates.add(p.getC1());
            allCoordinates.add(p.getC2());
        }

        // Sort the list so that we can count occurrences in a single pass
        allCoordinates.sort(null);

        // Find which that can't be merged, i.e. they don't occur exactly twice
        var nonMergable = new HashSet<Coordinate>();
        int count = 0;
        Coordinate currentCoord = null;
        for (var c : allCoordinates) {
            if (Objects.equals(currentCoord, c)) {
                count++;
            } else {
                if (count != 2 && currentCoord != null)
                    nonMergable.add(currentCoord);
                currentCoord = c;
                count = 1;
            }
        }
        return nonMergable;
    }

    /**
     * Faster alternative to {@link #linesFromPairsLegacy(GeometryFactory, Collection, double, double, double)}
     * This takes a collection of {@link CoordinatePair} which should represent <i>all</i> the lines generated by
     * coordinate tracing (as one-pixel horizontal or vertical segments).
     * It then builds line strings from these pairs, merging them where possible, before passing the results to
     * a {@link org.locationtech.jts.operation.polygonize.Polygonizer} to build the final geometry.
     */
    static Geometry linesFromPairsFast(GeometryFactory factory, Collection<CoordinatePair> pairs,
                                            double xOrigin, double yOrigin, double scale) {

        // Sort now to avoid sorting later
        var pairList = pairs.stream()
                .sorted(Comparator.comparing(CoordinatePair::getC1).thenComparing(CoordinatePair::getC2))
                .toList();

        var nonMergeable = findNonMergeableCoordinates(pairs);

        // Find horizontal & vertical edges, split by row and column
        var horizontal = new TreeMap<Double, List<CoordinatePair>>();
        var vertical = new TreeMap<Double, List<CoordinatePair>>();
        for (var p : pairList) {
            if (p.isHorizontal())
                horizontal.computeIfAbsent(p.getC1().getY(), y -> new ArrayList<>()).add(p);
            else if (p.isVertical())
                vertical.computeIfAbsent(p.getC1().getX(), x -> new ArrayList<>()).add(p);
        }

        Collection<List<Coordinate>> lines = new ArrayList<>();
        var mergeable = new MinimalCoordinateMap<List<Coordinate>>();
        for (var entry : horizontal.entrySet()) {
            var list = entry.getValue();
            for (var ls : buildLineStrings(list, nonMergeable::contains, factory, xOrigin, yOrigin, scale)) {
                var c1 = ls.getFirst();
                var c2 = ls.getLast();
                boolean isMergable = false;
                if (!nonMergeable.contains(c1)) {
                    if (mergeable.put(c1, ls) != null)
                        throw new RuntimeException("Horizontal mergeable already exists");
                    isMergable = true;
                }
                if (!nonMergeable.contains(c2)) {
                    if (mergeable.put(c2, ls) != null)
                        throw new RuntimeException("Vertical mergeable already exists");
                    isMergable = true;
                }
                if (!isMergable)
                    lines.add(ls);
            }
        }
        for (var entry : vertical.entrySet()) {
            var list = entry.getValue();
            var queued = new ArrayDeque<>(buildLineStrings(list, nonMergeable::contains, factory, xOrigin, yOrigin, scale));
            while (!queued.isEmpty()) {
                var ls = queued.pop();
                if (isClosed(ls)) {
                    lines.add(ls);
                    continue;
                }
                if (Thread.interrupted())
                    throw new RuntimeException("Interrupted");
                var c1 = ls.getFirst();
                var c2 = ls.getLast();
                boolean c1Mergable = !nonMergeable.contains(c1);
                boolean c2Mergable = !nonMergeable.contains(c2);
                if (c1Mergable) {
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
                if (c2Mergable) {
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
                if (c1Mergable || c2Mergable) {
                    if (c1Mergable)
                        mergeable.put(c1, ls);
                    if (c2Mergable)
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
        for (var line : lines) {
            lineStrings.add(factory.createLineString(line.toArray(Coordinate[]::new)));
        }

        return factory.buildGeometry(lineStrings);
    }

    private static boolean isClosed(List<Coordinate> coords) {
        return coords.size() > 2 && coords.getFirst().equals(coords.getLast());
    }

    private static List<Coordinate> mergeLines(List<Coordinate> l1, List<Coordinate> l2) {
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
            return null;
        }
    }

    private static List<Coordinate> concat(List<Coordinate> l1, List<Coordinate> l2) {
        int n = l1.size() + l2.size() - 1;
        var list = new ArrayList<Coordinate>(n);
        list.addAll(l1);
        list.addAll(l2.subList(1, l2.size()));
        return list;
    }


    private static List<List<Coordinate>> buildLineStrings(List<CoordinatePair> pairs, Predicate<Coordinate> counter,
                                                           GeometryFactory factory, double xOrigin, double yOrigin, double scale) {
        if (pairs.isEmpty())
            return List.of();

        List<List<Coordinate>> lines = new ArrayList<>();
        Coordinate firstCoord = pairs.getFirst().getC1();
        Coordinate secondCoord = pairs.getFirst().getC2();
        for (int i = 1; i < pairs.size(); i++) {
            var p = pairs.get(i);
            if (!secondCoord.equals(p.getC1()) || counter.test(secondCoord)) {
                // Finish the line we were building & start a new one
                lines.add(createLineString(firstCoord, secondCoord, factory, xOrigin, yOrigin, scale));
                firstCoord = p.getC1();
                secondCoord = p.getC2();
                continue;
            } else {
                // Continue the line
                secondCoord = p.getC2();
            }
        }
        lines.add(createLineString(firstCoord, secondCoord, factory, xOrigin, yOrigin, scale));
        return lines;
    }

    private static List<Coordinate> createLineString(Coordinate c1, Coordinate c2,
                                                     GeometryFactory factory, double xOrigin, double yOrigin, double scale) {
        if (xOrigin == 0 && yOrigin == 0 && scale == 1)
            return List.of(c1, c2);
        var pm = factory.getPrecisionModel();
        double x1 = pm.makePrecise(xOrigin + c1.x * scale);
        double x2 =  pm.makePrecise(xOrigin + c2.x * scale);
        double y1 =  pm.makePrecise(yOrigin + c1.y * scale);
        double y2 =  pm.makePrecise(yOrigin + c2.y * scale);
        return List.of(new Coordinate(x1, y1), new Coordinate(x2, y2));
    }


    /**
     * A minimal map-like class that can use coordinates as keys.
     * I know this looks awkward... but it can perform much better than a single HashMap<Coordinate, T>.
     * @param <T>
     */
    private static class MinimalCoordinateMap<T> {

        private final Map<Double, Map<Double, T>> map = new HashMap<>();

        T put(Coordinate c, T val) {
            return map.computeIfAbsent(c.getX(), x -> new HashMap<>()).put(c.getY(), val);
        }

        T remove(Coordinate c) {
            return map.computeIfAbsent(c.getX(), x -> new HashMap<>()).remove(c.getY());
        }

        T get(Coordinate c) {
            return getOrDefault(c, null);
        }

        T getOrDefault(Coordinate c, T defaultValue) {
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

}
