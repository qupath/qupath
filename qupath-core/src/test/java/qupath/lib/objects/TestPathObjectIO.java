package qupath.lib.objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import qupath.lib.common.ColorTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;


@SuppressWarnings("javadoc")
public class TestPathObjectIO extends PathObjectTestWrapper {
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
	
	Collection<PathObject> objs = Arrays.asList(myPDO, myPCO, myPAO, myPTO);
	
	@Test
	public void test_IOObjectsGeoJSON() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		// Add measurements
		mlDetection.addMeasurement("TestMeasurement1", 5.0);
		mlDetection.addMeasurement("TestMeasurement2", 10.0);
		mlCell.addMeasurement("TestMeasurement3", 15.0);
		mlCell.addMeasurement("TestMeasurement4", 20.0);
		
		// Export to GeoJSON
		PathObjectIO.exportObjectsToGeoJson(objs, bos, true, true);
		
		// Import from GeoJSON
		List<PathObject> col = new ArrayList<>(PathObjectIO.importObjectsFromGeoJson(new ByteArrayInputStream(bos.toByteArray())));
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0};
		for (PathObject po: col) {
			if (po == null)
				continue;

			// Test whether po has a ROI
			test_hasROI(po, Boolean.TRUE);
			
			if (po.isTile()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN));
				test_equalROIRegions(po.getROI(), roiTile);
				test_hasMeasurements(po, Boolean.FALSE);
				countCheck[0]++;
			} else if (po.isCell()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN));
				test_equalROIRegions(po.getROI(), roiCell1);
				test_equalROIRegions(((PathCellObject)po).getNucleusROI(), roiCell2);
				test_hasMeasurements(po, Boolean.TRUE);
				test_equalMeasurementListContent(po.getMeasurementList(), myPCO.getMeasurementList());
				countCheck[1]++;
			} else if (po.isDetection()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
				test_equalROIRegions(po.getROI(), roiDetection);
				test_hasMeasurements(po, Boolean.TRUE);
				test_equalMeasurementListContent(po.getMeasurementList(), myPDO.getMeasurementList());
				countCheck[2]++;
			} else if (po.isAnnotation()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
				test_equalROIRegions(po.getROI(), roiAnnotation);
				test_hasMeasurements(po, Boolean.FALSE);
				countCheck[3]++;
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1});
	}
	
	@Test
	public void test_IOObjectsSerialized() throws IOException, ClassNotFoundException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		// Add measurements
		mlDetection.addMeasurement("TestMeasurement1", 5.0);
		mlDetection.addMeasurement("TestMeasurement2", 10.0);
		mlCell.addMeasurement("TestMeasurement3", 15.0);
		mlCell.addMeasurement("TestMeasurement4", 20.0);
		
		// Serialize
		PathObjectIO.exportObjectsAsSerialized(objs, bos, true);
		
		// Deserialize
		List<PathObject> col = new ArrayList<>(PathObjectIO.importObjectsFromSerialized(bos.toByteArray()));
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0};
		for (PathObject po: col) {
			if (po == null)
				continue;

			// Test whether po has a ROI
			test_hasROI(po, Boolean.TRUE);
			
			if (po.isTile()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN));
				test_equalROIRegions(po.getROI(), roiTile);
				test_hasMeasurements(po, Boolean.FALSE);
				countCheck[0]++;
			} else if (po.isCell()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN));
				test_equalROIRegions(po.getROI(), roiCell1);
				test_equalROIRegions(((PathCellObject)po).getNucleusROI(), roiCell2);
				test_hasMeasurements(po, Boolean.TRUE);
				test_equalMeasurementListContent(po.getMeasurementList(), myPCO.getMeasurementList());
				countCheck[1]++;
			} else if (po.isDetection()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
				test_equalROIRegions(po.getROI(), roiDetection);
				test_hasMeasurements(po, Boolean.TRUE);
				test_equalMeasurementListContent(po.getMeasurementList(), myPDO.getMeasurementList());
				countCheck[2]++;
			} else if (po.isAnnotation()) {
				test_getPathClass(po, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
				test_equalROIRegions(po.getROI(), roiAnnotation);
				test_hasMeasurements(po, Boolean.FALSE);
				countCheck[3]++;
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1});
	}
}
