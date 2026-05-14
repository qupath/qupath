package qupath.lib.roi;

import org.junit.jupiter.api.Test;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPointsROI {

    @Test
    public void testArea() {
        var roi = ROIs.createPointsROI(0, 10);
        assertEquals(0, roi.getArea());
    }

    @Test
    public void testEmpty() {
        var roi = ROIs.createPointsROI();
        assertTrue(roi.isEmpty());
        assertEquals(0, roi.getNumPoints());
    }

    private static List<Point2> createPoints(int n) {
        List<Point2> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new Point2(i, i*2+1));
        }
        return points;
    }

    @Test
    public void testNotEmpty() {
        var roi = ROIs.createPointsROI(0, 10);
        assertFalse(roi.isEmpty());
    }

    @Test
    public void testLength() {
        var roi = ROIs.createPointsROI(createPoints(10));
        assertEquals(0, roi.getLength());
    }

    @Test
    public void testNumPoints() {
        int n = 10;
        var roi = ROIs.createPointsROI(createPoints(n));
        assertEquals(n, roi.getNumPoints());
    }

    @Test
    public void testEquals() {
        var roi = ROIs.createPointsROI(createPoints(5));
        var roi2 = ROIs.createPointsROI(createPoints(5));
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsDuplicate() {
        var roi = ROIs.createPointsROI(createPoints(5));
        var roi2 = roi.duplicate();
        assertEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX() {
        var roi = ROIs.createPointsROI(0, 10);
        var roi2 = ROIs.createPointsROI(1, 10);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY() {
        var roi = ROIs.createPointsROI(0, 10);
        var roi2 = ROIs.createPointsROI(0, 11);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX2() {
        var roi = ROIs.createPointsROI(List.of(new Point2(1, 1), new Point2(2, 2), new Point2(3, 3)));
        var roi2 = ROIs.createPointsROI(List.of(new Point2(1, 1), new Point2(1, 2), new Point2(3, 3)));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY2() {
        var roi = ROIs.createPointsROI(List.of(new Point2(1, 1), new Point2(2, 2), new Point2(3, 3)));
        var roi2 = ROIs.createPointsROI(List.of(new Point2(1, 1), new Point2(2, 1), new Point2(3, 3)));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneZ() {
        var roi = ROIs.createPointsROI(createPoints(4));
        var roi2 = roi.updatePlane(ImagePlane.getPlane(1, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneT() {
        var roi = ROIs.createPointsROI(createPoints(4));
        var roi2 = roi.updatePlane(ImagePlane.getPlane(0, 1));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneC() {
        var roi = ROIs.createPointsROI(createPoints(4));
        var roi2 = roi.updatePlane(ImagePlane.getPlaneWithChannel(1, 0, 0));
        assertNotEquals(roi, roi2);
    }

}
