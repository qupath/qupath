package qupath.lib.lazy.interfaces;

import java.util.function.Function;
import java.util.function.Supplier;

class DefaultLazyValue<S, T> implements LazyValue<S, T> {

    private final String name;
    private final String helpText;
    private final Function<S, T> fun;
    private final Class<T> valueType;

    DefaultLazyValue(String name, String helpText, Function<S, T> fun, Class<T> valueType) {
        this.name = name;
        this.helpText = helpText;
        this.fun = fun;
        this.valueType = valueType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<T> getMeasurementType() {
        return valueType;
    }

    @Override
    public T getValue(S input) {
        return fun.apply(input);
    }

    @Override
    public String getHelpText() {
        return helpText;
    }
}
