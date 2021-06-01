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

package qupath.lib.objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.HashSet;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

class PathObjectTestWrapper {
	private final Double epsilon = 1e-15; // error for double comparison
	
	//@Test
	public void test_getParent(PathObject myPO, PathObject parent) {
		assertEquals(myPO.getParent(), parent);
	}
	//@Test
	public void test_getLevel(PathObject myPO, Integer level) {
		assertEquals((Integer)myPO.getLevel(), level);
	}
	//@Test
	public void test_isRootObject(PathObject myPO, Boolean isroot) {
		assertEquals(myPO.isRootObject(), isroot);
	}
	//@Test
	public void test_isPoint(PathObject myPO, Boolean ispoint) {
		assertEquals(PathObjectTools.hasPointROI(myPO), ispoint);
	}
	//@Test
	public void test_getMeasurementList(PathObject myPO) {
		MeasurementList myPOML = myPO.getMeasurementList();
		assertTrue(myPOML instanceof MeasurementList);
	}
	//@Test
	public void test_getMeasurementList(PathObject myPO, MeasurementList ML) {
		MeasurementList myPOML = myPO.getMeasurementList();
		assertEquals(myPOML, ML);
	}
	//@Test
	public void test_equalMeasurementListContent(MeasurementList ML, MeasurementList ML2) {
		var keys = ML.getMeasurementNames();
		assertArrayEquals(keys.toArray(), ML2.getMeasurementNames().toArray());
		for (String name: keys) {
			assertEquals(ML.getMeasurementValue(name), ML2.getMeasurementValue(name));
		}
	}
	//@Test
	public void test_nMeasurements(PathObject myPO, Integer nmeasurements) {
		assertEquals((Integer)myPO.getMeasurementList().size(), nmeasurements);
	}
	//@Test
	public void test_objectCountPostfix(PathObject myPO, String objectcount) {
		assertEquals(myPO.objectCountPostfix(), objectcount);
	}
	//@Test
	public void test_toString(PathObject myPO, String tostring) {
		assertEquals(myPO.toString(), tostring); 
	}
	//@Test
	public void test_addPathObject(PathObject myPO, PathObject tPO, Integer nchildren) {
		myPO.addPathObject(tPO);
		assertEquals((Integer)myPO.nChildObjects(), nchildren);
	}
	//@Test
	public void test_addPathObjects(PathObject myPO, Collection<PathObject> colPO, Integer nchildren) {
		myPO.addPathObjects(colPO);
		assertEquals((Integer)myPO.nChildObjects(), nchildren);
	}
	//@Test
	public void test_removePathObject(PathObject myPO, PathObject tPO, Integer nchildren) {
		myPO.removePathObject(tPO);
		assertEquals((Integer)myPO.nChildObjects(), nchildren);
	}
	//@Test
	public void test_removePathObjects(PathObject myPO, Collection<PathObject> colPO, Integer nchildren) {
		myPO.removePathObjects(colPO);
		assertEquals((Integer)myPO.nChildObjects(), nchildren);
	}
	//@Test
	public void test_clearPathObjects(PathObject myPO, Integer nchildren) {
		myPO.clearPathObjects();
		assertEquals((Integer)myPO.nChildObjects(), nchildren);
	}
	//@Test
	public void test_nChildObjects(PathObject myPO, Integer nchildren) {
		assertEquals((Integer)myPO.nChildObjects(), nchildren);
	}
	//@Test
	public void test_hasChildren(PathObject myPO, Boolean haschildren) {
		assertEquals(myPO.nChildObjects()!=0?Boolean.TRUE:Boolean.FALSE, haschildren);
	}
	//@Test
	public void test_hasROI(PathObject myPO, Boolean hasroi) {
		assertEquals(myPO.getROI()!=null?Boolean.TRUE:Boolean.FALSE, hasroi);
	}
	//@Test
	public void test_isAnnotation(PathObject myPO, Boolean isannotation) {
		assertEquals(myPO.isAnnotation(), isannotation); 
	}
	//@Test
	public void test_isDetection(PathObject myPO, Boolean isdetection) {
		assertEquals(myPO.isDetection(), isdetection);
	}
	//@Test
	public void test_hasMeasurements(PathObject myPO, Boolean hasmeasurements) {
		assertEquals(myPO.getMeasurementList().size()!=0?Boolean.TRUE:Boolean.FALSE, hasmeasurements);
	}
	//@Test
	public void test_isTMACore(PathObject myPO, Boolean istmacore) {
		assertEquals(myPO.isTMACore(), istmacore);
	}
	//@Test
	public void test_isTile(PathObject myPO, Boolean istile) {
		assertEquals(myPO.isTile(), istile);
	}
	//@Test
	public void test_isEditable(PathObject myPO, Boolean iseditable) {
		assertEquals(myPO.isEditable(), iseditable);
	}
	
	/**
	 * This tests the child objects have the same elements, but ignores order.
	 * @param myPO
	 * @param listPO
	 */
	//@Test
	public void test_comparePathObjectListContents(PathObject myPO, Collection<PathObject> listPO) {
		assertEquals(new HashSet<>(myPO.getChildObjects()), new HashSet<>(listPO));
//		assertEquals(myPO.getChildObjects(), listPO);
	}	
	//@Test
	public void test_getPathClass(PathObject myPO, PathClass PC) {
		assertEquals(myPO.getPathClass(), PC);	
	}
	//@Test
	public void test_setPathClass(PathObject myPO, String ErrMsg, ByteArrayOutputStream errContent) {
		myPO.setPathClass(null);
		assertEquals(ErrMsg, errContent.toString());
	}
	//@Test
	public void test_setPathClass(PathObject myPO, PathClass PC) {
		myPO.setPathClass(PC);
		assertEquals(myPO.getPathClass(), PC);	
	}
	//@Test
	public void test_setPathClass(PathObject myPO, PathClass PC, Double prob) {
		myPO.setPathClass(PC, prob);
		assertEquals(myPO.getPathClass(), PC);
		assertEquals((Double)myPO.getClassProbability(), prob);
	}
	//@Test
	public void test_getClassProbability(PathObject myPO, Double classprob) {
		assertEquals(myPO.getClassProbability(), classprob, epsilon);		
	}
	//@Test
	public void test_getDisplayedName(PathObject myPO, String dispname) {
		assertEquals(myPO.getDisplayedName(), dispname);
	}
	//@Test
	public void test_getName(PathObject myPO, String name) {
		assertEquals(myPO.getName(), name);
	}
	//@Test
	public void test_setName(PathObject myPO, String name) {
		myPO.setName(name);
		assertEquals(myPO.getName(), name);
	}
	//@Test
	public void test_getROI(PathObject myPO, ROI roi) {
		assertEquals(myPO.getROI(), roi); 
	}
	//@Test
	public void test_equalROIRegions(ROI roi, ROI roi2) {
		assertEquals(roi.getAllPoints(), roi2.getAllPoints());
		assertEquals(roi.getImagePlane(), roi.getImagePlane());
	}
	//@Test
	public void test_getColorRGB(PathObject myPO, Integer colorrgb) {
		assertEquals(myPO.getColorRGB(), colorrgb);
	}
	//@Test
	public void test_setColorRGB(PathObject myPO, Integer colorrgb) {
		myPO.setColorRGB(colorrgb);
		assertEquals((Integer)myPO.getColorRGB(), colorrgb);
	}
}
