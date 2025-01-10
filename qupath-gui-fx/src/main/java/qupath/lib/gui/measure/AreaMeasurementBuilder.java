package qupath.lib.gui.measure;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class AreaMeasurementBuilder extends ROIMeasurementBuilder {

    AreaMeasurementBuilder(final ImageData<?> imageData) {
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
            return Double.NaN;
        if (hasPixelSizeMicrons())
            return roi.getScaledArea(pixelWidthMicrons(), pixelHeightMicrons());
        return roi.getArea();
    }

}
