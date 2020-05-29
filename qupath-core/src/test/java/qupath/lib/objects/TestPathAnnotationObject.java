/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import qupath.lib.measurements.MeasurementFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

@SuppressWarnings("javadoc")
public class TestPathAnnotationObject extends PathObjectTestWrapper { 
	private final Integer nPO = 10; // number of (child) objects to be added
	private final Double classprobability = 0.5;
	private final Double line_x = 0.0;
	private final Double line_y = 5.0;
	private final String nameML = "JJJ";
	private final Double valueML = 10.0;
	//private final double epsilon = 1e-15; 
	ROI myROI = ROIs.createLineROI(line_x,line_y, ImagePlane.getDefaultPlane());
	PathClass myPC = PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT);
	PathAnnotationObject myPO = new PathAnnotationObject();
	PathAnnotationObject myPO2 = new PathAnnotationObject(myROI);
	PathAnnotationObject myPO3 = new PathAnnotationObject(myROI, myPC);
	
	@Test
	public void test_BasicPO() {
		test_isRootObject(myPO, Boolean.FALSE);
		test_isPoint(myPO, Boolean.FALSE);
		test_isAnnotation(myPO, Boolean.TRUE);
		test_isDetection(myPO, Boolean.FALSE);
		test_isTMACore(myPO, Boolean.FALSE);
		test_isTile(myPO, Boolean.FALSE);
		test_isEditable(myPO, Boolean.TRUE); // it can be moved by default 
		myPO.setLocked(true); // lockROI=true locks a ROI and makes it not editable 
		test_isEditable(myPO, Boolean.FALSE);
		myPO.setLocked(false);
		test_isEditable(myPO, Boolean.TRUE);
		test_getParent(myPO, null); 
		test_getLevel(myPO, 0);
		test_getROI(myPO, null); 
		test_hasROI(myPO, Boolean.FALSE); 
		test_getROI(myPO2, myROI); 
		test_hasROI(myPO2, Boolean.TRUE); 
	}
	@Test
	public void test_NamesAndColors() {
//		test_toString(myPO, "Unassigned"); 
		test_getDisplayedName(myPO, myPO.getClass().getSimpleName()); // name of the class by default 
		test_getName(myPO, null); // no name yet in PO
		test_setName(myPO, "myPO");
		test_getName(myPO, "myPO");
		test_getColorRGB(myPO, null); // no color yet
		test_setColorRGB(myPO, 10); // fictitious color
		test_getColorRGB(myPO, 10);
	}
	@Test
	public void test_MeasurementList() {
		MeasurementList tML = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		tML.putMeasurement(MeasurementFactory.createMeasurement(nameML, valueML));
		PathAnnotationObject tPO = new PathAnnotationObject(myROI, myPC, tML);
		test_hasMeasurements(myPO, Boolean.FALSE); // no measurements
		test_nMeasurements(myPO, 0); // no measurements
		test_getMeasurementList(myPO); // checks for instanceOf(MeasurementList.class)
		test_hasMeasurements(myPO, Boolean.FALSE); 
		test_nMeasurements(myPO, 0);
		test_hasMeasurements(tPO, Boolean.TRUE); // there is 1 measurement
		test_nMeasurements(tPO, 1); // there is 1 measurement
		test_getMeasurementList(tPO, tML);
	}
	@Test
	public void test_AddingRemovingPO() {
		PathObject tPO = new PathAnnotationObject(); // PO to add and remove
		test_objectCountPostfix(myPO, ""); // no children yet
		test_hasChildren(myPO, Boolean.FALSE); // no children yet
		test_nChildObjects(myPO, 0); // no children yet
		test_addPathObject(myPO, tPO, 1); // added 1 child (of type root)
		test_hasChildren(myPO, Boolean.TRUE);
		test_nChildObjects(myPO, 1);
		test_getParent(tPO, myPO); 
		test_getLevel(tPO, 1);
		test_removePathObject(myPO, tPO, 0);
		test_hasChildren(myPO, Boolean.FALSE);
		test_nChildObjects(myPO, 0);
	}
	@Test
	public void test_AddingRemovingPOs() {
		Collection<PathObject> colPO = new ArrayList<>();
		for (int i = 0; i < nPO; ++i) 
			colPO.add(new PathRootObject());
		test_nChildObjects(myPO, 0);
		test_comparePathObjectListContents(myPO, Collections.emptyList()); // no children yet
		test_addPathObjects(myPO, colPO, nPO);
		test_comparePathObjectListContents(myPO, colPO);
		test_removePathObjects(myPO, colPO, 0);
		test_comparePathObjectListContents(myPO, Collections.emptyList());
		test_addPathObjects(myPO, colPO, nPO);
		test_clearPathObjects(myPO, 0); 
	}
	@Test
	public void test_SetGetPathClass() {
		test_getPathClass(myPO, null); 
		test_setPathClass(myPO, PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT));
		test_getClassProbability(myPO, Double.NaN);
		test_setPathClass(myPO, PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT), classprobability);
		test_getClassProbability(myPO, classprobability);
		test_getPathClass(myPO3, PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT));
		test_getClassProbability(myPO3, Double.NaN);
	}
/*	@Test
	public void test_serialization() {
		PathAnnotationObject myPO = new PathAnnotationObject();
		byte[] s_myPO = SerializationUtils.serialize(myPO);
		PathAnnotationObject d_myPO = (PathAnnotationObject) SerializationUtils.deserialize(s_myPO);
		//assertEquals(myPO, d_myPO); // equals needs to be created
		assertEquals(myPO.getClass(), d_myPO.getClass());
		assertEquals(myPO.getClassProbability(), d_myPO.getClassProbability(), epsilon);
		assertEquals(myPO.getColorRGB(), d_myPO.getColorRGB());
		assertEquals(myPO.getDisplayedName(), d_myPO.getDisplayedName());
		assertEquals(myPO.getLevel(), d_myPO.getLevel());
		assertEquals(myPO.getLockROI(), d_myPO.getLockROI());
		//assertEquals(myPO.getMeasurementList(), d_myPO.getMeasurementList());
		
	}*/
}
