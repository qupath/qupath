package qupath.lib.lazy.objects;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class ROIAreaValue extends ROIValue {

    ROIAreaValue(final ImageData<?> imageData) {
        super(imageData);
    }

    @Override
    public String getHelpText() {
        return "Area of the selected object's ROI";
    }

    @Override
    public String getName() {
        return hasPixelSizeMicrons() ? "Area " + GeneralTools.micrometerSymbol() + "^2" : "Area px^2";
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        ROI roi = pathObject.getROI();
        if (roi == null || !roi.isArea())
            return null;
        if (hasPixelSizeMicrons())
            return roi.getScaledArea(pixelWidthMicrons(), pixelHeightMicrons());
        return roi.getArea();
    }

}
