package qupath.lib.lazy.interfaces;

public interface LazyBooleanValue<S> extends LazyValue<S, Boolean> {

    @Override
    default Class<Boolean> getMeasurementType() {
        return Boolean.class;
    }

}
