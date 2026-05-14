package qupath.lib.lazy.objects;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class PerimeterValue extends ROIValue {

    PerimeterValue(final ImageData<?> imageData) {
        super(imageData);
    }

    @Override
    public String getHelpText() {
        return "Perimeter of the selected object's ROI";
    }

    @Override
    public String getName() {
        return hasPixelSizeMicrons() ? "Perimeter " + GeneralTools.micrometerSymbol() : "Perimeter px";
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        ROI roi = pathObject.getROI();
        if (roi == null || !roi.isArea())
            return Double.NaN;
        if (hasPixelSizeMicrons())
            return roi.getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
        return roi.getLength();
    }

}
