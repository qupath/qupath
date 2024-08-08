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

import org.junit.jupiter.api.BeforeAll;
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
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPathObjectHierarchy {

    private static final ImageRegion region1 = ImageRegion.createInstance(0, 0, 10, 20, 0, 0);
    private static final ImageRegion region2 = ImageRegion.createInstance(1000, 1000, 20, 10, 0, 0);
    private static final List<ImageRegion> regions = List.of(region1, region2);
    private static final PathObjectHierarchy hierarchy = new PathObjectHierarchy();
    private static final List<PathObject> cellRectangles = createObjects(regions, ROIs::createRectangleROI, r -> PathObjects.createCellObject(r, null));
    private static final List<PathObject> cellEllipses = createObjects(regions, ROIs::createEllipseROI, r -> PathObjects.createCellObject(r, null));
    private static final List<PathObject> tileRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createTileObject);
    private static final List<PathObject> tileEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createTileObject);
    private static final List<PathObject> detectionRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createDetectionObject);
    private static final List<PathObject> detectionEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createDetectionObject);
    private static final List<PathObject> annotationRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createAnnotationObject);
    private static final List<PathObject> annotationEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createAnnotationObject);
    private static final List<PathObject> defaultPlaneObjects = Stream.of(
            cellEllipses,
            cellRectangles,
            tileEllipses,
            tileRectangles,
            detectionEllipses,
            detectionRectangles,
            annotationEllipses,
            annotationRectangles
    ).flatMap(List::stream).toList();;
    private static final List<PathObject> z1Objects = defaultPlaneObjects.stream().map(p -> updateZ(p, 1)).toList();
    private static final List<PathObject> t1Objects = defaultPlaneObjects.stream().map(p -> updateT(p, 1)).toList();
    private static final ImageRegion region1Smaller = ImageRegion.createInstance(region1.getX(), region1.getY(), region1.getWidth() - 1, region1.getHeight() - 1, region1.getZ(), region1.getT());
    private static final ROI points = ROIs.createPointsROI(1, 2, ImagePlane.getDefaultPlane());
    private static final ROI points2 = ROIs.createPointsROI(new double[]{1, 2}, new double[]{3, 4}, ImagePlane.getDefaultPlane());
    private static final ROI rect = ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane());
    private static final PathObjectHierarchy hierarchy2 = new PathObjectHierarchy();
    private static final List<PathObject> annotations = List.of(points, points2, rect).stream().map(PathObjects::createAnnotationObject).toList();
    private static final List<PathObject> detections = List.of(points, points2, rect).stream().map(PathObjects::createDetectionObject).toList();

    @BeforeAll
    public static void init() {

        hierarchy.addObjects(defaultPlaneObjects);
        hierarchy.addObjects(z1Objects);
        hierarchy.addObjects(t1Objects);

        hierarchy2.addObjects(annotations);
        hierarchy2.addObjects(detections);

    }

    @Test
    public void test_get_annotations_for_roi_are_annotations() {
        // Check we get rectangles and ellipses for the correct regions
        assertTrue(hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isAnnotation));
    }
    @Test
    public void test_get_annotations_for_roi_arent_detections() {
        assertFalse(hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1)).stream().allMatch(PathObject::isDetection));
    }
    @Test
    public void test_get_annotations_for_rect_roi_right_size_1() {
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1)).size());
    }
    @Test
    public void test_get_annotations_for_ellipse_roi_right_size_1() {
        assertEquals(1, hierarchy.getAnnotationsForROI(ROIs.createEllipseROI(region1)).size());
    }
    @Test
    public void test_get_annotations_for_rect_roi_right_size_2() {
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region2)).size());
    }
    @Test
    public void test_get_annotations_for_ellipse_roi_right_size_2() {
        assertEquals(1, hierarchy.getAnnotationsForROI(ROIs.createEllipseROI(region2)).size());
    }
    @Test
    public void test_get_annotations_for_small_region_empty() {
        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(0, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1Smaller)).size());
    }
    @Test
    public void test_get_annotations_for_roi_with_z1_t0() {
        // Check behavior when z and t changes
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
    }
    @Test
    public void test_get_annotations_for_roi_with_z0_t1() {
        assertEquals(2, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
    }
    @Test
    public void test_get_annotations_for_roi_with_z2_t0() {
        assertEquals(0, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
    }
    @Test
    public void test_get_annotations_for_roi_with_z0_t2() {
        assertEquals(0, hierarchy.getAnnotationsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_1() {
        // Check we get rectangles and ellipses for the correct regions
        // Here, we expect both ellipses and rectangles when we use an ellipse - because of the 'centroid' rule for detections
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1)).size());
    }
    @Test
    public void test_get_cells_for_roi_ellipse_1() {
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createEllipseROI(region1)).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_2() {
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region2)).size());
    }
    @Test
    public void test_get_cells_for_roi_ellipse_2() {
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createEllipseROI(region2)).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_smaller() {
        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1Smaller)).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_z1_t0() {
        // Check behavior when z and t changes
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_z0_t1() {
        assertEquals(2, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_z2_t0() {
        assertEquals(0, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
    }
    @Test
    public void test_get_cells_for_roi_rectangle_z0_t2() {
        assertEquals(0, hierarchy.getCellsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());
    }
    @Test
    public void test_get_cells_for_roi_are_cells_and_detections() {
        var cells = hierarchy.getCellsForROI(ROIs.createRectangleROI(region1));
        assertTrue(cells.stream().allMatch(PathObject::isCell));
        assertTrue(cells.stream().allMatch(PathObject::isDetection));
    }
    @Test
    public void test_get_cells_for_roi_arent_annotations_or_tiles() {
        var cells = hierarchy.getCellsForROI(ROIs.createRectangleROI(region1));
        assertFalse(cells.stream().allMatch(PathObject::isAnnotation));
        assertFalse(cells.stream().allMatch(PathObject::isTile));
    }

    // TILES
    // Check we get rectangles and ellipses for the correct regions
    // Here, we expect both ellipses and rectangles when we use an ellipse - because of the 'centroid' rule for detections
    @Test
    public void test_get_tiles_for_rect_roi_size_1() {
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1)).size());
    }
    @Test
    public void test_get_tiles_for_ellipse_roi_size_1() {
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createEllipseROI(region1)).size());
    }
    @Test
    public void test_get_tiles_for_rect_roi_size_2() {
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region2)).size());
    }
    @Test
    public void test_get_tiles_for_ellipse_roi_size_2() {
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createEllipseROI(region2)).size());
    }
    @Test
    public void test_get_tiles_for_rect_roi_smaller() {
        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1Smaller)).size());
    }
    @Test
    public void test_get_tiles_for_rect_roi_z1_t0() {
        // Check behavior when z and t changes
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
    }
    @Test
    public void test_get_tiles_for_rect_roi_z0_t1() {
        assertEquals(2, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
    }
    @Test
    public void test_get_tiles_for_rect_roi_z2_t0() {
        assertEquals(0, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
    }
    @Test
    public void test_get_tiles_for_rect_roi_z0_t2() {
        assertEquals(0, hierarchy.getTilesForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());
    }
    @Test
    public void test_get_tiles_for_roi_arent_cells_or_detections_or_annotations() {
        // Check type
        var tiles = hierarchy.getTilesForROI(ROIs.createRectangleROI(region1));
        assertFalse(tiles.stream().allMatch(PathObject::isCell));
        assertTrue(tiles.stream().allMatch(PathObject::isDetection));
        assertFalse(tiles.stream().allMatch(PathObject::isAnnotation));
    }
    @Test
    public void test_get_tiles_for_roi_are_tiles() {
        var tiles = hierarchy.getTilesForROI(ROIs.createRectangleROI(region1));
        assertTrue(tiles.stream().allMatch(PathObject::isTile));
    }

    // ALL DETECTIONS
    // Check we get rectangles and ellipses for the correct regions
    // Here, we expect both ellipses and rectangles when we use an ellipse - because of the 'centroid' rule for detections
    // We also expect to receive all detections, regardless of type (i.e. including detections, cells and tiles)
    @Test
    public void test_get_detections_for_rect_roi_1() {
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1)).size());
    }
    @Test
    public void test_get_detections_for_ellipse_roi_1() {
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createEllipseROI(region1)).size());
    }
    @Test
    public void test_get_detections_for_rect_roi_2() {
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region2)).size());
    }
    @Test
    public void test_get_detections_for_ellipse_roi_2() {
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createEllipseROI(region2)).size());
    }
    @Test
    public void test_get_detections_smaller() {
        // Check we get no annotations for a smaller region (due to 'covers' rule)
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1Smaller)).size());
    }
    @Test
    public void test_get_detections_rectangle_z1_t0() {
        // Check behavior when z and t changes
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(1, 0))).size());
    }
    @Test
    public void test_get_detections_rectangle_z0_t1() {
        assertEquals(6, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 1))).size());
    }
    @Test
    public void test_get_detections_rectangle_z2_t0() {
        assertEquals(0, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(2, 0))).size());
    }
    @Test
    public void test_get_detections_rectangle_z0_t2() {
        assertEquals(0, hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1).updatePlane(ImagePlane.getPlane(0, 2))).size());
    }
    @Test
    public void test_get_detections_for_roi_are_detections_arent_cells_annotations_tiles() {
        // Check type
        var dets = hierarchy.getAllDetectionsForROI(ROIs.createRectangleROI(region1));
        assertTrue(dets.stream().allMatch(PathObject::isDetection));
        assertFalse(dets.stream().allMatch(PathObject::isCell));
        assertFalse(dets.stream().allMatch(PathObject::isAnnotation));
        assertFalse(dets.stream().allMatch(PathObject::isTile));
    }

    @Test
    public void test_annotations_for_region_are_annotations_not_detections() {
        // Get for region
        var ann = hierarchy.getAnnotationsForRegion(region1, null);
        assertTrue(ann.stream().allMatch(PathObject::isAnnotation));
        assertFalse(ann.stream().allMatch(PathObject::isDetection));
    }
    @Test
    public void test_annotations_for_region() {
        assertEquals(2, hierarchy.getAnnotationsForRegion(region1, null).size());
    }
    @Test
    public void test_annotations_for_region_smaller() {
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
    public void test_get_all_objects_with_root() {
        assertEquals(7, hierarchy2.getAllObjects(true).size());
    }
    @Test
    public void test_get_all_objects_no_root() {
        assertEquals(6, hierarchy2.getAllObjects(false).size());
    }

    @Test
    public void test_get_all_points() {
        assertEquals(4, hierarchy2.getAllPointObjects().size());
    }
    @Test
    public void test_get_all_point_annotations() {
        assertEquals(2, hierarchy2.getAllPointAnnotations().size());
    }

    @Test
    public void test_get_all_point_annotations_are_all_annotations() {
        assertTrue(hierarchy2.getAllPointAnnotations().stream().allMatch(PathObject::isAnnotation));
    }

}
