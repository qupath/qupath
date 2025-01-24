package qupath.lib.lazy.interfaces;

import java.util.function.Function;

class DefaultNumericLazyValue<S> extends DefaultLazyValue<S, Number> implements LazyNumericValue<S> {

    DefaultNumericLazyValue(String name, String helpText, Function<S, Number> fun) {
        super(name, helpText, fun, Number.class);
    }

}
