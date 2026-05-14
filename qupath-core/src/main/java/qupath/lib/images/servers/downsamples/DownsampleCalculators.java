package qupath.lib.images.servers.downsamples;

import com.google.gson.TypeAdapterFactory;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.io.GsonTools;

/**
 * Helper class to create downsample calculators, which can figure out how much to downsample a specified image region.
 */
public class DownsampleCalculators {

    private static final TypeAdapterFactory factory = GsonTools.createSubTypeAdapterFactory(
            DownsampleCalculator.class, "downsampleType")
            .registerSubtype(MaxDimensionDownsampleCalculator.class, "maxDim")
            .registerSubtype(FixedDownsampleCalculator.class, "fixed")
            .registerSubtype(PixelCalibrationDownsampleCalculator.class, "pixelSize");

    static {
        GsonTools.getDefaultBuilder().registerTypeAdapterFactory(factory);
    }

    /**
     * Create a downsample calculator that resizes an image to ensure that its width and height are &leq;
     * a specified maximum length.
     * @param maxDimension
     * @return
     */
    public static DownsampleCalculator maxDimension(final int maxDimension) {
        return new MaxDimensionDownsampleCalculator(maxDimension);
    }

    /**
     * Create a downsample calculator that simply returns a fixed value.
     * @param downsample
     * @return
     */
    public static DownsampleCalculator fixedDownsample(final double downsample) {
        return new FixedDownsampleCalculator(downsample);
    }

    /**
     * Create a downsample calculator that aims to downsample an image to have a fixed pixel size,
     * defined in microns.
     * @param pixelSizeMicrons
     * @return
     */
    public static DownsampleCalculator pixelSizeMicrons(double pixelSizeMicrons) {
        var cal = new PixelCalibration.Builder()
                .pixelSizeMicrons(pixelSizeMicrons, pixelSizeMicrons)
                .build();
        return pixelSize(cal);
    }

    /**
     * Create a downsample calculator that aims to downsample an image to have a fixed pixel size.
     * @param targetPixelSize
     * @return
     */
    public static DownsampleCalculator pixelSize(PixelCalibration targetPixelSize) {
        return new PixelCalibrationDownsampleCalculator(targetPixelSize);
    }

}
