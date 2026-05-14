package qupath.lib.lazy.interfaces;

/**
 * Interface for a {@link LazyValue} that gives a {@link String} output.
 * @param <S> type of the input used to compute the value
 */
public interface LazyStringValue<S> extends LazyValue<S, String> {

    @Override
    default Class<String> getMeasurementType() {
        return String.class;
    }

}
