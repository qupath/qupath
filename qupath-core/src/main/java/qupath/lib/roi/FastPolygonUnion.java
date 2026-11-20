/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024-2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.roi;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.PolygonExtracter;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.Timeit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compute a faster union of large numbers of polygons.
 * <p>
 * This is a sufficiently common requirement, especially with pixel classification,
 * to require its own method.
 * <p>
 * The algorithm is:
 * <ol>
 *     <li>
 *         Extract all polygons from the input.
 *     </li>
 *     <li>
 *         Identify intersecting and non-intersecting polygons
 *     </li>
 *     <li>
 *         Group all polygons that should potentially be merged, because they intersect
 *         (directly or indirectly) with other polygons in the group;
 *         each polygon should be represented in only one group
 *     </li>
 *     <li>
 *         Union all the polygon groups
 *     </li>
 *     <li>
 *         Combine all resulting polygons into a single polygon or multipolygon
 *     </lI>
 * </ol>
 * This partitioning of the problem makes it possible to parallelize checking for intersections
 * and computing the union of groups.
 */
public class FastPolygonUnion {

    private static final Logger logger = LoggerFactory.getLogger(FastPolygonUnion.class);

    /**
     * Compute a union of all polygons contained in an array of geometries.
     * Non-polygon geometries will be ignored.
     * @param geoms
     * @return the union of polygons, or an empty polygon if no geometries are provided
     */
    public static Geometry union(Geometry... geoms) {
        return union(Arrays.asList(geoms));
    }

    /**
     * Compute a union of all polygons contained in a collection of geometries.
     * Non-polygon geometries will be ignored.
     * @param geoms
     * @return the union of polygons, or an empty polygon if no geometries are provided
     */
    public static Geometry union(Collection<? extends Geometry> geoms) {
        logger.trace("Calling union for {} geometries", geoms.size());
        List<Polygon> allPolygons = extractAllPolygons(geoms);

        // Identify all polygons to group together
        var timeit = new Timeit();
        timeit.checkpoint("Groups");
        var groups = findConnectedGroups(allPolygons);

        List<Geometry> toKeep = new ArrayList<>();
        List<List<Polygon>> toMerge = new ArrayList<>();
        timeit.checkpoint("Distinct");
        // Get all the distinct groups
        List<Group<GroupedValue<Polygon>>> distinct = groups.stream().map(GroupedValue::getGroup).distinct().toList();
        timeit.checkpoint("Assembled");
        for (var gg : distinct) {
            assert !gg.isEmpty();
            if (gg.size() == 1) {
                toKeep.add(gg.getValue().iterator().next().getValue());
            } else {
                toMerge.add(gg.getValue().stream().map(WrappedValue::getValue).toList());
            }
        }

        timeit.checkpoint("Merged");
        toKeep.addAll(toMerge.parallelStream()
                .map(FastPolygonUnion::unionOpNg)
                .toList());

        timeit.stop();

        System.err.println(timeit);

        return createPolygonalGeometry(toKeep);
    }

    /**
     * Extract all non-empty polygons from a collection of geometries.
     * @param geoms
     * @return
     */
    private static List<Polygon> extractAllPolygons(Collection<? extends Geometry> geoms) {
        List<Polygon> allPolygons = new ArrayList<>();
        for (var g : geoms) {
            if (g != null)
                PolygonExtracter.getPolygons(g, allPolygons);
        }
        return allPolygons.stream()
                .filter(p -> p != null && !p.isEmpty())
                .toList();
    }

    /**
     * Create a standard union with JTS.
     * @param geoms
     * @return
     */
    private static Geometry unionOpNg(Collection<? extends Geometry> geoms) {
        var factory = GeometryTools.getDefaultFactory();

        if (geoms.isEmpty())
            return factory.createPolygon();
        else if (geoms.size() == 1)
            return geoms.iterator().next();

        try {
            return UnaryUnionNG.union(List.copyOf(geoms), factory, factory.getPrecisionModel());
        } catch (Exception e) {
            logger.error("Error during unary union operation for {} geometries, will attempt with buffer(0)", geoms.size(), e);
            return factory.createGeometryCollection(geoms.toArray(Geometry[]::new)).buffer(0);
        }
    }

