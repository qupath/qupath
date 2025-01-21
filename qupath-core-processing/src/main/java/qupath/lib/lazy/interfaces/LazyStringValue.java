package qupath.lib.lazy.interfaces;

public interface LazyStringValue extends LazyValue<String> {

    @Override
    default Class<String> getMeasurementType() {
        return String.class;
    }

}
