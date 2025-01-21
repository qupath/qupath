package qupath.lib.lazy.interfaces;

public interface LazyNumericValue<S> extends LazyValue<S, Number> {

    @Override
    default Class<Number> getMeasurementType() {
        return Number.class;
    }

}
