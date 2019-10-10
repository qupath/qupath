package qupath.opencv.ml.pixel.features;

import java.io.IOException;
import java.util.List;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;

/**
 * Calculate {@linkplain PixelFeature}s corresponding to a requested region.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface FeatureCalculator<T> {
	
	/**
	 * Check if an ImageData is compatible with this feature calculator.
	 * This test may include checking bit-depth, channel number and channel names, for example.
	 * 
	 * @param imageData
	 * @return
	 */
	public boolean supportsImage(ImageData<T> imageData);

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