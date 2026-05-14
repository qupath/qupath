package qupath.lib.lazy.objects;

import qupath.lib.images.ImageData;
import qupath.lib.lazy.interfaces.LazyNumericValue;
import qupath.lib.objects.PathObject;

abstract class ROIValue implements LazyNumericValue<PathObject> {

    private final ImageData<?> imageData;

    ROIValue(final ImageData<?> imageData) {
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
