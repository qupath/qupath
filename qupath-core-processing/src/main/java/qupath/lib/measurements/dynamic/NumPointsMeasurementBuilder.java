package qupath.lib.measurements.dynamic;

import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class NumPointsMeasurementBuilder implements NumericMeasurementBuilder {

    @Override
    public String getName() {
        return "Num points";
    }

    @Override
    public String getHelpText() {
        return "The number of points in a (multi)point ROI";
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        ROI roi = pathObject.getROI();
        if (roi == null || !roi.isPoint())
            return Double.NaN;
        return roi.getNumPoints();
    }

}
