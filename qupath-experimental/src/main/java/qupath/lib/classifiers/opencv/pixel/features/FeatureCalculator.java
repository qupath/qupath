package qupath.lib.classifiers.opencv.pixel.features;

import java.io.IOException;
import java.util.List;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;

public interface FeatureCalculator<S,T> {

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
     * @param imageData
     * @param request
     * @return a (possibly singleton) list of Features
     * 
     * @throws IOException if unable to read pixels from {@code server}
     */
    public List<Feature<T>> calculateFeatures(ImageData<S> imageData, RegionRequest request) throws IOException;
    
    /**
     * Get the input image size requested by this calculator.
     * @return
     */
    public ImmutableDimension getInputSize();
    
    /**
     * Get metadata that describes how the classifier should be called,
     * and the kind of output it provides.
     *
     * @return
     */
//    public PixelClassifierMetadata getMetadata();

}