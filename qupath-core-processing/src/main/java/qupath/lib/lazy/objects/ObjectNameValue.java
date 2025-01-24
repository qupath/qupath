package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;

class ObjectNameValue implements LazyStringValue<PathObject> {

    @Override
    public String getName() {
        return "Name";
    }

    @Override
    public String getHelpText() {
        return "Name of the object (may be empty)";
    }

    @Override
    public String getValue(PathObject pathObject) {
        if (pathObject == null)
            return null;
        // v0.5.0 change - previously used pathObject.getDisplayedName(),
        // but this frequently led to confusion for people writing scripts
        return pathObject.getName();
    }

}
