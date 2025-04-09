package qupath.lib.roi;

import org.junit.jupiter.api.Test;
import qupath.lib.regions.ImagePlane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLineROI {

    @Test
    public void testArea() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        assertEquals(0, roi.getArea());
    }

    @Test
    public void testEmpty() {
        var roi = ROIs.createLineROI(0, 10, 0, 10);
        assertTrue(roi.isEmpty());
    }

    @Test
    public void testNotEmpty() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        assertFalse(roi.isEmpty());
    }

    @Test
    public void testLength() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        assertEquals(Math.sqrt(800), roi.getLength());
    }

    @Test
    public void testNumPoints() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        assertEquals(2, roi.getNumPoints());
    }

    @Test
    public void testEquals() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = ROIs.createLineROI(0, 10, 20, 30);
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsDuplicate() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = roi.duplicate();
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsConvex() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = roi.getConvexHull();
        assertEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = ROIs.createLineROI(1, 10, 20, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = ROIs.createLineROI(0, 9, 20, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX2() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = ROIs.createLineROI(0, 10, 21, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY2() {
        var roi = ROIs.createLineROI(0, 10, 20, 30);
        var roi2 = ROIs.createLineROI(0, 10, 20, 31);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneZ() {
        var roi = ROIs.createLineROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlane(1, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneT() {
        var roi = ROIs.createLineROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlane(0, 1));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneC() {
        var roi = ROIs.createLineROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlaneWithChannel(1, 0, 0));
        assertNotEquals(roi, roi2);
    }

}
