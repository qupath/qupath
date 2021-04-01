/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.awt.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Test;

import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestAwtTools {
	
	@Test
	public void test_getBounds() {
		var region = ImageRegion.createInstance(0, 10, 500, 650, 0, 0);
		var rectangle1 = new Rectangle(0, 10, 500, 650);
		var rectangle2 = new Rectangle(5, 6, 1, 32);
		var rectangle3 = new Rectangle(500, 500, 500, 500);
		var roi = ROIs.createEllipseROI(500, 500, 500, 500, ImagePlane.getDefaultPlane());
		var roi2 = ROIs.createEmptyROI();
		assertEqualRectangles(rectangle3, AwtTools.getBounds(roi));
		assertEqualRectangles(new Rectangle(), AwtTools.getBounds(roi2));
		assertEqualRectangles(rectangle1, AwtTools.getBounds(region));
		assertEqualRectangles(rectangle1, AwtTools.getBounds(region, null));
		assertEqualRectangles(rectangle1, AwtTools.getBounds(region, rectangle2));
	}
	
	@Test
	public void test_getBounds2D() {
		var roi = ROIs.createRectangleROI(500, 500, 500, 500, ImagePlane.getDefaultPlane());
		var rectangle2d = new Rectangle2D.Double(500, 500, 500, 500);
		var rectangle2d2 = new Rectangle2D.Double(5, 6, 1, 32);
		assertEqualRectangles2D(rectangle2d, AwtTools.getBounds2D(roi));
		assertEqualRectangles2D(rectangle2d, AwtTools.getBounds2D(roi, null));
		assertEqualRectangles2D(rectangle2d, AwtTools.getBounds2D(roi, rectangle2d2));
	}
	
	@Test
	public void test_getImageRegion() {
		var rectangle = new Rectangle(20, 20, 64, 64);
		Shape rectangle2 = new Rectangle(6, 7, 50, 32);
		Shape line = new Line2D.Float(6, 7, 50, 32);
		assertEqualImageRegions(ImageRegion.createInstance(20, 20, 64, 64, 5, 6), AwtTools.getImageRegion(rectangle, 5, 6));
		assertEqualImageRegions(ImageRegion.createInstance(6, 7, 50, 32, 1, 1), AwtTools.getImageRegion(rectangle2, 1, 1));
		assertEqualImageRegions(ImageRegion.createInstance(6, 7, 50-6, 32-7, 0, 0), AwtTools.getImageRegion(line, 0, 0));
	}
	
	
	private static void assertEqualRectangles(Rectangle expected, Rectangle actual) {
		assertEquals(expected.getX(), actual.getX());
		assertEquals(expected.getY(), actual.getY());
		assertEquals(expected.getWidth(), actual.getWidth());
		assertEquals(expected.getHeight(), actual.getHeight());
	}
	
	private static void assertEqualRectangles2D(Rectangle2D expected, Rectangle2D actual) {
		assertEquals(expected.getX(), actual.getX());
		assertEquals(expected.getY(), actual.getY());
		assertEquals(expected.getWidth(), actual.getWidth());
		assertEquals(expected.getHeight(), actual.getHeight());
	}
	
	private static void assertEqualImageRegions(ImageRegion expected, ImageRegion actual) {
		assertEquals(expected.getX(), actual.getX());
		assertEquals(expected.getY(), actual.getY());
		assertEquals(expected.getWidth(), actual.getWidth());
		assertEquals(expected.getHeight(), actual.getHeight());
		assertEquals(expected.getZ(), actual.getZ());
		assertEquals(expected.getT(), actual.getT());
	}
}