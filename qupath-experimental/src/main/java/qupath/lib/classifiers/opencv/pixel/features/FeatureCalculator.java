package qupath.lib.classifiers.opencv.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

public interface FeatureCalculator<T> {

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
     * 
     * @throws IOException if unable to read pixels from {@code server}
     */
    public T calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException;
    
    /**
     * Get the input image size requested by this calculator.
     * @return
     */
    public ImmutableDimension getInputSize();
    
    /*
     * Get a list containing the names of each feature, in the order that they are returned.
     * @return
     * 
     * TODO: Consider if this is really necessary or if it can be removed
     */
    public List<String> getFeatureNames();
    
    /**
     * Get metadata that describes how the classifier should be called,
     * and the kind of output it provides.
     *
     * @return
     */
//    public PixelClassifierMetadata getMetadata();

}