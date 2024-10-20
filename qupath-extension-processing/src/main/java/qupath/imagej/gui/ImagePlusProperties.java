package qupath.imagej.gui;

import ij.ImagePlus;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * Store QuPath-related information within the properties of an {@link ImagePlus}.
 * <p>
 * Note that these use {@link ImagePlus#setProp(String, String)} rather than the tempting
 * {@link ImagePlus#setProperty(String, Object)}, as this seems necessary to be able to access the properties
 * later from a macro.
 */
public class ImagePlusProperties {

    /**
     * Key for an {@link ImagePlus} property to store a string representing a QuPath
     * {@link ImageData.ImageType}.
     */
    public static final String IMAGE_TYPE = "qupath.image.type";

    /**
     * Key for an {@link ImagePlus} property storing either {@code "light"}  or {@core "dark} depending upon
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
     * Key for an {@link ImagePlus} property to store a string representing a QuPath
     * {@link ImageRegion}.
     */
    public static final String IMAGE_REGION = "qupath.image.region";


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
    public static String setRegionProperty(ImagePlus imp, ImageRegion region) {
        if (region == null)
            return null;
        String prop = String.format("[x=%d, y=%d, w=%d, h=%d, z=%d, t=%d]",
                region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getZ(), region.getT());
        imp.setProp(IMAGE_REGION, prop);
        imp.setProp(IMAGE_REGION + ".x", region.getX());
        imp.setProp(IMAGE_REGION + ".y", region.getY());
        imp.setProp(IMAGE_REGION + ".width", region.getWidth());
        imp.setProp(IMAGE_REGION + ".height", region.getHeight());
        imp.setProp(IMAGE_REGION + ".z", region.getZ());
        imp.setProp(IMAGE_REGION + ".t", region.getT());
        if (region instanceof RegionRequest request) {
            imp.setProp(IMAGE_REGION + ".downsample", request.getDownsample());
        }
        return prop;
    }

    /**
     * Set the {@code IMAGE_TYPE} property based on the name of QuPath's image type, if available.
     * @param imp
     * @param imageType
     * @return the value that was set for the property, or null if it was not set
     */
    public static String setTypeProperty(ImagePlus imp, ImageData.ImageType imageType) {
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
    public static String setBackgroundProperty(ImagePlus imp, ImageData.ImageType imageType) {
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

}
