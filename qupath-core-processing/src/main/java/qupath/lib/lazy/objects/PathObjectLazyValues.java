package qupath.lib.lazy.objects;

import qupath.lib.images.ImageData;
import qupath.lib.lazy.interfaces.LazyBooleanValue;
import qupath.lib.lazy.interfaces.LazyNumericValue;
import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper class to create or access different {@link LazyValue} instances.
 * These can be used to extract (possibly dynamic) measurements from objects.
 */
public class PathObjectLazyValues {

    private static final Map<ImageData<?>, DetectionClassificationCounter> countsMap = Collections.synchronizedMap(new WeakHashMap<>());

    private static Function<PathObject, DetectionPathClassCounts> getCountsFunction(ImageData<?> imageData) {
        return countsMap.computeIfAbsent(imageData, data -> new DetectionClassificationCounter(data.getHierarchy()));
    }

    /**
     * Measurement to extract a string representation of an object's classification.
     */
    public static final LazyStringValue<PathObject> CLASSIFICATION = new PathClassValue();

    /**
     * Measurement to extract the <i>displayed</i> name of an object's parent.
     * The displayed name is derived from {@link PathObject#getDisplayedName()} for the parent.
     */
    public static final LazyStringValue<PathObject> PARENT_DISPLAYED_NAME = new ParentNameValue();

    /**
     * Measurement to extract the type of an object (e.g. Annotation, Detection, Cell).
     */
    public static final LazyStringValue<PathObject> OBJECT_TYPE = new ObjectTypeValue();

    /**
     * Measurement to extract the unique ID of an object, as a string.
     */
    public static final LazyStringValue<PathObject> OBJECT_ID = new ObjectIdValue();

    /**
     * Measurement to extract the name of an object.
     */
    public static final LazyStringValue<PathObject> OBJECT_NAME = new ObjectNameValue();

    /**
     * Measurement to extract the name of a TMA core, or of the TMA core that is an ancestor of the provided object.
     * This is useful to assign objects to specific cores.
     */
    public static final LazyStringValue<PathObject> TMA_CORE_NAME = new TMACoreNameValue();

    /**
     * Measurement to extract the name of a ROI type (e.g. Polygon, Rectangle).
     */
    public static final LazyStringValue<PathObject> ROI_TYPE = new ROINameValue();

    /**
     * Create a measurement that extracts the current name from an ImageData.
     * @param imageData the image data from which the name should be read
     * @return
     */
    public static LazyStringValue<PathObject> createImageNameMeasurement(ImageData<?> imageData) {
        return new ImageNameValue(imageData);
    }


    public static LazyBooleanValue<PathObject> TMA_CORE_MISSING = new MissingTMACoreValue();

    public static LazyNumericValue<PathObject> ROI_Z_SLICE = new ZSliceValue();

    public static LazyNumericValue<PathObject> ROI_TIMEPOINT = new TimepointValue();

    public static LazyNumericValue<PathObject> ROI_NUM_POINTS = new NumPointsValue();


    /**
     * Create a measurement that extracts the x-coordinate of the centroid of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static LazyNumericValue<PathObject> createROICentroidX(ImageData<?> imageData) {
        return new ROICentroidValue(imageData, ROICentroidValue.CentroidType.X);
    }

    /**
     * Create a measurement that extracts the y-coordinate of the centroid of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static LazyNumericValue<PathObject> createROICentroidY(ImageData<?> imageData) {
        return new ROICentroidValue(imageData, ROICentroidValue.CentroidType.Y);
    }

    /**
     * Create a measurement that extracts the area of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static LazyNumericValue<PathObject> createROIAreaMeasurement(ImageData<?> imageData) {
        return new ROIAreaValue(imageData);
    }

    /**
     * Create a measurement that extracts the perimeter of an object's ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static LazyNumericValue<PathObject> createROIPerimeterMeasurement(ImageData<?> imageData) {
        return new PerimeterValue(imageData);
    }

    /**
     * Create a measurement that extracts the length of a line ROI.
     * @param imageData the image data used for calibration
     * @return
     */
    public static LazyNumericValue<PathObject> createROILengthMeasurement(ImageData<?> imageData) {
        return new LineLengthValue(imageData);
    }

    /**
     * Create a measurement that counts the number of detections within an object's ROI.
     * @param imageData the image data containing the object hierarchy
     * @return
     */
    public static LazyNumericValue<PathObject> createDetectionCountMeasurement(ImageData<?> imageData) {
        return new ObjectTypeCountValue(imageData, PathDetectionObject.class);
    }


    /**
     * Create a measurement for displaying live measurements from a pixel classifier.
     * @param manager
     * @param name
     * @return
     */
    public static LazyNumericValue<PathObject> createLivePixelClassificationMeasurement(PixelClassificationMeasurementManager manager, String name) {
        return new PixelClassifierValue(manager, name);
    }


    public static LazyStringValue<PathObject> createMetadataMeasurement(String name) {
        return new StringMetadataValue(name);
    }

    public static LazyNumericValue<PathObject> createMeasurementListMeasurement(String name) {
        return new MeasurementListValue(name);
    }


    public static LazyNumericValue<PathObject> createHScoreMeasurement(
            ImageData<?> imageData,
            PathClass... pathClasses) {
        return new HScoreValue(getCountsFunction(imageData), pathClasses);
    }

    public static LazyNumericValue<PathObject> createPositivePercentageMeasurement(
            ImageData<?> imageData,
            PathClass... pathClasses) {
        return new PositivePercentageValue(getCountsFunction(imageData), pathClasses);
    }

    public static LazyNumericValue<PathObject> createDetectionClassDensityMeasurement(
            ImageData<?> imageData,
            PathClass pathClass) {
        return new ClassDensityValue(getCountsFunction(imageData), imageData, pathClass);
    }

    public static LazyNumericValue<PathObject> createBaseClassCountsMeasurement(
            ImageData<?> imageData,
            PathClass pathClass) {
        return new DetectionClassificationsCountValue(getCountsFunction(imageData), pathClass, true);
    }

    public static LazyNumericValue<PathObject> createExactClassCountsMeasurement(
            ImageData<?> imageData,
            PathClass pathClass) {
        return new DetectionClassificationsCountValue(getCountsFunction(imageData), pathClass, false);
    }

    public static LazyNumericValue<PathObject> createAllredIntensityMeasurement(
            ImageData<?> imageData,
            Supplier<Double> allredMinPercentage,
            PathClass... pathClasses) {
        return new AllredIntensityValue(getCountsFunction(imageData), allredMinPercentage, pathClasses);
    }

    public static LazyNumericValue<PathObject> createAllredProportionMeasurement(
            ImageData<?> imageData,
            Supplier<Double> allredMinPercentage,
            PathClass... pathClasses) {
        return new AllredProportionValue(getCountsFunction(imageData), allredMinPercentage, pathClasses);
    }

    public static LazyNumericValue<PathObject> createAllredMeasurement(
            ImageData<?> imageData,
            Supplier<Double> allredMinPercentage,
            PathClass... pathClasses) {
        return new AllredValue(getCountsFunction(imageData), allredMinPercentage, pathClasses);
    }


}
