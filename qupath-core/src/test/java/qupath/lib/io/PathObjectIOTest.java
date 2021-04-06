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

package qupath.lib.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;
import qupath.lib.io.PathIO.GeoJsonExportOptions;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

@SuppressWarnings("javadoc")
public class PathObjectIOTest {
	
	/**
	 * Test if importing back exported objects are unchanged (GeoJSON).
	 * Objects tested:
	 * <li>Detection (<u>class</u>: PathClassTest1, has measurements)</li>
	 * <li>Annotation (<u>class</u>: PathClassTest1, <u>no</u> measurements)</li>
	 * <li>Cell (<u>class</u>: PathClassTest2, has measurements)</li>
	 * <li>Tile (<u>class</u>: PathClassTest2, <u>no</u> measurements)</li>
	 * <li>TMA (<u>class</u>: None, <u>no</u> measurements)</li>
	 * 
	 * @throws IOException
	 */
	@Test
	public void test_IOObjectsGeoJSON() throws IOException {
		test_IOObjectsGeoJSONImpl(true); 
		test_IOObjectsGeoJSONImpl(false, GeoJsonExportOptions.values()); 
	}

	
	
	private void test_IOObjectsGeoJSONImpl(boolean keepMeasurements, GeoJsonExportOptions... options) throws IOException {
		ROI roiDetection = ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane());
		ROI roiAnnotation = ROIs.createRectangleROI(100, 100, 10, 10, ImagePlane.getDefaultPlane());
		ROI roiCell1 = ROIs.createRectangleROI(25, 25, 25, 25, ImagePlane.getDefaultPlane());
		ROI roiCell2 = ROIs.createRectangleROI(12, 12, 5, 5, ImagePlane.getDefaultPlane());
		ROI roiTile = ROIs.createRectangleROI(100, 100, 10, 10, ImagePlane.getDefaultPlane());
		
		MeasurementList mlDetection = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		MeasurementList mlCell = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		
		PathObject myPDO = PathObjects.createDetectionObject(roiDetection, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK), mlDetection);
		PathObject myPAO = PathObjects.createAnnotationObject(roiAnnotation, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
		PathObject myPCO = PathObjects.createCellObject(roiCell1, roiCell2,	PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN), mlCell);
		PathObject myPTO = PathObjects.createTileObject(roiTile, PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN), null);
		PathObject myTMA = PathObjects.createTMACoreObject(25, 25, 25, false);
		
		Collection<PathObject> objs = Arrays.asList(myPDO, myPCO, myPAO, myPTO, myTMA);

		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		// Add measurements
		mlDetection.addMeasurement("TestMeasurement1", 5.0);
		mlDetection.addMeasurement("TestMeasurement2", 10.0);
		mlCell.addMeasurement("TestMeasurement3", 15.0);
		mlCell.addMeasurement("TestMeasurement4", 20.0);
		
		// Export to GeoJSON
		PathIO.exportObjectsAsGeoJSON(bos, objs, options);
		
		// Import from GeoJSON
		List<PathObject> objsBack = new ArrayList<>(PathIO.readObjectsFromGeoJSON(new ByteArrayInputStream(bos.toByteArray())));
		assertEquals(objs.size(), objsBack.size());
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0, 0};
		for (PathObject po: objsBack) {
			if (po == null)
				continue;

			// Test whether po has a ROI
			assertTrue(po.hasROI());
			
			if (po.isTile()) {
				assertEquals(po.getPathClass(), PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN));
				assertSameROIs(po.getROI(), roiTile);
				assertFalse(po.hasMeasurements());
				countCheck[0]++;
			} else if (po.isCell()) {
				assertEquals(po.getPathClass(), PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN));
				assertSameROIs(po.getROI(), roiCell1);
				assertSameROIs(((PathCellObject)po).getNucleusROI(), roiCell2);
				if (keepMeasurements) {
					assertTrue(po.hasMeasurements());
					assertSameMeasurements(po.getMeasurementList(), myPCO.getMeasurementList());
				} else
					assertFalse(po.hasMeasurements());
				countCheck[1]++;
			} else if (po.isDetection()) {
				assertEquals(po.getPathClass(), PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
				assertSameROIs(po.getROI(), roiDetection);
				if (keepMeasurements) {
					assertTrue(po.hasMeasurements());
					assertSameMeasurements(po.getMeasurementList(), myPDO.getMeasurementList());
				} else
					assertFalse(po.hasMeasurements());
				countCheck[2]++;
			} else if (po.isAnnotation()) {
				assertEquals(po.getPathClass(), PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
				assertSameROIs(po.getROI(), roiAnnotation);
				assertFalse(po.hasMeasurements());
				countCheck[3]++;
			} else if (po.isTMACore()) {
				assertFalse(po.hasMeasurements());
				assertSameROIs(po.getROI(), myTMA.getROI());
				countCheck[4]++;
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1, 1});
	}
	
	private static void assertSameMeasurements(MeasurementList ml1, MeasurementList ml2) {
		assertEquals(ml1.size(), ml2.size());
		assertEquals(ml1.getMeasurementNames(), ml2.getMeasurementNames());
		for (int i = 0; i < ml1.size(); i++) {
			double val1 = ml1.getMeasurementValue(i);
			double val2 = ml2.getMeasurementValue(i);
			if (Double.isNaN(val1))
				assertTrue(Double.isNaN(val2));
			else
				assertEquals(val1, val2, 1e-6);
		}
	}
	
	private static void assertSameROIs(ROI roi1, ROI roi2) {
		assertEquals(roi1.getImagePlane(), roi2.getImagePlane());
		assertEquals(roi1.getAllPoints(), roi2.getAllPoints());
	}
	
}
