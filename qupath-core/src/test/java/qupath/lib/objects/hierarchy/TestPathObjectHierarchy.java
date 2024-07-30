package qupath.lib.objects.hierarchy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

}
