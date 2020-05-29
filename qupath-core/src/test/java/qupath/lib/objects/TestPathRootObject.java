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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import qupath.lib.objects.classes.PathClassFactory;


@SuppressWarnings("javadoc")
public class TestPathRootObject extends PathObjectTestWrapper {
	private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
	private final Integer nPO = 10;
	PathRootObject myPO = new PathRootObject();
	
	@BeforeEach
	public void setUpStreams() {
	    System.setErr(new PrintStream(errContent));
	}
	@AfterEach
	public void cleanUpStreams() {
	    System.setErr(null);
	}

	@Test
	public void test_BasicPO() {
		test_isRootObject(myPO, Boolean.TRUE);
		test_isPoint(myPO, Boolean.FALSE);
		test_isAnnotation(myPO, Boolean.FALSE);
		test_isDetection(myPO, Boolean.FALSE);
		test_isTMACore(myPO, Boolean.FALSE);
		test_isTile(myPO, Boolean.FALSE);
		test_isEditable(myPO, Boolean.FALSE);
		test_getParent(myPO, null); // root doesn't have parent
		test_getLevel(myPO, 0); // always 0 for root
		test_getROI(myPO, null); // root doesn't have ROI - why?
		test_hasROI(myPO, Boolean.FALSE); // same
	}
	@Test
	public void test_NamesAndColors() {
		test_toString(myPO, "Image"); 
		test_getDisplayedName(myPO, "Image"); // got it from default pathclass - is it not unclass???
		test_getName(myPO, null); // no name yet in PO
		test_setName(myPO, "myPO");
		test_getName(myPO, "myPO");
		test_getColorRGB(myPO, null); // no color yet
		test_setColorRGB(myPO, 10); // fictitious color
		test_getColorRGB(myPO, 10);
	}
	@Test
	public void test_MeasurementList() {
		test_hasMeasurements(myPO, Boolean.FALSE); // no measurements yet
		test_nMeasurements(myPO, 0); // no measurements yet
		test_getMeasurementList(myPO); // checks for instanceOf(MeasurementList.class)
		test_hasMeasurements(myPO, Boolean.FALSE); // creates an empty list with capacity of 16
		test_nMeasurements(myPO, 0); 
	}
	@Test
	public void test_AddingRemovingPO() { // maybe add some functionality to force no parenthood in roots???
		PathObject tPO = new PathRootObject(); // PO to add and remove
		test_objectCountPostfix(myPO, ""); // no children yet
		test_hasChildren(myPO, Boolean.FALSE); // no children yet
		test_nChildObjects(myPO, 0); // no children yet
		//test_addPathObject(myPO, tPO, 0); // added 1 child (of type root) - cannot be done
		test_hasChildren(myPO, Boolean.FALSE);
		test_nChildObjects(myPO, 0);
		test_getParent(tPO, null); 
		test_getLevel(tPO, 0);
		test_removePathObject(myPO, tPO, 0);
		test_hasChildren(myPO, Boolean.FALSE);
		test_nChildObjects(myPO, 0);
	}
	@Test
	public void test_AddingRemovingPOs() {
		List<PathObject> colPO = new ArrayList<>();
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
		test_getPathClass(myPO, PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT)); 
		//test_setPathClass(myPO, unclassErrMsg, errContent); // cannot be set
		test_getClassProbability(myPO, Double.NaN);
	}
}

