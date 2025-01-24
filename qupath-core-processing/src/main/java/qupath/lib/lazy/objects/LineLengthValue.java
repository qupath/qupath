package qupath.lib.lazy.objects;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class LineLengthValue extends ROIValue {

    LineLengthValue(final ImageData<?> imageData) {
        super(imageData);
    }

    @Override
    public String getHelpText() {
        return "Length of the selected object's line ROI";
    }

    @Override
    public String getName() {
        return hasPixelSizeMicrons() ? "Length " + GeneralTools.micrometerSymbol() : "Length px";
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        ROI roi = pathObject.getROI();
        if (roi == null || !roi.isLine())
            return Double.NaN;
        if (hasPixelSizeMicrons())
            return roi.getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
        return roi.getLength();
    }

}
