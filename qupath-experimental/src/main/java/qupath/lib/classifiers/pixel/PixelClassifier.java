package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

public interface PixelClassifier {

    /**
     * Apply pixel classifier to a specified region of an image.
     * <p>
     * An {@code ImageServer} and {@code RegionRequest} are supplied, rather 
     * than a {@code BufferedImage} directly, because there may be a need to adapt 
     * to the image resolution and/or incorporate padding to reduce boundary effects.
     * <p>
     * There is no guarantee that the returned {@code BufferedImage} will be the same size 
     * as the input region (after downsampling), but rather that it should contain the full 
     * classification information for the specified region.
     * <p>
     * Practically, this means that there may be fewer pixels in the output because the classification 
     * inherently involves downsampling.
     *
     * @param server
     * @param request
     * @return a {@code BufferedImage} representing the pixel classifications as separate bands.
     */
    public BufferedImage applyClassification(ImageServer<BufferedImage> server, RegionRequest request);

    /**
     * Get metadata that describes how the classifier should be called,
     * and the kind of output it provides.
     *
     * @return
     */
    public PixelClassifierMetadata getMetadata();

}