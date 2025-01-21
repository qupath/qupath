package qupath.lib.lazy.interfaces;

public interface LazyNumericValue extends LazyValue<Number> {

    @Override
    default Class<Number> getMeasurementType() {
        return Number.class;
    }

}
