package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.DoubleBinding;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class NumPointsMeasurementBuilder extends AbstractNumericMeasurementBuilder {

    @Override
    public String getName() {
        return "Num points";
    }

    @Override
    public String getHelpText() {
        return "The number of points in a (multi)point ROI";
    }

    @Override
    public Binding<Number> createMeasurement(final PathObject pathObject) {
        return new DoubleBinding() {
            @Override
            protected double computeValue() {
                ROI roi = pathObject.getROI();
                if (roi == null || !roi.isPoint())
                    return Double.NaN;
                return roi.getNumPoints();
            }

        };
    }

}
