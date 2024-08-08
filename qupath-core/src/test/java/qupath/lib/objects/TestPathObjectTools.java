/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022-2024 QuPath developers, The University of Edinburgh
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

import com.google.common.collect.Streams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import qupath.lib.geom.Point2;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("javadoc")
public class TestPathObjectTools extends TestPathObjectMethods {

	private static List<PathObject> duplicateObjectsNewIds;
	private static List<PathObject> duplicateObjectsSameIds;
	private static final List<ROI> rectangleROIs = Arrays.asList(
			ROIs.createRectangleROI(1, 2, 100, 200, ImagePlane.getDefaultPlane()),
			ROIs.createRectangleROI(1, 2, 100, 200, ImagePlane.getPlane(1, 2))
			);
	private static final List<PathClass> pathClasses = Arrays.asList(
			PathClass.fromString("First"),
			PathClass.getInstance("Second"),
			PathClass.fromString("Third: Fourth"),
			null
	);
	private static final ArrayList<PathObject> pathObjects = new ArrayList<>();
	private final ROI roiRect = ROIs.createRectangleROI(1, 2, 100, 200, ImagePlane.getDefaultPlane());
	private final ROI roiEllipse = ROIs.createEllipseROI(1, 2, 100, 200, ImagePlane.getDefaultPlane());
	private final PolygonROI roiDiamond = ROIs.createPolygonROI(List.of(
			new Point2(roiRect.getBoundsX(), roiRect.getCentroidY()),
			new Point2(roiRect.getCentroidX(), roiRect.getBoundsY()),
			new Point2(roiRect.getBoundsX()+ roiRect.getBoundsWidth(), roiRect.getCentroidY()),
			new Point2(roiRect.getBoundsX(), roiRect.getBoundsY()+ roiRect.getBoundsHeight())
	), ImagePlane.getDefaultPlane());;
	private final ROI roiOverlaps = roiRect.translate(10, 0);
	private final ROI roiOverlaps2 = roiRect.translate(0, 10);
	private final ROI roiSeparate = roiRect.translate(1000, 1000);
	private final List<ROI> allRois = List.of(roiRect, roiEllipse, roiDiamond, roiOverlaps, roiOverlaps2, roiSeparate);
	private final List<PathObject> pathObjectsAllROIs = allRois
			.stream()
			.map(PathObjects::createDetectionObject)
			.toList();

	@BeforeAll
	public static void init() {
		for (var r : rectangleROIs) {
			for (var pc : pathClasses) {
				pathObjects.add(PathObjects.createAnnotationObject(r, pc));
				pathObjects.add(PathObjects.createDetectionObject(r, pc));
				pathObjects.add(PathObjects.createTileObject(r, pc, null));
			}
		}

		duplicateObjectsNewIds = pathObjects.stream().map(p -> PathObjectTools.transformObject(p, null, true, true)).toList();
		duplicateObjectsSameIds = pathObjects.stream().map(p -> PathObjectTools.transformObject(p, null, true, false)).toList();
	}

	@Test
	public void test_duplicating_PO() {
		for (int i = 0; i < pathObjects.size(); i++) {
			var pathObject = pathObjects.get(i);
			var sameID = duplicateObjectsSameIds.get(i);
			var newID = duplicateObjectsNewIds.get(i);

			assertSame(pathObject, sameID, true);
			assertSame(pathObject, newID, false);
		}
	}

	@Test
	public void test_match_by_ID_newID() {
		// Check the matching functions work as well
		Collections.shuffle(pathObjects);
		var mapNewIds = PathObjectTools.matchByID(pathObjects, duplicateObjectsNewIds);
		assertEquals(pathObjects.size(), mapNewIds.size());
		assertEquals(0L, mapNewIds.values().stream().filter(Objects::nonNull).count());
	}

	@Test
	public void test_find_by_UUID_newID() {
		var mapNewIds2 = PathObjectTools.findByUUID(pathObjects.stream().map(PathObject::getID).toList(), duplicateObjectsNewIds);
		assertEquals(pathObjects.size(), mapNewIds2.size());
		assertEquals(0L, mapNewIds2.values().stream().filter(Objects::nonNull).count());
	}

