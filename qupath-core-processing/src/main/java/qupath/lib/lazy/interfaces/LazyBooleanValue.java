package qupath.lib.lazy.interfaces;

public interface LazyBooleanValue extends LazyValue<Boolean> {

    @Override
    default Class<Boolean> getMeasurementType() {
        return Boolean.class;
    }

}
