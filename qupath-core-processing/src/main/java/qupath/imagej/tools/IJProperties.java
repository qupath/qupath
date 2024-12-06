package qupath.imagej.tools;

import ij.ImagePlus;
import ij.gui.Roi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
     * Set the {@code IMAGE_REGION} property as a string representation of the region's bounding box.
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
            var region = ImageRegion.createInstance(
                    Integer.parseInt(sx), Integer.parseInt(sy), Integer.parseInt(swidth), Integer.parseInt(sheight),
                    z, t
            );
            return region;
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

    // TODO: Support object type as a property
//    public static final String OBJECT_TYPE = "qupath.object.type";

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
     * Set a property storing a QuPath object ID within a specified Roi.
     * @param roi the roi with the property to set
     * @param pathObject the object whose ID should be stored
     * @see #OBJECT_ID
     */
    public static String setObjectId(Roi roi, PathObject pathObject) {
        return setObjectId(roi, pathObject == null ? null : pathObject.getID());
    }

    /**
     * Set a property storing a QuPath object ID within a specified Roi.
     * @param roi the roi with the property to set
     * @param id the id value
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
