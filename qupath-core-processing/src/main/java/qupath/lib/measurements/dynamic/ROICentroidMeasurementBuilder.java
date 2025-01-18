package qupath.lib.measurements.dynamic;

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

    private Double getCentroid(ROI roi) {
        if (roi == null || type == null)
            return null;
        if (hasPixelSizeMicrons()) {
            return switch (type) {
                case X -> roi.getCentroidX() * pixelWidthMicrons();
                case Y -> roi.getCentroidY() * pixelHeightMicrons();
            };
        } else {
            return switch (type) {
                case X -> roi.getCentroidX();
                case Y -> roi.getCentroidY();
            };
        }
    }

    @Override
    public Number getValue(PathObject pathObject) {
        return getCentroid(pathObject.getROI());
    }

}
