package qupath.lib.lazy.interfaces;

/**
 * Interface for a {@link LazyValue} that gives a {@link Number} output.
 * @param <S> type of the input used to compute the value
 */
public interface LazyNumericValue<S> extends LazyValue<S, Number> {

    @Override
    default Class<Number> getMeasurementType() {
        return Number.class;
    }

}
