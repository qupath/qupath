package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.DoubleBinding;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class PerimeterMeasurementBuilder extends ROIMeasurementBuilder {

    PerimeterMeasurementBuilder(final ImageData<?> imageData) {
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
    public Binding<Number> createMeasurement(final PathObject pathObject) {
        return new DoubleBinding() {
            @Override
            protected double computeValue() {
                ROI roi = pathObject.getROI();
                if (roi == null || !roi.isArea())
                    return Double.NaN;
                if (hasPixelSizeMicrons())
                    return roi.getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
                return roi.getLength();
            }

        };
    }

}
