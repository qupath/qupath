/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;

import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Test {@link GeometryTools}. Note that most of the relevant tests for ROI conversion are in {@link TestROIs}.
 */
public class TestGeometryTools {
	
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
			e.printStackTrace();
			fail(e.getLocalizedMessage());
		}
		
	}
	

}