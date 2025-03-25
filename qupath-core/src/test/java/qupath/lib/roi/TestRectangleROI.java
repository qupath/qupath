package qupath.lib.roi;

import org.junit.jupiter.api.Test;
import qupath.lib.regions.ImagePlane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestRectangleROI {

    @Test
    public void testArea() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        assertEquals(600, roi.getArea());
    }

    @Test
    public void testPerimeter() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        assertEquals(100, roi.getLength());
    }

    @Test
    public void testNumPoints() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        assertEquals(4, roi.getNumPoints());
    }

    @Test
    public void testEquals() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = ROIs.createRectangleROI(0, 10, 20, 30);
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsDuplicate() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = roi.duplicate();
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsConvex() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = roi.getConvexHull();
        assertEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = ROIs.createRectangleROI(1, 10, 20, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = ROIs.createRectangleROI(0, 9, 20, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsWidth() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = ROIs.createRectangleROI(0, 10, 21, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsHeight() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30);
        var roi2 = ROIs.createRectangleROI(0, 10, 20, 31);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneZ() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlane(1, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneT() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlane(0, 1));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneC() {
        var roi = ROIs.createRectangleROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlaneWithChannel(1, 0, 0));
        assertNotEquals(roi, roi2);
    }

}
