package qupath.imagej.gui.macro.downsamples;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

/**
 * Downsample calculator that aims to ensure that the width and height of an output image
 * are no more than a specified fixed size.
 */
public class MaxDimensionDownsampleCalculator implements DownsampleCalculator {

    private final double maxDimension;

    public MaxDimensionDownsampleCalculator(double maxDimension) {
        this.maxDimension = maxDimension;
    }

    @Override
    public double getDownsample(ImageServer<?> server, ImageRegion region) {
        int width = region == null ? server.getWidth() : region.getWidth();
        int height = region == null ? server.getHeight() : region.getHeight();
        return Math.max(1.0, Math.max(width, height) / maxDimension);
    }

    @Override
    public String toString() {
        return "MaxDimensionDownsampleCalculator[maxDimension=" + maxDimension + "]";
    }

}
