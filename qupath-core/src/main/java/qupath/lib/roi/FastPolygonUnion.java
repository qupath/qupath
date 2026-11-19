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
import org.locationtech.jts.index.hprtree.HPRtree;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

        // Group as needed
        var toKeep = new LinkedHashSet<Geometry>(allPolygons);
        toKeep.removeAll(groups.alreadyGrouped);

        System.err.println("------");
        System.err.println("Total: " + geoms.size());
        System.err.println("Groups: " + groups.groups.size());
        System.err.println("Already grouped: " + groups.alreadyGrouped.size());
        int n = 0;
        for (var g : groups.groups) {
            n += g.size();
        }
        System.err.println("Group sum: " + n);
        System.err.println("Isolated: " + toKeep.size());
        System.err.println("------");

        toKeep.addAll(groups.groups.parallelStream()
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
    private static Geometry unionOpNg(Collection<Geometry> geoms) {
        var factory = GeometryTools.getDefaultFactory();

        if (geoms.isEmpty())
            return factory.createPolygon();
        else if (geoms.size() == 1)
            return geoms.iterator().next();

        try {
            return UnaryUnionNG.union(geoms, factory, factory.getPrecisionModel());
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

    private static ConnectedGroups findConnectedGroups(List<Polygon> allPolygons) {
        ConnectedGroups groups = new ConnectedGroups();
        var tree = new HPRtree();
        for (var poly : allPolygons) {
            tree.insert(poly.getEnvelopeInternal(), poly);
        }
        Set<Polygon> visited = new HashSet<>();
        for (var poly : allPolygons) {
            visited.add(poly);
            var env = poly.getEnvelopeInternal();
            for (var poly2 : (List<Polygon>)tree.query(env)) {
                // Matrix is symmetric, so only test where needed
                if (poly == poly2 || visited.contains(poly2)) {
                    continue;
                }
                // Check if polygons intersect
                if (poly.intersects(poly2)) {
                    groups.groupTogether(poly, poly2);
                }
            }
        }
        return groups;
    }


    private static class ConnectedGroups {

        private final Set<Set<Geometry>> groups = new LinkedHashSet<>();
        private final Set<Geometry> alreadyGrouped = new HashSet<>();

        synchronized void groupTogether(Geometry geom1, Geometry geom2) {
            if (!alreadyGrouped.add(geom1)) {
                // geom1 is in a group already
                var group1 = findGroup(geom1);
                if (!group1.contains(geom2)) {
                    if (alreadyGrouped.add(geom2)) {
                        // geom2 wasn't grouped, so add it now
                        group1.add(geom2);
                    } else {
                        // geom2 was in a different group, so merge the groups
                        var group2 = findGroup(geom2);
                        if (!group1.equals(group2)) {
                            group1.addAll(group2);
                        }
                    }
                }
                // Make sure we retain the first group
                groups.add(group1);
            } else if (alreadyGrouped.add(geom2)) {
                // Neither geom1 nor geom2 are in a group, so create a new group
                var newGroup = new HashSet<Geometry>();
                newGroup.add(geom1);
                newGroup.add(geom2);
                groups.add(newGroup);
            } else {
                // Only geom2 is in a group, so add geom1 and retain group2
                var group2 = findGroup(geom2);
                group2.add(geom1);
                groups.add(group2);
            }
        }

        private Set<Geometry> findGroup(Geometry geom) {
            var iterator = groups.iterator();
            while (iterator.hasNext()) {
                var group = iterator.next();
                if (group.contains(geom)) {
                    iterator.remove();
                    return group;
                }
            }

//            for (Set<Geometry> group : groups) {
//                if (group.contains(geom)) {
//                    return group;
//                }
//            }
            throw new IllegalArgumentException("No group found for geom: " + geom);
        }

    }

}
