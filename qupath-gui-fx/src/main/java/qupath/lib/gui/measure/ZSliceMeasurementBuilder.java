package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.ObjectBinding;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class ZSliceMeasurementBuilder extends AbstractNumericMeasurementBuilder {

    @Override
    public String getName() {
        return "Z-slice";
    }

    @Override
    public String getHelpText() {
        return "Index of z-slice (0-based)";
    }

    @Override
    public Binding<Number> createMeasurement(final PathObject pathObject) {
        return new ObjectBinding<>() {

            @Override
            protected Number computeValue() {
                ROI roi = pathObject.getROI();
                if (roi == null)
                    return null;
                return roi.getZ();
            }
        };
    }

}
