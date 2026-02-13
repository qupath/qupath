/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.scripting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.analysis.features.ObjectMeasurements.ShapeFeatures;
import qupath.lib.io.PathIO.GeoJsonExportOptions;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SuppressWarnings("javadoc")
public class TestQP {
	
	@Test
	public void test_parseEnumOptions() {
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, null), new GeoJsonExportOptions[0]);
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, "EXCLUDE_MEASUREMENTS"),
				new GeoJsonExportOptions[] {GeoJsonExportOptions.EXCLUDE_MEASUREMENTS});
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, "EXCLUDE_MEASUREMENTS", "FEATURE_COLLECTION"),
				new GeoJsonExportOptions[] {GeoJsonExportOptions.EXCLUDE_MEASUREMENTS, GeoJsonExportOptions.FEATURE_COLLECTION});
		assertArrayEquals(QP.parseEnumOptions(GeoJsonExportOptions.class, null, "FEATURE_COLLECTION"),
				new GeoJsonExportOptions[] {GeoJsonExportOptions.FEATURE_COLLECTION});
		
		assertArrayEquals(QP.parseEnumOptions(ShapeFeatures.class, null), new ShapeFeatures[0]);
		assertArrayEquals(QP.parseEnumOptions(ShapeFeatures.class, "AREA"),
				new ShapeFeatures[] {ShapeFeatures.AREA});
		assertArrayEquals(QP.parseEnumOptions(ShapeFeatures.class, "AREA", "MAX_DIAMETER"),
				new ShapeFeatures[] {ShapeFeatures.AREA, ShapeFeatures.MAX_DIAMETER});
	}

	@Test
	void Check_Annotations_Not_Merged_If_Different_Image_Planes() {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		List<PathObject> annotations = List.of(
				PathObjects.createAnnotationObject(ROIs.createPointsROI(0, 1, ImagePlane.getPlane(3, 6))),
				PathObjects.createAnnotationObject(ROIs.createPointsROI(1, 2, ImagePlane.getPlane(5, 6)))
		);

		boolean annotationsMerged = QP.mergeAnnotations(hierarchy, annotations);

		Assertions.assertFalse(annotationsMerged);
	}

	@Test
	void Check_Annotations_Not_Merged_If_No_Area_Or_Point_Annotation() {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		List<PathObject> annotations = List.of(
				PathObjects.createDetectionObject(ROIs.createPointsROI(0, 1)), // not taken into account because detection
				PathObjects.createAnnotationObject(ROIs.createLineROI(0, 1, 2, 3))// not taken into account
		);																						// because not area or point

		boolean annotationsMerged = QP.mergeAnnotations(hierarchy, annotations);

		Assertions.assertFalse(annotationsMerged);
	}

	@Test
	void Check_Merged_Annotation_Is_Selected() {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		List<PathObject> annotations = List.of(
				PathObjects.createAnnotationObject(ROIs.createPointsROI(0, 1), PathClass.fromString("Class 1")),
				PathObjects.createAnnotationObject(ROIs.createPointsROI(1, 2), PathClass.fromString("Class 2"))
		);

		QP.mergeAnnotations(hierarchy, annotations);

		Assertions.assertNotNull(hierarchy.getSelectionModel().getSelectedObject());
	}

	@Test
	void Check_Merged_Annotations_Are_Removed_From_Hierarchy() {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		List<PathObject> annotations = List.of(
				PathObjects.createAnnotationObject(ROIs.createPointsROI(0, 1)),
				PathObjects.createAnnotationObject(ROIs.createPointsROI(1, 2)),
				PathObjects.createAnnotationObject(ROIs.createPointsROI(2, 3)),
				PathObjects.createDetectionObject(ROIs.createPointsROI(0, 1)),
				PathObjects.createAnnotationObject(ROIs.createLineROI(0, 1, 2, 3))
		);
		hierarchy.addObjects(annotations);
		// 1 for created annotation + 2 for annotations not used during merge (last two objects in list above)
		int expectedNumberOfAnnotationsInHierarchy = 3;

		QP.mergeAnnotations(hierarchy, annotations);

		Assertions.assertEquals(expectedNumberOfAnnotationsInHierarchy, hierarchy.getAllObjects(false).size());
	}

	@Test
	void Check_PathClass_Of_Merged_Annotation_When_Different_Classes_Provided() {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		List<PathObject> annotations = List.of(
				PathObjects.createAnnotationObject(ROIs.createPointsROI(0, 1), PathClass.fromString("Class 1")),
				PathObjects.createAnnotationObject(ROIs.createPointsROI(1, 2), PathClass.fromString("Class 2"))
		);

		QP.mergeAnnotations(hierarchy, annotations);

		Assertions.assertNull(hierarchy.getSelectionModel().getSelectedObject().getPathClass());
	}

	@Test
	void Check_PathClass_Of_Merged_Annotation_When_Same_Class_Provided() {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		PathClass expectedClass = PathClass.fromString("Class 1");
		List<PathObject> annotations = List.of(
				PathObjects.createAnnotationObject(ROIs.createPointsROI(0, 1), expectedClass),
				PathObjects.createAnnotationObject(ROIs.createPointsROI(1, 2), expectedClass)
		);

		QP.mergeAnnotations(hierarchy, annotations);

		Assertions.assertEquals(expectedClass, hierarchy.getSelectionModel().getSelectedObject().getPathClass());
	}
}
