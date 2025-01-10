package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.DoubleBinding;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class ROICentroidMeasurementBuilder extends ROIMeasurementBuilder {

    enum CentroidType {X, Y}

    private CentroidType type;

    ROICentroidMeasurementBuilder(ImageData<?> imageData, final CentroidType type) {
        super(imageData);
        this.type = type;
    }

    @Override
    public String getHelpText() {
        return "The location of the ROI centroid";
    }

    @Override
    public String getName() {
        return String.format("Centroid %s %s", type, hasPixelSizeMicrons() ? GeneralTools.micrometerSymbol() : "px");
    }

    public double getCentroid(ROI roi) {
        if (roi == null || type == null)
            return Double.NaN;
        if (hasPixelSizeMicrons()) {
            return type == CentroidType.X
                    ? roi.getCentroidX() * pixelWidthMicrons()
                    : roi.getCentroidY() * pixelHeightMicrons();
        } else {
            return type == CentroidType.X
                    ? roi.getCentroidX()
                    : roi.getCentroidY();
        }
    }

    @Override
    public Binding<Number> createMeasurement(PathObject pathObject) {
        return new DoubleBinding() {
            @Override
            protected double computeValue() {
                return getCentroid(pathObject.getROI());
            }

        };
    }

}
