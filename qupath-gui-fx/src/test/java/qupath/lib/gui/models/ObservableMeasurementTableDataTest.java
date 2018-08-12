/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.models;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.util.Collections;

import org.junit.Test;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.PathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

/**
 * Some tests for observable measurements.
 * 
 * @author Pete Bankhead
 *
 */
public class ObservableMeasurementTableDataTest {

	private static final double EPSILON = 1e-15;
	
	
	@Test
	public void test() {
		
		ImageData<BufferedImage> imageData = new ImageData<>(null);
		
		PathClass tumorClass = PathClassFactory.getDefaultPathClass(PathClasses.TUMOR);
		PathClass stromaClass = PathClassFactory.getDefaultPathClass(PathClasses.STROMA);
//		PathClass otherClass = PathClassFactory.getDefaultPathClass(PathClasses.OTHER);
		PathClass artefactClass = PathClassFactory.getDefaultPathClass(PathClasses.ARTIFACT);
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Add a parent annotation
		PathObject parent = new PathAnnotationObject(ROIs.createRectangleROI(500, 500, 1000, 1000, ImagePlane.getDefaultPlane()));
		
		
		// Create 100 tumor detections
		for (int i = 0; i < 100; i++) {
			if (i < 25)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getNegative(tumorClass, null)));
			else if (i < 50)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getOnePlus(tumorClass, null)));
			else if (i < 75)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getTwoPlus(tumorClass, null)));
			else if (i < 100)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getThreePlus(tumorClass, null)));
		}
		// Create 100 stroma detections
		for (int i = 0; i < 100; i++) {
			if (i < 50)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getNegative(stromaClass, null)));
			else if (i < 60)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getOnePlus(stromaClass, null)));
			else if (i < 70)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getTwoPlus(stromaClass, null)));
			else if (i < 100)
				parent.addPathObject(new PathDetectionObject(null, PathClassFactory.getThreePlus(stromaClass, null)));
		}
		// Create 50 artefact detections
		for (int i = 0; i < 50; i++) {
			parent.addPathObject(new PathDetectionObject(null, artefactClass));
		}
		
		hierarchy.addPathObject(parent, false);
		
		ObservableMeasurementTableData model = new ObservableMeasurementTableData();
		model.setImageData(imageData, Collections.singletonList(parent));
		
		
		// Check tumor counts
		assertEquals(100, model.getNumericValue(parent, "Num Tumor (base)"), EPSILON);
		assertEquals(25, model.getNumericValue(parent, "Num Tumor: Negative"), EPSILON);
		assertEquals(25, model.getNumericValue(parent, "Num Tumor: 1+"), EPSILON);
		assertEquals(25, model.getNumericValue(parent, "Num Tumor: 2+"), EPSILON);
		assertEquals(25, model.getNumericValue(parent, "Num Tumor: 3+"), EPSILON);
		assertTrue(Double.isNaN(model.getNumericValue(parent, "Num Tumor: 4+")));
		
		// Check tumor H-score, Allred score & positive %
		assertEquals(150, model.getNumericValue(parent, "Tumor: H-score"), EPSILON);
		assertEquals(75, model.getNumericValue(parent, "Tumor: Positive %"), EPSILON);
		assertEquals(2, model.getNumericValue(parent, "Tumor: Allred intensity"), EPSILON);
		assertEquals(5, model.getNumericValue(parent, "Tumor: Allred proportion"), EPSILON);
		assertEquals(7, model.getNumericValue(parent, "Tumor: Allred score"), EPSILON);

		// Check tumor H-score unaffected when tumor detections added without intensity classification
		for (int i = 0; i < 10; i++)
			parent.addPathObject(new PathDetectionObject(null, tumorClass));
		hierarchy.fireHierarchyChangedEvent(this);
		model.refreshEntries();
//		model.setImageData(imageData, Collections.singletonList(parent));
		assertEquals(100, model.getNumericValue(parent, "Num Stroma (base)"), EPSILON);
		assertEquals(50, model.getNumericValue(parent, "Num Stroma: Negative"), EPSILON);
		assertEquals(150, model.getNumericValue(parent, "Tumor: H-score"), EPSILON);
		assertEquals(75, model.getNumericValue(parent, "Tumor: Positive %"), EPSILON);
		
		// Check stroma scores
		assertEquals(100, model.getNumericValue(parent, "Num Stroma (base)"), EPSILON);
		assertEquals(120, model.getNumericValue(parent, "Stroma: H-score"), EPSILON);

		// Check complete scores
		assertEquals(135, model.getNumericValue(parent, "Stroma + Tumor: H-score"), EPSILON);
		
		// Add a new parent that completely contains the current object, and confirm complete scores agree
		PathObject parentNew = new PathAnnotationObject(ROIs.createRectangleROI(0, 0, 2000, 2000, ImagePlane.getDefaultPlane()));
		hierarchy.addPathObject(parentNew, false);
		model.refreshEntries();
		assertEquals(135, model.getNumericValue(parent, "Stroma + Tumor: H-score"), EPSILON);
		assertEquals(135, model.getNumericValue(parentNew, "Stroma + Tumor: H-score"), EPSILON);
		
		// Create a new object and demonstrate Allred dependence on a single cell
		PathObject parentAllred = new PathAnnotationObject(ROIs.createRectangleROI(4000, 4000, 1000, 1000, ImagePlane.getDefaultPlane()));
		for (int i = 0; i < 100; i++)
			parentAllred.addPathObject(new PathDetectionObject(null, PathClassFactory.getNegative(tumorClass, null)));
		hierarchy.addPathObject(parentAllred, false);
		model.refreshEntries();
		assertEquals(0, model.getNumericValue(parentAllred, "Tumor: Allred score"), EPSILON);
		parentAllred.addPathObject(new PathDetectionObject(null, PathClassFactory.getThreePlus(tumorClass, null)));
		model.refreshEntries();
		assertEquals(4, model.getNumericValue(parentAllred, "Tumor: Allred score"), EPSILON);
		
		
	}

}
