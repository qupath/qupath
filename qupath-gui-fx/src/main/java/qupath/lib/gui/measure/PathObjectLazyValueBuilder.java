package qupath.lib.gui.measure;

import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathObject;

import java.util.List;

public interface PathObjectLazyValueBuilder {

    List<LazyValue<PathObject, ?>> getValues(PathObjectListWrapper wrapper);

}
