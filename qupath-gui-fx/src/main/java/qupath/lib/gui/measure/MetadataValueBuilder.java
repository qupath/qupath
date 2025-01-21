package qupath.lib.gui.measure;

import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.lazy.interfaces.LazyValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Measurements that only extract metadata from objects.
 * @return
 */
public class MetadataValueBuilder implements PathObjectLazyValueBuilder {

    @Override
    public List<LazyValue<?>> getValues(PathObjectListWrapper wrapper) {
        var list = new ArrayList<LazyValue<?>>();
        wrapper.getMetadataNames()
                .stream()
                .map(PathObjectLazyValues::createMetadataMeasurement)
                .forEach(list::add);
        return list;
    }

}
