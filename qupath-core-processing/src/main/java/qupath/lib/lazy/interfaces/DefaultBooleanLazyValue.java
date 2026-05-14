package qupath.lib.lazy.interfaces;

import java.util.function.Function;

class DefaultBooleanLazyValue<S> extends DefaultLazyValue<S, Boolean> implements LazyBooleanValue<S> {

    DefaultBooleanLazyValue(String name, String helpText, Function<S, Boolean> fun) {
        super(name, helpText, fun, Boolean.class);
    }

}
