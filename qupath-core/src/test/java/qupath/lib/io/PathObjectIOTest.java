package qupath.lib.io;

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
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectIO;
import qupath.lib.objects.PathObjectTestWrapper;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;


@SuppressWarnings("javadoc")
public class PathObjectIOTest extends PathObjectTestWrapper {
	private static final ROI roiDetection = ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane());
	private static final ROI roiAnnotation = ROIs.createRectangleROI(100, 100, 10, 10, ImagePlane.getDefaultPlane());
	private static final ROI roiCell1 = ROIs.createRectangleROI(25, 25, 25, 25, ImagePlane.getDefaultPlane());
	private static final ROI roiCell2 = ROIs.createRectangleROI(12, 12, 5, 5, ImagePlane.getDefaultPlane());
	private static final ROI roiTile = ROIs.createRectangleROI(100, 100, 10, 10, ImagePlane.getDefaultPlane());
	
	private static final MeasurementList mlDetection = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
	private static final MeasurementList mlCell = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
	
	private static final PathObject myPDO = PathObjects.createDetectionObject(roiDetection, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK), mlDetection);
	private static final PathObject myPAO = PathObjects.createAnnotationObject(roiAnnotation, PathClassFactory.getPathClass("PathClassTest1", ColorTools.BLACK));
	private static final PathObject myPCO = PathObjects.createCellObject(roiCell1, roiCell2,	PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN), mlCell);
	private static final PathObject myPTO = PathObjects.createTileObject(roiTile, PathClassFactory.getPathClass("PathClassTest2", ColorTools.GREEN), null);
	private static final PathObject myTMA = PathObjects.createTMACoreObject(25, 25, 25, false);
	
	private static final Collection<ROI> rois = Arrays.asList(roiDetection, roiAnnotation, roiCell1, roiCell2, roiTile);
	private static final Collection<PathObject> objs = Arrays.asList(myPDO, myPCO, myPAO, myPTO, myTMA);
	
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
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		// Add measurements
		mlDetection.addMeasurement("TestMeasurement1", 5.0);
		mlDetection.addMeasurement("TestMeasurement2", 10.0);
		mlCell.addMeasurement("TestMeasurement3", 15.0);
		mlCell.addMeasurement("TestMeasurement4", 20.0);
		
		// Export to GeoJSON
		PathObjectIO.exportObjectsToGeoJson(objs, bos, true, true);
		
		// Import from GeoJSON
		List<PathObject> objsBack = new ArrayList<>(PathObjectIO.importObjectsFromGeoJson(new ByteArrayInputStream(bos.toByteArray())));
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0, 0};
		for (PathObject po: objsBack) {
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
			} else if (po.isTMACore()) {
				test_hasMeasurements(po, Boolean.FALSE);
				test_equalROIRegions(po.getROI(), myTMA.getROI());
				countCheck[4]++;
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1, 1});
	}

	/**
	 * Test if importing back exported objects are unchanged (serialized).
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
		List<PathObject> objsBack = new ArrayList<>(PathObjectIO.importObjectsFromSerialized(bos.toByteArray()));
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0, 0};
		for (PathObject po: objsBack) {
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
			} else if (po.isTMACore()) {
				test_hasMeasurements(po, Boolean.FALSE);
				test_equalROIRegions(po.getROI(), myTMA.getROI());
				countCheck[4]++;
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1, 1});
	}
	
	/**
	 * Test if all imported back ROIs are unchanged (GeoJSON).
	 * 
	 * @throws IOException
	 */
	@Test
	public void test_IOROIGeoJSON() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		// Export to GeoJSON
		PathObjectIO.exportROIsToGeoJson(rois, bos, false);
		
		// Deserialize
		List<ROI> roisBack = PathObjectIO.importROIsFromGeoJson(new ByteArrayInputStream(bos.toByteArray()));
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0, 0};
		int index = 0;
		for (ROI roiBack: roisBack) {
			if (roiBack == null)
				continue;
			
			for (ROI roi: rois) {
				boolean samePoints = roiBack.getAllPoints().containsAll(roi.getAllPoints()) &&
						roi.getAllPoints().containsAll(roiBack.getAllPoints());
				boolean samePlane = roiBack.getImagePlane() == roi.getImagePlane();
				if (samePoints && samePlane) {
					countCheck[index++]++;
					break;
				}
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1, 1});
	}
	
	/**
	 * Test if all imported back ROIs are unchanged (Serialized).
	 * 
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@Test
	public void test_IOROISerialized() throws IOException, ClassNotFoundException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		// Export to GeoJSON
		PathObjectIO.exportROIsAsSerialized(rois, bos);
		
		// Deserialize
		List<ROI> roisBack = PathObjectIO.importROIsFromSerialized(new ByteArrayInputStream(bos.toByteArray()));
		
		// Array to count number of each PathObject type
		int[] countCheck = new int[] {0, 0, 0, 0, 0};
		int index = 0;
		for (ROI roiBack: roisBack) {
			if (roiBack == null)
				continue;
			
			for (ROI roi: rois) {
				boolean samePoints = roiBack.getAllPoints().containsAll(roi.getAllPoints()) &&
						roi.getAllPoints().containsAll(roiBack.getAllPoints());
				boolean samePlane = roiBack.getImagePlane() == roi.getImagePlane();
				if (samePoints && samePlane) {
					countCheck[index++]++;
					break;
				}
			}
		}
		assertArrayEquals(countCheck, new int[] {1, 1, 1, 1, 1});
	}
}
