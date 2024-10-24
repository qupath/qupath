package qupath.lib.images.servers.downsamples;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

/**
 * Downsample calculator that does not actually calculate anything: it simply uses a fixed downsample.
 */
class FixedDownsampleCalculator implements DownsampleCalculator {

    private final double downsample;

    FixedDownsampleCalculator(double downsample) {
        this.downsample = downsample;
    }

    @Override
    public double getDownsample(ImageServer<?> server, ImageRegion region) {
        return downsample;
    }

    @Override
    public String toString() {
        return "FixedDownsampleCalculator[downsample=" + downsample + "]";
    }

}
