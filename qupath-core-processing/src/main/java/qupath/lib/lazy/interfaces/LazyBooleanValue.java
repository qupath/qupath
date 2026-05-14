package qupath.lib.lazy.interfaces;

/**
 * Interface for a {@link LazyValue} that gives a {@link Boolean} output.
 * @param <S> type of the input used to compute the value
 */
public interface LazyBooleanValue<S> extends LazyValue<S, Boolean> {

    @Override
    default Class<Boolean> getMeasurementType() {
        return Boolean.class;
    }

}
