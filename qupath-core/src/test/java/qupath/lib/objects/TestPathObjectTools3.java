/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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


package qupath.lib.objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class TestPathObjectTools3 {

    @Test
    public void test_findTouchingBounds() {
        // Check we can find objects that touch a ROI boundary
        var parent = ROIs.createRectangleROI(0, 0, 100, 100);
        var intersecting = PathObjects.createDetectionObject(ROIs.createRectangleROI(99, 99, 10, 10));
        var touchingCorner = PathObjects.createDetectionObject(ROIs.createRectangleROI(100, 100, 10, 10));
        var touchingSide = PathObjects.createDetectionObject(ROIs.createRectangleROI(50, 100, 10, 10));
        var inside = PathObjects.createDetectionObject(ROIs.createRectangleROI(50, 50, 10, 10));
        var outside = PathObjects.createDetectionObject(ROIs.createRectangleROI(150, 150, 10, 10));

        var results = PathObjectTools.findTouchingBounds(parent, List.of(intersecting, touchingCorner, touchingSide, inside, outside));
        Assertions.assertEquals(Set.of(intersecting, touchingCorner, touchingSide), Set.copyOf(results));
    }

    @Test
    public void test_findTouchingBoundsDifferentPlane() {
        // Check we do *not* find objects that touch a ROI boundary, but are on the wrong plane
        var parent = ROIs.createRectangleROI(0, 0, 100, 100);
        var touchingSide = PathObjects.createDetectionObject(ROIs.createRectangleROI(50, 100, 10, 10));
        var touchingSideDiffZ = PathObjects.createDetectionObject(touchingSide.getROI().updatePlane(ImagePlane.getPlaneWithChannel(0, 1, 0)));
        var touchingSideDiffT= PathObjects.createDetectionObject(touchingSide.getROI().updatePlane(ImagePlane.getPlaneWithChannel(0, 0, 1)));
        var results = PathObjectTools.findTouchingBounds(parent, List.of(touchingSide, touchingSideDiffZ, touchingSideDiffT));
        Assertions.assertEquals(Set.of(touchingSide), Set.copyOf(results));
    }

    @Test
    public void test_removeTouchingBounds() {
        // Check that we can remove objects that touch a ROI boundary
        var parent = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 100, 100));

        var intersecting = PathObjects.createDetectionObject(ROIs.createRectangleROI(99, 99, 10, 10));
        var touchingCorner = PathObjects.createDetectionObject(ROIs.createRectangleROI(100, 100, 10, 10));
        var touchingSide = PathObjects.createDetectionObject(ROIs.createRectangleROI(50, 100, 10, 10));
        var inside = PathObjects.createDetectionObject(ROIs.createRectangleROI(50, 50, 10, 10));
        var outside = PathObjects.createDetectionObject(ROIs.createRectangleROI(150, 150, 10, 10));

        // Check using no filter
        var hierarchy = createHierarchy(parent, intersecting, touchingCorner, touchingSide, inside, outside);
        var result = PathObjectTools.removeTouchingBounds(hierarchy, parent);
        Assertions.assertTrue(result);
        Assertions.assertEquals(Set.of(parent, inside, outside), Set.copyOf(hierarchy.getAllObjects(false)));

        // Check using detecton filter
        var hierarchy2 = createHierarchy(parent, intersecting, touchingCorner, touchingSide, inside, outside);
        var resultRemoveDetections = PathObjectTools.removeTouchingBounds(hierarchy2, parent, PathObject::isDetection);
        Assertions.assertTrue(resultRemoveDetections);
        Assertions.assertEquals(Set.of(parent, inside, outside), Set.copyOf(hierarchy2.getAllObjects(false)));

        // Check using annotation filter
        var hierarchy3 = createHierarchy(parent, intersecting, touchingCorner, touchingSide, inside, outside);
        var resultRemoveAnnotations = PathObjectTools.removeTouchingBounds(hierarchy3, parent, PathObject::isAnnotation);
        Assertions.assertFalse(resultRemoveAnnotations);
        Assertions.assertEquals(Set.of(parent, intersecting, touchingCorner, touchingSide, inside, outside),
                hierarchy3.getAllObjects(false));
    }

    private static PathObjectHierarchy createHierarchy(PathObject... pathObjects) {
        var hierarchy = new PathObjectHierarchy();
        hierarchy.addObjects(Arrays.asList(pathObjects));
        return hierarchy;
    }

}
