package qupath.lib.roi;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.Test;

import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.ROI;

public class TestROIs {
	
	@Test
	public void roiSerialization() {
		
		double tol = 0.0;
		
		// Test serialization
		LineROI roi = new LineROI(100, 200, 300, 400);
		LineROI roi2 = (LineROI)objectFromBytes(objectToBytes(roi));
		testEqualBounds(roi, roi2, tol);
		testEqualLines(roi, roi2, tol);
		assertEquals(0, DefaultROIComparator.getInstance().compare(roi, roi2));
		
		RectangleROI rect = new RectangleROI(100, 200, 300, 400, 0, 1, 2);
		ROI rect2 = (ROI)objectFromBytes(objectToBytes(rect));
		testEqualBounds(rect, rect2, tol);
		testEqualPolygonPoints(rect, rect2, tol);
		assertEquals(0, DefaultROIComparator.getInstance().compare(rect, rect2));
		
		EllipseROI ellipse = new EllipseROI(100, 200, 300, 400, 0, 1, 2);
		ROI ellipse2 = (ROI)objectFromBytes(objectToBytes(ellipse));
		testEqualBounds(ellipse, ellipse2, tol);
		testEqualPolygonPoints(ellipse, ellipse2, tol);
		assertEquals(0, DefaultROIComparator.getInstance().compare(ellipse, ellipse2));
		
		PolygonROI poly = new PolygonROI(new float[] {1.0f, 2.5f, 5.0f}, new float[] {10.0f, 11.0f, 12.0f}, 0, 1, 2);
		ROI poly2 = (ROI)objectFromBytes(objectToBytes(poly));
		testEqualBounds(poly, poly2, tol);
		testEqualPolygonPoints(poly, poly2, tol);
		assertEquals(0, DefaultROIComparator.getInstance().compare(poly, poly2));
		
		// Test polygon construction from points
		PolygonROI poly3 = new PolygonROI(poly.getPolygonPoints(), 0, 1, 2);
		testEqualBounds(poly, poly3, tol);
		testEqualPolygonPoints(poly, poly3, tol);
		assertEquals(0, DefaultROIComparator.getInstance().compare(poly, poly3));
		
	}
	
	
	private static void testEqualLines(LineROI roi1, LineROI roi2, double tolerance) {
		assertEquals(roi1.getX1(), roi2.getX1(), tolerance);
		assertEquals(roi1.getY1(), roi2.getY1(), tolerance);
		assertEquals(roi1.getX2(), roi2.getX2(), tolerance);
		assertEquals(roi1.getY2(), roi2.getY2(), tolerance);
	}
	
	
	private static void testEqualBounds(ROI roi1, ROI roi2, double tolerance) {
		assertEquals(roi1.getBoundsX(), roi2.getBoundsX(), tolerance);
		assertEquals(roi1.getBoundsY(), roi2.getBoundsY(), tolerance);
		assertEquals(roi1.getBoundsWidth(), roi2.getBoundsWidth(), tolerance);
		assertEquals(roi1.getBoundsHeight(), roi2.getBoundsHeight(), tolerance);
		assertEquals(roi1.getC(), roi2.getC());
		assertEquals(roi1.getZ(), roi2.getZ());
		assertEquals(roi1.getT(), roi2.getT());
	}
	
	
	private static void testEqualPolygonPoints(ROI roi1, ROI roi2, double tolerance) {
		List<Point2> p1 = roi1.getPolygonPoints();
		List<Point2> p2 = roi2.getPolygonPoints();
		assertEquals(p1.size(), p2.size());
		for (int i = 0; i < p1.size(); i++)
			assertEquals(0.0, p1.get(i).distance(p2.get(i)), tolerance);
	}
	
	
	private static byte[] objectToBytes(Object o) {
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			new ObjectOutputStream(stream).writeObject(o);
			return stream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static Object objectFromBytes(byte[] bytes) {
		try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
			return new ObjectInputStream(stream).readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

}
