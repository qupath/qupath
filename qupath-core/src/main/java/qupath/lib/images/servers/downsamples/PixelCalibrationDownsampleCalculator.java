package qupath.lib.images.servers.downsamples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.ImageRegion;

import java.util.Objects;

/**
 * Downsample calculator that aims to match the resolution of the output image with a target pixel size.
 * <p>
 * Note that the pixel calibration unit of the target resolution should match the unit of the input image.
 * If it does not, the pixel sizes will still be used but a warning will be logged.
 */
class PixelCalibrationDownsampleCalculator implements DownsampleCalculator {

    private static final Logger logger = LoggerFactory.getLogger(PixelCalibrationDownsampleCalculator.class);

    private final PixelCalibration targetCalibration;

    PixelCalibrationDownsampleCalculator(PixelCalibration targetCalibration) {
        this.targetCalibration = targetCalibration;
    }

    @Override
    public double getDownsample(ImageServer<?> server, ImageRegion region) {
        var cal = server.getPixelCalibration();
        if (!unitsMatch(cal)) {
            logger.warn("Unmatched pixel width & height units - expected {}, {} but found {}, {}",
                    targetCalibration.getPixelWidthUnit(), targetCalibration.getPixelHeightUnit(),
                    cal.getPixelWidthUnit(), cal.getPixelHeightUnit());
        }
        return targetCalibration.getAveragedPixelSize().doubleValue() / cal.getAveragedPixelSize().doubleValue();
    }

    private boolean unitsMatch(PixelCalibration newCal) {
        return Objects.equals(newCal.getPixelWidthUnit(), targetCalibration.getPixelWidthUnit()) &&
            !Objects.equals(newCal.getPixelHeightUnit(), targetCalibration.getPixelHeightUnit());
    }

    @Override
    public String toString() {
        return "PixelCalibrationDownsampleCalculator[target=" + targetCalibration.getAveragedPixelSize() + "]";
    }

}
