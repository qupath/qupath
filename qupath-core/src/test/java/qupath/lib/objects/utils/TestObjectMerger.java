/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.objects.utils;

import java.util.Comparator;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestObjectMerger {

    // Convenience method for defining the default ROI width and height
    private static final double objectSize = 10.0;

    /**
     * Test behavior when objects touch at a corner, but do not overlap.
     */
    @Test
    public void test_mergeTouchingButNotOverlapping() {
        var pathObjects = Arrays.asList(
                createAnnotation(0, 0, "A"),
                createAnnotation(objectSize, objectSize, "A"),
                createAnnotation(objectSize*2, objectSize*2, "B"),
                createAnnotation(objectSize*3, objectSize*3, "C"),
                createAnnotation(objectSize*4, objectSize*4, "A")
        );
        var mergedByClassification = ObjectMerger.createSharedClassificationMerger().merge(pathObjects);
        assertEquals(3, mergedByClassification.size());
        assertEquals(1, mergedByClassification.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("A"))).count());
        assertEquals(1, mergedByClassification.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("B"))).count());
        assertEquals(1, mergedByClassification.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("C"))).count());

        var mergedByBoundary = ObjectMerger.createSharedTileBoundaryMerger(0.5).merge(pathObjects);
        assertEquals(5, mergedByBoundary.size());
        assertEquals(3, mergedByBoundary.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("A"))).count());
        assertEquals(1, mergedByBoundary.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("B"))).count());
        assertEquals(1, mergedByBoundary.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("C"))).count());

        var mergedByTouching = ObjectMerger.createTouchingMerger().merge(pathObjects);
        assertEquals(4, mergedByTouching.size());
        assertEquals(2, mergedByTouching.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("A"))).count());
        assertEquals(1, mergedByTouching.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("B"))).count());
        assertEquals(1, mergedByTouching.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("C"))).count());
    }

    /**
     * Test behavior when objects do not overlap or touch at all.
     */
    @Test
    public void test_mergeNotTouching() {
        var pathObjects = Arrays.asList(
                createAnnotation(0, 0, "A"),
                createAnnotation(0, objectSize*2, "A"),
                createAnnotation(0, objectSize*4, "B"),
                createAnnotation(0, objectSize*6, "C"),
                createAnnotation(0, objectSize*8, "A")
        );
        var mergedByClassification = ObjectMerger.createSharedClassificationMerger().merge(pathObjects);
        assertEquals(3, mergedByClassification.size());
        assertEquals(1, mergedByClassification.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("A"))).count());
        assertEquals(1, mergedByClassification.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("B"))).count());
        assertEquals(1, mergedByClassification.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("C"))).count());

        var mergedByBoundary = ObjectMerger.createSharedTileBoundaryMerger(0.5).merge(pathObjects);
        assertEquals(5, mergedByBoundary.size());
        assertEquals(3, mergedByBoundary.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("A"))).count());
        assertEquals(1, mergedByBoundary.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("B"))).count());
        assertEquals(1, mergedByBoundary.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("C"))).count());

        var mergedByTouching = ObjectMerger.createTouchingMerger().merge(pathObjects);
        assertEquals(5, mergedByTouching.size());
        assertEquals(3, mergedByTouching.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("A"))).count());
        assertEquals(1, mergedByTouching.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("B"))).count());
        assertEquals(1, mergedByTouching.stream().filter(o -> o.getPathClass().equals(PathClass.fromString("C"))).count());
    }

    /**
     * Test behavior when objects (i.e. areas) overlap by 50%.
     */
    @Test
    public void test_mergeIntersecting() {
        var pathObjects = Arrays.asList(
                createAnnotation(0, 0, "A"),
                createAnnotation(objectSize/2, 0, "A")
        );
        var mergedByClassification = ObjectMerger.createSharedClassificationMerger().merge(pathObjects);
        assertEquals(1, mergedByClassification.size());
        assertEquals(objectSize * objectSize * 1.5, mergedByClassification.get(0).getROI().getArea(), 0.0001);

        var mergedByBoundary = ObjectMerger.createSharedTileBoundaryMerger(0.5).merge(pathObjects);
        assertEquals(2, mergedByBoundary.size());

        var mergedByTouching = ObjectMerger.createTouchingMerger().merge(pathObjects);
        assertEquals(2, mergedByTouching.size());
    }

    /**
     * Test behavior when a boundary overlaps fully.
     */
    @Test
    public void test_mergeBoundaryOverlappingFull() {
        var pathObjects = Arrays.asList(
                createAnnotation(0, 0, "A"),
                createAnnotation(objectSize, 0, "A")
        );
        var mergedByClassification = ObjectMerger.createSharedClassificationMerger().merge(pathObjects);
        assertEquals(1, mergedByClassification.size());
        assertEquals(objectSize * objectSize * 2, mergedByClassification.get(0).getROI().getArea(), 0.0001);

        var mergedByBoundary = ObjectMerger.createSharedTileBoundaryMerger(0.5).merge(pathObjects);
        assertEquals(1, mergedByBoundary.size());
        assertEquals(objectSize * objectSize * 2, mergedByBoundary.get(0).getROI().getArea(), 0.0001);

        var mergedByTouching = ObjectMerger.createTouchingMerger().merge(pathObjects);
        assertEquals(1, mergedByTouching.size());
        assertEquals(objectSize * objectSize * 2, mergedByTouching.get(0).getROI().getArea(), 0.0001);
    }

    /**
     * Test behavior when a boundary overlaps by 50%.
     */
    @Test
    public void test_mergeBoundaryOverlappingPartial() {
        var pathObjects = Arrays.asList(
                createAnnotation(0, 0, "A"),
                createAnnotation(objectSize, objectSize/2.0, "A")
        );
        var mergedByClassification = ObjectMerger.createSharedClassificationMerger().merge(pathObjects);
        assertEquals(1, mergedByClassification.size());
        assertEquals(objectSize * objectSize * 2, mergedByClassification.get(0).getROI().getArea(), 0.0001);

        var mergedByBoundary = ObjectMerger.createSharedTileBoundaryMerger(0.33).merge(pathObjects);
        assertEquals(1, mergedByBoundary.size());
        assertEquals(objectSize * objectSize * 2, mergedByBoundary.get(0).getROI().getArea(), 0.0001);

        // This changed when using IoU (using original criterion we'd merge)
        var mergedByBoundary2 = ObjectMerger.createSharedTileBoundaryMerger(0.5).merge(pathObjects);
        assertEquals(2, mergedByBoundary2.size());
        assertEquals(objectSize * objectSize, mergedByBoundary2.get(0).getROI().getArea(), 0.0001);

        var mergedByBoundaryTooHigh = ObjectMerger.createSharedTileBoundaryMerger(0.75).merge(pathObjects);
        assertEquals(2, mergedByBoundaryTooHigh.size());

        var mergedByBoundaryLower = ObjectMerger.createSharedTileBoundaryMerger(0.25).merge(pathObjects);
        assertEquals(1, mergedByBoundary.size());
        assertEquals(objectSize * objectSize * 2, mergedByBoundaryLower.get(0).getROI().getArea(), 0.0001);

        var mergedByTouching = ObjectMerger.createTouchingMerger().merge(pathObjects);
        assertEquals(1, mergedByTouching.size());
        assertEquals(objectSize * objectSize * 2, mergedByTouching.get(0).getROI().getArea(), 0.0001);
    }


    /**
     * Don't merge objects of different types.
     */
    @Test
    public void test_mergeBoundaryOverlappingNonMatchingTypes() {
        var pathObjects = Arrays.asList(
                createAnnotation(0, 0, "A"),
                createDetection(objectSize, 0, "A"),
                createTile(objectSize*2, 0, "A")
        );
        var mergedByClassification = ObjectMerger.createSharedClassificationMerger().merge(pathObjects);
        assertEquals(3, mergedByClassification.size());

        var mergedByBoundary = ObjectMerger.createSharedTileBoundaryMerger(0.5).merge(pathObjects);
        assertEquals(3, mergedByBoundary.size());

        var mergedByTouching = ObjectMerger.createTouchingMerger().merge(pathObjects);
        assertEquals(3, mergedByTouching.size());
    }

    @Test
    public void test_intesectionOverMinimum() {
        // Create objects with an IoM of 100%, 50%, 75%, and 25%
        var poBase = createRectangleObject(0, 0, 100, 100);
        var poIoM100 = createRectangleObject(0, 0, 100, 200);
        var poIoM50 = createRectangleObject(50, 0, 100, 200);
        var poIoM75 = createRectangleObject(25, 0, 100, 200);
        var poIoM25 = createRectangleObject(75, 0, 100, 200);
        var poIoM0 = createRectangleObject(100, 0, 100, 200);
        var poEmpty = createRectangleObject(50, 0, 0, 0);

        // Test for IoM of 0.5
        assertEquals(1, ObjectMerger.createIoMinMerger(0.5).merge(List.of(poBase, poIoM100)).size());
        assertEquals(1, ObjectMerger.createIoMinMerger(0.5).merge(List.of(poBase, poIoM75)).size());
        assertEquals(1, ObjectMerger.createIoMinMerger(0.5).merge(List.of(poBase, poIoM50)).size());
        assertEquals(2, ObjectMerger.createIoMinMerger(0.5).merge(List.of(poBase, poIoM25)).size());
        assertEquals(2, ObjectMerger.createIoMinMerger(0.5).merge(List.of(poBase, poIoM0)).size());

        // Empty object always retained
        assertEquals(2, ObjectMerger.createIoMinMerger(0.5).merge(List.of(poBase, poEmpty)).size());

        // Check close to the threshold
        assertEquals(2, ObjectMerger.createIoMinMerger(0.5 + 1e-6).merge(List.of(poBase, poIoM50)).size());
        assertEquals(1, ObjectMerger.createIoMinMerger(0.5 - 1e-6).merge(List.of(poBase, poIoM50)).size());
    }

    @Test
    public void test_merge_strategy_ignore() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.IGNORE).process(objects);
        assertEquals(1, merged.size());
        assertEquals(new HashMap<>(), merged.getFirst().getMeasurements());
    }

    @Test
    public void test_merge_strategy_use_first() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.USE_FIRST).process(objects);
        assertEquals(1, merged.size());
        assertEquals(objects.getFirst().getMeasurements(), merged.getFirst().getMeasurements());
    }

    @Test
    public void test_merge_strategy_mean() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.MEAN).process(objects);
        assertEquals(1, merged.size());
        assertEquals(
                objects.stream().mapToDouble(po -> po.getMeasurementList().get("Measurement")).sum() / objects.size(),
                (double)merged.getFirst().getMeasurements().get("Measurement"),
                0.0001
                );
    }

    @Test
    public void test_merge_strategy_weighted_mean() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.WEIGHTED_MEAN).process(objects);
        assertEquals(
                0.2625,
                (double)merged.getFirst().getMeasurements().get("Measurement"),
                0.0001
        );
    }

    @Test
    public void test_merge_strategy_median() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.MEDIAN).process(objects);
        assertEquals(
                0.2,
                (double)merged.getFirst().getMeasurements().get("Measurement"),
                0.0001
        );
    }

    @Test
    public void test_merge_strategy_max() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.MAX).process(objects);
        assertEquals(
                0.4,
                (double)merged.getFirst().getMeasurements().get("Measurement"),
                0.0001
        );
    }

    @Test
    public void test_merge_strategy_min() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.MIN).process(objects);
        assertEquals(
                0.1,
                (double)merged.getFirst().getMeasurements().get("Measurement"),
                0.0001
        );
    }

    @Test
    public void test_merge_strategy_random() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.RANDOM).process(objects);
        var mergedMeasurements = merged.getFirst().getMeasurements();
        var found = objects.stream().filter(pathObject -> {
            return mergedMeasurements.entrySet().stream().allMatch(es -> pathObject.getMeasurementList().get(es.getKey()) == es.getValue().doubleValue());
        }).toList();
        assertEquals(1, found.size());
    }

    @Test
    public void test_merge_strategy_use_biggest() {
        List<PathObject> objects = createMergableObjectsWithMeasurements();
        var merged = ObjectMerger.createIoMinMerger(0.5, MeasurementStrategy.USE_BIGGEST).process(objects);
        var largest = objects.get(2); // or objects.stream().max(Comparator.comparingDouble(po -> po.getROI().getArea())).get();
        var mergedMeasurements = merged.getFirst().getMeasurements();
        assert mergedMeasurements.entrySet().stream().allMatch(es -> largest.getMeasurementList().get(es.getKey()) == es.getValue().doubleValue());
    }

    private List<PathObject> createMergableObjectsWithMeasurements() {
        var po1 = createRectangleObject(0, 0, 100, 100);
        var po2 = createRectangleObject(0, 0, 100, 180);
        var po3 = createRectangleObject(10, 0, 100, 200);
        po1.getMeasurementList().put("Measurement", 0.1);
        po2.getMeasurementList().put("Measurement", 0.2);
        po3.getMeasurementList().put("Measurement", 0.4);
        return List.of(po1, po2, po3);
    }


    private static PathObject createRectangleObject(double x, double y, double width, double height) {
        return PathObjects.createDetectionObject(
                ROIs.createRectangleROI(x, y, width, height, ImagePlane.getDefaultPlane()));
    }


    private static PathObject createAnnotation(double x, double y, String classification) {
        return createAnnotation(x, y, classification, ImagePlane.getDefaultPlane());
    }

    private static PathObject createAnnotation(double x, double y, String classification, ImagePlane plane) {
        return PathObjects.createAnnotationObject(
                createROI(x, y, plane),
                PathClass.fromString(classification));
    }

    private static PathObject createDetection(double x, double y, String classification) {
        return createDetection(x, y, classification, ImagePlane.getDefaultPlane());
    }

    private static PathObject createDetection(double x, double y, String classification, ImagePlane plane) {
        return PathObjects.createDetectionObject(
                createROI(x, y, plane),
                PathClass.fromString(classification));
    }

    private static PathObject createTile(double x, double y, String classification) {
        return createTile(x, y, classification, ImagePlane.getDefaultPlane());
    }

    private static PathObject createTile(double x, double y, String classification, ImagePlane plane) {
        return PathObjects.createTileObject(
                createROI(x, y, plane),
                PathClass.fromString(classification));
    }

    private static ROI createROI(double x, double y, ImagePlane plane) {
        return ROIs.createRectangleROI(x, y, objectSize, objectSize, plane);
    }


}