	@Test
	public void test_find_by_String_ID_newID() {

		var mapNewIds3 = PathObjectTools.findByStringID(pathObjects.stream().map(p -> p.getID().toString()).toList(), duplicateObjectsNewIds);
		assertEquals(pathObjects.size(), mapNewIds3.size());
		assertEquals(0L, mapNewIds3.values().stream().filter(Objects::nonNull).count());
	}

	@Test
	public void test_match_by_ID_sameID() {
		var mapSameIds = PathObjectTools.matchByID(pathObjects, duplicateObjectsSameIds);
		assertEquals(pathObjects.size(), mapSameIds.size());
		assertEquals(pathObjects.size(), mapSameIds.values().stream().filter(Objects::nonNull).count());
	}

	@Test
	public void test_find_by_UUID_sameID() {
		var mapSameIds2 = PathObjectTools.findByUUID(pathObjects.stream().map(PathObject::getID).toList(), duplicateObjectsSameIds);
		assertEquals(pathObjects.size(), mapSameIds2.size());
		assertEquals(pathObjects.size(), mapSameIds2.values().stream().filter(Objects::nonNull).count());
	}

	@Test
	public void test_find_by_string_ID_sameID() {
		var mapSameIds3 = PathObjectTools.findByStringID(pathObjects.stream().map(p -> p.getID().toString()).toList(), duplicateObjectsSameIds);
		assertEquals(pathObjects.size(), mapSameIds3.size());
		assertEquals(pathObjects.size(), mapSameIds3.values().stream().filter(Objects::nonNull).count());
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
		return Arrays.stream(rois).map(PathObjects::createDetectionObject).collect(Collectors.toSet());
	}




	@Test
	public void test_filter_by_roi_covers_others() {

		// Note that we can test list equality since we don't expect any reordering

		// Test covers
		assertEquals(List.of(roiRect, roiEllipse, roiDiamond),
				PathObjectTools.filterByROICovers(roiRect, pathObjectsAllROIs).stream().map(PathObject::getROI).toList());
	}

	@Test
	public void test_filter_by_roi_covers_self() {
		// Every other ROI only completely covers itself
		for (var roi : List.of(roiEllipse, roiDiamond, roiOverlaps, roiOverlaps2, roiSeparate)) {
			assertEquals(List.of(roi),
					PathObjectTools.filterByROICovers(roi, pathObjectsAllROIs).stream().map(PathObject::getROI).toList());
		}
	}

	@Test
	public void test_filter_by_roi_intersects_disjoint_separate() {
		// Test intersects
		// One ROI is separate, all other intersect
		assertEquals(List.of(roiSeparate),
				PathObjectTools.filterByROIIntersects(roiSeparate, pathObjectsAllROIs).stream().map(PathObject::getROI).toList());
	}

	@Test
	public void test_filter_by_roi_intersects_notseparate() {
		for (var roi : List.of(roiRect, roiEllipse, roiDiamond, roiOverlaps, roiOverlaps2)) {
			assertEquals(List.of(roiRect, roiEllipse, roiDiamond, roiOverlaps, roiOverlaps2),
					PathObjectTools.filterByROIIntersects(roi, pathObjectsAllROIs).stream().map(PathObject::getROI).toList());
		}
	}

	@Test
	public void test_filter_by_roi_contains_centroid_separate() {
		// Test contains centroid
		// One ROI is separate, all other intersect
		assertEquals(List.of(roiSeparate),
				PathObjectTools.filterByROIContainsCentroid(roiSeparate, pathObjectsAllROIs).stream().map(PathObject::getROI).toList());
	}

	@Test
	public void test_filter_by_roi_contains_centroid_notseparate() {
		for (var roi : List.of(roiRect, roiEllipse, roiDiamond, roiOverlaps, roiOverlaps2)) {
			assertEquals(List.of(roiRect, roiEllipse, roiDiamond, roiOverlaps, roiOverlaps2),
					PathObjectTools.filterByROIContainsCentroid(roi, pathObjectsAllROIs).stream().map(PathObject::getROI).toList());
		}
	}
}
