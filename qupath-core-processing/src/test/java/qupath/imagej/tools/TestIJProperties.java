package qupath.imagej.tools;

import ij.gui.Roi;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestIJProperties {

    @Test
    public void testSetClassification() {
        var roi = new Roi(0, 0, 10, 20);
        assertNull(null, IJProperties.getClassification(roi));
        String classification = "Tumor: Positive";
        IJProperties.setClassification(roi, classification);
        assertEquals(classification, IJProperties.getClassification(roi));
    }

    @Test
    public void testSetId() {
        var roi = new Roi(0, 0, 10, 20);
        var id = UUID.randomUUID();
        assertNull(IJProperties.getObjectId(roi));
        IJProperties.setObjectId(roi, id);
        assertEquals(id.toString(), IJProperties.setObjectId(roi, id));
    }

    @Test
    public void testSetName() {
        var roi = new Roi(0, 0, 10, 20);
        roi.setName("My name IJ");
        var name = "My name QuPath";
        assertNull(IJProperties.getObjectName(roi));
        IJProperties.setObjectName(roi, name);
        assertEquals(name, IJProperties.getObjectName(roi));
    }

    @Test
    public void testSetDefaultName() {
        var roi = new Roi(0, 0, 10, 20);
        var name = "My name QuPath";
        IJProperties.setObjectName(roi, name);
        assertEquals(name, IJProperties.getObjectName(roi));

        String nameIJ = "My name IJ";
        assertNull(IJProperties.getDefaultRoiName(roi));
        roi.setName(nameIJ);
        IJProperties.setDefaultRoiName(roi, nameIJ);
        assertEquals(nameIJ, IJProperties.getDefaultRoiName(roi));

        assertEquals(name, IJProperties.getObjectName(roi));
    }

    @Test
    public void testNoType() {
        var roi = new Roi(0, 0, 10, 20);
        assertNull(null, IJProperties.getObjectType(roi));
        assertFalse(IJProperties.getObjectCreator(roi).isPresent());
    }

    @Test
    public void testObjTypeDetection() {
        var roi = new Roi(0, 0, 10, 20);
        var objType = IJProperties.setObjectType(roi, PathObjects.createDetectionObject(ROIs.createEmptyROI()));
        assertEquals("detection", objType);
        var created = IJProperties.getObjectCreator(roi).get().apply(ROIs.createEmptyROI());
        assertInstanceOf(PathDetectionObject.class, created);
    }

    @Test
    public void testObjTypeTile() {
        var roi = new Roi(0, 0, 10, 20);
        var objType = IJProperties.setObjectType(roi, PathObjects.createTileObject(ROIs.createEmptyROI()));
        assertEquals("tile", objType);
        var created = IJProperties.getObjectCreator(roi).get().apply(ROIs.createEmptyROI());
        assertInstanceOf(PathTileObject.class, created);
    }

    @Test
    public void testObjTypeAnnotation() {
        var roi = new Roi(0, 0, 10, 20);
        var objType = IJProperties.setObjectType(roi, PathObjects.createAnnotationObject(ROIs.createEmptyROI()));
        assertEquals("annotation", objType);
        var created = IJProperties.getObjectCreator(roi).get().apply(ROIs.createEmptyROI());
        assertInstanceOf(PathAnnotationObject.class, created);
    }

    @Test
    public void testObjTypeCell() {
        var roi = new Roi(0, 0, 10, 20);
        var objType = IJProperties.setObjectType(roi, PathObjects.createCellObject(ROIs.createEmptyROI(), null));
        assertEquals("cell", objType);
        var created = IJProperties.getObjectCreator(roi).get().apply(ROIs.createEmptyROI());
        assertInstanceOf(PathCellObject.class, created);
    }

    @Test
    public void testObjTypeCellNucleus() {
        var roi = new Roi(0, 0, 10, 20);
        var objType = IJProperties.setObjectTypeCellNucleus(roi);
        assertEquals("cell.nucleus", objType);
        var created = IJProperties.getObjectCreator(roi).get().apply(ROIs.createEmptyROI());
        // Note that it is expected to be a detection, not a cell (because we can't make a cell with only a nucleus)!
        assertInstanceOf(PathDetectionObject.class, created);
    }

    @Test
    public void testObjCalibrate() {
        var detection = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 10, 20, 30, ImagePlane.getDefaultPlane()));
        detection.setPathClass(PathClass.fromString("Tumor: Positive"));
        detection.setName("My name in QuPath");
        detection.getMeasurements().put("My first measurement", 1.5);

        var roiIJ = IJTools.convertToIJRoi(detection.getROI(), 0, 0, 1);
        IJTools.calibrateRoi(roiIJ, detection);

        var detection2 = IJTools.convertToPathObject(
                roiIJ, 1, 0, 1, IJProperties.getObjectCreator(roiIJ).orElse(null), null
        );
        assertNotNull(detection2);
        assertEquals(detection.getClass(), detection2.getClass());
        assertEquals(detection.getPathClass(), detection2.getPathClass());
        assertEquals(detection.getName(), detection2.getName());
        // Measurements are NOT passed to ImageJ
        // assertEquals(detection.getMeasurements(), detection2.getMeasurements());
    }

}
