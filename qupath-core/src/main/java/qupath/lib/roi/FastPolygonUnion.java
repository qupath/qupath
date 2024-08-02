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

package qupath.lib.roi;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.PolygonExtracter;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.hprtree.HPRtree;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

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
 *
 * @author Pete Bankhead
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

        // Create a tree of envelopes and polygon indices
        var tree = new HPRtree();
        int n = allPolygons.size();
        for (int i = 0; i < n; i++) {
            tree.insert(allPolygons.get(i).getEnvelopeInternal(), i);
        }

        // Check for adjacent objects, restricting search using the tree
        var matrix = new AdjacencyMatrix(n);
        IntStream.range(0, n)
                .parallel()
                .forEach(i -> populateAdjacencyMatrix(allPolygons, matrix, tree, i));

        // Gather all the polygons that should be merged
        var groupsToMerge = new ArrayList<List<Geometry>>();
        var toKeep = new ArrayList<Geometry>();
        var visited = new HashSet<Integer>();
        for (int i = 0; i < n; i++) {
            if (matrix.getAdjacentCount(i) == 0) {
                // Nothing to merge, keep unchanged
                toKeep.add(allPolygons.get(i));
                visited.add(i);
            } else if (visited.contains(i)) {
                // Already in a group, skip
                continue;
            } else {
                // Generate a new group for merging
                List<Geometry> temp = new ArrayList<>();
                for (Integer ind : matrix.getAdjacencyGroup(i)) {
                    temp.add(allPolygons.get(ind));
                    if (!visited.add(ind))
                        logger.warn("Polygon added more than once!");
                }
                groupsToMerge.add(temp);
            }
        }
        logger.debug("Number of polygon collections to merge: {}", groupsToMerge.size());

        toKeep.addAll(groupsToMerge.parallelStream()
                .map(list -> unionOpNg(list))
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

    private static void populateAdjacencyMatrix(List<Polygon> allPolygons, AdjacencyMatrix matrix, SpatialIndex tree, int ind) {
        var poly = allPolygons.get(ind);
        for (int ind2 : (List<Integer>)tree.query(poly.getEnvelopeInternal())) {
            // Matrix is symmetric, so only test where needed
            if (ind2 <= ind || matrix.isAdjacent(ind, ind2))
                continue;
            // Check if polygons intersect
            var poly2 = allPolygons.get(ind2);
            if (poly.intersects(poly2)) {
                matrix.setAdjacent(ind, ind2);
            }
        }
    }

    /**
     * Simple adjacency matrix to help identify polygons that should be merged.
     */
    private static class AdjacencyMatrix {

        private List<BitSet> bits = new ArrayList<>();

        private AdjacencyMatrix(int n) {
            for (int i = 0; i < n; i++) {
                bits.add(new BitSet(n));
            }
        }

        /**
         * Flag that two entries are adjacent.
         * @param i
         * @param j
         * @return true if a change was made, false if the entries were already
         *         flagged as adjacent.
         */
        public boolean setAdjacent(int i, int j) {
            if (bits.get(i).get(j))
                return false;
            bits.get(i).set(j);
            bits.get(j).set(i);
            return true;
        }

        /**
         * Get indices for all entries that are directly or indirectly adjacent,
         * i.e. polygons that may need to be merged.
         * @param i
         * @return
         */
        public Set<Integer> getAdjacencyGroup(int i) {
            var set = new HashSet<Integer>();
            accumulateAdjacencyGroup(i, set);
            return set;
        }

        /**
         * Ensure an item is part of a group; if it has not already been added,
         * any additional adjacent items will be added as well.
         * @param i
         * @param group
         */
        private void accumulateAdjacencyGroup(int i, Set<Integer> group) {
            if (group.add(i)) {
                for (int j : bits.get(i).stream().toArray()) {
                    accumulateAdjacencyGroup(j, group);
                }
            }
        }

        /**
         * Get the number of directly-adjacent items for the given item index.
         * @param i
         * @return
         */
        public int getAdjacentCount(int i) {
            return bits.get(i).cardinality();
        }

        /**
         * Query whether two items are flagged as adjacent.
         * @param i
         * @param j
         * @return
         */
        public boolean isAdjacent(int i, int j) {
            return bits.get(i).get(j);
        }

    }

}
