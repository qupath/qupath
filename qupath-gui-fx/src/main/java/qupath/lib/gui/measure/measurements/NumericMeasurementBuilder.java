package qupath.lib.gui.measure.measurements;

public interface NumericMeasurementBuilder extends MeasurementBuilder<Number> {

    @Override
    default Class<Number> getMeasurementType() {
        return Number.class;
    }

}