    /**
     * Create a polygonal geometry after extracting all polygons from a list of geometries.
     * @param geoms
     * @return a Polygon or MultiPolygon (may be empty)
     */
    private static Geometry createPolygonalGeometry(Collection<? extends Geometry> geoms) {
        var list = extractAllPolygons(geoms);
        if (list.isEmpty())
            return GeometryTools.getDefaultFactory().createPolygon();
        else if (list.size() == 1)
            return list.getFirst();
        else
            return GeometryTools.getDefaultFactory().createMultiPolygon(list.toArray(Polygon[]::new));
    }

    private static List<GroupedValue<Polygon>> findConnectedGroups(List<Polygon> allPolygons) {
        List<GroupedValue<Polygon>> groups = allPolygons.stream().map(GroupedValue::new).toList();

        for (int i = 0; i < allPolygons.size(); i++) {
            GroupedValue<Polygon> currentGroup = groups.get(i);
            for (int j = i+1; j < allPolygons.size(); j++) {
                var otherGroup = groups.get(j);
                // Merge groups if polygons intersect
                if (currentGroup.getValue().intersects(otherGroup.getValue())) {
                    currentGroup.merge(otherGroup);
                }
            }
        }

        return groups;
    }

    /**
     * Wrap a value so that tests for equality only consider exact equality.
     * This is useful when using sets, but 'equals' and 'hashCode' calculations are either undesirable or too expensive.
     * @param <T> type of value to wrap
     */
    private static class WrappedValue<T> {

        private final static AtomicLong counter = new AtomicLong();

        private final T value;
        private final int hashCode;

        WrappedValue(T value) {
            this(value, Long.hashCode(counter.incrementAndGet()));
        }

        WrappedValue(T value, int hashCode) {
            this.value = value;
            this.hashCode = hashCode;
        }

        public T getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

    }

    /**
     * A wrapped value that wraps a set.
     * It would be possible to use {@code Set<Set<T>>} instead, but this can be very inefficient when sets get large.
     * Hashcode calculations defined in AbstractSet involve iterating through all items.
     * When grouping here, we don't care about regular 'set equality' (in terms of containing the same values),
     * but rather we only want to know if two objects share a reference to the exact same set object.
     * Elsewhere, we deal with ensuring that the same item can't occur in multiple groups/sets.
     * @param <T> generic type of items in the set
     */
    static class Group<T> extends WrappedValue<Set<T>> {

        Group() {
            super(new HashSet<>());
        }

        Group(T value) {
            this();
            add(value);
        }

        void add(T value) {
            getValue().add(value);
        }

        void addAll(Group<? extends T> values) {
            getValue().addAll(values.getValue());
        }

        int size() {
            return getValue().size();
        }

        boolean isEmpty() {
            return getValue().isEmpty();
        }

    }


    /**
     * A wrapped value that is part of a group, which may be merged with other groups.
     * @param <T> the type of the value
     */
    static class GroupedValue<T> extends WrappedValue<T> {

        private Group<GroupedValue<T>> group;

        GroupedValue(T geometry) {
            super(geometry);
        }

        Group<GroupedValue<T>> getGroup() {
            if (group == null) {
                this.group = new Group<>(this);
            }
            return group;
        }

        void merge(GroupedValue<T> other) {
            var group = getGroup();
            if (group == other.group) {
                // Nothing to do
                return;
            } else if (other.group == null) {
                // Assign group to other
                group.add(other);
                other.group = this.group;
            } else {
                // Merge groups and assign this group to all others
                var otherGroup = other.group;
                this.group.addAll(otherGroup);
                for (var otherTemp : otherGroup.getValue()) {
                    otherTemp.group = this.group;
                }
            }
        }


    }

}
