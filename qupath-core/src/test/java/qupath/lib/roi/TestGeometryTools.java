/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020, 2024 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.roi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.AffineTransformation;

import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.geom.Point2;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * Test {@link GeometryTools}. Note that most of the relevant tests for ROI conversion are in {@link TestROIs}.
 */
public class TestGeometryTools {

	private static final Logger logger = LoggerFactory.getLogger(TestGeometryTools.class);

	/**
	 * Compare conversion of {@link AffineTransform} and {@link AffineTransformation} objects.
	 */
	@Test
	public void testAffineTransforms() {
		
		double[] source = new double[] {1.3, 2.7};
		
		double[] destTransform = new double[source.length];
		var transform = AffineTransform.getScaleInstance(2.0, 0.5);
		transform.rotate(0.5);
		transform.translate(-20, 42);
		transform.transform(source, 0, destTransform, 0, source.length/2);
		
		var transformation = GeometryTools.convertTransform(transform);
		var c = new Coordinate(source[0], source[1]);
		transformation.transform(c, c);
		double[] destTransformation = new double[] {c.x, c.y};
		
		var transformBack = GeometryTools.convertTransform(transformation);
		double[] matBefore = new double[6];
		double[] matAfter = new double[6];
		transform.getMatrix(matBefore);
		transformBack.getMatrix(matAfter);
		
		assertArrayEquals(destTransform, destTransformation, 0.001);
		assertArrayEquals(matBefore, matAfter, 0.001);
		
	}
	
	/**
	 * Check we can perform various geometry changes while remaining valid
	 */
	@Test
	public void testComplexROIs() {
		
		File fileHierarchy = new File("src/test/resources/data/test-objects.hierarchy");
		try (InputStream stream = Files.newInputStream(fileHierarchy.toPath())) {
			var hierarchy = (PathObjectHierarchy)new ObjectInputStream(stream).readObject();
			var geometries = hierarchy.getFlattenedObjectList(null).stream().filter(p -> p.hasROI()).map(p -> p.getROI().getGeometry()).collect(Collectors.toCollection(() -> new ArrayList<>()));
			
			// Include some extra geometries that we know can be troublesome
			var rectangle = GeometryTools.createRectangle(0, 0, 100, 100);
			var ring = (Polygon)rectangle.buffer(100).difference(rectangle);
			geometries.add(ring);
			var nested = ring.union(rectangle.buffer(-40));
			geometries.add(nested);
			var nested2 = nested.difference(rectangle.buffer(-45));
			geometries.add(nested2);
			
			var filledNested = (Polygon)GeometryTools.fillHoles(nested);
			assertNotEquals(nested.getArea(), filledNested.getArea());
			assertNotEquals(nested.getNumGeometries(), 1);
			assertEquals(filledNested.getNumInteriorRing(), 0);
			assertEquals(filledNested.getArea(), GeometryTools.externalRingArea(filledNested));

			var filledNested2 = (Polygon)GeometryTools.fillHoles(nested2);
			assertNotEquals(nested2.getArea(), filledNested2.getArea());
			assertNotEquals(nested2.getNumGeometries(), 1);
			assertEquals(filledNested2.getNumInteriorRing(), 0);
			assertEquals(filledNested2.getArea(), GeometryTools.externalRingArea(filledNested2));

			for (var geom : geometries) {
				
				assertTrue(geom.isValid());
				
				var geom2 = GeometryTools.fillHoles(geom);
				assertTrue(geom2.isValid());
				
				geom2 = GeometryTools.removeFragments(geom, geom.getArea()/2);
				assertTrue(geom2.isValid());

				geom2 = GeometryTools.refineAreas(geom, geom.getArea()/2, geom.getArea()/2);
				assertTrue(geom2.isValid());
				
				geom2 = GeometryTools.ensurePolygonal(geom);
				assertTrue(geom2.isValid());			
			}
			var geom2 = GeometryTools.union(geometries);
			assertTrue(geom2.isValid());
		} catch (Exception e) {
			fail(e);
		}
		
	}

