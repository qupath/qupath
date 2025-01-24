package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

/**
 * Get the displayed name of the first TMACoreObject that is an ancestor of the supplied object.
 */
class TMACoreNameValue implements LazyStringValue<PathObject> {

    @Override
    public String getHelpText() {
        return "The name of the selected tissue microarray (TMA) core";
    }

    @Override
    public String getName() {
        return "TMA Core";
    }

    private TMACoreObject getAncestorTMACore(PathObject pathObject) {
        if (pathObject == null)
            return null;
        if (pathObject instanceof TMACoreObject core)
            return core;
        return getAncestorTMACore(pathObject.getParent());
    }

    @Override
    public String getValue(PathObject pathObject) {
        TMACoreObject core = getAncestorTMACore(pathObject);
        if (core == null)
            return null;
        return core.getDisplayedName();
    }

}
