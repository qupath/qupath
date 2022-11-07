/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Streams;

import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

@SuppressWarnings("javadoc")
public class TestPathObjectTools extends TestPathObjectMethods { 
	
	@Test
	public void test_BasicPO() {
		
		// Check that duplicating objects works
		var rois = Arrays.asList(
				ROIs.createRectangleROI(1, 2, 100, 200, ImagePlane.getDefaultPlane()),
				ROIs.createRectangleROI(1, 2, 100, 200, ImagePlane.getPlane(1, 2))
				);
		
		var pathClasses = Arrays.asList(
				PathClass.fromString("First"),
				PathClass.getInstance("Second"),
				PathClass.fromString("Third: Fourth"),
				null
				);
		
		var pathObjects = new ArrayList<PathObject>();
		for (var r : rois) {
			for (var pc : pathClasses) {
				pathObjects.add(PathObjects.createAnnotationObject(r, pc));
				pathObjects.add(PathObjects.createDetectionObject(r, pc));
				pathObjects.add(PathObjects.createTileObject(r, pc, null));
			}
		}
		
		var duplicateObjectsNewIds = pathObjects.stream().map(p -> PathObjectTools.transformObject(p, null, true, true)).collect(Collectors.toList());
		var duplicateObjectsSameIds = pathObjects.stream().map(p -> PathObjectTools.transformObject(p, null, true, false)).collect(Collectors.toList());
		
		for (int i = 0; i < pathObjects.size(); i++) {
			
			var pathObject = pathObjects.get(i);
			var sameID = duplicateObjectsSameIds.get(i);
			var newID = duplicateObjectsNewIds.get(i);
			
			assertSame(pathObject, sameID, true);
			assertSame(pathObject, newID, false);

		}
		
		// Check the matching functions work as well
		Collections.shuffle(pathObjects);
		var mapNewIds = PathObjectTools.matchByID(pathObjects, duplicateObjectsNewIds);
		assertEquals(pathObjects.size(), mapNewIds.size());
		assertEquals(0L, mapNewIds.values().stream().filter(p -> p != null).count());

		var mapNewIds2 = PathObjectTools.findByUUID(pathObjects.stream().map(p -> p.getID()).collect(Collectors.toList()), duplicateObjectsNewIds);
		assertEquals(pathObjects.size(), mapNewIds2.size());
		assertEquals(0L, mapNewIds2.values().stream().filter(p -> p != null).count());

		var mapNewIds3 = PathObjectTools.findByStringID(pathObjects.stream().map(p -> p.getID().toString()).collect(Collectors.toList()), duplicateObjectsNewIds);
		assertEquals(pathObjects.size(), mapNewIds3.size());
		assertEquals(0L, mapNewIds3.values().stream().filter(p -> p != null).count());

		var mapSameIds = PathObjectTools.matchByID(pathObjects, duplicateObjectsSameIds);
		assertEquals(pathObjects.size(), mapSameIds.size());
		assertEquals(pathObjects.size(), mapSameIds.values().stream().filter(p -> p != null).count());
		
		var mapSameIds2 = PathObjectTools.findByUUID(pathObjects.stream().map(p -> p.getID()).collect(Collectors.toList()), duplicateObjectsSameIds);
		assertEquals(pathObjects.size(), mapSameIds2.size());
		assertEquals(pathObjects.size(), mapSameIds2.values().stream().filter(p -> p != null).count());

		var mapSameIds3 = PathObjectTools.findByStringID(pathObjects.stream().map(p -> p.getID().toString()).collect(Collectors.toList()), duplicateObjectsSameIds);
		assertEquals(pathObjects.size(), mapSameIds3.size());
		assertEquals(pathObjects.size(), mapSameIds3.values().stream().filter(p -> p != null).count());
	}
	
	
	private static void assertSame(PathObject p1, PathObject p2, boolean sameIDs) {
		assertEquals(p1.getClass(), p2.getClass());
		assertEquals(p1.getROI(), p2.getROI());
		assertEquals(p1.getPathClass(), p2.getPathClass());
		if (sameIDs) {
			assertEquals(p1.getID(), p2.getID());			
		} else {
			assertNotEquals(p1.getID(), p2.getID());			
		}
	}
	
	
	@Test
	public void testFindOutside() {
		for (int t = 0; t < 2; t++) {
			for (int z = 0; z < 2; z++) {
				var region = ImageRegion.createInstance(10, 20, 30, 40, z, t);
				
				// Create an ellipse that fills the region - but doens't go beyond it
				var roiInside = ROIs.createEllipseROI(region.getMinX(), region.getMinY(), region.getWidth(), region.getHeight(), ImagePlane.getPlane(z, t));
				var roiInside2 = roiInside.updatePlane(ImagePlane.getDefaultPlane());

				var roiOtherZ = roiInside.updatePlane(ImagePlane.getPlane(z+1, t));
				var roiOtherT = roiInside.updatePlane(ImagePlane.getPlane(z, t+1));
				
				var roiOverlap1 = roiInside.translate(5, 0);
				var roiOverlap2 = roiInside.translate(-5, 0);
				var roiOverlap3 = roiInside.translate(0, 5);
				var roiOverlap4 = roiInside.translate(0, -5);
				
				var roiOutside1 = roiInside.translate(100, 0);
				var roiOutside2 = roiInside.translate(-100, 0);
				var roiOutside3 = roiInside.translate(0, 100);
				var roiOutside4 = roiInside.translate(0, -100);
				
				var pathObjectsInside = createObjects(roiInside, roiInside2);
				var pathObjectsOverlaps = createObjects(roiOverlap1, roiOverlap2, roiOverlap3, roiOverlap4);
				var pathObjectsOutside = createObjects(roiOtherZ, roiOtherT, roiOutside1, roiOutside2, roiOutside3, roiOutside4);
				
				var allObjects = Streams.concat(pathObjectsInside.stream(), pathObjectsOverlaps.stream(), pathObjectsOutside.stream()).collect(Collectors.toSet());
				var outsideOrIntersects = Streams.concat(pathObjectsOverlaps.stream(), pathObjectsOutside.stream()).collect(Collectors.toSet());
				
				var foundOutside = PathObjectTools.findObjectsOutsideRegion(allObjects, region, 0, region.getZ()+1, 0, region.getT()+1, true);
//				System.err.println(foundOutside.size() + " / " + pathObjectsOutside.size());
				assertEquals(pathObjectsOutside, new HashSet<>(foundOutside));

				var foundOutsideStrict = PathObjectTools.findObjectsOutsideRegion(allObjects, region, 0, region.getZ()+1, 0, region.getT()+1, false);
//				System.err.println(foundOutsideStrict.size() + " / " + outsideOrIntersects.size());
				assertEquals(outsideOrIntersects, new HashSet<>(foundOutsideStrict));

			}			
		}
	}
	
	
	private static Set<PathObject> createObjects(ROI...rois) {
		return Arrays.asList(rois).stream().map(r -> PathObjects.createDetectionObject(r)).collect(Collectors.toSet());
	}
	
	
	
}