	@Test
	public void testUnion() {
		var g1 = GeometryTools.createRectangle(0, 0, 100, 200).norm();
		var gBeside = GeometryTools.createRectangle(100, 0, 100, 200).norm();
		var gDiagonal = GeometryTools.createRectangle(100, 200, 100, 200).norm();
		var gDisconnected = GeometryTools.createRectangle(400, 400, 100, 200).norm();
		var gOverlapping = GeometryTools.createRectangle(0, 100, 100, 200).norm();
		var gLine = GeometryTools.createLineString(1000, 2000, 3000, 4000).norm();

		assertEquals(g1.copy().norm(), GeometryTools.union(g1, g1.copy()).norm());
		assertEquals(g1.copy().norm(), FastPolygonUnion.union(g1, g1.copy()).norm());
		assertEquals(1, FastPolygonUnion.union(g1, g1).getNumGeometries());

		assertEquals(g1.union(gBeside).norm(), GeometryTools.union(g1, gBeside).norm());
		assertEquals(g1.union(gBeside).norm(), FastPolygonUnion.union(g1, gBeside).norm());
		assertEquals(1, FastPolygonUnion.union(g1, gBeside).getNumGeometries());

		assertEquals(g1.union(gDiagonal).norm(), GeometryTools.union(g1, gDiagonal).norm());
		assertEquals(g1.union(gDiagonal).norm(), FastPolygonUnion.union(g1, gDiagonal).norm());
		assertEquals(2, FastPolygonUnion.union(g1, gDiagonal).getNumGeometries());

		assertEquals(g1.union(gDisconnected).norm(), GeometryTools.union(g1, gDisconnected).norm());
		assertEquals(g1.union(gDisconnected).norm(), FastPolygonUnion.union(g1, gDisconnected).norm());
		assertEquals(2, FastPolygonUnion.union(g1, gDisconnected).getNumGeometries());

		assertEquals(g1.union(gOverlapping).norm(), GeometryTools.union(g1, gOverlapping).norm());
		assertEquals(g1.union(gOverlapping).norm(), FastPolygonUnion.union(g1, gOverlapping).norm());
		assertEquals(1, FastPolygonUnion.union(g1, gOverlapping).getNumGeometries());

		var unionAll = g1.union(gOverlapping).union(gDisconnected).union(gBeside).union(gDiagonal).norm();
		assertEquals(unionAll, GeometryTools.union(g1, gOverlapping, gDisconnected, gBeside, gDiagonal).norm());
		assertEquals(unionAll, FastPolygonUnion.union(g1, gOverlapping, gDisconnected, gBeside, gDiagonal).norm());

		// We can compute a union with a line string
		assertEquals(2, GeometryTools.union(g1, gLine).getNumGeometries());
		// The fast polygon union discards lines
		assertEquals(1, FastPolygonUnion.union(g1, gLine).getNumGeometries());
	}


