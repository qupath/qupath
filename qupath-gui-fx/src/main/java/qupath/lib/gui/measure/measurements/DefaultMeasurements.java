package qupath.lib.gui.measure.measurements;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;

import java.util.List;

/**
 * Helper class to create or access different {@link MeasurementBuilder} instances.
 * These can be used to extract (possibly dynamic) measurements from objects.
 */
public class DefaultMeasurements {

    /**
     * Measurement to extract a string representation of an object's classification.
     */
    public static final StringMeasurementBuilder CLASSIFICATION = new PathClassMeasurementBuilder();

    /**
     * Measurement to extract the <i>displayed</i> name of an object's parent.
     * The displayed name is derived from {@link PathObject#getDisplayedName()} for the parent.
     */
    public static final StringMeasurementBuilder PARENT_DISPLAYED_NAME = new ParentNameMeasurementBuilder();

    /**
     * Measurement to extract the type of an object (e.g. Annotation, Detection, Cell).
     */
    public static final StringMeasurementBuilder OBJECT_TYPE = new ObjectTypeMeasurementBuilder();

    /**
     * Measurement to extract the unique ID of an object, as a string.
     */
    public static final StringMeasurementBuilder OBJECT_ID = new ObjectIdMeasurementBuilder();

    /**
     * Measurement to extract the name of an object.
     */
    public static final StringMeasurementBuilder OBJECT_NAME = new ObjectNameMeasurementBuilder();

    /**
     * Measurement to extract the name of a TMA core, or of the TMA core that is an ancestor of the provided object.
     * This is useful to assign objects to specific cores.
     */
    public static final StringMeasurementBuilder TMA_CORE_NAME = new TMACoreNameMeasurementBuilder();

    /**
     * Measurement to extract the name of a ROI type (e.g. Polygon, Rectangle).
     */
    public static final StringMeasurementBuilder ROI_TYPE = new ROINameMeasurementBuilder();

    /**
     * Create a measurement that extracts the current name from an ImageData.
     * @param imageData the image data from which the name should be read
     * @return
     */
    public static StringMeasurementBuilder createImageNameMeasurement(ImageData<?> imageData) {
        return new ImageNameMeasurementBuilder(imageData);
    }


    public static MeasurementBuilder<Boolean> TMA_CORE_MISSING = new MissingTMACoreMeasurementBuilder();

    public static NumericMeasurementBuilder ROI_Z_SLICE = new ZSliceMeasurementBuilder();

    public static NumericMeasurementBuilder ROI_TIMEPOINT = new TimepointMeasurementBuilder();

    public static NumericMeasurementBuilder ROI_NUM_POINTS = new NumPointsMeasurementBuilder();


    /**
     * Create a measurement that extracts the x-coordinate of the centroid of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static NumericMeasurementBuilder createROICentroidX(ImageData<?> imageData) {
        return new ROICentroidMeasurementBuilder(imageData, ROICentroidMeasurementBuilder.CentroidType.X);
    }

    /**
     * Create a measurement that extracts the y-coordinate of the centroid of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static NumericMeasurementBuilder createROICentroidY(ImageData<?> imageData) {
        return new ROICentroidMeasurementBuilder(imageData, ROICentroidMeasurementBuilder.CentroidType.Y);
    }

    /**
     * Create a measurement that extracts the area of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static NumericMeasurementBuilder createROIAreaMeasurement(ImageData<?> imageData) {
        return new AreaMeasurementBuilder(imageData);
    }

    /**
     * Create a measurement that extracts the perimeter of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static NumericMeasurementBuilder createROIPerimeterMeasurement(ImageData<?> imageData) {
        return new PerimeterMeasurementBuilder(imageData);
    }

    /**
     * Create a measurement that extracts the length of a line ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static NumericMeasurementBuilder createROILengthMeasurement(ImageData<?> imageData) {
        return new LineLengthMeasurementBuilder(imageData);
    }

    /**
     * Create a measurement that counts the number of detections within an object's ROI.
     * @param imageData the image data containing the object hierarchy
     * @return
     */
    public static NumericMeasurementBuilder createDetectionCountMeasurement(ImageData<?> imageData) {
        return new ObjectTypeCountMeasurementBuilder(imageData, PathDetectionObject.class);
    }


    /**
     * Create a measurement for displaying live measurements from a pixel classifier.
     * @param manager
     * @param name
     * @return
     */
    public static NumericMeasurementBuilder createLivePixelClassificationMeasurement(PixelClassificationMeasurementManager manager, String name) {
        return new PixelClassifierMeasurementBuilder(manager, name);
    }


    public static StringMeasurementBuilder createMetadataMeasurement(String name) {
        return new StringMetadataMeasurementBuilder(name);
    }


    public static List<MeasurementBuilder<?>> getClassifiedDetectionCountMeasurements(ImageData<?> imageData, boolean includeDensity) {
        return DerivedMeasurementManager.createMeasurements(imageData, includeDensity);
    }

}
