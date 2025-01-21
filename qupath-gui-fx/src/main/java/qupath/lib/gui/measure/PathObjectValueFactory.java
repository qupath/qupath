package qupath.lib.gui.measure;

import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Interface defining a way to create lazy values from an image and associated objects.
 * <p>
 * This purpose of this is to help define 'smart' measurements and measurement tables,
 * which adapt to the image and objects that should be measured.
 */
public interface PathObjectValueFactory {

    /**
     * Create the lazy values for the objects contained in the wrapper.
     * <p>
     * A simple implementation might return a fixed list of lazy values that extract properties
     * common to all objects.
     * But other implementations might query the objects to be measured, and return values that are
     * relevant to their contents.
     *
     * @param wrapper a wrapper that encapsulate the relevant objects and the image they belong to
     * @return a list of lazy values
     */
    List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper);

    /**
     * Join multiple factories for a collection to create all of their values.
     * @param factories
     * @return
     */
    static PathObjectValueFactory join(Collection<? extends PathObjectValueFactory> factories) {
        if (factories.isEmpty())
            return wrapper -> List.of();

        if (factories.size() == 1)
            return factories.iterator().next();

        return new ConcatValueFactory(factories);
    }

    /**
     * Join multiple factories to create all of their values.
     * @param factories
     * @return
     */
    static PathObjectValueFactory join(PathObjectValueFactory... factories) {
        return join(List.of(factories));
    }

}

class ConcatValueFactory implements PathObjectValueFactory {

    private final List<PathObjectValueFactory> factories;

    ConcatValueFactory(Collection<? extends PathObjectValueFactory> factories) {
        this.factories = new ArrayList<>(factories);
    }

    @Override
    public List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper) {
        return factories
                .stream()
                .flatMap(f -> f.createValues(wrapper).stream())
                .toList();
    }
}

