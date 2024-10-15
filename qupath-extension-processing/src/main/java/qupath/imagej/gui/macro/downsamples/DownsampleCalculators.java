package qupath.imagej.gui.macro.downsamples;

import com.google.gson.TypeAdapterFactory;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.io.GsonTools;

public class DownsampleCalculators {

    private static final TypeAdapterFactory factory = GsonTools.createSubTypeAdapterFactory(
            DownsampleCalculator.class, "downsampleType")
            .registerSubtype(MaxDimensionDownsampleCalculator.class, "maxDim")
            .registerSubtype(FixedDownsampleCalculator.class, "fixed")
            .registerSubtype(PixelCalibrationDownsampleCalculator.class, "pixelSize");

    static {
        GsonTools.getDefaultBuilder().registerTypeAdapterFactory(factory);
    }

    public static DownsampleCalculator maxDimension(final int maxDimension) {
        return new MaxDimensionDownsampleCalculator(maxDimension);
    }

    public static DownsampleCalculator fixedDownsample(final double downsample) {
        return new FixedDownsampleCalculator(downsample);
    }

    public static DownsampleCalculator pixelSizeMicrons(double pixelSizeMicrons) {
        var cal = new PixelCalibration.Builder()
                .pixelSizeMicrons(pixelSizeMicrons, pixelSizeMicrons)
                .build();
        return pixelSize(cal);
    }

    public static DownsampleCalculator pixelSize(PixelCalibration cal) {
        return new PixelCalibrationDownsampleCalculator(cal);
    }

}
