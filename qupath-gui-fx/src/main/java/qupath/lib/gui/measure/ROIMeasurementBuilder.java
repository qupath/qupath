package qupath.lib.gui.measure;

import qupath.lib.images.ImageData;

abstract class ROIMeasurementBuilder implements NumericMeasurementBuilder {

    private final ImageData<?> imageData;

    ROIMeasurementBuilder(final ImageData<?> imageData) {
        this.imageData = imageData;
    }

    /**
     * Query if the pixel width and height are available in microns.
     * @return
     */
    protected boolean hasPixelSizeMicrons() {
        return imageData != null && imageData.getServerMetadata().getPixelCalibration().hasPixelSizeMicrons();
    }

    /**
     * Get the pixel width in microns, or NaN if this is unavailable.
     * @return
     */
    protected double pixelWidthMicrons() {
        if (hasPixelSizeMicrons())
            return imageData.getServerMetadata().getPixelCalibration().getPixelWidthMicrons();
        return Double.NaN;
    }

    /**
     * Get the pixel height in microns, or NaN if this is unavailable.
     * @return
     */
    protected double pixelHeightMicrons() {
        if (hasPixelSizeMicrons())
            return imageData.getServerMetadata().getPixelCalibration().getPixelHeightMicrons();
        return Double.NaN;
    }

}
