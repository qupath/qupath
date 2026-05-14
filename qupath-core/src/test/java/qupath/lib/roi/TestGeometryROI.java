package qupath.lib.roi;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGeometryROI {

    private static final GeometryFactory factory = GeometryTools.getDefaultFactory();

    @Test
    public void testArea() {
        var roi = GeometryTools.createRectangle(0, 10, 20, 30);
        assertEquals(20*30, roi.getArea());
    }

    @Test
    public void testEmpty() {
        var roi = GeometryTools.geometryToROI(factory.createEmpty(2));
        assertTrue(roi.isEmpty());
    }

    @Test
    public void testNotEmpty() {
        var roi = GeometryTools.createRectangle(0, 1, 2, 3);
        assertFalse(roi.isEmpty());
    }

    @Test
    public void testEmptyCentroid() {
        // This test fails! Polygon.isRectangle() returns false if the width or height is 0,
        // and conversion to a Java Area results in an empty area... leading to an empty ROI.
        var roi = GeometryTools.geometryToROI(
                GeometryTools.createRectangle(0, 1, 2, 0));
        assertEquals(1, roi.getCentroidX());
        assertEquals(1, roi.getCentroidY());
    }

    private static ROI createGeometryROI(double x, double y) {
        var rect = GeometryTools.createRectangle(x, y, 100, 200);
        var geom = rect.difference(rect.buffer(-10));
        return GeometryTools.geometryToROI(geom);
    }

    @Test
    public void testEquals() {
        var roi = createGeometryROI(5, 10);
        var roi2 = createGeometryROI(5, 10);
        assertEquals(roi, roi2);
    }

    @Test
    public void testEqualsDuplicate() {
        var roi = createGeometryROI(5, 10);
        var roi2 = roi.duplicate();
        assertEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsX() {
        var roi = createGeometryROI(5, 10);
        var roi2 = createGeometryROI(6, 10);
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsY() {
        var roi = createGeometryROI(5, 10);
        var roi2 = createGeometryROI(5, 11);
        assertNotEquals(roi, roi2);
    }


    @Test
    public void testNotEqualsPlaneZ() {
        var roi = createGeometryROI(5, 10);
        var roi2 = roi.updatePlane(ImagePlane.getPlane(1, 0));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneT() {
        var roi = createGeometryROI(5, 10);
        var roi2 = roi.updatePlane(ImagePlane.getPlane(0, 1));
        assertNotEquals(roi, roi2);
    }

    @Test
    public void testNotEqualsPlaneC() {
        var roi = createGeometryROI(5, 10);
        var roi2 = roi.updatePlane(ImagePlane.getPlaneWithChannel(1, 0, 0));
        assertNotEquals(roi, roi2);
    }

}
