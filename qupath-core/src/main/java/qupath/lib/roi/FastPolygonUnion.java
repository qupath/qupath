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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        var groups = findConnectedGroups(allPolygons);

        List<Geometry> toKeep = new ArrayList<>();
        List<List<Polygon>> toMerge = new ArrayList<>();
        var distinct = groups.values().stream().map(GroupedGeometry::getOrCreateGroup).distinct().toList();
        for (var gg : distinct) {
            assert !gg.isEmpty();
            if (gg.size() == 1) {
                toKeep.add(gg.iterator().next().geometry);
            } else {
                toMerge.add(gg.stream().map(g -> g.geometry).toList());
            }
        }

        toKeep.addAll(toMerge.parallelStream()
                .map(FastPolygonUnion::unionOpNg)
                .toList());

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
            return list.get(0);
        else
            return GeometryTools.getDefaultFactory().createMultiPolygon(list.toArray(Polygon[]::new));
    }

    private static Map<Polygon, GroupedGeometry<Polygon>> findConnectedGroups(List<Polygon> allPolygons) {
        Map<Polygon, GroupedGeometry<Polygon>> groups = new LinkedHashMap<>();

        for (int i = 0; i < allPolygons.size(); i++) {
            var poly = allPolygons.get(i);
            int hashCode = Integer.hashCode(i);
            GroupedGeometry<Polygon> currentGroup = groups.computeIfAbsent(poly, p -> new GroupedGeometry<>(p, hashCode));
            for (int j = i+1; j < allPolygons.size(); j++) {
                var poly2 = allPolygons.get(j);
                // Check if polygons intersect
                if (poly.intersects(poly2)) {
                    int otherHashCode = Integer.hashCode(j);
                    var otherGroup = groups.computeIfAbsent(poly2, p -> new GroupedGeometry<>(p, otherHashCode));
                    currentGroup.merge(otherGroup);
                }
            }
        }

        return groups;
    }


    static class GroupedGeometry<T> {

        private final T geometry;
        private Set<GroupedGeometry<T>> group;
        private final int hashCode;

        GroupedGeometry(T geometry, int hashCode) {
            this.geometry = geometry;
            this.hashCode = hashCode;
        }

        private Set<GroupedGeometry<T>> getOrCreateGroup() {
            if (group == null) {
                this.group = new HashSet<>();
                this.group.add(this);
            }
            return group;
        }

        void merge(GroupedGeometry<T> other) {
            var group = getOrCreateGroup();
            if (group == other.group) {
                return;
            } else if (other.group == null) {
                group.add(other);
                other.group = this.group;
            } else {
                var otherGroup = other.group;
                this.group.addAll(otherGroup);
                for (var otherTemp : otherGroup) {
                    otherTemp.group = this.group;
                }
            }
        }

        boolean isGrouped() {
            return group != null && group.size() > 1;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

    }

}
