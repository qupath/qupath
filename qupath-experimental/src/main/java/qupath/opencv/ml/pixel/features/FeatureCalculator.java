package qupath.opencv.ml.pixel.features;

import java.io.IOException;
import java.util.List;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;

public interface FeatureCalculator<T> {

    /**
     * Calculate one or more pixel-based features from an image region.
     *
     * @param imageData
     * @param request
     * @return a (possibly singleton) list of Features
     * 
     * @throws IOException if unable to read pixels from {@code server}
     */
    public List<PixelFeature> calculateFeatures(ImageData<T> imageData, RegionRequest request) throws IOException;
    
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