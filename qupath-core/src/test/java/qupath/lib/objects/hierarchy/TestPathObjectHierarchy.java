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
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.opentest4j.AssertionFailedError;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
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

    @Test
    void Check_Resolve_Hierarchy_Of_Touching_Rectangles() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject leftRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 100, 100));
        PathObject rightRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(100, 0, 100, 100));
        hierarchy.addObjects(List.of(leftRectangle, rightRectangle));
        List<PathObject> expectedRootChildren = List.of(leftRectangle, rightRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Top_Touching() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject expectedParent = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject expectedChild = PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 0, 180, 50));
        hierarchy.addObjects(List.of(expectedParent, expectedChild));
        List<PathObject> expectedRootChildren = List.of(expectedParent);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Bottom_Touching() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject expectedParent = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject expectedChild = PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 50, 180, 50));
        hierarchy.addObjects(List.of(expectedParent, expectedChild));
        List<PathObject> expectedRootChildren = List.of(expectedParent);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Left_Touching() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject expectedParent = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject expectedChild = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 25, 100, 50));
        hierarchy.addObjects(List.of(expectedParent, expectedChild));
        List<PathObject> expectedRootChildren = List.of(expectedParent);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Right_Touching() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject expectedParent = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject expectedChild = PathObjects.createAnnotationObject(ROIs.createRectangleROI(100, 25, 100, 50));
        hierarchy.addObjects(List.of(expectedParent, expectedChild));
        List<PathObject> expectedRootChildren = List.of(expectedParent);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject expectedParent = PathObjects.createAnnotationObject(RoiTools.buffer(
                ROIs.createRectangleROI(0, 0, 200, 100),
                50
        ));
        PathObject expectedChild = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        hierarchy.addObjects(List.of(expectedParent, expectedChild));
        List<PathObject> expectedRootChildren = List.of(expectedParent);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_With_Hole_Within_Another_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject expectedParent = PathObjects.createAnnotationObject(RoiTools.buffer(
                ROIs.createRectangleROI(0, 0, 200, 100),
                50
        ));
        PathObject expectedChild = PathObjects.createAnnotationObject(RoiTools.subtract(
                RoiTools.buffer(ROIs.createRectangleROI(0, 0, 200, 100), 50),
                ROIs.createRectangleROI(0, 0, 200, 100)
        ));
        hierarchy.addObjects(List.of(expectedParent, expectedChild));
        List<PathObject> expectedRootChildren = List.of(expectedParent);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Hole_Smaller_Than_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject biggerRectangle = PathObjects.createAnnotationObject(RoiTools.subtract(
                RoiTools.buffer(ROIs.createRectangleROI(0, 0, 200, 100), 50),
                ROIs.createRectangleROI(0, 0, 200, 100)
        ));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(RoiTools.buffer(
                ROIs.createRectangleROI(0, 0, 200, 100),
                5
        ));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle, smallerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Hole_Bigger_Than_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject biggerRectangle = PathObjects.createAnnotationObject(RoiTools.subtract(
                RoiTools.buffer(ROIs.createRectangleROI(0, 0, 200, 100), 50),
                ROIs.createRectangleROI(0, 0, 200, 100)
        ));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(RoiTools.buffer(
                ROIs.createRectangleROI(0, 0, 200, 100),
                -5
        ));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle, smallerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_Shifted_By_One_Pixel() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        double shift = 1;
        PathObject biggerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(100 + shift, 0, 100, 100));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle, smallerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_Shifted_By_Less_Than_Precision() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        double shift = 0.5 / GeometryTools.getDefaultFactory().getPrecisionModel().getScale();
        PathObject biggerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(100 + shift, 0, 100, 100));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_Shifted_By_Precision() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        double shift = 1.0 / GeometryTools.getDefaultFactory().getPrecisionModel().getScale();
        PathObject biggerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(100 + shift, 0, 100, 100));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_Shifted_By_More_Than_Precision() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        double shift = 1.5 / GeometryTools.getDefaultFactory().getPrecisionModel().getScale();
        PathObject biggerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(100 + shift, 0, 100, 100));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle, smallerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Rectangle_Within_Another_Rectangle_With_Line_Sticking_Out() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject biggerRectangle = PathObjects.createAnnotationObject(RoiTools.subtract(
                ROIs.createRectangleROI(0, 0, 200, 100),
                ROIs.createRectangleROI(10, 0, 180, 40)
        ));
        PathObject smallerRectangle = PathObjects.createAnnotationObject(GeometryTools.geometryToROI(
                ROIs.createRectangleROI(50, 40, 100, 40).getGeometry()
                        .union(ROIs.createLineROI(100, 20, 100, 80).getGeometry().buffer(0.01))
        ));
        hierarchy.addObjects(List.of(biggerRectangle, smallerRectangle));
        List<PathObject> expectedRootChildren = List.of(biggerRectangle, smallerRectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Line_Within_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject line = PathObjects.createAnnotationObject(ROIs.createLineROI(50, 50, 150, 50));
        hierarchy.addObjects(List.of(rectangle, line));
        List<PathObject> expectedRootChildren = List.of(rectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Boundary_Line_Of_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject boundaryLine = PathObjects.createAnnotationObject(GeometryTools.geometryToROI(
                ((Polygon) ROIs.createRectangleROI(0, 0, 200, 100).getGeometry()).getExteriorRing()
        ));
        hierarchy.addObjects(List.of(rectangle, boundaryLine));
        List<PathObject> expectedRootChildren = List.of(rectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Polyline_Within_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject rectangle = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 200, 100));
        PathObject polyline = PathObjects.createAnnotationObject(GeometryTools.geometryToROI(
                ((Polygon) ROIs.createRectangleROI(10, 10, 180, 80).getGeometry()).getExteriorRing()
        ));
        hierarchy.addObjects(List.of(rectangle, polyline));
        List<PathObject> expectedRootChildren = List.of(rectangle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Polyline_Extending_Outside_C_Shaped_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject rectangle = PathObjects.createAnnotationObject(RoiTools.subtract(
                ROIs.createRectangleROI(0, 0, 200, 100),
                ROIs.createRectangleROI(50, 25, 200, 50)
        ));
        PathObject polyline = PathObjects.createAnnotationObject(GeometryTools.geometryToROI(
                ((Polygon) ROIs.createRectangleROI(10, 10, 180, 80).getGeometry()).getExteriorRing()
        ));
        hierarchy.addObjects(List.of(rectangle, polyline));
        List<PathObject> expectedRootChildren = List.of(rectangle, polyline);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Ring_Extending_Outside_C_Shaped_Rectangle() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        PathObject rectangle = PathObjects.createAnnotationObject(RoiTools.subtract(
                ROIs.createRectangleROI(0, 0, 200, 100),
                ROIs.createRectangleROI(50, 25, 200, 50)
        ));
        PathObject ring = PathObjects.createAnnotationObject(GeometryTools.geometryToROI(
                ((Polygon) ROIs.createRectangleROI(10, 10, 180, 80).getGeometry()).getExteriorRing()
                        .buffer(2, 0, BufferParameters.CAP_SQUARE)
        ));
        hierarchy.addObjects(List.of(rectangle, ring));
        List<PathObject> expectedRootChildren = List.of(rectangle, ring);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Touching_Circles() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        // We get more problems with non-integer dimensions
        PathObject leftCircle = PathObjects.createAnnotationObject(createEllipsePolygon(0, 0, 100.5, 100.5));
        PathObject rightCircle = PathObjects.createAnnotationObject(createEllipsePolygon(100.5, 0, 100.5, 100.5));
        hierarchy.addObjects(List.of(leftCircle, rightCircle));
        List<PathObject> expectedRootChildren = List.of(leftCircle, rightCircle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Intersecting_Circles_With_Big_Shift() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        // We get more problems with non-integer dimensions
        double shift = 100;
        PathObject biggerCircle = PathObjects.createAnnotationObject(createEllipsePolygon(0, 0, 200.5, 100.5));
        PathObject smallerCircle = PathObjects.createAnnotationObject(RoiTools.intersection(
                createEllipsePolygon(0, 0, 200.5, 100.5),
                createEllipsePolygon(200.5 - shift, 0, 200.5, 100.5)
        ));
        hierarchy.addObjects(List.of(biggerCircle, smallerCircle));
        List<PathObject> expectedRootChildren = List.of(biggerCircle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    @Test
    void Check_Resolve_Hierarchy_Of_Intersecting_Circles_With_Small_Shift() {
        PathObjectHierarchy hierarchy = new PathObjectHierarchy();
        // We get more problems with non-integer dimensions
        double shift = 0.001;
        PathObject biggerCircle = PathObjects.createAnnotationObject(createEllipsePolygon(0, 0, 200.5, 100.5));
        PathObject smallerCircle = PathObjects.createAnnotationObject(RoiTools.intersection(
                createEllipsePolygon(0, 0, 200.5, 100.5),
                createEllipsePolygon(200.5 - shift, 0, 200.5, 100.5)
        ));
        hierarchy.addObjects(List.of(biggerCircle, smallerCircle));
        List<PathObject> expectedRootChildren = List.of(biggerCircle);

        hierarchy.resolveHierarchy();

        assertCollectionsEqualsWithoutOrder(expectedRootChildren, hierarchy.getRootObject().getChildObjects());
    }

    private static <T> void assertCollectionsEqualsWithoutOrder(Collection<? extends T> expectedCollection, Collection<? extends T> actualCollection) {
        if (expectedCollection.size() != actualCollection.size()) {
            throw new AssertionFailedError(String.format(
                    "Expected collection size: %d but was: %d",
                    expectedCollection.size(),
                    actualCollection.size())
            );
        }

        if (!expectedCollection.containsAll(actualCollection) || !actualCollection.containsAll(expectedCollection)) {
            throw new AssertionFailedError(String.format(
                    "Expected collection: %s but was: %s",
                    expectedCollection,
                    actualCollection
            ));
        }
    }

    private static ROI createEllipsePolygon(double x, double y, double width, double height) {
        // used rather than EllipseROI to control the number of points used to represent the ellipse
        var shapeFactory = new GeometricShapeFactory(GeometryTools.getDefaultFactory());
        shapeFactory.setEnvelope(new Envelope(x, x+width, y, y+height));
        shapeFactory.setNumPoints(1000);
        return GeometryTools.geometryToROI(shapeFactory.createEllipse());
    }
}
