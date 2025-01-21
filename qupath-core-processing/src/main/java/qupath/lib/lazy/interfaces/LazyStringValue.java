package qupath.lib.lazy.interfaces;

public interface LazyStringValue<S> extends LazyValue<S, String> {

    @Override
    default Class<String> getMeasurementType() {
        return String.class;
    }

}
