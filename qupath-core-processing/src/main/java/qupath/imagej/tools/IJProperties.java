package qupath.imagej.tools;

import ij.ImagePlus;
import ij.gui.Roi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Store QuPath-related information within the properties of ImageJ objects.
 * <p>
 * Note that these use {@link ImagePlus#setProp(String, String)} rather than the tempting
 * {@link ImagePlus#setProperty(String, Object)}, as this seems necessary to be able to access the properties
 * later from a macro.
 */
public class IJProperties {

    private static final Logger logger = LoggerFactory.getLogger(IJProperties.class);

    /**
     * Key for an {@link ImagePlus} property to store a string representing a QuPath
     * {@link ImageData.ImageType}.
     */
    public static final String IMAGE_TYPE = "qupath.image.type";

    /**
     * Key for an {@link ImagePlus} property storing either {@code "light"}  or {@code "dark} depending upon
     * whether an image is known to be brightfield of fluorescence respectively.
     */
    public static final String IMAGE_BACKGROUND = "qupath.image.background";

    /**
     * Property value for IMAGE_BACKGROUND when an image is known to have a dark background.
     */
    public static final String BACKGROUND_DARK = "dark";

    /**
     * Property value for IMAGE_BACKGROUND when an image is known to have a light background.
     */
    public static final String BACKGROUND_LIGHT = "light";

    /**
     * Key for a {@link ImagePlus} properties to store the bounding box of a QuPath {@link ImageRegion}.
     * Each value (x, y, width, height) is stored as a separate entry.
     */
    public static final String IMAGE_REGION_ROOT = "qupath.image.region.";

    /**
     * Key for an {@link ImagePlus} property to store a json representation of a {@link RegionRequest} used
     * to request the image.
     */
    public static final String IMAGE_REQUEST = "qupath.image.request";


    /**
     * Set the IMAGE_REGION_ROOT property as a string representation of the region's bounding box.
     * <p>
     * This also stores additional properties under {@code "qupath.image.region.x"}, {@code "qupath.image.region.y"},
     * {@code "qupath.image.region.width"} and {@code "qupath.image.region.height"} to encode the values separately,
     * in addition to {@code "qupath.image.region.downsample"} if available.
     * @param imp
     * @param region
     * @return the value that was set for the property, or null if the region was null
     */
    public static String setImageRegion(ImagePlus imp, ImageRegion region) {
        if (region == null)
            return null;
        String prop = String.format("[x=%d, y=%d, w=%d, h=%d, z=%d, t=%d]",
                region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getZ(), region.getT());
        imp.setProp(IMAGE_REGION_ROOT, prop);
        imp.setProp(IMAGE_REGION_ROOT + "x", region.getX());
        imp.setProp(IMAGE_REGION_ROOT + "y", region.getY());
        imp.setProp(IMAGE_REGION_ROOT + "width", region.getWidth());
        imp.setProp(IMAGE_REGION_ROOT + "height", region.getHeight());
        imp.setProp(IMAGE_REGION_ROOT + "z", region.getZ());
        imp.setProp(IMAGE_REGION_ROOT + "t", region.getT());
        if (region instanceof RegionRequest request) {
            imp.setProp(IMAGE_REGION_ROOT + "downsample", request.getDownsample());
        }
        return prop;
    }

    /**
     * Get the IMAGE_REGION_ROOT property, if set.
     * @param imp
     * @return the image region stored in the image properties, or null if a valid region could not be found
     */
    public static ImageRegion getImageRegion(ImagePlus imp) {
        String sx = imp.getProp(IMAGE_REGION_ROOT + "x");
        String sy = imp.getProp(IMAGE_REGION_ROOT + "y");
        String swidth = imp.getProp(IMAGE_REGION_ROOT + "width");
        String sheight = imp.getProp(IMAGE_REGION_ROOT + "height");
        String sz = imp.getProp(IMAGE_REGION_ROOT + "z");
        String st = imp.getProp(IMAGE_REGION_ROOT + "t");
        if (sx == null || sy == null || swidth == null || sheight == null)
            return null;
        int z = sz == null ? 0 : Integer.parseInt(sz);
        int t = st == null ? 0 : Integer.parseInt(st);
        try {
            return ImageRegion.createInstance(
                    Integer.parseInt(sx), Integer.parseInt(sy), Integer.parseInt(swidth), Integer.parseInt(sheight),
                    z, t
            );
        } catch (Exception e) {
            logger.warn("Exception parsing region from properties: {}", e.getMessage());
            logger.debug(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Store a json representation of a RegionRequest as a property in an image.
     * @param imp the image
     * @param request the request that corresponds to the image
     * @return the json representation that is stored
     * @see #IMAGE_REQUEST
     */
    public static String setRegionRequest(ImagePlus imp, RegionRequest request) {
        var json = GsonTools.getInstance().toJson(request);
        imp.setProp(IMAGE_REQUEST, json);
        return json;
    }

    /**
     * Get a RegionRequest by reading the json representation stored as a property in the image.
     * @param imp the image
     * @return the RegionRequest, or null if none is found
     * @see #IMAGE_REQUEST
     */
    public static RegionRequest getRegionRequest(ImagePlus imp) {
        var json = imp.getProp(IMAGE_REQUEST);
        return json == null ? null : GsonTools.getInstance().fromJson(json, RegionRequest.class);
    }

    /**
     * Set the {@code IMAGE_TYPE} property based on the name of QuPath's image type, if available.
     * @param imp
     * @param imageType
     * @return the value that was set for the property, or null if it was not set
     */
    public static String setImageType(ImagePlus imp, ImageData.ImageType imageType) {
        if (imageType == null)
            return null;
        var prop = imageType.name();
        imp.setProp(IMAGE_TYPE, prop);
        return prop;
    }

    /**
     * Set the {@code IMAGE_BACKGROUND} property as {@code "dark"} for fluorescence images or {@code "light"}
     * for brightield images; otherwise, do nothing.
     * @param imp
     * @param imageType
     * @return the value that was set for the property, or null if it was not set
     */
    public static String setImageBackground(ImagePlus imp, ImageData.ImageType imageType) {
        if (imageType == null)
            return null;
        String prop = switch (imageType) {
            case FLUORESCENCE -> BACKGROUND_DARK;
            case BRIGHTFIELD_H_DAB, BRIGHTFIELD_H_E, BRIGHTFIELD_OTHER -> BACKGROUND_LIGHT;
            default -> null;
        };
        if (prop != null)
            imp.setProp(IMAGE_BACKGROUND, prop);
        return prop;
    }

    /**
     * Get the image background property value
     * @param imp
     * @return one of {@code "dark"}, {@code "light"} or {@code null}.
     */
    public static String getImageBackground(ImagePlus imp) {
        return imp.getProp(IMAGE_BACKGROUND);
    }


    /**
     * Set property for {@link PathObject#getClassification()} ()}
     */
    public static final String OBJECT_CLASSIFICATION = "qupath.object.classification";

    /**
     * Set property for {@link PathObject#getName()}
     */
    public static final String OBJECT_NAME = "qupath.object.name";

    /**
     * Set property that stores the type of an object.
     */
    public static final String OBJECT_TYPE = "qupath.object.type";

    /**
     * Set property for {@link PathObject#getID()} ()}
     */
    public static final String OBJECT_ID = "qupath.object.id";

    /**
     * Base for property names that store measurements to be added to {@link PathObject#getMeasurements()}.
     * The measurement name should be in the format {@code OBJECT_MEASUREMENT_ROOT + name} where {@code name}
     * must not contain any spaces or newlines.
     */
    public static final String OBJECT_MEASUREMENT_ROOT = "qupath.object.measurements.";

    /**
     * Property to store the name for a {@link Roi}.
     * The purpose of this is to allow QuPath to set the name of a {@code Roi} for processing within ImageJ,
     * but then identify whether that name has been changed within ImageJ or not.
     * <p>
     * It addresses the problem of figuring out when the name of a {@code Roi} should be used to update a QuPath
     * object (because the name was intentionally set elsewhere), and when it should not.
     */
    public static final String DEFAULT_ROI_NAME = "qupath.default.roi.name";

    /**
     * Set a property storing a QuPath object classification within a specified Roi.
     * @param roi the roi with the property to set
     * @param pathObject the object whose classification should be stored
     * @see #OBJECT_CLASSIFICATION
     */
    public static String setClassification(Roi roi, PathObject pathObject) {
        return setClassification(roi, pathObject == null ? null : pathObject.getClassification());
    }

    /**
     * Set a property storing a QuPath object classification within a specified Roi.
     * @param roi the roi with the property to set
     * @param classification the classification string value
     * @see #OBJECT_CLASSIFICATION
     */
    public static String setClassification(Roi roi, String classification) {
        roi.setProperty(OBJECT_CLASSIFICATION, classification);
        return classification;
    }

    /**
     * Get a QuPath classification, as stored in a roi's properties.
     * @param roi the roi
     * @return the classification if available, or null otherwise
     * @see #OBJECT_CLASSIFICATION
     */
    public static String getClassification(Roi roi) {
        return roi.getProperty(OBJECT_CLASSIFICATION);
    }

    /**
     * Set a property storing a QuPath object name within a specified Roi.
     * @param roi the roi with the property to set
     * @param pathObject the object whose name should be stored
     * @see #OBJECT_NAME
     */
    public static String setObjectName(Roi roi, PathObject pathObject) {
        return setObjectName(roi, pathObject == null ? null : pathObject.getName());
    }

    /**
     * Set a property storing a QuPath object name within a specified Roi.
     * @param roi the roi with the property to set
     * @param name the name value
     * @see #OBJECT_NAME
     */
    public static String setObjectName(Roi roi, String name) {
        roi.setProperty(OBJECT_NAME, name);
        return name;
    }

    /**
     * Get a QuPath object name, as stored in a roi's properties.
     * @param roi the roi
     * @return the name if available, or null otherwise
     * @see #OBJECT_NAME
     */
    public static String getObjectName(Roi roi) {
        return roi.getProperty(OBJECT_NAME);
    }

    /**
     * Set the value of the DEFAULT_ROI_NAME property.
     * @param roi the roi
     * @param name the name to set
     * @return the name that was set
     */
    public static String setDefaultRoiName(Roi roi, String name) {
        roi.setProperty(DEFAULT_ROI_NAME, name);
        return name;
    }

    /**
     * Get the value of the DEFAULT_ROI_NAME property.
     * @param roi the roi to query
     * @return the value of the property, or null if no value is found
     */
    public static String getDefaultRoiName(Roi roi) {
        return roi.getProperty(DEFAULT_ROI_NAME);
    }

    /**
     * Check whether the name of a Roi matches any (non-null) value of DEFAULT_ROI_NAME.
     * @param roi the roi to query
     * @return true if the default Roi name is not null, and matches the name of the Roi
     */
    public static boolean hasDefaultRoiName(Roi roi) {
        var defaultName = getDefaultRoiName(roi);
        return defaultName != null && defaultName.equals(roi.getName());
    }

    /**
     * Set a property storing a QuPath object ID within a specified Roi.
     * @param roi the roi with the property to set
     * @param pathObject the object whose ID should be stored
     * @return a string representation of the object id
     * @see #OBJECT_ID
     */
    public static String setObjectId(Roi roi, PathObject pathObject) {
        return setObjectId(roi, pathObject == null ? null : pathObject.getID());
    }

    /**
     * Set a property storing a QuPath object ID within a specified Roi.
     * @param roi the roi with the property to set
     * @param id the id value
     * @return a string representation of the object id
     * @see #OBJECT_ID
     */
    public static String setObjectId(Roi roi, UUID id) {
        var val = id == null ? null : id.toString();
        roi.setProperty(OBJECT_ID, val);
        return val;
    }

    /**
     * Get a QuPath object ID from the Roi properties.
     * @param roi the roi
     * @return a UUID if found in the Roi's properties, or null otherwise
     * @see #OBJECT_ID
     */
    public static UUID getObjectId(Roi roi) {
        var id = roi.getProperty(OBJECT_ID);
        try {
            return id == null || id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid object ID in Roi: {}", id);
            return null;
        }
    }


    /**
     * Set a property storing the type of an object (e.g. annotation, detection, cell),
     * optionally appending an additional string.
     * @param roi the roi with the property to set
     * @param pathObject the object whose type should be set
     * @return a string representation of the object type
     * @see #OBJECT_TYPE
     */
    public static String setObjectType(Roi roi, PathObject pathObject) {
        return switch (pathObject) {
            case PathAnnotationObject _ -> setObjectTypeAnnotation(roi);
            case PathCellObject _ -> setObjectTypeCell(roi);
            case PathTileObject _ -> setObjectTypeTile(roi);
            case PathDetectionObject _ -> setObjectTypeDetection(roi);
            case PathRootObject _ -> setObjectType(roi, "root");
            case null, default -> null;
        };
    }

    /**
     * Set the object type property of a Roi to indicate it should be an annotation in QuPath.
     * @param roi the ImageJ roi
     * @return the string representing the object type
     */
    public static String setObjectTypeAnnotation(Roi roi) {
        return setObjectType(roi, "annotation");
    }

    /**
     * Set the object type property of a Roi to indicate it should be an detection in QuPath.
     * @param roi the ImageJ roi
     * @return the string representing the object type
     */
    public static String setObjectTypeDetection(Roi roi) {
        return setObjectType(roi, "detection");
    }

    /**
     * Set the object type property of a Roi to indicate it should be a tile in QuPath.
     * @param roi the ImageJ roi
     * @return the string representing the object type
     */
    public static String setObjectTypeTile(Roi roi) {
        return setObjectType(roi, "tile");
    }

    /**
     * Set the object type property of a Roi to indicate it should be a cell in QuPath.
     * @param roi the ImageJ roi
     * @return the string representing the object type
     */
    public static String setObjectTypeCell(Roi roi) {
        return setObjectType(roi, "cell");
    }

    /**
     * Set the object type property of a Roi to indicate it should be a cell nucleus in QuPath.
     * <p>
     * Note that this may be useful for ImageJ, but it is not possible to create a QuPath cell object from a single
     * nucleus ROI.
     * @param roi the ImageJ roi
     * @return the string representing the object type
     */
    public static String setObjectTypeCellNucleus(Roi roi) {
        return setObjectType(roi, "cell.nucleus");
    }

    private static String setObjectType(Roi roi, String typeName) {
        roi.setProperty(OBJECT_TYPE, typeName);
        return typeName;
    }

    private static Function<ROI, PathObject> getObjectCreatorForType(String typeString) {
        return switch (typeString) {
            case "annotation" -> PathObjects::createAnnotationObject;
            case "cell" -> r -> PathObjects.createCellObject(r, null);
            case "cell.nucleus" -> PathObjects::createDetectionObject; // Can't create a cell with a nucleus only
            case "detection" -> PathObjects::createDetectionObject;
            case "tile" -> PathObjects::createTileObject;
            case null, default -> null;
        };
    }

    /**
     * Get the value of a Roi's OBJECT_TYPE property.
     * @param roi the roi
     * @return the value of OBJECT_TYPE, or null if no type property is set
     */
    public static String getObjectType(Roi roi) {
        return roi.getProperty(OBJECT_TYPE);
    }

    /**
     * Get a function to create a new object based upon the stored object type property, if available.
     * <p>
     * Note that not all object types can be created in this way (e.g., it isn't possible to create a root object or
     * TMA core object; it also isn't possible to create a cell containing a nucleus only, but rather only a detection).
     *
     * @param roi the Roi that may contain an object type property
     * @return an optional that contains an object creator, if known
     */
    public static Optional<Function<ROI, PathObject>> getObjectCreator(Roi roi) {
        var typeString = getObjectType(roi);
        return getObjectCreator(typeString);
    }

    /**
     * Get a function to create a new object based upon a property value of OBJECT_TYPE.
     * <p>
     * Note that not all object types can be created in this way (e.g., it isn't possible to create a root object or
     * TMA core object; it also isn't possible to create a cell containing a nucleus only, but rather only a detection).
     *
     * @param typeString the type string, as would be set by {@link #setObjectType(Roi, PathObject)}
     * @return an optional that contains an object creator, if known
     */
    public static Optional<Function<ROI, PathObject>> getObjectCreator(String typeString) {
        return Optional.ofNullable(getObjectCreatorForType(typeString));
    }

    /**
     * Set a property storing a QuPath object measurement within a specified Roi.
     * @param roi the roi with the property to set
     * @param name the name of the measurement
     * @param value the measurement value
     * @see #OBJECT_MEASUREMENT_ROOT
     */
    public static void putMeasurement(Roi roi, String name, double value) {
        roi.setProperty(OBJECT_MEASUREMENT_ROOT + name.replaceAll(" ", "_"), Double.toString(value));
    }

    /**
     * Get a measurement stored as a property.
     * The property name will begin with {@link #OBJECT_MEASUREMENT_ROOT} but the name supplied here
     * need only be the measurement name appended to this.
     * @param roi the roi that may contain the measurement as a property
     * @param name the measurement name
     * @return the measurement if it is found, or null otherwise
     * @see #OBJECT_MEASUREMENT_ROOT
     */
    public static Double getMeasurement(Roi roi, String name) {
        String key = name.replaceAll(" ", "_");
        if (!key.startsWith(OBJECT_MEASUREMENT_ROOT))
            key = OBJECT_MEASUREMENT_ROOT + key;
        var prop = roi.getProperty(key);
        return prop == null ? null : Double.parseDouble(prop);
    }

    /**
     * Get all QuPath object measurements found in the properties of a Roi.
     * These are properties with names that start with {@link #OBJECT_MEASUREMENT_ROOT}
     * and contain a numeric value that can be parsed.
     * @param roi the Roi to query
     * @return a map of all measurements that could be found, or an empty map if none are found
     * @see #OBJECT_MEASUREMENT_ROOT
     */
    public static Map<String, Number> getAllMeasurements(Roi roi) {
        var properties = roi.getProperties();
        if (properties == null || !properties.contains(OBJECT_MEASUREMENT_ROOT))
            return Collections.emptyMap();
        var map = new LinkedHashMap<String, Number>();
        for (var line : properties.split("\n")) {
            line = line.strip();
            if (line.startsWith(OBJECT_MEASUREMENT_ROOT)) {
                int indSplit = line.indexOf(":");
                if (indSplit >= 0 && indSplit < line.length()-1) {
                    try {
                        double val = Double.parseDouble(line.substring(indSplit + 1).strip());
                        String name = line.substring(OBJECT_MEASUREMENT_ROOT.length(), indSplit)
                                .replaceAll("_", " ")
                                .strip();
                        map.put(name, val);
                    } catch (NumberFormatException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
        return map;
    }

}
