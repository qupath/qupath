package qupath.lib.gui.measure;

public interface StringMeasurementBuilder extends MeasurementBuilder<String> {

    @Override
    default Class<String> getMeasurementType() {
        return String.class;
    }

}
