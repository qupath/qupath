package qupath.lib.gui.measure;

import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Measurements that only extract metadata from objects.
 */
public class MetadataValueFactory implements PathObjectValueFactory {

    @Override
    public List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper) {
        var list = new ArrayList<LazyValue<PathObject, ?>>();
        wrapper.getMetadataNames()
                .stream()
                .map(PathObjectLazyValues::createMetadataMeasurement)
                .forEach(list::add);
        return list;
    }

}
