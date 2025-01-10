package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.StringBinding;
import qupath.lib.objects.PathObject;

abstract class AbstractStringMeasurementBuilder implements MeasurementBuilder<String> {

    protected abstract String getMeasurementValue(final PathObject pathObject);

    @Override
    public Binding<String> createMeasurement(final PathObject pathObject) {
        return new StringBinding() {
            @Override
            protected String computeValue() {
                return getMeasurementValue(pathObject);
            }
        };
    }

}
