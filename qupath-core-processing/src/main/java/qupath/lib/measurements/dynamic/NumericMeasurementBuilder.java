package qupath.lib.measurements.dynamic;

public interface NumericMeasurementBuilder extends MeasurementBuilder<Number> {

    @Override
    default Class<Number> getMeasurementType() {
        return Number.class;
    }

}
