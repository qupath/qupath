package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.DoubleBinding;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class LineLengthMeasurementBuilder extends ROIMeasurementBuilder {

    LineLengthMeasurementBuilder(final ImageData<?> imageData) {
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
    public Binding<Number> createMeasurement(final PathObject pathObject) {
        return new DoubleBinding() {
            @Override
            protected double computeValue() {
                ROI roi = pathObject.getROI();
                if (roi == null || !roi.isLine())
                    return Double.NaN;
                if (hasPixelSizeMicrons())
                    return roi.getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
                return roi.getLength();
            }

        };
    }

}
