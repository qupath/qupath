package qupath.lib.lazy.interfaces;

import java.util.function.Function;

class DefaultStringLazyValue<S> extends DefaultLazyValue<S, String> implements LazyStringValue<S> {

    DefaultStringLazyValue(String name, String helpText, Function<S, String> fun) {
        super(name, helpText, fun, String.class);
    }

}
