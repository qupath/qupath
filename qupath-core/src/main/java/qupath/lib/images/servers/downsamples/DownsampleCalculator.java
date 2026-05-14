package qupath.lib.images.servers.downsamples;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

/**
 * Interface for classes that can calculate a downsample value to use when requesting pixels from an image.
 * <p>
 * This is used to support different ways to defining how a resolution could be calculated,
 * e.g. using a fixed value, based upon a target pixel size, or based upon a target image size.
 */
public interface DownsampleCalculator {

    /**
     * Calculate the downsample value to use when requesting a region from the server.
     * @param server the input server; this must not be null
     * @param region the region to request; this may be null, indicating that the entire image is requested
     * @return the downsample value to use
     */
    double getDownsample(ImageServer<?> server, ImageRegion region);

}
