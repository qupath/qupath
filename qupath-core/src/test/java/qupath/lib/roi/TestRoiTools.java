/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021-2022 QuPath developers, The University of Edinburgh
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.io.GsonTools;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.interfaces.ROI;

/**
 * Test {@link RoiTools}.
 */
public class TestRoiTools {
	
	private static final Logger logger = LoggerFactory.getLogger(TestRoiTools.class);
	
	/**
	 * Compare conversion of {@link AffineTransform} and {@link AffineTransformation} objects.
	 */
	@Test
	public void testCombiningAndTiling() {
		
		for (int z = 0; z < 2; z++) {
			for (int t = 0; t < 2; t++) {
				
				double x = 1000;
				double y = 2000;
				double w = 3000;
				double h = 4000;
				var plane = ImagePlane.getPlane(z, t);
						
				var outer = ROIs.createRectangleROI(x, y, w, h, plane);
				var inner = ROIs.createEllipseROI(x+w*0.1, y+h*0.1, w*0.5, h*0.5, plane);
				var exterior = ROIs.createEllipseROI(x+w, y+h, w, h, plane);
			
				var roi = RoiTools.combineROIs(outer, inner, CombineOp.SUBTRACT);
				roi = RoiTools.combineROIs(roi, exterior, CombineOp.ADD);
				
				double targetArea = outer.getArea() - inner.getArea() + exterior.getArea();
				assertEquals(roi.getArea(), targetArea, targetArea * 1e-3);
				
				int overlap = 10;
				var dim = ImmutableDimension.getInstance(512, 400);
				var dimMax = ImmutableDimension.getInstance(1000, 1024);

				// Ensure tiles match legacy tiles
				long startTime = System.currentTimeMillis();
				var tiles = RoiTools.computeTiledROIs(roi, dim, dimMax, false, overlap);
				long middleTime = System.currentTimeMillis();
				var tilesLegacy = RoiTools.computeTiledROIsLegacy(roi, dim, dimMax, false, overlap);
				long endTime = System.currentTimeMillis();
				
				long legacyTime = endTime - middleTime;
				long newTime = middleTime - startTime;
				logger.trace(legacyTime + " -> " + newTime);
				
				// Should have the same number of tile ROIs
				assertEquals(tiles.size(), tilesLegacy.size());
				
				// Should be on the same plane
				assertTrue(tiles.stream().allMatch(r -> r.getImagePlane().equals(plane)));
				
				// Get normalized geometries
				var tilesGeometry = tiles.stream().map(r -> r.getGeometry()).collect(Collectors.toList());
				tilesGeometry.stream().forEach(g -> g.normalize());
				var tilesLegacyGeometry = tiles.stream().map(r -> r.getGeometry()).collect(Collectors.toList());
				tilesLegacyGeometry.stream().forEach(g -> g.normalize());
				assertEquals(tilesGeometry, tilesLegacyGeometry);
				
			}
		}
		
		
	}
	
	
	@Test
	public void testRemoveSmallRegions() {
		
		var roi = ROIs.createRectangleROI(0, 0, 20, 20, ImagePlane.getDefaultPlane());
		
		assertEquals(roi, RoiTools.removeSmallPieces(roi, 10, 10));
		assertFalse(RoiTools.removeSmallPieces(roi, 10, 10).isEmpty());
		assertTrue(RoiTools.removeSmallPieces(roi, 500, 0).isEmpty());
		
		var line = ROIs.createLineROI(0, 0, 100, 100, ImagePlane.getDefaultPlane());
		assertEquals(line, RoiTools.removeSmallPieces(line, 0, 0)); // Unchanged
		assertTrue(RoiTools.removeSmallPieces(line, 50, 0).isEmpty());

		var points = ROIs.createPointsROI(10, 10, ImagePlane.getDefaultPlane());
		assertEquals(points, RoiTools.removeSmallPieces(points, 0, 0)); // Unchanged
		assertTrue(RoiTools.removeSmallPieces(points, 50, 0).isEmpty());

	}
	
	
	
