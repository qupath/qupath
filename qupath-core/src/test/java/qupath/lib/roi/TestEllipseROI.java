package qupath.lib.roi;

import org.junit.jupiter.api.Test;
import qupath.lib.regions.ImagePlane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestEllipseROI {

    @Test
    public void testArea() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 20);
        assertEquals(Math.PI * 10 * 10, roi.getArea());
    }

    @Test
    public void testPerimeter() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 20);
        assertEquals(Math.PI * 20, roi.getLength());
    }

    @Test
    public void testEquals() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = ROIs.createEllipseROI(0, 10, 20, 30);
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsDuplicate() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = roi.duplicate();
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsConvex() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = roi.getConvexHull();
        assertEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = ROIs.createEllipseROI(1, 10, 20, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = ROIs.createEllipseROI(0, 9, 20, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsWidth() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = ROIs.createEllipseROI(0, 10, 21, 30);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsHeight() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = ROIs.createEllipseROI(0, 10, 20, 31);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneZ() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlane(1, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneT() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlane(0, 1));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneC() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30, ImagePlane.getDefaultPlane());
        var roi2 = roi.updatePlane(ImagePlane.getPlaneWithChannel(1, 0, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsRectangle() {
        var roi = ROIs.createEllipseROI(0, 10, 20, 30);
        var roi2 = ROIs.createRectangleROI(0, 10, 20, 30);
        assertNotEquals(roi, roi2);
    }

}
