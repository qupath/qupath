package qupath.lib.measurements.dynamic;

public interface StringMeasurementBuilder extends MeasurementBuilder<String> {

    @Override
    default Class<String> getMeasurementType() {
        return String.class;
    }

}
