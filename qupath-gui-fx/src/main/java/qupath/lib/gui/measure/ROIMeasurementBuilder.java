package qupath.lib.gui.measure;

import qupath.lib.images.ImageData;

abstract class ROIMeasurementBuilder extends AbstractNumericMeasurementBuilder {

    private ImageData<?> imageData;

    ROIMeasurementBuilder(final ImageData<?> imageData) {
        this.imageData = imageData;
    }

    boolean hasPixelSizeMicrons() {
        return imageData != null && imageData.getServerMetadata().getPixelCalibration().hasPixelSizeMicrons();
    }

    double pixelWidthMicrons() {
        if (hasPixelSizeMicrons())
            return imageData.getServerMetadata().getPixelCalibration().getPixelWidthMicrons();
        return Double.NaN;
    }

    double pixelHeightMicrons() {
        if (hasPixelSizeMicrons())
            return imageData.getServerMetadata().getPixelCalibration().getPixelHeightMicrons();
        return Double.NaN;
    }

}