	@Test
	public void testFindLargestPolygonLineString() {
		var factory = GeometryTools.getDefaultFactory();
		var coords = new Coordinate[]{new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1)};
		assertNull(GeometryTools.findLargestPolygon(factory.createLineString(coords)));
	}

	@Test
	public void testFindLargestPolygonLinearRing() {
		var factory = GeometryTools.getDefaultFactory();
		var rectangle = GeometryTools.createRectangle(0, 0, 10, 10);
		assertNull(GeometryTools.findLargestPolygon(factory.createLinearRing(rectangle.getCoordinates())));
	}

	@Test
	public void testFindLargestPolygonPoints() {
		var factory = GeometryTools.getDefaultFactory();
		var coords = new Coordinate[]{new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1)};
		assertNull(GeometryTools.findLargestPolygon(factory.createMultiPointFromCoords(coords)));
	}

	@Test
	public void testFindLargestPolygonSingle() {
		var rectangle = GeometryTools.createRectangle(0, 0, 10, 10);
		assertEquals(rectangle, GeometryTools.findLargestPolygon(rectangle));

		var factory = GeometryTools.getDefaultFactory();
		var multipolygon = factory.createMultiPolygon(new Polygon[]{rectangle});
		assertEquals(rectangle, GeometryTools.findLargestPolygon(multipolygon));

		var geomCollection = factory.createGeometryCollection(new Geometry[]{rectangle});
		assertEquals(rectangle, GeometryTools.findLargestPolygon(geomCollection));
	}

	@Test
	public void testFindLargestPolygonMulti() {
		var small = GeometryTools.createRectangle(0, 0, 10, 10);
		var large = GeometryTools.createRectangle(0, 0, 20, 20);
		// Same area, but at the end of the list (so 'large' should be encountered first)
		var largeTranslated = GeometryTools.createRectangle(10, 10, 20, 20);

		var factory = GeometryTools.getDefaultFactory();
		var multipolygon1 = factory.createMultiPolygon(new Polygon[]{small, large, largeTranslated});
		var multipolygon2 = factory.createMultiPolygon(new Polygon[]{large, small, largeTranslated});
		var geomcollection1 = factory.createGeometryCollection(new Geometry[]{small, large, largeTranslated});
		var geomcollection2 = factory.createGeometryCollection(new Geometry[]{large, small, largeTranslated});

		assertEquals(large, GeometryTools.findLargestPolygon(multipolygon1));
		assertEquals(large, GeometryTools.findLargestPolygon(multipolygon2));
		assertEquals(large, GeometryTools.findLargestPolygon(geomcollection1));
		assertEquals(large, GeometryTools.findLargestPolygon(geomcollection2));
	}

	@Test
	public void testFindLargestPolygonHoles() {
		var outer = GeometryTools.createRectangle(0, 0, 100, 100);
		var hole = GeometryTools.createRectangle(10, 10, 20, 20);
		var withHole = outer.difference(hole);
		var large = GeometryTools.createRectangle(0, 0, 99, 99);

		var factory = GeometryTools.getDefaultFactory();
		var geomcollection1 = factory.createGeometryCollection(new Geometry[]{withHole, large});
		var geomcollection2 = factory.createGeometryCollection(new Geometry[]{large, withHole});

		assertEquals(large, GeometryTools.findLargestPolygon(geomcollection1));
		assertEquals(large, GeometryTools.findLargestPolygon(geomcollection2));
	}

	@Test
	public void testFillHoles() {
		var outer = GeometryTools.createRectangle(0, 0, 100, 100).norm();
		var hole = GeometryTools.createRectangle(10, 10, 20, 20).norm();
		var withHole = outer.difference(hole).norm();

		assertEquals(100*100 - 20*20, withHole.getArea(), 0.001);
		assertNotEquals(outer, withHole);
		assertEquals(outer, GeometryTools.fillHoles(withHole).norm());
	}

	@Test
	public void testConvertBowtie() {
		var polygon = ROIs.createPolygonROI(
				List.of(
						new Point2(0, 0),
						new Point2(10, 0),
						new Point2(0, 10),
						new Point2(10, 10)
				),
				ImagePlane.getDefaultPlane()
		);
		double eps = 1e-6;
		assertEquals(50, polygon.getArea(), eps);
		assertEquals(50, polygon.getGeometry().getArea(), eps);
		assertTrue(polygon.getGeometry().isValid());
		assertEquals(50, GeometryTools.roiToGeometry(polygon).getArea(), eps);
		assertTrue(GeometryTools.roiToGeometry(polygon).isValid());
		assertEquals(50, GeometryTools.shapeToGeometry(RoiTools.getShape(polygon)).getArea(), eps);
		assertTrue(GeometryTools.shapeToGeometry(RoiTools.getShape(polygon)).isValid());
	}


	/**
	 * The behavior of Polygonizer is rather... confusing.
	 * And non-deterministic.
	 * See https://github.com/locationtech/jts/issues/1063
	 * This test is ignored, but can be used to investigate the behavior of Polygonizer.
	 */
	@Test
	@Disabled
	public void randomPolygonize() {
		Random rng = new Random(100);
		GeometryFactory factory = GeometryTools.getDefaultFactory();
		PrecisionModel pm = factory.getPrecisionModel();
		// Note that increasing this to 1000 will cause the test to fail.
		int n = 100;
		Coordinate[] coords = new Coordinate[n];
		for (int i = 0; i < n-1; i++) {
			Coordinate c = new Coordinate(rng.nextDouble() * 1000, rng.nextDouble() * 1000);
			pm.makePrecise(c);
			coords[i] = c;
		}
		coords[coords.length-1] = coords[0];

		var lineString = factory.createLineString(coords).union();
		assertTrue(lineString.isValid());
		for (int k = 0; k < 100; k++) {
			var polygonizer = new Polygonizer(true);
			polygonizer.add(lineString);
			var polygons = polygonizer.getGeometry();
			var err = new IsValidOp(polygons).getValidationError();
			if (err != null) {
				logger.warn("Polygonizer gives {} points, error: {}", polygons.getNumPoints(), err);
			}
			assertTrue(polygons.isValid());
		}
	}

	/**
	 * Note that Polygonizer is unreliable for complex polygons, but we expect it to succeed
	 * for simple polygons.
	 */
	private static ROI createRandomPolygon() {
		var rng = new Random(100);
		var pm = GeometryTools.getDefaultFactory().getPrecisionModel();
		// Note that increasing this to 1000 will cause the test to fail, and the resulting (multi)polygon will be invalid.
		// I don't see a good solution to this - it can be considered a limitation of our ROI conversation that it
		// can't handle *extremely* complicated polygons.
		int n = 10;
		double[] x = rng.doubles(n, 0, 1000)
				.map(pm::makePrecise).toArray();
		double[] y = rng.doubles(n, 0, 1000)
				.map(pm::makePrecise).toArray();
		return ROIs.createPolygonROI(x, y, ImagePlane.getDefaultPlane());
	}

	@Test
	public void testConvertRandomPolygonDirect() {
		var polygon = createRandomPolygon();
		double area = polygon.getArea();
		double eps = Math.max(1e-6, area * 0.0001);

		var geom = polygon.getGeometry();
		var err = new IsValidOp(geom).getValidationError();
		assertNull(err);
		assertTrue(geom.isValid());
		assertEquals(area, geom.getArea(), eps);
	}

	@Test
	public void testConvertRandomPolygon() {
		var polygon = createRandomPolygon();
		double area = polygon.getArea();
		double eps = Math.max(1e-6, area * 0.0001);

		var geom = GeometryTools.roiToGeometry(polygon);
		var err = new IsValidOp(geom).getValidationError();
		assertNull(err);
		assertTrue(geom.isValid());
		assertEquals(area, geom.getArea(), eps);
	}

	@Test
	public void testConvertRandomPolygonViaShape() {
		var polygon = createRandomPolygon();
		double area = polygon.getArea();
		double eps = Math.max(1e-6, area * 0.0001);

		var geom = GeometryTools.shapeToGeometry(RoiTools.getShape(polygon));
		var err = new IsValidOp(geom).getValidationError();
		assertNull(err);
		assertTrue(geom.isValid());
		assertEquals(area, geom.getArea(), eps);
	}


	@Test
	public void testGeometryFactory() {
		var factory = GeometryTools.getDefaultFactory();
		assertFalse(factory.getPrecisionModel().isFloating());
		assertEquals(100, factory.getPrecisionModel().getScale());
	}


	@Test
	public void testRectangleIntersectionAreaTranslated() {
		var a = GeometryTools.createRectangle(0, 0, 200, 100);
		var b = GeometryTools.createRectangle(100, 0, 200, 100);
		assertEquals(100*100, GeometryTools.intersectionArea(a, b), 1e-6);
		assertEquals(a.intersection(b).getArea(), GeometryTools.intersectionArea(a, b), 1e-6);
	}

	@Test
	public void testRectangleIntersectionAreaEqual() {
		var a = GeometryTools.createRectangle(0, 0, 200, 100);
		var b = GeometryTools.createRectangle(0, 0, 200, 100);
		assertEquals(200*100, GeometryTools.intersectionArea(a, b), 1e-6);
		assertEquals(a.intersection(b).getArea(), GeometryTools.intersectionArea(a, b), 1e-6);
	}

	@Test
	public void testRectangleIntersectionAreaTouching() {
		var a = GeometryTools.createRectangle(0, 0, 200, 100);
		var b = GeometryTools.createRectangle(200, 0, 200, 100);
		assertEquals(0, GeometryTools.intersectionArea(a, b), 1e-6);
		assertEquals(a.intersection(b).getArea(), GeometryTools.intersectionArea(a, b), 1e-6);
	}


	@Test
	public void testEllipseIntersectionAreaTranslated() {
		int nPoints = 100;
		var a = GeometryTools.createEllipse(0, 0, 200, 100, nPoints);
		var b = GeometryTools.createEllipse(100, 0, 200, 100, nPoints);
		assertEquals(a.intersection(b).getArea(), GeometryTools.intersectionArea(a, b), 1e-6);
	}

	@Test
	public void testEllipseIntersectionAreaEqual() {
		int nPoints = 100;
		var a = GeometryTools.createEllipse(0, 0, 200, 100, nPoints);
		var b = GeometryTools.createEllipse(0, 0, 200, 100, nPoints);
		assertEquals(a.intersection(b).getArea(), GeometryTools.intersectionArea(a, b), 1e-6);
	}

	@Test
	public void testEllipseIntersectionAreaTouching() {
		int nPoints = 100;
		var a = GeometryTools.createEllipse(0, 0, 200, 100, nPoints);
		var b = GeometryTools.createEllipse(200, 0, 200, 100, nPoints);
		assertEquals(0, GeometryTools.intersectionArea(a, b), 1e-6);
		assertEquals(a.intersection(b).getArea(), GeometryTools.intersectionArea(a, b), 1e-6);
	}


}