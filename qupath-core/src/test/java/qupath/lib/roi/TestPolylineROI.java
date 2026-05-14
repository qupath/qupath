package qupath.lib.roi;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPolylineROI {

    @Test
    public void testArea() {
        var roi = ROIs.createPolylineROI(0, 10);
        assertEquals(0, roi.getArea());
    }

    @Test
    public void testEmpty() {
        var roi = ROIs.createPolylineROI(List.of());
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
        var roi = ROIs.createPolylineROI(List.of(new Point2(0, 0), new Point2(0, 1), new Point2(1, 0)));
        assertFalse(roi.isEmpty());
    }

    @Test
    public void testEmptyCentroid() {
        var roi = ROIs.createPolylineROI(List.of(new Point2(0, 1), new Point2(1, 2)));
        assertEquals(0.5, roi.getCentroidX());
        assertEquals(1.5, roi.getCentroidY());
    }

    @Test
    public void testGeometryEmptyCentroid() {
        var roi = ROIs.createPolylineROI(List.of(new Point2(0, 1), new Point2(1, 2)));
        var geom = roi.getGeometry();
        assertEquals(new Coordinate(0.5, 1.5), geom.getCentroid().getCoordinate());
    }

    @Test
    public void testGeometryNonEmptyCentroid() {
        var roi = ROIs.createPolylineROI(List.of(new Point2(0, 100), new Point2(100, 200), new Point2(0, 300)));
        var geom = roi.getGeometry();
        var centroid = geom.getCentroid().getCoordinate();
        assertEquals(roi.getCentroidX(), centroid.getX(), 1e-2);
        assertEquals(roi.getCentroidY(), centroid.getY(), 1e-2);
    }

    @Test
    public void testLength() {
        var points = createPoints(2);
        var roi = ROIs.createPolylineROI(points);
        assertEquals(points.getFirst().distance(points.getLast()), roi.getLength());
    }

    @Test
    public void testNumPoints() {
        int n = 10;
        var roi = ROIs.createPolylineROI(createPoints(n));
        assertEquals(n, roi.getNumPoints());
    }

    @Test
    public void testEquals() {
        var roi = ROIs.createPolylineROI(createPoints(5));
        var roi2 = ROIs.createPolylineROI(createPoints(5));
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsDuplicate() {
        var roi = ROIs.createPolylineROI(createPoints(5));
        var roi2 = roi.duplicate();
        assertEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX() {
        var roi = ROIs.createPolylineROI(0, 10);
        var roi2 = ROIs.createPolylineROI(1, 10);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY() {
        var roi = ROIs.createPolylineROI(0, 10);
        var roi2 = ROIs.createPolylineROI(0, 11);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX2() {
        var roi = ROIs.createPolylineROI(List.of(new Point2(1, 1), new Point2(2, 2), new Point2(3, 3)));
        var roi2 = ROIs.createPolylineROI(List.of(new Point2(1, 1), new Point2(1, 2), new Point2(3, 3)));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY2() {
        var roi = ROIs.createPolylineROI(List.of(new Point2(1, 1), new Point2(2, 2), new Point2(3, 3)));
        var roi2 = ROIs.createPolylineROI(List.of(new Point2(1, 1), new Point2(2, 1), new Point2(3, 3)));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneZ() {
        var roi = ROIs.createPolylineROI(createPoints(4));
        var roi2 = roi.updatePlane(ImagePlane.getPlane(1, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneT() {
        var roi = ROIs.createPolylineROI(createPoints(4));
        var roi2 = roi.updatePlane(ImagePlane.getPlane(0, 1));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneC() {
        var roi = ROIs.createPolylineROI(createPoints(4));
        var roi2 = roi.updatePlane(ImagePlane.getPlaneWithChannel(1, 0, 0));
        assertNotEquals(roi, roi2);
    }

}