	/**
	 * Test tiling for a complex (mult)ipolygon.
	 */
	@Test
	public void testTilingPerformance() {
		
		ROI roiMain = null;
		try (var reader = new InputStreamReader(this.getClass().getResourceAsStream("/data/polygon.geojson"), StandardCharsets.UTF_8)) {
			roiMain = GsonTools.getInstance().fromJson(reader, ROI.class);
		} catch (IOException e1) {
			logger.warn("Unable to read polygon! Will try to generate one instead.");
			var geometry = createRandomPolygon(100, GeometryTools.getDefaultFactory(), 10000, 10, 10000, 10000);
			roiMain = GeometryTools.geometryToROI(geometry, ImagePlane.getDefaultPlane());
		}
		
		// Repeat for filled & unfilled ROIs - and use filled twice for some 'warm-up' time useful if looking at performance
		var roiFilled = RoiTools.fillHoles(roiMain);
		var rois = Arrays.asList(roiFilled, roiFilled, roiMain);
		
		for (var roi : rois) {
			int overlap = 10;
			var dim = ImmutableDimension.getInstance(512, 400);
			var dimMax = ImmutableDimension.getInstance(1000, 1024);
			
			long startTime = System.currentTimeMillis();
			var tiles = RoiTools.computeTiledROIs(roi, dim, dimMax, false, overlap);
			long middleTime = System.currentTimeMillis();
			var tilesLegacy = RoiTools.computeTiledROIsLegacy(roi, dim, dimMax, false, overlap);
			long endTime = System.currentTimeMillis();
			
			long legacyTime = endTime - middleTime;
			long newTime = middleTime - startTime;
			// Note that this requires some warning up (i.e. running once isn't very representative)
			logger.trace("Legacy tiling time: {}, Current tiling time: {}", legacyTime, newTime);
			
			
			// Should have the same number of tile ROIs
			assertEquals(tiles.size(), tilesLegacy.size());
			
			// Should be on the same plane
			assertTrue(tiles.stream().allMatch(r -> r.getImagePlane().equals(ImagePlane.getDefaultPlane())));
			
			// Get normalized geometries
			var tilesGeometry = tiles.stream().map(r -> r.getGeometry()).collect(Collectors.toList());
			tilesGeometry.stream().forEach(g -> g.normalize());
			var tilesLegacyGeometry = tiles.stream().map(r -> r.getGeometry()).collect(Collectors.toList());
			tilesLegacyGeometry.stream().forEach(g -> g.normalize());
			assertEquals(tilesGeometry, tilesLegacyGeometry);
			
		}
	}
	
	
	/**
	 * Create a complicated polygon as the union of random triangles.
	 * @param seed random number seed
	 * @param factory geometry factory
	 * @param nTriangles number of triangles (some will be included within others)
	 * @param nHoleTriangles number of 'negative' triangles to remove
	 * @param maxX maximum x coordinate
	 * @param maxY maximum y coordinate
	 * @return the (multi)polygon
	 */
	static Geometry createRandomPolygon(long seed, GeometryFactory factory, int nTriangles, int nHoleTriangles, double maxX, double maxY) {
		
		var pm = factory.getPrecisionModel();
		
		var coords = new Coordinate[4];
		var triangles = new ArrayList<Geometry>();
		var holeTriangles = new ArrayList<Geometry>();
		var rand = new Random(100);
		for (int i = 0; i < nTriangles + nHoleTriangles; i++) {
			for (int k = 0; k < 3; k++) {
				double x = rand.nextDouble() * maxX;
				double y = rand.nextDouble() * maxY;
				coords[k] = new Coordinate(pm.makePrecise(x), pm.makePrecise(y));
			}
			coords[3] = coords[0];
			if (i < nTriangles)
				triangles.add(factory.createPolygon(coords));
			else
				holeTriangles.add(factory.createPolygon(coords));
		}
		var geom = GeometryTools.union(triangles);
		if (holeTriangles.isEmpty())
			return geom;
		else
			return geom.difference(GeometryTools.union(holeTriangles));
		
	}
	
	
	@Test
	void testRandomRectangles() {
		
		var region = ImageRegion.createInstance(1000, 2000, 300, 400, 2, 3);
		
		var rng = new Random(100);
		
		assertNotNull(RoiTools.createRandomRectangle(region, region.getWidth()-1, region.getHeight()-1, rng));
		assertNotNull(RoiTools.createRandomRectangle(region, region.getWidth(), region.getHeight(), rng));
		assertEquals(region.getImagePlane(), RoiTools.createRandomRectangle(region, region.getWidth()-1, region.getHeight()-1, rng).getImagePlane());
		assertThrows(IllegalArgumentException.class, () -> RoiTools.createRandomRectangle(region, region.getWidth()+0.1, region.getHeight(), rng));
		assertThrows(IllegalArgumentException.class, () -> RoiTools.createRandomRectangle(region, region.getWidth()+0.1, region.getHeight()+1, rng));

		
		var roi = ROIs.createEllipseROI(1000, 2000, 300, 400, ImagePlane.getPlane(3, 4));
		
		assertNotNull(RoiTools.createRandomRectangle(roi, roi.getBoundsWidth()/2, roi.getBoundsHeight()/2, 1000, true, rng));
		// Are smaller, but ROI won't fit
		assertNull(RoiTools.createRandomRectangle(roi, roi.getBoundsWidth(), roi.getBoundsHeight()/16, 1000, true, rng));
		assertEquals(roi.getImagePlane(), RoiTools.createRandomRectangle(roi, roi.getBoundsWidth()/2, roi.getBoundsHeight()/2, 1000, true, rng).getImagePlane());
		assertThrows(IllegalArgumentException.class, () -> RoiTools.createRandomRectangle(roi, roi.getBoundsWidth(), roi.getBoundsHeight(), 1000, true, rng));
		assertThrows(IllegalArgumentException.class, () -> RoiTools.createRandomRectangle(roi, roi.getBoundsWidth()+0.1, roi.getBoundsHeight(), 1000, true, rng));
		assertThrows(IllegalArgumentException.class, () -> RoiTools.createRandomRectangle(roi, roi.getBoundsWidth()+0.1, roi.getBoundsHeight()+1, 1000, true, rng));
		
		
	}
	
	
	

}