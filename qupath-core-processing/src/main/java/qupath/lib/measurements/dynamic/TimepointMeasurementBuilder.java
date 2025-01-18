package qupath.lib.measurements.dynamic;

import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class TimepointMeasurementBuilder implements NumericMeasurementBuilder {

    @Override
    public String getName() {
        return "Timepoint";
    }

    @Override
    public String getHelpText() {
        return "Index of timepoint (0-based)";
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        ROI roi = pathObject.getROI();
        if (roi == null)
            return null;
        return roi.getT();
    }

}
