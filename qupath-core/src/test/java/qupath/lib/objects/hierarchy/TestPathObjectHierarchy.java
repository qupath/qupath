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

package qupath.lib.objects.hierarchy;

import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPathObjectHierarchy {

    @Test
    public void testGetObjects() {

        // Define some boundaries to create objects
        var region1 = ImageRegion.createInstance(0, 0, 10, 20, 0, 0);
        var region2 = ImageRegion.createInstance(1000, 1000, 20, 10, 0, 0);

        var regions = List.of(region1, region2);

        var hierarchy = new PathObjectHierarchy();
        var cellRectangles = createObjects(regions, ROIs::createRectangleROI, r -> PathObjects.createCellObject(r, null));
        var cellEllipses = createObjects(regions, ROIs::createEllipseROI, r -> PathObjects.createCellObject(r, null));

        var tileRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createTileObject);
        var tileEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createTileObject);

        var detectionRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createDetectionObject);
        var detectionEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createDetectionObject);

        var annotationRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createAnnotationObject);
        var annotationEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createAnnotationObject);

        var defaultPlaneObjects = Stream.of(
                cellEllipses,
                cellRectangles,
                tileEllipses,
                tileRectangles,
                detectionEllipses,
                detectionRectangles,
                annotationEllipses,
                annotationRectangles
        ).flatMap(List::stream).toList();
        var z1Objects = defaultPlaneObjects.stream().map(p -> updateZ(p, 1)).toList();
        var t1Objects = defaultPlaneObjects.stream().map(p -> updateT(p, 1)).toList();

        hierarchy.addObjects(defaultPlaneObjects);
        hierarchy.addObjects(z1Objects);
        hierarchy.addObjects(t1Objects);

        var region1Smaller = ImageRegion.createInstance(region1.getX(), region1.getY(), region1.getWidth() - 1, region1.getHeight() - 1, region1.getZ(), region1.getT());

        // ANNOTATIONS

        // Check we get rectangles and ellipses for the correct regions
        assertTrue(hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isAnnotation));
        assertFalse(hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isDetection));
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1)).size());
        assertEquals(1, hierarchy.getAnnotationsForROI(ROIs.createEllipseROI(region1)).size());
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region2)).size());
        assertEquals(1, hierarchy.getAnnotationsForROI(ROIs.createEllipseROI(region2)).size());

        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(0, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1Smaller)).size());

        // Check behavior when z and t changes
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
        assertEquals(0, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
        assertEquals(0, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());

        // CELLS

        // Check we get rectangles and ellipses for the correct regions
        // Here, we expect both ellipses and rectangles when we use an ellipse - because of the 'centroid' rule for detections
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1)).size());
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createEllipseROI(region1)).size());
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region2)).size());
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createEllipseROI(region2)).size());

        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1Smaller)).size());

        // Check behavior when z and t changes
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
        assertEquals(0, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
        assertEquals(0, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());

        // Check type
        assertTrue(hierarchy.getCellsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isCell));
        assertTrue(hierarchy.getCellsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isDetection));
        assertFalse(hierarchy.getCellsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isAnnotation));
        assertFalse(hierarchy.getCellsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isTile));

        // TILES

        // Check we get rectangles and ellipses for the correct regions
        // Here, we expect both ellipses and rectangles when we use an ellipse - because of the 'centroid' rule for detections
        assertTrue(hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isTile));
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).size());
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createEllipseROI(region1)).size());
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region2)).size());
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createEllipseROI(region2)).size());

        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1Smaller)).size());

        // Check behavior when z and t changes
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
        assertEquals(0, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
        assertEquals(0, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());

        // Check type
        assertFalse(hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isCell));
        assertTrue(hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isDetection));
        assertFalse(hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isAnnotation));
        assertTrue(hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isTile));


        // ALL DETECTIONS

        // Check we get rectangles and ellipses for the correct regions
        // Here, we expect both ellipses and rectangles when we use an ellipse - because of the 'centroid' rule for detections
        // We also expect to receive all detections, regardless of type (i.e. including detections, cells and tiles)
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1)).size());
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createEllipseROI(region1)).size());
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region2)).size());
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createEllipseROI(region2)).size());

        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1Smaller)).size());

        // Check behavior when z and t changes
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
        assertEquals(0, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
        assertEquals(0, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());

        // Check type
        assertFalse(hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isCell));
        assertTrue(hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isDetection));
        assertFalse(hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isAnnotation));
        assertFalse(hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isTile));


        // Get for region
        assertTrue(hierarchy.getAnnotationsForRegion(region1, null).stream().allMatch(PathObject::isAnnotation));
        assertFalse(hierarchy.getAnnotationsForRegion(region1, null).stream().allMatch(PathObject::isDetection));
        assertEquals(2, hierarchy.getAnnotationsForRegion(region1, null).size());
        assertEquals(2, hierarchy.getAnnotationsForRegion(region1Smaller, null).size());

    }

    private static List<PathObject> createObjects(Collection<? extends ImageRegion> regions, Function<ImageRegion, ROI> roiCreator, Function<ROI, PathObject> objectCreator) {
        return regions.stream().map(r -> objectCreator.apply(roiCreator.apply(r))).toList();
    }

    private static PathObject updateZ(PathObject pathObject, int z) {
        return PathObjectTools.updatePlane(
                pathObject,
                ImagePlane.getPlane(z, pathObject.getROI().getT()),
                false, true);
    }

    private static PathObject updateT(PathObject pathObject, int t) {
        return PathObjectTools.updatePlane(
                pathObject,
                ImagePlane.getPlane(pathObject.getROI().getZ(), t),
                false, true);
    }


    @Test
    public void testGetPoints() {
        var points = ROIs.createPointsROI(1, 2, ImagePlane.getDefaultPlane());
        var points2 = ROIs.createPointsROI(new double[]{1, 2}, new double[]{3, 4}, ImagePlane.getDefaultPlane());
        var rect = ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane());

        var annotations = List.of(points, points2, rect).stream().map(PathObjects::createAnnotationObject).toList();
        var detections = List.of(points, points2, rect).stream().map(PathObjects::createDetectionObject).toList();
        var hierarchy = new PathObjectHierarchy();
        hierarchy.addObjects(annotations);
        hierarchy.addObjects(detections);

        assertEquals(7, hierarchy.getAllObjects(true).size());
        assertEquals(6, hierarchy.getAllObjects(false).size());
        assertEquals(4, hierarchy.getAllPointObjects().size());
        assertEquals(2, hierarchy.getAllPointAnnotations().size());
        assertTrue(hierarchy.getAllPointAnnotations().stream().allMatch(PathObject::isAnnotation));
    }

    @Test
    public void test_neighbors() {
        var hierarchy = new PathObjectHierarchy();
        var plane = ImagePlane.getDefaultPlane();
        assertTrue(hierarchy.getDetectionSubdivision(plane).isEmpty());

        var topLeft = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 1, 1, plane));
        var topRight = PathObjects.createDetectionObject(ROIs.createRectangleROI(10, 0, 1, 1, plane));
        var bottomLeft = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 10, 1, 1, plane));
        var bottomRight = PathObjects.createDetectionObject(ROIs.createRectangleROI(10, 10, 1, 1, plane));

        // Add 4 corner objects
        hierarchy.addObjects(List.of(topLeft, topRight, bottomLeft, bottomRight));

        // For a square, it's not obvious *which* two triangles will be created by the subdivision -
        // so we only require that two triangles *are* created
        if (hierarchy.findAllNeighbors(topLeft).size() == 2) {
            assertEquals(3, hierarchy.findAllNeighbors(topRight).size());
            assertEquals(3, hierarchy.findAllNeighbors(bottomLeft).size());
            assertEquals(2, hierarchy.findAllNeighbors(bottomRight).size());
        } else {
            assertEquals(3, hierarchy.findAllNeighbors(topLeft).size());
            assertEquals(2, hierarchy.findAllNeighbors(topRight).size());
            assertEquals(2, hierarchy.findAllNeighbors(bottomLeft).size());
            assertEquals(3, hierarchy.findAllNeighbors(bottomRight).size());
        }

        // Add center object - so requests to the subdivision should give updated results
        var center = PathObjects.createDetectionObject(ROIs.createRectangleROI(5, 5, 1, 1, plane));
        hierarchy.addObject(center);
        assertEquals(5, hierarchy.getDetectionSubdivision(plane).size());

        // Other planes should have empty subdivisions
        assertTrue(hierarchy.getDetectionSubdivision(ImagePlane.getPlane(1, 0)).isEmpty());
        assertTrue(hierarchy.getDetectionSubdivision(ImagePlane.getPlane(0, 1)).isEmpty());

        // Other object types should have empty subdivisions
        assertTrue(hierarchy.getCellSubdivision(plane).isEmpty());

        // Corners have center as nearest neighbor
        assertEquals(center, hierarchy.findNearestNeighbor(topLeft));
        assertEquals(center, hierarchy.findNearestNeighbor(topRight));
        assertEquals(center, hierarchy.findNearestNeighbor(bottomLeft));
        assertEquals(center, hierarchy.findNearestNeighbor(bottomRight));

        // Corners have 3 neighbors
        assertEquals(Set.of(topRight, center, bottomLeft), Set.copyOf(hierarchy.findAllNeighbors(topLeft)));
        assertEquals(Set.of(topRight, center, bottomLeft), Set.copyOf(hierarchy.findAllNeighbors(bottomRight)));

        // Center has all neighbors
        assertEquals(Set.of(topLeft, topRight, bottomLeft, bottomRight), Set.copyOf(hierarchy.findAllNeighbors(center)));

        assertTrue(hierarchy.getCellSubdivision(plane).isEmpty());
    }

}
