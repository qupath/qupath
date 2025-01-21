package qupath.lib.gui.measure;

import qupath.lib.lazy.interfaces.LazyValue;

import java.util.List;

public interface PathObjectLazyValueBuilder {

    List<LazyValue<?>> getValues(PathObjectListWrapper wrapper);

}
