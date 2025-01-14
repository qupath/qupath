package qupath.lib.gui.measure.measurements;

public interface StringMeasurementBuilder extends MeasurementBuilder<String> {

    @Override
    default Class<String> getMeasurementType() {
        return String.class;
    }

}
